package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import coil.imageLoader
import coil.request.ImageRequest
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object PanaLinkNotificationManager {
    private const val TAG = "PanaLinkNotificationManager"
    const val CHANNEL_MESSAGES = "panalink_messages_v3"
    
    // Remote Input Key
    const val KEY_TEXT_REPLY = "key_text_reply"

    fun showUploadSuccessNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("PanaLink")
            .setContentText("¡Hola! 🚀 Tu video se ha publicado correctamente y ya está disponible para que todos lo vean.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setColor(0xFF00FF85.toInt())

        notificationManager.notify(1001, builder.build())
    }

    fun showChatNotification(
        context: Context,
        senderName: String,
        senderAvatarUrl: String?,
        messageText: String,
        chatId: String,
        senderId: String = "",
        extras: Map<String, String> = emptyMap()
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        CoroutineScope(Dispatchers.IO).launch {
            // Load large icon (avatar)
            var largeIconBitmap: Bitmap? = null
            if (!senderAvatarUrl.isNullOrEmpty()) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(senderAvatarUrl)
                        .size(512, 512)
                        .build()
                    val result = context.imageLoader.execute(request)
                    largeIconBitmap = result.drawable?.toBitmap()
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading avatar: ${e.message}")
                }
            }

            // Fallback to initials if bitmap failed
            val finalIcon = largeIconBitmap ?: NotificationHelper.createInitialsBitmap(context, senderName)

            // Intent to open chat using canonical thread_id.
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("thread_id", chatId)
                putExtra("threadId", chatId)
                putExtra("chatId", chatId)
                putExtra("chat_id", chatId)
                if (senderId.isNotEmpty()) {
                    putExtra("otherUserId", senderId)
                    putExtra("sender_id", senderId)
                    putExtra("senderId", senderId)
                }
                extras.forEach { (key, value) -> putExtra(key, value) }
                putExtra("notification_type", "new_message")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                chatId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // RemoteInput for Reply
            val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel("Responder...")
                .build()

            val replyIntent = Intent(context, NotificationReplyReceiver::class.java).apply {
                 action = "ACTION_REPLY"
                 putExtra("chatId", chatId)
                 putExtra("thread_id", chatId)
                 putExtra("threadId", chatId)
                 putExtra("senderId", senderId)
                 putExtra("sender_id", senderId)
                 putExtra("notificationId", chatId.hashCode())
            }
            val replyPendingIntent = PendingIntent.getBroadcast(
                context,
                chatId.hashCode() + 1,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            val replyAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_send,
                "Responder",
                replyPendingIntent
            ).addRemoteInput(remoteInput).build()

            // Sender person
            val senderPerson = Person.Builder()
                .setName(senderName)
                .setIcon(IconCompat.createWithBitmap(finalIcon))
                .build()

            val messagingStyle = NotificationCompat.MessagingStyle(senderPerson)
                .addMessage(messageText, System.currentTimeMillis(), senderPerson)

            val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(senderName)
                .setContentText(messageText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setStyle(messagingStyle)
                .setLargeIcon(finalIcon)
                .setContentIntent(pendingIntent)
                .addAction(replyAction)
                .setAutoCancel(true)
                .setColor(0xFF00FF85.toInt())

            notificationManager.notify(chatId.hashCode(), builder.build())
        }
    }
}
