package com.example.rooms.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.rooms.data.VoiceRoomBanDto
import com.example.rooms.model.VoiceRoom
import com.example.rooms.model.VoiceRoomMember
import com.example.rooms.model.VoiceRoomMessage
import com.example.rooms.model.VoiceRoomSeat
import kotlin.random.Random
import kotlin.math.roundToInt

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
fun GlowOrb(
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
private fun VoiceRoomNoiseTexture(modifier: Modifier = Modifier) {
    val noiseAlpha = 0.08f
    Canvas(modifier = modifier.fillMaxSize()) {
        val area = size.width * size.height
        val step = 12f
        val count = (area / (step * step)).toInt()
        val rnd = Random(42)
        repeat(count) {
            val x = rnd.nextFloat() * size.width
            val y = rnd.nextFloat() * size.height
            val a = rnd.nextFloat() * noiseAlpha
            drawRect(color = Color.White.copy(alpha = a), topLeft = Offset(x.toFloat(), y.toFloat()), size = androidx.compose.ui.geometry.Size(step, step))
        }
    }
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
        color = VoiceRoomPalette.RedLive,
        shadowElevation = 4.dp
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
    val hostGlowAlpha by rememberInfiniteTransition(label = "hostGlow").animateFloat(
        initialValue = 0.25f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "hostGlowAlpha"
    )
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
                .shadow(6.dp, CircleShape)
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
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(VoiceRoomPalette.Gold.copy(alpha = hostGlowAlpha))
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    room?.name ?: "Sala de Voz",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize =  15.sp,
                    maxLines =  1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isPrivate) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Lock, contentDescription = "Privada", tint = VoiceRoomPalette.Gold, modifier = Modifier.size(12.dp))
                }
            }
            Spacer(Modifier.height(1.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                VoiceRoomLiveBadge()
                Text(
                    hostDisplayName ?: "Anfitrión",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize =  11.sp,
                    fontWeight = FontWeight.Medium,
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
fun VoiceRoomSpeakingAura(size: Dp, speaking: Boolean, modifier: Modifier = Modifier) {
    val base = size * 1.6f
    val waves = listOf(
        VoiceRoomPalette.Accent,
        VoiceRoomPalette.Pink,
        VoiceRoomPalette.Gold
    )
    Box(
        modifier = modifier
            .size(base),
        contentAlignment = Alignment.Center
    ) {
        waves.forEachIndexed { idx, color ->
            val infiniteTransition = rememberInfiniteTransition(label = "auraWave$idx")
            val auraScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.55f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1400, delayMillis = idx * 350, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "waveScale$idx"
            )
            val auraAlpha by infiniteTransition.animateFloat(
                initialValue = 0.55f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1400, delayMillis = idx * 350, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "waveAlpha$idx"
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = if (speaking) auraScale else 1f
                        scaleY = if (speaking) auraScale else 1f
                        alpha = if (speaking) auraAlpha else 0f
                    }
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.45f))
            )
        }
    }
}

