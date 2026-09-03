package com.example.data.repository.messages

import com.example.data.model.Message
import com.example.data.supabase.SupabaseClient
import com.example.data.repository.runCall
import android.util.Log
import retrofit2.Response

class MessageRepairService {
    private val TAG = "MessageRepairService"

    suspend fun repairMessageContentIfNeeded(msg: Message): Message {
        if (!msg.content.isNullOrEmpty() || !msg.mediaUrl.isNullOrEmpty()) return msg
        if (!SupabaseClient.isConfigured) return msg
        val service = SupabaseClient.apiService ?: return msg
        return try {
            val response = if (!msg.clientMessageUuid.isNullOrBlank()) {
                runCall { auth ->
                    service.getThreadMessageByClientUuid(
                        apiKey = SupabaseClient.supabaseAnonKey,
                        authorization = auth,
                        clientUuidFilter = "eq.${msg.clientMessageUuid}"
                    )
                }
            } else {
                runCall { auth ->
                    service.getThreadMessages(
                        apiKey = SupabaseClient.supabaseAnonKey,
                        authorization = auth,
                        threadIdFilter = "eq.${msg.chatId}",
                        createdAtFilter = "gte.${msg.createdAt}",
                        limit = 1
                    )
                }
            }
            val fullMsg = response?.takeIf { it.isSuccessful }?.body()?.firstOrNull()?.toMessage()
            if (fullMsg != null && (!fullMsg.content.isNullOrEmpty() || !fullMsg.mediaUrl.isNullOrEmpty())) {
                fullMsg.copy(
                    status = msg.status ?: fullMsg.status,
                    deliveredAt = msg.deliveredAt ?: fullMsg.deliveredAt,
                    seenAt = msg.seenAt ?: fullMsg.seenAt
                )
            } else {
                msg
            }
        } catch (e: Exception) {
            Log.w(TAG, "repairMessageContentIfNeeded failed for ${msg.id}", e)
            msg
        }
    }
}
