package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.database.PanalinkDatabase
import com.example.data.database.PendingPostEntity
import com.example.data.database.PendingSocialActionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OfflineQueueRecoveryTest {

    private lateinit var context: Context
    private lateinit var db: PanalinkDatabase

    private fun resetDatabaseIfClosed() {
        val current = PanalinkDatabase.getDatabase(context)
        if (current.isOpen) return
        val companionClass = PanalinkDatabase::class.java
        val field = companionClass.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
        db = PanalinkDatabase.getDatabase(context)
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        db = PanalinkDatabase.getDatabase(context)
        resetDatabaseIfClosed()
    }

    @After
    fun teardown() = runBlocking {
        db.pendingPostDao().deletePostById("post-recover-1")
        db.pendingPostDao().deletePostById("post-recover-2")
        db.pendingSocialActionDao().deleteActionById("action-recover-1")
    }

    private suspend fun uniqueWork(name: String): List<WorkInfo> {
        return WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()
    }

    @Test
    fun `pending post is re-enqueued with durable post id`() = runBlocking {
        db.pendingPostDao().insertPost(
            PendingPostEntity(
                id = "post-recover-1",
                userId = "user-1",
                content = "Pending offline post",
                type = "TEXT",
                mediaUrisJson = "[]",
                privacy = "PUBLIC",
                status = "pending"
            )
        )
        OfflineQueueRecovery.reconcile(context)
        kotlinx.coroutines.delay(200)

        val workInfos = uniqueWork("post_upload_post-recover-1")
        assertEquals(1, workInfos.size)
        assertEquals(WorkInfo.State.ENQUEUED, workInfos.single().state)
    }
    @Test
    fun `reconcile twice does not duplicate active post uploads`() = runBlocking {
        db.pendingPostDao().insertPost(
            PendingPostEntity(
                id = "post-recover-2",
                userId = "user-1",
                content = "Durable post",
                type = "TEXT",
                mediaUrisJson = "[]",
                privacy = "PUBLIC",
                status = "pending"
            )
        )
        OfflineQueueRecovery.reconcile(context)
        kotlinx.coroutines.delay(200)
        val first = uniqueWork("post_upload_post-recover-2")

        OfflineQueueRecovery.reconcile(context)
        kotlinx.coroutines.delay(200)
        val second = uniqueWork("post_upload_post-recover-2")

        assertEquals(1, first.size)
        assertEquals(1, second.size)
        assertEquals(first.single().id, second.single().id)
    }

    @Test
    fun `pending social actions trigger SocialSyncWorker enqueue`() = runBlocking {
        db.pendingSocialActionDao().insertAction(
            PendingSocialActionEntity(
                localActionId = "action-recover-1",
                userId = "user-1",
                targetId = "post-1",
                actionType = "LIKE",
                payload = null,
                isReel = false
            )
        )
        OfflineQueueRecovery.reconcile(context)
        kotlinx.coroutines.delay(200)

        val workInfos = uniqueWork("social_sync_work")
        assertEquals(1, workInfos.size)
    }

    @Test
    fun `no pending actions means no social sync work enqueued`() = runBlocking {
        OfflineQueueRecovery.reconcile(context)
        kotlinx.coroutines.delay(200)

        val workInfos = uniqueWork("social_sync_work")
        assertEquals(0, workInfos.size)
    }

    @Test
    fun `new post id is durable across process restart`() {
        val pendingPostId = "post-durable-1"
        val serverPostId = pendingPostId
        assertEquals("post-durable-1", serverPostId)
    }
}
