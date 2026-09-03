package com.example.data.repository.messages

import android.util.Log
import com.example.PanaApplication
import com.example.data.database.PanalinkDatabase
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Flush de reacciones pendientes (reactionPending=true) contra Supabase.
 * Upsert si hay ReactionEntity local; delete si no. Extraido de syncAllPendingAndUpdatedMessages
 * (bloque C). La API de la fachada delega aqui sin cambios de comportamiento.
 */
class ReactionSyncDataSource {

    private val TAG = "ReactionSyncDataSource"

    private val db by lazy { PanalinkDatabase.getDatabase(PanaApplication.instance) }
    private val messageDao by lazy { db.messageDao() }

    suspend fun syncPendingReactions(): Boolean = withContext(Dispatchers.IO) {

        val pendingMsgs = messageDao.getReactionPendingMessages()
        Log.d(TAG, "syncPendingReactions: Found ${pendingMsgs.size} messages with reactionPending = true")
        val service = SupabaseClient.apiService ?: return@withContext false
        var allSuccessful = true


        for (msg in pendingMsgs) {


        try {


                val currentUid = SupabaseClient.currentUser?.id


                if (currentUid != null) {
                    val reaction = db.reactionDao().getReaction(msg.id, currentUid)


                    if (reaction != null) {
                        val body = mapOf(
                            "thread_message_id" to msg.id,
                            "user_id" to currentUid,
                            "emoji" to reaction.emoji
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
                            messageDao.clearMessageReactionPending(msg.id)


                            Log.d(TAG, "syncPendingReactions: Cleared reactionPending (upsert) for msg ${msg.id}")
                        } else {
                            allSuccessful = false
                        }
                    } else {
                        val response = runMessagesCall(TAG) { authHeader ->



                            service.deleteMessageReaction(
                                apiKey = SupabaseClient.supabaseAnonKey,
                                authorization = authHeader,
                                threadMessageIdFilter = "eq.${msg.id}",
                                userIdFilter = "eq.$currentUid"
                            )
                        }


                        if (response != null && response.isSuccessful) {
                            messageDao.clearMessageReactionPending(msg.id)


                            Log.d(TAG, "syncPendingReactions: Cleared reactionPending (delete) for msg ${msg.id}")
                        } else {



                            allSuccessful = false
                        }
                    }
                } else {
                    messageDao.clearMessageReactionPending(msg.id)


                }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing pending reaction for msg ${msg.id}", e)
            allSuccessful = false
        }
        }
        return@withContext allSuccessful
    }
}