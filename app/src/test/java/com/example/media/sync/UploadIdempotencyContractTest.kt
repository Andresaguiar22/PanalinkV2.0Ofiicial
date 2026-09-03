package com.example.media.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Tests verifying the deterministic, idempotent contract between Android and the B2/VCDN Edge Functions.
 *
 * Contract:
 * 1. An upload attempt and any subsequent retries with the same [clientMessageUuid] or [stableFileName]
 *    MUST target the exact same remote object key in B2.
 * 2. User isolation (panalink/{uploadType}/{userId}/...) is strictly enforced.
 * 3. Arbitrary/legacy uploads without stable IDs generate unique UUID prefixes to prevent collision.
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

    @Test
    fun testStableFileNameProducesDeterministicB2ObjectKeyOnRetry() {
        val userId = "user_abc_123"
        val clientUuid = "550e8400-e29b-41d4-a716-446655440000"
        val stableName = "${clientUuid}_0.jpg"

        // First attempt
        val keyAttempt1 = buildB2ObjectKey(
            userId = userId,
            uploadType = "image",
            fileName = stableName,
            stableFileName = stableName,
            customFileName = stableName,
            clientMessageUuid = clientUuid
        )

        // Simulated retry after network interruption
        val keyAttempt2 = buildB2ObjectKey(
            userId = userId,
            uploadType = "image",
            fileName = stableName,
            stableFileName = stableName,
            customFileName = stableName,
            clientMessageUuid = clientUuid
        )

        assertEquals("Object key must be 100% identical on retries", keyAttempt1, keyAttempt2)
        assertEquals("panalink/image/user_abc_123/550e8400-e29b-41d4-a716-446655440000_0.jpg", keyAttempt1)
    }

    @Test
    fun testClientMessageUuidWithoutStableFileNameProducesDeterministicKey() {
        val userId = "user_xyz_789"
        val clientUuid = "msg_custom_id_999"
        val originalFile = "photo.png"

        val keyAttempt1 = buildB2ObjectKey(
            userId = userId,
            uploadType = "chat_media",
            fileName = originalFile,
            stableFileName = null,
            customFileName = null,
            clientMessageUuid = clientUuid
        )

        val keyAttempt2 = buildB2ObjectKey(
            userId = userId,
            uploadType = "chat_media",
            fileName = originalFile,
            stableFileName = null,
            customFileName = null,
            clientMessageUuid = clientUuid
        )

        assertEquals("Both attempts with same clientMessageUuid must match", keyAttempt1, keyAttempt2)
        assertEquals("panalink/chat_media/user_xyz_789/msg_custom_id_999-photo.png", keyAttempt1)
    }

    @Test
    fun testSocialWorkerStableKeysAreDeterministic() {
        val userId = "author_456"
        val storyId = "story_uuid_777"
        val stableStoryName = "social_${storyId}_state.mp4"

        val key1 = buildB2ObjectKey(
            userId = userId,
            uploadType = "STATE",
            fileName = stableStoryName,
            stableFileName = stableStoryName,
            customFileName = stableStoryName,
            clientMessageUuid = storyId
        )

        val key2 = buildB2ObjectKey(
            userId = userId,
            uploadType = "STATE",
            fileName = stableStoryName,
            stableFileName = stableStoryName,
            customFileName = stableStoryName,
            clientMessageUuid = storyId
        )

        assertEquals("Social story upload key must be idempotent across retries", key1, key2)
        assertEquals("panalink/STATE/author_456/social_story_uuid_777_state.mp4", key1)
    }

    @Test
    fun testUnkeyedUploadsGenerateUniqueRandomKeys() {
        val userId = "user_anon"

        val key1 = buildB2ObjectKey(
            userId = userId,
            uploadType = "misc",
            fileName = "temp.jpg",
            stableFileName = null,
            customFileName = null,
            clientMessageUuid = null
        )

        val key2 = buildB2ObjectKey(
            userId = userId,
            uploadType = "misc",
            fileName = "temp.jpg",
            stableFileName = null,
            customFileName = null,
            clientMessageUuid = null
        )

        assertNotEquals("Unkeyed generic uploads must generate unique random UUID keys to prevent collision", key1, key2)
        assertTrue(key1.startsWith("panalink/misc/user_anon/"))
        assertTrue(key2.startsWith("panalink/misc/user_anon/"))
        assertTrue(key1.endsWith("-temp.jpg"))
        assertTrue(key2.endsWith("-temp.jpg"))
    }

    @Test
    fun testUserIsolationAndPathTraversalSanitization() {
        val userId = "user_safe"
        val maliciousName = "../../../etc/passwd"

        val key = buildB2ObjectKey(
            userId = userId,
            uploadType = "image",
            fileName = maliciousName,
            stableFileName = maliciousName,
            customFileName = maliciousName,
            clientMessageUuid = null
        )

        assertEquals("panalink/image/user_safe/.._.._.._etc_passwd", key)
        assertTrue("Must be confined to user's panalink namespace", key.startsWith("panalink/image/user_safe/"))
    }
}
