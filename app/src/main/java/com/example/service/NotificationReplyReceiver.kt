package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import android.util.Log
import com.example.data.repository.MessagesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationReplyReceiver : BroadcastReceiver() {
    private val TAG = "NotificationReplyReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getStringExtra("chatId") ?: return
        val senderId = intent.getStringExtra("senderId") ?: ""
        val notificationId = intent.getIntExtra("notificationId", -1)
        
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence(PanaLinkNotificationManager.KEY_TEXT_REPLY)?.toString()?.trim()

        if (!replyText.isNullOrBlank()) {
            Log.i(TAG, "Direct reply received for chatId=$chatId: $replyText")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    MessagesRepository.getInstance().sendMessage(
                        chatId = chatId,
                        content = replyText,
                        receiverUid = senderId.takeIf { it.isNotBlank() && it != "unknown" }
                    )
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    if (notificationId >= 0) {
                        notificationManager.cancel(notificationId)
                    } else {
                        notificationManager.cancel(chatId.hashCode())
                        notificationManager.cancel(chatId.hashCode() + 1)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send direct reply message", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
