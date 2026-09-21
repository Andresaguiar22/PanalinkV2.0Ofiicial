package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.supabase.SessionManager
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Imports a CLEAN video from an external platform URL (TikTok, Instagram Reels,
 * YouTube Shorts, X...) through the Node CDN backend (`POST /import-url`).
 *
 * IMPORTANT: this importer NEVER touches B2 nor VCDN. It only downloads the original
 * clean file back into the app; publishing rides thenormal public video pipeline
 * ([VideoRouter] -> VCDN, with B2 only as existing failback infra).
 */
object SocialMediaImporter {

    private const val TAG = "SocialMediaImporter"
    private const val IMPORT_POLL_DOWNLOAD_TIMEOUT_SECONDS = 180L

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(IMPORT_POLL_DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** Platforms shown in the UI hint. */
    val supportedPlatformLabels = listOf(
        "TikTok", "Instagram Reels", "YouTube Shorts", "X / Twitter", "Facebook", "Reddit", "Pinterest"
    )

    data class ImportResult(
        val uri: Uri,
        val file: File,
        val mimeType: String,
        val sizeBytes: Long
    )

    fun isValidPlatformUrl(rawUrl: String?): Boolean {
        val url = rawUrl?.trim().orEmpty()
        if (url.isEmpty() || !url.startsWith("http") && !url.startsWith("https")) return false
        val host = runCatching { java.net.URI(url).host ?: "" }.getOrDefault("")
        return when {
            host.endsWith("tiktok.com") || host.endsWith("vm.tiktok.com") -> true
            host.endsWith("instagram.com") -> true
            host.endsWith("youtube.com") || host.endsWith("youtu.be") || host.endsWith("music.youtube.com") -> true
            host.endsWith("twitter.com") || host.endsWith("x.com") -> true
            host.endsWith("facebook.com") || host.endsWith("fb.watch") -> true
            host.endsWith("reddit.com") || host.endsWith("v.redd.it") -> true
            host.endsWith("pinterest.com") -> true
            host.endsWith("snapchat.com") -> true
            else -> false
        }
    }

    suspend fun importUrl(
        context: Context,
        rawUrl: String
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        if (!isValidPlatformUrl(url)) {
            return@withContext Result.failure(Exception("Enlace no soportado. Usa TikTok, Instagram, YouTube, X, Facebook, Reddit, Pinterest o Snapchat."))
        }

        val cdnBase = CdnManager.getCDNUrl(forceRefresh = false).trim().trimEnd('/')
        if (cdnBase.isEmpty()) {
            return@withContext Result.failure(Exception("No se pudo obtener la URL del CDN. Reintenta."))
        }
        val endpoint = "$cdnBase/import-url"

        try {
            val token = SupabaseClient.currentToken ?: return@withContext Result.failure(Exception("Sesión no disponible. Inicia sesión de nuevo."))
            val jsonBody = org.json.JSONObject().put("url", url).toString()
            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty()
                val parsed = runCatching { org.json.JSONObject(errBody).optString("error", "") }.getOrDefault("")
                val message = parsed.ifBlank { "El servidor no pudo importar ese enlace (HTTP ${response.code})." }
                if (response.code == 401 || response.code == 403) {
                    if (SessionManager.refreshSession()) {
                        return@withContext importUrl(context, url)
                    } else {
                        return@withContext Result.failure(Exception(message))
                    }
                } else {
                    return@withContext Result.failure(Exception(message))
                }
            }

            val responseBody = response.body?.string().orEmpty()
            response.close()

            if (responseBody.isBlank()) {
                return@withContext Result.failure(Exception("Respuesta vacía del servidor de importación."))
            }

            val json = runCatching { org.json.JSONObject(responseBody) }.getOrElse {
                return@withContext Result.failure(Exception("Respuesta inválida del servidor de importación."))
            }

            if (!json.optBoolean("success", false)) {
                return@withContext Result.failure(
                    Exception(json.optString("error", "No se pudo importar el vídeo."))
                )
            }

            val importedUrl = json.optString("url", "").trim()
            if (importedUrl.isEmpty()) {
                return@withContext Result.failure(
                    Exception("El servidor importó el vídeo pero no devolvió una URL.")
                )
            }

            if (!importedUrl.startsWith("http://") && !importedUrl.startsWith("https://")) {
                return@withContext Result.failure(Exception("La URL devuelta por el servidor no es válida."))
            }

            Log.i(TAG, "Importación completada; descargando MP4")

            val mediaRequest = Request.Builder()
                .url(importedUrl)
                .header("Accept", "video/mp4,video/*,*/*")
                .build()

            val mediaResponse = client.newCall(mediaRequest).execute()

            if (!mediaResponse.isSuccessful) {
                val code = mediaResponse.code
                mediaResponse.close()
                return@withContext Result.failure(
                    Exception("No se pudo descargar el vídeo importado (HTTP $code).")
                )
            }

            val mediaBody = mediaResponse.body
                ?: run {
                    mediaResponse.close()
                    return@withContext Result.failure(
                        Exception("El servidor devolvió una respuesta de vídeo vacía.")
                    )
                }

            val sizeHeader = mediaBody.contentLength()
            val bodyBytes = mediaBody.bytes()
            mediaResponse.close()

            if (bodyBytes.isEmpty()) {
                return@withContext Result.failure(Exception("El archivo importado está vacío."))
            }

            val mediaDir = File(context.filesDir, "pending_media")
            if (!mediaDir.exists()) mediaDir.mkdirs()
            val destFile = File(mediaDir, "import_${System.currentTimeMillis()}_${(0..9999).random()}.mp4")
            destFile.writeBytes(bodyBytes)

            if (destFile.length() <= 0L) {
                destFile.delete()
                return@withContext Result.failure(Exception("El archivo importado se guardó vacío."))
            }

            val finalSize = if (sizeHeader > 0L) sizeHeader else destFile.length()

            Log.i(TAG, "Import OK: ${destFile.name} (${destFile.length()} bytes, mime=video/mp4)")
            return@withContext Result.success(
                ImportResult(
                    uri = Uri.fromFile(destFile),
                    file = destFile,
                    mimeType = "video/mp4",
                    sizeBytes = finalSize
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Import failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}