@Composable
fun VoiceRoomAudioVisualizer(
    modifier: Modifier = Modifier,
    barCount: Int = 4,
    levels: List<Float>? = null
) {
    val idle = levels == null || levels.isEmpty()
    val displayLevels = if (idle) List(barCount) { 0f } else levels.take(barCount).let { if (it.size < barCount) it + List(barCount - it.size) { 0f } else it }
    val animatedHeights = remember(levels) { displayLevels.map { Animatable(4f) }.toMutableStateList() }

    LaunchedEffect(displayLevels) {
        displayLevels.forEachIndexed { idx, level ->
            val fraction = level.coerceIn(0f, 1f)
            val targetHeight = (4f + fraction * 14f).coerceAtLeast(4f)
            animatedHeights.getOrNull(idx)?.animateTo(targetHeight, tween(120))
        }
    }

    Row(
        modifier = modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally)
    ) {
        displayLevels.forEachIndexed { idx, level ->
            val fraction = level.coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((animatedHeights.getOrNull(idx)?.value ?: 4f).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (fraction > 0.05f) VoiceRoomPalette.Accent else VoiceRoomPalette.Accent.copy(alpha = 0.25f))
            )
        }
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
    val occupied = seat?.isOccupied == true
    val seatTransition = updateTransition(occupied, label = "seatOccupied")
    val seatAlpha by seatTransition.animateFloat(
        transitionSpec = { tween(220, easing = FastOutSlowInEasing) },
        label = "seatAlpha"
    ) { if (it) 1f else 0.6f }
    val seatScale by seatTransition.animateFloat(
        transitionSpec = { tween(220, easing = FastOutSlowInEasing) },
        label = "seatScale"
    ) { if (it) 1f else 0.92f }
    val auraSize = size * 1.6f
    Box(
        modifier = Modifier.size(auraSize),
        contentAlignment = Alignment.Center
    ) {
        VoiceRoomSpeakingAura(size = size, speaking = speaking)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .shadow(
                        elevation = if (occupied) 6.dp else 2.dp,
                        shape = CircleShape,
                        clip = false
                    )
                    .background(if (occupied) Color(0xFF4A2C21) else Color(0x29FFFFFF))
                    .border(if (speaking) 2.dp else 0.dp, VoiceRoomPalette.Accent, CircleShape)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.graphicsLayer { alpha = seatAlpha; scaleX = seatScale; scaleY = seatScale }) {
                    if (!seat?.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = seat.avatarUrl,
                            contentDescription = "Avatar de ${seat.displayName ?: seat.userId}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (occupied) {
                        Text(
                            seat.displayName?.take(1)?.uppercase() ?: "👤",
                            color = VoiceRoomPalette.Gold,
                            fontSize = (size.value * 0.35f).sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            Icons.Default.Chair,
                            contentDescription = "Sillón libre",
                            tint = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(size * 0.44f)
                        )
                    }
                }
            }
            if (speaking) {
                Spacer(Modifier.width(6.dp))
                VoiceRoomAudioVisualizer(levels = seat?.audioLevels ?: emptyList(), barCount = 4)
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
                Icon(Icons.Default.MicOff, contentDescription = "Silenciado", tint = Color.White, modifier = Modifier.size(10.dp))
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
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val chatMessages = messages.filter { !it.isSystem }
    val systemMessages = messages.filter { it.isSystem }
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }
    Box(modifier = modifier) {
        if (chatMessages.isEmpty() && systemMessages.isEmpty()) {
            Text(
                text = "No hay mensajes aún, saludá! 👋",
                color = Color(0x88FFFFFF),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        ) {
            items(chatMessages, key = { it.id }) { message ->
                VoiceRoomTikTokMessage(
                    message = message,
                    avatarUrl = memberById[message.senderId]?.avatarUrl,
                    onOpenProfile = onOpenProfile
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(90.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, VoiceRoomPalette.BgDeep)
                    )
                )
        )
        Box(modifier = Modifier.fillMaxSize()) {
            systemMessages.forEach { msg ->
                VoiceRoomSystemBubble(
                    message = msg,
                    modifier = Modifier.align(Alignment.BottomStart)
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
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(14f) }
    LaunchedEffect(message.id) {
        launch { alpha.animateTo(1f, tween(260)) }
        launch { offsetY.animateTo(0f, tween(260, easing = FastOutSlowInEasing)) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = offsetY.value.dp)
            .graphicsLayer { this.alpha = alpha.value }
            .then(if (onOpenProfile != null) Modifier.clip(RoundedCornerShape(14.dp)).clickable { onOpenProfile(message.senderId) } else Modifier),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(VoiceRoomPalette.Gold.copy(alpha = 0.25f), Color(0xFF4A2C21)),
                        radius = 1f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(model = avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(message.senderName?.take(1)?.uppercase() ?: "👤", color = VoiceRoomPalette.Gold, fontSize =  11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.Transparent,
            shadowElevation = 3.dp,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0x26FFFFFF), Color(0x12FFFFFF))
                        )
                    )
                    .padding(horizontal =  12.dp, vertical =  7.dp)
            ) {
            Text(
                message.senderName ?: message.senderId.take(8),
                color = VoiceRoomPalette.Blue,
                fontSize =  11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines =  1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(message.content, color = Color(0xFFF4E8DC), fontSize =  13.sp, lineHeight = 17.sp)
        }
    }
}
}

