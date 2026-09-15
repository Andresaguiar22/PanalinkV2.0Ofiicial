package com.example.ui.components.chat.bubble

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.ui.components.VoiceMessageBubble

@Composable
fun VoiceBubbleContent(
    audioUrl: String,
    isPlaying: Boolean,
    progress: Float,
    durationLabel: String,
    timestamp: String,
    isSender: Boolean,
    senderAvatarUrl: String?,
    onPlayPauseClick: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    com.example.ui.components.chat.media.PremiumVoicePlayer(
        audioUrl = audioUrl,
        isPlaying = isPlaying,
        progress = progress,
        durationLabel = durationLabel,
        senderAvatarUrl = senderAvatarUrl,
        isSender = isSender,
        onPlayPauseClick = onPlayPauseClick,
        onSeek = onSeek,
        modifier = modifier
            .clip(
                if (isSender) {
                    RoundedCornerShape(24.dp, 4.dp, 24.dp, 24.dp)
                } else {
                    RoundedCornerShape(4.dp, 24.dp, 24.dp, 24.dp)
                }
            )
            .background(
                if (isSender) {
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(
                            androidx.compose.ui.graphics.Color(0xFF38BDF8),
                            androidx.compose.ui.graphics.Color(0xFF1D4ED8)
                        )
                    )
                } else {
                    androidx.compose.ui.graphics.SolidColor(
                        androidx.compose.ui.graphics.Color(0xFF1E293B).copy(alpha = 0.9f)
                    )
                }
            )
    )
}
