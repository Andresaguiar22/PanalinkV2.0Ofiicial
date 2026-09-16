package com.example.core.media

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
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
 *
 * Configuración alineada con el motor de reels ([com.example.reels.engine.ReelPlayerPool]):
 * - Renderers con preferencia de HARDWARE (FFmpeg solo como fallback) —
 *   antes se usaba preferSoftware=true, lo que decodificaba todos los vídeos
 *   VCDN por software y arriesgaba CodecException/stutter en clips largos.
 * - Track selector forzando la máxima resolución/bitrate disponibles.
 * - LoadControl fast-start (300 ms para iniciar, margen amplio post-rebuffer).
 * - Recuperación reactiva de URL firmada caducada: VCDN expira el token ~60 s,
 *   el error HTTP 401 (código Media3 2004) re-resuelve el puntero `vcdn://`
 *   con [CdnManager.resolveMediaUrlFresh] y reanuda desde la misma posición.
 */
@OptIn(UnstableApi::class)
class StoryVideoPlayerSession(private val context: Context) {
    private val TAG = "StoryVideoPlayer"
    private val MAX_RETRIES = 2

    companion object {
        // Media3 error 2004 = HTTP 401 (expired signed token) — triggers the
        // reactive re-resolution path. Like the reel player (v1.3.35), the only
        // defense against VCDN token expiry is REACTIVE: never swap the media item
        // proactively, which would discard the buffered ranges and recreate codecs.
        private const val PLAYBACK_ERROR_HTTP_401 = 2004
    }

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
    /** Puntero estable (`vcdn://{id}` o URL convencional) usado para re-resolver
     *  la URL firmada cuando caduca (HTTP 401). Para VCDN, [lastVideoUrl] es
     *  una URL firmada efímera; re-resolverla a ciegas repetía el mismo token. */
    private var lastStableVideoUrl: String = ""
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
            // Deferred: the renderer's period notification arrives on a background
            // looper; keep the fast loop here and commit the real work on the main
            // handler so progress is always published on the UI thread.
            mainHandler.post {
                if (isReleased.get()) {
                    return@post
                }
                try {
                    val p = this@StoryVideoPlayerSession.player
                    val trim = lastTrim
                    val current = p.currentPosition
                    val rawPlayerDuration = p.duration
                    val isActuallyPlaying = p.isPlaying && p.playbackState == Player.STATE_READY

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

                            if (isActuallyPlaying && current >= actualEndMs) {
                                onPositionChanged?.invoke(effectiveDuration)
                                p.seekTo(startMs)
                            } else if (isActuallyPlaying) {
                                val relativePosition = (current - startMs)
                                    .coerceIn(0L, effectiveDuration)
                                onPositionChanged?.invoke(relativePosition)
                            }
                        } else {
                            onDurationReady?.invoke(0)
                        }
                    } else {
                        val effectiveDuration = rawPlayerDuration.coerceAtLeast(0L)
                        if (effectiveDuration > 0L) {
                            onDurationReady?.invoke(effectiveDuration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                        }
                        if (isActuallyPlaying) {
                            val boundedCurrent = current.coerceIn(0L, effectiveDuration.takeIf { it > 0L } ?: Long.MAX_VALUE)
                            onPositionChanged?.invoke(boundedCurrent)
                            if (effectiveDuration > 0L && boundedCurrent >= effectiveDuration) {
                                p.seekTo(0L)
                                onMediaEnded?.invoke()
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Session released or player tearing down.
                } finally {
                    if (!isReleased.get()) mainHandler.post(this)
                }
            }
        }
    }

    init {
        mainHandler.post(trimRunnable)
    }

