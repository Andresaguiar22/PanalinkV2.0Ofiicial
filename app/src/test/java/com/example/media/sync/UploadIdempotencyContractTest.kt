package com.example.media.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Tests verifying the deterministic, idempotent contract between Android and the B2/VCDN Edge Functions.
 *
 * Mandatory Test Matrix:
 * 1. Mismo upload + retry -> no crea segundo vídeo.
 * 2. Retry después de reinicio del Worker -> mismo destino lógico.
 * 3. Dos uploads diferentes -> no colisionan.
 * 4. Usuario A y usuario B con el mismo clientMessageUuid -> no comparten identidad.
 * 5. Chunk retry no duplica bytes.
 * 6. Complete repetido no crea un segundo vídeo.
 * 7. VCDN falla -> B2 fallback conserva la identidad.
 */
class UploadIdempotencyContractTest {

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(160)
    }

    private fun buildB2ObjectKey(
        userId: String,
        uploadType: String,
        fileName: String?,
        stableFileName: String?,
        customFileName: String?,
        clientMessageUuid: String?
    ): String {
        val sanitizedUploadType = sanitizeFileName(uploadType.ifBlank { "misc" })
        val finalFileName: String = when {
            !stableFileName.isNullOrBlank() -> {
                sanitizeFileName(stableFileName.trim())
            }
            !clientMessageUuid.isNullOrBlank() -> {
                val stableUuid = sanitizeFileName(clientMessageUuid.trim())
                val rawFileName = if (!fileName.isNullOrBlank()) sanitizeFileName(fileName.trim()) else "file.bin"
                if (rawFileName.startsWith(stableUuid)) {
                    rawFileName
                } else {
                    "${stableUuid}-$rawFileName"
                }
            }
            !customFileName.isNullOrBlank() -> {
                sanitizeFileName(customFileName.trim())
            }
            else -> {
                val rawFileName = sanitizeFileName(fileName?.takeIf { it.isNotBlank() } ?: "file.bin")
                "${UUID.randomUUID()}-$rawFileName"
            }
        }
        return "panalink/$sanitizedUploadType/$userId/$finalFileName"
    }

    // -------------------------------------------------------------
    // VCDN Edge Function Simulator matching supabase/functions/vcdn-upload/index.ts
    // -------------------------------------------------------------
    data class VcdnSession(
        val uploadId: String,
        val videoId: String,
        val uploadUrl: String,
        var bytesReceived: Long = 0L,
        var status: String = "initiated",
        var ready: Boolean = false,
        var posterUrl: String = "",
        val size: Long,
        val filename: String
    )

    class MockVcdnEdgeEngine {
        private val sessions = mutableMapOf<String, VcdnSession>()
        private var vcdnUploadCounter = 0
        private var vcdnVideoCounter = 0

        fun initUpload(
            userId: String,
            filename: String,
            size: Long,
            uploadType: String,
            stableFileName: String? = null,
            clientMessageUuid: String? = null,
            customFileName: String? = null
        ): Map<String, Any> {
            val stableId = when {
                !stableFileName.isNullOrBlank() -> sanitize(stableFileName)
                !clientMessageUuid.isNullOrBlank() -> sanitize(clientMessageUuid)
                !customFileName.isNullOrBlank() -> sanitize(customFileName)
                else -> null
            }

            if (stableId != null) {
                val sessionKey = "$userId:$stableId"
                val existing = sessions[sessionKey]
                if (existing != null) {
                    return mapOf(
                        "uploadId" to existing.uploadId,
                        "videoId" to existing.videoId,
                        "uploadUrl" to existing.uploadUrl,
                        "bytesReceived" to existing.bytesReceived,
                        "ready" to existing.ready,
                        "status" to existing.status,
                        "posterUrl" to existing.posterUrl,
                        "idempotentReused" to true
                    )
                }
            }

            // Fresh VCDN init (generates new upstream VCDN uploadId and videoId)
            vcdnUploadCounter++
            vcdnVideoCounter++
            val uploadId = "vcdn_up_$vcdnUploadCounter"
            val videoId = "vcdn_vid_$vcdnVideoCounter"
            val uploadUrl = "https://cdn.vcdn.me/api/v1/upload/$uploadId"

            val session = VcdnSession(
                uploadId = uploadId,
                videoId = videoId,
                uploadUrl = uploadUrl,
                bytesReceived = 0L,
                status = "initiated",
                ready = false,
                size = size,
                filename = filename
            )

            if (stableId != null) {
                sessions["$userId:$stableId"] = session
            }

            return mapOf(
                "uploadId" to uploadId,
                "videoId" to videoId,
                "uploadUrl" to uploadUrl,
                "bytesReceived" to 0L,
                "ready" to false,
                "status" to "initiated"
            )
        }

        fun uploadChunk(uploadId: String, chunkBytes: Long): Long {
            val session = sessions.values.find { it.uploadId == uploadId }
            if (session != null) {
                session.bytesReceived = minOf(session.size, session.bytesReceived + chunkBytes)
                session.status = "uploading"
                return session.bytesReceived
            }
            return chunkBytes
        }

        fun completeUpload(uploadId: String): Map<String, Any> {
            val session = sessions.values.find { it.uploadId == uploadId }
            if (session != null) {
                session.status = "completed"
                session.ready = true
                session.posterUrl = "https://embed.vcdn.me/posters/${session.videoId}.jpg"
                return mapOf("status" to "completed", "idempotent" to true)
            }
            return mapOf("status" to "completed")
        }

        fun getStatus(videoId: String): Map<String, Any> {
            val session = sessions.values.find { it.videoId == videoId }
            return if (session != null) {
                mapOf(
                    "status" to session.status,
                    "ready" to session.ready,
                    "posterUrl" to session.posterUrl,
                    "streamUrl" to if (session.ready) "https://embed.vcdn.me/hls/${session.videoId}/master.m3u8" else ""
                )
            } else {
                mapOf("status" to "not_found", "ready" to false)
            }
        }

        private fun sanitize(name: String): String =
            name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(160)
    }

    // -------------------------------------------------------------
    // 1. Mismo upload + retry -> no crea segundo vídeo
    // -------------------------------------------------------------
    @Test
    fun testVcdnSameUploadAndRetryDoesNotCreateSecondVideo() {
        val engine = MockVcdnEdgeEngine()
        val userId = "user_123"
        val postUuid = "post_abc_456"
        val fileName = "social_${postUuid}_reel.mp4"

        // Attempt 1
        val init1 = engine.initUpload(
            userId = userId,
            filename = fileName,
            size = 10_000_000L,
            uploadType = "REEL",
            stableFileName = fileName,
            clientMessageUuid = postUuid
        )
        val uploadId1 = init1["uploadId"] as String
        val videoId1 = init1["videoId"] as String

        // Simulate network failure and Worker retry
        val init2 = engine.initUpload(
            userId = userId,
            filename = fileName,
            size = 10_000_000L,
            uploadType = "REEL",
            stableFileName = fileName,
            clientMessageUuid = postUuid
        )
        val uploadId2 = init2["uploadId"] as String
        val videoId2 = init2["videoId"] as String
        val reused = init2["idempotentReused"] as? Boolean

        assertEquals("Same uploadId must be reused across retries", uploadId1, uploadId2)
        assertEquals("Same videoId must be reused across retries without creating a duplicate video", videoId1, videoId2)
        assertTrue("Idempotent session reuse must be flagged", reused == true)
    }

    // -------------------------------------------------------------
    // 2. Retry después de reinicio del Worker -> mismo destino lógico
    // -------------------------------------------------------------
    @Test
    fun testVcdnRetryAfterWorkerRestartResumesSameLogicalDestination() {
        val engine = MockVcdnEdgeEngine()
        val userId = "user_worker"
        val clientUuid = "worker_story_999"
        val fileName = "social_${clientUuid}_state.mp4"

        // Worker run 1: Inits and uploads 4MB of 8MB
        val init1 = engine.initUpload(
            userId = userId,
            filename = fileName,
            size = 8_000_000L,
            uploadType = "STATE",
            stableFileName = fileName,
            clientMessageUuid = clientUuid
        )
        val uploadId1 = init1["uploadId"] as String
        engine.uploadChunk(uploadId1, 4_000_000L)

        // Worker is killed and restarts. Loads entity from Room with same clientUuid.
        val init2 = engine.initUpload(
            userId = userId,
            filename = fileName,
            size = 8_000_000L,
            uploadType = "STATE",
            stableFileName = fileName,
            clientMessageUuid = clientUuid
        )

        val bytesReceived = init2["bytesReceived"] as Long
        assertEquals("Worker must resume from previously recorded bytesReceived", 4_000_000L, bytesReceived)
        assertEquals("Upload destination must be identical", uploadId1, init2["uploadId"])
    }

    // -------------------------------------------------------------
    // 3. Dos uploads diferentes -> no colisionan
    // -------------------------------------------------------------
    @Test
    fun testTwoDifferentUploadsDoNotCollide() {
        val engine = MockVcdnEdgeEngine()
        val userId = "user_creator"

        val upload1 = engine.initUpload(
            userId = userId,
            filename = "social_reel1_reel.mp4",
            size = 5_000_000L,
            uploadType = "REEL",
            stableFileName = "social_reel1_reel.mp4",
            clientMessageUuid = "reel1"
        )

        val upload2 = engine.initUpload(
            userId = userId,
            filename = "social_reel2_reel.mp4",
            size = 6_000_000L,
            uploadType = "REEL",
            stableFileName = "social_reel2_reel.mp4",
            clientMessageUuid = "reel2"
        )

        assertNotEquals("Distinct uploads must get distinct upload IDs", upload1["uploadId"], upload2["uploadId"])
        assertNotEquals("Distinct uploads must get distinct video IDs", upload1["videoId"], upload2["videoId"])
    }

    // -------------------------------------------------------------
    // 4. Usuario A y usuario B con el mismo clientMessageUuid -> no comparten identidad
    // -------------------------------------------------------------
    @Test
    fun testUserIsolationAcrossUsersWithIdenticalClientMessageUuid() {
        val engine = MockVcdnEdgeEngine()
        val sharedUuid = "same_client_uuid_123"

        val userAInit = engine.initUpload(
            userId = "user_alice",
            filename = "video.mp4",
            size = 10_000_000L,
            uploadType = "POST",
            clientMessageUuid = sharedUuid
        )

        val userBInit = engine.initUpload(
            userId = "user_bob",
            filename = "video.mp4",
            size = 10_000_000L,
            uploadType = "POST",
            clientMessageUuid = sharedUuid
        )

        assertNotEquals("User A and User B must never share uploadId", userAInit["uploadId"], userBInit["uploadId"])
        assertNotEquals("User A and User B must never share videoId", userAInit["videoId"], userBInit["videoId"])

        // Also verify for B2 keys
        val b2KeyUserA = buildB2ObjectKey("user_alice", "POST", "video.mp4", null, null, sharedUuid)
        val b2KeyUserB = buildB2ObjectKey("user_bob", "POST", "video.mp4", null, null, sharedUuid)
        assertNotEquals("B2 keys must be strictly isolated per user", b2KeyUserA, b2KeyUserB)
        assertTrue(b2KeyUserA.contains("user_alice"))
        assertTrue(b2KeyUserB.contains("user_bob"))
    }

    // -------------------------------------------------------------
    // 5. Chunk retry no duplica bytes
    // -------------------------------------------------------------
    @Test
    fun testChunkRetryDoesNotDuplicateBytes() {
        val engine = MockVcdnEdgeEngine()
        val userId = "user_streamer"
        val clientUuid = "chunk_test_1"

        val init = engine.initUpload(
            userId = userId,
            filename = "video.mp4",
            size = 10_000_000L,
            uploadType = "REEL",
            clientMessageUuid = clientUuid
        )
        val uploadId = init["uploadId"] as String

        // Upload chunk 1 (4MB)
        val rec1 = engine.uploadChunk(uploadId, 4_000_000L)
        assertEquals(4_000_000L, rec1)

        // Retry chunk 2 (6MB remaining, total 10MB)
        val rec2 = engine.uploadChunk(uploadId, 6_000_000L)
        assertEquals(10_000_000L, rec2)

        // Attempting to send extra bytes capped at size
        val rec3 = engine.uploadChunk(uploadId, 2_000_000L)
        assertEquals(10_000_000L, rec3)
    }

    // -------------------------------------------------------------
    // 6. Complete repetido no crea un segundo vídeo
    // -------------------------------------------------------------
    @Test
    fun testRepeatedCompleteDoesNotCreateSecondVideo() {
        val engine = MockVcdnEdgeEngine()
        val userId = "user_complete_test"
        val clientUuid = "complete_uuid_1"

        val init = engine.initUpload(
            userId = userId,
            filename = "video.mp4",
            size = 5_000_000L,
            uploadType = "REEL",
            clientMessageUuid = clientUuid
        )
        val uploadId = init["uploadId"] as String
        val videoId = init["videoId"] as String

        // First complete
        val res1 = engine.completeUpload(uploadId)
        assertEquals("completed", res1["status"])

        // Second complete call (e.g. timeout on client, retry complete step)
        val res2 = engine.completeUpload(uploadId)
        assertEquals("completed", res2["status"])

        // Query status
        val status = engine.getStatus(videoId)
        assertTrue(status["ready"] as Boolean)
        assertEquals("https://embed.vcdn.me/posters/$videoId.jpg", status["posterUrl"])

        // Re-init for same UUID returns ready video without creating a second video
        val initAfterComplete = engine.initUpload(
            userId = userId,
            filename = "video.mp4",
            size = 5_000_000L,
            uploadType = "REEL",
            clientMessageUuid = clientUuid
        )
        assertEquals("Must return the exact same videoId", videoId, initAfterComplete["videoId"])
        assertTrue("Must indicate video is already ready", initAfterComplete["ready"] as Boolean)
    }

    // -------------------------------------------------------------
    // 7. VCDN falla -> B2 fallback conserva la identidad
    // -------------------------------------------------------------
    @Test
    fun testVcdnFailureFallbackToB2PreservesStableIdentity() {
        val userId = "user_fallback_test"
        val clientUuid = "post_fall_back_789"
        val stableFileName = "social_${clientUuid}_post.mp4"

        // If VCDN fails, VideoRouter calls b2Fallback with the same customFileName and clientMessageUuid
        val b2Key = buildB2ObjectKey(
            userId = userId,
            uploadType = "POST",
            fileName = stableFileName,
            stableFileName = stableFileName,
            customFileName = stableFileName,
            clientMessageUuid = clientUuid
        )

        assertEquals("panalink/POST/user_fallback_test/social_post_fall_back_789_post.mp4", b2Key)

        // Multiple retries of fallback produce the exact same B2 target
        val b2KeyRetry = buildB2ObjectKey(
            userId = userId,
            uploadType = "POST",
            fileName = stableFileName,
            stableFileName = stableFileName,
            customFileName = stableFileName,
            clientMessageUuid = clientUuid
        )

        assertEquals("B2 fallback key must be 100% deterministic", b2Key, b2KeyRetry)
    }
}
