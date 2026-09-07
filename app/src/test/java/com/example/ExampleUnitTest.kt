package com.example

import com.example.data.database.MessageEntity
import com.example.data.database.MessageDao
import com.example.data.model.Message
import com.example.service.NotificationDeduplicator
import com.example.ui.components.chat.state.normalizeVisibleMessageKey
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class ExampleUnitTest {

    // Helper fake DAO to test pure/merging logic without DB runner
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
    fun testVisibleMessageKeyNormalizationStripsIndexSuffix() {
        assertEquals("msg_abc", normalizeVisibleMessageKey("msg_abc_0"))
        assertEquals("msg-uuid-123", normalizeVisibleMessageKey("msg-uuid-123"))
        assertNull(normalizeVisibleMessageKey("temp_123"))
        assertNull(normalizeVisibleMessageKey("typing_indicator"))
    }

    @Test
    fun testNotificationDeduplicatorSuppressesSameMessageAcrossUuidAndId() {
        NotificationDeduplicator.clearForTest()
        assertTrue(NotificationDeduplicator.shouldNotifyMessage("client-1", "msg-1"))
        assertFalse(NotificationDeduplicator.shouldNotifyMessage("client-1", "msg-2"))
        assertFalse(NotificationDeduplicator.shouldNotifyMessage("client-2", "msg-1"))
    }

    @Test
    fun testOfflineEditMessageMergeStrategy() {
        // Local state has editPending = true and modified content
        val local = MessageEntity(
            id = "msg1",
            chatId = "chat1",
            senderId = "user1",
            content = "Hello Local (Edited)",
            status = "sent",
            createdAt = "2026-08-01T12:00:00Z",
            editPending = true
        )

        // Remote comes with older/different text but newer updatedAt
        val remote = MessageEntity(
            id = "msg1",
            chatId = "chat1",
            senderId = "user1",
            content = "Hello Original",
            status = "sent",
            createdAt = "2026-08-01T12:00:00Z",
            updatedAt = "2026-08-01T12:05:00Z"
        )

        val merged = fakeDao.mergeEntities(local, remote)

        // Content must be preserved as local, because editPending is true
        assertEquals("Hello Local (Edited)", merged.content)
        assertTrue(merged.editPending)
        assertEquals("2026-08-01T12:05:00Z", merged.updatedAt)
    }

    @Test
    fun testOfflineDeleteMessageMergeStrategy() {
        // Local has deletePending = true
        val local = MessageEntity(
            id = "msg2",
            chatId = "chat1",
            senderId = "user1",
            content = "To Be Deleted",
            status = "deleted",
            createdAt = "2026-08-01T12:00:00Z",
            deletedAt = "2026-08-01T12:02:00Z",
            deletePending = true
        )

        val remote = MessageEntity(
            id = "msg2",
            chatId = "chat1",
            senderId = "user1",
            content = "To Be Deleted",
            status = "sent",
            createdAt = "2026-08-01T12:00:00Z",
            updatedAt = "2026-08-01T12:01:00Z"
        )

        val merged = fakeDao.mergeEntities(local, remote)

        // Status must remain deleted
        assertEquals("deleted", merged.status)
        assertEquals("2026-08-01T12:02:00Z", merged.deletedAt)
        assertTrue(merged.deletePending)
    }

    @Test
    fun testOfflineReactionMergeStrategy() {
        val local = MessageEntity(
            id = "msg3",
            chatId = "chat1",
            senderId = "user1",
            content = "Cool message",
            status = "sent",
            createdAt = "2026-08-01T12:00:00Z",
            reactionsJson = "{\"user_me\":\"👍\"}",
            reactionPending = true
        )

        val remote = MessageEntity(
            id = "msg3",
            chatId = "chat1",
            senderId = "user1",
            content = "Cool message",
            status = "sent",
            createdAt = "2026-08-01T12:00:00Z",
            reactionsJson = "{\"user_other\":\"❤️\"}"
        )

        val merged = fakeDao.mergeEntities(local, remote)

        // Con reaccion local pendiente, el JSON local gana integro hasta que
        // el worker lo suba; lo remoto llega en la siguiente sincronizacion.
        val reactionsObj = JSONObject(merged.reactionsJson)
        assertEquals("👍", reactionsObj.getString("user_me"))
        assertTrue(merged.reactionPending)
    }

}
