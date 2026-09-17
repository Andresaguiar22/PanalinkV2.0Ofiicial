@file:Suppress("UnusedMaterialScaffoldPaddingParameter")

package com.example.rooms.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.rooms.model.VoiceRoom
import com.example.rooms.model.VoiceRoomMember
import com.example.rooms.model.VoiceRoomMessage
import com.example.rooms.model.VoiceRoomSeat
import com.example.features.stickers.domain.Sticker
import com.example.features.stickers.presentation.StickerPanel
import kotlinx.coroutines.launch

// === Helpers ===

private fun levelFromRole(role: String): Int = when (role) {
    "owner" -> 6
    "admin" -> 5
    "speaker" -> 3
    else -> 1
}

private fun VoiceRoomSeat?.isMuteBadgeVisibleInternal(): Boolean =
    this != null && this.isOccupied && this.isMuted

/**
 * Parsea el contenido de un mensaje en busca del formato [sticker:RUTA].
 * Si coincide, devuelve la ruta extraída; si no, devuelve null.
 */
private fun parseStickerContent(content: String): String? {
    if (content.length < 9) return null
    if (!content.startsWith("[sticker:") || !content.endsWith("]")) return null
    val path = content.substring(9, content.length - 1)
    return if (path.isNotEmpty()) path else null
}

// === MAIN SCREEN ===

