package com.example.data.repository.messages

import android.util.Log
import com.example.data.database.PanalinkDatabase
import com.example.data.database.ReactionEntity
import com.example.PanaApplication
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reacciones emoji de mensajes: local-first (Room + broadcast Realtime) con
 * sync remoto best-effort a thread_message_reactions via upsert/delete.
 *
 * Extraido de [MessagesRepository] (grupo Reacciones). La API de la fachada
 * delega aqui sin cambios de comportamiento.
 */
class ReactionsDataSource {

    private val TAG = "ReactionsDataSource"
    private val db by lazy { PanalinkDatabase.getDatabase(PanaApplication.instance) }
    private val messageDao by lazy { db.messageDao() }

    suspend fun saveReaction(messageId: String, chatId: String, userId: String, emoji: String) = withContext(Dispatchers.IO) {
        // 1. Update auxiliary table as requested by user
        val reactionEntity = com.example.data.database.ReactionEntity(messageId, userId, emoji, SupabaseClient.getNowIsoString())
        db.reactionDao().insertReaction(reactionEntity)

        // 2. Update reactionsJson in MessageEntity for fast UI rendering
        val messages = messageDao.getMessagesForChat(chatId)
        val msg = messages.find { it.id == messageId }
        val reactionsMap = if (msg != null && msg.reactionsJson.isNotEmpty()) {
            try {
                org.json.JSONObject(msg.reactionsJson)
            } catch (e: Exception) {
                org.json.JSONObject()
            }
        } else {
            org.json.JSONObject()
        }

        reactionsMap.put(userId, emoji)
        val updatedJson = reactionsMap.toString()

        messageDao.markMessageReactionPending(messageId, updatedJson)
        SupabaseClient.broadcastReaction(messageId, chatId, emoji)

        if (SupabaseClient.isConfigured) {
            try {
                val service = SupabaseClient.apiService
                if (service != null) {

                    val body = mapOf(
                            "thread_message_id" to messageId,
                            "user_id" to userId,
                            "emoji" to emoji
                        )
                    val response = runMessagesCall(TAG) { authHeader ->
                        service.upsertMessageReaction(
                            apiKey = SupabaseClient.supabaseAnonKey,
                            authorization = authHeader,
                            prefer = "resolution=merge-duplicates",
                            reaction = body
                        )
                    }
                    if (response != null && response.isSuccessful) {
                        messageDao.clearMessageReactionPending(messageId)
                    }
                }
            } catch (e: Exception) {
                Log.e("ReactionsDataSource", "Failed to persist reaction on Supabase: ${e.localizedMessage}", e)
            }
        }
        }

    suspend fun deleteReaction(messageId: String, chatId: String, userId: String) = withContext(Dispatchers.IO) {
        // 1. Update auxiliary table
        db.reactionDao().deleteReaction(messageId, userId)

        // 2. Update reactionsJson in MessageEntity
        val messages = messageDao.getMessagesForChat(chatId)
        val msg = messages.find { it.id == messageId }
        val reactionsMap = if (msg != null && msg.reactionsJson.isNotEmpty()) {
            try {
                org.json.JSONObject(msg.reactionsJson)
            } catch (e: Exception) {
                org.json.JSONObject()
            }
        } else {
            org.json.JSONObject()
        }

        reactionsMap.remove(userId)
        val updatedJson = reactionsMap.toString()

        messageDao.markMessageReactionPending(messageId, updatedJson)
        SupabaseClient.broadcastReaction(messageId, chatId, "")

        if (SupabaseClient.isConfigured) {
            try {
                val service = SupabaseClient.apiService
                if (service != null) {

                    val response = runMessagesCall(TAG) { authHeader ->
                        service.deleteMessageReaction(
                                apiKey = SupabaseClient.supabaseAnonKey,
                                authorization = authHeader,
                                threadMessageIdFilter = "eq.$messageId",
                                userIdFilter = "eq.$userId"
                            )
                    }
                    if (response != null && response.isSuccessful) {
                        messageDao.clearMessageReactionPending(messageId)
                    }
                }
            } catch (e: Exception) {
                Log.e("ReactionsDataSource", "Failed to delete reaction on Supabase: ${e.localizedMessage}", e)
            }
        }
}

}
