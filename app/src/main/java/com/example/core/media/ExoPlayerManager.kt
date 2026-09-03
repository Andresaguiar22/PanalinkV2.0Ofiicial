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

    private fun createExoPlayer(context: Context): ExoPlayer {
        val renderersFactory = PanaRenderersFactory.create(context, preferSoftware = true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                5000,   // minBufferMs
                15000,  // maxBufferMs
                500,    // bufferForPlaybackMs
                3000    // bufferForPlaybackAfterRebufferMs
            )
            .build()

        // Wrap the cache factory with DefaultDataSource so scheme routing works:
        // http(s) -> cache factory (B2 re-sign + cache), content:// (gallery) /
        // file:// / asset:// -> the matching local DataSources. Without this wrap,
        // gallery URIs (content://) reached DefaultHttpDataSource and failed, which
        // is why the reel/story editor preview showed a black screen.
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(
            context,
            com.example.data.video.CacheDataSourceFactory.getCacheDataSourceFactory(context)
        )

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
            }
    }
}
