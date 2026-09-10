package com.example.data.repository

import android.util.Log
import com.example.data.supabase.SessionManager
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Resolves Backblaze B2 media URLs that may have expired (presigned GETs live up to
 * 7 days). When a stored [mediaUrl] is a B2 URL whose signature has expired (or is
 * about to), this calls the `b2-presign-download` edge function to mint a fresh
 * presigned GET for the same object and caches it in memory.
 *
 * Used by the Coil image interceptor and the ExoPlayer DataSource wrapper so every
 * remote B2 media load goes through here. The edge function needs the caller's JWT
 * (verify_jwt=true); the object key is extracted from the legacy URL's path so no
 * schema change is required.
 */
object B2UrlResolver {
    private const val TAG = "B2UrlResolver"
    private const val FUNCTION = "/functions/v1/b2-presign-download"
    private const val B2_HOST_SUFFIX = ".backblazeb2.com"

    // Refresh a URL 12h before its 7-day signature would actually expire, so the
    // player never sees a 403 mid-playback.
    private const val EXPIRY_SAFETY_MS = 12L * 60L * 60L * 1000L

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** freshUrl keyed by the stable object key (path without query), so cache survives across re-signs. */
    private val cacheByHostPath = ConcurrentHashMap<String, FreshUrl>()

    private data class FreshUrl(val url: String, val expiresAt: Long)

    /** True if [url] is a Backblaze B2 presigned URL we might need to re-sign. */
    fun isB2Url(url: String?): Boolean {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return false
        val host = try { URI(raw).host?.lowercase() } catch (_: Exception) { null } ?: return false
        return host.endsWith(B2_HOST_SUFFIX)
    }

    private fun stableKey(url: String): String? {
        val u = try { URI(url) } catch (_: Exception) { return null }
        val path = u.rawPath.orEmpty()
        if (path.isEmpty()) return null
        return (u.host?.lowercase() ?: "") + "|" + path
    }

    /**
     * Returns a loadable B2 URL for [originalUrl]:
     * - the cached fresh URL if still valid,
     * - else a freshly re-signed URL from the edge function.
     * On any failure returns [originalUrl] unchanged (fail-open) so the caller can
     * still attempt the original (e.g. it may not have expired yet).
     */
    suspend fun resolve(originalUrl: String?): String = withContext(Dispatchers.IO) {
        val raw = originalUrl?.trim().orEmpty()
        if (!isB2Url(raw)) return@withContext raw

        val key = stableKey(raw) ?: return@withContext raw
        cacheByHostPath[key]?.let { cached ->
            if (cached.expiresAt > System.currentTimeMillis()) return@withContext cached.url
        }
        // Offline: no hay red para re-firmar; devolver la original rapidísimo y
        // deja que el interceptor de Coil falle rápido (ver PanaApplication)..
        if (!com.example.util.NetworkMonitor.isOnline.value) return@withContext raw

        try {
            val fresh = resign(raw) ?: raw
            if (fresh != raw) {
                cacheByHostPath[key] = FreshUrl(fresh, System.currentTimeMillis() + EXPIRY_SAFETY_MS)
            }
            fresh
        } catch (e: Exception) {
            Log.w(TAG, "Re-sign failed for B2 media URL: ${e.javaClass.simpleName}; using original")
            raw
        }
    }

    /**
     * Synchronous variant used where a coroutine isn't available (Coil interceptor
     * runs on a background thread already). Calls the edge function blocking; on
     * any error returns null (caller keeps the original URL).
     */
    fun resolveBlocking(originalUrl: String?): String {
        val raw = originalUrl?.trim().orEmpty()
        if (!isB2Url(raw)) return raw
        val key = stableKey(raw) ?: return raw
        cacheByHostPath[key]?.let { cached ->
            if (cached.expiresAt > System.currentTimeMillis()) return cached.url
        }
        // Offline: no hay red para re-firmar; devolver la original rapidísimo (
        // el interceptor de Coil se encarga de fallar rápido sin red).

        if (!com.example.util.NetworkMonitor.isOnline.value) return raw

        val fresh = try {
            resignBlocking(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Re-sign (sync) failed: ${e.javaClass.simpleName}")
            null
        } ?: return raw
        if (fresh != raw) {
            cacheByHostPath[key] = FreshUrl(fresh, System.currentTimeMillis() + EXPIRY_SAFETY_MS)
        }
        return fresh
    }

    private suspend fun resign(raw: String): String? = withContext(Dispatchers.IO) {
        resignBlocking(raw)
    }

    private fun resignBlocking(raw: String): String? {
        var token = SessionManager.getUserAuthToken() ?: SupabaseClient.currentToken
        if (token.isNullOrBlank()) return null
        val fresh = tryResign(raw, token)
        // 401 = JWT expirado: refrescar (bloqueante) y reintentar una vez.
        if (fresh == null && SessionManager.isJwtExpired(token)) {
            Log.w(TAG, "JWT expirado en b2-presign-download; refrescando")
            val refreshed = runBlocking { SessionManager.refreshSession() }
            if (refreshed) {
                val newToken = SessionManager.getUserAuthToken() ?: SupabaseClient.currentToken
                if (!newToken.isNullOrBlank()) {
                    return tryResign(raw, newToken)
                }
            }
        }
        return fresh
    }

    private fun tryResign(raw: String, token: String): String? {
        val endpoint = SupabaseClient.supabaseUrl.trimEnd('/') + FUNCTION
        val body = JSONObject().apply { put("url", raw) }
            .toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(endpoint)
            .header("apikey", SupabaseClient.supabaseAnonKey)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(body)
            .build()
        var fresh: String? = null
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "b2-presign-download HTTP ${response.code}")
                return@use
            }
            val json = JSONObject(response.body?.string().orEmpty())
            val resolved = json.optString("publicUrl")
            if (resolved.isNotBlank()) fresh = resolved
        }
        return fresh
    }
}
