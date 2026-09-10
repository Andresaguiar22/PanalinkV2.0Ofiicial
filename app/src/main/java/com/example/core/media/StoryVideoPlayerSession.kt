package com.example.core.media

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.data.repository.CdnManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Session estable de ExoPlayer para el visor de Stories.
 *
 * El progreso que se emite al visor siempre representa el tiempo REAL que lleva
 * reproduciéndose el clip visible. Si existe VideoTrim, la posición es relativa
 * al inicio del trim y la duración efectiva se recalcula contra la duración real
 * del player para evitar que una duración HLS/metadata incorrecta desincronice la barra.
 */
@OptIn(UnstableApi::class)
class StoryVideoPlayerSession(private val context: Context) {
    private val TAG = "StoryVideoPlayer"
    private val MAX_RETRIES = 2

    var stateId: String = ""
        private set

    var onReady: (() -> Unit)? = null
    var onDurationReady: ((Int) -> Unit)? = null
    var onMediaEnded: (() -> Unit)? = null
    var onUnavailable: (() -> Unit)? = null
    var onStateChanged: ((String, Long, Long, Int, Long) -> Unit)? = null
    var onPositionChanged: ((Long) -> Unit)? = null
    var onError: ((String, String, Int) -> Unit)? = null

    private var retryCount = 0
    private var isReleased = AtomicBoolean(false)
    private var lastVideoUrl: String = ""
    private var lastIsMuted: Boolean = false
    private var lastTrim: Pair<Float, Float>? = null
    private val retryScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    val player: ExoPlayer = createPlayer()

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Emits position/duration from the same ExoPlayer instance that renders the video.
     * The boundary is handled BEFORE emitting progress, so the UI never receives a
     * fake 100% frame while the player is already looping back to the trim start.
     */
    private val trimRunnable = object : Runnable {
        override fun run() {
            if (isReleased.get()) return
            try {
                val p = this@StoryVideoPlayerSession.player
                val trim = lastTrim
                val current = p.currentPosition
                val rawPlayerDuration = p.duration

                if (trim != null) {
                    val startMs = (trim.first * 1000).toLong().coerceAtLeast(0L)
                    val requestedEndMs = (trim.second * 1000).toLong()
                    val actualEndMs = if (rawPlayerDuration > startMs) {
                        requestedEndMs.coerceIn(startMs, rawPlayerDuration)
                    } else {
                        requestedEndMs.coerceAtLeast(startMs)
                    }
                    val effectiveDuration = (actualEndMs - startMs).coerceAtLeast(0L)

                    if (effectiveDuration > 0L) {
                        onDurationReady?.invoke(effectiveDuration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())

                        // Reset BEFORE publishing the end position. This prevents the
                        // progress bar from reaching 100% while the video is still looping.
                        if (p.playbackState == Player.STATE_READY && current >= actualEndMs) {
                            p.seekTo(startMs)
                            onPositionChanged?.invoke(0L)
                        } else {
                            val relativePosition = (current - startMs)
                                .coerceIn(0L, effectiveDuration)
                            onPositionChanged?.invoke(relativePosition)
                        }
                    } else {
                        onDurationReady?.invoke(0)
                        onPositionChanged?.invoke(0L)
                    }
                } else {
                    // No trim: always use the player's current duration, not a stale
                    // duration captured only when STATE_READY fired. This matters for
                    // HLS/streamed stories whose duration can settle after READY.
                    val effectiveDuration = rawPlayerDuration.coerceAtLeast(0L)
                    if (effectiveDuration > 0L) {
                        onDurationReady?.invoke(effectiveDuration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    }
                    val boundedCurrent = current.coerceIn(0L, effectiveDuration.takeIf { it > 0L } ?: Long.MAX_VALUE)
                    onPositionChanged?.invoke(boundedCurrent)
                    if (effectiveDuration > 0L && boundedCurrent >= effectiveDuration && p.playbackState == Player.STATE_READY) {
                        p.seekTo(0L)
                        onMediaEnded?.invoke()
                    }
                }
            } catch (_: Exception) {
                // Session released or player tearing down; do not reschedule from a fatal access.
            } finally {
                if (!isReleased.get()) mainHandler.postDelayed(this, 100)
            }
        }
    }

    init {
        mainHandler.post(trimRunnable)
    }

    private fun createPlayer(): ExoPlayer {
        val loadControl = StoryVideoLoadControl.create()
        val trackSelector = DefaultTrackSelector(
            context,
            AdaptiveTrackSelection.Factory()
        ).apply {
            setParameters(buildUponParameters().clearVideoSizeConstraints())
        }
        val player = ExoPlayer.Builder(
            context,
            PanaRenderersFactory.create(context, preferSoftware = true)
        )
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(
                        androidx.media3.datasource.DefaultDataSource.Factory(
                            context,
                            com.example.data.video.CacheDataSourceFactory.getCacheDataSourceFactory(context)
                        )
                    )
            )
            .setLoadControl(loadControl)
            .build()
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                Log.i(
                    TAG,
                    "stateId=$stateId state=$stateName pos=${player.currentPosition} " +
                        "buffered=${player.bufferedPosition} pct=${player.bufferedPercentage} duration=${player.duration}"
                )

                if (playbackState == Player.STATE_READY) {
                    onReady?.invoke()

                    val trim = lastTrim
                    val duration = if (trim != null) {
                        val start = (trim.first * 1000).toLong().coerceAtLeast(0L)
                        val requestedEnd = (trim.second * 1000).toLong()
                        val actualEnd = if (player.duration > start) {
                            requestedEnd.coerceIn(start, player.duration)
                        } else {
                            requestedEnd.coerceAtLeast(start)
                        }
                        (actualEnd - start).coerceAtLeast(0L)
                    } else {
                        player.duration.coerceAtLeast(0L)
                    }

                    if (duration > 0L) {
                        onDurationReady?.invoke(duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    }
                    if (trim != null) {
                        val start = (trim.first * 1000).toLong().coerceAtLeast(0L)
                        player.seekTo(start)
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    onMediaEnded?.invoke()
                }

                onStateChanged?.invoke(
                    stateName,
                    player.currentPosition,
                    player.bufferedPosition,
                    player.bufferedPercentage,
                    player.duration
                )

                val trimForState = lastTrim
                val rawPosForState = player.currentPosition
                val rawDurationForState = player.duration
                if (trimForState != null) {
                    val start = (trimForState.first * 1000).toLong().coerceAtLeast(0L)
                    val requestedEnd = (trimForState.second * 1000).toLong()
                    val end = if (rawDurationForState > start) {
                        requestedEnd.coerceIn(start, rawDurationForState)
                    } else {
                        requestedEnd.coerceAtLeast(start)
                    }
                    val relative = if (end > start) {
                        (rawPosForState - start).coerceIn(0L, end - start)
                    } else {
                        0L
                    }
                    onPositionChanged?.invoke(relative)
                } else {
                    onPositionChanged?.invoke(
                        rawPosForState.coerceIn(0L, rawDurationForState.coerceAtLeast(0L).takeIf { it > 0L } ?: Long.MAX_VALUE)
                    )
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(
                    TAG,
                    "stateId=$stateId ERROR code=${error.errorCode} name=${error.errorCodeName} msg=${error.message}"
                )
                if (retryCount < MAX_RETRIES && !isReleased.get() && com.example.util.NetworkMonitor.isOnline.value) {
                    retryCount++
                    Log.w(TAG, "stateId=$stateId retry $retryCount re-resolviendo URL")
                    val expectedStateId = stateId
                    val expectedVideoUrl = lastVideoUrl
                    retryScope.launch {
                        val freshUrl = runCatching {
                            CdnManager.resolveMediaUrl(expectedVideoUrl)
                        }.getOrDefault("")
                        if (isReleased.get() || stateId != expectedStateId || lastVideoUrl != expectedVideoUrl) {
                            return@launch
                        }
                        if (freshUrl.isNotBlank()) {
                            player.setMediaItem(MediaItem.fromUri(freshUrl))
                            player.prepare()
                            player.play()
                        } else {
                            onError?.invoke(stateId, error.errorCodeName ?: "", error.errorCode)
                        }
                    }
                    return
                }
                onError?.invoke(stateId, error.errorCodeName ?: "", error.errorCode)
            }
        })
        return player
    }

    fun play(newStateId: String, videoUrl: String, isMuted: Boolean, videoTrim: Pair<Float, Float>?) {
        stateId = newStateId
        retryCount = 0
        lastVideoUrl = videoUrl
        lastIsMuted = isMuted
        lastTrim = videoTrim
        player.volume = if (isMuted) 0f else 1f
        val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
        if (player.currentMediaItem == null || currentUri != videoUrl) {
            player.setMediaItem(MediaItem.fromUri(videoUrl))
            player.prepare()
            player.play()
        } else {
            if (!player.playWhenReady) player.play()
        }
    }

    fun setPaused(paused: Boolean) {
        if (!isReleased.get()) player.playWhenReady = !paused
    }

    fun setMuted(muted: Boolean) {
        if (!isReleased.get()) player.volume = if (muted) 0f else 1f
    }

    fun release() {
        if (isReleased.compareAndSet(false, true)) {
            retryScope.cancel()
            mainHandler.removeCallbacks(trimRunnable)
            try {
                player.release()
            } catch (_: Exception) {}
        }
    }
}
