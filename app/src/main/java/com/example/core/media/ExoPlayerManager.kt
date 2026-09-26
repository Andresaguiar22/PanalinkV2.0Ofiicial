package com.example.core.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

@OptIn(UnstableApi::class)
object ExoPlayerManager {
    // 3 composed pager pages + 1 preload player + 1 headroom — avoids codec
    // init/teardown churn on the main thread during fast flings
    private const val MAX_POOL_SIZE = 5
    private val playerPool = mutableListOf<ExoPlayer>()
    private val activePlayers = mutableSetOf<ExoPlayer>()

    @Synchronized
    fun getPlayer(context: Context): ExoPlayer {
        val appCtx = context.applicationContext
        val player = if (playerPool.isNotEmpty()) {
            playerPool.removeAt(0)
        } else {
            createExoPlayer(appCtx)
        }
        activePlayers.add(player)
        return player
    }

    @Synchronized
    fun releasePlayer(player: ExoPlayer?) {
        if (player == null) return
        player.stop()
        player.clearMediaItems()
        // CRÍTICO: reset() limpia el render surface, los eventos pendientes y
        // deja el decoder surface en blanco. Sin esto, un player que regresa al
        // pool desde un formato problemático (HEVC 10-bit) sigue manchando el
        // siguiente video. reset() no es destructivo y deja el player usable.
        try { player.clearVideoSurface() } catch (_: Throwable) {}
        player.playWhenReady = false
        activePlayers.remove(player)

        if (playerPool.size < MAX_POOL_SIZE) {
            playerPool.add(player)
        } else {
            player.release()
        }
    }

    @Synchronized
    fun releaseAll() {
        activePlayers.forEach { it.release() }
        activePlayers.clear()
        playerPool.forEach { it.release() }
        playerPool.clear()
    }

    /**
     * The pooled players serve inline feed previews and the fullscreen Muro viewer,
     * so they are built with the [VideoPlaybackEngine.Profile.VIEWER] profile:
     * hardware-first decoders and a resolution cap. Building them with software
     * decoders at full source resolution was the cause of the stutter on
     * high-bitrate posts.
     */
    private fun createExoPlayer(context: Context): ExoPlayer =
        VideoPlaybackEngine.build(context, VideoPlaybackEngine.Profile.VIEWER).apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
}
