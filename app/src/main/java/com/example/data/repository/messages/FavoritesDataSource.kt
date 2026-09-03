package com.example.data.repository.messages

import android.util.Log
import com.example.data.database.PanalinkDatabase
import com.example.data.model.Message
import com.example.PanaApplication
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Favoritos de mensajes: local-first (Room) con sync remoto best-effort
 * a thread_message_favorites. El helper [formatSupabaseError] se extrajo
 * como copia local (la fachada conserva su propia versión privada).
 *
 * Extraido de [MessagesRepository] (grupo Favoritos). La API de la fachada
 * delega aqui sin cambios de comportamiento.
 */
class FavoritesDataSource {

    private val TAG = "FavoritesDataSource"

    private val db by lazy { PanalinkDatabase.getDatabase(PanaApplication.instance) }
    private val messageDao by lazy { db.messageDao() }

    suspend fun toggleMessageFavorite(message: Message): Result<Boolean> = withContext(Dispatchers.IO) {
        val newFavorited = !message.isFavorited

        // 1. Update locally first (Optimistic update)
        try {
            messageDao.updateMessageFavoriteStatus(message.id, newFavorited)
        } catch (e: Exception) {
            Log.e("FavoritesDataSource", "Failed to update local favorite status", e)
            return@withContext Result.failure(e)
        }

        // 2. Sync to Supabase in background, don't fail if it fails
        if (!SupabaseClient.isConfigured) return@withContext Result.success(newFavorited)
        val myUserId = SupabaseClient.currentUser?.id ?: return@withContext Result.success(newFavorited)

        try {
            val service = SupabaseClient.apiService
            if (service != null) {
                if (newFavorited) {
                    runMessagesCall(TAG) { auth ->
                        service.addFavoriteMessage(
                            apiKey = SupabaseClient.supabaseAnonKey,
                            authorization = auth,
                            body = mapOf(
                                "user_id" to myUserId,
                                "message_id" to message.id
                            )
                        )
                    }
                } else {
                    runMessagesCall(TAG) { auth ->
                        service.removeFavoriteMessage(
                            apiKey = SupabaseClient.supabaseAnonKey,
                            authorization = auth,
                            userIdFilter = "eq.$myUserId",
                            messageIdFilter = "eq.${message.id}"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FavoritesDataSource", "Exception syncing favorite", e)
        }

        Result.success(newFavorited)
    }

    fun getFavoritedMessagesFlow(): Flow<List<Message>> {
        return messageDao.getFavoritedMessagesFlow().map { entities ->
            entities.map { it.toMessage() }
        }
    }

    suspend fun syncFavorites(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseClient.isConfigured) return@withContext Result.success(Unit)
        val myUserId = SupabaseClient.currentUser?.id ?: return@withContext Result.failure(Exception("Not logged in"))

        try {
            val service = SupabaseClient.apiService ?: return@withContext Result.failure(Exception("Supabase not configured"))
            val response = runMessagesCall(TAG) { auth ->
                service.getThreadMessageFavorites(
                    apiKey = SupabaseClient.supabaseAnonKey,
                    authorization = auth,
                    userId = "eq.$myUserId"
                )
            }

            if (response?.isSuccessful == true) {
                val favorites = response.body() ?: emptyList()
                val favoritedMessageIds = favorites.mapNotNull { it["message_id"] as? String }

                // Update local DB: set isFavorited = 1 for these IDs, and 0 for others that were favorited
                val currentFavorited = messageDao.getFavoritedMessages()
                currentFavorited.forEach { entity ->
                    if (!favoritedMessageIds.contains(entity.id)) {
                        messageDao.updateMessageFavoriteStatus(entity.id, false)
                    }
                }

                favoritedMessageIds.forEach { id ->
                    messageDao.updateMessageFavoriteStatus(id, true)
                }

                Result.success(Unit)
            } else {
                Result.failure(Exception(formatSupabaseError(response?.errorBody()?.string())))
            }
        } catch (e: Exception) {
            Result.failure(e)
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
}
