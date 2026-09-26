package com.example.ui.components

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.net.URI
import java.net.URLDecoder
import com.example.ui.settings.ios.IosSettingsColors
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.PlayArrow

fun extractFilename(url: String): String {
    return try {
        val path = URI(url).path
        val name = path.substringAfterLast('/')
        val decoded = URLDecoder.decode(name, "UTF-8")
        if (decoded.contains("-")) decoded.substringAfter("-") else decoded
    } catch (e: Exception) {
        "Pista de Audio"
    }
}

fun formatAudioTime(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun PlaylistAudioPlayer(audioUrls: List<String>) {
    if (audioUrls.isEmpty()) return
    
    val context = LocalContext.current
    var currentTrackIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    
    var durationMs by remember { mutableIntStateOf(0) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    
    val currentUrl = audioUrls.getOrNull(currentTrackIndex) ?: audioUrls.first()
    
    DisposableEffect(currentUrl) {
        val mp = MediaPlayer().apply {
            setDataSource(context, Uri.parse(currentUrl))
            prepareAsync()
            setOnPreparedListener { 
                durationMs = it.duration
                if (isPlaying) {
                    start()
                }
            }
            setOnCompletionListener {
                if (currentTrackIndex < audioUrls.size - 1) {
                    currentTrackIndex++
                } else {
                    isPlaying = false
                    currentPositionMs = 0
                }
            }
        }
        mediaPlayer = mp
        
        onDispose {
            try {
                mp.stop()
                mp.release()
            } catch (e: Exception) {}
            mediaPlayer = null
            durationMs = 0
            currentPositionMs = 0
        }
    }
    
    LaunchedEffect(isPlaying, mediaPlayer) {
        while (isActive && isPlaying && mediaPlayer != null) {
            try {
                currentPositionMs = mediaPlayer?.currentPosition ?: 0
            } catch (e: Exception) {}
            delay(100)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(IosSettingsColors.cellElevated)
            .border(1.dp, IosSettingsColors.separator, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // Player Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Art or Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(IosSettingsColors.blue, IosSettingsColors.indigo)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = IosSettingsColors.label,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Track Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = extractFilename(currentUrl),
                    color = IosSettingsColors.label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (audioUrls.size > 1) "Pista ${currentTrackIndex + 1} de ${audioUrls.size}" else "Audio",
                    color = IosSettingsColors.secondaryLabel,
                    fontSize = 12.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Progress Bar
        val progress = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f
        
        // Custom Waveform/Progress animation (Spotify-like line)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(IosSettingsColors.separator)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(IosSettingsColors.green) // Spotify Green
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Time Info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatAudioTime(currentPositionMs),
                color = IosSettingsColors.secondaryLabel,
                fontSize = 11.sp
            )
            Text(
                text = formatAudioTime(durationMs),
                color = IosSettingsColors.secondaryLabel,
                fontSize = 11.sp
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (currentTrackIndex > 0) {
                        currentTrackIndex--
                        isPlaying = true
                    }
                },
                enabled = currentTrackIndex > 0
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Anterior",
                    tint = if (currentTrackIndex > 0) IosSettingsColors.label else IosSettingsColors.secondaryLabel,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(IosSettingsColors.onAccent)
                    .clickable {
                        if (isPlaying) {
                            mediaPlayer?.pause()
                            isPlaying = false
                        } else {
                            mediaPlayer?.start()
                            isPlaying = true
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = IosSettingsColors.onAccent,
                    modifier = Modifier.size(36.dp)
                )
            }
            
            IconButton(
                onClick = {
                    if (currentTrackIndex < audioUrls.size - 1) {
                        currentTrackIndex++
                        isPlaying = true
                    }
                },
                enabled = currentTrackIndex < audioUrls.size - 1
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Siguiente",
                    tint = if (currentTrackIndex < audioUrls.size - 1) IosSettingsColors.label else IosSettingsColors.secondaryLabel,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        // Playlist (if multiple)
        if (audioUrls.size > 1) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = IosSettingsColors.label.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(8.dp))
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
            ) {
                audioUrls.forEachIndexed { index, url ->
                    val isCurrent = index == currentTrackIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentTrackIndex = index
                                isPlaying = true
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = if (isCurrent) IosSettingsColors.green else IosSettingsColors.secondaryLabel,
                            fontSize = 12.sp,
                            modifier = Modifier.width(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = extractFilename(url),
                            color = if (isCurrent) IosSettingsColors.green else IosSettingsColors.label,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (isCurrent && isPlaying) {
                            // Small animation indicator
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = IosSettingsColors.green,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
