package com.example.rooms.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.rooms.data.VoiceRoomBanDto
import com.example.rooms.model.VoiceRoom
import com.example.rooms.model.VoiceRoomMember
import com.example.rooms.model.VoiceRoomMessage
import com.example.rooms.model.VoiceRoomSeat

internal object VoiceRoomPalette {
    val Bg = Color(0xFF120C0A)
    val BgDeep = Color(0xFF1E0F14)
    val WarmBg = Color(0xFF3A231A)
    val Accent = Color(0xFF4ADEAF)
    val AccentSoft= Color(0x334ADEAF)
    val Gold = Color(0xFFF6C66B)
    val Pink = Color(0xFFFF5C7A)
    val Blue = Color(0xFF7FC8FF)
    val RedLive = Color(0xFFEF2D55)
}

@Composable
fun VoiceRoomBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF401A17), VoiceRoomPalette.Bg, VoiceRoomPalette.BgDeep)
                )
            )
    ) {
        GlowOrb(
            color = VoiceRoomPalette.Accent,
            size = 260.dp,
            offsetX =  -60.dp,
            offsetY =  -40.dp
        )
        GlowOrb(
            color = VoiceRoomPalette.Pink,
            size = 220.dp,
            offsetX = 260.dp,
            offsetY =  60.dp
        )
        GlowOrb(
            color = VoiceRoomPalette.Gold,
            size = 180.dp,
            offsetX =  80.dp,
            offsetY =  420.dp
        )
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun GlowOrb(
    color: Color,
    size: Dp,
    offsetX: Dp,
    offsetY: Dp
) {
    Box(
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .size(size)
            .background(
                Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.16f), color.copy(alpha = 0.05f), Color.Transparent),
                    radius = 1f
                ),
                CircleShape
            )
    )
}

@Composable
fun VoiceRoomLiveBadge(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "liveBadge")
    val dotAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "dotAlpha"
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = VoiceRoomPalette.RedLive
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical =  3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = dotAlpha))
            )
            Text("EN VIVO", color = Color.White, fontSize =  9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun VoiceRoomHeader(
    room: VoiceRoom?,
    hostDisplayName: String?,
    hostAvatarUrl: String?,
    memberCount: Int,
    isPrivate: Boolean,
    showRequestsBadge: Boolean,
    onOpenRequests: () -> Unit,
    onOpenMembers: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical =  8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF2E1A12))
                .border(1.dp, VoiceRoomPalette.Accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!hostAvatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = hostAvatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(hostDisplayName?.take(1)?.uppercase() ?: "👑", color = VoiceRoomPalette.Gold, fontSize =  16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    room?.name ?: "Sala de Voz",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize =  14.sp,
                    maxLines =  1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isPrivate) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Lock, contentDescription = "Privada", tint = VoiceRoomPalette.Gold, modifier = Modifier.size(12.dp))
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                VoiceRoomLiveBadge()
                Text(
                    hostDisplayName ?: "Anfitrión",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize =  11.sp,
                    maxLines =  1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Surface(
            onClick = onOpenMembers,
            shape = RoundedCornerShape(20.dp),
            color = Color(0x33000000)
        ) {
            Row(
                modifier = Modifier.padding(horizontal =  8.dp, vertical =  4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Group, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                Text("$memberCount", color = Color.White, fontSize =  11.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (showRequestsBadge) {
            IconButton(onClick = onOpenRequests, modifier = Modifier.size(38.dp)) {
                BadgedBox(badge = { Badge { Text("!") } }) {
                    Icon(Icons.Default.People, contentDescription = "Solicitudes", tint = VoiceRoomPalette.Accent)
                }
            }
        }
        if (onOpenSettings != null) {
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.Settings, contentDescription = "Configuración de la sala", tint = VoiceRoomPalette.Accent)
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Salir", tint = Color.White)
        }
    }
}

@Composable
fun VoiceRoomSpeakingAura(size: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "speakingAura")
    val auraScale by transition.animateFloat(
        initialValue = 1f,
        targetValue =  1.2f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "auraScale"
    )
    val auraAlpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "auraAlpha"
    )
    Box(
        modifier = modifier
            .size(size * 1.5f),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = auraScale
                    scaleY = auraScale
                    alpha = auraAlpha
                }
                .clip(CircleShape)
                .background(VoiceRoomPalette.Accent.copy(alpha = 0.5f))
        )
    }
}

