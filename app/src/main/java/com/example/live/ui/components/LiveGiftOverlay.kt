package com.example.live.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.live.ui.viewmodel.LiveGiftPulse

/**
 * Muestra el último regalo recibido: banner con remitente y una animación
 * flotante del emoji. Se dispara con cada [pulse] real (local o de otro espectador).
 */
@Composable
fun LiveGiftOverlay(
    pulse: LiveGiftPulse?,
    modifier: Modifier = Modifier
) {
    var current by remember { mutableStateOf<LiveGiftPulse?>(null) }
    var bannerAlpha by remember { mutableStateOf(0f) }
    var floatOffset by remember { mutableStateOf(0f) }
    var floatAlpha by remember { mutableStateOf(0f) }
    var floatScale by remember { mutableStateOf(0.6f) }

    LaunchedEffect(pulse?.id) {
        val value = pulse ?: return@LaunchedEffect
        current = value

        bannerAlpha = 1f
        floatOffset = 0f
        floatAlpha = 1f
        floatScale = 0.6f

        val animation = Animatable(0f)
        animation.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2600, easing = LinearEasing)
        ) {
            floatOffset = -this.value * 260f
            floatAlpha = (1f - this.value).coerceIn(0f, 1f)
            floatScale = 0.6f + (this.value * 0.7f)
        }

        floatAlpha = 0f
        kotlinx.coroutines.delay(600)
        bannerAlpha = 0f
        current = null
    }

    Box(modifier = modifier, contentAlignment = Alignment.BottomStart) {
        val visible = current
        if (visible != null) {
            val identity = rememberLiveIdentity(visible.senderId)
            val senderName = identity.displayNameOr(visible.senderId)

            Column(horizontalAlignment = Alignment.Start) {
                Box(
                    modifier = Modifier
                        .offset(x = 0.dp, y = floatOffset.dp)
                        .scale(floatScale)
                        .alpha(floatAlpha)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = visible.emoji, fontSize = 34.sp)
                        if (visible.quantity > 1) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "x${visible.quantity}",
                                color = Color(0xFFFFD54F),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .alpha(bannerAlpha)
                        .background(
                            Color.Black.copy(alpha = 0.55f),
                            RoundedCornerShape(50)
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = visible.emoji, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$senderName envió ${visible.name} x${visible.quantity}",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
