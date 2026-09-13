package com.example.ui.components.chat.bubble

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
fun OutgoingBubbleContainer(
    groupPosition: MessageGroupPosition,
    modifier: Modifier = Modifier,
    shape: Shape = BubbleShapeFactory.createShape(groupPosition, isMe = true),
    containerColor: Color = Color(0xFF27548F),
    containerBrush: Brush? = null,
    tonalElevation: Float = 1f,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .then(
                    if (containerBrush != null) {
                        Modifier.background(containerBrush)
                    } else {
                        Modifier.background(containerColor)
                    }
                )
        ) {
            content()
        }
    }
}
