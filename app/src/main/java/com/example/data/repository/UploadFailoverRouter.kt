package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.UploadMediaResult
import java.io.File

/**
 * Failover CDN Panalink <-> B2 para TODA la multimedia (imagen, video, audio, docs).
 *
 * El CDN de PanaLink es el destino PRIMARIO (y preferido) para el chat: genera
 * thumbnails server-side, URL estable y sin expiración. B2 queda SOLO como respaldo
 * cuando no hay callback CDN (p.ej. llamadas que no pasan por un worker), porque su
 * cap de descargas (Class B) rompía la reproducción de los mensajes ya enviados.
 *
 * Se reintenta el CDN antes de rendirse (el servidor pudo haber guardado el archivo
 * aunque la respuesta se tarde — un timeout de OkHttp no significa que el body no
 * llegó). No se sube en paralelo a ambos ni se abre circuit breaker: duplicaría
 * datos móviles y es inútil cuando B2 está con el cap agotado.
 *
 * REACTIVACIÓN 2026-09-20: el CDN de PanaLink volvió a ser el destino primario del
 * chat tras confirmarse que el túnel trycloudflare activo (global_server_config)
 * responde /upload y /health correctamente. El callback [cdnUpload], provisto por
 * los workers, sube al CDN vía UploadRepository/uploadMediaAndThumbnail.
 */
object UploadFailoverRouter {
    private const val TAG = "UploadFailoverRouter"

    fun init(appContext: Context) {
        // Conservado por compatibilidad con PanaApplication; el router ya no
        // mantiene circuit breaker (el CDN siempre se reintenta antes de rendirse).
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
        // El CDN de PanaLink es el destino primario. B2 queda SOLO como respaldo
        // cuando el CDN no está configurado (sin callback), nunca como primer
        // fallback normal: su cap de descargas (Class B) rompe la reproducción.
        if (cdnUpload == null) {
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

        // Primer intento contra el CDN (2 intentos: el servidor pudo haber
        // guardado el archivo aunque la respuesta se tarde).
        var lastError: Throwable? = null
        var attempts = 2
        while (attempts-- > 0) {
            try {
                val res = cdnUpload!!(onProgress)
                if (res.isSuccess) {
                    Log.i(TAG, "CDN upload exitoso")
                    return res
                }
                lastError = res.exceptionOrNull()
                Log.w(TAG, "CDN intento falló (${lastError?.message}); reintentos restantes=$attempts")
            } catch (e: Exception) {
                lastError = e
                Log.e(TAG, "CDN upload lanzó excepción: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        // Después de agotar reintentos del CDN, DEvolvemos el error y dejamos que
        // el worker reintente (NO se marcará cooldown permanente ni se duplicará
        // a B2, que está con el cap de descargas roto).
        return Result.failure(lastError ?: Exception("CDN upload falló"))
    }
}
