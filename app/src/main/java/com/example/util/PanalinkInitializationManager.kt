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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object PanalinkInitializationManager {
    private const val TAG = "PanalinkInitManager"
    
    @Volatile
    private var initializedUserId: String? = null

    @Volatile
    private var initializingUserId: String? = null

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

        if (initializingUserId == profile.id) {
            Log.d(TAG, "User ${profile.id} initialization already in progress.")
            return
        }

        initializingUserId = profile.id
        Log.i(TAG, "Initializing services for complete profile: ${profile.id} (${profile.displayName})")

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

        // 2. Generate and register E2EE public key before declaring the user initialized.
        //    This removes the registration race that allowed a newly created account to
        //    enter chat without a row in public.user_keys.
        scope.launch(Dispatchers.IO) {
            var keySynced = false
            repeat(3) { attempt ->
                if (keySynced) return@repeat
                try {
                    Log.i(TAG, "E2EE public-key sync attempt ${attempt + 1}/3 for user ${profile.id}")
                    val result = UserKeysRepository.syncPublicKey()
                    if (result.isSuccess) {
                        keySynced = true
                        Log.i(TAG, "Successfully registered/uploaded E2EE Public Key for ${profile.id}")
                    } else {
                        Log.e(TAG, "E2EE public-key sync failed on attempt ${attempt + 1}/3: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "E2EE public-key sync exception on attempt ${attempt + 1}/3", e)
                }
                if (!keySynced && attempt < 2) delay(1000L * (attempt + 1))
            }

            // Services may still run if the network is unavailable, but do not hide a
            // failed key registration. The next initialization/foreground cycle can retry.
            if (keySynced) {
                initializedUserId = profile.id
            } else {
                Log.w(TAG, "E2EE public key is NOT registered for ${profile.id}; initialization remains retryable")
            }
            initializingUserId = null
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
        initializingUserId = null
    }
}