@Composable
fun VoiceRoomSystemBubble(message: VoiceRoomMessage, modifier: Modifier = Modifier) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.3f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    LaunchedEffect(message.id) {
        launch { alpha.animateTo(1f, tween(350)) }
        launch { scale.animateTo(1f, tween(350, easing = FastOutSlowInEasing)) }
        launch {
            offsetX.animateTo(220f, tween(1800, easing = LinearOutSlowInEasing))
            offsetY.animateTo(-180f, tween(1800, easing = LinearOutSlowInEasing))
        }
        delay(2200)
        launch { alpha.animateTo(0f, tween(400)) }
    }
    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = VoiceRoomPalette.Accent.copy(alpha = 0.92f),
            shadowElevation = 8.dp,
            modifier = Modifier.wrapContentSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👋", fontSize = 12.sp)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    message.content,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun VoiceRoomEmojiQuickBar(
    onReaction: (String) -> Unit,
    emojiCounts: Map<String, Int> = emptyMap(),
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
            val count = emojiCounts[emoji] ?: 0
            Surface(
                onClick = { onReaction(emoji) },
                shape = CircleShape,
                color = VoiceRoomPalette.AccentSoft,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(emoji, fontSize =  16.sp)
                    if (count > 0) {
                        Text(
                            text = count.toString(),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 2.dp, top = 2.dp)
                                .size(12.dp)
                                .background(Color(0xFFFF3B30), CircleShape)
                        )
                    }
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
                val trail = listOf(0.55f, 0.8f)
                trail.forEachIndexed { i, trailScale ->
                    Text(
                        emoji.emoji,
                        fontSize = 22.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom =  130.dp)
                            .graphicsLayer {
                                translationX = (emoji.xFraction - 0.5f) * size.width * 0.9f
                                translationY = -progress.value * size.height * 0.35f - (i + 1) * 8f
                                alpha =  (1f - progress.value) * (1f - (i + 1) * 0.25f)
                                scaleX =  0.5f + progress.value * 0.6f - (i + 1) * 0.15f
                                scaleY =  0.5f + progress.value * 0.6f - (i + 1) * 0.15f
                            }
                    )
                }
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
    if (queue.isEmpty()) return
    Box(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            item {
                Text("🎤 En cola:", color = VoiceRoomPalette.Gold, fontSize =  10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }
            items(queue.take(8), key = { it.userId ?: it.index }) { seat ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x332A1812),
                    modifier = Modifier.height(26.dp),
                    shadowElevation = 2.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4A2C21)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!seat.avatarUrl.isNullOrBlank()) {
                                AsyncImage(model = seat.avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Text((seat.displayName ?: "?").take(1).uppercase(), color = VoiceRoomPalette.Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            seat.displayName ?: seat.userId?.take(6) ?: "Pana",
                            color = Color(0xFFF4E8DC),
                            fontSize =  10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines =  1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(60.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, VoiceRoomPalette.BgDeep)
                    )
                )
        )
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
    val listenOnly = isSeated && needsPermission
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0x1F000000),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
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
        if (listenOnly) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Solo escucha",
                color = Color(0x88FFFFFF),
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            )
        } else {
            Spacer(Modifier.width(4.dp))
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder ={ Text("Di algo...", fontSize = 11.sp, color = Color(0xFFB8A99A)) },
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = VoiceRoomPalette.Accent,
                unfocusedBorderColor = Color(0x33FFFFFF),
                focusedContainerColor = Color(0x1F000000),
                unfocusedContainerColor = Color(0x1F000000)
            ),
            shape = RoundedCornerShape(18.dp)
        )
        IconButton(
            onClick = onSend,
            enabled = value.isNotBlank(),
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Send,
                contentDescription = "Enviar",
                tint = if (value.isNotBlank()) VoiceRoomPalette.Accent else Color(0x66FFFFFF),
                modifier = Modifier.size(18.dp)
            )
        }
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
    val active = isSeated && !isMuted
    val pulseAlpha by rememberInfiniteTransition(label = "micPulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Restart),
        label = "micPulseAlpha"
    )
    val pulseScale by rememberInfiniteTransition(label = "micPulseScale").animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Restart),
        label = "micPulseScale"
    )
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
        if (active) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        alpha = pulseAlpha
                    }
                    .clip(CircleShape)
                    .background(VoiceRoomPalette.Accent)
            )
        }
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = { scope.launch { sheetState.hide(); onDismiss() } },
        containerColor = VoiceRoomPalette.BgDeep,
        contentColor = Color.White,
        sheetState = sheetState,
        dragHandle = null,
        tonalElevation = 12.dp,
        modifier = Modifier.navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Miembros (${members.size})", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize =  17.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { scope.launch { sheetState.hide(); onDismiss() } }) { Icon(Icons.Default.Close, "Cerrar", tint = Color.White) }
            }
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

