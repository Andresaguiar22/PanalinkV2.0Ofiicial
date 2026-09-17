package com.example.live.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.live.ui.LiveEndRed
import com.example.live.ui.LiveHudBorder
import com.example.live.ui.LiveHudFill
import com.example.live.ui.LiveLiveGlow
import com.example.live.ui.LiveLiveRed
import com.example.live.ui.LiveNeon
import com.example.live.ui.formatLiveElapsed

/** Diámetro de los botones-herramienta del HUD inferior. */
val LiveHudButtonSize = 48.dp

/**
 * Píldora de estado del anfitrión: indicador REC, tiempo en vivo y espectadores.
 *
 * Va flotando sobre el video, así que el fondo es traslúcido oscuro con borde fino;
 * sin ese contraste el texto blanco se pierde en escenas claras.
 */
@Composable
fun LiveStatusPill(
    elapsedSeconds: Int,
    viewerCount: Int,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "rec-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "rec-pulse-alpha"
    )

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(LiveHudFill)
            .border(1.dp, LiveHudBorder, CircleShape)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlowDot(color = LiveLiveRed, haloColor = LiveLiveGlow, size = 14.dp, haloAlpha = pulse)

        Text(
            text = "REC",
            color = LiveLiveRed,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = formatLiveElapsed(elapsedSeconds),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )

        Icon(
            imageVector = Icons.Rounded.Visibility,
            contentDescription = "Espectadores",
            tint = Color.White,
            modifier = Modifier.size(17.dp),
        )

        Text(
            text = viewerLabel(viewerCount),
            color = Color.White,
            fontSize = 13.5.sp,
        )
    }
}

/**
 * Botón-herramienta circular del HUD (micrófono, cámara, invertir, comentarios).
 *
 * @param alert cuando es true el botón se pinta en rojo: el dispositivo está apagado.
 * @param statusDot dibuja el punto verde de "encendido" en la esquina superior derecha.
 */
@Composable
fun LiveGlassCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    alert: Boolean = false,
    statusDot: Boolean = false,
    size: Dp = LiveHudButtonSize,
) {
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(if (alert) LiveEndRed.copy(alpha = 0.85f) else LiveHudFill)
                .border(1.dp, if (alert) LiveEndRed else LiveHudBorder, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(size * 0.47f),
            )
        }

        if (statusDot) {
            GlowDot(
                color = LiveNeon,
                haloColor = LiveNeon,
                size = 13.dp,
                haloAlpha = 0.5f,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp),
            )
        }
    }
}

/** Punto de color con halo suave, dibujado a mano para que se lea sobre el video. */
@Composable
fun GlowDot(
    color: Color,
    haloColor: Color,
    size: Dp,
    haloAlpha: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(haloColor.copy(alpha = haloAlpha), Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
        drawCircle(
            color = color,
            radius = radius * 0.45f,
            center = center,
        )
    }
}

/** "1 Espectador" / "N Espectadores", como en el diseño. */
fun viewerLabel(viewerCount: Int): String =
    if (viewerCount == 1) "1 Espectador" else "$viewerCount Espectadores"
