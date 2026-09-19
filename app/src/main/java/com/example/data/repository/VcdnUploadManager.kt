package com.example.data.repository

import android.util.Log
import com.example.data.model.UploadMediaResult
import com.example.data.supabase.SessionManager
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/**
 * Secure VCDN video uploader (Camino 1).
 *
 * The VCDN API key NEVER enters the APK. The app streams the video to the
 * `vcdn-upload` edge function in 8MB chunks; the function forwards each chunk to
 * VCDN with the key. After all chunks are uploaded, it finalizes the upload and
 * polls the transcode status; once ready, it returns a [vcdn_video_id] (the app
 * stores the stable pointer `vcdn://{videoId}`, NOT the signed streamUrl which
 * expires). Resume is driven by `bytesReceived`.
 *
 * Contract mirrors [B2UploadManager.upload] so the [VideoRouter] can swap them.
 */
object VcdnUploadManager {
    private const val TAG = "VcdnUploadManager"
    private const val FUNCTION = "/functions/v1/vcdn-upload"
    private const val CHUNK_SIZE = 8L * 1024 * 1024 // 8MB (edge function limit = 10MB body)
    private const val POLL_INTERVAL_MS = 3000L
    private const val POLL_TIMEOUT_MS = 5L * 60 * 1000L

    // Reintentos locales ante fallos transitorios de chunk (503/409/timeouts):
    // VCDN documenta estos errores como retry-safe e idempotentes (mismo offset).
    private const val CHUNK_RETRY_COUNT = 3
    private const val CHUNK_RETRY_DELAY_MS = 800L

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(150, TimeUnit.SECONDS) // close to the 150s edge CPU limit
            .writeTimeout(150, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun upload(
        file: File,
        mimeType: String,
        userId: String,
        uploadType: String,
        customFileName: String? = null,
        clientMessageUuid: String? = null,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Result<UploadMediaResult> = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() <= 0L) {
            return@withContext Result.failure(Exception("VCDN: archivo local inexistente o vacío"))
        }

        try {
            val token = ensureToken()
                ?: return@withContext Result.failure(Exception("VCDN: usuario no autenticado"))

            // 1. init (with stable identity for idempotency)
            val targetFilename = customFileName ?: file.name
            val initJson = JSONObject().apply {
                put("step", "init")
                put("filename", targetFilename)
                put("size", file.length())
                put("contentType", mimeType)
                put("title", "Panalink $uploadType $targetFilename")
                put("uploadType", uploadType)
                if (!customFileName.isNullOrBlank()) {
                    put("stableFileName", customFileName)
                    put("customFileName", customFileName)
                }
                if (!clientMessageUuid.isNullOrBlank()) {
                    put("clientMessageUuid", clientMessageUuid)
                }
            }
            val init = JSONObject(
                callEdge(
                    token,
                    initJson.toString().toRequestBody("application/json".toMediaTypeOrNull())
                )
            )
            val uploadId = init.optString("uploadId")
            val videoId = init.optString("videoId")
            val isReady = init.optBoolean("ready", false)
            val existingPoster = init.optString("posterUrl", "")
            val initialBytesReceived = init.optLong("bytesReceived", 0L)

            if (videoId.isBlank()) {
                return@withContext Result.failure(Exception("VCDN init: respuesta incompleta"))
            }

            // Si el video ya fue subido y transcodificado en un intento previo, devolver éxito inmediato
            if (isReady) {
                Log.i(TAG, "VCDN video ya completado y listo: videoId=$videoId")
                return@withContext Result.success(
                    UploadMediaResult(
                        url = "vcdn://$videoId",
                        thumbnailUrl = existingPoster.takeIf { it.isNotBlank() && !VcdnUrlResolver.isDeadPosterHost(it) },
                        mime = mimeType,
                        size = file.length(),
                        duration = 0L,
                        width = 0,
                        height = 0
                    )
                )
            }

            if (uploadId.isBlank()) {
                return@withContext Result.failure(Exception("VCDN init: falta uploadId para la subida"))
            }

            // 2. chunks (sequential; VCDN appends in order, reanuda desde initialBytesReceived si aplica)
            val total = file.length()
            var offset = initialBytesReceived.coerceIn(0L, total)
            if (offset > 0) {
                Log.i(TAG, "VCDN reanudando subida desde offset $offset / $total")
                onProgress?.invoke(offset, total)
            }

            RandomAccessFile(file, "r").use { raf ->
                while (offset < total) {
                    val len = minOf(CHUNK_SIZE, total - offset)
                    raf.seek(offset)
                    val buf = ByteArray(len.toInt())
                    raf.readFully(buf)

                    val chunkReq = Request.Builder()
                        .url(endpoint())
                        .header("apikey", SupabaseClient.supabaseAnonKey)
                        .header("Authorization", "Bearer $token")
                        .header("Content-Type", "application/octet-stream")
                        .header("x-vcdn-upload-id", uploadId)
                        .header("x-vcdn-chunk-offset", offset.toString())
                        .header("x-vcdn-chunk-length", len.toString())
                        .post(buf.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                        .build()

                    val chunkResp = executeChunkWithRetry(chunkReq, tryIndex = 0)
                    val received = chunkResp.optLong("bytesReceived", offset + len)
                    offset = maxOf(offset + len, received)
                    onProgress?.invoke(offset, total)
                }
            }

            // 3. complete
            callEdge(
                token,
                JSONObject().apply { put("step", "complete"); put("uploadId", uploadId) }
                    .toString().toRequestBody("application/json".toMediaTypeOrNull())
            )

            // 4. poll status until ready/failed/timeout
            val started = System.currentTimeMillis()
            var status = "uploaded"
            var poster = existingPoster
            while (System.currentTimeMillis() - started < POLL_TIMEOUT_MS) {
                delay(POLL_INTERVAL_MS)
                val s = JSONObject(
                    callEdge(
                        token,
                        JSONObject().apply { put("step", "status"); put("videoId", videoId) }
                            .toString().toRequestBody("application/json".toMediaTypeOrNull())
                    )
                )
                status = s.optString("status")
                poster = s.optString("posterUrl", poster)
                onProgress?.invoke(total, total)
                if (status == "ready") break
                if (status == "failed" || status == "error") {
                    return@withContext Result.failure(Exception("VCDN transcode failed"))
                }
            }
            if (status != "ready") {
                return@withContext Result.failure(Exception("VCDN timeout de transcodificación"))
            }

            Log.i(TAG, "VCDN upload successful: videoId=$videoId")
            // Store the stable pointer. The signed streamUrl is resolved at playback
            // time by VcdnUrlResolver (it expires and must not be persisted).
            Result.success(
                UploadMediaResult(
                    url = "vcdn://$videoId",
                    thumbnailUrl = poster.takeIf { it.isNotBlank() && !VcdnUrlResolver.isDeadPosterHost(it) },
                    mime = mimeType,
                    size = file.length(),
                    duration = 0L,
                    width = 0,
                    height = 0
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "VCDN upload exception", e)
            Result.failure(e)
        }
    }

    private fun endpoint(): String = SupabaseClient.supabaseUrl.trimEnd('/') + FUNCTION

    private suspend fun ensureToken(): String? {
        SessionManager.refreshSession()
        return SessionManager.getUserAuthToken() ?: SupabaseClient.currentToken
    }

    /**
     * Envía un chunk con reintentos locales para errores transitorios de red/edge
     * (503 upload_chunk_accounting_failed, 409 session_closed documentados como
     * retry-safe por VCDN; también timeouts). Reutiliza el mismo offset: el chunk
     * es idempotente (VCDN/edge deduplican por bytesReceived).
     */
    private suspend fun executeChunkWithRetry(chunkReq: Request, tryIndex: Int): JSONObject {
        try {
            client.newCall(chunkReq).execute().use { resp ->
                val b = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw Exception("VCDN chunk HTTP ${resp.code}: ${b.take(300)}")
                return JSONObject(b)
            }
        } catch (e: Exception) {
            if (tryIndex < CHUNK_RETRY_COUNT) {
                Log.w(TAG, "VCDN chunk reintentando (${tryIndex + 1}/$CHUNK_RETRY_COUNT): ${e.message?.take(120)}")
                delay(CHUNK_RETRY_DELAY_MS)
                return executeChunkWithRetry(chunkReq, tryIndex + 1)
            }
            throw e
        }
    }

    private fun callEdge(token: String, body: RequestBody): String {
        val request = Request.Builder()
            .url(endpoint())
            .header("apikey", SupabaseClient.supabaseAnonKey)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            val b = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw Exception("VCDN edge HTTP ${resp.code}: ${b.take(300)}")
            return b
        }
    }
}
