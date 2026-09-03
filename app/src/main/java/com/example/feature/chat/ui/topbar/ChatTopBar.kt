package com.example.feature.chat.ui.topbar

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
    onNavigateToSearch: () -> Unit,
) {
    var showChatMenu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            if (isLocalSearching) {
                androidx.compose.foundation.text.BasicTextField(
                    value = localSearchQuery,
                    onValueChange =onSearchQueryChange,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF111B21), fontSize = 16.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF00A884)),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (localSearchQuery.isEmpty()) {
                                Text("Buscar en este chat...", color = Color(0xFF667781), fontSize = 16.sp)
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .testTag("chat_local_search_input")
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .bounceClick()
                        .clickable { onShowContactDetail() }
                ) {
                    com.example.ui.components.PanaAvatar(
                        avatarUrl = otherUser?.avatarUrl,
                        userId = otherUser?.id,
                        placeholderName = otherUser?.displayName ?: "",
                        size = 40.dp,
                        borderWidth = 0.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = otherUser?.displayName ?: "Cargando pana...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
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
                                tint = Color(0xFF00A884),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                            val isTyping = typingUsers.contains(otherUser?.id ?: "other_user_id_demo")
                            val otherPresence = userPresence[otherUser?.id ?: "other_user_id_demo"] ?: "en línea"
                            Text(
                                text = if (isTyping) "escribiendo..." else otherPresence,
                                fontSize = 12.sp,
                                color = if (isTyping) Color(0xFF00A884) else Color(0xFF8696A0),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontStyle = if (isTyping) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                            )

                    }
                }
            }
        },
        actions = {
            IconButton(
                onClick =onStartSearch,
                modifier = Modifier.bounceClick()
            ) {
                Icon(Icons.Default.Search, contentDescription = "Buscar en chat", tint = Color(0xFF8696A0))
            }
            IconButton(
                onClick =onVideoCall,
                modifier = Modifier.bounceClick()
            ) {
                Icon(Icons.Default.Videocam, contentDescription = "Videollamada", tint = Color(0xFF8696A0))
            }
            IconButton(
                onClick =onAudioCall,
                modifier = Modifier.bounceClick()
            ) {
                Icon(Icons.Default.Call, contentDescription = "Llamada de voz", tint = Color(0xFF8696A0))
            }

                // Chat Options Menu
                Box {
                    IconButton(onClick = { showChatMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Más opciones", tint = Color(0xFF8696A0))
                    }
                    DropdownMenu(
                        expanded = showChatMenu,
                        onDismissRequest = { showChatMenu = false },
                        modifier = Modifier.background(Color(0xFF233138))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Ver contacto", color = Color.White) },
                            onClick = {
                                showChatMenu = false
                                onShowContactDetail()
                            },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF8696A0)) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isMuted) "Activar notificaciones" else "Silenciar notificaciones", color = Color.White) },
                            onClick = {
                                showChatMenu = false
                                onToggleMute()
                            },
                            leadingIcon = {
                                Icon(
                                    if (isMuted) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                                    contentDescription = null,
                                    tint = Color(0xFF8696A0)
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isPinned) "Desanclar chat" else "Fijar chat", color = Color.White) },
                            onClick = {
                                showChatMenu = false
                                onTogglePin()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = null,
                                    tint = Color(0xFF8696A0)
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Archivos multimedia", color = Color.White) },
                            onClick = {
                                showChatMenu = false
                                onNavigateToChatMedia()
                            },
                            leadingIcon = { Icon(Icons.Default.PermMedia, contentDescription = null, tint = Color(0xFF8696A0)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Buscar", color = Color.White) },
                            onClick = {
                                showChatMenu = false
                                onNavigateToSearch()
                            },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF8696A0)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Fondo de chat", color = Color.White) },
                            onClick = {
                                showChatMenu = false
                                onShowBackgroundDialog()
                            },
                            leadingIcon = { Icon(Icons.Default.Wallpaper, contentDescription = null, tint = Color(0xFF8696A0)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Vaciar chat", color = Color.White) },
                            onClick = {
                                showChatMenu = false
                                onClearChat()
                            },
                            leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFF8696A0)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Borrar chat", color = Color(0xFFFF5252)) },
                            onClick = {
                                showChatMenu = false
                                onDeleteChat()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252)) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isBlockedUser) "Desbloquear contacto" else "Bloquear contacto", color = Color(0xFFFF5252)) },
                            onClick = {
                                showChatMenu = false
                                onToggleBlockUser()
                            },
                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFFFF5252)) }
                        )
                    }
                }
        },
        navigationIcon = {
            if (isLocalSearching) {
                IconButton(onClick =onStopSearch) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Detener búsqueda", tint = Color(0xFF54656F))
                }
            } else {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color(0xFF54656F))
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF111B21)
        )
    )
}
