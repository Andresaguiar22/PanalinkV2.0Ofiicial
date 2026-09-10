package com.example.data.repository

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Resolves a stable `vcdn://{videoId}` pointer to a fresh, signed HLS streamUrl.
 *
 * VCDN's [streamUrl] carries a signed token that expires, so we never persist it.
 * The app stores the stable [vcdn_video_id]; at playback time this resolver calls
 * the public BFF (embed.vcdn.me/api/bff/player-config/{videoId}) which mints a
 * fresh token, and caches it in memory until shortly before its [expires] time.
 *
 * Fail-open: if resolution fails we return the raw input so the caller can still
 * attempt it (or the caller may fall back to B2 for legacy media).
 */
object VcdnUrlResolver {
    private const val TAG = "VcdnUrlResolver"
    private const val BFF_BASE = "https://embed.vcdn.me"
    private const val SCHEME = "vcdn"

    // Refresh 1h before the BFF expires the token, so the player never sees a stale URL mid-playback.
    private const val EXPIRY_SAFETY_MS = 1L * 60L * 60L * 1000L

    // Negative cache: when the BFF proves the video no longer exists (not_found/
    // resource-gone), don't hammer it again on every recomposition for a short
    // window. The row in Supabase/Room is NEVER touched by this layer.
    private const val NOT_FOUND_COOLDOWN_MS = 5L * 60L * 1000L
    private val notFoundUntil = ConcurrentHashMap<String, Long>()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private data class FreshUrl(val url: String, val posterUrl: String?, val expiresAt: Long)

    private val cache = ConcurrentHashMap<String, FreshUrl>()
    /** Mutex solo protege la manipulación del mapa cache y notFoundUntil (operaciones rápidas de memoria). */
    private val cacheMutex = Mutex()
    /** Deduplicación por videoId: si un fetch de BFF ya está en vuelo para un videoId,
     *  las coroutines concurrentes esperan el mismo resultado en lugar de lanzar
     *  múltiples requests. Diferente de un mutex global: videos distintos se resuelven en paralelo. */
    private val inFlightFetches = ConcurrentHashMap<String, CompletableDeferred<FetchResult?>>()

    fun isVcdnUrl(url: String?): Boolean {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return false
        return raw.startsWith("$SCHEME://")
    }

    fun videoIdOf(url: String): String? {
        val u = Uri.parse(url)
        if (u.scheme?.lowercase() != SCHEME) return null
        val id = u.host?.trim().orEmpty()
        return id.ifBlank { null }
    }

    /** Synchronous variant for Coil/image loaders. Returns EMPTY STRING when the video is
     *  not available (null from [resolve]) — callers must treat "" as "no playback",
     *  never as "vcdn://..." raw pointer. Fail-open (empty) keeps callers safe.
     *
     *  When [forceRefresh] is true, the in-memory cache entry is evicted and a fresh
     *  BFF request is issued — bypassing any stale cached URL. Used for 401 recovery
     *  when a signed URL has expired mid-playback. */
    fun resolveBlocking(originalUrl: String, forceRefresh: Boolean = false): String =
        runBlocking { resolve(originalUrl, forceRefresh = forceRefresh) ?: "" }

    /**
     * Returns a loadable HLS streamUrl for [originalUrl].
     *
     * - the cached fresh URL if still valid,
     * - else a freshly resolved URL from the BFF.
     *
     * When the BFF proves the video no longer exists (HTTP 4xx / not_found),
     * returns NULL (NEVER the raw vcdn:// pointer which would reach ExoPlayer
     * as a non-playable scheme). A short negative cache (5 min) prevents hammering.
     * Transient errors (timeouts/5xx) fall back to a stale cached URL if fresh
     * enough (24h), else NULL too (a raw vcdn:// must never reach the player).
     */
    suspend fun resolve(originalUrl: String?, forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        val raw = originalUrl?.trim().orEmpty()
        if (!isVcdnUrl(raw)) return@withContext raw
        val videoId = videoIdOf(raw) ?: return@withContext raw
        val now = System.currentTimeMillis()

        // 1. Cache hit (in-memory, instant) — skip when forceRefresh to bypass
        //    a potentially stale URL (e.g. signed token expired mid-playback).
        if (!forceRefresh) {
            cache[videoId]?.let { cached ->
                if (cached.expiresAt > now) return@withContext cached.url
            }
        } else {
            cache.remove(videoId)
        }

        // Offline: skip network entirely
        if (!com.example.util.NetworkMonitor.isOnline.value) {
            Log.d(TAG, "Offline: skipping vcdn:// resolution for $videoId")
            return@withContext null
        }

        // Negative cache
        if (notFoundUntil[videoId]?.let { it > now } == true) {
            Log.d(TAG, "video $videoId in negative cache (not_found); skipping")
            return@withContext null
        }

        // 2. Deduplicación por videoId: evitar múltiples fetches concurrentes del mismo video
        val existingDeferred = inFlightFetches[videoId]
        if (existingDeferred != null) {
            // Esperar al resultado del fetch en vuelo para este mismo videoId
            val result = existingDeferred.await()
            // El resultado se procesa igual que el fetch propio
            return@withContext processFetchResult(videoId, result, now)
        }

        // 3. Registrar fetch en vuelo y ejecutar BFF call (NO bajo mutex global)
        val deferred = CompletableDeferred<FetchResult?>()
        val previous = inFlightFetches.putIfAbsent(videoId, deferred)
        if (previous != null) {
            // Otro coroutine ganó la carrera: esperar su resultado
            val result = previous.await()
            return@withContext processFetchResult(videoId, result, now)
        }

        var fetchResult: FetchResult? = null
        try {
            fetchResult = try {
                fetchConfig(videoId)
            } catch (e: Exception) {
                Log.w(TAG, "resolve failed for $videoId; trying stale cache: ${e.javaClass.simpleName}")
                null
            }
            // Resolver para esta coroutine
            processFetchResult(videoId, fetchResult, now)
        } finally {
            // Limpiar el estado in-flight siempre, incluso si falla
            inFlightFetches.remove(videoId)
            if (!deferred.isCompleted) {
                deferred.complete(fetchResult)
            }
        }
    }

