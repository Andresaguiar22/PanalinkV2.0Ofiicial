package com.example.features.stickers.studio

import android.net.Uri
import android.view.Surface
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.core.media.ExoPlayerManager
import kotlinx.coroutines.delay

private val PANA_GREEN = Color(0xFF00A884)

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun VideoTrimmerScreen(
    videoUri: Uri,
    onConfirm: (startTimeMs: Long, endTimeMs: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val maxDurationSec = 5
    val player = remember {
        ExoPlayerManager.getPlayer(context).apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            playWhenReady = true
            prepare()
        }
    }

    var durationMs by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    var trimStart by remember { mutableStateOf(0f) }  // 0f to 1f
    var trimEnd by remember { mutableStateOf(0.5f) }  // 0f to 1f

    fun startMs(): Long = (trimStart * durationMs).toLong()
    fun endMs(): Long = (trimEnd * durationMs).toLong()

    LaunchedEffect(player) {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    durationMs = player.duration.coerceAtLeast(0L)
                }
            }
        })
        while (isPlaying) {
            delay(100)
            if (player.currentPosition >= endMs() && endMs() > 0) {
                player.seekTo(startMs())
                player.play()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.stop()
            ExoPlayerManager.releasePlayer(player)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B141A))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = Color.White)
            }
            Text(
                text = "Recortar video (${(endMs() - startMs()) / 1000f}s)",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            TextButton(
                onClick = {
                    val segMs = endMs() - startMs()
                    val s = startMs()
                    val e = endMs()
                    if (segMs < 200L) {
                        onConfirm(s, e.coerceAtLeast(s + 2000L))
                    } else {
                        onConfirm(s, e)
                    }
                },
                enabled = (endMs() - startMs()) >= 200L
            ) {
                Icon(Icons.Default.Check, contentDescription = "Confirmar", tint = if ((endMs() - startMs()) >= 200L) PANA_GREEN else Color.Gray)
            }
        }

        // Video preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(1f),
                factory = {
                    PlayerView(context).apply {
                        this.player = player
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        useController = false
                    }
                }
            )

            // Play/pause overlay
            IconButton(
                onClick = {
                    if (isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                    isPlaying = !isPlaying
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0x66000000), RoundedCornerShape(50.dp))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.PlayArrow else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Timeline trimmer
        if (durationMs > 0) {
            TrimTimeline(
                durationMs = durationMs,
                startMs = startMs(),
                endMs = endMs(),
                trimStart = trimStart,
                trimEnd = trimEnd,
                onTrimStartChange = { trimStart = it.coerceIn(0f, trimEnd - 0.01f) },
                onTrimEndChange = { trimEnd = it.coerceIn(trimStart + 0.01f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun TrimTimeline(
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    trimStart: Float,
    trimEnd: Float,
    onTrimStartChange: (Float) -> Unit,
    onTrimEndChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF1F2C34), RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp)
    ) {
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .align(Alignment.Center)
        ) {
            val width = size.width
            val height = size.height
            val left = width * trimStart
            val right = width * trimEnd

            // Background track
            drawLine(
                color = Color(0xFF4A5568),
                start = Offset(0f, height / 2),
                end = Offset(width, height / 2),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Selected segment
            drawLine(
                color = PANA_GREEN,
                start = Offset(left, height / 2),
                end = Offset(right, height / 2),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Start handle
        Box(
            modifier = Modifier
                .size(24.dp)
                .align(Alignment.CenterStart)
                .offset(x = (trimStart * 100 - 12).dp)
                .background(PANA_GREEN, RoundedCornerShape(50))
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        val deltaX = change.position.x - change.previousPosition.x
                        val newPos = ((trimStart * size.width + deltaX) / size.width).coerceIn(0f, trimEnd - 0.01f)
                        onTrimStartChange(newPos)
                        change.consume()
                    }
                }
        )

        // End handle
        Box(
            modifier = Modifier
                .size(24.dp)
                .align(Alignment.CenterEnd)
                .offset(x = (-(1f - trimEnd) * 100 + 12).dp)
                .background(PANA_GREEN, RoundedCornerShape(50))
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        val deltaX = change.position.x - change.previousPosition.x
                        val newPos = ((trimEnd * size.width + deltaX) / size.width).coerceIn(trimStart + 0.01f, 1f)
                        onTrimEndChange(newPos)
                        change.consume()
                    }
                }
        )
    }
}
