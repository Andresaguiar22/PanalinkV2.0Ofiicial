package com.example.data.repository

import android.util.Log
import com.example.PanaApplication
import com.example.data.database.MessageEntity
import com.example.data.database.PanalinkDatabase
import com.example.data.model.*
import com.example.data.repository.messages.CallHistoryDataSource
import com.example.data.repository.messages.FavoritesDataSource
import com.example.data.repository.messages.MessageRealtimeHandler
import com.example.data.repository.messages.MessageRepairService
import com.example.data.repository.messages.ReactionsDataSource
import com.example.data.repository.messages.ReactionSyncDataSource
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow

import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.util.UUID

import com.example.data.supabase.SessionManager

import androidx.work.*
import com.example.worker.SyncMessagesWorker
import com.example.worker.MediaUploadWorker
import com.example.util.PanalinkMediaManager
import androidx.work.Data

class MessagesRepository private constructor() {

    private val TAG = "MessagesRepository"

    private fun isTransientHttpStatus(code: Int?): Boolean {
        return code == null || code == 0 || code == 408 || code == 409 || code == 425 || code == 429 || code >= 500
    }

    private fun isTransientException(error: Throwable): Boolean {
        return error is java.io.IOException ||
            error is java.net.SocketTimeoutException ||
            error is java.net.ConnectException ||
            error is java.net.UnknownHostException ||
            ((error as? retrofit2.HttpException)?.code()?.let(::isTransientHttpStatus) == true)
    }
    private val db = PanalinkDatabase.getDatabase(PanaApplication.instance)
    private val messageDao = db.messageDao()
    private val repositoryScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private val realtimeHandler = MessageRealtimeHandler(
        messageDao,
        repositoryScope,
        { getUserDeletedMessageIds() },
        { chatId, clearedAt -> getEffectiveClearedAt(chatId, clearedAt) },
        { msg -> messageRepairService.repairMessageContentIfNeeded(msg) }
    )
    private val userDeletedMessageIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val callHistoryDataSource = CallHistoryDataSource()
    private val reactionsDataSource = ReactionsDataSource()
    private val reactionSyncDataSource = ReactionSyncDataSource()
    private val favoritesDataSource = FavoritesDataSource()
    private val messageRepairService = MessageRepairService()

    fun getUserDeletedMessageIds(): Set<String> = userDeletedMessageIds

    fun getEffectiveClearedAt(chatId: String, remoteClearedAt: String?): String? {
        val localCleared = localClearedAtMap[chatId]
        return when {
            remoteClearedAt != null && localCleared != null -> {
                if (isTimestampBeforeOrEqual(remoteClearedAt, localCleared)) localCleared else remoteClearedAt
            }
            remoteClearedAt != null -> remoteClearedAt
            else -> localCleared
        }
    }

    /* Realtime logic moved to MessageRealtimeHandler */

    /* repairMessageContentIfNeeded moved to MessageRepairService */


    val localClearedAtMap = java.util.concurrent.ConcurrentHashMap<String, String>()
    val lastSyncTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val confirmedReadWatermarks = java.util.concurrent.ConcurrentHashMap<String, String>()



    fun observeCallHistory(): kotlinx.coroutines.flow.Flow<List<com.example.data.model.CallLog>> =
            callHistoryDataSource.observeCallHistory()

    fun clearCallHistory() = callHistoryDataSource.clearCallHistory()