    /** Procesa el resultado del fetch, aplicando negative cache y stale fallback. */
    private suspend fun processFetchResult(videoId: String, fetchResult: FetchResult?, now: Long): String? {
        val fresh = if (fetchResult?.notFound == true) {
            cacheMutex.withLock {
                notFoundUntil[videoId] = now + NOT_FOUND_COOLDOWN_MS
            }
            Log.w(TAG, "resolve failed for $videoId (not found); null")
            null
        } else {
            fetchResult?.fresh
        }
        if (fresh == null) {
            cache[videoId]?.let { stale ->
                if (stale.expiresAt > now - 24L * 60L * 60L * 1000L) return stale.url
            }
            return null
        }
        cacheMutex.withLock {
            cache[videoId] = fresh
        }
        return fresh.url
    }

    /** True when an VCDN pointer can currently be resolved to a real HTTP URL. */
    suspend fun isVideoAvailable(originalUrl: String?): Boolean {
        val resolved = resolve(originalUrl)
        return resolved?.startsWith("https://") == true || resolved?.startsWith("http://") == true
    }

    private data class FetchResult(val fresh: FreshUrl?, val notFound: Boolean)

    private suspend fun fetchConfig(videoId: String): FetchResult? {
        // VCDN BFF rejects requests that don't look like a real browser (403 Forbidden
        // with plain OkHttp UA). Send browser-like headers: the streamUrl minted
        // by this endpoint is public/signed per-video and expires, so no secrets leak.

        val request = Request.Builder()
            .url("$BFF_BASE/api/bff/player-config/${Uri.encode(videoId)}")
            .header("Accept", "application/json")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36")
            .header("Referer", "https://vcdn.me/")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val code = response.code
                Log.w(TAG, "BFF player-config HTTP $code for video $videoId")
                return FetchResult(null, notFound = code == 404 || code == 410 || code == 400)
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return FetchResult(null, false)
            val json = JSONObject(body)
            val errorDetail = json.optString("detail").ifBlank { json.optString("error") }
            val notFound = errorDetail.contains("not_found", ignoreCase = true) || json.optString("error").contains("not_found", ignoreCase = true)
            val streamUrl = json.optString("streamUrl").ifBlank {
                val arr = json.optJSONArray("playbackSources")
                arr?.optJSONObject(0)?.optString("streamUrl").orEmpty()
            }
            if (streamUrl.isBlank() || notFound) return FetchResult(null, notFound)
            val expires = json.optLong("expires", 0L)
            val expiresAt = if (expires > 0L) expires * 1000L - EXPIRY_SAFETY_MS
            else System.currentTimeMillis() + 5L * 60L * 1000L
            return FetchResult(FreshUrl(streamUrl, json.optString("posterUrl").ifBlank { null }, expiresAt), false)
        }
    }

    suspend fun resolvePoster(originalUrl: String?): String? = withContext(Dispatchers.IO) {
        val raw = originalUrl?.trim().orEmpty()
        if (!isVcdnUrl(raw)) return@withContext null
        val videoId = videoIdOf(raw) ?: return@withContext null
        cache[videoId]?.let { return@withContext it.posterUrl }
        if (!com.example.util.NetworkMonitor.isOnline.value) return@withContext null
        try {
            val fetchResult = fetchConfig(videoId)
            val fresh = fetchResult?.fresh
            if (fresh != null && fetchResult?.notFound != true) {
                cacheMutex.withLock { cache[videoId] = fresh }
                return@withContext fresh.posterUrl
            }
            null
        } catch (_: Exception) { null }
    }
}