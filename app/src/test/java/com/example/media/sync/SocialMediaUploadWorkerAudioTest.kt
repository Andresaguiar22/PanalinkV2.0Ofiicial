package com.example.media.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.TestWorkerBuilder
import com.example.data.database.PanalinkDatabase
import com.example.data.database.PendingUploadEntity
import com.example.worker.SocialMediaUploadWorker
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
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SocialMediaUploadWorkerAudioTest {

    private lateinit var context: Context
    private lateinit var db: PanalinkDatabase
    private var uploadId: String? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = PanalinkDatabase.getDatabase(context)
    }

    @After
    fun teardown() {
        val id = uploadId
        if (id != null) {
            runBlocking { db.pendingUploadDao().deleteUploadById(id) }
        }
        db.close()
    }

    @Test
    fun `AUDIO worker executes durable completion path and does not create Story`() = runBlocking {
        val id = "worker-audio-${UUID.randomUUID()}"
        uploadId = id
        val localFile = File(context.cacheDir, "$id.m4a").apply { writeText("audio") }
        val remoteUrl = "https://cdn.panalink.app/$id.m4a"

        db.pendingUploadDao().insertUpload(
            PendingUploadEntity(
                id = id,
                userId = "user-123",
                uploadType = "AUDIO",
                localFilePath = localFile.absolutePath,
                mimeType = "audio/mp4",
                caption = "Audio de historia",
                status = "pending",
                remoteUrl = remoteUrl
            )
        )

        val worker = TestWorkerBuilder.from(
            context,
            SocialMediaUploadWorker::class.java
        ).setInputData(
            androidx.work.workDataOf("uploadId" to id)
        ).build()

        val result = worker.startWork().get()

        assertTrue("El Worker AUDIO debe terminar con success", result is androidx.work.ListenableWorker.Result.Success)

        val entity = db.pendingUploadDao().getUploadById(id)
        assertEquals("completed", entity?.status)
        assertEquals(remoteUrl, entity?.remoteUrl)
        assertEquals("AUDIO", entity?.uploadType)
        assertFalse("El worker debe limpiar el archivo local AUDIO al completar", localFile.exists())
        assertTrue("AUDIO no debe crear ninguna Story", db.statesDao().getAllStatesSync().isEmpty())
    }
}