@Composable
fun VoiceRoomStageSeat(
    seat: VoiceRoomSeat?,
    size: Dp,
    label: String,
    isHost: Boolean = false,
    isMine: Boolean = false,
    showAdminAction: Boolean = false,
    onClick: () -> Unit,
    onAdmin: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isHost) {
            Text("👑 Anfitrión", color = VoiceRoomPalette.Gold, fontSize =  11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
        }
        VoiceRoomSeatCircle(
            seat = seat,
            size = size,
            label = label,
            showAdminCog = showAdminAction && seat?.isOccupied == true && !isMine,
            onClick = onClick,
            onAdmin = onAdmin
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = when {
                seat?.isOccupied != true -> if (isHost) "Anfitrión" else label
                isMine -> "Tú"
                else ->seat.displayName ?: "Pana"
            },
            color = if (seat?.isOccupied == true) Color.White else Color.Gray,
            fontSize = if (isHost) 11.sp else 10.sp,
            fontWeight = if (isMine) FontWeight.Bold else FontWeight.Normal,
            maxLines =  1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VoiceRoomSeatCircle(
    seat: VoiceRoomSeat?,
    size: Dp,
    label:String,
    showAdminCog:Boolean,
    onClick:()->Unit,
    onAdmin:()->Unit,
) {
    val speaking = seat?.isSpeaking == true
    Box {
        if (speaking) {
            VoiceRoomSpeakingAura(size = size)
        }
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (seat?.isOccupied == true) Color(0xFF4A2C21) else Color(0x29FFFFFF))
                .border(if (speaking) 2.dp else 0.dp, VoiceRoomPalette.Accent, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (!seat?.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = seat.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (seat?.isOccupied == true) {
                Text(
                    seat.displayName?.take(1)?.uppercase() ?: "👤",
                    color = VoiceRoomPalette.Gold,
                    fontSize = (size.value * 0.35f).sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    Icons.Default.Chair,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(size * 0.44f)
                )
            }
        }
        if (seat?.isMuteBadgeVisible() == true) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE85D5D)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MicOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
            }
        }
        if (showAdminCog) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(VoiceRoomPalette.Gold)
                    .clickable(onClick = onAdmin),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Administrar", tint = Color(0xFF1A120E), modifier = Modifier.size(12.dp))
            }
        }
    }
}

private fun VoiceRoomSeat?.isMuteBadgeVisible(): Boolean = this != null && this.isOccupied && this.isMuted

@Composable
fun VoiceRoomTikTokChat(
    messages: List<VoiceRoomMessage>,
    memberById: Map<String, VoiceRoomMember>,
    onOpenProfile: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            if (message.isSystem) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        message.content,
                        color = VoiceRoomPalette.Gold,
                        fontSize =  10.sp,
                        modifier = Modifier
                            .background(Color(0x332A1812), RoundedCornerShape(8.dp))
                            .padding(horizontal =  10.dp, vertical =  3.dp)
                    )
                }
            } else {
                VoiceRoomTikTokMessage(
                    message = message,
                    avatarUrl = memberById[message.senderId]?.avatarUrl,
                    onOpenProfile = onOpenProfile
                )
            }
        }
    }
}

@Composable
private fun VoiceRoomTikTokMessage(
    message: VoiceRoomMessage,
    avatarUrl: String?,
    onOpenProfile: ((String) -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onOpenProfile != null) Modifier.clip(RoundedCornerShape(12.dp)).clickable { onOpenProfile(message.senderId) } else Modifier),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(0xFF4A2C21)),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(model = avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(message.senderName?.take(1)?.uppercase() ?: "👤", color = VoiceRoomPalette.Gold, fontSize =  11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(
            modifier = Modifier
                .background(Color.Black.copy(alpha =  0.35f), RoundedCornerShape(12.dp))
                .padding(horizontal =  10.dp, vertical =  6.dp)
        ) {
            Text(
                message.senderName ?: message.senderId.take(8),
                color = VoiceRoomPalette.Blue,
                fontSize =  10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines =  1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(1.dp))
            Text(message.content, color = Color.White, fontSize =  13.sp)
        }
    }
}

@Composable
fun VoiceRoomEmojiQuickBar(
    onReaction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val emojis = listOf("❤️", "🔥", "😍", "😂", "👏", "🎉", "👍", "💜")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal =  10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        emojis.forEach { emoji ->
            Surface(
                onClick = { onReaction(emoji) },
                shape = CircleShape,
                color = VoiceRoomPalette.AccentSoft,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(emoji, fontSize =  16.sp)
                }
            }
        }
    }
}

