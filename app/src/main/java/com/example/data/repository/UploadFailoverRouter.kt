package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.UploadMediaResult
import java.io.File

/**
 * Failover CDN Panalink <-> B2 para TODA la multimedia (imagen, video, audio, docs).
 *
 * El CDN es el destino primario (genera thumbnails server-side, URL estable y sin
 * expiración). Si falla, el archivo va directo a Backblaze B2 (presigned URL,
 * credenciales solo en la edge function) y se abre un circuit breaker de 15 min:
 * durante esa ventana las subidas van directo a B2 sin quemar timeouts contra el
 * CDN caido. Vencida la ventana se vuelve a probar el CDN (half-open); si responde,
 * se cierra el circuito y todo vuelve al CDN. Si B2 tambien falla, se prueba el CDN
 * como ultimo recurso.
 *
 * No se sube en paralelo a ambos: duplicaria datos moviles y almacenamiento.
 *
 * REACTIVACIÓN 2026-09-20: el CDN de PanaLink volvió a ser el destino primario del
 * chat tras confirmarse que el túnel trycloudflare activo (global_server_config)
 * responde /upload y /health correctamente. B2 queda como fallback automático
 * (cap Class B bajo riesgo). El callback [cdnUpload], provisto por los workers,
 * sube al CDN vía UploadRepository/uploadMediaAndThumbnail.
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
        // Circuito cerrado (CDN sano): el CDN de PanaLink es el destino primario.
        // El callback [cdnUpload] sube al CDN vía UploadRepository/uploadMediaAndThumbnail.
        val cdnReady = !isCdnDown() && cdnUpload != null
        if (cdnReady) {
            val cdnResult = try {
                cdnUpload!!(onProgress)
            } catch (e: Exception) {
                Log.e(TAG, "CDN upload lanzó excepción: ${e.javaClass.simpleName}: ${e.message}")
                Result.failure(e)
            }
            if (cdnResult.isSuccess) {
                markCdnHealthy()
                return cdnResult
            }
            Log.w(TAG, "CDN fallo (${cdnResult.exceptionOrNull()?.message}); activando fallback B2")
            markCdnFailed()
        }

        // Circuito abierto (CDN en cooldown) o sin callback CDN → B2 directo.
        val b2Result = B2UploadManager.upload(
            file = file,
            mimeType = mimeType,
            userId = userId,
            uploadType = uploadType,
            customFileName = customFileName,
            clientMessageUuid = clientMessageUuid,
            onProgress = onProgress
        )
        if (b2Result.isSuccess) return b2Result

        // Último recurso: B2 falló y el CDN estaba en cooldown → intentar CDN una vez.
        if (cdnUpload != null && !cdnReady) {
            Log.w(TAG, "B2 también falló; último intento contra el CDN")
            return try {
                cdnUpload(onProgress)
            } catch (e: Exception) {
                Log.e(TAG, "Último intento CDN falló: ${e.javaClass.simpleName}: ${e.message}")
                Result.failure(e)
            }
        }
        return b2Result
    }
}