    private fun createPlayer(): ExoPlayer {
        // Same fast-start tuning as the reel pool: start almost immediately with
        // 300 ms of buffered data, allow a wide margin after a rebuffer so a long
        // VCDN clip does not stall while the player refills after a URL swap.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                10_000,  // minBufferMs: keep 10s (long HLS VCDN segments)
                60_000,  // maxBufferMs
                300,     // bufferForPlaybackMs: fast start
                6_000    // bufferForPlaybackAfterRebufferMs: tolerant after hiccup
            )
            .setBackBuffer(4_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .clearVideoSizeConstraints()
                    .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                    .setMaxVideoBitrate(Int.MAX_VALUE)
            )
        }
        val player = ExoPlayer.Builder(context, PanaRenderersFactory.create(context))
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
            .setHandleAudioBecomingNoisy(true)
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
                    com.example.feature.diagnostics.StoryDiagnostics.event(
                        "READY",
                        correlationId = stateId.take(36),
                        details = "pos=${player.currentPosition}, duration=${player.duration}"
                    )
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
                    // The trim loop boundary is handled in trimRunnable (emits 100%
                    // then seeks back to start BEFORE the player ever enters ENDED),
                    // so the only genuine ENDED here is the natural clip end.
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
                val isVcdn = com.example.data.repository.VcdnUrlResolver.isVcdnUrl(lastStableVideoUrl)
                val errUrlHost = try { java.net.URI(lastVideoUrl).host } catch (_: Exception) { "" }
                com.example.feature.diagnostics.StoryDiagnostics.failed(
                    "Player error ($stateId)",
                    correlationId = stateId.take(36),
                    details = "code=${error.errorCode}, name=${error.errorCodeName}, isVcdn=$isVcdn, host=$errUrlHost"
                )
                Log.e(
                    TAG,
                    "stateId=$stateId ERROR code=${error.errorCode} name=${error.errorCodeName} msg=${error.message}"
                )
                // Reactive expiry recovery (mirrors ReelPlayerPool.handlePlayerError):
                // a VCDN signed URL expiring mid-playback surfaces as HTTP 401 (2004).
                // Re-resolve from the STABLE pointer (vcdn://) — the current URL is
                // a one-time signed token and re-minting it directly returns the same
                // expired value. Preserve position so the story resumes where it cut.
                val isExpiredToken = error.errorCode == PLAYBACK_ERROR_HTTP_401 &&
                    com.example.data.repository.VcdnUrlResolver.isVcdnUrl(lastStableVideoUrl)
                if (retryCount < MAX_RETRIES && !isReleased.get() &&
                    com.example.util.NetworkMonitor.isOnline.value && isExpiredToken
                ) {
                    retryCount++
                    Log.w(TAG, "stateId=$stateId HTTP 401: re-resolviendo URL firmada")
                    val expectedStateId = stateId
                    val expectedStableUrl = lastStableVideoUrl
                    val positionMs = player.currentPosition.coerceAtLeast(0L)
                    com.example.feature.diagnostics.StoryDiagnostics.started(
                        "Recuperación 401",
                        correlationId = stateId.take(36),
                        details = "attempt=$retryCount, position=$positionMs"
                    )
                    val recoveryStart = System.currentTimeMillis()
                    retryScope.launch {
                        val freshUrl = runCatching {
                            CdnManager.resolveMediaUrlFresh(expectedStableUrl)
                        }.getOrDefault("")
                        if (isReleased.get() || stateId != expectedStateId || lastStableVideoUrl != expectedStableUrl) {
                            return@launch
                        }
                        // forceRefresh re-solving to the SAME URL that already 401'd
                        // means the BFF minted a dead token (or the video is gone but a
                        // stale-cache fallback re-served it). Re-preparing the same URL is
                        // pointless — it will 401 again in a loop. Invalidate VCDN memory
                        // so the NEXT user retry hits the BFF from scratch, and surface a
                        // definitive error now.
                        val isSameDeadUrl = freshUrl == lastVideoUrl
                        if (isSameDeadUrl) {
                            com.example.data.repository.VcdnUrlResolver.invalidate(expectedStableUrl)
                            com.example.feature.diagnostics.StoryDiagnostics.failed(
                                "Recuperación 401",
                                recoveryStart,
                                correlationId = stateId.take(36),
                                details = "attempt=$retryCount, sameUrl=true, cacheInvalidated=1"
                            )
                            onError?.invoke(stateId, error.errorCodeName ?: "", error.errorCode)
                            return@launch
                        }
                        if (freshUrl.isNotBlank() && !freshUrl.startsWith("vcdn://")) {
                            lastVideoUrl = freshUrl
                            try {
                                player.setMediaItem(MediaItem.fromUri(freshUrl))
                                player.prepare()
                                player.seekTo(positionMs)
                                player.play()
                                com.example.feature.diagnostics.StoryDiagnostics.completed(
                                    "Recuperación 401",
                                    recoveryStart,
                                    correlationId = stateId.take(36),
                                    details = "attempt=$retryCount"
                                )
                            } catch (_: IllegalStateException) {
                                // Player being torn down between the check and the swap.
                                com.example.feature.diagnostics.StoryDiagnostics.failed(
                                    "Recuperación 401",
                                    recoveryStart,
                                    correlationId = stateId.take(36),
                                    details = "attempt=$retryCount, ise=true"
                                )
                                onError?.invoke(stateId, error.errorCodeName ?: "", error.errorCode)
                            }
                        } else {
                            com.example.feature.diagnostics.StoryDiagnostics.failed(
                                "Recuperación 401",
                                recoveryStart,
                                correlationId = stateId.take(36),
                                details = "attempt=$retryCount, noFreshUrl=${freshUrl.isNullOrBlank()}"
                            )
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

    /**
     * @param stableVideoUrl puntero estable (`vcdn://{id}`, copia local o URL
     *   convencional) del que se re-resuelve una URL firmada caducada. Puede ser
     *   la misma que [videoUrl] cuando no hay puntero VCDN.
     * @param videoUrl URL ya resuelta y reproducible; nunca un `vcdn://` a secas.
     */
    fun play(
        newStateId: String,
        videoUrl: String,
        isMuted: Boolean,
        videoTrim: Pair<Float, Float>?,
        stableVideoUrl: String? = null,
    ) {
        stateId = newStateId
        retryCount = 0
        lastVideoUrl = videoUrl
        lastStableVideoUrl = stableVideoUrl?.takeIf { it.isNotBlank() } ?: videoUrl
        lastIsMuted = isMuted
        lastTrim = videoTrim
        player.volume = if (isMuted) 0f else 1f
        val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
        val isSwap = player.currentMediaItem == null || currentUri != videoUrl
        val playHost = try { java.net.URI(videoUrl).host } catch (_: Exception) { "" }
        com.example.feature.diagnostics.StoryDiagnostics.event(
            if (isSwap) "Play iniciado" else "Play reanudado",
            correlationId = newStateId.take(36),
            details = "isVcdn=${com.example.data.repository.VcdnUrlResolver.isVcdnUrl(lastStableVideoUrl)}, host=$playHost, trim=${videoTrim != null}"
        )
        if (isSwap) {
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
