package com.example.rooms.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rooms.model.VoiceRoomSeat

@Composable
fun VoiceRoomScreenV2(
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
    var hasMic by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
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
    val adminCanModerate = { seat: VoiceRoomSeat -> state.isAdmin && seat.isOccupied && seat.userId != state.myUserId }

    val hostSeat = state.seats.getOrNull(0)
    val hostMember = state.members.firstOrNull { it.userId == hostSeat?.userId }

    val snackbarHostState = remember { SnackbarHostState() }
    val chatListState = rememberLazyListState()

    // Smart scroll is handled INSIDE VoiceRoomTikTokChat (single owner)

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

    VoiceRoomBackground {
        Box(modifier = Modifier.fillMaxSize()) {

            // ── Contenido principal: header + escenario + asientos + up next + chat ──
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                VoiceRoomHeader(
                    room = state.room,
                    hostDisplayName = hostMember?.displayName ?: hostSeat?.displayName,
                    hostAvatarUrl = hostMember?.avatarUrl ?: hostSeat?.avatarUrl,
                    memberCount = state.memberCount,
                    isPrivate = state.room?.isPrivate == true,
                    showRequestsBadge = state.isAdmin && state.seatRequests.any { it.status == "pending" },
                    onOpenRequests = { showRequests = true },
                    onOpenMembers = { showMembers = true },
                    onOpenSettings = if (state.isAdmin) { { viewModel.openSettings() } } else null,
                    onClose = { viewModel.leaveRoom(); onBack() }
                )

                if (state.isJoining) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = VoiceRoomPalette.ActiveCyan
                    )
                }

                // Host centrado, con separación vertical compacta.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    VoiceRoomStageSeat(
                        seat = hostSeat,
                        size = 56.dp,
                        isHost = true,
                        isMine = (hostSeat?.userId == state.myUserId),
                        showAdminAction = (hostSeat?.let { adminCanModerate(it) } == true),
                        onClick = { seatClickHaptic(); viewModel.onSeatClicked(0, hasMic) },
                        onAdmin = { hostSeat?.userId?.let { moderationTarget = it } }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Starmaker: cuatro asientos por fila, agrupados cerca del centro.
                // ROW A: [1] [2] [5] [6] (4 sillones espaciados uniformemente)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        val s1 = state.seats.getOrNull(1)
                        val s2 = state.seats.getOrNull(2)
                        VoiceRoomStageSeat(
                            seat = s1, size = 56.dp, isMine = (s1?.userId == state.myUserId),
                            showAdminAction = (s1?.let { adminCanModerate(it) } == true),
                            onClick = { seatClickHaptic(); viewModel.onSeatClicked(1, hasMic) },
                            onAdmin = { s1?.userId?.let { moderationTarget = it } }
                        )
                        VoiceRoomStageSeat(
                            seat = s2, size = 56.dp, isMine = (s2?.userId == state.myUserId),
                            showAdminAction = (s2?.let { adminCanModerate(it) } == true),
                            onClick = { seatClickHaptic(); viewModel.onSeatClicked(2, hasMic) },
                            onAdmin = { s2?.userId?.let { moderationTarget = it } }
                        )
                        val s5 = state.seats.getOrNull(5)
                        val s6 = state.seats.getOrNull(6)
                        VoiceRoomStageSeat(
                            seat = s5, size = 56.dp, isMine = (s5?.userId == state.myUserId),
                            showAdminAction = (s5?.let { adminCanModerate(it) } == true),
                            onClick = { seatClickHaptic(); viewModel.onSeatClicked(5, hasMic) },
                            onAdmin = { s5?.userId?.let { moderationTarget = it } }
                        )
                        VoiceRoomStageSeat(
                            seat = s6, size = 56.dp, isMine = (s6?.userId == state.myUserId),
                            showAdminAction = (s6?.let { adminCanModerate(it) } == true),
                            onClick = { seatClickHaptic(); viewModel.onSeatClicked(6, hasMic) },
                            onAdmin = { s6?.userId?.let { moderationTarget = it } }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // ROW B: [3] [4] [7] [8] (4 sillones espaciados uniformemente)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        val s3 = state.seats.getOrNull(3)
                        val s4 = state.seats.getOrNull(4)
                        VoiceRoomStageSeat(
                            seat = s3, size = 56.dp, isMine = (s3?.userId == state.myUserId),
                            showAdminAction = (s3?.let { adminCanModerate(it) } == true),
                            onClick = { seatClickHaptic(); viewModel.onSeatClicked(3, hasMic) },
                            onAdmin = { s3?.userId?.let { moderationTarget = it } }
                        )
                        VoiceRoomStageSeat(
                            seat = s4, size = 56.dp, isMine = (s4?.userId == state.myUserId),
                            showAdminAction = (s4?.let { adminCanModerate(it) } == true),
                            onClick = { seatClickHaptic(); viewModel.onSeatClicked(4, hasMic) },
                            onAdmin = { s4?.userId?.let { moderationTarget = it } }
                        )
                        val s7 = state.seats.getOrNull(7)
                        val s8 = state.seats.getOrNull(8)
                        VoiceRoomStageSeat(
                            seat = s7, size = 56.dp, isMine = (s7?.userId == state.myUserId),
                            showAdminAction = (s7?.let { adminCanModerate(it) } == true),
                            onClick = { seatClickHaptic(); viewModel.onSeatClicked(7, hasMic) },
                            onAdmin = { s7?.userId?.let { moderationTarget = it } }
                        )
                        VoiceRoomStageSeat(
                            seat = s8, size = 56.dp, isMine = (s8?.userId == state.myUserId),
                            showAdminAction = (s8?.let { adminCanModerate(it) } == true),
                            onClick = { seatClickHaptic(); viewModel.onSeatClicked(8, hasMic) },
                            onAdmin = { s8?.userId?.let { moderationTarget = it } }
                        )
                    }
                }

                VoiceRoomUpNextStrip(
                    seats = state.seats,
                    members = state.members,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp)
                )

                // Chat ocupa todo el espacio restante y queda detrás del compositor.
                Box(modifier = Modifier.weight(1f)) {
                    VoiceRoomTikTokChat(
                        messages = state.messages,
                        memberById = memberById,
                        onOpenProfile = onOpenProfile,
                        modifier = Modifier.fillMaxSize(),
                        listState = chatListState
                    )
                }
            }

            VoiceRoomFloatingEmojiOverlay(
                emojis = floatingEmojis,
                onDone = { id -> floatingEmojis = floatingEmojis.filterNot { it.id == id } }
            )

            // Composer siempre por encima de la barra de navegación y del chat.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                VoiceRoomInputBar(
                    value = inputText,
                    onValueChange = { inputText = it.take(2000) },
                    onSend = {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    },
                    isSeated = state.isSeated,
                    isMuted = state.mySeat?.isMuted == true,
                    pendingRequest = state.pendingSeatRequest != null,
                    needsPermission = (!hasMic && state.mySeat?.isMuted != true),
                    onRequestSeat = { viewModel.requestAnySeat() },
                    onToggleMute = { viewModel.toggleMute() },
                    onEnableMic = { permission.launch(Manifest.permission.RECORD_AUDIO) },
                    onReaction = { emoji -> pushReaction(emoji) }
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
            )
        }
    }

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
}

