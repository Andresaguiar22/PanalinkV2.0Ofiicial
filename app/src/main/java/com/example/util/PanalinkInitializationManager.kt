package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.call.CallManager
import com.example.data.model.Profile
import com.example.data.repository.UserKeysRepository
import com.example.notification.engine.device.DeviceIdentityManager
import com.example.service.PanalinkFirebaseMessagingService
import com.example.service.PanalinkRealtimeService
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object PanalinkInitializationManager {
    private const val TAG = "PanalinkInitManager"
    
    @Volatile
    private var initializedUserId: String? = null

    fun initializeCompleteUser(
        context: Context,
        profile: Profile,
        scope: CoroutineScope,
        onComplete: (() -> Unit)? = null
    ) {
        if (!profile.isProfileComplete) {
            Log.w(TAG, "Attempted to initialize services for incomplete profile ${profile.id}. Initialization blocked.")
            return
        }

        if (initializedUserId == profile.id) {
            Log.d(TAG, "User ${profile.id} services already initialized.")
            onComplete?.invoke()
            return
        }

        Log.i(TAG, "Initializing services for complete profile: ${profile.id} (${profile.displayName})")
        initializedUserId = profile.id

        // 0. Perform Security Audit (Shield System)
        val audit = SecurityManager.getSecurityAudit(context)
        Log.i(TAG, "Shield Security Audit: Status=${audit.status}, Score=${audit.score}/100")
        if (audit.isRooted) {
            Log.w(TAG, "SECURITY ALERT: Device is ROOTED. Application data may be compromised.")
        }
        if (audit.isEmulator) {
            Log.w(TAG, "SECURITY NOTICE: Application is running on an EMULATOR.")
        }

        // 1. Initialize CallManager signaling connection
        try {
            val callManager = CallManager.getInstance(context)
            callManager.initialize(profile.id)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize CallManager", e)
        }

        // 2. Generate and upload E2EE Public Key (a user_keys; NUNCA a
        //    device_fingerprint: esa columna la usa save-token para FCM y
        //    pisarla rompe el fallback de push del servidor)
        scope.launch(Dispatchers.IO) {
            try {
                val result = UserKeysRepository.syncPublicKey()
                if (result.isSuccess) {
                    Log.i(TAG, "Successfully registered/uploaded E2EE Public Key!")
                } else {
                    Log.e(TAG, "Failed to sync E2EE Public Key: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register E2EE Public Key", e)
            }
        }

        // 3. Start Realtime Service
        try {
            val serviceIntent = Intent(context, PanalinkRealtimeService::class.java)
            try {
                context.startService(serviceIntent)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to start PanalinkRealtimeService via startService", e)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start service", e)
        }

        // 4. Fetch and upload Firebase Cloud Messaging token
        try {
            // Primero intentar con el token guardado localmente (por si onNewToken se disparó sin login)
            val savedToken = PanalinkFirebaseMessagingService.getSavedToken(context)
            if (!savedToken.isNullOrEmpty() && savedToken != "no_token") {
                Log.d(TAG, "Found saved FCM token, uploading to Supabase...")
                PanalinkFirebaseMessagingService.sendTokenToSupabase(context, savedToken, profile.id)
            }

            // Luego intentar obtener token fresco de Firebase
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        if (!token.isNullOrEmpty()) {
                            val pushToken = DeviceIdentityManager.getInstance().getPushToken(context, token)
                            PanalinkFirebaseMessagingService.saveToken(context, pushToken)
                            PanalinkFirebaseMessagingService.sendTokenToSupabase(context, pushToken, profile.id)
                        }
                    } else {
                        Log.w(TAG, "Fetching FCM registration token unavailable: ${task.exception?.message}")
                        // Fallback: usar token guardado si existe
                        if (!savedToken.isNullOrEmpty() && savedToken != "no_token") {
                            PanalinkFirebaseMessagingService.sendTokenToSupabase(context, savedToken, profile.id)
                        } else {
                            val fallbackToken = DeviceIdentityManager.getInstance().getPushToken(context)
                            PanalinkFirebaseMessagingService.saveToken(context, fallbackToken)
                            PanalinkFirebaseMessagingService.sendTokenToSupabase(context, fallbackToken, profile.id)
                        }
                    }
                }
        } catch (e: Throwable) {
            Log.w(TAG, "FCM not available on device: ${e.message}")
            val savedToken = PanalinkFirebaseMessagingService.getSavedToken(context)
            if (!savedToken.isNullOrEmpty() && savedToken != "no_token") {
                PanalinkFirebaseMessagingService.sendTokenToSupabase(context, savedToken, profile.id)
            } else {
                val fallbackToken = DeviceIdentityManager.getInstance().getPushToken(context)
                PanalinkFirebaseMessagingService.saveToken(context, fallbackToken)
                PanalinkFirebaseMessagingService.sendTokenToSupabase(context, fallbackToken, profile.id)
            }
        }

        onComplete?.invoke()
    }

    fun reset() {
        initializedUserId = null
    }
}
