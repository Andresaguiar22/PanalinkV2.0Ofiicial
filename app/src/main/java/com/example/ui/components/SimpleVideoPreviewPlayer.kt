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
    trimEndSeconds: Float = 0f
) {
    val context = LocalContext.current
    val view = LocalView.current
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var forceRotationDegrees by remember(videoUri) { mutableStateOf(0f) }
    var isVisible by remember { mutableStateOf(false) }

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

    DisposableEffect(videoUri, trimStartSeconds, trimEndSeconds) {
        // Grab a pooled player so scrolling the feed never pays codec init/teardown.
        val player = com.example.core.media.ExoPlayerManager.getPlayer(context).apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = false
            volume = if (isMuted) 0f else 1f
            prepare()
        }

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
            com.example.core.media.ExoPlayerManager.releasePlayer(player)
            exoPlayer = null
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