@Composable
fun VoiceRoomFloatingEmojiOverlay(
    emojis: List<VoiceRoomFloatingEmoji>,
    onDone: (Long) -> Unit,
    modifier: Modifier = Modifier
)
{
    Box(modifier = modifier.fillMaxSize()) {
        emojis.forEach { emoji ->
            key(emoji.id) {
                val progress = remember { androidx.compose.animation.core.Animatable(0f) }
                LaunchedEffect(emoji.id) {
                    progress.animateTo(
                        targetValue =  1f,
                        animationSpec = tween(1400, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    )
                    onDone(emoji.id)
                }
                Text(
                    emoji.emoji,
                    fontSize =  22.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom =  130.dp)
                        .graphicsLayer {
                            translationX = (emoji.xFraction - 0.5f) * size.width * 0.9f
                            translationY = -progress.value * size.height * 0.35f
                            alpha =  1f - progress.value
                            scaleX =  0.7f + progress.value * 0.8f
                            scaleY =  0.7f + progress.value * 0.8f
                        }
                )
            }
        }
    }
}

data class VoiceRoomFloatingEmoji(
    val id: Long,
    val emoji:String,
    val xFraction: Float = 0.5f
)
@Composable
fun VoiceRoomUpNextStrip(
    seats: List<VoiceRoomSeat>,
    members: List<VoiceRoomMember>,
    modifier: Modifier = Modifier
) {
    val occupiedSeats = seats.filter{ it.isOccupied }
    val joinedAtByUserId = members.associate{ it.userId to it.joinedAt }
    val queue = occupiedSeats.sortedBy { joinedAtByUserId[it.userId] ?: "" }
    val names = queue.mapNotNull { it.displayName ?: it.userId?.take(6) ?: "" }
    if (names.isEmpty()) return
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal =  12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("🎤 En cola:", color = VoiceRoomPalette.Gold, fontSize =  10.sp, fontWeight = FontWeight.Bold)
        names.take(4).forEach { name ->
            Surface(
                color = Color(0x332A1812),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    " $name ",
                    color = Color(0xFFE8DCD0),
                    fontSize =  10.sp,
                    maxLines =  1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (names.size >  4) {
            Surface(
                color = Color(0x33FFFFFF),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "+${names.size - 4}",
                    color = Color.Gray,
                    fontSize =  10.sp
                )
            }
        }
    }
}

@Composable
fun VoiceRoomInputBar(
    value: String,
    onValueChange:(String) -> Unit,
    onSend:()->Unit,
    isSeated:Boolean,
    isMuted:Boolean,
    pendingRequest:Boolean,
    needsPermission:Boolean,
    onRequestSeat:()->Unit,
    onToggleMute:()->Unit,
    onEnableMic:()->Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal =  8.dp, vertical =  6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VoiceRoomMicSeatButton(
            isSeated = isSeated,
            isMuted = isMuted,
            pendingRequest = pendingRequest,
            needsPermission = needsPermission,
            onRequestSeat = onRequestSeat,
            onToggleMute = onToggleMute,
            onEnableMic = onEnableMic
        )
        Spacer(Modifier.width(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder ={ Text("Di algo...", fontSize =  13.sp, color = Color.Gray) },
            textStyle = LocalTextStyle.current.copy(fontSize =  14.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = VoiceRoomPalette.Accent,
                unfocusedBorderColor = Color(0x33FFFFFF),
                focusedContainerColor = Color(0x1F000000),
                unfocusedContainerColor = Color(0x1F000000)
            ),
            shape = RoundedCornerShape(24.dp)
        )
        IconButton(
            onClick = onSend,
            enabled = value.isNotBlank(),
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.Default.Send,
                contentDescription = "Enviar",
                tint = if (value.isNotBlank()) VoiceRoomPalette.Accent else Color.Gray
            )
        }
    }
}

