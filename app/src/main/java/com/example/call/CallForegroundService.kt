package com.example.call

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.MainActivity

/**
 * CallForegroundService keeps active WebRTC calls persistent in the background
 * with a high-priority notification and foregroundServiceType="phoneCall".
 *
 * For incoming calls it also drives the device ringtone + vibration loop so the
 * phone audibly rings on an FCM-delivered call (stopped on accept/reject/stop).
 */
class CallForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "panalink_call_channel"
        private const val CHANNEL_NAME = "PanaLink Calls"
        private const val NOTIFICATION_ID = 2001

        const val ACTION_START_CALL_SERVICE = "com.example.call.START_CALL_SERVICE"
        const val ACTION_STOP_CALL_SERVICE = "com.example.call.STOP_CALL_SERVICE"
        const val ACTION_END_CALL_FROM_NOTIFICATION = "com.example.call.END_CALL_NOTIFICATION"
        const val ACTION_INCOMING_CALL = "com.example.call.INCOMING_CALL"
        const val ACTION_ACCEPT_CALL = "com.example.call.ACCEPT_CALL"
        const val ACTION_REJECT_CALL = "com.example.call.REJECT_CALL"

        const val EXTRA_OPPONENT_NAME = "extra_opponent_name"
        const val EXTRA_IS_VIDEO = "extra_is_video"

        // Max time the device rings for an unanswered incoming call.
        private const val RING_TIMEOUT_MS = 45_000L

        fun startService(context: Context, opponentName: String = "Llamada PanaLink", isVideo: Boolean = false) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_START_CALL_SERVICE
                putExtra(EXTRA_OPPONENT_NAME, opponentName)
                putExtra(EXTRA_IS_VIDEO, isVideo)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startIncomingCall(context: Context, opponentName: String, isVideo: Boolean) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_INCOMING_CALL
                putExtra(EXTRA_OPPONENT_NAME, opponentName)
                putExtra(EXTRA_IS_VIDEO, isVideo)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_STOP_CALL_SERVICE
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    // Ringtone + vibration loop for incoming calls.
    private var ringtonePlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val ringHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val stopRingingRunnable = Runnable { stopRinging(); stopSelf() }

    private fun startRinging() {
        stopRinging()
        try {
            // Prefer the user's actual ringtone; fall back to the system default
            // ringtone URI if none is set (some devices/configs return null).
            var uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
            if (uri == null) {
                uri = Settings.System.DEFAULT_RINGTONE_URI
            }
            if (uri != null) {
                ringtonePlayer = MediaPlayer().apply {
                    setDataSource(this@CallForegroundService, uri)
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    setOnPreparedListener { it.start() }
                    prepareAsync()
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("CallForegroundService", "ringtone playback failed", e)
        }
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VibratorManager::class.java))?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            }
            val pattern = longArrayOf(0, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            android.util.Log.w("CallForegroundService", "vibration failed", e)
        }
        ringHandler.removeCallbacks(stopRingingRunnable)
        ringHandler.postDelayed(stopRingingRunnable, RING_TIMEOUT_MS)
    }

    private fun stopRinging() {
        ringHandler.removeCallbacks(stopRingingRunnable)
        try { ringtonePlayer?.let { if (it.isPlaying) it.stop(); it.release() } } catch (_: Exception) {}
        ringtonePlayer = null
        try { vibrator?.cancel() } catch (_: Exception) {}
        vibrator = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CALL_SERVICE -> {
                val opponentName = intent.getStringExtra(EXTRA_OPPONENT_NAME) ?: "Llamada PanaLink"
                val isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)
                startCallForeground(opponentName, isVideo)
            }
            ACTION_INCOMING_CALL -> {
                val opponentName = intent.getStringExtra(EXTRA_OPPONENT_NAME) ?: "Llamada PanaLink"
                val isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)
                showIncomingCallNotification(opponentName, isVideo)
                startRinging()
            }
            ACTION_ACCEPT_CALL -> {
                stopRinging()
                CallManager.getInstance(applicationContext).acceptCall()
                val opponentName = intent.getStringExtra(EXTRA_OPPONENT_NAME) ?: "Llamada PanaLink"
                val isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)
                startCallForeground(opponentName, isVideo)
            }
            ACTION_REJECT_CALL -> {
                stopRinging()
                CallManager.getInstance(applicationContext).rejectCall()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_STOP_CALL_SERVICE -> {
                stopRinging()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_END_CALL_FROM_NOTIFICATION -> {
                stopRinging()
                CallManager.getInstance(applicationContext).endCall()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun showIncomingCallNotification(opponentName: String, isVideo: Boolean) {
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            100,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val acceptIntent = Intent(this, CallForegroundService::class.java).apply {
            action = ACTION_ACCEPT_CALL
            putExtra(EXTRA_OPPONENT_NAME, opponentName)
            putExtra(EXTRA_IS_VIDEO, isVideo)
        }
        val acceptPendingIntent = PendingIntent.getService(
            this,
            101,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rejectIntent = Intent(this, CallForegroundService::class.java).apply {
            action = ACTION_REJECT_CALL
        }
        val rejectPendingIntent = PendingIntent.getService(
            this,
            102,
            rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callTypeLabel = if (isVideo) "Videollamada entrante" else "Llamada de voz entrante"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(opponentName)
            .setContentText(callTypeLabel)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(
                android.R.drawable.ic_menu_call,
                "Aceptar",
                acceptPendingIntent
            )
            .addAction(
                android.R.drawable.ic_delete,
                "Rechazar",
                rejectPendingIntent
            )
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            android.util.Log.e("CallForegroundService", "Error starting incoming call FGS", e)
        }
    }

    private fun startCallForeground(opponentName: String, isVideo: Boolean) {
        val returnIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val returnPendingIntent = PendingIntent.getActivity(
            this,
            0,
            returnIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val endCallIntent = Intent(this, CallForegroundService::class.java).apply {
            action = ACTION_END_CALL_FROM_NOTIFICATION
        }
        val endCallPendingIntent = PendingIntent.getService(
            this,
            1,
            endCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callTypeLabel = if (isVideo) "Videollamada en curso" else "Llamada de voz en curso"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(opponentName)
            .setContentText(callTypeLabel)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setContentIntent(returnPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_call,
                "Volver a la llamada",
                returnPendingIntent
            )
            .addAction(
                android.R.drawable.ic_delete,
                "Colgar",
                endCallPendingIntent
            )
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            android.util.Log.e("CallForegroundService", "Error starting active call FGS", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Canal persistente para llamadas activas WebRTC"
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
