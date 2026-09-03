package com.example.ui.components.chat.bubble

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.media.playlist.PlaylistSharePayload

/**
 * Premium shared-playlist card for the chat (Spotify-style):
 * full-width cover with a play overlay, title, stats and a save action.
 */
@Composable
fun PlaylistChatBubble(
    payload: PlaylistSharePayload,
    isMe: Boolean,
    formattedTime: String,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bubbleColor = if (isMe) Color(0xFF005C4B) else Color(0xFF1F2C34)
    val accent = Color(0xFF38BDF8)

    Surface(
        color = bubbleColor,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp,
        modifier = modifier
            .widthIn(max = 264.dp)
            .clickable(onClick = onOpen)
    ) {
        Column {
            // Cover art with play overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            ) {
                if (!payload.coverPath.isNullOrEmpty()) {
                    AsyncImage(
                        model = payload.coverPath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }

                // Bottom scrim for legibility
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                            )
                        )
                )

                // "PLAYLIST" tag
                Text(
                    text = "PLAYLIST",
                    color = Color.White,
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )

                // Play overlay button
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Reproducir",
                        tint = Color(0xFF0B0F19),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = payload.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${payload.trackCount} canciones • ${formatTotalDuration(payload.durationMs)}",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    maxLines = 1
                )

                Text(
                    text = "Compartida por ${payload.sharedBy}",
                    color = accent.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPlay,
                        modifier = Modifier.weight(1f).height(34.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = Color(0xFF0B0F19)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shape = RoundedCornerShape(17.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reproducir", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onSave,
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        shape = RoundedCornerShape(17.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.4f)))
                        )
                    ) {
                        Icon(Icons.Default.PlaylistAdd, contentDescription = "Guardar", modifier = Modifier.size(16.dp))
                    }
                }

                Text(
                    text = formattedTime,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                )
            }
        }
    }
}

private fun formatTotalDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    return if (minutes >= 60) {
        "${minutes / 60} h ${minutes % 60} min"
    } else {
        "$minutes min"
    }
}
