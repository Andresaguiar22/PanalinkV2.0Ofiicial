package com.example.data.repository.messages

import android.util.Log
import com.example.data.database.MessageDao
import com.example.data.database.MessageEntity
import com.example.data.model.Message
import com.example.data.supabase.SupabaseClient
import com.example.util.CryptoManager
import com.example.util.MessageFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MessageRealtimeHandler(
    private val messageDao: MessageDao,
    private val scope: CoroutineScope,
    private val getDeletedMessageIds: () -> Set<String>,
    private val getEffectiveClearedAt: (String, String?) -> String?,
    private val repairMessage: suspend (Message) -> Message
) {
    private val TAG = "MessageRealtimeHandler"

    init {
        scope.launch {
            launch {
                SupabaseClient.realtimeMessageDeletions.collect { messageId ->
                    try {
                        messageDao.deleteMessageById(messageId)
                        Log.d(TAG, "Realtime: Deleted message $messageId from local DB")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting message $messageId from Room upon realtime signal", e)
                    }
                }
            }

            launch {
                SupabaseClient.realtimeMessages.collect { msg ->
                    try {
                        val repairedMsg = repairMessage(msg)
                        val decryptedMsg = CryptoManager.decryptMessageIfNeeded(repairedMsg)
                        val effectiveClearedAt = getEffectiveClearedAt(decryptedMsg.chatId, null)
                        val shouldKeep = MessageFilter.shouldKeepMessage(
                            messageId = decryptedMsg.id,
                            messageClientUuid = decryptedMsg.clientMessageUuid,
                            messageCreatedAt = decryptedMsg.createdAt,
                            lastClearedAt = effectiveClearedAt,
                            deletedMessageIds = getDeletedMessageIds()
                        )
                        if (shouldKeep) {
                            messageDao.mergeAndSaveMessage(MessageEntity.fromMessage(decryptedMsg))
                            Log.d(TAG, "MessageRealtimeHandler (Realtime): Merged message ${decryptedMsg.id} into Room")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "MessageRealtimeHandler (Realtime): Error processing incoming message", e)
                    }
                }
            }
        }
    }
}
