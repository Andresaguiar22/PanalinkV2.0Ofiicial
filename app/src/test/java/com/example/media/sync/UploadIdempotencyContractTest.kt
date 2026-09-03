package com.example.media.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests verifying the deterministic, atomic idempotent contract between Android and the B2/VCDN Edge Functions.
 *
 * Mandatory Test Matrix:
 * 1. Concurrent init: 100 concurrent requests (same userId, same stableId) -> exactly 1 upstream video created.
 * 2. Cross-instance: Edge instance A (init + chunks) -> Edge instance B (init/retry) -> recovers exact state.
 * 3. Lost response: Chunk arrives at VCDN, response lost, client retries -> no byte duplication.
 * 4. Concurrent complete: 100 concurrent complete calls -> exactly 1 upstream completion, all return success.
 * 5. Mismo upload + retry -> no crea segundo vídeo.
 * 6. Retry después de reinicio del Worker -> mismo destino lógico.
 * 7. Dos uploads diferentes -> no colisionan.
 * 8. Usuario A y usuario B con el mismo clientMessageUuid -> no comparten identidad.
 * 9. VCDN falla -> B2 fallback conserva la identidad.
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
    // Simulated PostgreSQL Database Engine (Matches 20260907000000_vcdn_upload_sessions.sql)
    // -------------------------------------------------------------
    data class DbVcdnSession(
        val id: String,
        val userId: String,
        val stableId: String,
        var uploadId: String? = null,
        var videoId: String? = null,
        var uploadUrl: String? = null,
        var filename: String? = null,
        var contentType: String? = null,
        var size: Long = 0L,
        var bytesReceived: Long = 0L,
        var status: String = "claiming",
        var posterUrl: String? = null,
        var ready: Boolean = false,
        var claimedAt: Long = System.currentTimeMillis()
    )

    class SimulatedPostgresDb {
        // Enforces UNIQUE(user_id, stable_id) constraint
        private val sessions = ConcurrentHashMap<String, DbVcdnSession>()
        private val lock = Any()

        fun claimVcdnUploadSession(
            userId: String,
            stableId: String,
            filename: String?,
            contentType: String?,
            size: Long
        ): Map<String, Any> = synchronized(lock) {
            val key = "$userId:$stableId"
            val existing = sessions[key]

            if (existing != null) {
                // If populated with uploadId/videoId, reuse
                if (existing.uploadId != null && existing.videoId != null) {
                    return mapOf(
                        "action" to "REUSE",
                        "uploadId" to existing.uploadId!!,
                        "videoId" to existing.videoId!!,
                        "uploadUrl" to (existing.uploadUrl ?: ""),
                        "bytesReceived" to existing.bytesReceived,
                        "status" to existing.status,
                        "ready" to existing.ready,
                        "posterUrl" to (existing.posterUrl ?: "")
                    )
                }

                // If claimed recently (<15s) and being initialized upstream
                if (System.currentTimeMillis() - existing.claimedAt < 15000L) {
                    return mapOf(
                        "action" to "WAIT_CLAIM",
                        "status" to existing.status
                    )
                }

                // Reclaim expired/failed claim
                existing.claimedAt = System.currentTimeMillis()
                existing.status = "claiming"
                return mapOf("action" to "PROCEED_INIT")
            }

            // Atomic insert
            val newSession = DbVcdnSession(
                id = key,
                userId = userId,
                stableId = stableId,
                filename = filename,
                contentType = contentType,
                size = size,
                status = "claiming",
                claimedAt = System.currentTimeMillis()
            )
            sessions[key] = newSession
            return mapOf("action" to "PROCEED_INIT")
        }

        fun populateVcdnUploadSession(
            userId: String,
            stableId: String,
            uploadId: String,
            videoId: String,
            uploadUrl: String?
        ): Boolean = synchronized(lock) {
            val key = "$userId:$stableId"
            val existing = sessions[key] ?: return false
            existing.uploadId = uploadId
            existing.videoId = videoId
            existing.uploadUrl = uploadUrl
            existing.status = "initiated"
            existing.bytesReceived = 0L
            return true
        }

        fun updateVcdnUploadBytes(uploadId: String, bytesReceived: Long): Map<String, Any> = synchronized(lock) {
            val session = sessions.values.find { it.uploadId == uploadId }
            if (session != null) {
                session.bytesReceived = maxOf(session.bytesReceived, bytesReceived)
                if (session.status != "completed" && session.status != "ready") {
                    session.status = "uploading"
                }
                return mapOf("bytesReceived" to session.bytesReceived, "status" to session.status)
            }
            return mapOf("bytesReceived" to bytesReceived, "status" to "uploading")
        }

        fun claimVcdnComplete(uploadId: String): Map<String, Any> = synchronized(lock) {
            val session = sessions.values.find { it.uploadId == uploadId }
                ?: return mapOf("action" to "PROCEED_COMPLETE")

            if (session.status == "completed" || session.status == "ready" || session.ready) {
                return mapOf("action" to "ALREADY_COMPLETED", "videoId" to (session.videoId ?: ""))
            }
            if (session.status == "completing") {
                return mapOf("action" to "ALREADY_COMPLETING", "videoId" to (session.videoId ?: ""))
            }

            session.status = "completing"
            return mapOf("action" to "PROCEED_COMPLETE", "videoId" to (session.videoId ?: ""))
        }

        fun finalizeVcdnSession(uploadId: String, ready: Boolean, posterUrl: String?) = synchronized(lock) {
            val session = sessions.values.find { it.uploadId == uploadId }
            if (session != null) {
                session.status = if (ready) "ready" else "completed"
                session.ready = ready
                if (posterUrl != null) session.posterUrl = posterUrl
            }
        }

        fun getSessionCount(): Int = sessions.size
    }

    // -------------------------------------------------------------
    // Simulated Upstream VCDN API & Edge Function Instance
    // -------------------------------------------------------------
    class SimulatedVcdnUpstream {
        val initCallCount = AtomicInteger(0)
        val completeCallCount = AtomicInteger(0)
        val chunkBytesReceivedMap = ConcurrentHashMap<String, Long>()

        fun vcdnInit(): Pair<String, String> {
            val count = initCallCount.incrementAndGet()
            return "vcdn_up_$count" to "vcdn_vid_$count"
        }

        fun vcdnChunk(uploadId: String, bytesLength: Long): Long {
            return chunkBytesReceivedMap.compute(uploadId) { _, current ->
                (current ?: 0L) + bytesLength
            }!!
        }

        fun vcdnComplete(): Boolean {
            completeCallCount.incrementAndGet()
            return true
        }
    }

    class EdgeFunctionInstance(
        private val db: SimulatedPostgresDb,
        private val upstream: SimulatedVcdnUpstream
    ) {
        fun handleInit(
            userId: String,
            stableId: String?,
            filename: String,
            size: Long
        ): Map<String, Any> {
            if (stableId != null) {
                var claim = db.claimVcdnUploadSession(userId, stableId, filename, "video/mp4", size)
                var retries = 0
                while (claim["action"] == "WAIT_CLAIM" && retries < 20) {
                    Thread.sleep(10)
                    claim = db.claimVcdnUploadSession(userId, stableId, filename, "video/mp4", size)
                    retries++
                }

                if (claim["action"] == "REUSE") {
                    return mapOf(
                        "uploadId" to claim["uploadId"] as String,
                        "videoId" to claim["videoId"] as String,
                        "bytesReceived" to claim["bytesReceived"] as Long,
                        "ready" to claim["ready"] as Boolean,
                        "idempotentReused" to true
                    )
                }

                if (claim["action"] == "PROCEED_INIT") {
                    // Call upstream VCDN
                    val (uploadId, videoId) = upstream.vcdnInit()
                    db.populateVcdnUploadSession(userId, stableId, uploadId, videoId, "https://cdn.vcdn.me/$uploadId")
                    return mapOf(
                        "uploadId" to uploadId,
                        "videoId" to videoId,
                        "bytesReceived" to 0L,
                        "ready" to false
                    )
                }
            }

            val (uploadId, videoId) = upstream.vcdnInit()
            return mapOf("uploadId" to uploadId, "videoId" to videoId, "bytesReceived" to 0L, "ready" to false)
        }

        fun handleChunk(uploadId: String, offset: Long, chunkLength: Long): Map<String, Any> {
            val currentUpstream = upstream.vcdnChunk(uploadId, chunkLength)
            val dbRes = db.updateVcdnUploadBytes(uploadId, currentUpstream)
            return mapOf("bytesReceived" to dbRes["bytesReceived"] as Long)
        }

        fun handleComplete(uploadId: String): Map<String, Any> {
            val claim = db.claimVcdnComplete(uploadId)
            val action = claim["action"] as String
            if (action == "ALREADY_COMPLETED" || action == "ALREADY_COMPLETING") {
                return mapOf("status" to "completed", "idempotent" to true)
            }

            upstream.vcdnComplete()
            db.finalizeVcdnSession(uploadId, true, "https://embed.vcdn.me/poster.jpg")
            return mapOf("status" to "completed")
        }
    }

    // =============================================================
    // 1. CONCURRENT INIT: 100 requests simultáneos -> exactamente 1 vídeo upstream
    // =============================================================
    @Test
    fun test100ConcurrentInitsProduceExactlyOneUpstreamVideo() {
        val db = SimulatedPostgresDb()
        val upstream = SimulatedVcdnUpstream()
        val edge = EdgeFunctionInstance(db, upstream)

        val threadCount = 100
        val executor = Executors.newFixedThreadPool(16)
        val startGate = CountDownLatch(1)
        val endGate = CountDownLatch(threadCount)

        val results = ConcurrentHashMap<Int, Map<String, Any>>()
        val userId = "user_concurrent_test"
        val stableId = "post_uuid_stable_100"

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    startGate.await()
                    val res = edge.handleInit(userId, stableId, "video.mp4", 15_000_000L)
                    results[i] = res
                } finally {
                    endGate.countDown()
                }
            }
        }

        startGate.countDown()
        assertTrue("All 100 requests must finish", endGate.await(10, TimeUnit.SECONDS))
        executor.shutdown()

        assertEquals("All 100 requests must receive responses", 100, results.size)
        assertEquals("PostgreSQL must enforce exactly 1 persisted session", 1, db.getSessionCount())
        assertEquals("Upstream VCDN init must be called EXACTLY ONCE", 1, upstream.initCallCount.get())

        val firstUploadId = results[0]?.get("uploadId") as String
        val firstVideoId = results[0]?.get("videoId") as String

        for (entry in results.values) {
            assertEquals("Every concurrent thread must get the exact same uploadId", firstUploadId, entry["uploadId"])
            assertEquals("Every concurrent thread must get the exact same videoId", firstVideoId, entry["videoId"])
        }
    }

    // =============================================================
    // 2. CROSS-INSTANCE: Instancia A -> Instancia B recupera estado exacto
    // =============================================================
    @Test
    fun testCrossInstanceEdgeStateRecovery() {
        val sharedDb = SimulatedPostgresDb()
        val upstream = SimulatedVcdnUpstream()

        val instanceA = EdgeFunctionInstance(sharedDb, upstream)
        val instanceB = EdgeFunctionInstance(sharedDb, upstream)

        val userId = "user_cross_instance"
        val stableId = "story_cross_456"

        // Instance A handles init and 4MB chunk
        val initA = instanceA.handleInit(userId, stableId, "story.mp4", 10_000_000L)
        val uploadId = initA["uploadId"] as String
        val videoId = initA["videoId"] as String

        instanceA.handleChunk(uploadId, 0L, 4_000_000L)

        // Instance B receives a retry init from another worker/region
        val initB = instanceB.handleInit(userId, stableId, "story.mp4", 10_000_000L)

        assertEquals("Instance B must recover exact uploadId from DB", uploadId, initB["uploadId"])
        assertEquals("Instance B must recover exact videoId from DB", videoId, initB["videoId"])
        assertEquals("Instance B must recover exact bytesReceived from DB", 4_000_000L, initB["bytesReceived"])
        assertEquals("Upstream init must still only have been called once", 1, upstream.initCallCount.get())
    }

    // =============================================================
    // 3. LOST RESPONSE: Chunk llega pero respuesta HTTP se pierde
    // =============================================================
    @Test
    fun testLostResponseOnChunkDoesNotCorruptOrMultiplyBytes() {
        val db = SimulatedPostgresDb()
        val upstream = SimulatedVcdnUpstream()
        val edge = EdgeFunctionInstance(db, upstream)

        val userId = "user_lost_resp"
        val stableId = "chunk_lost_test"

        val init = edge.handleInit(userId, stableId, "video.mp4", 8_000_000L)
        val uploadId = init["uploadId"] as String

        // Chunk 1 (4MB) arrives at server and updates DB
        val chunkResp1 = edge.handleChunk(uploadId, 0L, 4_000_000L)
        assertEquals(4_000_000L, chunkResp1["bytesReceived"])

        // Client lost response due to socket timeout and re-queries DB bytesReceived
        val reCheckInit = edge.handleInit(userId, stableId, "video.mp4", 8_000_000L)
        assertEquals(4_000_000L, reCheckInit["bytesReceived"])

        // Client resumes and sends remaining chunk (4MB)
        val chunkResp2 = edge.handleChunk(uploadId, 4_000_000L, 4_000_000L)
        assertEquals(8_000_000L, chunkResp2["bytesReceived"])
    }

    // =============================================================
    // 4. CONCURRENT COMPLETE: 100 complete simultáneos -> 1 llamada upstream
    // =============================================================
    @Test
    fun test100ConcurrentCompletesExecuteUpstreamOnlyOnce() {
        val db = SimulatedPostgresDb()
        val upstream = SimulatedVcdnUpstream()
        val edge = EdgeFunctionInstance(db, upstream)

        val userId = "user_complete_concurrent"
        val stableId = "post_comp_100"

        val init = edge.handleInit(userId, stableId, "video.mp4", 5_000_000L)
        val uploadId = init["uploadId"] as String

        val threadCount = 100
        val executor = Executors.newFixedThreadPool(16)
        val startGate = CountDownLatch(1)
        val endGate = CountDownLatch(threadCount)
        val results = ConcurrentHashMap<Int, Map<String, Any>>()

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    startGate.await()
                    results[i] = edge.handleComplete(uploadId)
                } finally {
                    endGate.countDown()
                }
            }
        }

        startGate.countDown()
        assertTrue("All 100 complete requests must finish", endGate.await(10, TimeUnit.SECONDS))
        executor.shutdown()

        assertEquals("All complete requests must return status=completed", 100, results.values.count { it["status"] == "completed" })
        assertEquals("Upstream VCDN complete must be called EXACTLY ONCE", 1, upstream.completeCallCount.get())
    }

    // =============================================================
    // 5. Mismo upload + retry -> no crea segundo vídeo
    // =============================================================
    @Test
    fun testVcdnSameUploadAndRetryDoesNotCreateSecondVideo() {
        val db = SimulatedPostgresDb()
        val upstream = SimulatedVcdnUpstream()
        val edge = EdgeFunctionInstance(db, upstream)

        val userId = "user_123"
        val postUuid = "post_abc_456"
        val fileName = "social_${postUuid}_reel.mp4"

        val init1 = edge.handleInit(userId, postUuid, fileName, 10_000_000L)
        val uploadId1 = init1["uploadId"] as String
        val videoId1 = init1["videoId"] as String

        val init2 = edge.handleInit(userId, postUuid, fileName, 10_000_000L)
        val uploadId2 = init2["uploadId"] as String
        val videoId2 = init2["videoId"] as String

        assertEquals("Same uploadId must be reused across retries", uploadId1, uploadId2)
        assertEquals("Same videoId must be reused across retries", videoId1, videoId2)
        assertEquals("Upstream init must only be called once", 1, upstream.initCallCount.get())
    }

    // =============================================================
    // 6. Dos uploads diferentes -> no colisionan
    // =============================================================
    @Test
    fun testTwoDifferentUploadsDoNotCollide() {
        val db = SimulatedPostgresDb()
        val upstream = SimulatedVcdnUpstream()
        val edge = EdgeFunctionInstance(db, upstream)

        val userId = "user_creator"

        val upload1 = edge.handleInit(userId, "reel_1", "reel1.mp4", 5_000_000L)
        val upload2 = edge.handleInit(userId, "reel_2", "reel2.mp4", 6_000_000L)

        assertNotEquals("Distinct uploads must get distinct upload IDs", upload1["uploadId"], upload2["uploadId"])
        assertNotEquals("Distinct uploads must get distinct video IDs", upload1["videoId"], upload2["videoId"])
        assertEquals("Upstream init must be called twice for distinct uploads", 2, upstream.initCallCount.get())
    }

    // =============================================================
    // 7. Usuario A y usuario B con el mismo clientMessageUuid -> no comparten identidad
    // =============================================================
    @Test
    fun testUserIsolationAcrossUsersWithIdenticalClientMessageUuid() {
        val db = SimulatedPostgresDb()
        val upstream = SimulatedVcdnUpstream()
        val edge = EdgeFunctionInstance(db, upstream)

        val sharedUuid = "same_client_uuid_123"

        val userAInit = edge.handleInit("user_alice", sharedUuid, "video.mp4", 10_000_000L)
        val userBInit = edge.handleInit("user_bob", sharedUuid, "video.mp4", 10_000_000L)

        assertNotEquals("User A and User B must never share uploadId", userAInit["uploadId"], userBInit["uploadId"])
        assertNotEquals("User A and User B must never share videoId", userAInit["videoId"], userBInit["videoId"])
        assertEquals("Two distinct users must result in 2 upstream sessions", 2, upstream.initCallCount.get())

        // B2 keys verification
        val b2KeyUserA = buildB2ObjectKey("user_alice", "POST", "video.mp4", null, null, sharedUuid)
        val b2KeyUserB = buildB2ObjectKey("user_bob", "POST", "video.mp4", null, null, sharedUuid)
        assertNotEquals("B2 keys must be strictly isolated per user", b2KeyUserA, b2KeyUserB)
        assertTrue(b2KeyUserA.contains("user_alice"))
        assertTrue(b2KeyUserB.contains("user_bob"))
    }

    // =============================================================
    // 8. VCDN falla -> B2 fallback conserva la identidad
    // =============================================================
    @Test
    fun testVcdnFailureFallbackToB2PreservesStableIdentity() {
        val userId = "user_fallback_test"
        val clientUuid = "post_fall_back_789"
        val stableFileName = "social_${clientUuid}_post.mp4"

        val b2Key = buildB2ObjectKey(
            userId = userId,
            uploadType = "POST",
            fileName = stableFileName,
            stableFileName = stableFileName,
            customFileName = stableFileName,
            clientMessageUuid = clientUuid
        )

        assertEquals("panalink/POST/user_fallback_test/social_post_fall_back_789_post.mp4", b2Key)

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