    fun deleteCallLog(messageId: String) = callHistoryDataSource.deleteCallLog(messageId)
    companion object {
        @Volatile
        private var instance: MessagesRepository? = null

        fun getInstance(): MessagesRepository {
            return instance ?: synchronized(this) {
                instance ?: MessagesRepository().also { instance = it }
            }
        }

        fun isValidUuid(uuidStr: String?): Boolean {
            if (uuidStr.isNullOrEmpty()) return false
            return try {
                java.util.UUID.fromString(uuidStr)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    enum class ChatKind {
        DM,
        CHANNEL,
        LEGACY,
        UNKNOWN
    }

    data class CanonicalChatIdentity(
        val kind: ChatKind,
        val chatId: String,
        val threadId: String? = null,
        val receiverId: String? = null
    )

    suspend fun resolveChatIdentity(chatId: String, receiverHint: String? = null): CanonicalChatIdentity = withContext(Dispatchers.IO) {
        val currentUid = try { SupabaseClient.currentUser?.id } catch (e: Throwable) { null }
        val db = PanalinkDatabase.getDatabase(PanaApplication.instance)
        val chatEntity = try { db.chatDao().getChatById(chatId) } catch (e: Exception) { null }

        // Local check for Channel or Legacy
        if (chatEntity?.type == "channel" || chatEntity?.type == "group") {
            return@withContext CanonicalChatIdentity(kind = ChatKind.CHANNEL, chatId = chatId)
        }
        if (chatEntity?.type == "legacy") {
            return@withContext CanonicalChatIdentity(kind = ChatKind.LEGACY, chatId = chatId)
        }

        val targetReceiverId = receiverHint?.takeIf { isValidUuid(it) && it != currentUid }
            ?: chatEntity?.otherUserId?.takeIf { isValidUuid(it) && it != currentUid }

        // Caso A: Room tiene DM + threadId explícito canónico y válido
        if (chatEntity?.type == "dm" && !chatEntity.threadId.isNullOrEmpty() && isValidUuid(chatEntity.threadId)) {
            val receiverId = targetReceiverId ?: chatEntity.otherUserId
            return@withContext CanonicalChatIdentity(
                kind = ChatKind.DM,
                chatId = chatId,
                threadId = chatEntity.threadId,
                receiverId = receiverId
            )
        }

        // Caso B y C: Consultar one_to_one_threads en Supabase para obtener el thread.id canónico
        val service = SupabaseClient.apiService
        if (service != null && !currentUid.isNullOrEmpty() && SupabaseClient.isConfigured) {
            try {
                val orFilter = if (!targetReceiverId.isNullOrEmpty()) {
                    "(id.eq.$chatId,and(user_a.eq.$currentUid,user_b.eq.$targetReceiverId),and(user_a.eq.$targetReceiverId,user_b.eq.$currentUid))"
                } else {
                    "(id.eq.$chatId)"
                }

                val threadResponse = runCall { auth ->
                    service.getOneToOneThreads(
                        apiKey = SupabaseClient.supabaseAnonKey,
                        authorization = auth,
                        orFilter = orFilter
                    )
                }

                if (threadResponse != null && threadResponse.isSuccessful) {
                    val threads = threadResponse.body()
                    if (!threads.isNullOrEmpty()) {
                        val thread = threads[0]
                        val derivedReceiver = if (thread.userA == currentUid) thread.userB else thread.userA

                        try {
                            val existingChat = chatEntity ?: com.example.data.database.ChatEntity(
                                id = chatId,
                                createdAt = thread.createdAt ?: SupabaseClient.getNowIsoString(),
                                type = "dm",
                                name = "Chat"
                            )
                            val newChatEntity = existingChat.copy(
                                type = "dm",
                                otherUserId = derivedReceiver,
                                threadId = thread.id
                            )
                            db.chatDao().insertChat(newChatEntity)
                        } catch (_: Exception) {}

                        // Caso C: Supabase confirma el thread -> Usar thread.id
                        return@withContext CanonicalChatIdentity(
                            kind = ChatKind.DM,
                            chatId = chatId,
                            threadId = thread.id,
                            receiverId = derivedReceiver
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving canonical identity for $chatId", e)
            }
        }

        // Caso D: No se puede resolver ni en Room ni en Supabase -> Devolver UNKNOWN y NUNCA inventar el threadId
        return@withContext CanonicalChatIdentity(kind = ChatKind.UNKNOWN, chatId = chatId)
    }

    private suspend fun <R> runCall(call: suspend (String) -> retrofit2.Response<R>): retrofit2.Response<R>? =
        com.example.data.repository.messages.runMessagesCall(TAG, call)
    /**
     * Contexto compartido de historial/sync: resuelve el last_cleared_at efectivo
     * (remoto + local merge) y el conjunto de IDs borrados para el usuario actual.
     * Usado por getMessagesForChatPaged y syncUpdatedMessages, que antes
     * duplicaban este bloque de ~40 lineas. La semantica es identica.
     */
    private suspend fun fetchClearedAndDeletedContext(
        chatId: String,
        service: com.example.data.supabase.SupabaseApiService
    ): Pair<String?, Set<String>> {
        var lastClearedAt: String? = null
        val currentUid = SupabaseClient.currentUser?.id
        val userDeletedIds = mutableSetOf<String>()
        userDeletedIds.addAll(userDeletedMessageIds)

        if (currentUid != null) {
            val participantRes = runCall { authHeader ->
                service.getChatParticipant(
                    apiKey = SupabaseClient.supabaseAnonKey,
                    authorization = authHeader,
                    chatIdFilter = "eq.$chatId",
                    userIdFilter = "eq.$currentUid"
                )
            }
            if (participantRes?.isSuccessful == true && !participantRes.body().isNullOrEmpty()) {
                lastClearedAt = participantRes.body()!![0].lastClearedAt
            }

            val localCleared = localClearedAtMap[chatId]
            if (localCleared != null) {
                if (lastClearedAt == null || isTimestampBeforeOrEqual(lastClearedAt, localCleared)) {
                    lastClearedAt = localCleared
                }
            }

            // Fetch messages deleted specifically for current user from user_deleted_messages
            val deletedRes = runCall { authHeader ->
                service.getUserDeletedMessages(
                    apiKey = SupabaseClient.supabaseAnonKey,
                    authorization = authHeader,
                    userIdFilter = "eq.$currentUid"
                )
            }
            if (deletedRes?.isSuccessful == true) {
                deletedRes.body()?.forEach { item ->
                    val msgId = item["message_id"] as? String
                    if (!msgId.isNullOrEmpty()) {
                        userDeletedIds.add(msgId)
                        userDeletedMessageIds.add(msgId)
                    }
                }
            }
        }

        return Pair(lastClearedAt, userDeletedIds)
    }


    suspend fun getMessagesForChatPaged(
        chatId: String,
        limit: Int = 50,
        oldestTimestamp: String? = null,
        newestTimestamp: String? = null
    ): Result<List<Message>> = withContext(Dispatchers.IO) {
        // Pre-warm chat-to-user cache and other user's public key
        try {
            val db = com.example.data.database.PanalinkDatabase.getDatabase(com.example.PanaApplication.instance)
            val chatEntity = db.chatDao().getChatById(chatId)
            val otherUserId = chatEntity?.otherUserId
            if (!otherUserId.isNullOrEmpty()) {
                com.example.util.CryptoManager.chatToOtherUserCache[chatId] = otherUserId
                if (!com.example.util.CryptoManager.publicKeyCache.containsKey(otherUserId)) {
                    UserKeysRepository.getPublicKeyForUser(otherUserId)
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error pre-warming E2EE key cache", e)
        }

        val cachedEntities = messageDao.getMessagesForChatPaged(chatId, limit, oldestTimestamp)
        var messagesList = cachedEntities.map { it.toMessage() }.stableSortedByCreatedAt()

        val lastSync = lastSyncTimestamps[chatId] ?: 0L
        val now = System.currentTimeMillis()
        if (oldestTimestamp == null && newestTimestamp == null && now - lastSync < 45000L) { // 45 seconds smart sync threshold only on initial fetch
            Log.d(TAG, "getMessagesForChatPaged: Chat $chatId was synced recently (${now - lastSync} ms ago). Skipping remote HTTP request and returning local messages directly.")
            return@withContext Result.success(messagesList)
        }

        val createdAtFilterStr = when {
            oldestTimestamp != null -> "lt.$oldestTimestamp"
            newestTimestamp != null -> "gt.$newestTimestamp"
            else -> {
                val newestMsgTimestamp = messageDao.getNewestMessageTimestamp(chatId) ?: messagesList.lastOrNull()?.createdAt
                if (!newestMsgTimestamp.isNullOrEmpty()) "gt.$newestMsgTimestamp" else null
            }
        }

        if (!SupabaseClient.isConfigured) {
            if (oldestTimestamp == null && newestTimestamp == null && messagesList.isEmpty()) {
                val demoList = SupabaseClient.demoMessages.filter { it.chatId == chatId }.sortedBy { timestampEpochMilli(it.createdAt) }
                val entities = demoList.map { MessageEntity.fromMessage(it) }
                messageDao.insertOrMergeMessages(entities)
                messagesList = demoList.takeLast(limit)
            }
            return@withContext Result.success(messagesList)
        }

        try {
            val service = SupabaseClient.apiService ?: return@withContext Result.success(messagesList)
            
            if (SupabaseClient.currentToken == null) {
                Log.w(TAG, "getMessagesForChatPaged: SupabaseClient.currentToken is null! Fetching may return unauthenticated or fail.")
            }

            val clearedDeletedContext = fetchClearedAndDeletedContext(chatId, service)
            val lastClearedAt = clearedDeletedContext.first
            val userDeletedIds = clearedDeletedContext.second

            if (userDeletedIds.isNotEmpty()) {
                try {
                    val allLocal = messageDao.getMessagesForChat(chatId)
                    val deletedLocal = allLocal.filter { userDeletedIds.contains(it.id) }
                    deletedLocal.forEach { messageDao.deleteMessageById(it.id) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error purging user deleted local messages", e)
                }
            }

            val identity = resolveChatIdentity(chatId)
            val isDm = identity.kind == ChatKind.DM

            val response = if (isDm && !identity.threadId.isNullOrEmpty()) {
                runCall { authHeader ->
                    Log.d(TAG, "getMessagesForChatPaged: Querying thread_messages for DM $chatId (thread ${identity.threadId}) with createdAtFilter=$createdAtFilterStr")
                    service.getThreadMessages(
                        apiKey = SupabaseClient.supabaseAnonKey,
                        authorization = authHeader,
                        threadIdFilter = "eq.${identity.threadId}",
                        createdAtFilter = createdAtFilterStr,
                        order = "created_at.desc",
                        limit = limit
                    )
                }
            } else {
                null
            }

            if (response != null) {
                if (response.isSuccessful) {
                    lastSyncTimestamps[chatId] = System.currentTimeMillis()
                    val remoteList = response.body() ?: emptyList()
                    Log.i(TAG, "getMessagesForChatPaged (thread_messages) SUCCESS: code=${response.code()}, size=${remoteList.size} filas")
                    if (remoteList.isNotEmpty()) {
                        val decryptedList = remoteList.mapNotNull { item ->
                            val msg = (item as? com.example.data.model.ThreadMessage)?.toMessage()
                            msg?.let { com.example.util.CryptoManager.decryptMessageIfNeeded(it) }
                        }.filter { msg ->
                            com.example.util.MessageFilter.shouldKeepMessage(
                                messageId = msg.id,
                                messageClientUuid = msg.clientMessageUuid,
                                messageCreatedAt = msg.createdAt,
                                lastClearedAt = lastClearedAt,
                                deletedMessageIds = userDeletedIds
                            )
                        }
                        val entities = decryptedList.map { MessageEntity.fromMessage(it) }
                        if (entities.isNotEmpty()) {
                            messageDao.insertOrMergeMessages(entities)
                        }
                    }
                    if (!lastClearedAt.isNullOrEmpty()) {
                        try {
                            val allLocal = messageDao.getMessagesForChat(chatId)
                            val staleLocal = allLocal.filter { isTimestampBeforeOrEqual(it.createdAt, lastClearedAt) }
                            staleLocal.forEach { messageDao.deleteMessageById(it.id) }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error purging stale local messages", e)
                        }
                    }
                    
                    val freshLocal = messageDao.getMessagesForChatPaged(chatId, limit, oldestTimestamp)
                    val validLocal = freshLocal.filter { local ->
                        com.example.util.MessageFilter.shouldKeepMessage(
                            messageId = local.id,
                            messageClientUuid = local.clientMessageUuid,
                            messageCreatedAt = local.createdAt,
                            lastClearedAt = lastClearedAt,
                            deletedMessageIds = userDeletedIds
                        )
                    }
                    return@withContext Result.success(validLocal.map { it.toMessage() }.stableSortedByCreatedAt())
                } else {
                    val errBody = response.errorBody()?.string() ?: "No error body"
                    Log.e(TAG, "🚨 getMessagesForChatPaged (thread_messages) FAILED: code=${response.code()}, error=$errBody")
                    // DM strictly returns local cache and NEVER falls through to messages table
                    if (isDm) return@withContext Result.success(messagesList)
                }
            }

            // If CHANNEL or LEGACY
            if (identity.kind == ChatKind.CHANNEL || identity.kind == ChatKind.LEGACY) {
                val legacyResponse = runCall { authHeader ->
                    Log.d(TAG, "getMessagesForChatPaged: Querying legacy messages for Channel $chatId with createdAtFilter=$createdAtFilterStr")
                    service.getMessages(
                        apiKey = SupabaseClient.supabaseAnonKey,
                        authorization = authHeader,
                        chatIdFilter = "eq.$chatId",
                        createdAtFilter = createdAtFilterStr,
                        order = "created_at.desc",
                        limit = limit
                    )
                }

                if (legacyResponse != null && legacyResponse.isSuccessful) {
                    lastSyncTimestamps[chatId] = System.currentTimeMillis()
                    val remoteList = legacyResponse.body() ?: emptyList()
                    Log.i(TAG, "getMessagesForChatPaged (legacy messages) SUCCESS: code=${legacyResponse.code()}, size=${remoteList.size} filas")
                    if (remoteList.isNotEmpty()) {
                        val decryptedList = remoteList.map { it: Message -> com.example.util.CryptoManager.decryptMessageIfNeeded(it) }.filter { msg ->
                            com.example.util.MessageFilter.shouldKeepMessage(
                                messageId = msg.id,
                                messageClientUuid = msg.clientMessageUuid,
                                messageCreatedAt = msg.createdAt,
                                lastClearedAt = lastClearedAt,
                                deletedMessageIds = userDeletedIds
                            )
                        }
                        val entities = decryptedList.map { MessageEntity.fromMessage(it) }
                        if (entities.isNotEmpty()) {
                            messageDao.insertOrMergeMessages(entities)
                        }
                    }
                    if (!lastClearedAt.isNullOrEmpty()) {
                        try {
                            val allLocal = messageDao.getMessagesForChat(chatId)
                            val staleLocal = allLocal.filter { isTimestampBeforeOrEqual(it.createdAt, lastClearedAt) }
                            staleLocal.forEach { messageDao.deleteMessageById(it.id) }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error purging stale local messages", e)
                        }
                    }
                    val freshLocal = messageDao.getMessagesForChatPaged(chatId, limit, oldestTimestamp)
                    val validLocal = freshLocal.filter { local ->
                        com.example.util.MessageFilter.shouldKeepMessage(
                            messageId = local.id,
                            messageClientUuid = local.clientMessageUuid,
                            messageCreatedAt = local.createdAt,
                            lastClearedAt = lastClearedAt,
                            deletedMessageIds = userDeletedIds
                        )
                    }
                    return@withContext Result.success(validLocal.map { it.toMessage() }.stableSortedByCreatedAt())
                } else {
                    val errBody = legacyResponse?.errorBody()?.string() ?: "No error body"
                    Log.e(TAG, "getMessagesForChatPaged (legacy messages) FAILED: code=${legacyResponse?.code()}, error=$errBody")
                }
            }

            Result.success(messagesList)
        } catch (e: Exception) {
            Log.e(TAG, "getMessagesForChatPaged exception, loading cache", e)
            Result.success(messagesList)
        }
    }

    suspend fun getMessagesForChat(chatId: String): Result<List<Message>> = withContext(Dispatchers.IO) {
        getMessagesForChatPaged(chatId, 100, null, null)
    }

    suspend fun syncUpdatedMessages(chatId: String): Result<List<Message>> = withContext(Dispatchers.IO) {
        val context = com.example.PanaApplication.instance
        val prefs = context.getSharedPreferences("panalink_prefs", android.content.Context.MODE_PRIVATE)
        val prefKey = "last_sync_updated_at_$chatId"
        
        var lastUpdatedAt = prefs.getString(prefKey, null)
        if (lastUpdatedAt.isNullOrEmpty()) {
            val allLocal = messageDao.getMessagesForChat(chatId)
            lastUpdatedAt = allLocal.mapNotNull { it.updatedAt }.maxOrNull()
        }
        
        val timestamp = if (!lastUpdatedAt.isNullOrEmpty()) lastUpdatedAt else "1970-01-01T00:00:00Z"
        Log.d(TAG, "syncUpdatedMessages: Starting incremental sync for chat $chatId since $timestamp")
        
        if (!SupabaseClient.isConfigured) {
            return@withContext Result.success(emptyList())
        }
        
        val service = SupabaseClient.apiService ?: return@withContext Result.success(emptyList())
        
        try {
            val clearedDeletedContext = fetchClearedAndDeletedContext(chatId, service)
            val lastClearedAt = clearedDeletedContext.first
            val userDeletedIds = clearedDeletedContext.second

            var remoteMessages = emptyList<Message>()
            
            val identity = resolveChatIdentity(chatId)
            val isDm = identity.kind == ChatKind.DM

            if (isDm && !identity.threadId.isNullOrEmpty()) {
                val response = runCall { authHeader ->
                    Log.d(TAG, "syncUpdatedMessages: Querying thread_messages for DM $chatId (thread ${identity.threadId}) with updatedAtFilter=gt.$timestamp")
                    service.getIncrementalThreadMessages(
                        apiKey = SupabaseClient.supabaseAnonKey,
                        authorization = authHeader,
                        threadIdFilter = "eq.${identity.threadId}",
                        updatedAtFilter = "gt.$timestamp"
                    )
                }

                if (response != null && response.isSuccessful) {
                    val remoteList = response.body() ?: emptyList()
                    Log.i(TAG, "syncUpdatedMessages (thread_messages) SUCCESS: code=${response.code()}, size=${remoteList.size} filas")
                    remoteMessages = remoteList.mapNotNull { it.toMessage() }
                } else {
                    val errBody = response?.errorBody()?.string() ?: "No response or error body"
                    Log.e(TAG, "🚨 syncUpdatedMessages (thread_messages) FAILED: error=$errBody")
                    return@withContext Result.failure(Exception("DM sync failed: $errBody"))
                }
            } else if (identity.kind == ChatKind.CHANNEL || identity.kind == ChatKind.LEGACY) {
                val legacyResponse = runCall { authHeader ->
                    Log.d(TAG, "syncUpdatedMessages: Querying legacy messages for Channel $chatId with updatedAtFilter=gt.$timestamp")
                    service.getIncrementalMessages(
                        apiKey = SupabaseClient.supabaseAnonKey,
                        authorization = authHeader,
                        chatIdFilter = "eq.$chatId",
                        updatedAtFilter = "gt.$timestamp"
                    )
                }

                if (legacyResponse != null && legacyResponse.isSuccessful) {
                    val remoteList = legacyResponse.body() ?: emptyList()
                    Log.i(TAG, "syncUpdatedMessages (legacy messages) SUCCESS: code=${legacyResponse.code()}, size=${remoteList.size} filas")
                    remoteMessages = remoteList
                } else {
                    val errBody = legacyResponse?.errorBody()?.string() ?: "No legacy response or error body"
                    Log.e(TAG, "🚨 syncUpdatedMessages (legacy messages) FAILED: error=$errBody")
                }
            } else {
                Log.w(TAG, "syncUpdatedMessages: Chat $chatId identity is UNKNOWN. Skipping remote incremental query.")
                return@withContext Result.success(emptyList())
            }

            val decryptedRemoteMessages = remoteMessages.map { msg ->
                com.example.util.CryptoManager.decryptMessageIfNeeded(msg)
            }
            val newestRemoteUpdatedAt = decryptedRemoteMessages.mapNotNull { it.updatedAt }.maxOrNull()
            val processedMessages = decryptedRemoteMessages.filter { msg ->
                com.example.util.MessageFilter.shouldKeepMessage(
                    messageId = msg.id,
                    messageClientUuid = msg.clientMessageUuid,
                    messageCreatedAt = msg.createdAt,
                    lastClearedAt = lastClearedAt,
                    deletedMessageIds = userDeletedIds
                )
            }

            if (!newestRemoteUpdatedAt.isNullOrEmpty()) {
                prefs.edit().putString(prefKey, newestRemoteUpdatedAt).apply()
                Log.d(TAG, "syncUpdatedMessages: Updated last_sync_updated_at cursor for chat $chatId to $newestRemoteUpdatedAt")
            }

            if (processedMessages.isNotEmpty()) {
                val entities = processedMessages.map { MessageEntity.fromMessage(it) }
                entities.forEach { entity ->
                    messageDao.mergeAndSaveMessage(entity)
                }
            }

            return@withContext Result.success(processedMessages)

        } catch (e: Exception) {
            Log.e(TAG, "syncUpdatedMessages exception occurred", e)
            return@withContext Result.failure(e)
        }
    }

    suspend fun insertLocalMessage(msg: Message) = withContext(Dispatchers.IO) {
        // Acción 3: Enrich story reply messages — ensure the story thumbnail
        // is attached so the chat bubble can display the story preview above the text.
        val enrichedMsg = if (msg.replyStoryId != null && msg.thumbnailUrl.isNullOrEmpty()) {
            val state = try {
                com.example.data.database.PanalinkDatabase.getDatabase(
                    com.example.PanaApplication.instance
                ).statesDao().getStateById(msg.replyStoryId!!)
            } catch (_: Exception) {
                null
            }
            if (state != null) {
                msg.copy(thumbnailUrl = state.thumbnailUrl ?: state.mediaUrl)
            } else msg
        } else msg

        val effectiveClearedAt = getEffectiveClearedAt(enrichedMsg.chatId, null)
        val shouldKeep = com.example.util.MessageFilter.shouldKeepMessage(
            messageId = enrichedMsg.id,
            messageClientUuid = enrichedMsg.clientMessageUuid,
            messageCreatedAt = enrichedMsg.createdAt,
            lastClearedAt = effectiveClearedAt,
            deletedMessageIds = getUserDeletedMessageIds()
        )
        if (shouldKeep) {
            messageDao.insertMessage(MessageEntity.fromMessage(enrichedMsg))
        } else {
            Log.d(TAG, "insertLocalMessage: Message filtered out by MessageFilter")
        }
    }

    suspend fun updateLocalMessageStatus(id: String, status: String) = withContext(Dispatchers.IO) {
        messageDao.updateMessageStatus(id, status)
    }

    fun getMessagesFlow(chatId: String): kotlinx.coroutines.flow.Flow<List<Message>> {
        return messageDao.getMessagesForChatFlow(chatId).map { entities: List<MessageEntity> ->
            // Sort in memory by EPOCH (not string). SQLite ORDER BY on ISO-8601
            // strings mis-orders messages sent in the same minute when PostgreSQL
            // mixes 'Z' with '+00:00' or drops the millis.
            entities.map { it.toMessage() }.stableSortedByCreatedAt()
        }
    }

    suspend fun getCachedMessages(chatId: String): List<Message> = withContext(Dispatchers.IO) {
        val entities = messageDao.getMessagesForChat(chatId)
        entities.map { it.toMessage() }.stableSortedByCreatedAt()
    }

    fun scheduleSync(chatId: String? = null) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = Data.Builder()
            .putString("chatId", chatId)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncMessagesWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            .addTag("sync_messages_work")
            .build()

        WorkManager.getInstance(PanaApplication.instance)
            .enqueueUniqueWork(
                chatId?.let { "sync_messages_$it" } ?: "sync_messages_unique",
                ExistingWorkPolicy.KEEP,
                syncRequest
            )
        Log.i(TAG, "Scheduled background sync with WorkManager${chatId?.let { " for chat $it" } ?: ""}")
    }

    fun scheduleMediaUpload(messageId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = Data.Builder()
            .putString("messageId", messageId)
            .build()

        val uploadRequest = OneTimeWorkRequestBuilder<MediaUploadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            .addTag("media_upload_work")
            .build()

        WorkManager.getInstance(PanaApplication.instance)
            .enqueueUniqueWork(
                "upload_$messageId",
                // ExistingWorkPolicy.KEEP:
                // - Máximo un WorkRequest activo por messageId.
                // - Si el trabajo ya está ENQUEUED o RUNNING, se conserva intacto sin cancelarlo ni duplicarlo.
                // - No genera cadenas innecesarias de WorkRequests anidados.
                // - Si el trabajo anterior concluyó (SUCCEEDED/FAILED), permite encolar uno nuevo (idempotencia y soporte para retryMessage).
                ExistingWorkPolicy.KEEP,
                uploadRequest
            )
        Log.i(TAG, "WORKMANAGER_CREATION: uniqueWorkName=upload_$messageId, workRequestId=${uploadRequest.id}, messageId=$messageId, policy=KEEP")
        try {
            val workInfos = WorkManager.getInstance(PanaApplication.instance)
                .getWorkInfosForUniqueWork("upload_$messageId")
                .get()
            val stateSummary = workInfos.joinToString { "${it.id}:${it.state}" }
            Log.i(TAG, "WORKMANAGER_OBSERVED_STATE: uniqueWorkName=upload_$messageId, states=[$stateSummary]")
        } catch (we: Exception) {
            Log.w(TAG, "WORKMANAGER_OBSERVED_STATE_ERROR: uniqueWorkName=upload_$messageId, error=${we.message}")
        }
        Log.i(TAG, "Scheduled background media upload for message $messageId")
    }

    suspend fun retryMessage(messageId: String) = withContext(Dispatchers.IO) {
        val entity = messageDao.getMessageById(messageId) ?: return@withContext
        messageDao.updateMessageStatus(messageId, "sending")
        if (!entity.localMediaUri.isNullOrEmpty() || (entity.messageType != null && entity.messageType != "text")) {
            scheduleMediaUpload(messageId)
        } else {
            scheduleSync()
        }
    }

    suspend fun sendMultimediaMessage(
        chatId: String,
        context: android.content.Context,
        sourceUri: android.net.Uri? = null,
        sourceFile: java.io.File? = null,
        mimeType: String,
        typeLabel: String,
        content: String = "",
        replyToId: String? = null,
        isGhost: Boolean = false,
        receiverId: String? = null
    ): Result<Message> = withContext(Dispatchers.IO) {
        val currentUid = SupabaseClient.currentUser?.id ?: "me_demo_id"
        val nowStr = SupabaseClient.getNowIsoString()
        val tempId = "temp_" + java.util.UUID.randomUUID().toString()
        val clientUuid = java.util.UUID.randomUUID().toString()

        try {
            // 1. Save media to local storage (persistent)
            val localMediaUri = PanalinkMediaManager.saveMediaToLocal(
                context,
                sourceUri,
                sourceFile,
                "msg_${tempId}_orig"
            )
            
            if (localMediaUri == null) {
                return@withContext Result.failure(Exception("Failed to save media locally"))
            }
            
            val localFile = java.io.File(localMediaUri)
            
            // 2. Generate local thumbnail for immediate UI feedback
            val thumbFile = if (typeLabel.equals("Video", ignoreCase = true)) {
                PanalinkMediaManager.generateVideoThumbnail(context, localFile)
            } else if (typeLabel.equals("Image", ignoreCase = true)) {
                PanalinkMediaManager.generateImageThumbnail(localFile)
            } else null

            val localThumbUri = thumbFile?.absolutePath

            val formattedContent = if (isGhost && !content.startsWith("[Ghost]")) {
                "[Ghost] $content"
            } else {
                content
            }

            val identity = resolveChatIdentity(chatId, receiverId)
            val resolvedReceiver = identity.receiverId ?: receiverId
            val canonicalChatId = if (identity.kind == ChatKind.DM && !identity.threadId.isNullOrEmpty() && isValidUuid(identity.threadId)) {
                identity.threadId
            } else {
                chatId
            }

            // 3. Create and insert MessageEntity with local paths and "sending" status
            val entity = MessageEntity(
                id = tempId,
                chatId = canonicalChatId,
                senderId = currentUid,
                receiverId = resolvedReceiver,
                content = formattedContent,
                createdAt = nowStr,
                status = "sending",
                replyToMessageId = replyToId,
                clientMessageUuid = clientUuid,
                messageType = typeLabel.lowercase(),
                mediaMime = mimeType,
                localMediaUri = localMediaUri,
                localThumbnailUri = localThumbUri,
                thumbnailUrl = localThumbUri,
                isGhost = isGhost
            )
            
            val effectiveClearedAt = getEffectiveClearedAt(canonicalChatId, null)
            val shouldKeep = com.example.util.MessageFilter.shouldKeepMessage(
                messageId = entity.id,
                messageClientUuid = entity.clientMessageUuid,
                messageCreatedAt = entity.createdAt,
                lastClearedAt = effectiveClearedAt,
                deletedMessageIds = getUserDeletedMessageIds()
            )
            if (shouldKeep) {
                messageDao.insertMessage(entity)
                Log.i(TAG, "Inserted pending multimedia message: $tempId")
            } else {
                Log.w(TAG, "Multimedia message creation filtered out by MessageFilter")
            }

            // 4. Enqueue WorkManager job instead of uploading immediately
            scheduleMediaUpload(tempId)

            val msg = entity.toMessage()
            Result.success(msg)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending multimedia message", e)
            Result.failure(e)
        }
    }

    /**
     * Envia N imagenes como UN SOLO mensaje album (grid estilo WhatsApp). El entity local
     * junta los paths con ","; el MediaUploadWorker sube cada uno con failover y guarda
     * las URLs remotas tambien con ",". ImageBubbleContent renderiza la cuadricula.
     */
    suspend fun sendImageAlbum(
        chatId: String,
        context: android.content.Context,
        uris: List<android.net.Uri>,
        mimeType: String = "image/jpeg",
        replyToId: String? = null,
        receiverId: String? = null
    ): Result<Message> = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext Result.failure(Exception("No images selected"))
        val currentUid = SupabaseClient.currentUser?.id ?: "me_demo_id"
        val nowStr = SupabaseClient.getNowIsoString()
        val tempId = "temp_" + java.util.UUID.randomUUID().toString()
        val clientUuid = java.util.UUID.randomUUID().toString()

        try {
            val localPaths = mutableListOf<String>()
            var firstThumb: String? = null
            for ((index, uri) in uris.withIndex()) {
                val localMediaUri = PanalinkMediaManager.saveMediaToLocal(
                    context,
                    uri,
                    null,
                    "msg_${tempId}_$index"
                ) ?: return@withContext Result.failure(Exception("Failed to save image $index locally"))
                localPaths += localMediaUri
                if (firstThumb == null) {
                    firstThumb = PanalinkMediaManager
                        .generateImageThumbnail(java.io.File(localMediaUri))
                        ?.absolutePath
                }
            }

            val identity = resolveChatIdentity(chatId, receiverId)
            val resolvedReceiver = identity.receiverId ?: receiverId
            val canonicalChatId = if (identity.kind == ChatKind.DM && !identity.threadId.isNullOrEmpty() && isValidUuid(identity.threadId)) {
                identity.threadId
            } else {
                chatId
            }
            val caption = "[${uris.size} fotos]"

            val entity = MessageEntity(
                id = tempId,
                chatId = canonicalChatId,
                senderId = currentUid,
                receiverId = resolvedReceiver,
                content = caption,
                createdAt = nowStr,
                status = "sending",
                replyToMessageId = replyToId,
                clientMessageUuid = clientUuid,
                messageType = "image",
                mediaMime = mimeType,
                localMediaUri = localPaths.joinToString(","),
                localThumbnailUri = firstThumb,
                thumbnailUrl = firstThumb
            )

            val effectiveClearedAt = getEffectiveClearedAt(canonicalChatId, null)
            val shouldKeep = com.example.util.MessageFilter.shouldKeepMessage(
                messageId = entity.id,
                messageClientUuid = entity.clientMessageUuid,
                messageCreatedAt = entity.createdAt,
                lastClearedAt = effectiveClearedAt,
                deletedMessageIds = getUserDeletedMessageIds()
            )
            if (shouldKeep) messageDao.insertMessage(entity)

            scheduleMediaUpload(tempId)
            Result.success(entity.toMessage())
        } catch (e: Exception) {
            android.util.Log.e("MessagesRepository", "Error sending image album", e)
            Result.failure(e)
        }
    }

    /**
     * Reenvía un mensaje preservando tipo y metadatos multimedia.
     * - Texto: usa el canal directo existente (sendMessage).
     * - Media local pendiente (file://, /absoluto, content://): reutiliza el archivo local
     *   y lo re-encola para subida durable sin perderlo.
     * - Media remota (http/https B2 firmada, CDN o vcdn://): descarga los bytes
     *   y genera una NUEVA subida por el circuito vigente (UploadFailoverRouter→B2).
     *   Nunca reenvía la URL B2 firmada original de 7 días; nunca envía
     *   "[Image]"/"[Video]"/etc. como sustituto: si la descarga falla, la
     *   operación falla con error visible.
     */
    suspend fun forwardMessage(
        chatId: String,
        context: android.content.Context,
        source: Message,
        receiverId: String? = null
    ): Result<Message> = withContext(Dispatchers.IO) {
        val mime = source.mediaMime?.trim()?.takeIf { it.isNotEmpty() }
        val typeRaw = source.messageType?.lowercase()?.trim().orEmpty()
        val isMultimedia = source.mediaUrl?.isNotBlank() == true && typeRaw !in listOf("text", "call", "playlist", "playlist_share", "location")
        if (!isMultimedia) {
            return@withContext sendMessage(
                chatId = chatId,
                content = source.textContent,
                replyToId = null,
                receiverUid = receiverId ?: source.receiverId,
                messageType = "text"
            )
        }

        val typeLabel = when (typeRaw) {
            "image", "photo" -> "Image"
            "video", "vid" -> "Video"
            "audio", "voice", "voice_note", "audio_note" -> "Audio"
            "document", "file", "archive", "pdf" -> "Document"
            "sticker" -> "Sticker"
            "gif" -> "GIF"
            else -> when {
                mime?.startsWith("image/") == true -> "Image"
                mime?.startsWith("video/") == true -> "Video"
                mime?.startsWith("audio/") == true -> "Audio"
                else -> "Document"
            }
        }

        forwardMedia(chatId, context, source, receiverId, typeLabel, mime)
    }

    private suspend fun forwardMedia(
        chatId: String,
        context: android.content.Context,
        source: Message,
        receiverId: String?,
        typeLabel: String,
        mime: String?
    ): Result<Message> {
        val resolved = com.example.data.repository.CdnManager.resolveMediaUrl(source.mediaUrl!!)
        val segments = resolved.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.isEmpty()) return Result.failure(Exception("Media vacía para reenviar"))
        val downloadedTempFiles = mutableListOf<java.io.File>()

        if (segments.size > 1) {
            // Album imagen (paths o URLs separadas por coma): subida como nuevo album.
            val urisResult = resolveForwardUris(context, segments, mime, typeLabel, downloadedTempFiles)

            if (urisResult.isFailure) return Result.failure(urisResult.exceptionOrNull() ?: Exception("No se pudo obtener la multimedia para reenviar"))
            return try {
                val res = sendImageAlbum(
                    chatId = chatId,
                    context = context,
                    uris = urisResult.getOrThrow(),
                    mimeType = mime ?: "image/jpeg",
                    replyToId = null,
                    receiverId = receiverId ?: source.receiverId
                )
                res
            } finally {
                downloadedTempFiles.forEach { it.delete() }
            }
        }

        val urisResult = resolveForwardUris(context, listOf(segments.first()), mime, typeLabel, downloadedTempFiles)
        val uris = urisResult.getOrNull()
        if (urisResult.isFailure || uris == null || uris.isEmpty()) {
            return urisResult.exceptionOrNull()?.let { Result.failure<Message>(it) } ?: Result.failure(Exception("No se pudo obtener la multimedia para reenviar"))
        }

        val singleUri = uris.first()
        return try {
            sendMultimediaMessage(
                chatId = chatId,
                context = context,
                sourceUri = if (singleUri.scheme == "content" || singleUri.scheme == "android.resource") singleUri else null,
                sourceFile = if (singleUri.scheme == "content" || singleUri.scheme == "android.resource") null else java.io.File(singleUri.path ?: ""),
                mimeType = mime ?: "application/octet-stream",
                typeLabel = typeLabel,
                content = cleanForwardCaption(source.textContent),
                replyToId = null,
                isGhost = false,
                receiverId = receiverId ?: source.receiverId
            )
        } finally {
            downloadedTempFiles.forEach { it.delete() }
        }
    }

    private suspend fun resolveForwardUris(
        context: android.content.Context,
        segments: List<String>,
        mime: String?,
        typeLabel: String,
        downloadedTempFiles: MutableList<java.io.File>
    ): Result<List<android.net.Uri>> {
        val result = mutableListOf<android.net.Uri>()
        for (raw in segments) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            val localFile = if (trimmed.startsWith("content://") || trimmed.startsWith("android.resource://")) {
                result += android.net.Uri.parse(trimmed)
                continue
            } else if (trimmed.startsWith("file://")) {
                try { java.io.File(android.net.Uri.parse(trimmed).path) } catch (e: Exception) { null }
            } else if (trimmed.startsWith("/") || trimmed.startsWith("file:")) {
                java.io.File(trimmed.removePrefix("file:").trimStart(java.io.File.separatorChar))
            } else {
                null
            }
            if (localFile != null && localFile.exists() && localFile.length() > 0L) {
                result += android.net.Uri.fromFile(localFile)
                continue
            }

            val temp = com.example.util.PanalinkMediaManager.downloadMedia(
                context = context,
                url = trimmed,
                fileName = "fwd_${java.util.UUID.randomUUID()}${mediaForwardExtension(mime, typeLabel)}"
            ) {}
            if (temp == null) {
                downloadedTempFiles.forEach { it.delete() }
                return Result.failure(Exception("No se pudo descargar la multimedia para reenviar"))
            }
            downloadedTempFiles += temp
            result += android.net.Uri.fromFile(temp)
        }
        return Result.success(result)
    }

    private fun cleanForwardCaption(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        val placeholderPattern = Regex("^\\s*(\\[(?:Image|Video|Audio|Document|Sticker|GIF|Ghost)\\]\\s*)+$", RegexOption.IGNORE_CASE)
        return if (placeholderPattern.matches(trimmed)) "" else trimmed
    }

    private fun mediaForwardExtension(mime: String?, typeLabel: String): String = when {
        typeLabel.equals("Video", true) -> ".mp4"
        typeLabel.equals("Audio", true) -> ".m4a"
        typeLabel.equals("Sticker", true) -> ".webp"
        typeLabel.equals("GIF", true) -> ".gif"
        mime?.contains("png") == true -> ".png"
        mime?.contains("jpeg") == true || mime?.contains("jpg") == true -> ".jpg"
        mime?.contains("pdf") == true -> ".pdf"
        else -> ".bin"
    }

    suspend fun sendPlaylistShareMessage(
        chatId: String,
        playlistId: String,
        playlistName: String,
        coverUrl: String? = null
    ): Result<Message> = withContext(Dispatchers.IO) {
        val currentUid = SupabaseClient.currentUser?.id ?: "me_demo_id"
        val nowStr = SupabaseClient.getNowIsoString()
        val tempId = "temp_" + java.util.UUID.randomUUID().toString()
        val clientUuid = java.util.UUID.randomUUID().toString()

        val entity = MessageEntity(
            id = tempId,
            chatId = chatId,
            senderId = currentUid,
            content = "Shared a playlist: $playlistName",
            createdAt = nowStr,
            status = "sending",
            clientMessageUuid = clientUuid,
            messageType = "playlist_share",
            mediaUrl = coverUrl,
            musicPlaylistId = playlistId
        )

        try {
            val effectiveClearedAt = getEffectiveClearedAt(chatId, null)
            val shouldKeep = com.example.util.MessageFilter.shouldKeepMessage(
                messageId = entity.id,
                messageClientUuid = entity.clientMessageUuid,
                messageCreatedAt = entity.createdAt,
                lastClearedAt = effectiveClearedAt,
                deletedMessageIds = getUserDeletedMessageIds()
            )
            if (shouldKeep) {
                messageDao.insertMessage(entity)
            }
            
            scheduleSync()
            Result.success(entity.toMessage())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending playlist share message", e)
            Result.failure(e)
        }
    }

    suspend fun syncPendingMessages(): Boolean = withContext(Dispatchers.IO) {
        if (!SupabaseClient.isConfigured) return@withContext true
        val pending = messageDao.getPendingMessages()
        Log.d(TAG, "[DIAGNOSTIC_LOG] START_SYNC_PENDING: pending_count=${pending.size}")
        if (pending.isEmpty()) return@withContext true
        Log.i(TAG, "Syncing ${pending.size} pending offline messages...")

                val parseMessage = { jsonStr: String?, isDm: Boolean ->
            if (jsonStr.isNullOrEmpty()) null
            else {
                try {
                    if (isDm) {
                        val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.model.ThreadMessage::class.java)
                        val adapter = SupabaseClient.moshi.adapter<List<com.example.data.model.ThreadMessage>>(listType)
                        val msgs = adapter.fromJson(jsonStr)
                        msgs?.firstOrNull()?.toMessage()
                    } else {
                        val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.model.Message::class.java)
                        val adapter = SupabaseClient.moshi.adapter<List<com.example.data.model.Message>>(listType)
                        val msgs = adapter.fromJson(jsonStr)
                        msgs?.firstOrNull()
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }

        var allSuccessful = true
        for (entity in pending) {
            try {
                if (entity.messageType != null && entity.messageType != "text" && entity.mediaUrl.isNullOrEmpty()) {
                    Log.w(TAG, "Skipping sync for multimedia message ${entity.id} because mediaUrl is missing (upload in progress or incomplete)")
                    if (!entity.localMediaUri.isNullOrBlank()) {
                        // Idempotente con ExistingWorkPolicy.KEEP: si ya está encolado o corriendo, no duplica
                        scheduleMediaUpload(entity.id)
                    } else {
                        // Sin URL remota y sin archivo local: estado irrecuperable automáticamente
                        Log.e(TAG, "Multimedia message ${entity.id} has no mediaUrl and no localMediaUri; marking failed")
                        messageDao.updateMessageStatus(entity.id, "failed")
                    }
                    continue
                }
                
                val service = SupabaseClient.apiService ?: run { allSuccessful = false; return@withContext false }
                val currentUid = SupabaseClient.currentUser?.id ?: run { allSuccessful = false; return@withContext false }

                // Resolve receiver and determine canonical identity
                val identity = resolveChatIdentity(entity.chatId, entity.receiverId)
                if (identity.kind == ChatKind.UNKNOWN) {
                    Log.w(TAG, "Chat ${entity.chatId} identity UNKNOWN during pending sync. Staying pending.")
                    allSuccessful = false
                    continue
                }

                val isDmSync = identity.kind == ChatKind.DM
                val receiverUid: String? = identity.receiverId ?: entity.receiverId

                if (isDmSync && !receiverUid.isNullOrEmpty() && entity.receiverId.isNullOrEmpty()) {
                    try {
                        messageDao.updateMessageReceiverId(entity.id, receiverUid)
                    } catch (_: Exception) {}
                }

                // VALIDACIÓN PARA DMs
                if (isDmSync) {
                    if (receiverUid.isNullOrEmpty() || !isValidUuid(receiverUid)) {
                        Log.e(TAG, "SYNC_FAILED: receiver_id is missing or invalid for DM ${entity.id}. Skipping to prevent RLS/Constraint violation.")
                        allSuccessful = false
                        continue
                    }
                    
                    if (receiverUid == currentUid) {
                        Log.e(TAG, "SYNC_FAILED: receiver_id cannot be the same as sender_id for DM ${entity.id}")
                        allSuccessful = false
                        continue
                    }
                }

                // Retrieve receiver's E2EE public key (Only for DMs)
                val receiverPublicKey = if (isDmSync && !receiverUid.isNullOrEmpty()) {
                    try {
                        com.example.data.repository.UserKeysRepository.getPublicKeyForUser(receiverUid!!)
                    } catch (e: com.example.data.repository.UserKeysRepository.MissingPublicKeyException) {
                        Log.w(TAG, "SYNC: Receiver $receiverUid has no E2EE key; sending plaintext fallback")
                        null
                    }
                } else null

                val contentToUpload = if (isDmSync && !receiverPublicKey.isNullOrEmpty() && !entity.content.isNullOrEmpty()) {
                    try {
                        Log.d(TAG, "Encrypting pending message content using E2EE during sync")
                        com.example.util.CryptoManager.encrypt(entity.content!!, receiverPublicKey)
                    } catch (e: Exception) {
                        Log.w(TAG, "SYNC: E2EE encryption failed; sending plaintext fallback", e)
                        entity.content
                    }
                } else {
                    entity.content
                }

                val remoteId = if (isValidUuid(entity.id)) entity.id else java.util.UUID.randomUUID().toString()
                val normalizedMessageType = when {
                    entity.messageType == "voice" || entity.messageType == "voice_note" || entity.messageType?.startsWith("audio/") == true -> "audio"
                    entity.messageType in listOf("image", "video", "audio", "document", "sticker", "gif", "text", "location", "call") -> entity.messageType
                    entity.messageType?.startsWith("image/") == true -> if (entity.messageType == "image/gif") "gif" else if (entity.messageType == "image/webp") "sticker" else "image"
                    entity.messageType?.startsWith("video/") == true -> "video"
                    entity.messageType?.startsWith("application/") == true || entity.messageType?.startsWith("text/") == true -> "document"
                    else -> entity.messageType?.lowercase() ?: "text"
                }

                val msgMap = mutableMapOf<String, Any?>(
                    "id" to remoteId,
                    "sender_id" to currentUid,
                    "reply_to" to entity.replyToMessageId?.takeIf { isValidUuid(it) },
                    "reply_story_id" to entity.replyStoryId,
                    "text_content" to contentToUpload,
                    "client_message_uuid" to entity.clientMessageUuid,
                    "created_at" to entity.createdAt,
                    "media_url" to entity.mediaUrl,
                    "thumbnail_url" to remoteThumbnail(entity.thumbnailUrl),
                    "media_mime" to entity.mediaMime,
                    "message_type" to normalizedMessageType,
                    "file_size" to entity.mediaSize,
                    "duration" to entity.mediaDuration,
                    "width" to entity.mediaWidth,
                    "height" to entity.mediaHeight,
                    "music_playlist_id" to entity.musicPlaylistId
                )

                if (isDmSync) {
                    if (identity.threadId.isNullOrEmpty()) {
                        Log.e(TAG, "SYNC_FAILED: Cannot sync DM message ${entity.id} because threadId is missing in identity. Staying pending.")
                        allSuccessful = false
                        continue
                    }
                    msgMap["thread_id"] = identity.threadId
                    msgMap["receiver_id"] = receiverUid
                } else {
                    msgMap["chat_id"] = entity.chatId
                }
                if (entity.isGhost || entity.content?.startsWith("[Ghost]") == true) {
                    msgMap["is_ghost"] = true
                }
                val cleanMsgMap = msgMap.filterValues { it != null }

                val targetThreadId = if (isDmSync) identity.threadId else entity.chatId
                Log.i(
                    TAG,
                    "MESSAGE_REGISTER_START: messageId=${entity.id}, clientMessageUuid=${entity.clientMessageUuid}, authUid=$currentUid, senderId=${entity.senderId}, receiverId=$receiverUid, threadId=$targetThreadId, messageType=${entity.messageType}, hasMediaUrl=${!entity.mediaUrl.isNullOrBlank()}, roomStatus=${entity.status}"
                )

                Log.i(TAG, "PANALINK_SYNC: chatId=${entity.chatId}, message_type=${entity.messageType}, media_url=${entity.mediaUrl}")

                // Pre-POST Reconciliation Check (Rule 1)
                var wasReconciled = false
                if (!entity.clientMessageUuid.isNullOrBlank()) {
                    try {
                        val verifyResponse = runCall { auth ->
                            service.getThreadMessageByClientUuid(
                                apiKey = SupabaseClient.supabaseAnonKey,
                                authorization = auth,
                                clientUuidFilter = "eq.${entity.clientMessageUuid}"
                            )
                        }
                        if (verifyResponse?.isSuccessful == true && verifyResponse.body()?.isNotEmpty() == true) {
                            val serverMsgList = verifyResponse.body()!!
                            val threadMsg = serverMsgList.first()
                            val mappedMsg = threadMsg.toMessage()
                            val finalMsg = com.example.util.CryptoManager.decryptMessageIfNeeded(mappedMsg).copy(
                                status = "sent",
                                clientMessageUuid = entity.clientMessageUuid ?: ""
                            )
                            val effectiveClearedAt = getEffectiveClearedAt(finalMsg.chatId, null)
                            val shouldKeep = com.example.util.MessageFilter.shouldKeepMessage(
                                messageId = finalMsg.id,
                                messageClientUuid = finalMsg.clientMessageUuid,
                                messageCreatedAt = finalMsg.createdAt,
                                lastClearedAt = effectiveClearedAt,
                                deletedMessageIds = getUserDeletedMessageIds()
                            )
                            if (shouldKeep) {
                                if (entity.id.startsWith("temp_") && finalMsg.id != entity.id) {
                                    messageDao.replaceTemporaryMessage(entity.id, MessageEntity.fromMessage(finalMsg))
                                } else {
                                    messageDao.insertMessage(MessageEntity.fromMessage(finalMsg))
                                }
                            } else {
                                messageDao.deleteMessageById(entity.id)
                            }
                            Log.i(TAG, "MESSAGE_LOCAL_TRANSITION: messageId=${entity.id}, finalSavedId=${finalMsg.id}, oldStatus=${entity.status}, newStatus=sent")
                            Log.i(TAG, "MESSAGE_REGISTER_SUCCESS: messageId=${entity.id}, remoteId=${finalMsg.id} (reconciled pre-POST)")
                            Log.i(TAG, "MESSAGE_REMOTE_VERIFY: found=true, remoteId=${finalMsg.id}, remoteStatus=${finalMsg.status}")
                            val localFinal = messageDao.getMessageById(finalMsg.id) ?: entity.clientMessageUuid?.let { messageDao.getMessagesByUuid(it).firstOrNull() }
                            Log.i(
                                TAG,
                                "MESSAGE_LOCAL_FINAL_STATE: messageId=${localFinal?.id ?: finalMsg.id}, status=${localFinal?.status}, hasMediaUrl=${!localFinal?.mediaUrl.isNullOrBlank()}, hasLocalMediaUri=${!localFinal?.localMediaUri.isNullOrBlank()}"
                            )
                            Log.i(TAG, "TRACE_SYNC: Reconciled message ${entity.clientMessageUuid} before POST. Marked as SENT.")
                            wasReconciled = true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error checking reconciliation before POST for msg ${entity.clientMessageUuid}", e)
                    }
                }

                if (wasReconciled) {
                    continue
                }

                var successful = false
                var is409OrTimeout = false
                var threadResponse: retrofit2.Response<okhttp3.ResponseBody>? = null
                
                try {
                    threadResponse = runCall { auth ->
                        if (isDmSync) {
                            service.createThreadMessage(
                                apiKey = SupabaseClient.supabaseAnonKey,
                                authorization = auth,
                                message = cleanMsgMap
                            )
                        } else {
                            service.createMessage(
                                apiKey = SupabaseClient.supabaseAnonKey,
                                authorization = auth,
                                message = cleanMsgMap
                            )
                        }
                    }
                    if (threadResponse?.code() == 409 || threadResponse?.code() == 408 || (threadResponse?.code() ?: 0) >= 500) {
                        is409OrTimeout = true
                    }
                } catch (e: Exception) {
                    val isTransient = e is java.io.IOException || 
                                      e is java.net.SocketTimeoutException || 
                                      e is java.net.ConnectException || 
                                      (e as? retrofit2.HttpException)?.code() in listOf(408, 409) || 
                                      ((e as? retrofit2.HttpException)?.code() ?: 0) >= 500
                    if (isTransient) {
                        is409OrTimeout = true
                    } else {
                        throw e
                    }
                }
                
                var code = threadResponse?.code() ?: 0
                var isSuccessful = threadResponse?.isSuccessful == true
                var errBody = threadResponse?.errorBody()?.string()
                var respBody = if (isSuccessful) threadResponse?.body()?.string() else null
                val sanitizedErrBody = sanitizeLogBody(errBody)

                Log.i(
                    TAG,
                    "MESSAGE_REGISTER_RESPONSE: httpStatus=$code, isSuccessful=$isSuccessful, errorBody=$sanitizedErrBody"
                )
                
                if (is409OrTimeout && !entity.clientMessageUuid.isNullOrBlank()) {
                    Log.i(TAG, "TRACE_SYNC: 409, timeout or transient error detected for msg ${entity.clientMessageUuid}. Reconciling...")
                    try {
                        val verifyResponse = runCall { auth ->
                            service.getThreadMessageByClientUuid(
                                apiKey = SupabaseClient.supabaseAnonKey,
                                authorization = auth,
                                clientUuidFilter = "eq.${entity.clientMessageUuid}"
                            )
                        }
                        if (verifyResponse?.isSuccessful == true && verifyResponse.body()?.isNotEmpty() == true) {
                            val serverMsgList = verifyResponse.body()!!
                            val threadMsg = serverMsgList.first()
                            val mappedMsg = threadMsg.toMessage()
                            val finalMsg = com.example.util.CryptoManager.decryptMessageIfNeeded(mappedMsg).copy(
                                status = "sent",
                                clientMessageUuid = entity.clientMessageUuid ?: ""
                            )
                            val effectiveClearedAt = getEffectiveClearedAt(finalMsg.chatId, null)
                            val shouldKeep = com.example.util.MessageFilter.shouldKeepMessage(
                                messageId = finalMsg.id,
                                messageClientUuid = finalMsg.clientMessageUuid,
                                messageCreatedAt = finalMsg.createdAt,
                                lastClearedAt = effectiveClearedAt,
                                deletedMessageIds = getUserDeletedMessageIds()
                            )
                            if (shouldKeep) {
                                if (entity.id.startsWith("temp_") && finalMsg.id != entity.id) {
                                    messageDao.replaceTemporaryMessage(entity.id, MessageEntity.fromMessage(finalMsg))
                                } else {
                                    messageDao.insertMessage(MessageEntity.fromMessage(finalMsg))
                                }
                            } else {
                                messageDao.deleteMessageById(entity.id)
                            }
                            Log.i(TAG, "MESSAGE_LOCAL_TRANSITION: messageId=${entity.id}, finalSavedId=${finalMsg.id}, oldStatus=${entity.status}, newStatus=sent")
                            Log.i(TAG, "MESSAGE_REGISTER_SUCCESS: messageId=${entity.id}, remoteId=${finalMsg.id} (reconciled on timeout/409)")
                            Log.i(TAG, "MESSAGE_REMOTE_VERIFY: found=true, remoteId=${finalMsg.id}, remoteStatus=${finalMsg.status}")
                            val localFinal = messageDao.getMessageById(finalMsg.id) ?: entity.clientMessageUuid?.let { messageDao.getMessagesByUuid(it).firstOrNull() }
                            Log.i(
                                TAG,
                                "MESSAGE_LOCAL_FINAL_STATE: messageId=${localFinal?.id ?: finalMsg.id}, status=${localFinal?.status}, hasMediaUrl=${!localFinal?.mediaUrl.isNullOrBlank()}, hasLocalMediaUri=${!localFinal?.localMediaUri.isNullOrBlank()}"
                            )
                            Log.i(TAG, "TRACE_SYNC: Reconciled message ${entity.clientMessageUuid} after failed POST. Marked as SENT.")
                            continue
                        } else {
                            Log.w(TAG, "TRACE_SYNC: Message ${entity.clientMessageUuid} not found on remote after error/timeout. Keeping as pending/sending.")
                            Log.e(TAG, "MESSAGE_REGISTER_FAILURE: messageId=${entity.id}, httpStatus=$code, error=$sanitizedErrBody")
                            allSuccessful = false
                            continue
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reconciling message after failed POST", e)
                        Log.e(TAG, "MESSAGE_REGISTER_FAILURE: messageId=${entity.id}, httpStatus=$code, error=${sanitizeLogBody(e.message)}")
                        allSuccessful = false
                        continue
                    }
                }
                   
                Log.i(TAG, "TRACE_SYNC_AFTER_CREATE_THREAD: httpStatus=$code, isSuccessful=$isSuccessful, supabaseResponse=$respBody, errorBody=$errBody")

                Log.d(TAG, "[DIAGNOSTIC_LOG] AFTER_CREATE_THREAD: msgId=${entity.id}, clientUuid=${entity.clientMessageUuid}, httpStatus=$code, isSuccessful=$isSuccessful, err=${errBody?.take(100)}")

                if (successful || threadResponse?.isSuccessful == true) {
                    successful = true
                    val responseBody = respBody ?: threadResponse?.body()?.string()
                    Log.i(TAG, "PANALINK_SYNC_RESULT: $responseBody, markedAsSent=true")
                } else {
                    val errorCode = threadResponse?.code() ?: 0
                    val errorBody = threadResponse?.errorBody()?.string() ?: ""
                    Log.w(TAG, "PANALINK_SYNC_RESULT: thread_messages failed (Code: $errorCode, Error: $errorBody).")
                    
                    // IF IT IS A DM, WE DO NOT FALLBACK TO LEGACY MESSAGES
                    if (isDmSync) {
                        val nextStatus = if (isTransientHttpStatus(errorCode)) "pending" else "failed"
                        messageDao.updateMessageStatus(entity.id, nextStatus)
                        Log.e(TAG, "DM sync failed for message ${entity.id}. Transitioning to $nextStatus.")
                        Log.e(TAG, "MESSAGE_REGISTER_FAILURE: messageId=${entity.id}, httpStatus=$errorCode, error=$sanitizedErrBody")
                        if (nextStatus == "pending") allSuccessful = false
                        continue
                    }

                    Log.i(TAG, "Channel/Legacy chat detected. Trying legacy fallback for message ${entity.id}.")
                    val legacyMsgMap = mutableMapOf<String, Any?>(
                        "id" to remoteId,
                        "thread_id" to entity.chatId,
                        "sender_id" to currentUid,
                        "receiver_id" to receiverUid,
                        "text_content" to contentToUpload,
                        "client_message_uuid" to entity.clientMessageUuid,
                        "created_at" to entity.createdAt,
                        "media_url" to entity.mediaUrl,
                        "thumbnail_url" to remoteThumbnail(entity.thumbnailUrl),
                        "media_mime" to entity.mediaMime,
                        "message_type" to normalizedMessageType,
                        "file_size" to entity.mediaSize,
                        "duration" to entity.mediaDuration,
                        "width" to entity.mediaWidth,
                        "height" to entity.mediaHeight,
                        "music_playlist_id" to entity.musicPlaylistId
                    )
                    if (entity.replyToMessageId != null && isValidUuid(entity.replyToMessageId)) {
                        legacyMsgMap["reply_to"] = entity.replyToMessageId
                    }
                    if (entity.replyStoryId != null) {
                        legacyMsgMap["reply_story_id"] = entity.replyStoryId
                    }
                    val cleanLegacyMsgMap = legacyMsgMap.filterValues { it != null }

                    val legacyResponse = runCall { auth ->
                        service.createMessage(
                            apiKey = SupabaseClient.supabaseAnonKey,
                            authorization = auth,
                            message = cleanLegacyMsgMap
                        )
                    }
                    if (legacyResponse != null && legacyResponse.isSuccessful) {
                        successful = true
                        respBody = legacyResponse.body()?.string()
                    } else {
                        val legacyCode = legacyResponse?.code() ?: 0
                        val legacyErr = sanitizeLogBody(legacyResponse?.errorBody()?.string())
                        Log.e(TAG, "MESSAGE_REGISTER_FAILURE: messageId=${entity.id}, httpStatus=$legacyCode, error=$legacyErr")
                        val nextStatus = if (isTransientHttpStatus(legacyCode)) "pending" else "failed"
                        messageDao.updateMessageStatus(entity.id, nextStatus)
                        if (nextStatus == "pending") allSuccessful = false
                    }
                }

                if (successful) {
                    val serverMsg = parseMessage(respBody, isDmSync)
                    val confirmedRemoteId = serverMsg?.id ?: remoteId
                    Log.i(TAG, "MESSAGE_REGISTER_SUCCESS: messageId=${entity.id}, remoteId=$confirmedRemoteId")

                    var finalSavedId = entity.id
                    if (serverMsg != null) {
                        val finalMsgRaw = serverMsg
                        val finalMsg = com.example.util.CryptoManager.decryptMessageIfNeeded(finalMsgRaw).copy(
                            status = "sent",
                            clientMessageUuid = entity.clientMessageUuid ?: ""
                        )
                        finalSavedId = finalMsg.id
                        val effectiveClearedAt = getEffectiveClearedAt(finalMsg.chatId, null)
                        val shouldKeep = com.example.util.MessageFilter.shouldKeepMessage(
                            messageId = finalMsg.id,
                            messageClientUuid = finalMsg.clientMessageUuid,
                            messageCreatedAt = finalMsg.createdAt,
                            lastClearedAt = effectiveClearedAt,
                            deletedMessageIds = getUserDeletedMessageIds()
                        )
                        if (shouldKeep) {
                            if (entity.id.startsWith("temp_") && finalMsg.id != entity.id) {
                                messageDao.replaceTemporaryMessage(entity.id, MessageEntity.fromMessage(finalMsg))
                            } else {
                                messageDao.insertMessage(MessageEntity.fromMessage(finalMsg))
                            }
                        } else {
                            messageDao.deleteMessageById(entity.id)
                        }
                    } else {
                        messageDao.updateMessageStatus(entity.id, "sent")
                    }
                    Log.i(TAG, "MESSAGE_LOCAL_TRANSITION: messageId=${entity.id}, finalSavedId=$finalSavedId, oldStatus=${entity.status}, newStatus=sent")

                    // Verification of remote state via client_message_uuid
                    var remoteFound = false
                    var verifiedRemoteId: String? = null
                    var verifiedRemoteStatus: String? = null

                    try {
                        if (!entity.clientMessageUuid.isNullOrBlank() && isDmSync) {
                            val checkResp = runCall { auth ->
                                service.getThreadMessageByClientUuid(
                                    apiKey = SupabaseClient.supabaseAnonKey,
                                    authorization = auth,
                                    clientUuidFilter = "eq.${entity.clientMessageUuid}"
                                )
                            }
                            if (checkResp?.isSuccessful == true && !checkResp.body().isNullOrEmpty()) {
                                val remoteItem = checkResp.body()!!.first()
                                remoteFound = true
                                verifiedRemoteId = remoteItem.id
                                verifiedRemoteStatus = remoteItem.status ?: "sent"
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "MESSAGE_REMOTE_VERIFY error: ${e.message}")
                    }

                    if (!remoteFound && serverMsg != null) {
                        remoteFound = true
                        verifiedRemoteId = serverMsg.id
                        verifiedRemoteStatus = serverMsg.status
                    }

                    Log.i(
                        TAG,
                        "MESSAGE_REMOTE_VERIFY: found=$remoteFound, remoteId=$verifiedRemoteId, remoteStatus=$verifiedRemoteStatus"
                    )

                    val localFinal = messageDao.getMessageById(finalSavedId)
                        ?: (entity.clientMessageUuid?.let { messageDao.getMessagesByUuid(it).firstOrNull() })
                    Log.i(
                        TAG,
                        "MESSAGE_LOCAL_FINAL_STATE: messageId=${localFinal?.id ?: finalSavedId}, status=${localFinal?.status}, hasMediaUrl=${!localFinal?.mediaUrl.isNullOrBlank()}, hasLocalMediaUri=${!localFinal?.localMediaUri.isNullOrBlank()}"
                    )

                    Log.d(TAG, "Successfully synchronized message: ${entity.id}")
                    if (!receiverUid.isNullOrEmpty()) {
                        triggerSendPushNotification(
                            chatId = entity.chatId,
                            recipientUserId = receiverUid,
                            title = SupabaseClient.currentProfile?.displayName ?: "Mensaje nuevo",
                            bodyText = entity.content ?: ""
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync pending message: ${entity.id}", e)
                Log.e(TAG, "MESSAGE_REGISTER_FAILURE: messageId=${entity.id}, httpStatus=0, error=${sanitizeLogBody(e.message)}")
                allSuccessful = false
            }
        }
        return@withContext allSuccessful
    }

    suspend fun syncAllPendingAndUpdatedMessages(): Boolean = withContext(Dispatchers.IO) {
        if (!SupabaseClient.isConfigured) return@withContext true

        Log.i(TAG, "syncAllPendingAndUpdatedMessages: Starting full hybrid sync cycle...")
        var allSuccessful = true

        try {
            // A) Upload de mensajes locales pendientes (status = sending / pending / pending_media)
            val pendingMsgs = messageDao.getPendingMessages()
            val pendingSuccess = syncPendingMessages()
            if (!pendingSuccess) {
                allSuccessful = false
                Log.w(TAG, "syncAllPendingAndUpdatedMessages: syncPendingMessages failed or partially failed")
            }

            val service = SupabaseClient.apiService
            if (service == null) {
                return@withContext false
            }

            // B) Upload de ediciones: editPending = true
            val editPendingMsgs = messageDao.getEditPendingMessages()
            Log.d(TAG, "syncAllPendingAndUpdatedMessages: Found ${editPendingMsgs.size} messages with editPending = true")
            for (msg in editPendingMsgs) {
                try {
                    val response = runCall { auth ->
                        service.updateThreadMessage(
                            apiKey = SupabaseClient.supabaseAnonKey,
                            authorization = auth,
                            idFilter = "eq.${msg.id}",
                            updates = mapOf(
                                "text_content" to msg.content
                            )
                        )
                    }
                    if (response != null && response.isSuccessful) {
                        messageDao.clearMessageEditPending(msg.id)
                        Log.d(TAG, "syncAllPendingAndUpdatedMessages: Cleared editPending for msg ${msg.id}")
                    } else {
                        allSuccessful = false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error syncing pending edit for msg ${msg.id}", e)
                    allSuccessful = false
                }
            }

                        // C) Upload de reacciones: reactionPending = true
            val reactionPendingChatIds = messageDao.getReactionPendingMessages().map { it.chatId }
            val reactionSuccess = reactionSyncDataSource.syncPendingReactions()
            if (!reactionSuccess) {
                allSuccessful = false
            }
// D) Upload de eliminaciones: deletePending = true
            val deletePendingMsgs = messageDao.getDeletePendingMessages()
            Log.d(TAG, "syncAllPendingAndUpdatedMessages: Found ${deletePendingMsgs.size} messages with deletePending = true")
            for (msg in deletePendingMsgs) {
                try {
                    val nowStr = msg.deletedAt ?: java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { 
                        timeZone = java.util.TimeZone.getTimeZone("UTC") 
                    }.format(java.util.Date())
                    
                    val response = runCall { auth ->
                        service.updateThreadMessage(
                            apiKey = SupabaseClient.supabaseAnonKey,
                            authorization = auth,
                            idFilter = "eq.${msg.id}",
                            updates = mapOf(
                                "status" to "deleted",
                                "deleted_at" to nowStr
                            )
                        )
                    }
                    if (response != null && response.isSuccessful) {
                        messageDao.clearMessageDeletePending(msg.id)
                        Log.d(TAG, "syncAllPendingAndUpdatedMessages: Cleared deletePending for msg ${msg.id}")
                    } else {
                        allSuccessful = false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error syncing pending delete for msg ${msg.id}", e)
                    allSuccessful = false
                }
            }

            // Gather all chat IDs to sync delta updates for
            val activeChatIds = mutableSetOf<String>()
            activeChatIds.addAll(pendingMsgs.map { it.chatId })
            activeChatIds.addAll(editPendingMsgs.map { it.chatId })
            activeChatIds.addAll(reactionPendingChatIds)
            activeChatIds.addAll(deletePendingMsgs.map { it.chatId })
            
            try {
                activeChatIds.addAll(messageDao.getDistinctChatIds())
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching distinct chatIds from local database", e)
            }

            val finalChatIds = activeChatIds.filter { it.isNotEmpty() }
            Log.d(TAG, "syncAllPendingAndUpdatedMessages: Starting incremental delta updates (E, F, G) for chats: $finalChatIds")
            
            for (chatId in finalChatIds) {
                val syncResult = syncUpdatedMessages(chatId)
                if (syncResult.isFailure) {
                    allSuccessful = false
                    Log.e(TAG, "syncAllPendingAndUpdatedMessages: syncUpdatedMessages failed for chat $chatId")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "syncAllPendingAndUpdatedMessages: Global sync cycle exception", e)
            allSuccessful = false
        }

        return@withContext allSuccessful
    }


    suspend fun saveReaction(messageId: String, chatId: String, userId: String, emoji: String) =
            reactionsDataSource.saveReaction(messageId, chatId, userId, emoji)

    suspend fun deleteReaction(messageId: String, chatId: String, userId: String) =
            reactionsDataSource.deleteReaction(messageId, chatId, userId)
    suspend fun sendMessage(
        chatId: String,
        content: String,
        replyToId: String? = null,
        replyStoryId: String? = null,
        receiverUid: String? = null,
        messageId: String? = null,
        messageType: String = "text",
        mediaUrl: String? = null,
        thumbnailUrl: String? = null,
        mediaMime: String? = null,
        mediaSize: Long? = null,
        duration: Long? = null,
        width: Int? = null,
        height: Int? = null,
        clientMessageUuid: String? = null,
        isGhost: Boolean = false
    ): Result<Message> = withContext(Dispatchers.IO) {
        val currentUid = if (SupabaseClient.isConfigured) {
            val uid = SupabaseClient.currentUser?.id
            if (uid.isNullOrEmpty()) {
                Log.e(TAG, "sendMessage: Authenticated user ID is missing or null!")
                return@withContext Result.failure(Exception("No autenticado (UID faltante)"))
            }
            uid
        } else {
            "me_demo_id"
        }
        val nowStr = SupabaseClient.getNowIsoString()
        val clientUuid = clientMessageUuid ?: UUID.randomUUID().toString()
        val tempId = messageId ?: if (SupabaseClient.isConfigured) {
            "temp_${UUID.randomUUID()}"
        } else {
            "msg_${UUID.randomUUID()}"
        }

        val formattedContent = if (isGhost && !content.startsWith("[Ghost]")) {
            "[Ghost] $content"
        } else {
            content
        }

        val message = Message(
            id = tempId,
            chatId = chatId,
            senderId = currentUid,
            receiverId = receiverUid,
            content = formattedContent,
            createdAt = nowStr,
            status = "sending",
            replyToMessageId = replyToId,
            replyStoryId = replyStoryId,
            clientMessageUuid = clientUuid,
            thumbnailUrl = thumbnailUrl,
            mediaUrl = mediaUrl,
            mediaMime = mediaMime,
            mediaSize = mediaSize,
            duration = duration,
            width = width,
            height = height,
            messageType = messageType,
            isGhost = isGhost
        )

        // Save to local Room first for true offline-first
        messageDao.insertMessage(MessageEntity.fromMessage(message))

        // Corrección de identidad sin red: si este chat local NO existe aún (thread_id
        // no cacheado) pero el hilo DM sí pertenece a Room por otros medios (otherUserId,
        // o el thread es este mismo chat canalizado por otro id local), re-apuntamos el
        // mensaje local al chat canónico para NO crear un chat paralelo/duplicado fantasma..
        val localChat = runCatching { com.example.data.database.PanalinkDatabase.getDatabase(com.example.PanaApplication.instance).chatDao().getChatById(chatId) }.getOrNull()
        if (localChat == null) {
            val otherUserId = if (receiverUid.isNullOrBlank()) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val roomChat = com.example.data.database.PanalinkDatabase.getDatabase(com.example.PanaApplication.instance).chatDao().getChatByThreadId(chatId)
                    roomChat?.otherUserId ?: if (receiverUid.isNullOrBlank()) null else receiverUid
                }
            } else receiverUid
            if (!otherUserId.isNullOrBlank()) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val canonical = com.example.data.database.PanalinkDatabase.getDatabase(com.example.PanaApplication.instance)
                        .chatDao().getChatByOtherUserId(otherUserId)
                    if (canonical != null && canonical.id != chatId) {
                        messageDao.insertMessage(MessageEntity.fromMessage(message.copy(chatId = canonical.id)))
                    }
                }
            }
        }

        if (!SupabaseClient.isConfigured) {
            delay(300)
            messageDao.updateMessageStatus(tempId, "sent")
            val updatedMsg = message.copy(status = "sent")
            SupabaseClient.demoMessages.add(updatedMsg)
            simulateDemoReply(chatId, content)
            return@withContext Result.success(updatedMsg)
        }

        try {
            val service = SupabaseClient.apiService ?: run {
                messageDao.updateMessageStatus(tempId, "pending")
                scheduleSync()
                return@withContext Result.failure(Exception("Servicio de mensajería no disponible"))
            }
            SessionManager.validateAndRefreshSessionIfNeeded()

            val identity = resolveChatIdentity(chatId, receiverUid)
            var finalReceiverUid = identity.receiverId ?: receiverUid
            val isDm = identity.kind == ChatKind.DM

            if (identity.kind == ChatKind.UNKNOWN || (isDm && identity.threadId.isNullOrEmpty())) {
                Log.i(TAG, "sendMessage: Chat $chatId identity UNKNOWN or missing threadId. Forcing fresh canonical identity resolve.")
                val freshIdentity = resolveChatIdentity(chatId, receiverUid)
                if (freshIdentity.kind == ChatKind.UNKNOWN || (freshIdentity.kind == ChatKind.DM && freshIdentity.threadId.isNullOrEmpty())) {
                    Log.w(TAG, "sendMessage: Chat $chatId identity STILL UNKNOWN after fresh resolve. Keeping message local and scheduling sync.")
                    scheduleSync()
                    return@withContext Result.success(message)
                }
                finalReceiverUid = freshIdentity.receiverId ?: receiverUid
            }

            // We need to use Supabase API to insert the message
            val receiverPublicKey = if (isDm && !finalReceiverUid.isNullOrEmpty()) {
                try {
                    com.example.data.repository.UserKeysRepository.getPublicKeyForUser(finalReceiverUid)
                } catch (e: com.example.data.repository.UserKeysRepository.MissingPublicKeyException) {
                    Log.w(TAG, "Receiver $finalReceiverUid has no E2EE key; sending plaintext fallback")
                    null
                }
            } else {
                null
            }

            val contentToUpload = if (!receiverPublicKey.isNullOrEmpty()) {
                try {
                    Log.d(TAG, "Encrypting message content using E2EE")
                    com.example.util.CryptoManager.encrypt(formattedContent, receiverPublicKey)
                } catch (e: Exception) {
                    Log.w(TAG, "E2EE encryption failed; sending plaintext fallback", e)
                    formattedContent
                }
            } else {
                formattedContent
            }

            // Rule: If it's a multimedia message and the URL is still NULL, 
            // we SKIP the sync for now. The MediaUploadWorker will update the URL and trigger sync later.
            if (messageType.lowercase() != "text" && mediaUrl.isNullOrBlank()) {
                Log.d(TAG, "Multimedia message without URL, letting MediaUploadWorker handle it later.")
                // Unhide the chat for the sender
                try {
                    runCall { auth ->
                        service.updateChatParticipant(
                            apiKey = SupabaseClient.supabaseAnonKey,
                            authorization = auth,
                            chatIdFilter = "eq.$chatId",
                            userIdFilter = "eq.$currentUid",
                            updates = mapOf<String, Any>("is_hidden" to false)
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error unhiding chat", e)
                }
                return@withContext Result.success(message)
            }

            // Unhide the chat for the sender
            try {
                runCall { auth ->
                    service.updateChatParticipant(
                        apiKey = SupabaseClient.supabaseAnonKey,
                        authorization = auth,
                        chatIdFilter = "eq.$chatId",
                        userIdFilter = "eq.$currentUid",
                        updates = mapOf<String, Any>("is_hidden" to false)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error unhiding chat", e)
            }

            val remoteId = if (isValidUuid(tempId)) tempId else java.util.UUID.randomUUID().toString()
            val normalizedMessageType = when {
                messageType == "voice" || messageType == "voice_note" || messageType.startsWith("audio/") -> "audio"
                messageType in listOf("image", "video", "audio", "document", "sticker", "gif", "text", "location", "call") -> messageType
                messageType.startsWith("image/") -> if (messageType == "image/gif") "gif" else if (messageType == "image/webp") "sticker" else "image"
                messageType.startsWith("video/") -> "video"
                messageType.startsWith("application/") || messageType.startsWith("text/") -> "document"
                else -> messageType.lowercase()
            }

            val msgMap = mutableMapOf<String, Any?>(
                "id" to remoteId,
                "thread_id" to if (isDm) identity.threadId else null,
                "chat_id" to if (isDm) null else chatId,
                "sender_id" to currentUid,
                "receiver_id" to if (isDm) finalReceiverUid?.takeIf { isValidUuid(it) } else null,
                "reply_to" to replyToId?.takeIf { isValidUuid(it) },
                "reply_story_id" to replyStoryId,
                "text_content" to contentToUpload,
                "client_message_uuid" to clientUuid,
                "created_at" to nowStr,
                "media_url" to mediaUrl,
                "thumbnail_url" to thumbnailUrl,
                "media_mime" to mediaMime,
                "message_type" to normalizedMessageType,
                "file_size" to mediaSize,
                "duration" to duration,
                "width" to width,
                "height" to height
            )
            if (isGhost) {
                msgMap["is_ghost"] = true
            }
            val cleanMsgMap = msgMap.filterValues { it != null }

            var successful = false
            var errorStr = ""

            val response = runCall { auth ->
                if (isDm) {
                    service.createThreadMessage(
                        apiKey = SupabaseClient.supabaseAnonKey,
                        authorization = auth,
                        message = cleanMsgMap
                    )
                } else {
                    service.createMessage(
                        apiKey = SupabaseClient.supabaseAnonKey,
                        authorization = auth,
                        message = cleanMsgMap
                    )
                }
            }

            var returnedId: String? = null
            var returnedCreatedAtRaw: String? = null
            if (response != null && response.isSuccessful) {
                successful = true
                try {
                    val bodyStr = response.body()?.string()
                    if (!bodyStr.isNullOrBlank()) {
                        val jsonArray = org.json.JSONArray(bodyStr)
                        if (jsonArray.length() > 0) {
                            returnedId = jsonArray.getJSONObject(0).optString("id", null)
                            returnedCreatedAtRaw = jsonArray.getJSONObject(0).optString("created_at", null)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing response body", e)
                }
            } else {
                val errorCode = response?.code() ?: 0
                val errorBody = response?.errorBody()?.string() ?: "Response is null"
                errorStr = "Code: $errorCode, Body: $errorBody"
                
                val jwtUserId = SessionManager.getJwtUserId(SupabaseClient.currentToken)
                if (!jwtUserId.isNullOrBlank() && !currentUid.isNullOrBlank() && jwtUserId != currentUid) {
                    Log.e(TAG, "[DIAGNOSTIC_LOG] AUTH_IDENTITY_MISMATCH: jwtUserId=$jwtUserId != currentUserId=$currentUid, senderId=$currentUid")
                }
                
                Log.w(TAG, "[DIAGNOSTIC_LOG] createThreadMessage failed: HTTP status=$errorCode, threadId=$chatId, senderId=$currentUid, currentUserId=$currentUid, clientMessageUuid=$clientUuid, responseBody=${errorBody.take(200)}")
                
                // FALLBACK TO LEGACY MESSAGES TABLE (ONLY FOR NON-DM)
                if (!isDm) {
                    Log.i(TAG, "Non-DM chat detected. Trying legacy createMessage fallback.")
                    val legacyMsgMap = mutableMapOf<String, Any?>(
                        "id" to remoteId,
                        "thread_id" to null,
                        "chat_id" to chatId,
                        "sender_id" to currentUid,
                        "receiver_id" to null,
                        "text_content" to contentToUpload,
                        "client_message_uuid" to clientUuid,
                        "created_at" to nowStr,
                        "media_url" to mediaUrl,
                        "thumbnail_url" to thumbnailUrl,
                        "media_mime" to mediaMime,
                        "message_type" to normalizedMessageType,
                        "file_size" to mediaSize,
                        "duration" to duration,
                        "width" to width,
                        "height" to height
                    )
                    if (replyToId != null && isValidUuid(replyToId)) {
                        legacyMsgMap["reply_to"] = replyToId
                    }
                    if (replyStoryId != null) {
                        legacyMsgMap["reply_story_id"] = replyStoryId
                    }
                    val cleanLegacyMsgMap = legacyMsgMap.filterValues { it != null }

                    val legacyResponse = runCall { auth ->
                        service.createMessage(
                            apiKey = SupabaseClient.supabaseAnonKey,
                            authorization = auth,
                            message = cleanLegacyMsgMap
                        )
                    }

                    if (legacyResponse != null && legacyResponse.isSuccessful) {
                        successful = true
                        try {
                            val bodyStr = legacyResponse.body()?.string()
                            if (!bodyStr.isNullOrBlank()) {
                                val jsonArray = org.json.JSONArray(bodyStr)
                                if (jsonArray.length() > 0) {
                                    returnedId = jsonArray.getJSONObject(0).optString("id", null)
                                    returnedCreatedAtRaw = jsonArray.getJSONObject(0).optString("created_at", null)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing legacy response body", e)
                        }
                    } else {
                        val legacyCode = legacyResponse?.code()
                        val legacyError = legacyResponse?.errorBody()?.string() ?: "Legacy response is null"
                        errorStr = "ThreadMsgErr: $errorStr | LegacyErr: (Code: $legacyCode, Body: $legacyError)"
                        Log.e(TAG, "Legacy createMessage also failed: $errorStr")
                    }
                } else {
                    Log.e(TAG, "DM creation failed: $errorStr. Message remains pending for background sync retry.")
                }
            }

            if (successful) {
                val finalId = returnedId?.takeIf { it.isNotBlank() } ?: remoteId

                // Adopt the server's authoritative createdAt (the "real" inserted
                // timestamp), so optimistic/local and post-sync versions share the
                // same instant as every Realtime/HTTP peer. Postgres may also
                // normalize the format ('.SSS Z' vs '+00:00'), which otherwise
                // breaks string ordering in Room for same-minute messages.
                val updated = message.copy(
                    id = finalId,
                    status = "sent",
                    createdAt = returnedCreatedAtRaw?.takeIf { it.isNotBlank() } ?: message.createdAt
                )
                val effectiveClearedAt = getEffectiveClearedAt(updated.chatId, null)
                val shouldKeep = com.example.util.MessageFilter.shouldKeepMessage(
                    messageId = updated.id,
                    messageClientUuid = updated.clientMessageUuid,
                    messageCreatedAt = updated.createdAt,
                    lastClearedAt = effectiveClearedAt,
                    deletedMessageIds = getUserDeletedMessageIds()
                )
                if (shouldKeep) {
                    messageDao.replaceTemporaryMessage(tempId, MessageEntity.fromMessage(updated))
                } else {
                    messageDao.deleteMessageById(tempId)
                }
                return@withContext Result.success(updated)
            } else {
                val nextStatus = if (isTransientHttpStatus(response?.code())) "pending" else "failed"
                messageDao.updateMessageStatus(tempId, nextStatus)
                if (nextStatus == "pending") scheduleSync()
                return@withContext Result.failure(Exception("Failed to send message: $errorStr"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage exception", e)
            val nextStatus = if (isTransientException(e)) "pending" else "failed"
            messageDao.updateMessageStatus(tempId, nextStatus)
            if (nextStatus == "pending") scheduleSync()
            Result.failure(e)
        }
    }


    // Stream new messages from Supabase Realtime channel and insert into Room (ciphertext) and emit


    suspend fun markThreadDelivered(chatId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (com.example.data.repository.PrivacyManager.isPremiumFeatureActive("hide_double_ticks_received")) {
            return@withContext Result.success(true)
        }
        if (!SupabaseClient.isConfigured) return@withContext Result.success(true)
        try {
            val service = SupabaseClient.apiService ?: return@withContext Result.success(false)
            val identity = resolveChatIdentity(chatId)
            val targetThreadId = identity.threadId ?: chatId
            val response = runCall { auth ->
                service.markThreadDelivered(
                    apiKey = SupabaseClient.supabaseAnonKey,
                    authorization = auth,
                    params = mapOf("p_thread_id" to targetThreadId)
                )
            }
            if (response != null && response.isSuccessful) {
                messageDao.markChatMessagesAsDelivered(
                    chatId = chatId,
                    myUserId = SupabaseClient.currentUser?.id.orEmpty(),
                    deliveredAt = SupabaseClient.getNowIsoString()
                )
                return@withContext Result.success(true)
            }
            Result.success(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking thread delivered: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    suspend fun markVisibleMessagesAsRead(chatId: String, visibleIds: List<String>): Result<Boolean> = withContext(Dispatchers.IO) {
        if (visibleIds.isEmpty()) return@withContext Result.success(true)
        val myUserId = SupabaseClient.currentUser?.id.orEmpty()
        val watermark = messageDao.getMessagesByIds(chatId, visibleIds)
            .asSequence()
            .filter { it.senderId != myUserId }
            .map { it.createdAt }
            .maxOrNull()
            ?: return@withContext Result.success(true)
        markThreadRead(chatId, watermark)
    }

    suspend fun markThreadRead(chatId: String, watermarkCreatedAt: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        val currentUid = SupabaseClient.currentUser?.id ?: ""
        val nowStr = SupabaseClient.getNowIsoString()
        try {
            if (watermarkCreatedAt.isNullOrBlank()) {
                messageDao.markChatMessagesAsRead(chatId, currentUid, nowStr)
            } else {
                messageDao.markChatMessagesAsReadThrough(chatId, currentUid, watermarkCreatedAt, nowStr)
            }
            // Recompute the unread badge from the actual message state instead of
            // zeroing it globally. A progressive read watermark should only clear
            // the messages it actually marks as seen; the chat badge must reflect
            // the remaining unread count on the server side.
            messageDao.recomputeUnreadCountForChat(chatId, currentUid)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating local messages as read: ${e.localizedMessage}", e)
        }
        
        val isBlueTicksHidden = com.example.data.repository.PrivacyManager.isPremiumFeatureActive("hide_blue_ticks")
        val sendOnReply = com.example.data.repository.PrivacyManager.isPremiumFeatureActive("send_blue_tick_on_reply")

        // Free privacy toggle (Centro de Privacidad): if the user disabled read
        // receipts we keep the local Room update but never ack the server.
        val readReceiptsEnabled = try {
            val ctx = com.example.PanaApplication.instance
            val prefs = ctx.getSharedPreferences("panalink_prefs", android.content.Context.MODE_PRIVATE)
            prefs.getBoolean("privacy_read_receipts_$currentUid", true)
        } catch (e: Exception) {
            true
        }

        if (isBlueTicksHidden || sendOnReply || !readReceiptsEnabled) {
            return@withContext Result.success(true)
        }
        if (!SupabaseClient.isConfigured) return@withContext Result.success(true)
        val previousWatermark = confirmedReadWatermarks[chatId]
        if (!watermarkCreatedAt.isNullOrBlank() && previousWatermark != null && previousWatermark >= watermarkCreatedAt) {
            return@withContext Result.success(true)
        }
        try {
            val service = SupabaseClient.apiService ?: return@withContext Result.success(false)
            val identity = resolveChatIdentity(chatId)
            val targetThreadId = identity.threadId ?: chatId
            val response = runCall { auth ->
                if (watermarkCreatedAt.isNullOrBlank()) {
                    service.markThreadRead(
                        apiKey = SupabaseClient.supabaseAnonKey,
                        authorization = auth,
                        params = mapOf("p_thread_id" to targetThreadId)
                    )
                } else {
                    service.markThreadReadThrough(
                        apiKey = SupabaseClient.supabaseAnonKey,
                        authorization = auth,
                        params = mapOf(
                            "p_thread_id" to targetThreadId,
                            "p_before" to watermarkCreatedAt
                        )
                    )
                }
            }

            if (response != null && response.isSuccessful) {
                if (!watermarkCreatedAt.isNullOrBlank()) {
                    confirmedReadWatermarks[chatId] = watermarkCreatedAt
                }
                return@withContext Result.success(true)
            }
            Result.success(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking thread read: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    /** Marks messages read ONLY in Room (clears badges) without notifying the server. */
    suspend fun markThreadReadLocally(chatId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val currentUid = SupabaseClient.currentUser?.id ?: ""
        val nowStr = SupabaseClient.getNowIsoString()
        try {
            messageDao.markChatMessagesAsRead(chatId, currentUid, nowStr)
            messageDao.recomputeUnreadCountForChat(chatId, currentUid)
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating local messages as read: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    suspend fun consumeGhostMessage(messageId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val nowStr = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())
        try {
            // Update local Room database immediately
            messageDao.updateGhostOpenedAt(messageId, nowStr)
            
            val currentUid = SupabaseClient.currentUser?.id
            if (currentUid != null && SupabaseClient.isConfigured) {
                val service = SupabaseClient.apiService
                if (service != null) {
                    runCall { auth ->
                        service.updateThreadMessage(
                            apiKey = SupabaseClient.supabaseAnonKey,
                            authorization = auth,
                            idFilter = "eq.$messageId",
                            updates = mapOf("ghost_opened_at" to nowStr)
                        )
                    }
                }
            }
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error consuming ghost message: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

suspend fun deleteMessageDefinitively(messageId: String): Result<Boolean> =withContext(Dispatchers.IO) {
        // VCDN cleanup: capture local media before physical row deletion.

        try {
            val existing = messageDao.getMessageById(messageId)
            if (existing != null) {
                VcdnDeleter.deleteVideos(mediaUrls = listOfNotNull(existing.mediaUrl, existing.thumbnailUrl))
            }
        } catch (e: Exception) {
            Log.e(TAG, "VCDN cleanup on definitive delete failed: ${e.localizedMessage}", e)
        }

        try {
            messageDao.deleteMessageById(messageId)        } catch (e: Exception) {
            Log.e(TAG, "Error deleting local message: ${e.localizedMessage}", e)
        }

        if (!SupabaseClient.isConfigured) return@withContext Result.success(true)

        try {
            val service = SupabaseClient.apiService ?: return@withContext Result.success(false)
            val response = runCall { auth ->
                service.deleteThreadMessage(
                    apiKey = SupabaseClient.supabaseAnonKey,
                    authorization = auth,
                    idFilter = "eq.$messageId"
                )
            }
            if (response != null && response.isSuccessful) {
                return@withContext Result.success(true)
            }
            Result.success(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error definitively deleting message: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    suspend fun clearChat(chatId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val nowStr = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { 
            timeZone = java.util.TimeZone.getTimeZone("UTC") 
        }.format(java.util.Date())
        localClearedAtMap[chatId] = nowStr

        try {
            messageDao.clearChatMessages(chatId)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing local chat messages: ${e.localizedMessage}", e)
        }

        if (!SupabaseClient.isConfigured) return@withContext Result.success(true)

        try {
            val service = SupabaseClient.apiService ?: return@withContext Result.success(false)
            val apiKey = SupabaseClient.supabaseAnonKey
            val currentUser = com.example.data.supabase.SupabaseClient.currentUser?.id 
                ?: return@withContext Result.failure(Exception("No user logged in"))

            // Upsert chat member row to update last_cleared_at
            val upsertRes = runCall { auth ->
                service.upsertChatMemberMap(
                    apiKey = apiKey,
                    authorization = auth,
                    prefer = "resolution=merge-duplicates",
                    memberData = mapOf("chat_id" to chatId, "user_id" to currentUser, "last_cleared_at" to nowStr)
                )
            }

            if (upsertRes?.isSuccessful == true) {
                return@withContext Result.success(true)
            }

            // Fallback to updateChatParticipant
            val result = runCall { auth -> 
                service.updateChatParticipant(
                    apiKey = apiKey,
                    authorization = auth,
                    chatIdFilter = "eq.$chatId",
                    userIdFilter = "eq.$currentUser",
                    updates = mapOf<String, Any>("last_cleared_at" to nowStr)
                ) 
            }
            
            if (result?.isSuccessful == true) {
                Result.success(true)
            } else {
                Log.e(TAG, "Error from updateChatParticipant (clearChat): ${result?.errorBody()?.string()}")
                // Try fallback
                val fallbackResult = runCall { auth -> 
                    service.clearChatRpc(
                        apiKey = apiKey,
                        authorization = auth,
                        params = mapOf("p_chat_id" to chatId)
                    ) 
                }
                if (fallbackResult?.isSuccessful == true) {
                    Result.success(true)
                } else {
                    Result.success(true) // Treat as success since local cache & map updated
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error personal clearing chat messages: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    suspend fun editMessage(messageId: String, newContent: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            messageDao.markMessageEditPending(messageId, newContent)
        } catch (e: Exception) {
            Log.e(TAG, "Error editing local message content: ${e.localizedMessage}", e)
        }

        if (!SupabaseClient.isConfigured) {
            val index = SupabaseClient.demoMessages.indexOfFirst { it.id == messageId }
            if (index != -1) {
                val old = SupabaseClient.demoMessages[index]
                val updated = old.copy(content = newContent, isEdited = true)
                SupabaseClient.demoMessages[index] = updated
                SupabaseClient.emitRealtimeMessage(updated)
            }
            return@withContext Result.success(true)
        }

        try {
            val service = SupabaseClient.apiService ?: return@withContext Result.success(false)
            val response = runCall { auth ->
                service.updateThreadMessage(
                    apiKey = SupabaseClient.supabaseAnonKey,
                    authorization = auth,
                    idFilter = "eq.$messageId",
                    updates = mapOf(
                        "text_content" to newContent
                    )
                )
            }
            if (response != null && response.isSuccessful) {
                messageDao.clearMessageEditPending(messageId)
                return@withContext Result.success(true)
            }
            Result.success(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error editing message: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

suspend fun deleteMessageForEveryone(messageId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        // VCDN cleanup: capture local media references before soft-delete.
        try {
            val existing = messageDao.getMessageById(messageId)
            if (existing != null) {
                VcdnDeleter.deleteVideos(mediaUrls = listOfNotNull(existing.mediaUrl, existing.thumbnailUrl))
            }
        } catch (e: Exception) {
            Log.e(TAG, "VCDN cleanup on delete-for-everyone failed: ${e.localizedMessage}", e)
        }

        val nowStr = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())
        try {
            messageDao.markMessageDeletePending(messageId, nowStr)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating local message to deleted: ${e.localizedMessage}", e)
        }

        if (!SupabaseClient.isConfigured) {
            val index = SupabaseClient.demoMessages.indexOfFirst { it.id == messageId }
            if (index != -1) {
                val old = SupabaseClient.demoMessages[index]
                val updated = old.copy(status = "deleted")
                SupabaseClient.demoMessages[index] = updated
                SupabaseClient.emitRealtimeMessage(updated)
            }
            return@withContext Result.success(true)
        }

        try {
            val service = SupabaseClient.apiService ?: return@withContext Result.success(false)
            val response = runCall { auth ->
                service.updateThreadMessage(
                    apiKey = SupabaseClient.supabaseAnonKey,
                    authorization = auth,
                    idFilter = "eq.$messageId",
                    updates = mapOf(
                        "status" to "deleted",
                        "deleted_at" to nowStr
                    )
                )
            }
            if (response != null && response.isSuccessful) {
                messageDao.clearMessageDeletePending(messageId)
                return@withContext Result.success(true)
            }
            Result.success(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting message for everyone: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteMessageForMe(messageId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        userDeletedMessageIds.add(messageId)
        try {
            messageDao.deleteMessageById(messageId)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting local message for me: ${e.localizedMessage}", e)
        }

        if (!SupabaseClient.isConfigured) {
            return@withContext Result.success(true)
        }

        try {
            val service = SupabaseClient.apiService ?: return@withContext Result.success(true)
            val response = runCall { auth ->
                service.deleteMessageForMeRpc(
                    apiKey = SupabaseClient.supabaseAnonKey,
                    authorization = auth,
                    params = mapOf("p_message_id" to messageId)
                )
            }
            if (response != null && response.isSuccessful) {
                Result.success(true)
            } else {
                Log.w(TAG, "deleteMessageForMe: RPC returned code ${response?.code()}")
                Result.success(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing delete_message_for_me RPC: ${e.localizedMessage}", e)
            Result.success(true)
        }
    }


    suspend fun toggleMessageFavorite(message: Message): Result<Boolean> =
            favoritesDataSource.toggleMessageFavorite(message)

    fun getFavoritedMessagesFlow(): kotlinx.coroutines.flow.Flow<List<Message>> =
            favoritesDataSource.getFavoritedMessagesFlow()

    suspend fun syncFavorites(): Result<Unit> =
            favoritesDataSource.syncFavorites()
    private fun simulateDemoReply(chatId: String, userMessage: String) {
        val otherMemberId = SupabaseClient.demoChatMembers
            .firstOrNull { it.chatId == chatId && it.userId != "me_demo_id" }?.userId ?: return

        val replyText = when {
            userMessage.contains("hola", ignoreCase = true) || userMessage.contains("épa", ignoreCase = true) -> {
                "¡Épa mi pana! ¿Todo fino? ¿Cómo van las cosas por allá?"
            }
            userMessage.contains("cerveza", ignoreCase = true) || userMessage.contains("fría", ignoreCase = true) || userMessage.contains("🍻") -> {
                "¡Uff de una! Activemoooos 🍻 unas polarcitas bien frías, tú pones los hielos mano!"
            }
            userMessage.contains("arepa", ignoreCase = true) || userMessage.contains("comida", ignoreCase = true) -> {
                "De pana que me comería una de carne mechada con full queso amarillo, ¡qué sabroso! 🤤"
            }
            else -> {
                "¡Buenísimo mi pana! Háblame, ¿qué más cuentas?"
            }
        }

        repositoryScope.launch {
            SupabaseClient.emitRealtimeTyping(SupabaseClient.TypingStatus(chatId, otherMemberId, true))
            delay(2000)
            SupabaseClient.emitRealtimeTyping(SupabaseClient.TypingStatus(chatId, otherMemberId, false))
            delay(300)

            val replyMessage = Message(
                id = "msg_sim_${UUID.randomUUID().toString().take(6)}",
                chatId = chatId,
                senderId = otherMemberId,
                content = replyText,
                createdAt = SupabaseClient.getNowIsoString(),
                status = "read"
            )
            
            messageDao.insertMessage(MessageEntity.fromMessage(replyMessage))
            SupabaseClient.demoMessages.add(replyMessage)
            SupabaseClient.emitRealtimeMessage(replyMessage)
        }
    }

    private fun triggerSendPushNotification(chatId: String, recipientUserId: String, title: String, bodyText: String) {
        repositoryScope.launch {
            if (!SupabaseClient.isConfigured) {
                Log.d(TAG, "[Demo Mode] triggerSendPushNotification to $recipientUserId, title=$title, body=$bodyText")
                return@launch
            }
            try {
                val service = SupabaseClient.apiService ?: return@launch
                
                val baseUrl = SupabaseClient.supabaseUrl.trim().removeSuffix("/")
                val edgeFunctionUrl = if (baseUrl.contains(".supabase.co")) {
                    baseUrl.replace(".supabase.co", ".functions.supabase.co") + "/send-push"
                } else {
                    "$baseUrl/functions/v1/send-push"
                }
                
                val body = mapOf(
                    "user_id" to recipientUserId,
                    "title" to title,
                    "body" to bodyText,
                                "chat_id" to chatId,
                    "chatId" to chatId,
                    "notification_type" to "new_message",
                    "notificationType" to "new_message"
                )
                
                val authHeader = if (!SupabaseClient.currentToken.isNullOrEmpty()) {
                    "Bearer ${SupabaseClient.currentToken}"
                } else {
                    "Bearer ${SupabaseClient.supabaseAnonKey}"
                }

                Log.d(TAG, "Calling send-push edge function at $edgeFunctionUrl for recipient $recipientUserId")
                val response = service.callEdgeFunction(
                    url = edgeFunctionUrl,
                    apiKey = SupabaseClient.supabaseAnonKey,
                    authorization = authHeader,
                    body = body
                )
                
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully invoked send-push Edge Function")
                } else {
                    val errMsg = response.errorBody()?.string() ?: "Unknown error"
                    Log.e(TAG, "Failed to invoke send-push Edge Function: $errMsg (code=${response.code()})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception calling send-push edge function", e)
            }
        }
    }

    // Only remote URLs may be sent to Supabase; local thumbnails are filesystem paths
    // that other devices can never load.
    private fun remoteThumbnail(thumb: String?): String? = thumb?.takeIf { it.startsWith("http") }

    private fun isValidUuid(uuidStr: String?): Boolean {
        if (uuidStr.isNullOrEmpty()) return false
        return try {
            java.util.UUID.fromString(uuidStr)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun formatSupabaseError(errorBodyStr: String?): String {
        if (errorBodyStr.isNullOrEmpty()) return "Unknown error"
        return try {
            val json = org.json.JSONObject(errorBodyStr)
            val message = json.optString("message", "")
            val code = json.optString("code", "")
            val details = json.optString("details", "")
            val hint = json.optString("hint", "")
            
            val sb = StringBuilder()
            if (message.isNotEmpty()) sb.append("Message: ").append(message)
            if (code.isNotEmpty()) sb.append(" (Code: ").append(code).append(")")
            if (details.isNotEmpty() && details != "null") sb.append(" | Details: ").append(details)
            if (hint.isNotEmpty() && hint != "null") sb.append(" | Hint: ").append(hint)
            
            if (sb.isEmpty()) errorBodyStr else sb.toString()
        } catch (e: Exception) {
            errorBodyStr
        }
    }

    suspend fun logDebug(etapa: String, info: String) {
        Log.d(TAG, "[DIAGNOSTIC_LOG] $etapa: $info")
    }

    // In-memory chronological ordering. SQLite orders by the raw ISO string,
    // which breaks when Postgres mixes 'Z' and '+00:00' suffixes or drops the
    // millis for fast messages. This re-sorts by epoch and is STABLE: messages
    // with equal/unparseable timestamps keep their insertion order (the local
    // optimistic message is created before its in-flight mirror, so the
    // optimistic one wins the position when the timestamps tie).
    private fun List<Message>.stableSortedByCreatedAt(): List<Message> {
        val comparator: java.util.Comparator<Message> = java.util.Comparator { a: Message, b: Message ->
            val t = timestampEpochMilli(a.createdAt).compareTo(timestampEpochMilli(b.createdAt))
            if (t != 0) {
                t
            } else {
                // Stability fallback: tie-break by a composite of uuid+content so
                // the sort stays deterministic even for identical timestamps.
                val byUuid = a.clientMessageUuid.compareTo(b.clientMessageUuid)
                if (byUuid != 0) byUuid else a.id.compareTo(b.id)
            }
        }
        return sortedWith(comparator)
    }

    private fun timestampEpochMilli(ts: String?): Long {
        if (ts.isNullOrEmpty()) return 0L
        return try {
            java.time.Instant.parse(ts).toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.OffsetDateTime.parse(ts).toInstant().toEpochMilli()
            } catch (_: Exception) {
                try {
                    val cleaned = ts.replace(" ", "T")
                    if (!cleaned.contains("Z") && !cleaned.contains("+")) {
                        java.time.LocalDateTime.parse(cleaned).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
                    } else {
                        java.time.OffsetDateTime.parse(cleaned).toInstant().toEpochMilli()
                    }
                } catch (_: Exception) {
                    0L
                }
            }
        }
    }

    private fun parseToEpochMilli(ts: String?): Long {
        if (ts.isNullOrEmpty()) return 0L
        return timestampEpochMilli(ts)
    }

    private fun isTimestampBeforeOrEqual(ts1: String?, ts2: String?): Boolean {
        if (ts1.isNullOrEmpty() || ts2.isNullOrEmpty()) return false
        val t1 = parseToEpochMilli(ts1)
        val t2 = parseToEpochMilli(ts2)
        if (t1 > 0L && t2 > 0L) {
            return t1 <= t2
        }
        return ts1 <= ts2
    }

    private fun sanitizeLogBody(body: String?): String {
        if (body.isNullOrBlank()) return ""
        return body
            .replace(Regex("eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+"), "[REDACTED_TOKEN]")
            .replace(Regex("(?i)(apikey|bearer)\\s+[a-zA-Z0-9._-]+"), "$1 [REDACTED]")
            .replace(Regex("https?://[^\\s\"',]+"), "[REDACTED_URL]")
            .take(300)
    }
}
