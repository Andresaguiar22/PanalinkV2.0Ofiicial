package com.example.ui.components.chat.bubble

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun IncomingBubbleContainer(
    groupPosition: MessageGroupPosition,
    avatarUrl: String?,
    avatarUserId: String? = null,
    modifier: Modifier = Modifier,
    shape: Shape = BubbleShapeFactory.createShape(groupPosition, isMe = false),
    containerColor: Color = Color(0xFF39435A).copy(alpha = 0.90f),
    containerBrush: Brush? = null,
    tonalElevation: Float = 1f,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // Acción 3: Avatar pequeño (24.dp) solo en la última burbuja del bloque
        val showAvatar = groupPosition == MessageGroupPosition.LAST || groupPosition == MessageGroupPosition.SINGLE

        if (showAvatar) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Avatar de contacto",
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            Spacer(modifier = Modifier.width(32.dp))
        }

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
