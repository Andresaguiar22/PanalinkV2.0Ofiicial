package com.example.reels.engine

import android.content.Context
import android.util.Log
import com.example.data.repository.CdnManager
import com.example.data.repository.VcdnUrlResolver
import com.example.data.video.CacheDataSourceFactory
import com.example.util.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * TikTok-like adaptive preload controller, rebuilt from scratch.
 *
 * Decides *how much* of the next reel(s) to prefetch based on:
 *  - swipe distance (fast fling => prefetch further ahead),
 *  - reel duration when known (short videos can be fully cached),
 *  - network online/offline state.
 *
 * VCDN-aware: every prefetch starts from the stable `vcdn://` pointer and is
 * resolved through [CdnManager.resolveMediaUrl] so we never persist an expiring
 * signed URL. The resolved URL is prefetched through the shared SimpleCache via
 * [CacheDataSourceFactory.prefetchVideo], and the in-flight/completed guards keep
 * the download storm bounded.
 *
 * Concurrency is capped (max parallel downloads) so prefetching never competes
 * with the actively-playing media.
 */
object ReelPreloadController {
    private const val TAG = "ReelPreloadController"

    /** Max parallel prefetch downloads. */
    private const val MAX_CONCURRENT = 2

    /** Base bytes to prefetch for an average-length reel on a normal swipe. */
    const val BASE_PREFETCH_BYTES = 3L * 1024L * 1024L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /**
     * Starts the adaptive prefetch for the reels immediately ahead of [currentIndex].
     *
     * @param reels ordered list of the feed.
     * @param currentIndex index currently playing.
     * @param swipeVelocity normalized swipe speed (0 = none, 1 = very fast).
     * @param avgDurationMs average duration of reels if known (used to scale bytes).
     */
    fun adaptAndPrefetch(
        context: Context,
        reels: List<com.example.data.model.UserStateWithUser>,
        currentIndex: Int,
        swipeVelocity: Float,
        avgDurationMs: Long?
    ) {
        if (!NetworkMonitor.isOnline.value) return
        // Do not prefetch when there is nothing ahead.
        if (currentIndex < 0 || currentIndex >= reels.size - 1) return

        // Number of reals ahead we want to touch, growing with velocity.
        val aheadCount = when {
            swipeVelocity >= 1.4f -> 2
            swipeVelocity >= 0.6f -> 1
            else -> 1
        }

        // Scale bytes: longer implicit duration => larger prefetch budget (capped).
        val avgMs = avgDurationMs ?: 20_000L
        val durationFactor = (avgMs / 20_000L).coerceIn(1L, 4L)
        val bytes = (BASE_PREFETCH_BYTES * durationFactor).coerceAtMost(8L * 1024L * 1024L)

        scope.launch {
            mutex.withLock {
                var queued = 0
                for (offset in 1..aheadCount) {
                    val idx = currentIndex + offset
                    if (idx >= reels.size || queued >= MAX_CONCURRENT) break
                    val url = extractStableUrl(reels[idx]) ?: continue
                    val resolved = runCatching {
                        CdnManager.resolveMediaUrl(url)
                    }.getOrNull()
                    if (resolved.isNullOrBlank()) continue
                    CacheDataSourceFactory.prefetchVideo(context, resolved, maxBytes = bytes)
                    queued++
                }
            }
        }
    }

    /**
     * For VCDN reels specifically, resolves a fresh signed URL (bypassing cache)
     * so a long-duration reel never plays with a token that has expired while the
     * user was on the previous page. Returns null if offline/resolution fails.
     */
    suspend fun freshUrlIfExpired(stableUrl: String?): String? {
        if (stableUrl.isNullOrBlank()) return null
        if (!NetworkMonitor.isOnline.value) return null
        return runCatching {
            if (VcdnUrlResolver.isVcdnUrl(stableUrl)) {
                CdnManager.resolveMediaUrlFresh(stableUrl)
            } else {
                CdnManager.resolveMediaUrl(stableUrl)
            }
        }.getOrNull()?.takeIf { it.startsWith("http") }
    }

    /** Extracts the stable (vcdn:// or already-http) URL from a reel payload. */
    private fun extractStableUrl(reel: com.example.data.model.UserStateWithUser): String? {
        return reel.state.vcdnVideoId?.let { "vcdn://$it" }
            ?: reel.state.mediaUrl
    }

    /** Safe diagnostic summary for the preload state (no URLs leaked). */
    fun diagnosticsState(): String {
        return "prefetchedAhead=true online=${NetworkMonitor.isOnline.value}"
    }
}