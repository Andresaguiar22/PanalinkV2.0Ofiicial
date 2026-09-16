package com.example.live.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.identity.bridge.LegacyIdentityBridge
import com.example.identity.memory.IdentityMemoryCache
import com.example.identity.model.toIdentityUiState
import com.example.live.domain.model.LiveStream
import com.example.live.ui.formatLiveCount
import com.example.ui.components.PanaAvatar

@Composable
fun LiveViewerHeader(
    liveStream: LiveStream?,
    viewerCount: Int,
    likeCount: Int,
    elapsedSeconds: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hostId = liveStream?.hostId
    val context = LocalContext.current
    val bridge = remember(context) { LegacyIdentityBridge(context) }
    val cached = remember(hostId) { hostId?.let { IdentityMemoryCache.profiles[it] } }

    val identity by produceState(
        initialValue = cached?.toIdentityUiState(),
        key1 = hostId
    ) {
        if (hostId.isNullOrEmpty()) {
            value = null
        } else {
            bridge.identityRepository.observeIdentity(hostId).collect { value = it }
        }
    }

    val displayName = identity?.displayName?.takeIf { it.isNotBlank() }
        ?: liveStream?.title?.takeIf { it.isNotBlank() }
        ?: "Streamer"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .border(0.5.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(26.dp))
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PanaAvatar(
                userId = hostId,
                size = 46.dp,
                borderWidth = 2.dp,
                borderColor = Color.White,
                contentDescription = "Avatar de $displayName",
                placeholderName = displayName
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    LiveBadge()
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatChip(label = "Espectadores:", value = formatLiveCount(viewerCount))
                    StatChip(label = "Me gusta:", value = formatLiveCount(likeCount))
                }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        Surface(
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.35f),
            modifier = Modifier.padding(top = 2.dp)
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun LiveBadge() {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = Color.White.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f))
    ) {
        Text(
            text = "LIVE",
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.22f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = value,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
