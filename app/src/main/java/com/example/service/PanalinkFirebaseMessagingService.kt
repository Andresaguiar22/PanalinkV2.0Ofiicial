package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.repository.ProfilesRepository
import com.example.data.supabase.SupabaseClient
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import coil.imageLoader
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import androidx.core.graphics.drawable.toBitmap

class PanalinkFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "PanalinkFCM"
        const val CHANNEL_ID = "panalink_chats_channel"
        const val CHANNEL_NAME = "Mensajes de Panalink"
        const val PREFS_NAME = "panalink_fcm_prefs"
        const val KEY_FCM_TOKEN = "fcm_token"

        fun getSavedToken(context: Context): String? {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_FCM_TOKEN, null)
        }

        fun saveToken(context: Context, token: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_FCM_TOKEN, token)
                .apply()
        }

        fun clearSavedToken(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_FCM_TOKEN)
                .apply()
        }

        fun sendTokenToSupabase(context: Context, token: String, explicitUserId: String? = null) {
            val currentUserId = explicitUserId ?: SupabaseClient.currentUser?.id
            if (!currentUserId.isNullOrEmpty()) {
                Log.d(TAG, "Sending FCM token to Supabase for authenticated user")
                val profilesRepo = ProfilesRepository()
                CoroutineScope(Dispatchers.IO).launch {
                    // Solo por el camino bueno (save-token -> fcm_tokens).
                    // Escribirlo en device_fingerprint pisaba la clave E2EE
                    // que ahi quedaba de instalaciones viejas.
                    val resEdge = profilesRepo.saveFcmTokenToEdgeFunction(currentUserId, token)
                    Log.d(TAG, "FCM Edge function save-token result: ${resEdge.isSuccess}")
                }
            } else {
                Log.d(TAG, "Cannot send FCM token to Supabase, user is not logged in yet.")
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "onNewToken triggered")
        saveToken(applicationContext, token)
        sendTokenToSupabase(applicationContext, token)
    }

    override fun onUnregistered(installationId: String) {
        super.onUnregistered(installationId)
        Log.i(TAG, "FCM installation unregistered: $installationId")
        clearSavedToken(applicationContext)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "onMessageReceived from: ${remoteMessage.from}")

        serviceScope.launch {
            try {
                NotificationHelper.createNotificationChannels(applicationContext)

                if (remoteMessage.data.isNotEmpty()) {
                    Log.d(TAG, "Message data payload received")
                }

                val notificationTitle = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "Pana 💬"
                val notificationBody = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "Nueva notificación recibida"
                
                // Prioridad estricta para identidad canónica del chat:
                // 1. thread_id / threadId / p_thread_id
                // 2. chat_id / chatId / p_chat_id
                // 3. aliases legacy
                val rawThreadId = remoteMessage.data["thread_id"]
                    ?: remoteMessage.data["threadId"]
                    ?: remoteMessage.data["p_thread_id"]

                val rawChatId = remoteMessage.data["chat_id"]
                    ?: remoteMessage.data["chatId"]
                    ?: remoteMessage.data["p_chat_id"]

                val initialChatId = rawThreadId?.takeIf { it.isNotBlank() }
                    ?: rawChatId?.takeIf { it.isNotBlank() }
                    ?: ""

                val db = try { com.example.data.database.PanalinkDatabase.getDatabase(applicationContext) } catch (e: Exception) { null }
                val chatEntity = if (initialChatId.isNotEmpty()) {
                    try {
                        db?.chatDao()?.getChatByThreadId(initialChatId) ?: db?.chatDao()?.getChatById(initialChatId)
                    } catch (e: Exception) { null }
                } else null

                // El valor entregado como chatId para navegación y lógica interna DEBE ser el thread_id canónico
                val canonicalChatId = if (rawThreadId.isNullOrBlank() && !chatEntity?.threadId.isNullOrBlank()) {
                    chatEntity!!.threadId!!
                } else {
                    initialChatId
                }

                val chatId = canonicalChatId
                val stateId = remoteMessage.data["state_id"] ?: remoteMessage.data["stateId"] ?: remoteMessage.data["p_state_id"] ?: ""
                val notificationType = remoteMessage.data["notification_type"] ?: remoteMessage.data["notificationType"] ?: remoteMessage.data["p_notification_type"] ?: "new_message"
                val thumbnailUrl = remoteMessage.data["thumbnail_url"] ?: remoteMessage.data["thumbnailUrl"] ?: remoteMessage.data["p_thumbnail_url"]
                val mediaUrl = remoteMessage.data["media_url"] ?: remoteMessage.data["mediaUrl"] ?: remoteMessage.data["p_media_url"]
                val senderAvatar = remoteMessage.data["sender_avatar"] ?: remoteMessage.data["senderAvatar"] ?: remoteMessage.data["p_sender_avatar"]
                val clientMessageUuid = remoteMessage.data["client_message_uuid"] ?: remoteMessage.data["clientMessageUuid"] ?: remoteMessage.data["p_client_message_uuid"]
                val messageId = remoteMessage.data["message_id"] ?: remoteMessage.data["messageId"] ?: remoteMessage.data["id"] ?: remoteMessage.data["p_message_id"]
                val notificationId = remoteMessage.data["notification_id"] ?: remoteMessage.data["notificationId"] ?: remoteMessage.data["p_notification_id"]

                val isChatMuted = if (canonicalChatId.isNotEmpty() && (notificationType == "new_message" || notificationType == "chat_message")) {
                    chatEntity?.isMuted == true
                } else false

                if (isChatMuted) {
                    Log.d(TAG, "Chat $canonicalChatId is muted. FCM notification suppressed.")
                    return@launch
                }

                val isChatActive = (notificationType == "new_message" || notificationType == "chat_message") &&
                        SupabaseClient.isChatScreenActive &&
                        (SupabaseClient.activeChatId == canonicalChatId || (chatEntity?.id != null && SupabaseClient.activeChatId == chatEntity.id))

                if (notificationType == "new_message" || notificationType == "chat_message") {
                    // FCM is a wake/materialization signal only. Reconcile through
                    // the existing Room + HTTP sync path; never create a second
                    // local message from notification payload data.
                    try {
                        com.example.data.repository.MessagesRepository.getInstance().scheduleSync(canonicalChatId.takeIf { it.isNotBlank() })
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not schedule message reconciliation from FCM", e)
                    }
                }

                if (isChatActive) {
                    NotificationDeduplicator.markAsNotified(clientMessageUuid, messageId)
                    NotificationHelper.playActiveChatSound(applicationContext)
                    Log.d(TAG, "Active chat: skipping system notification pop.")
                    return@launch
                }

                if (notificationType == "new_message" || notificationType == "chat_message") {
                    if (!NotificationDeduplicator.shouldNotifyMessage(clientMessageUuid, messageId)) {
                        Log.d(TAG, "FCM message already notified/deduplicated (uuid=$clientMessageUuid, id=$messageId). Suppressed.")
                        return@launch
                    }
                    val senderId = remoteMessage.data["sender_id"] ?: remoteMessage.data["senderId"] ?: remoteMessage.data["p_sender_id"] ?: ""
                    // Preservar tanto sender_id como los identificadores originales en notifExtras
                    val notifExtras = mutableMapOf<String, String>()
                    if (senderId.isNotEmpty()) {
                        notifExtras["sender_id"] = senderId
                        notifExtras["senderId"] = senderId
                        notifExtras["otherUserId"] = senderId
                    }
                    if (!rawThreadId.isNullOrEmpty()) {
                        notifExtras["thread_id"] = rawThreadId
                        notifExtras["threadId"] = rawThreadId
                    }
                    if (!rawChatId.isNullOrEmpty()) {
                        notifExtras["chat_id"] = rawChatId
                        notifExtras["chatId"] = rawChatId
                    }
                    PanaLinkNotificationManager.showChatNotification(
                        context = applicationContext,
                        senderName = remoteMessage.data["sender_name"] ?: remoteMessage.data["senderName"] ?: remoteMessage.data["p_sender_name"] ?: "",
                        senderAvatarUrl = senderAvatar,
                        messageText = notificationBody,
                        chatId = canonicalChatId,
                        senderId = senderId,
                        extras = notifExtras
                    )
                } else {
                    val genericKey = notificationId?.takeIf { it.isNotEmpty() }?.let { "notif_$it" }
                        ?: "${notificationType}_${chatId}_${stateId}"
                    if (!NotificationDeduplicator.shouldNotifyGeneric(genericKey)) {
                        Log.d(TAG, "FCM generic notification already processed ($genericKey). Suppressed.")
                        return@launch
                    }
                    val channelId = when (notificationType) {
                        "new_story", "new_reel" -> NotificationHelper.CHANNEL_ALERTS
                        "system_news", "app_update", "new_content" -> NotificationHelper.CHANNEL_ALERTS
                        "friend_request", "friend_request_accepted" -> NotificationHelper.CHANNEL_ALERTS
                        "llamada_entrante" -> NotificationHelper.CHANNEL_CALLS
                        else -> NotificationHelper.CHANNEL_MESSAGES
                    }

                    val extrasMap = mutableMapOf<String, String>()
                    if (notificationType == "llamada_entrante") {
                        val callerId = remoteMessage.data["callerId"] ?: ""
                        val callerName = remoteMessage.data["callerName"] ?: ""
                        val callType = remoteMessage.data["callType"] ?: ""
                        val sdp = remoteMessage.data["sdp"] ?: ""
                        extrasMap["callerId"] = callerId
                        extrasMap["callerName"] = callerName
                        extrasMap["callType"] = callType
                        extrasMap["sdp"] = sdp
                        com.example.call.CallManager.getInstance(applicationContext)
                            .handleFCMIncomingCall(callerId, callerName, callType, sdp)
                        // The incoming-call foreground service drives the
                        // full-screen ringing UI; skip the tray notification.
                        return@launch
                    }

                    val senderName = remoteMessage.data["sender_name"] ?: remoteMessage.data["senderName"] ?: notificationTitle
                    var largeIconBitmap: android.graphics.Bitmap? = null
                    val loadUrl = thumbnailUrl ?: mediaUrl ?: senderAvatar

                    if (!loadUrl.isNullOrEmpty()) {
                        try {
                            val request = ImageRequest.Builder(applicationContext)
                                .data(loadUrl)
                                .size(512, 512)
                                .build()
                            val result = applicationContext.imageLoader.execute(request)
                            largeIconBitmap = result.drawable?.toBitmap()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading notification image: ${e.message}")
                        }
                    }

                    NotificationHelper.showNotification(
                        context = applicationContext,
                        title = if (notificationType == "new_message") senderName else notificationTitle,
                        body = notificationBody,
                        chatId = chatId,
                        stateId = stateId,
                        notificationType = notificationType,
                        channelId = channelId,
                        extras = extrasMap,
                        imageUrl = thumbnailUrl ?: mediaUrl,
                        largeIcon = largeIconBitmap,
                        senderName = senderName
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing FCM message: ${e.message}")
            }
        }
    }
}