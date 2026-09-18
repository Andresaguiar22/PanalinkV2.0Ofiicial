package com.example.live.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Fondo translúcido común a todos los elementos flotantes del directo. */
private val LiveGlassFill = Color(0xFF111113).copy(alpha = 0.55f)
private val LiveGlassBorder = Color.White.copy(alpha = 0.15f)

/**
 * Panel de estado flotante del directo: indicador rojo pulsante, tiempo y
 * espectadores agrupados en una sola píldora glass.
 */
@Composable
fun LiveStatusPill(
    elapsedSeconds: Int,
    viewerCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(LiveGlassFill)
            .border(1.dp, LiveGlassBorder, CircleShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LivePulseIndicator(isLive = true)
        Text(
            text = "EN VIVO",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.6.sp,
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = formatLiveElapsed(elapsedSeconds),
            color = Color.White.copy(alpha = 0.9f),
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Espectadores",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = "$viewerCount",
                color = Color.White.copy(alpha = 0.9f),
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * Icono de herramienta en contenedor circular individual con efecto glass.
 * `activeTint` permite marcar el estado apagado (mic/cámara) en rojo.
 */
@Composable
fun LiveGlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isAlert: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (isAlert) Color(0xFFEF5350).copy(alpha = 0.85f) else LiveGlassFill)
            .border(1.dp, LiveGlassBorder, CircleShape),
        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Botón FINALIZAR: píldora roja intensa anclada abajo a la derecha.
 */
@Composable
fun LiveEndPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFFFF3B30), Color(0xFFD32F2F))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "FINALIZAR",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 0.8.sp,
        )
    }
}

internal fun formatLiveElapsed(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%d:%02d", m, s)
    }
}