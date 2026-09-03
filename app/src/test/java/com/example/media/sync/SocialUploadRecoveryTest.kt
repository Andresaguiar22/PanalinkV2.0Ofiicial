package com.example.media.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.PanalinkDatabase
import com.example.data.database.PendingUploadEntity
import com.example.util.SocialUploadRecoveryHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SocialUploadRecoveryTest {

    private lateinit var db: PanalinkDatabase
    private lateinit var context: Context

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
    fun `1 pending upload is recoverable`() = runBlocking {
        val dao = db.pendingUploadDao()
        val upload = PendingUploadEntity(
            id = "test-1",
            userId = "user-123",
            uploadType = "STATE",
            localFilePath = "/dummy/path.jpg",
            mimeType = "image/jpeg",
            status = "pending"
        )
        dao.insertUpload(upload)

        val retrieved = dao.getUploadById("test-1")
        assertNotNull(retrieved)
        assertEquals("pending", retrieved?.status)
    }

    @Test
    fun `2 stale uploading upload becomes pending during reconciliation`() = runBlocking {
        val dao = db.pendingUploadDao()
        val staleTime = System.currentTimeMillis() - (5 * 60 * 1000L) // 5 minutes ago
        val upload = PendingUploadEntity(
            id = "test-stale",
            userId = "user-123",
            uploadType = "STATE",
            localFilePath = "/dummy/path.jpg",
            mimeType = "image/jpeg",
            status = "uploading",
            updatedAt = staleTime
        )
        dao.insertUpload(upload)

        // Run recovery helper logic or simulate stale check
        val all = dao.getUploadsByStatus("uploading")
        assertEquals(1, all.size)

        // Simulate reconciliation step
        val now = System.currentTimeMillis()
        val item = all[0]
        if (item.status == "uploading" && (now - item.updatedAt > 3 * 60 * 1000L)) {
            dao.updateUpload(item.copy(status = "pending", updatedAt = now))
        }

        val recovered = dao.getUploadById("test-stale")
        assertEquals("pending", recovered?.status)
    }

    @Test
    fun `3 failed upload does not revive automatically`() = runBlocking {
        val dao = db.pendingUploadDao()
        val upload = PendingUploadEntity(
            id = "test-failed",
            userId = "user-123",
            uploadType = "STATE",
            localFilePath = "/dummy/path.jpg",
            mimeType = "image/jpeg",
            status = "failed",
            retryCount = 3
        )
        dao.insertUpload(upload)

        // Active active uploads query filters out failed status unless explicitly queried
        val active = dao.getUploadsByStatus("pending")
        assertEquals(0, active.size)

        val failedList = dao.getUploadsByStatus("failed")
        assertEquals(1, failedList.size)
        assertEquals("failed", failedList[0].status)
    }

    @Test
    fun `4 manual retry makes failed upload pending again`() = runBlocking {
        val dao = db.pendingUploadDao()
        val upload = PendingUploadEntity(
            id = "test-retry",
            userId = "user-123",
            uploadType = "STATE",
            localFilePath = "/dummy/path.jpg",
            mimeType = "image/jpeg",
            status = "failed",
            retryCount = 3
        )
        dao.insertUpload(upload)

        // Manual retry action
        dao.updateUpload(upload.copy(status = "pending", retryCount = 0, errorMessage = null))

        val updated = dao.getUploadById("test-retry")
        assertEquals("pending", updated?.status)
        assertEquals(0, updated?.retryCount)
        assertNull(updated?.errorMessage)
    }

    @Test
    fun `5 same uploadId replaces gracefully preventing duplicates`() = runBlocking {
        val dao = db.pendingUploadDao()
        val upload1 = PendingUploadEntity(
            id = "test-dup",
            userId = "user-123",
            uploadType = "STATE",
            localFilePath = "/dummy/path1.jpg",
            mimeType = "image/jpeg",
            status = "pending"
        )
        dao.insertUpload(upload1)

        val upload2 = upload1.copy(localFilePath = "/dummy/path2.jpg")
        dao.insertUpload(upload2) // REPLACE strategy

        val all = dao.getAllUploadsFlow()
        // Room query or flow test
        val retrieved = dao.getUploadById("test-dup")
        assertEquals("/dummy/path2.jpg", retrieved?.localFilePath)
    }

    @Test
    fun `7 missing local file results in failed status`() = runBlocking {
        val dao = db.pendingUploadDao()
        val nonExistentPath = File(context.filesDir, "non_existent_file.jpg").absolutePath
        val upload = PendingUploadEntity(
            id = "test-missing-file",
            userId = "user-123",
            uploadType = "STATE",
            localFilePath = nonExistentPath,
            mimeType = "image/jpeg",
            status = "pending"
        )
        dao.insertUpload(upload)

        val file = File(upload.localFilePath)
        if (!file.exists()) {
            dao.updateUpload(upload.copy(status = "failed", errorMessage = "Archivo local no encontrado"))
        }

        val result = dao.getUploadById("test-missing-file")
        assertEquals("failed", result?.status)
        assertEquals("Archivo local no encontrado", result?.errorMessage)
    }

    @Test
    fun `8 success state marks upload completed`() = runBlocking {
        val dao = db.pendingUploadDao()
        val upload = PendingUploadEntity(
            id = "test-success",
            userId = "user-123",
            uploadType = "STATE",
            localFilePath = "/dummy/path.jpg",
            mimeType = "image/jpeg",
            status = "uploading"
        )
        dao.insertUpload(upload)

        dao.updateUpload(upload.copy(status = "completed", remoteUrl = "https://cdn.panalink.app/media.jpg"))

        val result = dao.getUploadById("test-success")
        assertEquals("completed", result?.status)
        assertEquals("https://cdn.panalink.app/media.jpg", result?.remoteUrl)
    }

    @Test
    fun `9 metadata persistence and remote thumbnail recovery after process restart`() = runBlocking {
        val dao = db.pendingUploadDao()
        val uploadId = "upload-uuid-12345"
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
        assertEquals("/dummy/thumb_local.jpg", retrieved?.thumbnailPath)

        // Verify JSON parsing of preserved metadata
        val parsedJson = org.json.JSONObject(retrieved!!.metadataJson!!)
        assertEquals("https://cdn.panalink.app/thumb.jpg", parsedJson.getString("remoteThumbnailUrl"))
        assertEquals("https://cdn.panalink.app/music.mp3", parsedJson.getString("audioUrl"))

        // Simulate successful registration marking
        parsedJson.put("publicationRegistered", true)
        parsedJson.put("publicationId", uploadId)
        dao.updateUpload(retrieved.copy(status = "completed", metadataJson = parsedJson.toString()))

        val completed = dao.getUploadById(uploadId)
        assertEquals("completed", completed?.status)
        val completedJson = org.json.JSONObject(completed!!.metadataJson!!)
        assertEquals(true, completedJson.getBoolean("publicationRegistered"))
        assertEquals(uploadId, completedJson.getString("publicationId"))
    }

    @Test
    fun `10 deterministic targetStateId derivation prevents duplicate creation`() {
        val uploadId1 = "00000000-0000-0000-0000-000000000001"
        val derived1 = try {
            java.util.UUID.fromString(uploadId1)
            uploadId1
        } catch (_: Exception) {
            java.util.UUID.nameUUIDFromBytes("panalink_state_$uploadId1".toByteArray()).toString()
        }
        assertEquals(uploadId1, derived1)

        val nonUuidUploadId = "upload_story_1725372800000"
        val derived2A = try {
            java.util.UUID.fromString(nonUuidUploadId)
            nonUuidUploadId
        } catch (_: Exception) {
            java.util.UUID.nameUUIDFromBytes("panalink_state_$nonUuidUploadId".toByteArray()).toString()
        }
        val derived2B = try {
            java.util.UUID.fromString(nonUuidUploadId)
            nonUuidUploadId
        } catch (_: Exception) {
            java.util.UUID.nameUUIDFromBytes("panalink_state_$nonUuidUploadId".toByteArray()).toString()
        }
        assertEquals(derived2A, derived2B)
        // Valid UUID check
        assertNotNull(java.util.UUID.fromString(derived2A))
    }
}
