package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.UploadMediaResult
import java.io.File

/**
 * Failover CDN Panalink <-> B2 para TODA la multimedia (imagen, video, audio, docs).
 *
 * El CDN es el destino primario (genera thumbnails server-side). Si falla, el archivo
 * va directo a Backblaze B2 (presigned URL, credenciales solo en la edge function) y se abre un
 * circuit breaker de 15 min: durante esa ventana las subidas van directo a B2 sin
 * quemar timeouts contra el CDN caido. Vencida la ventana se vuelve a probar el CDN
 * (half-open); si responde, se cierra el circuito y todo vuelve al CDN.
 * Si B2 tambien falla, se prueba el CDN como ultimo recurso.
 *
 * No se sube en paralelo a ambos: duplicaria datos moviles y almacenamiento.
 */
object UploadFailoverRouter {
    private const val TAG = "UploadFailoverRouter"
    private const val PREFS_NAME = "panalink_upload_failover"
    private const val KEY_CDN_DOWN_UNTIL = "cdn_down_until_ms"
    private const val CDN_DOWN_COOLDOWN_MS = 15L * 60L * 1000L

    private var context: Context? = null
    @Volatile private var cdnDownUntilMs = 0L

    fun init(appContext: Context) {
        if (context != null) return
        val ctx = appContext.applicationContext
        context = ctx
        try {
            cdnDownUntilMs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_CDN_DOWN_UNTIL, 0L)
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring failover state", e)
        }
    }

    fun isCdnDown(): Boolean = System.currentTimeMillis() < cdnDownUntilMs

    fun markCdnFailed() {
        cdnDownUntilMs = System.currentTimeMillis() + CDN_DOWN_COOLDOWN_MS
        persist()
        Log.w(TAG, "CDN marcado como caido por ${CDN_DOWN_COOLDOWN_MS / 60000} min; las subidas iran directo a B2")
    }

    fun markCdnHealthy() {
        if (cdnDownUntilMs != 0L) {
            cdnDownUntilMs = 0L
            persist()
            Log.i(TAG, "CDN recuperado; las subidas vuelven al CDN")
        }
    }

    private fun persist() {
        try {
            context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()?.putLong(KEY_CDN_DOWN_UNTIL, cdnDownUntilMs)?.apply()
        } catch (_: Exception) {}
    }

    suspend fun uploadWithFailover(
        file: File,
        mimeType: String,
        userId: String,
        uploadType: String,
        customFileName: String? = null,
        clientMessageUuid: String? = null,
        onProgress: ((Long, Long) -> Unit)? = null,
        cdnUpload: (suspend (onProgress: ((Long, Long) -> Unit)?) -> Result<UploadMediaResult>)? = null
    ): Result<UploadMediaResult> {
        // CDN has been decommissioned. Always use B2 as primary storage for non-public-video media.
        return B2UploadManager.upload(
            file = file,
            mimeType = mimeType,
            userId = userId,
            uploadType = uploadType,
            customFileName = customFileName,
            clientMessageUuid = clientMessageUuid,
            onProgress = onProgress
        )
    }
}
