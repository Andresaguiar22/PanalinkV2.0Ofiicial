package com.example.util

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.example.core.media.PanaRenderersFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.data.video.CacheDataSourceFactory
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

@UnstableApi
object AppFloatingPlayerManager {
    var activeId by mutableStateOf<String?>(null)
    var activeUrl by mutableStateOf<String?>(null)
    var activeTitle by mutableStateOf<String?>(null)
    var activeType by mutableStateOf<String?>(null) // "reel" or "panatv"
    
    var exoPlayer by mutableStateOf<ExoPlayer?>(null)
    var isFloating by mutableStateOf(false)
    var isMuted by mutableStateOf(false)

    // The HTTP data-source factory is kept as a reference so that per-stream
    // headers (User-Agent / Referer) can be updated dynamically between
    // setMediaItem calls on the shared player — no rebuild required.
    private var httpDataSourceFactory: DefaultHttpDataSource.Factory? = null

    // Whether the current ExoPlayer was built preferring the FFmpeg software
    // decoders. PanaTV escalates to that mode to recover from MediaCodec failures,
    // and the flag lets acquirePlayer() know a rebuild is required.
    private var playerPreferSoftware = false

    // Whether the current ExoPlayer reads through the disk cache. Live IPTV
    // streams must NOT use it: an infinite MPEG-TS grows the cache unboundedly
    // and SimpleCache evicts spans the player is still reading -> "source error".
    private var playerUseCache = true

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 Panalink/1.0"
    
    // Track position in screen
    var bubbleOffsetX by mutableStateOf(0f)
    var bubbleOffsetY by mutableStateOf(0f)
    
    // Native PiP State for the Activity
    var isInNativePip by mutableStateOf(false)

    // Resume position of the LAST reel that was playing when the user left the
    // feed, so re-entering resumes that exact reel. Only one entry is kept:
    // reels swiped away mid-feed always restart from the beginning.
    private val resumePositions = java.util.LinkedHashMap<String, Long>(1, 0.75f, false)

    fun saveResumePosition(id: String?, positionMs: Long) {
        if (id.isNullOrEmpty() || positionMs <= 0L) return
        synchronized(resumePositions) {
            resumePositions.clear()
            resumePositions[id] = positionMs
        }
    }

    fun acquirePlayer(
        context: Context,
        id: String,
        url: String,
        title: String?,
        type: String,
        userAgent: String? = null,
        referrer: String? = null,
        preferSoftware: Boolean = false,
        useCache: Boolean = true
    ): ExoPlayer {
        // If we already have a player with the same video playing, reuse it!
        val currentPlayer = exoPlayer
        if (currentPlayer != null && activeUrl == url && playerPreferSoftware == preferSoftware && playerUseCache == useCache) {
            isFloating = false // Bring it out of floating mode
            activeId = id
            activeTitle = title
            activeType = type
            return currentPlayer
        }

        // Reuse the SAME ExoPlayer instance across videos: swapping the media item
        // avoids full codec teardown + player construction on the main thread,
        // which is what froze the app during fast reel swipes. A different renderer
        // mode or cache mode means the player must be rebuilt instead.
        val reuse = currentPlayer?.takeIf { playerPreferSoftware == preferSoftware && playerUseCache == useCache }
        if (currentPlayer != null && reuse == null) {
            try { currentPlayer.stop() } catch (_: Throwable) {}
            try { currentPlayer.release() } catch (_: Throwable) {}
        }
        val player = reuse ?: buildPlayer(context.applicationContext, preferSoftware, useCache)
        playerPreferSoftware = preferSoftware
        playerUseCache = useCache

        // Update per-stream headers on the shared HTTP factory before setMediaItem.
        // The factory is mutable: subsequent createDataSource() calls pick up the
        // new properties, so a single prepare() applies the right headers. Both the
        // UA and the referer are ALWAYS reset, otherwise a channel without them
        // would inherit the previous channel's headers and fail to load.
        httpDataSourceFactory?.let { factory ->
            factory.setUserAgent(userAgent?.takeIf { it.isNotBlank() } ?: DEFAULT_USER_AGENT)
            val props = mutableMapOf<String, String>()
            referrer?.takeIf { it.isNotBlank() }?.let { props["Referer"] = it }
            factory.setDefaultRequestProperties(props)
        }

        // CRÍTICO: limpiar surface y reset antes de cambiar media item para evitar
        // frame corrupto residual (causa de pantalla negra después de unos segundos).
        try { player.clearVideoSurface() } catch (_: Throwable) {}

        val mediaItem = if (url.startsWith("http")) {
            MediaItem.fromUri(url)
        } else {
            // Ensure local path is correctly formatted as file://
            val uri = if (url.startsWith("/")) android.net.Uri.fromFile(java.io.File(url)) else android.net.Uri.parse(url)
            MediaItem.fromUri(uri)
        }
        // setMediaItem(replace) + prepare is enough: explicit stop() forces decoder
        // teardown and discards buffered/cache-read state, which is what made swiped
        // videos reload from zero.
        player.setMediaItem(mediaItem)
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.playWhenReady = false // DO NOT play by default to prevent audio overlap during preloading
        player.volume = if (isMuted) 0f else 1f
        val resumeMs = synchronized(resumePositions) { resumePositions[id] ?: 0L }
        if (resumeMs > 0L) player.seekTo(resumeMs)
        player.prepare()

        exoPlayer = player
        activeId = id
        activeUrl = url
        activeTitle = title
        activeType = type
        isFloating = false

        return player
    }

