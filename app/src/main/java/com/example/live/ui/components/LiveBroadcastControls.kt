package com.example.live.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.live.ui.LiveEndRed

/**
 * Controles flotantes del anfitrión durante el directo.
 *
 * Sin contenedor de fondo: cada herramienta es un círculo de cristal independiente
 * sobre el video, y "FINALIZAR" va anclado a la derecha con halo rojo.
 */
@Composable
fun LiveBroadcastControls(
    isMicMuted: Boolean,
    isCameraOff: Boolean,
    commentsVisible: Boolean,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleComments: () -> Unit,
    onEndLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LiveGlassCircleButton(
            icon = if (isMicMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
            contentDescription = if (isMicMuted) "Activar micrófono" else "Silenciar micrófono",
            onClick = onToggleMic,
            alert = isMicMuted,
            statusDot = !isMicMuted,
        )

        LiveGlassCircleButton(
            icon = if (isCameraOff) Icons.Rounded.VideocamOff else Icons.Rounded.Videocam,
            contentDescription = if (isCameraOff) "Activar cámara" else "Apagar cámara",
            onClick = onToggleCamera,
            alert = isCameraOff,
            statusDot = !isCameraOff,
        )

        LiveGlassCircleButton(
            icon = Icons.Rounded.Cameraswitch,
            contentDescription = "Cambiar cámara",
            onClick = onSwitchCamera,
        )

        LiveGlassCircleButton(
            icon = Icons.AutoMirrored.Rounded.Chat,
            contentDescription = if (commentsVisible) "Ocultar comentarios" else "Mostrar comentarios",
            onClick = onToggleComments,
        )

        Spacer(modifier = Modifier.weight(1f))

        EndLiveButton(onClick = onEndLive)
    }
}

/** Píldora roja de "FINALIZAR" con halo, borde claro y destello decorativo. */
@Composable
private fun EndLiveButton(onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        // Halo difuminado del MISMO tamaño que la píldora. `matchParentSize` no
        // influye en el tamaño del Box (lo fija el botón), así que en API < 31 —
        // donde Modifier.blur es no-op — el halo queda oculto detrás del botón en
        // vez de asomar como un borde rojo duro.
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(20.dp, BlurredEdgeTreatment.Unbounded)
                .background(LiveEndRed.copy(alpha = 0.60f), CircleShape)
        )

        Box(
            modifier = Modifier
                .height(44.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    ambientColor = LiveEndRed,
                    spotColor = LiveEndRed,
                    clip = false,
                )
                .clip(CircleShape)
                .background(LiveEndRed.copy(alpha = 0.88f))
                .border(1.dp, Color(0xFFFF8A80), CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "FINALIZAR",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        Sparkle(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = 12.dp, y = 12.dp)
                .size(26.dp)
        )
    }
}

/** Destello de cuatro puntas que remata la píldora, como en el diseño. */
@Composable
private fun Sparkle(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w / 2f, 0f)
            quadraticTo(w * 0.56f, h * 0.44f, w, h / 2f)
            quadraticTo(w * 0.56f, h * 0.56f, w / 2f, h)
            quadraticTo(w * 0.44f, h * 0.56f, 0f, h / 2f)
            quadraticTo(w * 0.44f, h * 0.44f, w / 2f, 0f)
            close()
        }
        drawPath(path = path, color = Color.White.copy(alpha = 0.30f))
    }
}
