package com.example.ui.components.chat.bubble

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ui.settings.ios.IosSettingsColors

@Composable
fun VideoBubbleContent(
    videoUrl: String,
    thumbUrl: String,
    durationLabel: String? = null,
    bubbleColor: Color = IosSettingsColors.cell,
    onVideoClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    MediaMessageBubble(
        mediaUrls = listOf(videoUrl),
        isVideo = true,
        thumbnailUrl = thumbUrl,
        durationLabel = durationLabel,
        bubbleColor = bubbleColor,
        onMediaClick = { _, url -> onVideoClick(url) },
        modifier = modifier
    )
}
