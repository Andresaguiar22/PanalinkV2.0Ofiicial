package com.example.feature.chat.ui.message

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun AnimatedAudioWaves(
    amplitudes: List<Float> = emptyList(),
    isPaused: Boolean = false
) {
    val barList = if (amplitudes.isEmpty()) List(10) { 0.1f } else amplitudes
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        barList.forEach { level ->
            val targetHeight = if (isPaused) 4.dp else (4.dp + (level * 20).dp)
            val animatedHeight by animateDpAsState(
                targetValue = targetHeight,
                animationSpec = tween(durationMillis = 80, easing = LinearEasing),
                label = "RealWaveHeight"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(animatedHeight)
                    .background(Color(0xFF00A884), RoundedCornerShape(1.5.dp))
            )
        }
    }
}


