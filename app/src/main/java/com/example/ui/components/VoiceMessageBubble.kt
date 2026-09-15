package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage

@Composable
fun VoiceMessageBubble(
    audioUrl: String,
    isPlaying: Boolean,
    progress: Float, // value between 0f and 1f
    durationLabel: String,
    timestamp: String,
    isSender: Boolean,
    onPlayPauseClick: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    senderAvatarUrl: String? = null
) {
    val appColors = com.example.ui.theme.LocalAppColors.current
    val isVoiceNote = true // Acción 4: always voice note style
    
    // Acción 1 & 4: Premium Glassmorphism colors
    val bubbleColor = if (isSender) Color(0xFF1D4ED8) else Color(0xFF1E293B).copy(alpha = 0.9f)
    val contentColor = if (isSender) Color.White else Color(0xE6FFFFFF)
    val playedColor = if (isSender) Color(0xFF00E5FF) else Color(0xFF38BDF8)
    val unplayedColor = Color(0xFF94A3B8).copy(alpha = 0.3f)
    
    // Waveform configuration
    val barCount = 35
    val amplitudes = remember(audioUrl) {
        val seed = audioUrl.hashCode().toLong()
        val random = java.util.Random(seed)
        List(barCount) { 0.15f + random.nextFloat() * 0.85f }
    }

    // Animation for "live" feeling when playing
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "wave")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "waveOffset"
    )

    Row(
        modifier = modifier
            .clip(
                if (isSender) {
                    RoundedCornerShape(
                        topStart = 24.dp,
                        topEnd = 4.dp,
                        bottomStart = 24.dp,
                        bottomEnd = 24.dp
                    )
                } else {
                    RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 24.dp,
                        bottomStart = 24.dp,
                        bottomEnd = 24.dp
                    )
                }
            )
            .background(bubbleColor)
            .widthIn(min = 240.dp, max = 300.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ... (Profile photo Box)
        Box(
            modifier = Modifier
                .size(40.dp)
                .padding(2.dp)
        ) {
            com.example.ui.components.PanaAvatar(
                avatarUrl = senderAvatarUrl,
                size = 36.dp,
                borderWidth = 0.dp,
                contentDescription = "Avatar de remitente"
            )
            // Premium microphone badge (cyan for voice notes)
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(playedColor)
                    .align(Alignment.BottomEnd)
                    .padding(1.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Play/Pause circular button (premium cyan)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(playedColor.copy(alpha = 0.15f))
                .clickable { onPlayPauseClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pausar nota de voz" else "Reproducir nota de voz",
                tint = playedColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Waveform Canvas + Duration column
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Interactive Waveform Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp) // Slightly taller for better visualization
                    .pointerInput(audioUrl) {
                        detectTapGestures { offset ->
                            val seekFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeek(seekFraction)
                        }
                    }
                    .pointerInput(audioUrl) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val seekFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            onSeek(seekFraction)
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    
                    val barWidth = 2.5f.dp.toPx()
                    val spacing = 2f.dp.toPx()
                    val totalBarWidth = barWidth + spacing
                    
                    // Center the waveform vertically inside canvas
                    val maxBarHeight = canvasHeight * 0.9f
                    
                    // Draw each vertical amplitude bar
                    for (i in 0 until barCount) {
                        var amplitude = amplitudes[i]
                        
                        // Add live effect if playing
                        if (isPlaying) {
                            val phase = (waveOffset * 2 * Math.PI + i * 0.5).toFloat()
                            val variation = Math.sin(phase.toDouble()).toFloat() * 0.15f
                            amplitude = (amplitude + variation).coerceIn(0.1f, 1.0f)
                        }

                        val barHeight = maxBarHeight * amplitude
                        val x = i * totalBarWidth + spacing / 2
                        val y = (canvasHeight - barHeight) / 2
                        
                        val barProgressFraction = i.toFloat() / barCount.toFloat()
                        val isPlayed = barProgressFraction <= progress
                        
                        val barColor = if (isPlayed) {
                            playedColor
                        } else {
                            unplayedColor
                        }

                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Info row (Duration, Timestamp, and double check marks)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = durationLabel,
                    color = contentColor.copy(alpha = 0.65f),
                    fontSize = 10.sp,
                    style = MaterialTheme.typography.bodySmall
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = timestamp,
                        color = contentColor.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (isSender) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy((-4).dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                             Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Leído",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(11.dp)
                            )
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Leído",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
