package com.example.feature.chat.ui.topbar

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Profile

/**
 * Accion 4: Barra superior flotante de cristal (glassmorphism premium), calcada de
 * la referencia 1000420775: tarjeta redondeada translucida separada de los bordes,
 * avatar centrado con anillo cian + badge de presencia, nombre debajo del avatar y
 * acciones (videollamada, llamada, menu) a la derecha. El estado de presencia va a
 * la izquierda junto al boton de atras.
 */
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
    onShowBubblePaletteDialog: () -> Unit,
    onToggleMute: () -> Unit,
    onTogglePin: () -> Unit,
    onClearChat: () -> Unit,
    onDeleteChat: () -> Unit,
    onToggleBlockUser: () -> Unit,
    onNavigateToChatMedia: () -> Unit,
    onNavigateToSearch: () -> Unit
) {
    var showChatMenu by remember { mutableStateOf(false) }

    // Determinar si el contacto esta online basado en la presencia real
    val otherPresenceText = userPresence[otherUser?.id ?: ""] ?: ""
    val isOnlineReal = otherPresenceText.equals("en línea", ignoreCase = true) ||
        otherPresenceText.contains("online", ignoreCase = true) ||
        otherPresenceText.contains("conectado", ignoreCase = true) ||
        otherPresenceText.contains("En línea", ignoreCase = true)

    val isTyping = typingUsers.contains(otherUser?.id ?: "other_user_id_demo")

    // Paleta de cristal de la barra superior
    val glassTop = Color(0xFF41506A).copy(alpha = 0.90f)
    val glassBottom = Color(0xFF2A3546).copy(alpha = 0.90f)
    val glassBorder = Color.White.copy(alpha = 0.12f)
    val iconTint = Color(0xFFCBD5E1)
    val accentCyan = Color(0xFF38BDF8)

    Box(modifier = Modifier.fillMaxWidth()) {
        if (isLocalSearching) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    cursorBrush = SolidColor(accentCyan),
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
            }
        } else {
            // Tarjeta flotante de cristal, separada de los bordes y aislada del resto
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 5.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(Brush.verticalGradient(listOf(glassTop, glassBottom)))
                        .border(1.dp, glassBorder, RoundedCornerShape(28.dp))
                        .clickable(enabled = otherUser != null) { onShowContactDetail() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Zona izquierda: atras + estado de presencia
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Atrás",
                                    tint = iconTint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            PresenceLabel(
                                isOnlineReal = isOnlineReal,
                                isTyping = isTyping,
                                presenceText = otherPresenceText
                            )
                        }

                        // Zona central: avatar con anillo cian y badge de presencia
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, accentCyan, CircleShape)
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                com.example.ui.components.PanaAvatar(
                                    avatarUrl = otherUser?.avatarUrl,
                                    userId = otherUser?.id,
                                    placeholderName = otherUser?.displayName ?: "",
                                    size = 40.dp,
                                    borderWidth = 0.dp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(11.dp)
                                    .clip(CircleShape)
                                    .background(if (isOnlineReal) Color(0xFF4ADE80) else Color(0xFF94A3B8))
                                    .border(2.dp, Color(0xFF3A4759), CircleShape)
                            )
                        }

                        // Zona derecha: acciones
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TopBarAction(Icons.Default.Videocam, "Videollamada", onVideoCall, iconTint)
                            TopBarAction(Icons.Default.Call, "Llamada de voz", onAudioCall, iconTint)
                            Box {
                                IconButton(onClick = { showChatMenu = true }, modifier = Modifier.size(34.dp)) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "Más opciones",
                                        tint = iconTint,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showChatMenu,
                                    onDismissRequest = { showChatMenu = false },
                                    modifier = Modifier.background(
                                        Brush.verticalGradient(listOf(glassTop, glassBottom))
                                    )
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
                                        text = { Text("Buscar en este chat", color = Color.White) },
                                        onClick = {
                                            showChatMenu = false
                                            onStartSearch()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Search,
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
                                        text = { Text("Color de burbujas", color = Color.White) },
                                        onClick = {
                                            showChatMenu = false
                                            onShowBubblePaletteDialog()
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Palette, contentDescription = null, tint = Color(0xFF94A3B8))
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

                    Spacer(modifier = Modifier.height(6.dp))

                    // Nombre centrado debajo del avatar (como en la referencia)
                    Text(
                        text = otherUser?.displayName ?: "Cargando pana...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee()
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBarAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color
) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun PresenceLabel(
    isOnlineReal: Boolean,
    isTyping: Boolean,
    presenceText: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isTyping) {
            Text(
                text = "escribiendo...",
                fontSize = 12.sp,
                color = Color(0xFF38BDF8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontStyle = FontStyle.Italic
            )
        } else if (isOnlineReal) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color(0xFF4ADE80), CircleShape)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "En línea",
                fontSize = 12.sp,
                color = Color(0xFFCBD5E1),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Text(
                text = presenceText.ifEmpty { "Fuera de línea" },
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
