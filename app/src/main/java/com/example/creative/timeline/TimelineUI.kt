package com.example.creative.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.creative.core.CreativeLayer
import com.example.ui.settings.ios.IosSettingsColors
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Timeline

/**
 * P6.5A - Professional Multi-Track Timeline UI Composable
 * CapCut / Premiere style multi-track visual timeline for Reel Studio and other editors.
 */

@Composable
fun MultiTrackTimelineUI(
    tracks: List<CreativeTrack>,
    layers: List<CreativeLayer>,
    currentTimeMs: Long,
    totalDurationMs: Long,
    selectedTrackId: String?,
    selectedLayerId: String?,
    onSeek: (Long) -> Unit,
    onSelectTrack: (String) -> Unit,
    onSelectLayer: (String) -> Unit,
    onToggleMuteTrack: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = IosSettingsColors.groupBackground),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, IosSettingsColors.blue)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header with Playhead Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Timeline, contentDescription = null, tint = IosSettingsColors.blue, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Timeline Multipista (${currentTimeMs / 1000f}s / ${totalDurationMs / 1000f}s)",
                        color = IosSettingsColors.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Snapping 🧲", color = IosSettingsColors.blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Playhead Scrubber Slider
            Slider(
                value = currentTimeMs.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..totalDurationMs.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = IosSettingsColors.blue,
                    activeTrackColor = IosSettingsColors.blue
                )
            )

            // Multi-Track List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Video & Audio Tracks
                items(tracks) { track ->
                    val isSelected = (track.id == selectedTrackId)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) IosSettingsColors.cellElevated else IosSettingsColors.cell,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectTrack(track.id) }
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onToggleMuteTrack(track.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (track.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Mute",
                                tint = if (track.isMuted) IosSettingsColors.red else IosSettingsColors.secondaryLabel,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = track.name,
                            color = IosSettingsColors.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(90.dp),
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Visual Clip Block
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp)
                                .background(
                                    when (track) {
                                        is CreativeTrack.VideoTrack -> IosSettingsColors.blue.copy(alpha = 0.8f)
                                        is CreativeTrack.AudioTrack -> IosSettingsColors.blue.copy(alpha = 0.8f)
                                        is CreativeTrack.VoiceTrack -> IosSettingsColors.green.copy(alpha = 0.8f)
                                        else -> IosSettingsColors.yellow.copy(alpha = 0.8f)
                                    },
                                    RoundedCornerShape(4.dp)
                                ),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = " Clip (${track.durationMs / 1000}s)",
                                color = IosSettingsColors.onAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Overlay Layers Track (Text / Sticker / Drawing)
                items(layers) { layer ->
                    val isSelected = (layer.id == selectedLayerId)
                    val titleName = when (layer) {
                        is CreativeLayer.Text -> "Txt: \"${layer.text}\""
                        is CreativeLayer.Sticker -> "Sticker"
                        is CreativeLayer.Drawing -> "Dibujo"
                        is CreativeLayer.Filter -> "Filtro"
                        is CreativeLayer.Audio -> "Audio"
                        is CreativeLayer.Interactive -> layer.interactiveType
                        is CreativeLayer.Group -> layer.groupName
                        is CreativeLayer.Image -> "Imagen"
                        is CreativeLayer.Video -> "Video"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) IosSettingsColors.cellElevated else IosSettingsColors.cell,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectLayer(layer.id) }
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Layers, contentDescription = null, tint = IosSettingsColors.secondaryLabel, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = titleName,
                            color = IosSettingsColors.label,
                            fontSize = 11.sp,
                            modifier = Modifier.width(90.dp),
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(20.dp)
                                .background(IosSettingsColors.separator, RoundedCornerShape(4.dp))
                        ) {
                            val startPct = (layer.startOffsetMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                            val durPct = (layer.durationMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f - startPct)

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(durPct)
                                    .background(IosSettingsColors.pink, RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}
