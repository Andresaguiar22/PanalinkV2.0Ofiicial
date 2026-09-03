package com.example.feature.chat.ui.call

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Profile
import com.example.ui.components.PanaAvatar
import com.example.ui.theme.bounceClick

@Composable
fun ActiveCallOverlay(
    activeCallState: String?,
    callTimerSeconds: Int,
    otherUser: Profile?,
    onReject: () -> Unit,
    onAccept: () -> Unit,
    onHangUp: () -> Unit,
) {
    if (activeCallState != null) {
        val otherName = otherUser?.displayName ?: ""
        
        Box(
        modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.94f))
        .clickable(enabled = false) {}, // absorb clicks
        contentAlignment = Alignment.Center
        ) {
        Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(24.dp)
        ) {
        // Glow & pulsate effect
        val infinitePulse = rememberInfiniteTransition(label = "pulse")
        val pulseScale by infinitePulse.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
        animation = tween(1000, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
        )
        
        Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(160.dp)
        ) {
        // Pulsing glow rings
        Box(
        modifier = Modifier
        .size(140.dp)
        .scale(pulseScale)
        .background(
        if (activeCallState == "active") Color(0xFF25D366).copy(alpha = 0.15f)
        else Color(0xFF007AFF).copy(alpha = 0.15f),
        CircleShape
        )
        )
        
        com.example.ui.components.PanaAvatar(
        avatarUrl = otherUser?.avatarUrl,
        userId = otherUser?.id,
        size = 100.dp,
        borderColor = if (activeCallState == "active") Color(0xFF25D366) else Color(0xFF007AFF),
        borderWidth = 3.dp,
        placeholderName = otherName
        )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
        text = otherName,
        color = Color.White,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (activeCallState == "ringing") {
        Text(
        text = "Llamando de pana... 🔔",
        color = Color(0xFF00E676),
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Row(
        horizontalArrangement = Arrangement.spacedBy(40.dp),
        verticalAlignment = Alignment.CenterVertically
        ) {
        // Reject call
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
        onClick = onReject,
        modifier = Modifier
        .size(64.dp)
        .bounceClick()
        .background(Color.Red, CircleShape)
        ) {
        Icon(Icons.Default.Close, contentDescription = "Rechazar", tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Rechazar", color = Color.White, fontSize = 12.sp)
        }
        
        // Accept call
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
        onClick = onAccept,
        modifier = Modifier
        .size(64.dp)
        .bounceClick()
        .background(Color(0xFF25D366), CircleShape)
        ) {
        Icon(Icons.Default.Call, contentDescription = "Contestar", tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Atender", color = Color.White, fontSize = 12.sp)
        }
        }
        } else if (activeCallState == "active") {
        val mins = callTimerSeconds / 60
        val secs = callTimerSeconds % 60
        Text(
        text = String.format("Llamada activa • %02d:%02d", mins, secs),
        color = Color.White.copy(alpha = 0.7f),
        fontSize = 16.sp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Simulated live call wave visualizer
        AudioVisualizer(
        isPlaying = true,
        modifier = Modifier
        .width(200.dp)
        .height(40.dp)
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Hang up
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
        onClick = onHangUp,
        modifier = Modifier
        .size(64.dp)
        .bounceClick()
        .background(Color.Red, CircleShape)
        ) {
        Icon(Icons.Default.Close, contentDescription = "Colgar", tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Colgar", color = Color.White, fontSize = 12.sp)
        }
        }
        }
        }
    }
}

@Composable
fun AudioVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio_visualizer")

    val animationScale by if (isPlaying) {
    infiniteTransition.animateFloat(
    initialValue = 0.3f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
    animation = tween(durationMillis = 450, easing = LinearEasing),
    repeatMode = RepeatMode.Reverse
    ),
    label = "wave_scale"
    )
    } else {
    remember { mutableStateOf(0.3f) }
    }
    
    androidx.compose.foundation.Canvas(
    modifier = modifier
    .fillMaxWidth()
    .height(28.dp)
    .padding(vertical = 2.dp)
    ) {
    val barCount = 40
    val spacing = 2.dp.toPx()
    val totalSpacing = spacing * (barCount - 1)
    val barWidth = (size.width - totalSpacing) / barCount
    val maxBarHeight = size.height
    
    for (i in 0 until barCount) {
    val wavePhase = (i * 0.2f).toFloat()
    val dynamicScale = if (isPlaying) {
    val sinValue = kotlin.math.sin(animationScale * Math.PI * 2 + wavePhase)
    (kotlin.math.abs(sinValue).toFloat() * 0.7f + 0.3f)
    } else {
    val base = kotlin.math.sin(wavePhase)
    (kotlin.math.abs(base) * 0.4f + 0.2f).toFloat()
    }
    
    val barHeight = maxBarHeight * dynamicScale
    val x = i * (barWidth + spacing)
    val y = (maxBarHeight - barHeight) / 2
    
    drawRoundRect(
    color = if (isPlaying) Color(0xFF25D366) else Color(0xFF8596A0).copy(alpha = 0.6f),
    topLeft = androidx.compose.ui.geometry.Offset(x, y),
    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2, barWidth / 2)
    )
    }
    }
}
