package com.example.live.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LiveBroadcastControls(
    isMicMuted: Boolean,
    isCameraOff: Boolean,
    elapsedSeconds: Int,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndLive: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onToggleMic,
            colors = IconButtonDefaults.iconButtonColors(containerColor = if (isMicMuted) Color(0xFFEF5350) else Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = "Micrófono",
                tint = Color.White
            )
        }

        IconButton(
            onClick = onToggleCamera,
            colors = IconButtonDefaults.iconButtonColors(containerColor = if (isCameraOff) Color(0xFFEF5350) else Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = if (isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                contentDescription = "Cámara",
                tint = Color.White
            )
        }

        IconButton(
            onClick = onSwitchCamera,
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Cambiar cámara",
                tint = Color.White
            )
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = formatElapsed(elapsedSeconds),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Button(
            onClick = onEndLive,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text("FINALIZAR", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatElapsed(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%d:%02d", m, s)
    }
}