@Composable
fun VoiceRoomMicSeatButton(
    isSeated: Boolean,
    isMuted: Boolean,
    pendingRequest: Boolean,
    needsPermission: Boolean,
    onRequestSeat: () -> Unit,
    onToggleMute: () -> Unit,
    onEnableMic: () -> Unit
) {
    val icon = if (needsPermission || (isMuted && isSeated)) Icons.Default.MicOff else Icons.Default.Mic
    val tint = when {
        needsPermission ->VoiceRoomPalette.Accent
        !isSeated ->(if (pendingRequest) Color.Gray else VoiceRoomPalette.Accent)
        isMuted ->Color(0xFFFF8A80)
        else ->VoiceRoomPalette.Accent
    }
    val enabled = needsPermission || isSeated || !pendingRequest
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(VoiceRoomPalette.AccentSoft)
            .clickable(enabled = enabled) {
                if (needsPermission) onEnableMic()
                else if (isSeated) onToggleMute()
                else onRequestSeat()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRoomMembersSheet(
    members: List<VoiceRoomMember>,
    myUserId: String,
    onDismiss: () -> Unit,
    onOpenProfile: ((String) -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VoiceRoomPalette.BgDeep,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Text("Miembros (${members.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize =  16.sp)
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(members, key = { it.userId }) { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical =  6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4A2C21)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!member.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = member.avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(member.displayName?.take(1)?.uppercase() ?: "👤", color = VoiceRoomPalette.Gold, fontSize =  14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                member.displayName ?: "Usuario",
                                color = Color.White,
                                fontSize =  13.sp,
                                maxLines =  1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                voiceRoomRoleLabel(member.role),
                                color = if (member.role == "owner" || member.role == "admin") VoiceRoomPalette.Gold else VoiceRoomPalette.Accent,
                                fontSize =  10.sp
                            )
                        }
                        if (member.userId != myUserId && onOpenProfile != null) {
                            TextButton(onClick = { onOpenProfile(member.userId) }) {
                                Text("Ver perfil", color = VoiceRoomPalette.Accent, fontSize =  11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun voiceRoomRoleLabel(role: String): String = when (role) {
    "owner" -> "👑 Anfitrión"
    "admin" ->"⚙ Admin"
    "speaker" ->"🎤 Hablando"
    else ->"👂 Oyente"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRoomSettingsSheet(
    room: VoiceRoom?,
    members: List<VoiceRoomMember>,
    myUserId: String,
    isHost: Boolean,
    isSaving: Boolean,
    message: String? = null,
    bannedUsers: List<VoiceRoomBanDto> = emptyList(),
    onClose: () -> Unit,
    onSaveSettings: (String?, String?, String?, String?, String?, Boolean?) -> Unit,
    onDeleteRoom: () -> Unit,
    onSetAdmin: (String, Boolean) -> Unit,
    onKick: (String) -> Unit,
    onBan: (String) -> Unit,
    onRemoveBan: (String) -> Unit,
    onOpenProfile: ((String) -> Unit)? = null
) {
    var name by remember { mutableStateOf(room?.name ?: "") }
    var description by remember { mutableStateOf(room?.description ?: "") }
    var coverUrl by remember { mutableStateOf(room?.coverUrl ?: "") }
    var category by remember { mutableStateOf(room?.category ?: "general") }
    var visibility by remember { mutableStateOf(if (room?.isPrivate == true) "private" else "public") }
    var isLocked by remember { mutableStateOf(room?.isLocked ?: false) }
    var editingField by remember { mutableStateOf<String?>(null) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMembersTab by remember { mutableStateOf(false) }
    var showBannedTab by remember { mutableStateOf(false) }

    val categories = listOf("general" to "General", "chat" to "Charlar", "meeting" to "Reunión", "work" to "Trabajo", "dating" to "Enamorados", "friends" to "Conocer gente", "music" to "Música", "gaming" to "Gaming")
    val categoryLabel = categories.firstOrNull { it.first == category }?.second ?: "General"

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = VoiceRoomPalette.BgDeep,
        contentColor = Color.White
    ) {
 Column(
            Modifier.fillMaxWidth().padding(horizontal =  16.dp, vertical =  8.dp)
        ) {
 Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, null, tint = VoiceRoomPalette.Accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Configuración de la sala", color = Color.White, fontSize =  18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cerrar", tint = Color.White) }
            }
            Spacer(Modifier.height(6.dp))
            message?.let {
 Surface(color = VoiceRoomPalette.Accent.copy(alpha =  0.12f), shape = RoundedCornerShape(12.dp)) {
 Text(it, color = VoiceRoomPalette.Accent, fontSize =  12.sp, modifier = Modifier.padding(horizontal =  10.dp, vertical =  6.dp))
                }
            }
            Spacer(Modifier.height(4.dp))

            if (!isHost) {
 Text("Solo el anfitrión puede editar la configuración de la sala.", color = Color(0xFFB8A99A), fontSize =  12.sp, modifier = Modifier.padding(vertical =  6.dp))
            }

            SettingsSectionTitle("Perfil de la sala", "✏️")
            SettingsCard {
 SettingsRow("Nombre", name.ifBlank { "Sin nombre" }) { editingField = "name" }
                SettingsDivider()
 SettingsRow("Anuncio", description.ifBlank { "Sin anuncio" }) { editingField = "description" }
                SettingsDivider()
 SettingsRow("Portada", coverUrl.ifBlank { "Sin portada" }) { editingField = "cover" }
                SettingsDivider()
 SettingsRow("Categoría", categoryLabel) { editingField = null; showCategoryPicker = true }
            }

            SettingsSectionTitle("Privacidad", "🔒")
            SettingsCard {
 SettingsRow("Sala pública", "") { visibility = "public" }
                SettingsDivider()
 SettingsRow("Sala privada (solo invitados)", "") { visibility = "private" }
                SettingsDivider()
 SettingsToggleRow("Bloquear sala", "Nadie nuevo puede entrar", checked = isLocked) { isLocked = it }
            }

            if (editingField != null) {
                SettingsCard {
 Column(Modifier.padding(vertical =  6.dp)) {
 OutlinedTextField(
                            value = when (editingField) { "name" -> name; "description" -> description; else -> coverUrl },
                            onValueChange = { v -> when (editingField) { "name" -> name = v.take(80); "description" -> description = v.take(280); else -> coverUrl = v.take(500) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = editingField != "description",
                            minLines = if (editingField == "description") 2 else  1,
                            maxLines = if (editingField == "description") 4 else 1,
                            placeholder = { Text(if (editingField == "cover") "https://..." else if (editingField == "description") "Describe tu sala" else "Nombre de la sala") },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VoiceRoomPalette.Accent, unfocusedBorderColor = Color(0xFF3E3E44), focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
 Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
 TextButton(onClick = { editingField = null }) { Text("Listo", color = VoiceRoomPalette.Accent) }
                        }
                    }
                }
            }

            Button(
                onClick = { onSaveSettings(name,description,coverUrl.ifBlank { null },category,visibility,isLocked) },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = VoiceRoomPalette.Accent, contentColor = Color(0xFF1A120E)),
                shape = RoundedCornerShape(16.dp)
            ) { Text(if (isSaving) "Guardando..." else "Guardar cambios", fontWeight = FontWeight.Bold, fontSize =  15.sp) }
            Spacer(Modifier.height(12.dp))

            SettingsSectionTitle("Miembros y administración", "👥")
            SettingsCard {
 SettingsRow("Miembros (${members.size})", "") { showMembersTab = true }
                SettingsDivider()
 SettingsRow("Baneados (${bannedUsers.size})", "") { showBannedTab = true }
            }

            if (isHost) {
                Spacer(Modifier.height(10.dp))
                SettingsSectionTitle("Zona peligrosa", "⚠️")
                SettingsCard {
 SettingsRow("Borrar sala", "Esta acción es permanente", danger = true) { showDeleteConfirm = true }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showCategoryPicker) {
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = { Text("Categoría", color = Color.White) },
            text = { Column { categories.forEach { (key,label) -> Row(Modifier.fillMaxWidth().clickable { category = key; showCategoryPicker = false }.padding(vertical =  10.dp), verticalAlignment = Alignment.CenterVertically) { if (category == key) Icon(Icons.Default.Check, null, tint = VoiceRoomPalette.Accent, modifier = Modifier.size(18.dp)) else Spacer(Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text(label, color = Color.White, fontSize =  15.sp) } } } },
            confirmButton = { TextButton(onClick = { showCategoryPicker = false }) { Text("Cerrar", color = VoiceRoomPalette.Accent) } },
            containerColor = VoiceRoomPalette.BgDeep
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar sala", color = Color.White) },
            text = { Text("¿Seguro que quieres borrar esta sala para siempre? Se eliminarán sillones, mensajes, solicitudes y miembros.", color = Color(0xFFD8CDC4)) },
            confirmButton = { TextButton(onClick = { showDeleteConfirm = false; onDeleteRoom() }) { Text("Eliminar", color = Color(0xFFFF6E6E) ) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar", color = Color.White) } },
            containerColor = VoiceRoomPalette.BgDeep
        )
    }

if (showMembersTab) {
    AlertDialog(
        onDismissRequest = { showMembersTab = false },
        title = { Text("Miembros de la sala", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 420.dp)) {
                members.forEach { member ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF2E1A12)), contentAlignment = Alignment.Center) {
                            Text(member.displayName?.take(1) ?: "?", color = Color.White, fontSize =  14.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(member.displayName ?: "Usuario", color = Color.White, fontSize =  14.sp, fontWeight = FontWeight.Medium)
                            Text(voiceRoomRoleLabel(member.role), color = if (member.role == "owner" || member.role == "admin") VoiceRoomPalette.Gold else VoiceRoomPalette.Accent, fontSize =  11.sp)
                        }
                        if (member.userId != myUserId && member.userId != room?.ownerId) {

                            if (isHost && member.role != "admin") {
                                TextButton(onClick = { onSetAdmin(member.userId, true) }) { Text("Hacer admin", color = VoiceRoomPalette.Accent, fontSize =  11.sp) }
                            }
                            if (isHost && member.role == "admin") {
                                TextButton(onClick = { onSetAdmin(member.userId, false) }) { Text("Quitar admin", color = Color(0xFFE8C46A), fontSize =  11.sp) }
                            }
                            TextButton(onClick = { onKick(member.userId) }) { Text("Expulsar", color = Color(0xFFFF8A80), fontSize =  11.sp) }
                        }
                        onOpenProfile?.let {
                            TextButton(onClick = { it(member.userId) }) { Text("Ver", color = Color.Gray, fontSize =  11.sp) }
                        }
                    }
                    HorizontalDivider(color = Color(0x1FFFFFFF))
                }
            }
        },
        confirmButton = { TextButton(onClick = { showMembersTab = false }) { Text("Cerrar", color = VoiceRoomPalette.Accent) } },
        containerColor = VoiceRoomPalette.BgDeep
    )
}
    if (showBannedTab) {
    AlertDialog(
        onDismissRequest = { showBannedTab = false },
        title = { Text("Usuarios baneados", color = Color.White) },
        text = {
            if (bannedUsers.isEmpty()) {
                Text("No hay usuarios baneados.", color = Color(0xFFD8CDC4))
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max =420.dp)) {
                    bannedUsers.forEach { ban ->
                        Row(Modifier.fillMaxWidth().padding(vertical =  8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF2E1A12)), contentAlignment = Alignment.Center) {
                                Text(ban.displayName.take(1).ifBlank { "?" }, color = Color.White, fontSize =  14.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ban.displayName.ifBlank { "Usuario baneado" }, color = Color.White, fontSize =  14.sp, fontWeight = FontWeight.Medium)
                                Text(ban.reason ?: "Sin motivo", color = Color(0xFFB8A99A), fontSize =  11.sp, maxLines =  1, overflow = TextOverflow.Ellipsis)

                            }
                            TextButton(onClick = { onRemoveBan(ban.userId) }) { Text("Desbanear", color = VoiceRoomPalette.Accent, fontSize =  11.sp) }
                        }
                        HorizontalDivider(color = Color(0x1FFFFFFF))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showBannedTab = false }) { Text("Cerrar", color = VoiceRoomPalette.Accent) } },
        containerColor = VoiceRoomPalette.BgDeep
    )
}}
@Composable
private fun SettingsSectionTitle(title: String, emoji: String) {
    Text("$emoji $title", color = Color(0xFFB8A99A), fontWeight = FontWeight.Bold, fontSize =  13.sp, modifier = Modifier.padding(top =  14.dp, bottom =  6.dp))
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color(0xFF241510), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {

 Column(content = content)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = Color(0x1AFFFFFF), modifier = Modifier.padding(horizontal =  14.dp))
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String = "",
    danger: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(Modifier.fillMaxWidth().then(clickMod).padding(horizontal =  16.dp, vertical =  13.dp), verticalAlignment = Alignment.CenterVertically) {

        Column(Modifier.weight(1f)) {

            Text(title, color = if (danger) Color(0xFFFF6E6E) else Color.White, fontSize =  14.sp, fontWeight = FontWeight.Medium)


            if (subtitle.isNotBlank()) { Text(subtitle, color = Color(0xFFB8A99A), fontSize =  11.sp, maxLines =  1, overflow = TextOverflow.Ellipsis) }

        }

        if (danger) { Text("⚠️", fontSize =  14.sp) }

        else if (onClick != null) { Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFB8A99A), modifier = Modifier.size(16.dp)) }

    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(horizontal =  16.dp, vertical =  8.dp), verticalAlignment = Alignment.CenterVertically) {

        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize =  14.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) { Text(subtitle, color = Color(0xFFB8A99A), fontSize =  11.sp) }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = VoiceRoomPalette.Accent, checkedTrackColor = VoiceRoomPalette.Accent.copy(alpha =  0.35f), uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFF3E3E44)))
    }
}