package com.example

import com.example.data.database.ChatEntity
import com.example.data.model.ThreadMessage
import com.example.util.ChatRoutingResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageRoutingTest {

    @Test
    fun threadMessage_prefersCanonicalThreadIdOverChatId() {
        val message = ThreadMessage(
            id = "11111111-1111-1111-1111-111111111111",
            threadId = "22222222-2222-2222-2222-222222222222",
            chatId = "33333333-3333-3333-3333-333333333333",
            senderId = "44444444-4444-4444-4444-444444444444",
            receiverId = "55555555-5555-5555-5555-555555555555",
            createdAt = "2026-08-16T22:00:00Z",
            clientMessageUuid = "66666666-6666-6666-6666-666666666666"
        )

        assertEquals("22222222-2222-2222-2222-222222222222", message.toMessage().chatId)
    }

    @Test
    fun caseA_fcmContainsChatIdAndThreadId_resultIsThreadId() {
        val payload = mapOf(
            "chat_id" to "phys-chat-999",
            "thread_id" to "canonical-thread-123",
            "sender_id" to "user-alice"
        )
        val extractedId = ChatRoutingResolver.extractChatIdFromPayload(payload)
        assertEquals("canonical-thread-123", extractedId)

        val resolved = ChatRoutingResolver.resolveCanonicalChatId(
            rawThreadId = payload["thread_id"],
            rawChatId = payload["chat_id"],
            localChatEntity = null
        )
        assertEquals("canonical-thread-123", resolved)
    }

    @Test
    fun caseB_fcmContainsOnlyThreadId_resultIsThreadId() {
        val payload = mapOf(
            "thread_id" to "canonical-thread-123",
            "sender_id" to "user-alice"
        )
        val extractedId = ChatRoutingResolver.extractChatIdFromPayload(payload)
        assertEquals("canonical-thread-123", extractedId)

        val resolved = ChatRoutingResolver.resolveCanonicalChatId(
            rawThreadId = payload["thread_id"],
            rawChatId = null,
            localChatEntity = null
        )
        assertEquals("canonical-thread-123", resolved)
    }

    @Test
    fun caseC_fcmContainsOnlyChatId_resolvesViaRoomEntityIfKnown() {
        val payload = mapOf(
            "chat_id" to "phys-chat-999",
            "sender_id" to "user-alice"
        )
        val localEntity = ChatEntity(
            id = "phys-chat-999",
            createdAt = "2026-09-04T12:00:00Z",
            threadId = "canonical-thread-123",
            name = "Alice",
            type = "dm",
            otherUserId = "user-alice"
        )

        val resolved = ChatRoutingResolver.resolveCanonicalChatId(
            rawThreadId = null,
            rawChatId = payload["chat_id"],
            localChatEntity = localEntity
        )
        assertEquals("canonical-thread-123", resolved)
    }

    @Test
    fun caseD_chatIdDifferentFromThreadId_neverUsesPhysicalChatIdWhenThreadResolvable() {
        val physicalChatId = "chat-phys-0000"
        val canonicalThreadId = "thread-canon-1111"

        val localEntity = ChatEntity(
            id = physicalChatId,
            createdAt = "2026-09-04T12:00:00Z",
            threadId = canonicalThreadId,
            name = "Bob",
            type = "dm",
            otherUserId = "user-bob"
        )

        val resolved = ChatRoutingResolver.resolveCanonicalChatId(
            rawThreadId = null,
            rawChatId = physicalChatId,
            localChatEntity = localEntity
        )

        // Must resolve to the canonical thread, never the physical chat ID
        assertEquals(canonicalThreadId, resolved)
        assertTrue(resolved != physicalChatId)
    }

    @Test
    fun caseE_senderCorrect_matchesExpectedUser() {
        val payloadSender = "user-charlie"
        val roomOtherUser = "user-charlie"

        assertTrue(ChatRoutingResolver.isSenderConsistent(payloadSender, roomOtherUser))
    }

    @Test
    fun caseF_senderContradictory_detectsMismatchToPreventOpeningWrongChat() {
        val payloadSender = "user-attacker"
        val roomOtherUser = "user-victim"

        // When there is an explicit contradiction between payload sender and room otherUser, it must fail validation
        assertFalse(ChatRoutingResolver.isSenderConsistent(payloadSender, roomOtherUser))
    }

    @Test
    fun caseG_coldStartPayloadPreservation_extractsProperSenderAndThread() {
        val payload = mapOf(
            "thread_id" to "canonical-thread-456",
            "chat_id" to "phys-chat-789",
            "sender_id" to "user-daniela"
        )

        val chatId = ChatRoutingResolver.extractChatIdFromPayload(payload)
        val senderId = ChatRoutingResolver.extractSenderIdFromPayload(payload)

        assertEquals("canonical-thread-456", chatId)
        assertEquals("user-daniela", senderId)
    }

    @Test
    fun caseH_chatAlreadyActive_matchesCanonicalOrPhysicalIdWithoutDuplicating() {
        val canonicalThreadId = "canonical-thread-123"
        val physicalChatId = "phys-chat-999"

        // Active chat matching canonical threadId
        assertTrue(
            ChatRoutingResolver.isChatActiveAndMatching(
                isChatScreenActive = true,
                activeChatId = canonicalThreadId,
                canonicalChatId = canonicalThreadId,
                physicalChatId = physicalChatId
            )
        )

        // Active chat matching physical chatId
        assertTrue(
            ChatRoutingResolver.isChatActiveAndMatching(
                isChatScreenActive = true,
                activeChatId = physicalChatId,
                canonicalChatId = canonicalThreadId,
                physicalChatId = physicalChatId
            )
        )

        // Different chat active
        assertFalse(
            ChatRoutingResolver.isChatActiveAndMatching(
                isChatScreenActive = true,
                activeChatId = "other-thread-555",
                canonicalChatId = canonicalThreadId,
                physicalChatId = physicalChatId
            )
        )

        // Screen not active
        assertFalse(
            ChatRoutingResolver.isChatActiveAndMatching(
                isChatScreenActive = false,
                activeChatId = canonicalThreadId,
                canonicalChatId = canonicalThreadId,
                physicalChatId = physicalChatId
            )
        )
    }
}