fun voiceRoomRoleLabel(role: String): String = when (role) {
    "owner" -> "👑 Anfitrión"
    "admin" ->"⚙ Admin"
    "speaker" ->"🎤 Hablando"
    else ->"👂 Oyente"
}

@Composable
fun AnimatedDialog(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    text: @Composable (() -> Unit)? = null,
    confirmText: String,
    dangerConfirm: Boolean = false,
    onConfirm: () -> Unit,
    dismissText: String? = null,
    onDismissClick: (() -> Unit)? = null,
    containerColor: Color = VoiceRoomPalette.BgDeep,
    content: (@Composable () -> Unit)? = null
) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.92f) }
    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(200)) }
        launch { scale.animateTo(1f, tween(200, easing = FastOutSlowInEasing)) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
        text = text ?: content,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (dangerConfirm) Color(0xFFFF6E6E) else VoiceRoomPalette.Accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = if (dismissText != null && onDismissClick != null) {
            { TextButton(onClick = onDismissClick) { Text(dismissText, color = Color.White) } }
        } else null,
        containerColor = containerColor,
        modifier = modifier.graphicsLayer {
            this.alpha = alpha.value
            scaleX = scale.value
            scaleY = scale.value
        }
    )
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
        onDismissRequest = { onClose() },
        containerColor = VoiceRoomPalette.BgDeep,
        contentColor = Color.White,
        tonalElevation = 16.dp,
        modifier = Modifier.navigationBarsPadding()
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal =  16.dp, vertical =  10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, null, tint = VoiceRoomPalette.Accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Configuración de la sala", color = Color.White, fontSize =  18.sp, fontWeight = FontWeight.ExtraBold)
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
        AnimatedDialog(
            onDismiss = { showCategoryPicker = false },
            title = "Categoría",
            confirmText = "Cerrar",
            onConfirm = { showCategoryPicker = false },
            onDismissClick = { showCategoryPicker = false },
            containerColor = VoiceRoomPalette.BgDeep
        ) {
            Column {
                categories.forEach { (key,label) ->
                    Row(Modifier.fillMaxWidth().clickable { category = key; showCategoryPicker = false }.padding(vertical =  10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (category == key) Icon(Icons.Default.Check, null, tint = VoiceRoomPalette.Accent, modifier = Modifier.size(18.dp)) else Spacer(Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(label, color = Color.White, fontSize =  15.sp)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AnimatedDialog(
            onDismiss = { showDeleteConfirm = false },
            title = "Eliminar sala",
            text = { Text("¿Seguro que quieres borrar esta sala para siempre? Se eliminarán sillones, mensajes, solicitudes y miembros.", color = Color(0xFFD8CDC4)) },
            confirmText = "Eliminar",
            dangerConfirm = true,
            onConfirm = { showDeleteConfirm = false; onDeleteRoom() },
            dismissText = "Cancelar",
            onDismissClick = { showDeleteConfirm = false },
            containerColor = VoiceRoomPalette.BgDeep
        )
    }

    if (showMembersTab) {
        AnimatedDialog(
            onDismiss = { showMembersTab = false },
            title = "Miembros de la sala",
            confirmText = "Cerrar",
            onConfirm = { showMembersTab = false },
            containerColor = VoiceRoomPalette.BgDeep
        ) {
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
        }
    }
    if (showBannedTab) {
        AnimatedDialog(
            onDismiss = { showBannedTab = false },
            title = "Usuarios baneados",
            confirmText = "Cerrar",
            onConfirm = { showBannedTab = false },
            containerColor = VoiceRoomPalette.BgDeep
        ) {
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
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String, emoji: String) {
    Text("$emoji $title", color = Color(0xFFB8A99A), fontWeight = FontWeight.Bold, fontSize =  13.sp, modifier = Modifier.padding(top =  14.dp, bottom =  6.dp))
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color(0xFF241510), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {

 Column(content = content)
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(color = Color(0x1AFFFFFF), modifier = Modifier.padding(horizontal =  14.dp))
}

@Composable
fun SettingsRow(
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
fun SettingsToggleRow(
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
