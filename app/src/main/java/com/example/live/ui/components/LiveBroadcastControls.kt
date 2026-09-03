package com.example.live.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LiveBroadcastControls(
    isMicMuted: Boolean,
    isCameraOff: Boolean,
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

        Button(
            onClick = onEndLive,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("FINALIZAR", color = Color.White)
        }
    }
}
