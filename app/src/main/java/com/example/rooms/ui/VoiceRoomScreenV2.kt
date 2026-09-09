package com.example.rooms.ui

import android.Manifest
import android.content.pm.PackageManager
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val context = androidx.compose.ui.platform.LocalContext.current
    var moderationTarget by remember { mutableStateOf<String?>(null) }
    var showRequests by remember { mutableStateOf(false) }
    var showMembers by remember { mutableStateOf(false) }
    var hasMic by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasMic = granted; viewModel.onAudioPermissionResult(granted) }

    var inputText by remember { mutableStateOf("") }
    var emojiReactions by remember { mutableStateOf<List<VoiceRoomFloatingEmoji>>(emptyList()) }
    var reactionIdCounter by remember { mutableStateOf(0L) }
    val pushReaction: (String) -> Unit = { emoji ->
        val xFraction = 0.15f + (Math.random().toFloat() * 0.7f)
        emojiReactions = emojiReactions + VoiceRoomFloatingEmoji(reactionIdCounter++, emoji, xFraction)
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
    LaunchedEffect(state.error) {
        val message = state.error
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            chatListState.animateScrollToItem(0)
        }
    }

    fun seatClickHaptic() {
        val view = (context as? android.app.Activity)?.window?.decorView
        view?.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    }

    VoiceRoomBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
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
            if (state.isJoining) LinearProgressIndicator(Modifier.fillMaxWidth(), color = VoiceRoomPalette.Accent)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                VoiceRoomStageSeat(
                    seat = hostSeat,
                    size =  58.dp,
                    label = "Anfitrión",
                    isHost = true,
                    isMine = (hostSeat?.userId == state.myUserId),
                    showAdminAction = (hostSeat?.let { adminCanModerate(it) } == true),
                    onClick = { seatClickHaptic(); viewModel.onSeatClicked(0, hasMic) },
                    onAdmin = { hostSeat?.userId?.let { moderationTarget = it } }
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal =  12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                (1..4).forEach { idx ->
                    val seat = state.seats.getOrNull(idx)
                    VoiceRoomStageSeat(
                        seat = seat,
                        size =  54.dp,
                        label = "NO. $idx",
                        isMine = (seat?.userId == state.myUserId),
                        showAdminAction = (seat?.let { adminCanModerate(it) } == true),
                        onClick = { seatClickHaptic(); viewModel.onSeatClicked(idx, hasMic) },
                        onAdmin = { seat?.userId?.let { moderationTarget = it } }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal =  12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                (5..8).forEach { idx ->
                    val seat = state.seats.getOrNull(idx)
                    VoiceRoomStageSeat(
                        seat = seat,
                        size =  54.dp,
                        label = "NO. $idx",
                        isMine = (seat?.userId == state.myUserId),
                        showAdminAction = (seat?.let { adminCanModerate(it) } == true),
                        onClick = { seatClickHaptic(); viewModel.onSeatClicked(idx, hasMic) },
                        onAdmin = { seat?.userId?.let { moderationTarget = it } }
                    )
                }
            }

            HorizontalDivider(
                color = Color(0x26FFFFFF),
                modifier = Modifier.padding(top =  6.dp, bottom =  2.dp)
            )

            VoiceRoomUpNextStrip(
                seats = state.seats,
                members = state.members,
                modifier = Modifier.fillMaxWidth()
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
            ) {
                VoiceRoomTikTokChat(
                    messages = state.messages,
                    memberById = memberById,
                    onOpenProfile = onOpenProfile,
                    modifier = Modifier.fillMaxSize(),
                    listState = chatListState
                )
                VoiceRoomFloatingEmojiOverlay(
                    emojis = emojiReactions,
                    onDone = { id -> emojiReactions = emojiReactions.filterNot { it.id == id } }
                )
            }

            Spacer(Modifier.weight(1f))

            VoiceRoomEmojiQuickBar(
                onReaction = { emoji -> pushReaction(emoji) }
            )

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
                onEnableMic = { permission.launch(Manifest.permission.RECORD_AUDIO) }
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
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
            onSaveSettings = { name, description, coverUrl, category, visibility, isLocked -> viewModel.updateRoomSettings(name, description, coverUrl, category, visibility, isLocked) },
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
private fun ModerationDialog(
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
        title = { Text("Administrar usuario") },
        text = {
            Column {
                Text(targetUserId.take(12), color = Color.Gray, fontSize =  11.sp)
                if (!targetIsAdmin || isHost) {
                    TextButton(onClick = if (targetMuted) onUnmute else onMute) {
                        Text(if (targetMuted) "Desmutear" else "Mutear")
                    }
                    TextButton(onClick = onKick) {
                        Text("Expulsar")
                    }
                    TextButton(onClick = onBan) {
                        Text("Bloquear")
                    }
                }
                if (isHost && !targetIsAdmin) {
                    TextButton(onClick = onAdmin) {
                        Text("Dar administración")
                    }
                }
                if (isHost && targetIsAdmin) {
                    TextButton(onClick = onRemoveAdmin) {
                        Text("Quitar administración")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
        containerColor = VoiceRoomPalette.BgDeep,
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

@Composable
private fun SeatRequestsDialog(
    requests: List<com.example.rooms.model.VoiceRoomSeatRequest>,
    onApprove: (String) -> Unit,
    onDeny: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Solicitudes de sillón") },
        text = {
            Column {
                if (requests.isEmpty()) {
                    Text("No hay solicitudes pendientes")
                } else {
                    requests.forEach { r ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(r.userId.take(10), color = Color.White)
                                Text(
                                    if (r.requestedSeatIndex == null) "Cualquier sillón" else "Sillón ${r.requestedSeatIndex}",
                                    color = Color.Gray,
                                    fontSize =  11.sp
                                )
                            }
                            IconButton(onClick = { onApprove(r.id) }) {
                                Icon(Icons.Default.Check, contentDescription = "Aprobar", tint = VoiceRoomPalette.Accent)
                            }
                            IconButton(onClick = { onDeny(r.id) }) {
                                Icon(Icons.Default.Close, contentDescription = "Denegar", tint = Color(0xFFFF7B72))
                            }
                }
            }
        }
        }
},
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
        containerColor = VoiceRoomPalette.BgDeep,
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}