package com.example.feature.chat.ui.topbar

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Profile
import com.example.ui.theme.bounceClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    otherUser: Profile?,
    isMuted: Boolean,
    isPinned: Boolean,
    isLocalSearching: Boolean,
    localSearchQuery: String,
    typingUsers: List<String>,
    userPresence: Map<String, String>,
    isBlockedUser: Boolean,
    onBack: () -> Unit,
    onVideoCall: () -> Unit,
    onAudioCall: () -> Unit,
    onStartSearch: () -> Unit,
    onStopSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onShowContactDetail: () -> Unit,
    onShowBackgroundDialog: () -> Unit,
    onToggleMute: () -> Unit,
    onTogglePin: () -> Unit,
    onClearChat: () -> Unit,
    onDeleteChat: () -> Unit,
    onToggleBlockUser: () -> Unit,
    onNavigateToChatMedia: () -> Unit,
    onNavigateToSearch: () -> Unit
) {
    val density = LocalDensity.current
    var showChatMenu by remember { mutableStateOf(false) }

    // Determinar si el contacto está online basado en la presencia real
    val otherPresenceText = userPresence[otherUser?.id ?: ""] ?: ""
    val isOnlineReal = otherPresenceText.equals("en línea", ignoreCase = true) ||
        otherPresenceText.contains("online", ignoreCase = true) ||
        otherPresenceText.contains("conectado", ignoreCase = true) ||
        otherPresenceText.contains("En línea", ignoreCase = true)

    val isTyping = typingUsers.contains(otherUser?.id ?: "other_user_id_demo")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .blur(16.dp)
            .padding(top = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(56.dp)
                    .clickable(enabled = !isLocalSearching && otherUser != null) {
                        onShowContactDetail()
                    }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLocalSearching) {
                    IconButton(onClick = onStopSearch) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Detener búsqueda",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = localSearchQuery,
                        onValueChange = onSearchQueryChange,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Color(0xFF38BDF8)),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("chat_local_search_input"),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (localSearchQuery.isEmpty()) {
                                    Text(
                                        "Buscar en este chat...",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 16.sp
                                    )
                                }
                                innerTextField()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_local_search_input")
                    )
                } else {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Avatar + nombre + estado
                    com.example.ui.components.PanaAvatar(
                        avatarUrl = otherUser?.avatarUrl,
                        userId = otherUser?.id,
                        placeholderName = otherUser?.displayName ?: "",
                        size = 36.dp,
                        borderWidth = 0.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = otherUser?.displayName ?: "Cargando pana...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .basicMarquee()
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Cifrado",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(12.dp)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isOnlineReal && !isTyping) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF00E5FF), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "En línea",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else if (isTyping) {
                                Text(
                                    text = "escribiendo...",
                                    fontSize = 11.sp,
                                    color = Color(0xFF38BDF8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontStyle = FontStyle.Italic
                                )
                            } else {
                                Text(
                                    text = otherPresenceText.ifEmpty { "Sin conexión" },
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Action buttons
                if (isLocalSearching) {
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    IconButton(onClick = onStartSearch) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Buscar en chat",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = onVideoCall) {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = "Videollamada",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = onAudioCall) {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = "Llamada de voz",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Box {
                        IconButton(onClick = { showChatMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Más opciones",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showChatMenu,
                            onDismissRequest = { showChatMenu = false },
                            modifier = Modifier.background(Color(0xFF1E293B))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Ver contacto", color = Color.White) },
                                onClick = {
                                    showChatMenu = false
                                    onShowContactDetail()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isMuted) "Activar notificaciones" else "Silenciar notificaciones",
                                        color = Color.White
                                    )
                                },
                                onClick = {
                                    showChatMenu = false
                                    onToggleMute()
                                },
                                leadingIcon = {
                                    Icon(
                                        if (isMuted) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isPinned) "Desanclar chat" else "Fijar chat",
                                        color = Color.White
                                    )
                                },
                                onClick = {
                                    showChatMenu = false
                                    onTogglePin()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.PushPin,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Archivos multimedia", color = Color.White) },
                                onClick = {
                                    showChatMenu = false
                                    onNavigateToChatMedia()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.PermMedia,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Buscar", color = Color.White) },
                                onClick = {
                                    showChatMenu = false
                                    onNavigateToSearch()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Fondo de chat", color = Color.White) },
                                onClick = {
                                    showChatMenu = false
                                    onShowBackgroundDialog()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Wallpaper, contentDescription = null, tint = Color(0xFF94A3B8))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Vaciar chat", color = Color.White) },
                                onClick = {
                                    showChatMenu = false
                                    onClearChat()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFF94A3B8))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Borrar chat", color = Color(0xFFFF5252)) },
                                onClick = {
                                    showChatMenu = false
                                    onDeleteChat()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252))
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isBlockedUser) "Desbloquear contacto" else "Bloquear contacto",
                                        color = Color(0xFFFF5252)
                                    )
                                },
                                onClick = {
                                    showChatMenu = false
                                    onToggleBlockUser()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFFFF5252))
                                }
                            )
                        }
                    }
                }
            }

            // Glassmorphism divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.06f))
            )
        }
    }
}