    private fun buildPlayer(context: Context, preferSoftware: Boolean = false, useCache: Boolean = true): ExoPlayer {
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                10000, // minBufferMs: 10s
                20000, // maxBufferMs: 20s
                200,   // bufferForPlaybackMs: 200ms for instant start
                500    // bufferForPlaybackAfterRebufferMs: 500ms
            )
            .setBackBuffer(5000, true) // Cache 5s of already played video for instant seek-back
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Setup adaptive track selection
        val trackSelector = DefaultTrackSelector(context, androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection.Factory()).apply {
            setParameters(buildUponParameters().clearVideoSizeConstraints()) // Allow high quality
        }

        // Keep a reference to the HTTP factory so acquirePlayer() can update
        // per-stream headers (User-Agent / Referer) dynamically without rebuilding
        // the player. The factory is mutable; changes are picked up by new
        // DefaultHttpDataSource instances created during prepare().
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(DEFAULT_USER_AGENT)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true)
        httpDataSourceFactory = httpFactory

        // Live IPTV must read the network directly (no disk cache), otherwise the
        // unbounded SimpleCache growth evicts in-flight spans. VOD keeps caching.
        val upstream: DataSource.Factory =
            if (useCache) CacheDataSourceFactory.getCacheDataSourceFactory(context, httpFactory)
            else httpFactory

        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, upstream)

        return ExoPlayer.Builder(context, PanaRenderersFactory.create(context, preferSoftware))
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(dataSourceFactory)
            )
            .setLoadControl(loadControl)
            .build()
    }

    /**
     * Drops ownership of the shared player WITHOUT releasing it. Pages call this on
     * dispose so a config change or a fast swipe never tears down the codec; the
     * feed screen calls [releasePlayer] when the user actually leaves.
     */
    fun unacquireIfOwner(id: String) {
        if (activeId == id) {
            activeId = null
            activeUrl = null
        }
    }

    fun releasePlayer() {
        try { exoPlayer?.clearVideoSurface() } catch (_: Throwable) {}
        exoPlayer?.stop()
        try { exoPlayer?.clearMediaItems() } catch (_: Throwable) {}
        exoPlayer?.release()
        exoPlayer = null
        activeId = null
        activeUrl = null
        activeTitle = null
        activeType = null
        isFloating = false
        isInNativePip = false
        playerPreferSoftware = false
        playerUseCache = true
    }
}
