package com.example.util

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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

    fun acquirePlayer(context: Context, id: String, url: String, title: String?, type: String): ExoPlayer {
        // If we already have a player with the same video playing, reuse it!
        val currentPlayer = exoPlayer
        if (currentPlayer != null && activeUrl == url) {
            isFloating = false // Bring it out of floating mode
            activeId = id
            activeTitle = title
            activeType = type
            return currentPlayer
        }

        // Reuse the SAME ExoPlayer instance across videos: swapping the media item
        // avoids full codec teardown + player construction on the main thread,
        // which is what froze the app during fast reel swipes.
        val player = currentPlayer ?: buildPlayer(context.applicationContext)

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

    private fun buildPlayer(context: Context): ExoPlayer {
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

        // DefaultDataSource delegates local files to FileDataSource and http(s) to the
        // shared cache factory, so one player instance handles both transparently.
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(
            context,
            CacheDataSourceFactory.getCacheDataSourceFactory(context)
        )

        return ExoPlayer.Builder(context, PanaRenderersFactory.create(context))
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
    }
}
