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
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.automirrored.filled.StickyNote2
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
import com.example.ui.settings.ios.IosSettingsColors

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
            colors = CardDefaults.cardColors(containerColor = IosSettingsColors.cellElevated),
            shape = RoundedCornerShape(16.dp, 16.dp, 0.dp,  0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Compartir con tu pana... 🇻🇪",
                    color = IosSettingsColors.label,
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
                    AttachmentItem(icon = Icons.Rounded.PhotoCamera, label = "Cámara", color = IosSettingsColors.pink) {
                        onCamera()
                    }
                    AttachmentItem(icon = Icons.Rounded.Image, label = "Imagen", color = IosSettingsColors.blue) {
                        onImage()
                    }
                    AttachmentItem(icon = Icons.Rounded.Videocam, label = "Video", color = IosSettingsColors.purple) {
                        onVideo()
                    }
                    AttachmentItem(icon = Icons.Rounded.Description, label = "Doc", color = IosSettingsColors.green) {
                        onDocument()
                    }
                    AttachmentItem(icon = Icons.Rounded.MusicNote, label = "Audio", color = IosSettingsColors.orange) {
                        onAudio()
                    }
                    AttachmentItem(icon = Icons.AutoMirrored.Filled.QueueMusic, label = "Playlist", color = IosSettingsColors.blue) {
                        onPlaylist()
                    }
                    AttachmentItem(icon = Icons.Rounded.Gif, label = "GIF", color = IosSettingsColors.orange) {
                        onGif()
                    }
                    AttachmentItem(icon = Icons.AutoMirrored.Filled.StickyNote2, label = "Stickers", color = IosSettingsColors.pink) {
                        onSticker()
                    }
                    AttachmentItem(
                        icon = if (isGhostMode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        label = "Ghost",
                        color = if (isGhostMode) IosSettingsColors.blue else IosSettingsColors.secondaryLabel
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
            Icon(icon, contentDescription = label, tint = IosSettingsColors.label, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, color = IosSettingsColors.secondaryLabel, fontSize =  11.sp, fontWeight = FontWeight.SemiBold)
    }
}