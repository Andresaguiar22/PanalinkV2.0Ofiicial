package com.example.media.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import com.example.data.database.PanalinkDatabase
import com.example.data.database.PendingUploadEntity
import com.example.util.SocialUploadRecoveryHelper
import com.example.util.SocialWorkManagerClient
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SocialUploadRecoveryTest {

    private lateinit var db: PanalinkDatabase
    private lateinit var context: Context

    class FakeSocialWorkManagerClient(
        var workInfosProvider: (String) -> List<WorkInfo> = { emptyList() }
    ) : SocialWorkManagerClient {
        data class EnqueuedWorkRecord(
            val uniqueWorkName: String,
            val policy: ExistingWorkPolicy,
            val request: OneTimeWorkRequest
        )

        val enqueuedWorks = mutableListOf<EnqueuedWorkRecord>()

        override fun getWorkInfosForUniqueWork(uniqueWorkName: String): List<WorkInfo> {
            return workInfosProvider(uniqueWorkName)
        }

        override fun enqueueUniqueWork(
            uniqueWorkName: String,
            existingWorkPolicy: ExistingWorkPolicy,
            request: OneTimeWorkRequest
        ) {
            enqueuedWorks.add(EnqueuedWorkRecord(uniqueWorkName, existingWorkPolicy, request))
        }
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, PanalinkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `A - pending with no active WorkManager really creates and enqueues WorkRequest`() = runBlocking {
        val dummyFile = File(context.cacheDir, "test_file_a.jpg").apply { writeText("dummy") }
        val dao = db.pendingUploadDao()
        val upload = PendingUploadEntity(
            id = "upload-a",
            userId = "user-123",
            uploadType = "STATE",
            localFilePath = dummyFile.absolutePath,
            mimeType = "image/jpeg",
            status = "pending"
        )
        dao.insertUpload(upload)

        val client = FakeSocialWorkManagerClient(workInfosProvider = { emptyList() })
        SocialUploadRecoveryHelper.reconcileInternal(context, db, client)

        assertEquals(1, client.enqueuedWorks.size)
        val enqueued = client.enqueuedWorks[0]
        assertEquals("social_upload_upload-a", enqueued.uniqueWorkName)
        assertEquals(ExistingWorkPolicy.KEEP, enqueued.policy)
        assertEquals("upload-a", enqueued.request.workSpec.input.getString("uploadId"))
        assertEquals(NetworkType.CONNECTED, enqueued.request.workSpec.constraints.requiredNetworkType)
        assertTrue(enqueued.request.tags.contains("social_upload"))
        assertTrue(enqueued.request.tags.contains("upload_upload-a"))

        val inDb = dao.getUploadById("upload-a")
        assertEquals("pending", inDb?.status)
    }

    @Test
    fun `B - uploading with WorkManager RUNNING or ENQUEUED remains uploading and does NOT create duplicate`() = runBlocking {
        val dummyFile = File(context.cacheDir, "test_file_b.jpg").apply { writeText("dummy") }
        val dao = db.pendingUploadDao()
        val now = System.currentTimeMillis()
        val upload = PendingUploadEntity(
            id = "upload-b",
            userId = "user-123",
            uploadType = "STATE",
            localFilePath = dummyFile.absolutePath,
            mimeType = "image/jpeg",
            status = "uploading",
            updatedAt = now
        )
        dao.insertUpload(upload)

        val runningInfo = WorkInfo(
            UUID.randomUUID(),
            WorkInfo.State.RUNNING,
            setOf("social_upload")
        )
        val client = FakeSocialWorkManagerClient(workInfosProvider = { listOf(runningInfo) })

        SocialUploadRecoveryHelper.reconcileInternal(context, db, client)

        assertEquals(0, client.enqueuedWorks.size) // No duplicate enqueued
        val inDb = dao.getUploadById("upload-b")
        assertEquals("uploading", inDb?.status)
    }

    @Test
    fun `C - uploading with missing or finished WorkManager resets to pending and re-enqueues`() = runBlocking {
        val dummyFile = File(context.cacheDir, "test_file_c.jpg").apply { writeText("dummy") }
        val dao = db.pendingUploadDao()
        val now = System.currentTimeMillis()
        val upload = PendingUploadEntity(
            id = "upload-c",
            userId = "user-123",
            uploadType = "STATE",
            localFilePath = dummyFile.absolutePath,
            mimeType = "image/jpeg",
            status = "uploading",
            updatedAt = now
        )
        dao.insertUpload(upload)

        val failedInfo = WorkInfo(
            UUID.randomUUID(),
            WorkInfo.State.FAILED,
            setOf("social_upload")
        )
        val client = FakeSocialWorkManagerClient(workInfosProvider = { listOf(failedInfo) })

        SocialUploadRecoveryHelper.reconcileInternal(context, db, client)

        val inDb = dao.getUploadById("upload-c")
        assertEquals("pending", inDb?.status)
        assertEquals(1, client.enqueuedWorks.size)
        assertEquals("social_upload_upload-c", client.enqueuedWorks[0].uniqueWorkName)
        assertEquals(ExistingWorkPolicy.KEEP, client.enqueuedWorks[0].policy)
    }

    @Test
    fun `D - uploading when WorkManager query throws exception remains uploading, does NOT modify Room, does NOT enqueue`() = runBlocking {
        val dummyFile = File(context.cacheDir, "test_file_d.jpg").apply { writeText("dummy") }
        val dao = db.pendingUploadDao()
        val originalTimestamp = 123456789L
        val upload = PendingUploadEntity(
            id = "upload-d",
            userId = "user-123",
            uploadType = "STATE",
            localFilePath = dummyFile.absolutePath,
            mimeType = "image/jpeg",
            status = "uploading",
            updatedAt = originalTimestamp
        )
        dao.insertUpload(upload)

        val throwingClient = FakeSocialWorkManagerClient(
            workInfosProvider = { throw RuntimeException("Simulated WorkManager query exception (e.g. database error)") }
        )

        SocialUploadRecoveryHelper.reconcileInternal(context, db, throwingClient)

        // Verifications:
        // 1. Room not modified
        val inDb = dao.getUploadById("upload-d")
        assertNotNull(inDb)
        assertEquals("uploading", inDb?.status)
        assertEquals(originalTimestamp, inDb?.updatedAt)
        // 2. Not converted to pending
        // 3. No work enqueued
        assertEquals(0, throwingClient.enqueuedWorks.size)
    }

    @Test
    fun `E - WorkManager SUCCEEDED marks upload as completed in Room`() = runBlocking {
        val dummyFile = File(context.cacheDir, "test_file_e.jpg").apply { writeText("dummy") }
        val dao = db.pendingUploadDao()
        val upload = PendingUploadEntity(
            id = "upload-e",
            userId = "user-123",
            uploadType = "STATE",
            localFilePath = dummyFile.absolutePath,
            mimeType = "image/jpeg",
            status = "uploading"
        )
        dao.insertUpload(upload)

        val succeededInfo = WorkInfo(
            UUID.randomUUID(),
            WorkInfo.State.SUCCEEDED,
            setOf("social_upload")
        )
        val client = FakeSocialWorkManagerClient(workInfosProvider = { listOf(succeededInfo) })

        SocialUploadRecoveryHelper.reconcileInternal(context, db, client)

        val inDb = dao.getUploadById("upload-e")
        assertEquals("completed", inDb?.status)
        assertEquals(0, client.enqueuedWorks.size)
    }

    @Test
    fun `F - stale uploading past threshold resets to pending and re-enqueues via reconcileInternal`() = runBlocking {
        val dummyFile = File(context.cacheDir, "test_file_f.jpg").apply { writeText("dummy") }
        val dao = db.pendingUploadDao()
        val staleTime = System.currentTimeMillis() - (5 * 60 * 1000L) // 5 minutes ago > 3 minutes threshold
        val upload = PendingUploadEntity(
            id = "upload-f",
            userId = "user-123",
            uploadType = "STATE",
            localFilePath = dummyFile.absolutePath,
            mimeType = "image/jpeg",
            status = "uploading",
            updatedAt = staleTime
        )
        dao.insertUpload(upload)

        val client = FakeSocialWorkManagerClient(workInfosProvider = { emptyList() })

        SocialUploadRecoveryHelper.reconcileInternal(context, db, client)

        val inDb = dao.getUploadById("upload-f")
        assertEquals("pending", inDb?.status)
        assertEquals(1, client.enqueuedWorks.size)
        assertEquals("social_upload_upload-f", client.enqueuedWorks[0].uniqueWorkName)
    }

    @Test
    fun `G - missing local file without remoteUrl transitions to failed in Room`() = runBlocking {
        val dao = db.pendingUploadDao()
        val nonExistentPath = File(context.filesDir, "non_existent_file_${System.currentTimeMillis()}.jpg").absolutePath
        val upload = PendingUploadEntity(
            id = "upload-g",
            userId = "user-123",
            uploadType = "STATE",
            localFilePath = nonExistentPath,
            remoteUrl = null,
            mimeType = "image/jpeg",
            status = "pending"
        )
        dao.insertUpload(upload)

        val client = FakeSocialWorkManagerClient()
        SocialUploadRecoveryHelper.reconcileInternal(context, db, client)

        val inDb = dao.getUploadById("upload-g")
        assertEquals("failed", inDb?.status)
        assertEquals("Archivo local no encontrado", inDb?.errorMessage)
        assertEquals(0, client.enqueuedWorks.size)
    }

    @Test
    fun `H - remoteUrl already persisted allows recovery to proceed without local file`() = runBlocking {
        val dao = db.pendingUploadDao()
        val nonExistentPath = File(context.filesDir, "already_uploaded_${System.currentTimeMillis()}.mp4").absolutePath
        val upload = PendingUploadEntity(
            id = "upload-h",
            userId = "user-123",
            uploadType = "REEL",
            localFilePath = nonExistentPath,
            remoteUrl = "https://cdn.panalink.app/media/reel_123.mp4",
            mimeType = "video/mp4",
            status = "pending"
        )
        dao.insertUpload(upload)

        val client = FakeSocialWorkManagerClient(workInfosProvider = { emptyList() })
        SocialUploadRecoveryHelper.reconcileInternal(context, db, client)

        val inDb = dao.getUploadById("upload-h")
        assertEquals("pending", inDb?.status)
        assertNull(inDb?.errorMessage)
        assertEquals("https://cdn.panalink.app/media/reel_123.mp4", inDb?.remoteUrl)
        assertEquals(1, client.enqueuedWorks.size)
    }

    @Test
    fun `I - deterministic targetStateId derivation uses production function`() {
        // Case 1: Valid UUID is preserved as identity
        val validUuid = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
        val derivedUuid = SocialUploadRecoveryHelper.deriveTargetStateId(validUuid)
        assertEquals(validUuid, derivedUuid)

        // Case 2: Non-UUID upload ID deterministically generates identical valid UUID
        val nonUuid = "story_temp_upload_987654321"
        val derived1 = SocialUploadRecoveryHelper.deriveTargetStateId(nonUuid)
        val derived2 = SocialUploadRecoveryHelper.deriveTargetStateId(nonUuid)
        assertEquals(derived1, derived2)
        assertNotNull(UUID.fromString(derived1))

        // Case 3: Different uploadIds generate distinct UUIDs
        val otherNonUuid = "story_temp_upload_987654322"
        val derivedOther = SocialUploadRecoveryHelper.deriveTargetStateId(otherNonUuid)
        assertNotEquals(derived1, derivedOther)
    }

    @Test
    fun `Extra - metadata and remoteThumbnailUrl persistence in Room`() = runBlocking {
        val dao = db.pendingUploadDao()
        val uploadId = "upload-uuid-metadata"
        val metadataWithThumbnail = """{"remoteThumbnailUrl":"https://cdn.panalink.app/thumb.jpg","audioUrl":"https://cdn.panalink.app/music.mp3"}"""

        val upload = PendingUploadEntity(
            id = uploadId,
            userId = "user-123",
            uploadType = "STATE",
            localFilePath = "/dummy/video.mp4",
            thumbnailPath = "/dummy/thumb_local.jpg",
            mimeType = "video/mp4",
            caption = "Test Caption",
            metadataJson = metadataWithThumbnail,
            status = "uploading",
            remoteUrl = "https://cdn.panalink.app/video.mp4"
        )
        dao.insertUpload(upload)

        val retrieved = dao.getUploadById(uploadId)
        assertNotNull(retrieved)
        assertEquals("https://cdn.panalink.app/video.mp4", retrieved?.remoteUrl)

        val parsedJson = org.json.JSONObject(retrieved!!.metadataJson!!)
        assertEquals("https://cdn.panalink.app/thumb.jpg", parsedJson.getString("remoteThumbnailUrl"))
        assertEquals("https://cdn.panalink.app/music.mp3", parsedJson.getString("audioUrl"))
    }
}
