package com.example.ui.call

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Videocam

import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PanaAvatar
import com.example.ui.settings.ios.IosSettingsColors

/**
 * Premium incoming call screen (WhatsApp-style) with a soft gradient
 * background, big avatar, pulsing animation, and large accept/reject
 * buttons. Purely cosmetic — the underlying accept/reject callbacks and
 * the CallScreen triggers remain unchanged.
 */
@Composable
fun IncomingCallScreen(
    opponentId: String? = null,
    opponentName: String,
    isVideo: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "incomingPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        IosSettingsColors.groupBackground,
                        IosSettingsColors.cell,
                        IosSettingsColors.groupBackground
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp)
            ) {
                Text(
                    text = if (isVideo) "Videollamada entrante" else "Llamada de voz entrante",
                    color = IosSettingsColors.secondaryLabel,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = opponentName,
                    color = IosSettingsColors.label,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Panalink",
                    color = IosSettingsColors.tertiaryLabel,
                    fontSize = 12.sp
                )
            }

            // Avatar with pulsing ring
            Box(
                modifier = Modifier.size(216.dp),
                contentAlignment = Alignment.Center
            ) {
                // Animated outer ring
                Box(
                    modifier = Modifier
                        .size(164.dp)
                        .scale(pulse)
                        .background(
                            color = if (isVideo) IosSettingsColors.blue.copy(alpha = 0.35f) else IosSettingsColors.green.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                )
                // Inner ring
                Box(
                    modifier = Modifier
                        .size(154.dp)
                        .scale(pulse)
                        .background(
                            color = if (isVideo) IosSettingsColors.blue.copy(alpha = 0.6f) else IosSettingsColors.green.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                )
                PanaAvatar(
                    avatarUrl = null,
                    userId = opponentId,
                    placeholderName = opponentName,
                    size = 128.dp,
                    modifier = Modifier
                        .size(128.dp)
                        .clip(CircleShape)
                )
            }

            // Big action buttons
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 64.dp)
            ) {
                // Reject
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier
                            .size(78.dp)
                            .scale(1f)
                            .clickable(onClick = onReject),
                        color = IosSettingsColors.red,
                        shape = CircleShape,
                        shadowElevation = 8.dp
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CallEnd,
                            contentDescription = "Rechazar",
                            tint = IosSettingsColors.label,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Rechazar",
                        color = IosSettingsColors.red,
                        fontSize = 12.sp
                    )
                }

                // Accept
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier
                            .size(78.dp)
                            .clickable(onClick = onAccept),
                        color = IosSettingsColors.green,
                        shape = CircleShape,
                        shadowElevation = 8.dp
                    ) {
                        Icon(
                            imageVector = if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                            contentDescription = "Aceptar",
                            tint = IosSettingsColors.label,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Aceptar",
                        color = IosSettingsColors.green,
                        fontSize = 12.sp
                    )
                }
            }

        }
    }
}
