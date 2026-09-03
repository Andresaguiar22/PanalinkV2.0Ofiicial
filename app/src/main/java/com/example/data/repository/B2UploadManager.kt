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

    private fun sanitizeUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return try {
            val uri = java.net.URI(url)
            "${uri.scheme}://${uri.host}${uri.path}"
        } catch (e: Exception) {
            url.substringBefore("?").take(100)
        }
    }

    private fun sanitizeError(error: String?): String {
        if (error.isNullOrBlank()) return ""
        return error.replace(Regex("eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+"), "[REDACTED_TOKEN]")
            .replace(Regex("(?i)bearer\\s+[a-zA-Z0-9._~+/-]+"), "Bearer [REDACTED]")
            .replace(Regex("(?i)apikey=[^&\\s]+"), "apikey=[REDACTED]")
            .take(300)
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
            Log.e(TAG, "B2_EXCEPTION: exceptionClass=IllegalArgumentException, exactMessage=B2: archivo local inexistente o vacio")
            return@withContext Result.failure(Exception("B2: archivo local inexistente o vacío"))
        }

        try {
            // Asegurar JWT fresco: el CDN esta caido y B2 requiere verify_jwt=true.
            // Sin esto, un JWT expirado (>1h) da 401 y la subida queda atascada.
            val refreshed = SessionManager.refreshSession()
            if (!refreshed) {
                Log.e(TAG, "Session refresh failed before B2 upload")
                Log.e(TAG, "B2_EXCEPTION: exceptionClass=IllegalStateException, exactMessage=B2: No se pudo refrescar la sesion")
                return@withContext Result.failure(Exception("B2: No se pudo refrescar la sesión"))
            }
            val token = SessionManager.getUserAuthToken() ?: SupabaseClient.currentToken
            if (token.isNullOrBlank()) {
                Log.e(TAG, "B2_EXCEPTION: exceptionClass=IllegalStateException, exactMessage=B2: usuario no autenticado")
                return@withContext Result.failure(Exception("B2: usuario no autenticado"))
            }

            val presignResult = doPresign(file, mimeType, userId, uploadType, token, customFileName, clientMessageUuid, onProgress)
            if (presignResult.isFailure) {
                val err = presignResult.exceptionOrNull()?.message.orEmpty()
                // 401 = JWT expirado; refrescar y reintentar una vez.
                if (err.contains("401") || presignResult.exceptionOrNull()?.message?.contains("401") == true) {
                    Log.w(TAG, "B2 presign devolvio 401; refrescando JWT y reintentando")
                    val refreshedAgain = SessionManager.refreshSession()
                    if (refreshedAgain) {
                        val newToken = SessionManager.getUserAuthToken() ?: SupabaseClient.currentToken
                        if (!newToken.isNullOrBlank()) {
                            val retryResult = doPresign(file, mimeType, userId, uploadType, newToken, customFileName, clientMessageUuid, onProgress)
                            if (retryResult.isFailure) {
                                val retryEx = retryResult.exceptionOrNull() ?: Exception("B2 presign fallo tras refrescar JWT")
                                Log.e(TAG, "B2_EXCEPTION: exceptionClass=${retryEx.javaClass.name}, exactMessage=${sanitizeError(retryEx.message)}")
                                return@withContext Result.failure(retryEx)
                            }
                            return@withContext retryResult
                        }
                    }
                    Log.e(TAG, "B2_EXCEPTION: exceptionClass=IllegalStateException, exactMessage=B2: JWT expirado y no se pudo refrescar")
                    return@withContext Result.failure(Exception("B2: JWT expirado y no se pudo refrescar"))
                }
                val presignEx = presignResult.exceptionOrNull() ?: Exception("B2 presign fallo")
                Log.e(TAG, "B2_EXCEPTION: exceptionClass=${presignEx.javaClass.name}, exactMessage=${sanitizeError(presignEx.message)}")
                return@withContext presignResult
            }

            presignResult
        } catch (e: Exception) {
            Log.e(TAG, "B2_EXCEPTION: exceptionClass=${e.javaClass.name}, exactMessage=${sanitizeError(e.message)}", e)
            Result.failure(e)
        }
    }

    private suspend fun doPresign(
        file: File,
        mimeType: String,
        userId: String,
        uploadType: String,
        token: String,
        customFileName: String? = null,
        clientMessageUuid: String? = null,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Result<UploadMediaResult> = withContext(Dispatchers.IO) {
        val endpoint = SupabaseClient.supabaseUrl.trimEnd('/') + FUNCTION
        val finalFileName = customFileName ?: file.name
        val requestBody = JSONObject().apply {
            put("fileName", finalFileName)
            put("mimeType", mimeType)
            put("size", file.length())
            put("uploadType", uploadType)
            put("userId", userId)
            if (!customFileName.isNullOrBlank()) {
                put("stableFileName", customFileName)
                put("customFileName", customFileName)
            }
            if (!clientMessageUuid.isNullOrBlank()) {
                put("clientMessageUuid", clientMessageUuid)
            }
        }.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val presignRequest = Request.Builder()
            .url(endpoint)
            .header("apikey", SupabaseClient.supabaseAnonKey)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()
        Log.i(TAG, "B2_PRESIGN_START: fileName=$finalFileName, mimeType=$mimeType, sizeBytes=${file.length()}, uploadType=$uploadType, customFileName=$customFileName, clientMessageUuid=$clientMessageUuid")

        client.newCall(presignRequest).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val presignCode = response.code
            val isPresignSuccess = response.isSuccessful
            val sanitizedPresignBody = sanitizeError(body)
            Log.i(TAG, "B2_PRESIGN_RESPONSE: httpStatus=$presignCode, isSuccessful=$isPresignSuccess, error=$sanitizedPresignBody")

            if (!isPresignSuccess) {
                Log.e(TAG, "Presign failed: HTTP $presignCode: $sanitizedPresignBody")
                return@withContext Result.failure(Exception("B2 presign HTTP $presignCode: $sanitizedPresignBody"))
            }

            val json = JSONObject(body)
            val uploadUrl = json.optString("uploadUrl")
            val publicUrl = json.optString("publicUrl")
            val resolvedMime = json.optString("mimeType", mimeType)
            if (uploadUrl.isBlank() || publicUrl.isBlank()) {
                Log.e(TAG, "B2_EXCEPTION: exceptionClass=IllegalStateException, exactMessage=B2 presign: respuesta incompleta")
                return@withContext Result.failure(Exception("B2 presign: respuesta incompleta"))
            }

            val sanitizedUploadUrl = sanitizeUrl(uploadUrl)
            Log.i(TAG, "B2_PUT_START: targetHostPath=$sanitizedUploadUrl, mimeType=$resolvedMime, sizeBytes=${file.length()}")

            val putBody = FileRequestBody(resolvedMime, file, onProgress)
            val putRequest = Request.Builder()
                .url(uploadUrl)
                .header("Content-Type", resolvedMime)
                .put(putBody)
                .build()

            client.newCall(putRequest).execute().use { putResponse ->
                val putCode = putResponse.code
                val isPutSuccess = putResponse.isSuccessful
                val putError = sanitizeError(putResponse.body?.string())
                Log.i(TAG, "B2_PUT_RESPONSE: httpStatus=$putCode, isSuccessful=$isPutSuccess, error=$putError")

                if (!isPutSuccess) {
                    Log.e(TAG, "B2 PUT failed: HTTP $putCode: $putError")
                    return@withContext Result.failure(Exception("B2 PUT failed: HTTP $putCode - $putError"))
                }
            }

            val sanitizedPublicUrl = sanitizeUrl(publicUrl)
            Log.i(TAG, "B2_RESULT_URL: publicUrl=$sanitizedPublicUrl, mime=$resolvedMime, sizeBytes=${file.length()}")
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
