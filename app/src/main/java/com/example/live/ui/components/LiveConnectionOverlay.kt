package com.example.live.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.live.domain.model.LiveConnectionState
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun LiveConnectionOverlay(
    connectionState: LiveConnectionState,
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(
        targetValue = if (connectionState is LiveConnectionState.Connected) 0f else 0.85f,
        animationSpec = tween(durationMillis = 300),
        label = "overlay_alpha"
    )

    if (alpha > 0.01f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .alpha(alpha),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = CircleShape,
                modifier = Modifier.size(120.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val icon = when (connectionState) {
                        is LiveConnectionState.Connecting -> Icons.Default.Wifi
                        is LiveConnectionState.Reconnecting -> Icons.Default.Refresh
                        is LiveConnectionState.Error -> Icons.Default.WifiOff
                        else -> Icons.Default.Wifi
                    }
                    val text = when (connectionState) {
                        is LiveConnectionState.Connecting -> "Conectando..."
                        is LiveConnectionState.Reconnecting -> "Reconectando..."
                        is LiveConnectionState.Error -> "Sin señal"
                        else -> ""
                    }
                    val color = when (connectionState) {
                        is LiveConnectionState.Error -> Color(0xFFEF5350)
                        is LiveConnectionState.Reconnecting -> Color(0xFFFFC107)
                        else -> Color.White
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = text,
                        tint = color,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun LivePulseIndicator(
    isLive: Boolean,
    modifier: Modifier = Modifier
) {
    var pulseScale by remember { mutableStateOf(1f) }
    var pulseAlpha by remember { mutableStateOf(0.8f) }

    LaunchedEffect(isLive) {
        if (isLive) {
            while (true) {
                pulseScale = 1.4f
                pulseAlpha = 0f
                delay(1200)
                pulseScale = 1f
                pulseAlpha = 0.8f
                delay(1200)
            }
        }
    }

    if (isLive) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .scale(pulseScale)
                    .alpha(pulseAlpha)
                    .background(Color(0xFFEF5350), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(Color(0xFFEF5350), CircleShape)
            )
        }
    }
}
