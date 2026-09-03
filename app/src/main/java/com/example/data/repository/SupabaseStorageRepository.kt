package com.example.data.repository

import android.util.Log
import com.example.data.supabase.SupabaseClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.OkHttpClient
import java.io.File

/** Small/private-profile media storage. Credentials never leave SupabaseClient configuration. */
class SupabaseStorageRepository {
    private val client = OkHttpClient()
    private val tag = "SupabaseStorageRepository"

    suspend fun uploadProfileMedia(
        file: File,
        userId: String,
        mimeType: String,
        cover: Boolean
    ): Result<String> {
        if (!file.exists() || file.length() <= 0L) {
            return Result.failure(IllegalArgumentException("Archivo local inexistente o vacío"))
        }
        if (file.length() > 2L * 1024L * 1024L) {
            return Result.failure(IllegalArgumentException("La imagen supera el límite de 2 MB de Storage"))
        }

        val bucket = if (cover) "profile-covers" else "avatars"
        val extension = file.extension.ifBlank { if (mimeType.contains("png")) "png" else "jpg" }
        val objectPath = "$userId/profile_${if (cover) "cover" else "avatar"}.$extension"
        val base = SupabaseClient.supabaseUrl.trimEnd('/')
        val url = "$base/storage/v1/object/$bucket/$objectPath"

        return try {
            val request = Request.Builder()
                .url(url)
                .header("apikey", SupabaseClient.supabaseAnonKey)
                .header("Authorization", "Bearer ${SupabaseClient.currentToken ?: ""}")
                .header("Content-Type", mimeType)
                .header("x-upsert", "true")
                .put(file.asRequestBody(mimeType.toMediaTypeOrNull()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    Log.e(tag, "Storage upload failed: HTTP ${response.code} $body")
                    return Result.failure(Exception("Supabase Storage HTTP ${response.code}"))
                }
                val publicUrl = "$base/storage/v1/object/public/$bucket/$objectPath?v=${System.currentTimeMillis()}"
                Result.success(publicUrl)
            }
        } catch (e: Exception) {
            Log.e(tag, "Storage upload exception", e)
            Result.failure(e)
        }
    }

    /**
     * Sube una miniatura (thumbnail JPEG) al bucket "thumbnails" de Supabase.
     * Supabase es la fuente de verdad; el CDN queda como respaldo.
     * Devuelve la URL publica o null para que el caller caiga al CDN.
     */
    suspend fun uploadThumbnail(
        file: File,
        userId: String,
        objectName: String
    ): String? {
        if (!file.exists() || file.length() <= 0L) return null
        return try {
            if (!SupabaseClient.isConfigured) return null
            val base = SupabaseClient.supabaseUrl.trimEnd('/')
            val objectPath = "$userId/$objectName"
            val url = "$base/storage/v1/object/thumbnails/$objectPath"
            val request = Request.Builder()
                .url(url)
                .header("apikey", SupabaseClient.supabaseAnonKey)
                .header("Authorization", "Bearer ${SupabaseClient.currentToken ?: ""}")
                .header("Content-Type", "image/jpeg")
                .header("x-upsert", "true")
                .put(file.asRequestBody("image/jpeg".toMediaTypeOrNull()))
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    "$base/storage/v1/object/public/thumbnails/$objectPath?v=${System.currentTimeMillis()}"
                } else {
                    Log.w(tag, "Thumbnails bucket rechazo (${response.code}), cayendo al CDN")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Excepcion subiendo thumbnail a Supabase, cayendo al CDN", e)
            null
        }
    }

    /**
     * Sube media de canal (cover o avatar) al bucket "channels" de Supabase.
     * Devuelve la URL publica o null para que el caller caiga al CDN.
     */
    suspend fun uploadChannelMedia(
        file: File,
        userId: String,
        mimeType: String,
        isCover: Boolean
    ): String? {
        if (!file.exists() || file.length() <= 0L) return null
        return try {
            if (!SupabaseClient.isConfigured) return null
            val extension = file.extension.ifBlank { if (mimeType.contains("png")) "png" else "jpg" }
            val objectPath = "$userId/channel_${if (isCover) "cover" else "avatar"}.$extension"
            val base = SupabaseClient.supabaseUrl.trimEnd('/')
            val url = "$base/storage/v1/object/channels/$objectPath"
            val request = Request.Builder()
                .url(url)
                .header("apikey", SupabaseClient.supabaseAnonKey)
                .header("Authorization", "Bearer ${SupabaseClient.currentToken ?: ""}")
                .header("Content-Type", mimeType)
                .header("x-upsert", "true")
                .put(file.asRequestBody(mimeType.toMediaTypeOrNull()))
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    "$base/storage/v1/object/public/channels/$objectPath?v=${System.currentTimeMillis()}"
                } else {
                    Log.w(tag, "Channels bucket rechazo (${response.code}), cayendo al CDN")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Excepcion subiendo media de canal a Supabase, cayendo al CDN", e)
            null
        }
    }
}
