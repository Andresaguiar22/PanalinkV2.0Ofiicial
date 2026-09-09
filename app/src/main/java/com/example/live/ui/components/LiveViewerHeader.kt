package com.example.live.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.data.supabase.SupabaseClient
import com.example.live.domain.model.LiveStream
import com.example.ui.components.PanaAvatar

@Composable
fun LiveViewerHeader(
    liveStream: LiveStream?,
    viewerCount: Int,
    elapsedSeconds: Int,
    onClose: () -> Unit,
    onFollowClick: (() -> Unit)? = null,
    onShareClick: (() -> Unit)? = null,
    onReportClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val currentUid = SupabaseClient.currentUser?.id ?: "anon"
    val isFollowing by remember(liveStream?.hostId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 36.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(36.dp)) {
                if (!liveStream?.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = com.example.data.repository.CdnManager.resolveMediaUrlSync(liveStream!!.thumbnailUrl!!),
                        contentDescription = "Miniatura del live",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Gray.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 10.dp)) {
                Text(
                    text = liveStream?.title ?: "Streamer",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "👁 ${viewerCount} · ${formatElapsed(elapsedSeconds)}",
                    color = Color.LightGray,
                    fontSize = 10.sp
                )
            }

            if (liveStream?.hostId != currentUid) {
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = {
                        onFollowClick?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (isFollowing) "Siguiendo" else "+ Seguir",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Más opciones",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(Color(0xFF1F2C34))
            ) {
                DropdownMenuItem(
                    text = { Text("Compartir", color = Color.White, fontSize = 14.sp) },
                    onClick = {
                        showMenu = false
                        onShareClick?.invoke()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Reportar", color = Color(0xFFEF5350), fontSize = 14.sp) },
                    onClick = {
                        showMenu = false
                        onReportClick?.invoke()
                    }
                )
            }
        }

        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cerrar",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
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
