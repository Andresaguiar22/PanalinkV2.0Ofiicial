package com.example.data.repository

import android.util.Log
import com.example.data.model.UploadMediaResult
import java.io.File

/**
 * Routes PUBLIC video uploads (reels, stories, public muro posts) to VCDN, with
 * Backblaze B2 as fallback. Chat/muro privado and non-video media bypass this
 * router entirely (they keep using [UploadFailoverRouter] = CDN -> B2).
 *
 * Kill switch: flip [enabled] to `false` and every public video upload falls back
 * to B2 immediately, leaving the existing B2/CDN infrastructure untouched. No need
 * to roll back the integration.
 *
 * On a VCDN failure we fall back to B2 for THIS upload; a circuit breaker keeps
 * subsequent uploads on B2 for a cooldown window so we don't burn timeouts against
 * a VCDN that may be down (same pattern as [UploadFailoverRouter]).
 */
object VideoRouter {
    private const val TAG = "VideoRouter"
    private const val PREFS_NAME = "panalink_video_router"
    private const val KEY_VCDN_DOWN_UNTIL = "vcdn_down_until_ms"
    private const val VCDN_DOWN_COOLDOWN_MS = 15L * 60L * 1000L

    /** Kill switch / feature gate. `true` = VCDN primary (dev). Flip to `false` to route everything to B2. */
    @Volatile var enabled: Boolean = true

    @Volatile private var vcdnDownUntilMs = 0L

    fun init(appContext: android.content.Context) {
        try {
            vcdnDownUntilMs = appContext.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .getLong(KEY_VCDN_DOWN_UNTIL, 0L)
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring video router state", e)
        }
    }

    private fun isVcdnDown(): Boolean = System.currentTimeMillis() < vcdnDownUntilMs

    private fun markVcdnFailed() {
        vcdnDownUntilMs = System.currentTimeMillis() + VCDN_DOWN_COOLDOWN_MS
        persist()
        Log.w(TAG, "VCDN marcado caido por ${VCDN_DOWN_COOLDOWN_MS / 60000} min; videos publicos a B2")
    }

    private fun markVcdnHealthy() {
        if (vcdnDownUntilMs != 0L) {
            vcdnDownUntilMs = 0L
            persist()
            Log.i(TAG, "VCDN recuperado; videos publicos vuelven a VCDN")
        }
    }

    private var prefsContext: android.content.Context? = null
    fun setContext(ctx: android.content.Context) { prefsContext = ctx.applicationContext }

    private fun persist() {
        try {
            prefsContext?.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                ?.edit()?.putLong(KEY_VCDN_DOWN_UNTIL, vcdnDownUntilMs)?.apply()
        } catch (_: Exception) {}
    }

    /**
     * Upload a PUBLIC video. If the gate is on and VCDN is healthy -> VCDN; on any
     * failure (or gate off / circuit open) -> B2 fallback. Never throws: returns a
     * [Result] so the worker can handle the failure.
     */
    suspend fun uploadPublicVideo(
        file: File,
        mimeType: String,
        userId: String,
        uploadType: String,
        customFileName: String? = null,
        clientMessageUuid: String? = null,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Result<UploadMediaResult> {
        if (!enabled || isVcdnDown()) {
            Log.i(TAG, "Gate off o circuito abierto; subiendo video a B2")
            return b2Fallback(file, mimeType, userId, uploadType, customFileName, clientMessageUuid, onProgress)
        }

        val vcdnResult = try {
            VcdnUploadManager.upload(
                file = file,
                mimeType = mimeType,
                userId = userId,
                uploadType = uploadType,
                customFileName = customFileName,
                clientMessageUuid = clientMessageUuid,
                onProgress = onProgress
            )
        } catch (e: Exception) {
            Log.e(TAG, "VCDN lanzó excepción", e)
            Result.failure(e)
        }

        if (vcdnResult.isSuccess) {
            markVcdnHealthy()
            return vcdnResult
        }
        Log.w(TAG, "VCDN fallo (${vcdnResult.exceptionOrNull()?.message}); activando fallback B2")
        markVcdnFailed()
        return b2Fallback(file, mimeType, userId, uploadType, customFileName, clientMessageUuid, onProgress)
    }

    private suspend fun b2Fallback(
        file: File,
        mimeType: String,
        userId: String,
        uploadType: String,
        customFileName: String? = null,
        clientMessageUuid: String? = null,
        onProgress: ((Long, Long) -> Unit)?
    ): Result<UploadMediaResult> =
        B2UploadManager.upload(
            file = file,
            mimeType = mimeType,
            userId = userId,
            uploadType = uploadType,
            customFileName = customFileName,
            clientMessageUuid = clientMessageUuid,
            onProgress = onProgress
        )
}
