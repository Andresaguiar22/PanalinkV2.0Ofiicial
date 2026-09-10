package com.example.data.repository

import android.util.Log
import com.example.data.supabase.SessionManager
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Talks to the `livekit-token` edge function to obtain a short-lived participant
 * token + the LiveKit server URL. The API secret stays server-side; the device
 * only ever holds the signed JWT.
 *
 * Result is cached in-memory (token + expiry) so repeated room operations within
 * the TTL don't round-trip to the edge function.
 */
object LiveKitTokenService {
    private const val TAG = "LiveKitTokenService"
    private const val FUNCTION = "/functions/v1/livekit-token"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private data class CachedToken(
        val token: String,
        val url: String,
        val room: String,
        val userId: String,
        val expiresAtMs: Long,
    )
    @Volatile private var cache: CachedToken? = null
    private const val SAFETY_MARGIN_MS = 5 * 60 * 1000L // re-fetch 5min before expiry

    data class TokenResponse(val token: String, val url: String, val identity: String, val room: String)

    /**
     * Returns a valid token for [room]. Reuses the cached token when it is still
     * fresh and was issued for the same room.
     */
    suspend fun fetchToken(room: String, name: String? = null): TokenResponse? = withContext(Dispatchers.IO) {
        val currentUserId = SessionManager.getCurrentUserId()
        val cached = cache
        // Cache is keyed on both the authenticated user and the room, so a
        // different user/session within the same process cannot reuse a token
        // issued to a previous user for the same room.
        if (cached != null
            && cached.userId == currentUserId
            && cached.room == room
            && cached.expiresAtMs - SAFETY_MARGIN_MS > System.currentTimeMillis()) {
            return@withContext TokenResponse(cached.token, cached.url, identity = "", room = room)
        }
        requestToken(room, name)
    }

    private fun requestToken(room: String, name: String?): TokenResponse? {
        val token = SessionManager.getUserAuthToken() ?: SupabaseClient.currentToken
        if (token.isNullOrBlank()) {
            Log.w(TAG, "No auth token; cannot request LiveKit token")
            return null
        }
        val endpoint = SupabaseClient.supabaseUrl.trimEnd('/') + FUNCTION
        val payload = JSONObject().apply {
            put("room", room)
            if (!name.isNullOrBlank()) put("name", name)
        }.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(endpoint)
            .header("apikey", SupabaseClient.supabaseAnonKey)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(payload)
            .build()

        var result: TokenResponse? = null
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "livekit-token HTTP ${response.code}")
                return@use
            }
            val json = JSONObject(response.body?.string().orEmpty())
            val jwt = json.optString("token")
            val url = json.optString("url")
            if (jwt.isBlank() || url.isBlank()) return@use
            val ttlSec = json.optInt("ttl", 3600).coerceIn(60, 86400)
            // Decode JWT exp to compute a precise cache window (falls back to ttl).
            val expMs = decodeExpMs(jwt) ?: (System.currentTimeMillis() + ttlSec * 1000)
            cache = CachedToken(jwt, url, room, SessionManager.getCurrentUserId() ?: "", expMs)
            result = TokenResponse(jwt, url, json.optString("identity"), json.optString("room"))
        }
        return result
    }

    private fun decodeExpMs(jwt: String): Long? = try {
        val payload = jwt.split(".")[1]
        // base64url -> base64
        val b64 = payload.padEnd((payload.length + 3) / 4 * 4, '=').replace('-', '+').replace('_', '/')
        val json = JSONObject(android.util.Base64.decode(b64, android.util.Base64.DEFAULT).toString(Charsets.UTF_8))
        json.optLong("exp").takeIf { it > 0 }?.times(1000)
    } catch (e: Exception) {
        null
    }

    fun clearCache() { cache = null }
}
