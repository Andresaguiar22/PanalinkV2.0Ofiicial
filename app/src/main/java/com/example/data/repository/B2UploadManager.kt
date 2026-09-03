package com.example.data.repository

import android.util.Log
import com.example.data.model.UploadMediaResult
import com.example.data.supabase.SessionManager
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Secure Backblaze B2 uploader.
 *
 * The B2 Key ID / Application Key never enter the Android application. The Edge Function
 * `b2-presign-upload` issues a short-lived presigned PUT URL (device uploads the bytes
 * directly to B2) plus a long-lived presigned GET URL stored as the media's public URL
 * (B2 private bucket has no permanent public URL, so we serve via signed GETs).
 */
object B2UploadManager {
    private const val TAG = "B2UploadManager"
    private const val FUNCTION = "/functions/v1/b2-presign-upload"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun upload(
        file: File,
        mimeType: String,
        userId: String,
        uploadType: String,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Result<UploadMediaResult> = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() <= 0L) {
            return@withContext Result.failure(Exception("B2: archivo local inexistente o vacío"))
        }

        try {
            // Asegurar JWT fresco: el CDN esta caido y B2 requiere verify_jwt=true.
            // Sin esto, un JWT expirado (>1h) da 401 y la subida queda atascada.
            val refreshed = SessionManager.refreshSession()
            if (!refreshed) {
                Log.e(TAG, "Session refresh failed before B2 upload")
                return@withContext Result.failure(Exception("B2: No se pudo refrescar la sesión"))
            }
            val token = SessionManager.getUserAuthToken() ?: SupabaseClient.currentToken
            if (token.isNullOrBlank()) {
                return@withContext Result.failure(Exception("B2: usuario no autenticado"))
            }

            val presignResult = doPresign(file, mimeType, userId, uploadType, token, onProgress)
            if (presignResult.isFailure) {
                val err = presignResult.exceptionOrNull()?.message.orEmpty()
                // 401 = JWT expirado; refrescar y reintentar una vez.
                // Verificamos por mensaje O por codigo de error.
                if (err.contains("401") || presignResult.exceptionOrNull()?.message?.contains("401") == true) {
                    Log.w(TAG, "B2 presign devolvio 401; refrescando JWT y reintentando")
                    val refreshed = SessionManager.refreshSession()
                    if (refreshed) {
                        val newToken = SessionManager.getUserAuthToken() ?: SupabaseClient.currentToken
                        if (!newToken.isNullOrBlank()) {
                            val retryResult = doPresign(file, mimeType, userId, uploadType, newToken, onProgress)
                            if (retryResult.isFailure) {
                                return@withContext Result.failure(retryResult.exceptionOrNull()
                                    ?: Exception("B2 presign fallo tras refrescar JWT"))
                            }
                            return@withContext retryResult
                        }
                    }
                    return@withContext Result.failure(Exception("B2: JWT expirado y no se pudo refrescar"))
                }
                return@withContext presignResult
            }

            presignResult
        } catch (e: Exception) {
            Log.e(TAG, "B2 upload exception", e)
            Result.failure(e)
        }
    }

    private suspend fun doPresign(
        file: File,
        mimeType: String,
        userId: String,
        uploadType: String,
        token: String,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Result<UploadMediaResult> = withContext(Dispatchers.IO) {
        val endpoint = SupabaseClient.supabaseUrl.trimEnd('/') + FUNCTION
        val requestBody = JSONObject().apply {
            put("fileName", file.name)
            put("mimeType", mimeType)
            put("size", file.length())
            put("uploadType", uploadType)
            put("userId", userId)
        }.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val presignRequest = Request.Builder()
            .url(endpoint)
            .header("apikey", SupabaseClient.supabaseAnonKey)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()
        Log.i(TAG, "Executing B2 presign request to $endpoint")

        client.newCall(presignRequest).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.e(TAG, "Presign failed: HTTP ${response.code}: ${body.take(500)}")
                return@withContext Result.failure(Exception("B2 presign HTTP ${response.code}"))
            }

            val json = JSONObject(body)
            val uploadUrl = json.optString("uploadUrl")
            val publicUrl = json.optString("publicUrl")
            val resolvedMime = json.optString("mimeType", mimeType)
            if (uploadUrl.isBlank() || publicUrl.isBlank()) {
                return@withContext Result.failure(Exception("B2 presign: respuesta incompleta"))
            }

            val putBody = FileRequestBody(resolvedMime, file, onProgress)
            val putRequest = Request.Builder()
                .url(uploadUrl)
                .header("Content-Type", resolvedMime)
                .put(putBody)
                .build()

            client.newCall(putRequest).execute().use { putResponse ->
                if (!putResponse.isSuccessful) {
                    val error = putResponse.body?.string().orEmpty()
                    Log.e(TAG, "B2 PUT failed: HTTP ${putResponse.code}: ${error.take(500)}")
                    return@withContext Result.failure(Exception("B2 PUT failed: HTTP ${putResponse.code} - ${error.take(500)}"))
                } else {
                    Log.i(TAG, "B2 PUT successful: HTTP ${putResponse.code}")
                }
            }

            Log.i(TAG, "B2 upload successful: $publicUrl")
            Result.success(
                UploadMediaResult(
                    url = publicUrl,
                    thumbnailUrl = null,
                    mime = resolvedMime,
                    size = file.length(),
                    duration = 0L,
                    width = 0,
                    height = 0
                )
            )
        }
    }

    private class FileRequestBody(
        private val mime: String,
        private val file: File,
        private val onProgress: ((Long, Long) -> Unit)?
    ) : RequestBody() {
        override fun contentType() = mime.toMediaTypeOrNull()
        override fun contentLength(): Long = file.length()

        override fun writeTo(sink: BufferedSink) {
            val total = contentLength().coerceAtLeast(1L)
            val buffer = ByteArray(64 * 1024)
            var written = 0L
            file.inputStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    sink.write(buffer, 0, read)
                    written += read
                    onProgress?.invoke(written, total)
                }
            }
        }
    }
}
