package com.example.live.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.live.ui.formatLiveCount
import com.example.ui.components.PanaAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveStudioSheet(
    hostId: String?,
    hostName: String,
    viewerCount: Int,
    likeCount: Int,
    giftCoins: Long,
    presentUsers: List<String>,
    myUserId: String?,
    isBroadcaster: Boolean,
    onDismiss: () -> Unit,
    onStartOwnLive: () -> Unit,
    onRequestCoHost: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1B1B1F),
        contentColor = Color.White
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("Panalink Studio", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                PanaAvatar(
                    userId = hostId,
                    size = 48.dp,
                    borderWidth = 2.dp,
                    borderColor = Color.White,
                    placeholderName = hostName
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(hostName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (isBroadcaster) "Estás transmitiendo" else "Anfitrión del directo",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StudioMetric("Espectadores", formatLiveCount(viewerCount), Color(0xFF6FD3FF))
                StudioMetric("Me gusta", formatLiveCount(likeCount), Color(0xFFFF7BAC))
                StudioMetric("Monedas", giftCoins.toString(), Color(0xFFFFD54F))
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "En el directo ahora: ${presentUsers.size}",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (presentUsers.isEmpty()) {
                Text(
                    text = "Todavía no hay otros espectadores.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(presentUsers, key = { it }) { userId ->
                        val identity = rememberLiveIdentity(userId)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PanaAvatar(
                                avatarUrl = identity?.avatarUrl,
                                userId = userId,
                                size = 44.dp,
                                borderWidth = 1.5.dp,
                                borderColor = Color(0xFF2EA8FF),
                                placeholderName = identity.displayNameOr(userId)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (userId == myUserId) "Tú" else identity.displayNameOr(userId),
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onStartOwnLive,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A884)),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Transmitir mi propio Live", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onRequestCoHost,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Solicitar ser co-host", color = Color.White)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StudioMetric(label: String, value: String, accent: Color) {
    Column(
        modifier = Modifier
            .background(Color(0xFF26262E), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
    }
}