@Composable
fun VoiceRoomRedesignedScreen(
    roomId: String,
    onBack: () -> Unit,
    viewModel: VoiceRoomViewModel = viewModel(),
    onOpenProfile: ((String) -> Unit)? = null
) {
    com.example.util.KeepScreenOn()
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    var moderationTarget by remember { mutableStateOf<String?>(null) }
    var showRequests by remember { mutableStateOf(false) }
    var showMembers by remember { mutableStateOf(false) }
    var showMyPendant by remember { mutableStateOf(false) }
    var hasMic by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMic = granted
        viewModel.onAudioPermissionResult(granted)
    }

    var inputText by remember { mutableStateOf("") }
    var floatingEmojis by remember { mutableStateOf<List<VoiceRoomFloatingEmoji>>(emptyList()) }

    val pushReaction: (String) -> Unit = { emoji ->
        val xFraction = 0.15f + (Math.random().toFloat() * 0.7f)
        floatingEmojis = floatingEmojis + VoiceRoomFloatingEmoji(
            id = System.currentTimeMillis() + floatingEmojis.size,
            emoji = emoji,
            xFraction = xFraction
        )
    }

    LaunchedEffect(roomId) { viewModel.enterRoom(roomId) }
    DisposableEffect(Unit) {
        onDispose {
            val activity = context as? android.app.Activity
            if (activity?.isChangingConfigurations != true) viewModel.leaveRoom()
        }
    }

    val memberById = remember(state.members) { state.members.associateBy { it.userId } }
    val adminCanModerate = { seat: VoiceRoomSeat ->
        state.isAdmin && seat.isOccupied && seat.userId != state.myUserId
    }

    val hostSeat = state.seats.getOrNull(0)
    val hostMember = state.members.firstOrNull { it.userId == hostSeat?.userId }

    val snackbarHostState = remember { SnackbarHostState() }
    val chatListState = rememberLazyListState()

    LaunchedEffect(state.error) {
        val message = state.error
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    fun seatClickHaptic() {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    }

    val userLevel = levelFromRole(state.myRole)
    val roomObj = state.room
    val roomName = if (roomObj?.name.isNullOrEmpty()) "PANALINK VOZ" else roomObj.name
    val needsPermission = !hasMic && state.mySeat?.isMuted != true

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF020617))
                )
            )
    ) {
        // ── Contenido principal: header + sillones + chat ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ── Header rediseñado (sin botón +, sin barra de nivel) ──
            VoiceRoomRedesignedHeader(
                roomName = roomName,
                roomId = roomId,
                userLevel = userLevel,
                memberCount = state.memberCount,
                isPrivate = state.room?.isPrivate == true,
                showRequestsBadge = state.isAdmin && state.seatRequests.any { it.status == "pending" },
                onOpenRequests = { showRequests = true },
                onOpenMembers = { showMembers = true },
                onOpenSettings = if (state.isAdmin) { { viewModel.openSettings() } } else null,
                onOpenShare = { /* compartir sala - no-op por ahora */ },
                onClose = { viewModel.leaveRoom(); onBack() }
            )

            if (state.isJoining) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    color = VoiceRoomPalette.ActiveCyan
                )
            }

            // ── Sillón del anfitrión (centrado) ──
            VoiceRoomHostSeat(
                seat = hostSeat,
                member = hostMember,
                myUserId = state.myUserId,
                isAdmin = state.isAdmin,
                pendantCode = state.pendantCode,
                onClick = { seatClickHaptic(); viewModel.onSeatClicked(0, hasMic) },
                onAdmin = { hostSeat?.userId?.let { moderationTarget = it } }
            )

            // ── Sillones de invitados: 2 filas de 4 (compactas) ──
            VoiceRoomGuestSeatGrid(
                seats = state.seats,
                memberById = memberById,
                myUserId = state.myUserId,
                isAdmin = state.isAdmin,
                hasMic = hasMic,
                pendantCode = state.pendantCode,
                onSeatClicked = { index -> seatClickHaptic(); viewModel.onSeatClicked(index, hasMic) },
                onModeration = { userId -> moderationTarget = userId },
                onOpenProfile = onOpenProfile
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Up Next strip (preservado) ──
            VoiceRoomUpNextStrip(
                seats = state.seats,
                members = state.members,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp)
            )

            // ── Área de Chat ──
            Box(modifier = Modifier.weight(1f)) {
                VoiceRoomRedesignedChat(
                    messages = state.messages,
                    memberById = memberById,
                    myUserId = state.myUserId,
                    onOpenProfile = onOpenProfile,
                    modifier = Modifier.fillMaxSize(),
                    listState = chatListState
                )
            }
        }

        // ── Barra inferior rediseñada ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        ) {
            VoiceRoomRedesignedBottomBar(
                inputText = inputText,
                onValueChange = { inputText = it.take(2000) },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                isSeated = state.isSeated,
                isMuted = state.mySeat?.isMuted == true,
                needsPermission = needsPermission,
                onRequestSeat = { viewModel.requestAnySeat() },
                onToggleMute = { viewModel.toggleMute() },
                onEnableMic = { permission.launch(Manifest.permission.RECORD_AUDIO) },
                onStickerSelected = { stickerUrl -> viewModel.sendMessage("[sticker:$stickerUrl]") },
                onOpenSettings = if (state.isAdmin) { { viewModel.openSettings() } } else null,
                onOpenToolbox = if (state.isAdmin) { { viewModel.openToolbox() } } else null,
                onOpenMyPendant = { showMyPendant = true },
                onLeaveRoom = { viewModel.leaveRoom(); onBack() },
                isAdmin = state.isAdmin
            )
        }

        // ── Overlay de emojis flotantes ──
        VoiceRoomFloatingEmojiOverlay(
            emojis = floatingEmojis,
            onDone = { id -> floatingEmojis = floatingEmojis.filterNot { it.id == id } }
        )

        // ── Snackbar ──
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
        )
    }

    // ── Diálogos (reutilizados) ──
    if (moderationTarget != null) {
        ModerationDialog(
            targetUserId = moderationTarget!!,
            isHost = state.isHost,
            targetIsAdmin = state.members.firstOrNull { it.userId == moderationTarget }?.role == "admin",
            targetMuted = state.seats.firstOrNull { it.userId == moderationTarget }?.isMuted == true,
            onDismiss = { moderationTarget = null },
            onMute = { viewModel.moderateMute(moderationTarget!!, true); moderationTarget = null },
            onUnmute = { viewModel.moderateMute(moderationTarget!!, false); moderationTarget = null },
            onKick = { viewModel.kickUser(moderationTarget!!); moderationTarget = null },
            onBan = { viewModel.banUser(moderationTarget!!); moderationTarget = null },
            onAdmin = { viewModel.setAdmin(moderationTarget!!, true); moderationTarget = null },
            onRemoveAdmin = { viewModel.setAdmin(moderationTarget!!, false); moderationTarget = null }
        )
    }
    if (showRequests) {
        SeatRequestsDialog(
            requests = state.seatRequests.filter { it.status == "pending" },
            onApprove = { id -> viewModel.approveSeatRequest(id) },
            onDeny = { id -> viewModel.denySeatRequest(id) },
            onDismiss = { showRequests = false }
        )
    }
    if (showMembers) {
        VoiceRoomMembersSheet(
            members = state.members,
            myUserId = state.myUserId,
            onDismiss = { showMembers = false },
            onOpenProfile = onOpenProfile
        )
    }
    if (state.showSettings) {
        VoiceRoomSettingsSheet(
            room = state.room,
            members = state.members,
            myUserId = state.myUserId,
            isHost = state.isHost,
            isSaving = state.isSettingsSaving,
            message = state.settingsMessage,
            bannedUsers = state.bannedUsers,
            onClose = { viewModel.closeSettings() },
            onSaveSettings = { name, description, coverUrl, category, visibility, isLocked ->
                viewModel.updateRoomSettings(name, description, coverUrl, category, visibility, isLocked)
            },
            onDeleteRoom = { viewModel.deleteRoom { onBack() } },
            onSetAdmin = { userId, makeAdmin -> viewModel.setAdmin(userId, makeAdmin) },
            onKick = { viewModel.kickUser(it) },
            onBan = { viewModel.banUser(it) },
            onRemoveBan = { viewModel.removeBan(it) },
            onOpenProfile = onOpenProfile
        )
    }

    // ── Toolbox del dueño: entradas + colgantes (solo admin) ──
    if (state.showToolbox) {
        VoiceRoomToolboxSheet(
            currentEntrance = state.entranceCode,
            currentPendant = state.pendantCode,
            onSelectEntrance = { viewModel.setEntrance(it) },
            onSelectPendant = { viewModel.setPendant(it) },
            onDismiss = { viewModel.closeToolbox() }
        )
    }

    // ── Mi colgante personal (todos los usuarios) ──
    if (showMyPendant) {
        VoiceRoomMyPendantSheet(
            myPendantCode = state.myPendantCode ?: state.mySeat?.pendantCode,
            onSelect = { code -> viewModel.setMyPendant(code); showMyPendant = false },
            onDismiss = { showMyPendant = false }
        )
    }

    // ── Overlay de ENTRADA a pantalla completa (todos los miembros) ──
    state.entranceEvent?.let { event ->
        VoiceRoomEntranceOverlay(
            event = event,
            onDone = { viewModel.clearEntranceEvent() },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// === Header rediseñado ===

@Composable
fun VoiceRoomRedesignedHeader(
    roomName: String,
    roomId: String,
    userLevel: Int,
    memberCount: Int,
    isPrivate: Boolean,
    showRequestsBadge: Boolean,
    onOpenRequests: () -> Unit,
    onOpenMembers: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    onOpenShare: (() -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Bloque izquierdo: estrella + nombre + ID ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(VoiceRoomPalette.Gold.copy(alpha = 0.2f))
                    .border(1.dp, VoiceRoomPalette.Gold.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Nivel $userLevel",
                    tint = VoiceRoomPalette.Gold,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = roomName,
                    color = VoiceRoomPalette.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "ID: ${roomId.take(8)}...",
                    color = VoiceRoomPalette.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Separador flexible: empuja los iconos a la esquina derecha
        Spacer(modifier = Modifier.weight(1f))

        // ── Bloque derecho: usuario+contador, regalo, compartir, cerrar ──
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Contador de usuarios
            IconButton(
                onClick = onOpenMembers,
                modifier = Modifier.size(36.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = VoiceRoomPalette.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = memberCount.toString(),
                        color = VoiceRoomPalette.TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (showRequestsBadge) {
                IconButton(onClick = onOpenRequests, modifier = Modifier.size(36.dp)) {
                    Box {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = "Solicitudes",
                            tint = VoiceRoomPalette.Pink,
                            modifier = Modifier.size(20.dp)
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(VoiceRoomPalette.RedLive)
                                .padding(horizontal = 3.dp, vertical = 1.dp)
                                .align(Alignment.TopEnd)
                        ) {
                            Text("!", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            onOpenShare?.let { share ->
                IconButton(onClick = share, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Compartir",
                        tint = VoiceRoomPalette.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            onOpenSettings?.let { settings ->
                IconButton(onClick = settings, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Configuración",
                        tint = VoiceRoomPalette.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = VoiceRoomPalette.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// === Sillón del Anfitrión ===

@Composable
fun VoiceRoomHostSeat(
    seat: VoiceRoomSeat?,
    member: VoiceRoomMember?,
    myUserId: String,
    isAdmin: Boolean,
    pendantCode: String = "none",
    onClick: () -> Unit,
    onAdmin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayName = when {
        seat?.isOccupied != true -> null
        else -> seat.displayName?.takeIf { !it.isNullOrBlank() } ?: member?.displayName
    }
    val avatarUrl = seat?.avatarUrl ?: member?.avatarUrl

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Slot FIJO del tamaño del marco (no del avatar): el sillón no se mueve
        // ni cambia de tamaño cuando el usuario sube con colgante.
        val slotSize = 54.dp * SeatSlotScale
        Box(
            modifier = Modifier.size(slotSize),
            contentAlignment = Alignment.Center
        ) {
            VoiceRoomRedesignedSeatCircle(
                seat = seat,
                size = 54.dp,
                avatarUrl = avatarUrl,
                displayName = displayName,
                isHost = true,
                showAdminCog = false,
                onClick = onClick,
                onAdmin = onAdmin
            )

            // Colgante del anfitrión: su colgante personal si lo definió, o el de la sala.
            val hostPendant = seat?.pendantCode?.takeIf { it != "none" } ?: pendantCode
            if (seat?.isOccupied == true && hostPendant.isNotBlank() && hostPendant != "none") {
                VoiceRoomPendant(
                    code = hostPendant,
                    size = 54.dp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Nombre del anfitrión: chip superpuesto bajo el avatar (gap 6.dp); el
            // anillo del marco vectorial llega a ~5.5.dp bajo el avatar.
            if (seat?.isOccupied == true) {
                VoiceRoomSeatNameChip(
                    text = displayName ?: "",
                    isMine = seat.userId == myUserId,
                    fontSize = 14.sp,
                    chipModifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = 54.dp / 2 + 6.dp)
                )
            } else {
                Text(
                    text = "Sin anfitrión",
                    color = VoiceRoomPalette.TextSecondary,
                    fontSize = 11.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 54.dp + 4.dp)
                )
            }
        }
    }
}

/** Chip con el nombre del usuario, anclado justo debajo del avatar dentro del slot.
 *  Un fondo translúcido mantiene el texto legible cuando un colgante dibuja
 *  material en la zona inferior del badge. Se posiciona desde el BoxScope padre
 *  mediante [chipModifier] (p.ej. align + padding), por lo que el texto solo
 *  aplica su propio fondo. */
@Composable
private fun VoiceRoomSeatNameChip(
    text: String,
    isMine: Boolean,
    fontSize: TextUnit,
    chipModifier: Modifier = Modifier
) {
    Box(modifier = chipModifier) {
        Text(
            text = text,
            color = if (isMine) VoiceRoomPalette.ActiveCyan else VoiceRoomPalette.TextSecondary,
            fontSize = fontSize,
            fontWeight = if (isMine) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .background(
                    color = Color(0x990B1220),
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}
// === Grid de sillones de invitados ===

@Composable
fun VoiceRoomGuestSeatGrid(
    seats: List<VoiceRoomSeat>,
    memberById: Map<String, VoiceRoomMember>,
    myUserId: String,
    isAdmin: Boolean,
    hasMic: Boolean,
    pendantCode: String = "none",
    onSeatClicked: (Int) -> Unit,
    onModeration: (String) -> Unit,
    onOpenProfile: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Fila A: NO. 1, 2, 3, 4
        VoiceRoomSeatRow(
            startIndex = 1,
            endIndex = 4,
            seats = seats,
            memberById = memberById,
            myUserId = myUserId,
            isAdmin = isAdmin,
            size = 54.dp,
            pendantCode = pendantCode,
            onSeatClicked = onSeatClicked,
            onModeration = onModeration,
            onOpenProfile = onOpenProfile
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Fila B: NO. 5, 6, 7, 8
        VoiceRoomSeatRow(
            startIndex = 5,
            endIndex = 8,
            seats = seats,
            memberById = memberById,
            myUserId = myUserId,
            isAdmin = isAdmin,
            size = 54.dp,
            pendantCode = pendantCode,
            onSeatClicked = onSeatClicked,
            onModeration = onModeration,
            onOpenProfile = onOpenProfile
        )
    }
}

@Composable
private fun VoiceRoomSeatRow(
    startIndex: Int,
    endIndex: Int,
    seats: List<VoiceRoomSeat>,
    memberById: Map<String, VoiceRoomMember>,
    myUserId: String,
    isAdmin: Boolean,
    size: Dp,
    pendantCode: String = "none",
    onSeatClicked: (Int) -> Unit,
    onModeration: (String) -> Unit,
    onOpenProfile: ((String) -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top
    ) {
        for (i in startIndex..endIndex) {
            val seat = seats.getOrNull(i)
            val member = seat?.userId?.let { memberById[it] }
            VoiceRoomRedesignedSeat(
                seat = seat,
                seatNumber = i,
                member = member,
                size = size,
                isMine = seat?.userId == myUserId,
                showAdminAction = seat?.let { isAdmin && it.isOccupied && it.userId != myUserId } == true,
                pendantCode = pendantCode,
                onClick = { onSeatClicked(i) },
                onAdmin = { seat?.userId?.let { onModeration(it) } },
                onOpenProfile = if (seat?.userId != null && seat.userId != myUserId && onOpenProfile != null) {
                    { onOpenProfile(seat.userId) }
                } else null
            )
        }
    }
}

// === Sillón individual rediseñado ===

@Composable
fun VoiceRoomRedesignedSeat(
    seat: VoiceRoomSeat?,
    seatNumber: Int,
    member: VoiceRoomMember?,
    size: Dp,
    isHost: Boolean = false,
    isMine: Boolean = false,
    showAdminAction: Boolean = false,
    pendantCode: String = "none",
    onClick: () -> Unit,
    onAdmin: () -> Unit = {},
    onOpenProfile: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val displayName = when {
        seat?.isOccupied != true -> null
        else -> seat.displayName?.takeIf { !it.isNullOrBlank() } ?: member?.displayName
    }
    val avatarUrl = seat?.avatarUrl ?: member?.avatarUrl

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Slot FIJO del tamaño del marco: los sillones no se desplazan entre sí
        // cuando uno de ellos sube con un colgante (el marco es más grande que
        // el avatar pero ya no agranda el contenedor del asiento).
        val slotSize = size * SeatSlotScale
        Box(
            modifier = Modifier.size(slotSize),
            contentAlignment = Alignment.Center
        ) {
            VoiceRoomRedesignedSeatCircle(
                seat = seat,
                size = size,
                avatarUrl = avatarUrl,
                displayName = displayName,
                isHost = isHost,
                showAdminCog = showAdminAction && seat?.isOccupied == true && !isMine,
                onClick = onClick,
                onAdmin = onAdmin
            )

            // Colgante (pendant) del usuario del sillón; si no tiene uno propio,
            // se usa el de la sala (que define el dueño).
            val seatPendant = seat?.pendantCode?.takeIf { it != "none" } ?: pendantCode
            if (seat?.isOccupied == true && seatPendant.isNotBlank() && seatPendant != "none") {
                VoiceRoomPendant(
                    code = seatPendant,
                    size = size,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Nombre del ocupante: chip superpuesto bajo el avatar (gap 6.dp); el
            // anillo del marco vectorial llega a ~5.5.dp bajo el avatar.
            if (seat?.isOccupied == true) {
                VoiceRoomSeatNameChip(
                    text = displayName ?: "",
                    isMine = isMine,
                    fontSize = 10.sp,
                    chipModifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = size / 2 + 6.dp)
                )
            } else {
                Text(
                    text = "NO. $seatNumber",
                    color = VoiceRoomPalette.TextSecondary,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                )
            }
        }
    }
}

@Composable
fun VoiceRoomRedesignedSeatCircle(
    seat: VoiceRoomSeat?,
    size: Dp,
    avatarUrl: String?,
    displayName: String?,
    isHost: Boolean = false,
    showAdminCog: Boolean = false,
    onClick: () -> Unit,
    onAdmin: () -> Unit = {}
) {
    val speaking = seat?.isSpeaking == true
    val occupied = seat?.isOccupied == true
    val isMuted = seat?.isMuted == true

    val circleBg = if (occupied) {
        if (speaking) VoiceRoomPalette.DarkSurface.copy(alpha = 0.6f)
        else VoiceRoomPalette.DarkSurface.copy(alpha = 0.45f)
    } else {
        VoiceRoomPalette.SurfaceBlue.copy(alpha = 0.4f)
    }

    // Borde animado que pulsa visiblemente cuando el usuario habla
    val borderWidthAnim = if (speaking && !isMuted) {
        val borderWidthAnimFloat = rememberInfiniteTransition(label = "speakingBorder")
            .animateFloat(
                initialValue = 3f,
                targetValue = 5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "speakingBorderWidth"
            )
        borderWidthAnimFloat.value.dp
    } else {
        1.5.dp
    }

    val borderColor = if (speaking && !isMuted) VoiceRoomPalette.ActiveCyan else Color(0x33FFFFFF)

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(circleBg)
            .border(borderWidthAnim, borderColor, CircleShape)
            .shadow(if (occupied) 10.dp else 3.dp, CircleShape, clip = false)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (occupied) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Avatar de ${displayName ?: "usuario"}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Text(
                    text = displayName?.take(1)?.uppercase() ?: "👤",
                    color = VoiceRoomPalette.ActiveCyan,
                    fontSize = (size.value * 0.35f).sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (speaking && !isMuted) {
                // Aura premium: aurora + ondas sonar alrededor del avatar.
                VoiceRoomSpeakingAura(
                    speaking = true,
                    avatarSize = size,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else {
            Icon(
                imageVector = Icons.Default.Chair,
                contentDescription = if (isHost) "Sillón del anfitrión libre" else "Sillón libre",
                tint = VoiceRoomPalette.TextSecondary.copy(alpha = 0.7f),
                modifier = Modifier.size(size * 0.35f)
            )
        }

        // Badge de silencio
        if (seat.isMuteBadgeVisibleInternal()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size((size.value * 0.3f).dp)
                    .clip(CircleShape)
                    .background(VoiceRoomPalette.RedLive),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MicOff,
                    contentDescription = "Silenciado",
                    tint = Color.White,
                    modifier = Modifier.size((size.value * 0.18f).dp)
                )
            }
        }

        // Cog de administrador
        if (showAdminCog) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size((size.value * 0.32f).dp)
                    .clip(CircleShape)
                    .background(VoiceRoomPalette.Gold)
                    .clickable(onClick = onAdmin),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Administrar",
                    tint = VoiceRoomPalette.DeepBlue,
                    modifier = Modifier.size((size.value * 0.18f).dp)
                )
            }
        }
    }
}

// === Área de chat rediseñada ===

@Composable
fun VoiceRoomRedesignedChat(
    messages: List<VoiceRoomMessage>,
    memberById: Map<String, VoiceRoomMember>,
    myUserId: String,
    onOpenProfile: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val chatMessages = messages.filter { !it.isSystem }
    val systemMessages = messages.filter { it.isSystem }

    // Mensaje de bienvenida como mensaje de sistema cuando el chat está vacío
    val welcomeMessage = VoiceRoomMessage(
        id = "welcome",
        roomId = "",
        senderId = "",
        senderName = null,
        content = "¡Bienvenido a la sala!",
        createdAt = "",
        isSystem = true
    )

    // Lista combinada: system messages al final (reverseLayout = true → índice 0 = bottom)
    val allItems: List<Any> = if (chatMessages.isEmpty() && systemMessages.isEmpty()) {
        listOf(welcomeMessage)
    } else {
        // System messages first (bottom of list in reverseLayout), then chat messages (top)
        systemMessages + chatMessages
    }

    // Lógica de scroll inteligente (misma semántica que VoiceRoomTikTokChat)
    val shouldAutoScroll = remember {
        derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) true
            else {
                val bottomIndex = visible.lastOrNull()?.index
                bottomIndex == 0 || bottomIndex == null
            }
        }
    }

    LaunchedEffect(allItems.size) {
        if (allItems.isNotEmpty() && shouldAutoScroll.value) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    ) {
        items(allItems, key = { item ->
            when (item) {
                is VoiceRoomMessage -> if (item.isSystem) "sys_${item.id}" else "msg_${item.id}"
                else -> item.hashCode().toString()
            }
        }) { item ->
            if (item is VoiceRoomMessage && item.isSystem) {
                VoiceRoomSystemBubble(message = item, modifier = Modifier.fillMaxWidth())
            } else if (item is VoiceRoomMessage) {
                val member = memberById[item.senderId]
                val displayName = member?.displayName ?: item.senderName ?: "(sin nombre)"
                val avatarUrl = member?.avatarUrl
                VoiceRoomRedesignChatMessage(
                    message = item,
                    avatarUrl = avatarUrl,
                    senderName = displayName,
                    isOwnMessage = item.senderId == myUserId,
                    onOpenProfile = if (item.senderId != myUserId && item.senderId.isNotEmpty() && onOpenProfile != null) {
                        { onOpenProfile(item.senderId) }
                    } else null
                )
            }
        }
    }
}

// === Mensaje de chat individual ===

@Composable
fun VoiceRoomRedesignChatMessage(
    message: VoiceRoomMessage,
    avatarUrl: String?,
    senderName: String?,
     isOwnMessage: Boolean,
     onOpenProfile: (() -> Unit)? = null
) {
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(8f) }
    LaunchedEffect(message.id) {
        launch { alpha.animateTo(1f, tween(200)) }
        launch { offsetY.animateTo(0f, tween(200, easing = LinearOutSlowInEasing)) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = offsetY.value.dp)
            .graphicsLayer(alpha = alpha.value),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(VoiceRoomPalette.SurfaceBlue.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                if (onOpenProfile != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .clickable(onClick = onOpenProfile ?: {})
                    )
                } else {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                }
            } else {
                Text(
                    text = senderName?.take(1)?.uppercase() ?: "👤",
                    color = VoiceRoomPalette.ActiveCyan,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        val stickerPath = parseStickerContent(message.content)
        if (stickerPath != null) {
            // Stickers: render directamente sobre fondo transparente, sin burbuja
            AsyncImage(
                model = stickerPath,
                contentDescription = "Sticker",
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit,
                placeholder = null
            )
        } else {
            // Text messages: con burbuja de fondo
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .widthIn(max = 200.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x14FFFFFF))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalAlignment = Alignment.Start
            ) {
                if (!senderName.isNullOrBlank() && senderName != "(sin nombre)") {
                    Text(
                        text = senderName,
                        color = if (isOwnMessage) VoiceRoomPalette.ActiveCyan else VoiceRoomPalette.TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = message.content,
                    color = VoiceRoomPalette.TextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier
                        .widthIn(max = 200.dp)
                        .wrapContentHeight()
                )
            }
        }
    }
}

// === Barra inferior rediseñada ===

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VoiceRoomRedesignedBottomBar(
    modifier: Modifier = Modifier,
    inputText: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isSeated: Boolean,
    isMuted: Boolean,
    needsPermission: Boolean,
    onRequestSeat: () -> Unit,
    onToggleMute: () -> Unit,
    onEnableMic: () -> Unit,
    onStickerSelected: ((String) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onOpenToolbox: (() -> Unit)? = null,
    onOpenMyPendant: (() -> Unit)? = null,
    onLeaveRoom: () -> Unit,
    isAdmin: Boolean
) {
    var showMenu by remember { mutableStateOf(false) }
    var showStickers by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    if (showStickers && onStickerSelected != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showStickers = false },
            sheetState = sheetState
        ) {
            StickerPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                onStickerSelected = { sticker ->
                    showStickers = false
                    onStickerSelected(sticker.imageUrl)
                    keyboardController?.hide()
                }
            )
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Campo de texto con forma de píldora
        OutlinedTextField(
            value = inputText,
            onValueChange = { onValueChange(it.take(2000)) },
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 40.dp)
                .background(Color(0xFF1E293B), shape = CircleShape)
                .focusRequester(focusRequester),
            singleLine = true,
            placeholder = {
                Text(
                    "Vamos a platicar",
                    fontSize = 14.sp,
                    color = VoiceRoomPalette.TextSecondary
                )
            },
            textStyle = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = VoiceRoomPalette.TextPrimary
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = VoiceRoomPalette.TextPrimary,
                unfocusedTextColor = VoiceRoomPalette.TextPrimary,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color(0xFF1E293B),
                unfocusedContainerColor = Color(0xFF1E293B)
            ),
            shape = CircleShape,
            maxLines = 1
        )

        // IconButton para Enviar (pegado a la píldora)
        IconButton(
            onClick = onSend,
            enabled = inputText.isNotBlank(),
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Enviar",
                tint = if (inputText.isNotBlank()) VoiceRoomPalette.ActiveCyan else VoiceRoomPalette.TextSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }

        // IconButton para Emojis (abre teclado)
        IconButton(
            onClick = {
                focusRequester.requestFocus()
                keyboardController?.show()
            },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Face,
                contentDescription = "Emojis",
                tint = VoiceRoomPalette.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        // IconButton para Stickers/GIFs
        IconButton(
            onClick = { if (onStickerSelected != null) { showStickers = true } },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Gif,
                contentDescription = "Stickers",
                tint = VoiceRoomPalette.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Botón Caja de herramientas (solo admin): entradas + colgantes
        if (isAdmin && onOpenToolbox != null) {
            IconButton(
                onClick = { onOpenToolbox() },
                modifier = Modifier.size(36.dp)
            ) {
                Text(
                    text = "🎛️",
                    fontSize = 18.sp
                )
            }
        }

        // IconButton para Micrófono (mutear/desmutear + menú en long-press)
        Box(
            modifier = Modifier
                .size(36.dp)
                .combinedClickable(
                    onLongClick = { showMenu = true },
                    onClick = {
                        if (needsPermission) onEnableMic()
                        else if (isSeated) onToggleMute()
                        else onRequestSeat()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            val micIcon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic
            val micTint = when {
                needsPermission -> VoiceRoomPalette.ActiveCyan
                isMuted -> Color(0xFFFF8A80)
                else -> VoiceRoomPalette.ActiveCyan
            }
            Icon(
                imageVector = micIcon,
                contentDescription = if (isMuted) "Micrófono silenciado" else "Micrófono activo",
                tint = micTint,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    // Menú desplegable: sillón / configuración / salir
    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false },
        containerColor = VoiceRoomPalette.DeepBlue,
        border = BorderStroke(1.dp, VoiceRoomPalette.ActiveCyan.copy(alpha = 0.2f))
    ) {
        if (!isSeated) {
            DropdownMenuItem(
                onClick = { onRequestSeat(); showMenu = false },
                text = { Text("Unirme a un sillón", color = VoiceRoomPalette.TextPrimary, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = VoiceRoomPalette.ActiveCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }

        if (isSeated) {
            if (needsPermission) {
                DropdownMenuItem(
                    onClick = { onEnableMic(); showMenu = false },
                    text = { Text("Activar micrófono", color = VoiceRoomPalette.ActiveCyan, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = VoiceRoomPalette.ActiveCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            } else {
                DropdownMenuItem(
                    onClick = { onToggleMute(); showMenu = false },
                    text = {
                        Text(
                            text = if (isMuted) "Activar micrófono" else "Silenciar micrófono",
                            color = if (isMuted) Color(0xFFFF8A80) else VoiceRoomPalette.TextPrimary,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (isMuted) Color(0xFFFF8A80) else VoiceRoomPalette.ActiveCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }

        if (onOpenMyPendant != null) {
            DropdownMenuItem(
                onClick = { onOpenMyPendant(); showMenu = false },
                text = { Text("Mi colgante", color = VoiceRoomPalette.TextPrimary, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Diamond,
                        contentDescription = null,
                        tint = VoiceRoomPalette.Gold,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }

        if (isAdmin && onOpenSettings != null) {
            DropdownMenuItem(
                onClick = { onOpenSettings(); showMenu = false },
                text = { Text("Configuración", color = VoiceRoomPalette.TextPrimary, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = VoiceRoomPalette.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }

        DropdownMenuItem(
            onClick = { onLeaveRoom(); showMenu = false },
            text = { Text("Salir de la sala", color = Color(0xFFFF8A80), fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = Color(0xFFFF8A80),
                    modifier = Modifier.size(16.dp)
                )
            }
        )
    }
}
