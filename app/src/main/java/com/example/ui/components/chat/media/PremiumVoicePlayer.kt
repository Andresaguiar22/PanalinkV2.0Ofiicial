package com.example.ui.components.chat.media

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import com.example.ui.components.PanaAvatar

@Composable
fun PremiumVoicePlayer(
    audioUrl: String,
    isPlaying: Boolean,
    progress: Float,
    durationLabel: String,
    senderAvatarUrl: String?,
    isSender: Boolean,
    onPlayPauseClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit = {},
    isLoading: Boolean = false,
    isError: Boolean = false,
    messageStatus: String? = "sent",
    isSending: Boolean = false,
    isVoiceNote: Boolean = true,
    uploadBytesWritten: Long = 0L,
    uploadTotalBytes: Long = 0L,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF1E293B)
) {
    var playbackSpeed by remember { mutableFloatStateOf(1f) }

    // Waveform premium: amplitud variable con picos y valles (como decibeles reales)
    val barCount = 40
    val amplitudes = remember(audioUrl) {
        val seed = audioUrl.hashCode().toLong()
        List(barCount) { i ->
            val harmonic = 0.35f + 0.55f * abs(
                0.65f * kotlin.math.cos((i * 0.35 + seed % 11).toDouble()).toFloat() +
                0.35f * kotlin.math.cos((i * 0.85 + (seed % 7)).toDouble()).toFloat()
            )
            harmonic.coerceIn(0.12f, 0.96f)
        }
    }

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val currentProgress = if (isDragging) dragProgress else progress

    val pulseTransition = rememberInfiniteTransition(label = "WavePulse")
    val pulseAlpha by if (isPlaying) {
        pulseTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "PulseAlpha"
        )
    } else {
        remember { mutableStateOf(1.0f) }
    }

    val effectiveIsSending = isSending || messageStatus == "sending" || messageStatus == "pending" || messageStatus == "pending_media"
    val isFailed = messageStatus == "failed"

    // Acción 1 & 4: Premium Glassmorphism colors
    // El fondo real de la burbuja lo pinta el contenedor (Incoming/OutgoingBubbleContainer);
    // aquí solo se usa para los bordes del badge del avatar.
    val bubbleBgColor = backgroundColor
    val contentTextColor = if (isSender) Color.White else Color(0xE6FFFFFF) // 90% white
    val playedColor = if (isVoiceNote || isSender) {
        if (isSender) Color(0xFF00E5FF) else Color(0xFF38BDF8)
    } else {
        Color(0xFFA78BFA)
    }
    val unplayedColor = Color(0xFF94A3B8).copy(alpha = 0.35f)
    val secondaryText = Color(0xFF94A3B8)

    // Acción 4: La forma asimétrica la aplica el contenedor de la burbuja.
    val waveTransition = rememberInfiniteTransition(label = "WaveAnimation")
    val waveOffset by if (isPlaying) {
        waveTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "WaveOffset"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    // Acción 4: Voice note bubble with asymmetric shape + waveform.
    // La forma y el fondo los aplica el contenedor de la burbuja (glassmorphism);
    // aquí NO se repinta el fondo para evitar un recuadro dentro de la burbuja.
    Box(
        modifier = modifier
            .widthIn(min = 240.dp, max = 320.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
        // Play/Pause button
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(enabled = !isLoading && !effectiveIsSending) { onPlayPauseClick() },
            contentAlignment = Alignment.Center
        ) {
            if (effectiveIsSending || isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = playedColor,
                    strokeWidth = 2.dp,
                    trackColor = Color.Transparent
                )
            } else {
                Icon(
                    imageVector = when {
                        isFailed -> Icons.Default.Error
                        isError -> Icons.Default.Refresh
                        isPlaying -> Icons.Default.Pause
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = null,
                    tint = if (isFailed) Color.Red else playedColor,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(2.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Waveform rendering with seek dot
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .pointerInput(audioUrl) {
                        detectTapGestures { offset ->
                            val w = size.width
                            if (w > 0) onSeek((offset.x / w).coerceIn(0f, 1f))
                        }
                    }
                    .pointerInput(audioUrl) {
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onDrag = { change, _ ->
                                val w = size.width
                                if (w > 0) {
                                    dragProgress = (change.position.x / w).coerceIn(0f, 1f)
                                    onSeek(dragProgress)
                                }
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barWidth = 3.dp.toPx()
                    val spacing = 1.6.dp.toPx()
                    val totalW = barWidth + spacing
                    val count = (size.width / totalW).toInt().coerceAtMost(barCount)
                    val midY = size.height / 2

                    for (i in 0 until count) {
                        val baseAmp = amplitudes.getOrElse(i) { 0.3f }
                        val phase = if (isPlaying) (waveOffset * 2 * Math.PI).toFloat() else 0f
                        val dynAmp = if (isPlaying) {
                            (baseAmp * (0.75f + 0.25f * Math.sin((i * 0.6 + phase).toDouble()).toFloat())).coerceIn(0.15f, 1.2f)
                        } else baseAmp

                        val h = (size.height * 0.95f) * dynAmp
                        val x = i * totalW + (size.width - (count * totalW)) / 2
                        val barProgress = i.toFloat() / count.toFloat()
                        val color = if (barProgress <= currentProgress) playedColor.copy(alpha = pulseAlpha) else unplayedColor

                        drawRoundRect(
                            color = color,
                            topLeft = Offset(x, midY - h / 2),
                            size = Size(barWidth, h),
                            cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                        )
                    }

                    // Seek dot on the progress edge
                    val dotX = (size.width * currentProgress).coerceIn(6.dp.toPx(), size.width - 6.dp.toPx())
                    drawCircle(
                        color = playedColor,
                        radius = 5.dp.toPx(),
                        center = Offset(dotX, midY)
                    )
                }
            }

            // Small progress bar for uploading - "Minucioso"
            if (effectiveIsSending) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .clip(CircleShape),
                    color = playedColor,
                    trackColor = playedColor.copy(alpha = 0.1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        isFailed -> "Error de envío"
                        effectiveIsSending && uploadTotalBytes > 0L ->
                            formatUploadKb(uploadBytesWritten) + " / " + formatUploadKb(uploadTotalBytes)
                        effectiveIsSending -> "Subiendo..."
                        isError -> "Error de descarga"
                        else -> durationLabel
                    },
                    color = if (isFailed) Color.Red else secondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                // Speed Selector
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            playbackSpeed = when (playbackSpeed) {
                                1f -> 1.5f
                                1.5f -> 2f
                                else -> 1f
                            }
                            onSpeedChange(playbackSpeed)
                        },
                    color = Color.White.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = "${if (playbackSpeed % 1f == 0f) playbackSpeed.toInt() else playbackSpeed}x",
                        color = playedColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Sender avatar with mic badge at the trailing edge (WhatsApp style)
        Box {
            PanaAvatar(
                avatarUrl = senderAvatarUrl,
                modifier = Modifier.size(34.dp),
                size = 34.dp,
                borderWidth = 0.dp
            )

            if (isVoiceNote) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .background(playedColor, CircleShape)
                        .border(1.2.dp, bubbleBgColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(9.dp)
                    )
                }
            }
        }
    }
}

}

private fun formatUploadKb(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024f * 1024f))
    else -> String.format("%.0f KB", bytes / 1024f)
}
