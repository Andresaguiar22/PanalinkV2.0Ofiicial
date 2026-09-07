package com.example

import com.example.data.database.MessageEntity
import com.example.data.database.MessageDao
import com.example.data.model.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class RealtimeSoftDeleteTest {

    private val fakeDao = object : MessageDao {
        override fun searchMessages(chatId: String, query: String) = throw NotImplementedError()
        override fun getMessagesForChatFlow(chatId: String) = throw NotImplementedError()
        override suspend fun getMessagesForChat(chatId: String) = throw NotImplementedError()
        override suspend fun getMessagesForChatPaged(chatId: String, limit: Int, oldestTimestamp: String?) = throw NotImplementedError()
        override suspend fun getPendingMessages() = throw NotImplementedError()
        override suspend fun getDistinctChatIds() = throw NotImplementedError()
        override fun observeCallMessages(limit: Int): kotlinx.coroutines.flow.Flow<List<MessageEntity>> = throw NotImplementedError()
        override suspend fun getCallMessages(limit: Int) = throw NotImplementedError()
        override suspend fun clearCallHistory() = throw NotImplementedError()
        override suspend fun getEditPendingMessages() = throw NotImplementedError()
        override suspend fun getReactionPendingMessages() = throw NotImplementedError()
        override suspend fun getDeletePendingMessages() = throw NotImplementedError()
        override suspend fun getMessagesByUuid(uuid: String) = throw NotImplementedError()
        override fun observeMessageByClientUuid(uuid: String) = throw NotImplementedError()
        override suspend fun getMessageById(id: String) = throw NotImplementedError()
        override suspend fun getMessagesByIds(chatId: String, ids: List<String>): List<MessageEntity> = throw NotImplementedError()
        override suspend fun getChatIdByThreadId(threadId: String) = throw NotImplementedError()
        override suspend fun insertMessage(message: MessageEntity) = throw NotImplementedError()
        override suspend fun insertMessages(messages: List<MessageEntity>) = throw NotImplementedError()
        override suspend fun deleteMessageById(id: String) = throw NotImplementedError()
        override suspend fun deleteTemporaryMessageByUuid(uuid: String) = throw NotImplementedError()
        override suspend fun updateMessageStatus(id: String, status: String) = throw NotImplementedError()
        override suspend fun updateMessageStatusAndContent(id: String, status: String, content: String) = throw NotImplementedError()
        override suspend fun updateMessageDelivered(id: String, deliveredAt: String, status: String) = throw NotImplementedError()
        override suspend fun updateMessageSeen(id: String, seenAt: String) = throw NotImplementedError()
        override suspend fun updateMessageReactions(id: String, reactionsJson: String) = throw NotImplementedError()
        override suspend fun markChatMessagesAsRead(chatId: String, myUserId: String, seenAt: String) = throw NotImplementedError()
        override suspend fun markChatMessagesAsDelivered(chatId: String, myUserId: String, deliveredAt: String) = throw NotImplementedError()
        override suspend fun markChatMessagesAsReadThrough(chatId: String, myUserId: String, watermark: String, seenAt: String) = throw NotImplementedError()
        override suspend fun updateMessageFavoriteStatus(id: String, isFavorited: Boolean) = throw NotImplementedError()
        override fun getFavoritedMessagesFlow() = throw NotImplementedError()
        override suspend fun getFavoritedMessages() = throw NotImplementedError()
        override suspend fun clearChatMessages(chatId: String) = throw NotImplementedError()
        override suspend fun updateMessageContent(id: String, content: String) = throw NotImplementedError()
        override suspend fun markMessageEditPending(id: String, content: String) = throw NotImplementedError()
        override suspend fun clearMessageEditPending(id: String) = throw NotImplementedError()
        override suspend fun markMessageReactionPending(id: String, reactionsJson: String) = throw NotImplementedError()
        override suspend fun clearMessageReactionPending(id: String) = throw NotImplementedError()
        override suspend fun markMessageDeletePending(id: String, deletedAt: String) = throw NotImplementedError()
        override suspend fun clearMessageDeletePending(id: String) = throw NotImplementedError()
        override suspend fun updateGhostOpenedAt(id: String, openedAt: String) = throw NotImplementedError()
        override suspend fun updateMessageReceiverId(id: String, receiverId: String) = throw NotImplementedError()
        override suspend fun getLastMessageForChat(chatId: String) = throw NotImplementedError()
        override suspend fun getUnreadCountForChat(chatId: String, myUserId: String) = throw NotImplementedError()
        override suspend fun getOldestMessageTimestamp(chatId: String) = throw NotImplementedError()
        override suspend fun getNewestMessageTimestamp(chatId: String) = throw NotImplementedError()
        override suspend fun insertMessageRaw(message: MessageEntity) = throw NotImplementedError()
        override suspend fun insertMessagesRaw(messages: List<MessageEntity>) = throw NotImplementedError()
        override suspend fun updateChatLastMessageAndUnread(chatId: String, lastMessageId: String, shouldIncrementUnread: Int) = throw NotImplementedError()
        override suspend fun resetUnreadCountForChat(chatId: String) = throw NotImplementedError()
        override suspend fun recomputeUnreadCountForChat(chatId: String, myUserId: String) = throw NotImplementedError()
        override suspend fun hasChat(chatId: String) = throw NotImplementedError()
        override suspend fun insertChatPlaceholder(chat: com.example.data.database.ChatEntity) = throw NotImplementedError()
    }

    @Test
    fun realtimeUpdateDeletedAtSurvivesMerge() {
        val local = MessageEntity(
            id = "m1",
            chatId = "c1",
            senderId = "u1",
            content = "Original content",
            status = "sent",
            createdAt = "2026-08-01T12:00:00Z",
            deliveredAt = "2026-08-01T12:00:05Z",
            seenAt = "2026-08-01T12:00:10Z"
        )
        val remote = MessageEntity(
            id = "m1",
            chatId = "c1",
            senderId = "u1",
            content = "Original content",
            status = "deleted",
            createdAt = "2026-08-01T12:00:00Z",
            deliveredAt = "2026-08-01T12:00:05Z",
            seenAt = "2026-08-01T12:00:10Z",
            deletedAt = "2026-08-01T12:30:00Z",
            updatedAt = "2026-08-01T12:30:00Z"
        )
        val merged = fakeDao.mergeEntities(local, remote)
        assertEquals("2026-08-01T12:30:00Z", merged.deletedAt)

        // Regla del guard de UI evaluada contra el resultado del merge:
        assertTrue(!merged.deletedAt.isNullOrEmpty() || merged.status == "deleted")
    }

    @Test
    fun realtimeUpdateIsEditedSurvivesMerge() {
        val local = MessageEntity(
            id = "m2",
            chatId = "c1",
            senderId = "u1",
            content = "Old content",
            status = "sent",
            createdAt = "2026-08-01T12:00:00Z",
            isEdited = false
        )
        val remote = MessageEntity(
            id = "m2",
            chatId = "c1",
            senderId = "u1",
            content = "Edited content",
            status = "sent",
            createdAt = "2026-08-01T12:00:00Z",
            isEdited = true,
            updatedAt = "2026-08-01T12:10:00Z"
        )
        val merged = fakeDao.mergeEntities(local, remote)
        assertEquals("Edited content", merged.content)
        assertTrue(merged.isEdited)
    }

    @Test
    fun softDeletedGuardRules() {
        val normal = Message(
            id = "m3",
            chatId = "c1",
            senderId = "u1",
            content = "heya",
            createdAt = "2026-08-01T12:00:00Z",
            status = "sent"
        )
        assertFalse(com.example.ui.components.chat.bubble.isSoftDeleted(normal))

        val deletedByAt = normal.copy(deletedAt = "2026-08-01T12:30:00Z")
        assertTrue(com.example.ui.components.chat.bubble.isSoftDeleted(deletedByAt))

        val deletedByStatus = normal.copy(status = "deleted")
        assertTrue(com.example.ui.components.chat.bubble.isSoftDeleted(deletedByStatus))

        val live = Message(
            id = "m4",
            chatId = "c1",
            senderId = "u1",
            content = "live",
            createdAt = "2026-08-01T12:00:00Z",
            status = "seen",
            deletedAt = null,
            seenAt = "2026-08-01T12:00:05Z"
        )
        assertFalse(com.example.ui.components.chat.bubble.isSoftDeleted(live))
    }
}