@Composable
fun ModerationDialog(
    targetUserId: String,
    isHost: Boolean,
    targetIsAdmin: Boolean,
    targetMuted: Boolean,
    onDismiss: () -> Unit,
    onMute: () -> Unit,
    onUnmute: () -> Unit,
    onKick: () -> Unit,
    onBan: () -> Unit,
    onAdmin: () -> Unit,
    onRemoveAdmin: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Administrar usuario", color = VoiceRoomPalette.TextPrimary) },
        text = {
            Column {
                Text(targetUserId.take(12), color = VoiceRoomPalette.TextSecondary, fontSize = 11.sp)
                if (!targetIsAdmin || isHost) {
                    TextButton(onClick = if (targetMuted) onUnmute else onMute) {
                        Text(if (targetMuted) "Desmutear" else "Mutear", color = VoiceRoomPalette.ActiveCyan)
                    }
                    TextButton(onClick = onKick) {
                        Text("Expulsar", color = Color(0xFFFF8A80))
                    }
                    TextButton(onClick = onBan) {
                        Text("Bloquear", color = Color(0xFFFF8A80))
                    }
                }
                if (isHost && !targetIsAdmin) {
                    TextButton(onClick = onAdmin) {
                        Text("Dar administración", color = VoiceRoomPalette.Gold)
                    }
                }
                if (isHost && targetIsAdmin) {
                    TextButton(onClick = onRemoveAdmin) {
                        Text("Quitar administración", color = VoiceRoomPalette.Gold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = VoiceRoomPalette.TextSecondary)
            }
        },
        containerColor = VoiceRoomPalette.DeepBlue,
        titleContentColor = VoiceRoomPalette.TextPrimary,
        textContentColor = VoiceRoomPalette.TextPrimary
    )
}

@Composable
fun SeatRequestsDialog(
    requests: List<com.example.rooms.model.VoiceRoomSeatRequest>,
    onApprove: (String) -> Unit,
    onDeny: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Solicitudes de sillón", color = VoiceRoomPalette.TextPrimary) },
        text = {
            Column {
                if (requests.isEmpty()) {
                    Text("No hay solicitudes pendientes", color = VoiceRoomPalette.TextSecondary)
                } else {
                    requests.forEach { r ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(r.userId.take(10), color = VoiceRoomPalette.TextSecondary, fontSize = 11.sp)
                                Text(
                                    if (r.requestedSeatIndex == null) "Cualquier sillón" else "Sillón ${r.requestedSeatIndex}",
                                    color = VoiceRoomPalette.TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(onClick = { onApprove(r.id) }) {
                                Icon(Icons.Default.Check, contentDescription = "Aprobar", tint = VoiceRoomPalette.ActiveCyan)
                            }
                            IconButton(onClick = { onDeny(r.id) }) {
                                Icon(Icons.Default.Close, contentDescription = "Denegar", tint = Color(0xFFFF8A80))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = VoiceRoomPalette.TextSecondary)
            }
        },
        containerColor = VoiceRoomPalette.DeepBlue,
        titleContentColor = VoiceRoomPalette.TextPrimary,
        textContentColor = VoiceRoomPalette.TextPrimary
    )
}
