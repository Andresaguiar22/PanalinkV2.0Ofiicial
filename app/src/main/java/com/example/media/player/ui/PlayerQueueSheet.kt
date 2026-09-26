package com.example.media.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.media.audio.AudioTrackEntity
import com.example.ui.settings.ios.IosSettingsColors

/**
 * P6.7.3 - Player Queue Sheet
 * Modal bottom sheet for viewing and managing the playback queue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerQueueSheet(
    queue: List<AudioTrackEntity>,
    currentIndex: Int,
    onTrackClick: (Int) -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onReorderTrack: (Int, Int) -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight(0.75f)
            .background(IosSettingsColors.groupBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Cola de Reproducción",
                color = IosSettingsColors.label,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onClearQueue) {
                Text("Limpiar", color = IosSettingsColors.blue)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            itemsIndexed(queue) { index, track ->
                val isCurrent = index == currentIndex
                QueueItem(
                    track = track,
                    isCurrent = isCurrent,
                    onClick = { onTrackClick(index) },
                    onRemove = { onRemoveTrack(index) }
                )
            }
        }
    }
}

@Composable
fun QueueItem(
    track: AudioTrackEntity,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(IosSettingsColors.cellElevated),
            contentAlignment = Alignment.Center
        ) {
            if (!track.coverPath.isNullOrEmpty()) {
                AsyncImage(model = track.coverPath, contentDescription = null, contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = IosSettingsColors.secondaryLabel)
            }
            if (isCurrent) {
                Box(modifier = Modifier.fillMaxSize().background(IosSettingsColors.mediaScrimSoft), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = IosSettingsColors.blue, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                color = if (isCurrent) IosSettingsColors.blue else IosSettingsColors.label,
                fontSize = 15.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                track.artist,
                color = IosSettingsColors.secondaryLabel,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = "Eliminar", tint = IosSettingsColors.secondaryLabel, modifier = Modifier.size(20.dp))
        }

        Icon(Icons.Default.DragHandle, contentDescription = "Mover", tint = IosSettingsColors.secondaryLabel)
    }
}
