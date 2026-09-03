package com.example.live.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LiveViewerControls(
    onCloseClick: () -> Unit,
    onMuteClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onMuteClick,
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Audio",
                tint = Color.White
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onCloseClick,
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFFEF5350))
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cerrar",
                tint = Color.White
            )
        }
    }
}

@Composable
fun LiveViewerBottomBar(
    onOpenInput: () -> Unit,
    onGift: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Campo "Escribe algo..."
        Surface(
            onClick = onOpenInput,
            modifier = Modifier
                .weight(1f)
                .height(40.dp),
            color = Color.Black.copy(alpha = 0.4f),
            shape = androidx.compose.foundation.shape.CircleShape
        ) {
            Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Escribe algo...", color = Color.LightGray)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(start = 12.dp)
        ) {
            IconButton(onClick = {}) { 
                Icon(Icons.Default.People, contentDescription = null, tint = Color.White) 
            }
            IconButton(onClick = onGift) { 
                Icon(Icons.Default.LocalFlorist, contentDescription = null, tint = Color(0xFFFE2C55)) 
            }
            IconButton(onClick = {}) { 
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Color(0xFFFE2C55))
                    Text("20", color = Color.White, fontSize = 8.sp)
                } 
            }
        }
    }
}

