package com.example.live.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.live.domain.model.LiveStream

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.IconButton

@Composable
fun LiveViewerHeader(
    liveStream: LiveStream?,
    viewerCount: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 36.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Píldora del Streamer
        Row(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Placeholder for Avatar
            Box(modifier = Modifier.size(32.dp).background(Color.Gray, CircleShape))
            
            Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(text = liveStream?.title ?: "Streamer", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(text = "332.0K", color = Color.LightGray, fontSize = 10.sp)
            }
            
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55)),
                modifier = Modifier.padding(start = 4.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text("+ Seguir", color = Color.White, fontSize = 10.sp)
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Right side
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.4f),
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Text(text = "$viewerCount", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}
