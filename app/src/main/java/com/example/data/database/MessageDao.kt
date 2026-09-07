package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM local_messages WHERE chatId = :chatId AND content LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchMessages(chatId: String, query: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM local_messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    fun getMessagesForChatFlow(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM local_messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    suspend fun getMessagesForChat(chatId: String): List<MessageEntity>

    @Query("SELECT * FROM local_messages WHERE chatId = :chatId AND (:oldestTimestamp IS NULL OR createdAt < :oldestTimestamp) ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getMessagesForChatPaged(chatId: String, limit: Int, oldestTimestamp: String?): List<MessageEntity>

    // Outgoing queue: only active transient states ('sending', 'pending', 'pending_media')
    // are eligible for automatic background sync retry.
    // 'failed' is a terminal state for the automatic sync cycle and must NOT be retried
    // automatically. It can only be revived back to 'sending' via an explicit manual retry.
    @Query("SELECT DISTINCT local_messages.* FROM local_messages LEFT JOIN local_chats ON local_messages.chatId = local_chats.id WHERE local_messages.status IN ('sending', 'pending', 'pending_media') AND (local_chats.id IS NULL OR local_chats.id = local_messages.chatId) ORDER BY local_messages.createdAt ASC")
    suspend fun getPendingMessages(): List<MessageEntity>

    @Query("SELECT DISTINCT chatId FROM local_messages")
    suspend fun getDistinctChatIds(): List<String>

    @Query("SELECT * FROM local_messages WHERE messageType = 'call' ORDER BY createdAt DESC LIMIT :limit")
    fun observeCallMessages(limit: Int = 200): Flow<List<MessageEntity>>

    @Query("SELECT * FROM local_messages WHERE messageType = 'call' ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getCallMessages(limit: Int = 200): List<MessageEntity>

    @Query("DELETE FROM local_messages WHERE messageType = 'call'")
    suspend fun clearCallHistory()

    @Query("SELECT * FROM local_messages WHERE clientMessageUuid = :uuid")
    suspend fun getMessagesByUuid(uuid: String): List<MessageEntity>

    @Query("SELECT * FROM local_messages WHERE clientMessageUuid = :uuid LIMIT 1")
    fun observeMessageByClientUuid(uuid: String): Flow<MessageEntity?>

    @Query("SELECT * FROM local_messages WHERE editPending = 1")
    suspend fun getEditPendingMessages(): List<MessageEntity>

    @Query("SELECT * FROM local_messages WHERE reactionPending = 1")
    suspend fun getReactionPendingMessages(): List<MessageEntity>

    @Query("SELECT * FROM local_messages WHERE deletePending = 1")
    suspend fun getDeletePendingMessages(): List<MessageEntity>

    @Query("SELECT * FROM local_messages WHERE id = :id")
    suspend fun getMessageById(id: String): MessageEntity?

    @Query("SELECT * FROM local_messages WHERE id IN (:ids) AND chatId = :chatId")
    suspend fun getMessagesByIds(chatId: String, ids: List<String>): List<MessageEntity>

    @Query("SELECT id FROM local_chats WHERE threadId = :threadId LIMIT 1")
    suspend fun getChatIdByThreadId(threadId: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessageRaw(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessagesRaw(messages: List<MessageEntity>)

    @Query("UPDATE local_chats SET lastMessageId = :lastMessageId, unreadCount = CASE WHEN :shouldIncrementUnread = 1 THEN unreadCount + 1 ELSE unreadCount END WHERE id = :chatId")
    suspend fun updateChatLastMessageAndUnread(chatId: String, lastMessageId: String, shouldIncrementUnread: Int)

    /** Clears the unread badge column for a chat (used when the chat is opened / marked read). */
    @Query("UPDATE local_chats SET unreadCount = 0 WHERE id = :chatId")
    suspend fun resetUnreadCountForChat(chatId: String)

    /** Recomputes the unread badge column for a chat from the actual unread message count. */
    @Query("UPDATE local_chats SET unreadCount = (SELECT COUNT(*) FROM local_messages WHERE chatId = :chatId AND senderId != :myUserId AND status != 'seen' AND seenAt IS NULL) WHERE id = :chatId")
    suspend fun recomputeUnreadCountForChat(chatId: String, myUserId: String)

    @Query("SELECT COUNT(*) FROM local_chats WHERE id = :chatId")
    suspend fun hasChat(chatId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChatPlaceholder(chat: ChatEntity)

    @Transaction
    suspend fun updateChatMetadataForMessage(
        message: MessageEntity,
        shouldIncrementUnreadOverride: Boolean? = null
    ) {
        val myUserId = try { com.example.data.supabase.SupabaseClient.currentUser?.id ?: "" } catch (e: Exception) { "" }
        val isChatActive = com.example.data.supabase.SupabaseClient.isChatScreenActive && com.example.data.supabase.SupabaseClient.activeChatId == message.chatId
        val shouldIncrementUnread = shouldIncrementUnreadOverride ?: (
            message.senderId != myUserId &&
                !isChatActive &&
                message.status != "seen" &&
                message.seenAt == null
            )

        if (hasChat(message.chatId) == 0) {
            // Cuando el mensaje es de otro, el "other user" es el sender. Cuando es
            // del usuario actual, es el receiver — pero NUNCA null: null crea un chat
            // fantasma sin nombre/foto que el usuario ve como eco "perfil desconocido".
            val threadId = getChatIdByThreadId(message.chatId)
            val otherUserId = if (message.senderId != myUserId) message.senderId else message.receiverId

            // UN SOLO hilo por pareja de usuarios: un mensaje DM (thread_messages) llega
            // con thread_id UID del hilo, pero el id local del chat puede estar cacheado con
            // otro id (thread.id del backend). Si Room ya tiene esa conversación por el
            // threadId o por el otherUserId, el mensaje se inserta en ESE chat y se
            // actualiza su metadata — jamás se crea un chat paralelo/duplicado fantasma..
            var existingChat: ChatEntity? = null
            if (threadId != null && threadId.isNotEmpty()) {
                existingChat = com.example.data.database.PanalinkDatabase.getDatabase(com.example.PanaApplication.instance).chatDao().getChatByThreadId(threadId)
            }
            if (existingChat == null && !otherUserId.isNullOrBlank()) {
                existingChat = com.example.data.database.PanalinkDatabase.getDatabase(com.example.PanaApplication.instance).chatDao().getChatByOtherUserId(otherUserId)
            }
            if (existingChat == null && !message.receiverId.isNullOrBlank() && message.senderId != myUserId) {

                // El sender es el otro usuario implicado por construcción del hilo, pero soporte
                // extra si el receiver apunta al mismo contacto (senderId/receiverId invertidos)..
                existingChat = com.example.data.database.PanalinkDatabase.getDatabase(com.example.PanaApplication.instance).chatDao().getChatByOtherUserId(message.receiverId)
            }
            if (existingChat != null) {
                // Reapuntar el mensaje al chat canónico y evitar el chat duplicado.

                val merged = message.copy(chatId = existingChat.id)
                insertMessageRaw(merged)
                updateChatLastMessageAndUnread(
                    existingChat.id,
                    merged.id,
                    if (shouldIncrementUnread) 1 else 0
                )
                return
            }

            val newChat = ChatEntity(
                id = message.chatId,
                createdAt = message.createdAt,
                type = "dm",
                otherUserId = otherUserId,
                lastMessageId = message.id,
                unreadCount = if (shouldIncrementUnread) 1 else 0
            )
            insertChatPlaceholder(newChat)
        } else {
            updateChatLastMessageAndUnread(
                message.chatId,
                message.id,
                if (shouldIncrementUnread) 1 else 0
            )
        }
    }

    private suspend fun normalizeChatIdentity(message: MessageEntity): MessageEntity {
        if (hasChat(message.chatId) > 0) return message
        if (!message.chatId.isNullOrBlank()) {
            val localChatId = getChatIdByThreadId(message.chatId)
            if (!localChatId.isNullOrBlank()) return message.copy(chatId = localChatId)
        }
        // Last resort: look up by otherUserId (receiverId for outgoing, senderId for incoming).
        // This prevents ghost chats when the threadId is not yet cached in Room but the
        // DM partner relationship already exists locally.
        val myUserId = try { com.example.data.supabase.SupabaseClient.currentUser?.id ?: "" } catch (e: Exception) { "" }
        val otherUserId = if (message.senderId != myUserId) message.senderId else message.receiverId
        if (!otherUserId.isNullOrBlank()) {
            val db = com.example.data.database.PanalinkDatabase.getDatabase(com.example.PanaApplication.instance)
            val existingChat = try { db.chatDao().getChatByOtherUserId(otherUserId) } catch (_: Exception) { null }
            if (existingChat != null) {
                return message.copy(chatId = existingChat.id)
            }
        }
        return message
    }

    @Transaction
    suspend fun insertMessage(message: MessageEntity) {
        val normalizedMessage = normalizeChatIdentity(message)
        val existingById = getMessageById(normalizedMessage.id)
        val existingByUuid = if (!normalizedMessage.clientMessageUuid.isNullOrBlank()) {
            getMessagesByUuid(normalizedMessage.clientMessageUuid!!).firstOrNull()
        } else {
            null
        }

        insertMessageRaw(normalizedMessage)

        // Only an incoming, never-seen message that arrived while the chat was
        // NOT on-screen should bump the unread badge. Outgoing messages (from
        // the current user) must NEVER increment unreadCount — that would mark
        // the user's own chat as having "new" messages. A duplicate (already
        // present by id or uuid) is an update, not a new arrival either.
        val isNew = existingById == null && existingByUuid == null
        val isOutgoing = normalizedMessage.senderId == try {
            com.example.data.supabase.SupabaseClient.currentUser?.id ?: ""
        } catch (e: Exception) { "" }
        val shouldIncrementUnread = isNew && !isOutgoing
        updateChatMetadataForMessage(normalizedMessage, shouldIncrementUnread)
    }

    @Transaction
    suspend fun insertMessages(messages: List<MessageEntity>) {
        messages.forEach { message ->
            val normalizedMessage = normalizeChatIdentity(message)
            val existingById = getMessageById(normalizedMessage.id)
            val existingByUuid = if (!normalizedMessage.clientMessageUuid.isNullOrBlank()) {
                getMessagesByUuid(normalizedMessage.clientMessageUuid!!).firstOrNull()
            } else {
                null
            }

            insertMessageRaw(normalizedMessage)

            val isNew = existingById == null && existingByUuid == null
            val isOutgoing = normalizedMessage.senderId == try {
                com.example.data.supabase.SupabaseClient.currentUser?.id ?: ""
            } catch (e: Exception) { "" }
            val shouldIncrementUnread = isNew && !isOutgoing
            updateChatMetadataForMessage(normalizedMessage, shouldIncrementUnread)
        }
    }

    @Query("DELETE FROM local_messages WHERE id = :id")
    suspend fun deleteMessageById(id: String)

    @Query("DELETE FROM local_messages WHERE clientMessageUuid = :uuid AND id LIKE 'temp_%'")
    suspend fun deleteTemporaryMessageByUuid(uuid: String)

    fun isTimestampBeforeOrEqual(ts1: String?, ts2: String?): Boolean {
        if (ts1 == null) return true
        if (ts2 == null) return false
        return ts1 <= ts2
    }

    fun mergeReactions(localJson: String?, remoteJson: String?): String {
        return when {
            !remoteJson.isNullOrEmpty() -> remoteJson
            !localJson.isNullOrEmpty() -> localJson
            else -> ""
        }
    }

    fun mergeEntities(local: MessageEntity, remote: MessageEntity): MessageEntity {
        val mergedUpdatedAt = if (isTimestampBeforeOrEqual(local.updatedAt, remote.updatedAt)) {
            remote.updatedAt
        } else {
            local.updatedAt
        }

        // Message status is monotonic. A stale realtime/HTTP response must
        // never move a confirmed message back to "sending" or "pending".
        // A stale failure must not overwrite a confirmed successful send.
        val localStatus = local.status?.lowercase()
        val remoteStatus = remote.status?.lowercase()
        val successfulStatuses = setOf("sent", "delivered", "seen")
        val transientStatuses = setOf("sending", "pending")
        val finalStatus = when {
            local.deletePending -> "deleted"
            local.editPending -> local.status
            remoteStatus in successfulStatuses && localStatus in successfulStatuses -> {
                when {
                    localStatus == "seen" || remoteStatus == "seen" -> "seen"
                    localStatus == "delivered" || remoteStatus == "delivered" -> "delivered"
                    else -> "sent"
                }
            }
            localStatus in successfulStatuses && remoteStatus !in successfulStatuses -> local.status
            remoteStatus in successfulStatuses -> remote.status
            localStatus == "failed" && remoteStatus in transientStatuses -> local.status
            remoteStatus == "failed" && localStatus in transientStatuses -> remote.status
            else -> remote.status ?: local.status
        }

        // Un evento remoto con contenido vacío (broadcast parcial, fila legacy
        // sin text_content) nunca debe borrar el texto ya conocido localmente.
        val finalContent = if (local.editPending) {
            local.content
        } else {
            remote.content?.takeIf { it.isNotEmpty() } ?: local.content
        }

        val finalDeletedAt = if (local.deletePending) {
            local.deletedAt ?: remote.deletedAt
        } else {
            remote.deletedAt ?: local.deletedAt
        }

        val finalReactionsJson = if (local.reactionPending) {
            local.reactionsJson
        } else {
            mergeReactions(local.reactionsJson, remote.reactionsJson)
        }

        return remote.copy(
            receiverId = remote.receiverId ?: local.receiverId,
            content = finalContent,
            status = finalStatus,
            deletedAt = finalDeletedAt,
            reactionsJson = finalReactionsJson,
            deliveredAt = remote.deliveredAt ?: local.deliveredAt,
            seenAt = remote.seenAt ?: local.seenAt,
            thumbnailUrl = remote.thumbnailUrl ?: local.thumbnailUrl,
            mediaUrl = remote.mediaUrl ?: local.mediaUrl,
            mediaMime = remote.mediaMime ?: local.mediaMime,
            mediaSize = remote.mediaSize ?: local.mediaSize,
            mediaDuration = remote.mediaDuration ?: local.mediaDuration,
            mediaWidth = remote.mediaWidth ?: local.mediaWidth,
            mediaHeight = remote.mediaHeight ?: local.mediaHeight,
            messageType = remote.messageType ?: local.messageType,
            updatedAt = mergedUpdatedAt,
            localMediaUri = local.localMediaUri ?: remote.localMediaUri,
            localThumbnailUri = local.localThumbnailUri ?: remote.localThumbnailUri,
            isFavorited = if (local.editPending || local.reactionPending) {
                local.isFavorited
            } else {
                remote.isFavorited
            },
            clientMessageUuid = remote.clientMessageUuid ?: local.clientMessageUuid,
            editPending = local.editPending,
            reactionPending = local.reactionPending,
            deletePending = local.deletePending,
            ghostOpenedAt = local.ghostOpenedAt ?: remote.ghostOpenedAt,
            musicPlaylistId = remote.musicPlaylistId ?: local.musicPlaylistId
        )
    }

    @Transaction
    suspend fun insertOrMergeMessage(remote: MessageEntity) {
        mergeAndSaveMessage(remote)
    }

    @Transaction
    suspend fun insertOrMergeMessages(remoteList: List<MessageEntity>) {
        remoteList.forEach { remote ->
            mergeAndSaveMessage(remote, allowUnreadIncrement = false)
        }
    }

    @Transaction
    suspend fun mergeAndSaveMessage(
        remote: MessageEntity,
        allowUnreadIncrement: Boolean = true
    ) {
        val normalizedRemote = normalizeChatIdentity(remote)
        val uuid = normalizedRemote.clientMessageUuid
        var localByUuid: MessageEntity? = null
        if (!uuid.isNullOrBlank()) {
            val found = getMessagesByUuid(uuid)
            localByUuid = found.firstOrNull()
            deleteTemporaryMessageByUuid(uuid)
        }
        val localById = getMessageById(normalizedRemote.id)
        val local = localById ?: localByUuid
        if (local != null) {
            val merged = mergeEntities(local, normalizedRemote)
            insertMessage(merged)
        } else if (allowUnreadIncrement) {
            insertMessage(normalizedRemote)
        } else {
            insertMessageRaw(normalizedRemote)
            updateChatMetadataForMessage(normalizedRemote, false)
        }
    }

    @Transaction
    suspend fun replaceMessageByUuid(finalEntity: MessageEntity) {
        val normalizedFinal = normalizeChatIdentity(finalEntity)
        val uuid = normalizedFinal.clientMessageUuid
        var localByUuid: MessageEntity? = null
        if (!uuid.isNullOrBlank()) {
            val found = getMessagesByUuid(uuid)
            localByUuid = found.firstOrNull()
            deleteTemporaryMessageByUuid(uuid)
        }
        val localById = getMessageById(normalizedFinal.id)
        val local = localById ?: localByUuid
        if (local != null) {
            val merged = mergeEntities(local, normalizedFinal)
            insertMessage(merged)
        } else {
            insertMessage(normalizedFinal)
        }
    }

    @Transaction
    suspend fun replaceTemporaryMessage(tempId: String, finalEntity: MessageEntity) {
        val normalizedFinal = normalizeChatIdentity(finalEntity)
        val uuid = normalizedFinal.clientMessageUuid
        val localTempByUuid = if (!uuid.isNullOrBlank()) getMessagesByUuid(uuid).firstOrNull() else null
        val localTempById = getMessageById(tempId)
        val localTemp = localTempById ?: localTempByUuid

        if (!uuid.isNullOrBlank()) {
            deleteTemporaryMessageByUuid(uuid)
        }
        deleteMessageById(tempId)

        val localById = getMessageById(normalizedFinal.id)
        val baseLocal = localById ?: localTemp

        if (baseLocal != null) {
            val merged = mergeEntities(baseLocal, normalizedFinal)
            insertMessage(merged)
        } else {
            insertMessage(normalizedFinal)
        }
    }

    @Query("UPDATE local_messages SET status = :status WHERE id = :id")
    suspend fun updateMessageStatus(id: String, status: String)

    @Query("UPDATE local_messages SET status = :status, content = :content WHERE id = :id")
    suspend fun updateMessageStatusAndContent(id: String, status: String, content: String)

    @Query("UPDATE local_messages SET status = :status, deliveredAt = :deliveredAt WHERE id = :id")
    suspend fun updateMessageDelivered(id: String, deliveredAt: String, status: String)

    @Query("UPDATE local_messages SET status = 'seen', seenAt = :seenAt WHERE id = :id")
    suspend fun updateMessageSeen(id: String, seenAt: String)

    @Query("UPDATE local_messages SET reactionsJson = :reactionsJson WHERE id = :id")
    suspend fun updateMessageReactions(id: String, reactionsJson: String)

    @Query("UPDATE local_messages SET status = 'seen', seenAt = :seenAt WHERE chatId = :chatId AND senderId != :myUserId AND status != 'seen'")
    suspend fun markChatMessagesAsRead(chatId: String, myUserId: String, seenAt: String)

    @Query("UPDATE local_messages SET status = CASE WHEN status = 'seen' THEN status ELSE 'delivered' END, deliveredAt = COALESCE(deliveredAt, :deliveredAt) WHERE chatId = :chatId AND senderId != :myUserId AND deliveredAt IS NULL")
    suspend fun markChatMessagesAsDelivered(chatId: String, myUserId: String, deliveredAt: String)

    @Query("UPDATE local_messages SET status = 'seen', seenAt = :seenAt, deliveredAt = COALESCE(deliveredAt, :seenAt) WHERE chatId = :chatId AND senderId != :myUserId AND createdAt <= :watermark AND status != 'seen'")
    suspend fun markChatMessagesAsReadThrough(chatId: String, myUserId: String, watermark: String, seenAt: String)

    @Query("UPDATE local_messages SET isFavorited = :isFavorited WHERE id = :id")
    suspend fun updateMessageFavoriteStatus(id: String, isFavorited: Boolean)

    @Query("SELECT * FROM local_messages WHERE isFavorited = 1 ORDER BY createdAt DESC")
    fun getFavoritedMessagesFlow(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM local_messages WHERE isFavorited = 1 ORDER BY createdAt DESC")
    suspend fun getFavoritedMessages(): List<MessageEntity>

    @Query("DELETE FROM local_messages WHERE chatId = :chatId")
    suspend fun clearChatMessages(chatId: String)

    @Query("UPDATE local_messages SET content = :content, isEdited = 1 WHERE id = :id")
    suspend fun updateMessageContent(id: String, content: String)

    @Query("UPDATE local_messages SET content = :content, isEdited = 1, editPending = 1 WHERE id = :id")
    suspend fun markMessageEditPending(id: String, content: String)

    @Query("UPDATE local_messages SET editPending = 0 WHERE id = :id")
    suspend fun clearMessageEditPending(id: String)

    @Query("UPDATE local_messages SET reactionPending = 1, reactionsJson = :reactionsJson WHERE id = :id")
    suspend fun markMessageReactionPending(id: String, reactionsJson: String)

    @Query("UPDATE local_messages SET reactionPending = 0 WHERE id = :id")
    suspend fun clearMessageReactionPending(id: String)

    @Query("UPDATE local_messages SET status = 'deleted', deletedAt = :deletedAt, deletePending = 1 WHERE id = :id")
    suspend fun markMessageDeletePending(id: String, deletedAt: String)

    @Query("UPDATE local_messages SET deletePending = 0 WHERE id = :id")
    suspend fun clearMessageDeletePending(id: String)

    @Query("UPDATE local_messages SET ghostOpenedAt = :openedAt WHERE id = :id")
    suspend fun updateGhostOpenedAt(id: String, openedAt: String)

    @Query("UPDATE local_messages SET receiverId = :receiverId WHERE id = :id")
    suspend fun updateMessageReceiverId(id: String, receiverId: String)

    @Query("SELECT * FROM local_messages WHERE chatId = :chatId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLastMessageForChat(chatId: String): MessageEntity?

    @Query("SELECT COUNT(*) FROM local_messages WHERE chatId = :chatId AND senderId != :myUserId AND status != 'seen' AND seenAt IS NULL")
    suspend fun getUnreadCountForChat(chatId: String, myUserId: String): Int

    @Query("SELECT createdAt FROM local_messages WHERE chatId = :chatId ORDER BY createdAt ASC LIMIT 1")
    suspend fun getOldestMessageTimestamp(chatId: String): String?

    @Query("SELECT createdAt FROM local_messages WHERE chatId = :chatId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getNewestMessageTimestamp(chatId: String): String?
}
