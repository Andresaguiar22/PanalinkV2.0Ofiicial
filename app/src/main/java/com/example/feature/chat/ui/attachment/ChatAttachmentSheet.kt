package com.example.feature.chat.ui.attachment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatAttachmentSheet(
        visible: Boolean,
    isGhostMode: Boolean,
    onCamera: () -> Unit,
    onImage: () -> Unit,
    onVideo: () -> Unit,
    onDocument: () -> Unit,
    onAudio: () -> Unit,
    onPlaylist: () -> Unit,
    onGif: () -> Unit = {},
    onSticker: () -> Unit = {},
    onToggleGhostMode: () -> Unit
) {
    // Smooth collapsing files attachments drawer
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF202C33)),
            shape = RoundedCornerShape(16.dp, 16.dp, 0.dp,  0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Compartir con tu pana... 🇻🇪",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize =  14.sp,
                    modifier = Modifier.padding(bottom =  12.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AttachmentItem(icon = Icons.Default.PhotoCamera, label = "Cámara", color = Color(0xFFFF2D55)) {
                        onCamera()
                    }
                    AttachmentItem(icon = Icons.Default.Image, label = "Imagen", color = Color(0xFF007AFF)) {
                        onImage()
                    }
                    AttachmentItem(icon = Icons.Default.Videocam, label = "Video", color = Color(0xFF5856D6)) {
                        onVideo()
                    }
                    AttachmentItem(icon = Icons.Default.Description, label = "Doc", color = Color(0xFF4CD964)) {
                        onDocument()
                    }
                    AttachmentItem(icon = Icons.Default.MusicNote, label = "Audio", color = Color(0xFFFF9500)) {
                        onAudio()
                    }
                    AttachmentItem(icon = Icons.Default.QueueMusic, label = "Playlist", color = Color(0xFF38BDF8)) {
                        onPlaylist()
                    }
                    AttachmentItem(icon = Icons.Default.Gif, label = "GIF", color = Color(0xFFFF9E00)) {
                        onGif()
                    }
                    AttachmentItem(icon = Icons.Default.StickyNote2, label = "Stickers", color = Color(0xFFFF2D55)) {
                        onSticker()
                    }
                    AttachmentItem(
                        icon = if (isGhostMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        label = "Ghost",
                        color = if (isGhostMode) Color(0xFFBB86FC) else Color(0xFF8596A0)
                    ) {
                        onToggleGhostMode()
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentItem(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, color = Color(0xFF54656F), fontSize =  11.sp, fontWeight = FontWeight.SemiBold)
    }
}