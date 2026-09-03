package com.example.feature.chat.ui.message

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
internal fun TypingDotIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    
    val dot1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0f at 0 using LinearEasing
                -6f at 200 using LinearEasing
                0f at 400 using LinearEasing
                0f at 600 using LinearEasing
            },
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(0)
        ),
        label = "dot1"
    )

    val dot2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0f at 0 using LinearEasing
                -6f at 200 using LinearEasing
                0f at 400 using LinearEasing
                0f at 600 using LinearEasing
            },
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(150)
        ),
        label = "dot2"
    )

    val dot3Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0f at 0 using LinearEasing
                -6f at 200 using LinearEasing
                0f at 400 using LinearEasing
                0f at 600 using LinearEasing
            },
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(300)
        ),
        label = "dot3"
    )

    Row(
        modifier = Modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val colors = listOf(Color(0xFF25D366), Color(0xFF00E676), Color(0xFF05C657))
        Box(
            modifier = Modifier
                .size(6.dp)
                .graphicsLayer { translationY = dot1Offset }
                .background(colors[0], CircleShape)
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .graphicsLayer { translationY = dot2Offset }
                .background(colors[1], CircleShape)
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .graphicsLayer { translationY = dot3Offset }
                .background(colors[2], CircleShape)
        )
    }
}

