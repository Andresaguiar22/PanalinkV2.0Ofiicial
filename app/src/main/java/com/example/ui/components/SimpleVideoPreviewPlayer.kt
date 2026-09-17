package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.viewinterop.AndroidView
import com.example.core.logger.AppLogger
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun SimpleVideoPreviewPlayer(
    videoUri: Uri,
    modifier: Modifier = Modifier,
    isMuted: Boolean = true,
    trimStartSeconds: Float = 0f,
    trimEndSeconds: Float = 0f,
    onPositionUpdate: ((Long) -> Unit)? = null,
    // Puntero estable (vcdn://...) que NO cambia cuando se renueva la URL firmada.
    // Cuando videoUri cambia pero este valor es el mismo, es una renovacion del
    // mismo video: refrescamos en caliente preservando la posicion en lugar de
    // reiniciar desde 0 (un video largo ya no se atasca ni se reinicia a la marca
    // de expiracion de la URL VCDN).
    stableUrl: String? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var forceRotationDegrees by remember(videoUri) { mutableStateOf(0f) }
    var isVisible by remember { mutableStateOf(false) }
    var lastStableUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(videoUri) {
        val key = videoUri.toString()
        // MediaMetadataRetriever cannot parse HLS manifests (.m3u8) or VCDN stream
        // URLs and would block the IO thread trying; rotation detection only makes
        // sense for progressive/local files. Skip it for HLS to avoid stalls.
        if (key.contains(".m3u8", ignoreCase = true) || key.startsWith("vcdn://")) {
            return@LaunchedEffect
        }
        val cached = com.example.core.media.VideoMetadataCache.getRotation(key)
        if (cached != null) {
            if (cached != 0f) forceRotationDegrees = cached
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            var retriever: android.media.MediaMetadataRetriever? = null
            try {
                retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(context, videoUri)
                val rotationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                val widthStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val heightStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)

                val rotation = rotationStr?.toIntOrNull() ?: 0
                val width = widthStr?.toIntOrNull() ?: 0
                val height = heightStr?.toIntOrNull() ?: 0

                // If the video is wider than it is tall, but was shot vertically
                val needed = if (width > height && (rotation == 0 || rotation == 180)) 90f else 0f
                com.example.core.media.VideoMetadataCache.putRotation(key, needed)
                if (needed != 0f) {
                    withContext(Dispatchers.Main) {
                        forceRotationDegrees = needed
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(message = "Error detecting video rotation", throwable = e)
            } finally {
                try {
                    retriever?.release()
                } catch (e: Exception) {}
            }
        }
    }

    DisposableEffect(videoUri, trimStartSeconds, trimEndSeconds, stableUrl) {
        // Capturamos en el cuerpo (no en onDispose): indica si ESTA emision es un
        // refresh en caliente del mismo video (y por tanto NO hay que liberar el
        // player al cerrar, porque el siguiente efecto lo va a reutilizar).
        val isHotRefresh = exoPlayer != null && stableUrl != null && stableUrl == lastStableUrl
        val player = if (isHotRefresh) {
            // Mismo video, URL renovada: refrescar en caliente sobre el MISMO player
            // preservando posicion y estado de reproduccion (sin reiniciar).
            val p = exoPlayer!!
            val pos = p.currentPosition
            val playing = p.playWhenReady
            try {
                p.setMediaItem(MediaItem.fromUri(videoUri), true) // resetPosition=false
                p.prepare()
                p.seekTo(pos)
                p.playWhenReady = playing
            } catch (_: Exception) {}
            p
        } else {
            // Video distinto o primera carga: pool (para el scroll) o reconstruir.
            val poolPlayer = com.example.core.media.ExoPlayerManager.getPlayer(context).apply {
                setMediaItem(MediaItem.fromUri(videoUri))
                repeatMode = Player.REPEAT_MODE_ALL
                playWhenReady = false
                volume = if (isMuted) 0f else 1f
                prepare()
            }
            poolPlayer
        }
        lastStableUrl = stableUrl

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && trimStartSeconds > 0) {
                    player.seekTo((trimStartSeconds * 1000).toLong())
                }
            }
        }
        player.addListener(listener)
        exoPlayer = player

        onDispose {
            player.removeListener(listener)
            if (!isHotRefresh) {
                com.example.core.media.ExoPlayerManager.releasePlayer(player)
            }
            if (exoPlayer === player) exoPlayer = null
        }
    }

    LaunchedEffect(isMuted, exoPlayer) {
        exoPlayer?.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(exoPlayer, trimStartSeconds, trimEndSeconds) {
        val p = exoPlayer ?: return@LaunchedEffect
        val hasTrims = trimStartSeconds > 0f || trimEndSeconds > 0f
        if (!hasTrims) return@LaunchedEffect // no loop-polling coroutine for regular playback
        while (true) {
            kotlinx.coroutines.delay(100)
            if (p.playbackState == Player.STATE_READY) {
                val currentPosMs = p.currentPosition
                val startMs = (trimStartSeconds * 1000).toLong()
                val duration = p.duration
                if (duration > 0) {
                    val endMs = if (trimEndSeconds > 0f) (trimEndSeconds * 1000).toLong() else duration
                    if (currentPosMs < startMs) {
                        p.seekTo(startMs)
                    } else if (endMs > startMs && currentPosMs >= endMs) {
                        p.seekTo(startMs)
                    }
                }
            }
        }
    }

    LaunchedEffect(isVisible, exoPlayer) {
        exoPlayer?.playWhenReady = isVisible
    }

    // Report current position for video continuity
    LaunchedEffect(exoPlayer, isVisible) {
        val player = exoPlayer ?: return@LaunchedEffect
        if (!isVisible) return@LaunchedEffect
        while (true) {
            if (player.playbackState == Player.STATE_READY) {
                onPositionUpdate?.invoke(player.currentPosition)
            }
            kotlinx.coroutines.delay(200)
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                player = exoPlayer
            }
        },
        update = { playerView ->
            playerView.player = exoPlayer
        },
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                val viewRect = android.graphics.Rect()
                view.getGlobalVisibleRect(viewRect)
                val visibleWidth = (kotlin.math.min(bounds.right, viewRect.right.toFloat()) - kotlin.math.max(bounds.left, viewRect.left.toFloat())).coerceAtLeast(0f)
                val visibleHeight = (kotlin.math.min(bounds.bottom, viewRect.bottom.toFloat()) - kotlin.math.max(bounds.top, viewRect.top.toFloat())).coerceAtLeast(0f)
                val visibleArea = visibleWidth * visibleHeight
                val totalArea = bounds.width * bounds.height
                isVisible = if (totalArea > 0) (visibleArea / totalArea) >= 0.5f else false
            }
            .graphicsLayer {
                if (forceRotationDegrees != 0f) {
                    rotationZ = forceRotationDegrees
                }
            }
    )
}
