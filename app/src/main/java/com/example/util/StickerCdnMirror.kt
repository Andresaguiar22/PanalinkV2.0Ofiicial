package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.repository.CdnManager
import com.example.data.repository.UploadFailoverRouter
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Espeja stickers/GIFs de proveedores externos (Klipy, Giphy, etc.) al CDN de
 * PanaLink para que los mensajes de chat no dependan de la disponibilidad de
 * terceros (mismo patrón que rompió B2 por cap Class B: un proveedor con su
 * propio CDN puede caer, ir lento o cambiar URLs y el sticker histórico se pierde).
 *
 * El flujo es: si la URL apunta a un proveedor externo, se descargan los bytes,
 * se suben al CDN vía [UploadFailoverRouter] (CDN primario) y se devuelve la URL
 * de nuestro CDN. La URL original se conserva como fallback: si el CDN está caído
 * o la descarga del proveedor falla, se devuelve la original para no romper el envío.
 *
 * El resultado se cachea por URL original en SharedPreferences para no re-descargar
 * ni re-subir el mismo sticker en el mismo dispositivo cada vez que se envía.
 */
object StickerCdnMirror {
    private const val TAG = "StickerCdnMirror"
    private const val PREFS = "sticker_cdn_mirror"
    private const val KEY_CACHE_PREFIX = "mirror_cache_"

    /**
     * True si la URL ya vive en nuestra infraestructura (CDN de PanaLink, local,
     * Supabase Storage, vCDN o B2) y no necesita espejarse.

     * vCDN y B2 devuelven false en [CdnManager.isCdnRelated] por diseño (sus URLs
     * firmadas son auto-contenidas; nunca se re-anclan), pero son nuestra
     * infraestructura: espejarlas sería un desperdicio de datos mòviles y bytes.

     * Supabase Storage tampoco se espeja: su origen siempre es alcanzable y
     * es la fuente de verdad de avatares/covers.
     */
    fun isCdnOwned(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("http")) return true
        val host = try { java.net.URI(trimmed).host?.lowercase() } catch (_: Exception) { return false }
        if (host?.endsWith(".vcdn.me") == true || host == "cdn.vcdn.me" || host == "embed.vcdn.me") return true
        if (host?.endsWith(".backblazeb2.com") == true) return true
        val supabaseHost = try { java.net.URI(com.example.data.supabase.SupabaseClient.supabaseUrl).host?.lowercase() } catch (_: Exception) { null }
        if (supabaseHost != null && host == supabaseHost) return true
        return CdnManager.isCdnRelated(trimmed)
    }

    /**
     * Devuelve una URL de nuestro CDN para [url]. Si la URL ya es de nuestra
     * infraestructura o no se pudo espejar, devuelve la URL original.
     */
    suspend fun mirrorIfExternal(
        context: Context,
        url: String,
        typeLabel: String,
        mimeType: String
    ): String {
        val original = url.trim()
        if (original.isEmpty() || !original.startsWith("http")) return original
        if (isCdnOwned(original)) return original

        getCached(original)?.let { return it }

        // Descarga + subida con límite total: los timeouts de OkHttp del CDN son
        // de 600s para archivos grandes y un sticker/GIF no debería jamás retener
        // el envío de un mensaje. Si se supera, se devuelve la URL original.
        return withTimeoutOrNull(45_000L) {
            val tempFile: File? = downloadToTemp(context, original)
            if (tempFile == null || tempFile.length() == 0L) {
                throw IllegalStateException("No se pudo descargar el sticker/GIF externo")
            }

            val userId = com.example.data.supabase.SupabaseClient.currentUser?.id ?: "me_demo_id"
            try {
                val detectedMime = detectMime(tempFile, mimeType)
                val upload = UploadFailoverRouter.uploadWithFailover(
                    file = tempFile,
                    mimeType = detectedMime,
                    userId = userId,
                    uploadType = "sticker"
                ) {
                    PanalinkMediaManager.uploadMediaAndThumbnail(
                        context = context,
                        mediaFile = tempFile,
                        mimeType = detectedMime,
                        typeLabel = typeLabel,
                        userId = userId,
                        caption = "Sticker/GIF espejado al CDN"
                    )
                }
                val cdnUrl = upload.getOrNull()?.url
                if (cdnUrl != null && cdnUrl.startsWith("http")) {
                    cache(original, cdnUrl)
                    Log.i(TAG, "Sticker/GIF espejado al CDN: ${cdnUrl.take(140)}")
                    cdnUrl
                } else {
                    throw IllegalStateException("La subida del sticker/GIF al CDN falló")
                }
            } finally {
                runCatching { tempFile.delete() }
            }
        } ?: throw IllegalStateException("Timeout descargando sticker/GIF externo")
    }

    private suspend fun downloadToTemp(context: Context, url: String): File? = withContext(Dispatchers.IO) {
        try {
            val ext = when {
                url.contains(".gif") -> "gif"
                url.contains(".webp") -> "webp"
                url.contains(".png") -> "png"
                url.contains(".jpg") || url.contains(".jpeg") -> "jpg"
                else -> "bin"
            }
            val temp = File.createTempFile("cdn_mirror_", ".$ext", context.cacheDir)
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "PanaLink/Android")
                .header("Accept", "*/*")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP ${response.code} descargando sticker externo")
                    return@withContext null
                }
                val body = response.body ?: return@withContext null
                val responseMime = body.contentType()?.toString()?.substringBefore(";")?.lowercase(Locale.ROOT)
                val detectedMime = when {
                    responseMime == "image/gif" -> "image/gif"
                    responseMime == "image/png" -> "image/png"
                    responseMime == "image/webp" -> "image/webp"
                    responseMime == "image/jpeg" -> "image/jpeg"
                    url.lowercase(Locale.ROOT).substringBefore("?").substringBefore("#").endsWith(".gif") -> "image/gif"
                    url.lowercase(Locale.ROOT).substringBefore("?").substringBefore("#").endsWith(".png") -> "image/png"
                    url.lowercase(Locale.ROOT).substringBefore("?").substringBefore("#").endsWith(".jpg") || url.lowercase(Locale.ROOT).substringBefore("?").substringBefore("#").endsWith(".jpeg") -> "image/jpeg"
                    else -> mimeType
                }
                body.byteStream().use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (temp.length() == 0L) {
                temp.delete()
                null
            } else {
                temp
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallo la descarga del sticker externo: ${e.message}")
            null
        }
    }

    private fun detectMime(file: File, hint: String): String {
        val ext = file.extension.lowercase(Locale.ROOT)
        return when (ext) {
            "gif" -> "image/gif"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> hint
        }
    }

    private fun prefs(): android.content.SharedPreferences =
        com.example.PanaApplication.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun getCached(original: String): String? =
        try {
            prefs().getString(KEY_CACHE_PREFIX + original.hashCode(), null)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }

    private fun cache(original: String, cdnUrl: String) {
        try {
            prefs().edit().putString(KEY_CACHE_PREFIX + original.hashCode(), cdnUrl).apply()
        } catch (_: Exception) {
        }
    }
}