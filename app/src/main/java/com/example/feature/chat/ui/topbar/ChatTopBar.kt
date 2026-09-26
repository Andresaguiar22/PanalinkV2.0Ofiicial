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
import com.example.ui.settings.ios.IosSettingsColors
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search

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

    // Paleta iOS para la barra superior (negro puro + azul iOS)
    val glassTop = IosSettingsColors.groupBackground
    val glassBottom = IosSettingsColors.groupBackground
    val glassBorder = IosSettingsColors.separator.copy(alpha = 0.5f)
    val iconTint = IosSettingsColors.blue
    val accentCyan = IosSettingsColors.blue

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
                        tint = IosSettingsColors.secondaryLabel,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = localSearchQuery,
                    onValueChange = onSearchQueryChange,
                    textStyle = TextStyle(color = IosSettingsColors.label, fontSize = 16.sp),
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
                                    color = IosSettingsColors.secondaryLabel,
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
            // iOS inline Top Bar (iMessage): franja negra pura, sin burbuja glass
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    IosSettingsColors.groupBackground.copy(alpha = 0.9f),
                                    IosSettingsColors.cell.copy(alpha = 0.8f),
                                    IosSettingsColors.groupBackground.copy(alpha = 0.9f)
                                )
                            )
                        )
                        .border(width = 0.5.dp, color = IosSettingsColors.separator)
                        .clickable(enabled = otherUser != null) { onShowContactDetail() }
                        .padding(horizontal = 8.dp, vertical =   6.dp),
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
                                    .offset(x = 1.dp, y = 1.dp)
                                    .size(13.dp)
                                    .clip(CircleShape)
                                    .background(if (isOnlineReal) IosSettingsColors.green else IosSettingsColors.secondaryLabel)
                                    .border(2.dp, IosSettingsColors.cell, CircleShape)
                            )
                        }

                        // Zona derecha: acciones
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TopBarAction(Icons.Rounded.Videocam, "Videollamada", onVideoCall, iconTint)
                            TopBarAction(Icons.Rounded.Call, "Llamada de voz", onAudioCall, iconTint)
                            Box {
                                IconButton(onClick = { showChatMenu = true }, modifier = Modifier.size(34.dp)) {
                                    Icon(
                                        Icons.Rounded.MoreVert,
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
                                        text = { Text("Ver contacto", color = IosSettingsColors.label) },
                                        onClick = {
                                            showChatMenu = false
                                            onShowContactDetail()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.Person,
                                                contentDescription = null,
                                                tint = IosSettingsColors.secondaryLabel
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Buscar en este chat", color = IosSettingsColors.label) },
                                        onClick = {
                                            showChatMenu = false
                                            onStartSearch()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.Search,
                                                contentDescription = null,
                                                tint = IosSettingsColors.secondaryLabel
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (isMuted) "Activar notificaciones" else "Silenciar notificaciones",
                                                color = IosSettingsColors.label
                                            )
                                        },
                                        onClick = {
                                            showChatMenu = false
                                            onToggleMute()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                if (isMuted) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff,
                                                contentDescription = null,
                                                tint = IosSettingsColors.secondaryLabel
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (isPinned) "Desanclar chat" else "Fijar chat",
                                                color = IosSettingsColors.label
                                            )
                                        },
                                        onClick = {
                                            showChatMenu = false
                                            onTogglePin()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.PushPin,
                                                contentDescription = null,
                                                tint = IosSettingsColors.secondaryLabel
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Archivos multimedia", color = IosSettingsColors.label) },
                                        onClick = {
                                            showChatMenu = false
                                            onNavigateToChatMedia()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.PermMedia,
                                                contentDescription = null,
                                                tint = IosSettingsColors.secondaryLabel
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Buscar", color = IosSettingsColors.label) },
                                        onClick = {
                                            showChatMenu = false
                                            onNavigateToSearch()
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Search, contentDescription = null, tint = IosSettingsColors.secondaryLabel)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Fondo de chat", color = IosSettingsColors.label) },
                                        onClick = {
                                            showChatMenu = false
                                            onShowBackgroundDialog()
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Wallpaper, contentDescription = null, tint = IosSettingsColors.secondaryLabel)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Color de burbujas", color = IosSettingsColors.label) },
                                        onClick = {
                                            showChatMenu = false
                                            onShowBubblePaletteDialog()
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Palette, contentDescription = null, tint = IosSettingsColors.secondaryLabel)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Vaciar chat", color = IosSettingsColors.label) },
                                        onClick = {
                                            showChatMenu = false
                                            onClearChat()
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = IosSettingsColors.secondaryLabel)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Borrar chat", color = IosSettingsColors.red) },
                                        onClick = {
                                            showChatMenu = false
                                            onDeleteChat()
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Delete, contentDescription = null, tint = IosSettingsColors.red)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (isBlockedUser) "Desbloquear contacto" else "Bloquear contacto",
                                                color = IosSettingsColors.red
                                            )
                                        },
                                        onClick = {
                                            showChatMenu = false
                                            onToggleBlockUser()
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Block, contentDescription = null, tint = IosSettingsColors.red)
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
                        color = IosSettingsColors.label,
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
                color = IosSettingsColors.blue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontStyle = FontStyle.Italic
            )
        } else if (isOnlineReal) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(IosSettingsColors.green, CircleShape)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "En línea",
                fontSize = 12.sp,
                color = IosSettingsColors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Text(
                text = presenceText.ifEmpty { "Fuera de línea" },
                fontSize = 12.sp,
                color = IosSettingsColors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
