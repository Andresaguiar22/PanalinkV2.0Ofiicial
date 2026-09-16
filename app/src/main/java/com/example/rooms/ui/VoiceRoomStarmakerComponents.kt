@file:Suppress("UnusedMaterialScaffoldPaddingParameter")

package com.example.rooms.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Check
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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// === Paleta Starmaker — azul profundo / petróleo ===

internal object VoiceRoomPalette {
    val MainBlue       = Color(0xFF00557D)
    val DeepBlue       = Color(0xFF00466A)
    val SurfaceBlue    = Color(0xFF075D84)
    val DarkSurface    = Color(0xFF003E5E)
    val BgTop          = Color(0xFF000E1A)
    val BgBottom       = Color(0xFF000509)
    val ActiveCyan     = Color(0xFF4FE7EA)
    val ActiveCyanSoft = Color(0x4D4FE7EA)
    val Pink           = Color(0xFFFF5C7A)
    val RedLive        = Color(0xFFEF2D55)
    val TextPrimary    = Color(0xFFF2F7FA)
    val TextSecondary  = Color(0xFFA9C5D2)

    // Aliases backward-compat con VoiceRoomScreenV2
    val Bg             = DeepBlue
    val BgDeep         = MainBlue
    val Surface        = SurfaceBlue
    val SurfaceDark    = DarkSurface
    val Accent         = ActiveCyan
    val AccentSoft     = ActiveCyanSoft
    val Gold           = Color(0xFFF6C66B)
}

// === Background ===

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
                    colors = listOf(VoiceRoomPalette.BgTop, VoiceRoomPalette.BgBottom)
                )
            )
    ) {
        VoiceRoomNoiseTexture(Modifier.matchParentSize())
        // Sutil overlay de profundidad
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x08FFFFFF),
                            Color(0x00FFFFFF)
                        ),
                        center = Offset.Zero,
                        radius = 1200f
                    )
                )
        )
        Box(Modifier.matchParentSize()) { content() }
    }
}

@Composable
private fun VoiceRoomNoiseTexture(modifier: Modifier = Modifier) {
    val noiseAlpha = 0.04f
    Canvas(modifier = modifier) {
        val area = size.width * size.height
        val step = 12f
        val count = (area / (step * step)).toInt()
        val rnd = Random(42)
        repeat(count) {
            val x = rnd.nextFloat() * size.width
            val y = rnd.nextFloat() * size.height
            val a = rnd.nextFloat() * noiseAlpha
            drawRect(
                color = VoiceRoomPalette.ActiveCyan.copy(alpha = a),
                topLeft = Offset(x, y),
                size = Size(step, step)
            )
        }
    }
}

// === Header — compacto, horizontal ===

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
    onOpenShare: (() -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circular del host
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(VoiceRoomPalette.DarkSurface.copy(alpha = 0.4f))
                .border(1.5.dp, VoiceRoomPalette.ActiveCyan.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!hostAvatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = hostAvatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Text(
                    text = hostDisplayName?.take(1)?.uppercase() ?: "🎤",
                    color = VoiceRoomPalette.ActiveCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))

        // Nombre de la sala + información secundaria
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = room?.name ?: "Sala de Voz",
                color = VoiceRoomPalette.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPrivate) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Privada",
                        tint = VoiceRoomPalette.Gold,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Privada", color = VoiceRoomPalette.TextSecondary, fontSize = 11.sp)
                } else {
                    VoiceRoomLiveBadge()
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = hostDisplayName ?: "Anfitrión",
                        color = VoiceRoomPalette.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Contador de personas en cápsula
        Surface(
            onClick = onOpenMembers,
            shape = RoundedCornerShape(20.dp),
            color = VoiceRoomPalette.DarkSurface.copy(alpha = 0.4f),
            tonalElevation = 2.dp,
            modifier = Modifier
                .height(32.dp)
                .padding(end = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Icon(
                    Icons.Default.Group,
                    contentDescription = null,
                    tint = VoiceRoomPalette.TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = memberCount.toString(),
                    color = VoiceRoomPalette.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Controles de sala
        if (showRequestsBadge) {
            IconButton(onClick = onOpenRequests, modifier = Modifier.size(36.dp)) {
                Box {
                    Icon(
                        Icons.Default.People,
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
                    Icons.Default.People,
                    contentDescription = "Invitar",
                    tint = VoiceRoomPalette.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        onOpenSettings?.let { settings ->
            IconButton(onClick = settings, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Configuración",
                    tint = VoiceRoomPalette.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Salir",
                tint = VoiceRoomPalette.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// === Live badge — pequeño, minimal ===

@Composable
fun VoiceRoomLiveBadge() {
    val transition = rememberInfiniteTransition(label = "liveBadge")
    val dotAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "dotAlpha"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = dotAlpha))
        )
        Text("LIVE", color = VoiceRoomPalette.RedLive, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

// === Speaking aura — aurora + ondas sonar saliendo del avatar (premium) ===

/** Relación slot/marco usada para el layout de sillones: el slot cuadrado es un
 *  poco mayor que el marco del avatar (overflowScale 1.72) para que todos los
 *  asientos tengan tamaño fijo sin importar qué marco/código tengan. Se mantiene
 *  ajustado (1.75) para que el nombre del perfil quede pegado al avatar sin que
 *  el colgante lo tape. */
val SeatSlotScale: Float = 1.75f
private const val DEG = 0.0174532925f

@Composable
fun VoiceRoomSpeakingAura(
    speaking: Boolean,
    avatarSize: Dp,
    modifier: Modifier = Modifier,
    color: Color = VoiceRoomPalette.ActiveCyan
) {
    if (!speaking) return

    val slot = avatarSize * SeatSlotScale
    val transition = rememberInfiniteTransition(label = "speakingAura")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "phase"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(640, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Canvas(modifier = modifier.size(slot)) {
        val d = size.minDimension
        if (d <= 0f) return@Canvas
        val c = center
        val r0 = d / 2f / SeatSlotScale          // radio del avatar
        val outer = d / 2f                       // borde del slot
        val ringRange = outer - r0

        // Halo aurora pulsante alrededor del avatar (radial, no tapa la cara).
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = 0.28f * pulse),
                    color.copy(alpha = 0.10f * pulse),
                    Color.Transparent
                ),
                center = c,
                radius = outer
            ),
            radius = outer,
            center = c
        )

        // Ondas sonar que se expanden desde el borde del avatar hacia afuera.
        for (i in 0..1) {
            val k = (phase * 1.6f + i * 0.5f) % 1f
            val r = r0 + ringRange * k
            val alpha = (1f - k) * 0.75f
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = r,
                center = c,
                style = Stroke(width = 1.5f + 2.5f * (1f - k), cap = StrokeCap.Round)
            )
        }

        // Arco rotatorio con gradiente (efecto 3D).
        val sweepStart = (phase * 720f)
        drawArc(
            color = color.copy(alpha = 0.85f * pulse),
            startAngle = sweepStart,
            sweepAngle = 55f,
            useCenter = false,
            topLeft = Offset(c.x - (r0 + ringRange * 0.30f), c.y - (r0 + ringRange * 0.30f)),
            size = Size((r0 + ringRange * 0.30f) * 2f, (r0 + ringRange * 0.30f) * 2f),
            style = Stroke(width = 2.2f, cap = StrokeCap.Round)
        )

        // Partículas orbitando.
        val n = 4
        for (i in 0 until n) {
            val ang = phase * 360f * 1.7f + i * (360f / n)
            val rad = r0 + ringRange * (0.35f + 0.22f * ((phase * 3f + i) % 1f))
            val px = c.x + cos(ang * DEG) * rad
            val py = c.y + sin(ang * DEG) * rad
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = 1.6f,
                center = Offset(px, py)
            )
        }
    }
}

// === VoiceRoomAudioVisualizer ===

@Composable
fun VoiceRoomAudioVisualizer(
    modifier: Modifier = Modifier,
    barCount: Int = 4,
    levels: List<Float>? = null
) {
    val idle = levels == null || levels.isEmpty()
    val displayLevels = if (idle) List(barCount) { 0f }
    else levels.take(barCount).let {
        if (it.size < barCount) it + List(barCount - it.size) { 0f } else it
    }
    val animatedHeights = remember(displayLevels) {
        displayLevels.map { Animatable(4f) }.toMutableStateList()
    }

    LaunchedEffect(displayLevels) {
        displayLevels.forEachIndexed { idx, level ->
            val targetHeight = (4f + level.coerceIn(0f, 1f) * 12f).coerceAtLeast(4f)
            animatedHeights.getOrNull(idx)?.animateTo(targetHeight, tween(120))
        }
    }

    Row(
        modifier = modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally)
    ) {
        displayLevels.forEachIndexed { idx, level ->
            val fraction = level.coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((animatedHeights.getOrNull(idx)?.value ?: 4f).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (fraction > 0.05f) VoiceRoomPalette.ActiveCyan else VoiceRoomPalette.ActiveCyan.copy(alpha = 0.25f))
            )
        }
    }
}

// === VoiceRoomStageSeat — Host con avatar real + badge "Anfitrión" separado ===

@Composable
fun VoiceRoomStageSeat(
    seat: VoiceRoomSeat?,
    size: Dp,
    isHost: Boolean = false,
    isMine: Boolean = false,
    showAdminAction: Boolean = false,
    onClick: () -> Unit,
    onAdmin: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val displayName = when {
        seat?.isOccupied != true -> null
        isMine -> "Tú"
        else -> seat.displayName?.takeIf { !it.isNullOrBlank() }
    }
    val avatarUrl = seat?.avatarUrl

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            VoiceRoomSeatCircle(
                seat = seat,
                size = size,
                avatarUrl = avatarUrl,
                displayName = displayName,
                isHost = isHost,
                showAdminCog = showAdminAction && seat?.isOccupied == true && !isMine,
                onClick = onClick,
                onAdmin = onAdmin
            )
            // Badge "Anfitrión" — siempre como overlay, NUNCA como nombre
            if (isHost && seat?.isOccupied == true) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-size.value * 0.1f).dp)
                        .clip(CircleShape)
                        .background(VoiceRoomPalette.Gold)
                        .padding(horizontal = 5.dp, vertical = 1.5.dp)
                ) {
                    Text("Anfitrión", color = VoiceRoomPalette.DeepBlue, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = displayName ?: "",
            color = if (seat?.isOccupied == true) VoiceRoomPalette.TextPrimary else VoiceRoomPalette.TextSecondary,
            fontSize = if (isHost) 11.sp else 10.sp,
            fontWeight = if (isMine) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VoiceRoomSeatCircle(
    seat: VoiceRoomSeat?,
    size: Dp,
    avatarUrl: String?,
    displayName: String?,
    isHost: Boolean,
    showAdminCog: Boolean,
    onClick: () -> Unit,
    onAdmin: () -> Unit
) {
    val speaking = seat?.isSpeaking == true
    val occupied = seat?.isOccupied == true
    val isMuted = seat?.isMuted == true

    val circleBg = if (occupied) VoiceRoomPalette.DarkSurface.copy(alpha = 0.5f) else VoiceRoomPalette.SurfaceBlue.copy(alpha = 0.4f)
    val displaySize = size

    Box(
        modifier = Modifier
            .size(displaySize)
            .clip(CircleShape)
            .background(circleBg)
            .border(
                if (speaking) 2.dp else 1.dp,
                if (speaking) VoiceRoomPalette.ActiveCyan else Color(0x1AFFFFFF),
                CircleShape
            )
            .shadow(if (occupied) 8.dp else 2.dp, CircleShape, clip = false)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (occupied) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Avatar de ${displayName ?: "usuario"}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
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
                VoiceRoomSpeakingAura(
                    speaking = true,
                    avatarSize = size,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else {
            Icon(
                Icons.Default.Chair,
                contentDescription = "Sillón libre",
                tint = VoiceRoomPalette.TextSecondary,
                modifier = Modifier.size(size * 0.4f)
            )
        }

        if (seat?.isMuteBadgeVisible() == true) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size((size.value * 0.3f).dp)
                    .clip(CircleShape)
                    .background(VoiceRoomPalette.RedLive),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MicOff,
                    contentDescription = "Silenciado",
                    tint = Color.White,
                    modifier = Modifier.size((size.value * 0.18f).dp)
                )
            }
        }

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
                    Icons.Default.Settings,
                    contentDescription = "Administrar",
                    tint = VoiceRoomPalette.DeepBlue,
                    modifier = Modifier.size((size.value * 0.18f).dp)
                )
            }
        }
    }
}

private fun VoiceRoomSeat?.isMuteBadgeVisible(): Boolean =
    this != null && this.isOccupied && this.isMuted

// === Chat — burbujas compactas, scroll inteligente (único owner) ===

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

    // Smart scroll con semántica correcta para reverseLayout:
    // - visibleItemsInfo.first() = elemento visible más ARRIBA
    // - visibleItemsInfo.last()  = elemento visible más ABAJO = índice 0 en reverseLayout
    // - Si el usuario está en la parte inferior (cerca de índice 0), auto-scroll
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

    // Autoscroll cuando llegan nuevos mensajes y el usuario está en la parte inferior
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty() && shouldAutoScroll.value) {
            listState.animateScrollToItem(0)
        }
    }

    Box(modifier = modifier) {
        if (chatMessages.isEmpty() && systemMessages.isEmpty()) {
            Text(
                text = "Aún no hay mensajes. ¡Escribe algo! 👋",
                color = VoiceRoomPalette.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        ) {
            items(chatMessages, key = { it.id }) { message ->
                val member = memberById[message.senderId]
                val displayName = member?.displayName
                    ?: message.senderName
                    ?: "(sin nombre)"
                val avatarUrl = member?.avatarUrl
                VoiceRoomChatMessage(
                    message = message,
                    avatarUrl = avatarUrl,
                    senderName = displayName,
                    onOpenProfile = onOpenProfile
                )
            }
        }
        // System bubbles overlay (fixed at top in reverse layout = bottom visually)
        if (systemMessages.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(0.8f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, VoiceRoomPalette.DeepBlue.copy(alpha = 0.9f))
                        )
                    )
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                systemMessages.takeLast(3).forEach { msg ->
                    VoiceRoomSystemBubble(message = msg)
                }
            }
        }
    }
}

@Composable
private fun VoiceRoomChatMessage(
    message: VoiceRoomMessage,
    avatarUrl: String?,
    senderName: String?,
    onOpenProfile: ((String) -> Unit)? = null
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
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
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
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x14FFFFFF))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.Start
        ) {
            if (!senderName.isNullOrBlank() && senderName != "(sin nombre)") {
                Text(
                    text = senderName,
                    color = VoiceRoomPalette.ActiveCyan,
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
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun VoiceRoomSystemBubble(message: VoiceRoomMessage, modifier: Modifier = Modifier) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.3f) }
    LaunchedEffect(message.id) {
        launch { alpha.animateTo(1f, tween(300)) }
        launch { scale.animateTo(1f, tween(300, easing = LinearOutSlowInEasing)) }
    }
    Box(
        modifier = modifier.graphicsLayer(scaleX = scale.value, scaleY = scale.value, alpha = alpha.value)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = VoiceRoomPalette.ActiveCyan.copy(alpha = 0.85f),
            shadowElevation = 4.dp,
            modifier = Modifier.wrapContentSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("👋", fontSize = 11.sp)
                Text(
                    text = message.content,
                    color = VoiceRoomPalette.DeepBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// === Floating emoji overlay — preservado ===

@Composable
fun VoiceRoomFloatingEmojiOverlay(
    emojis: List<VoiceRoomFloatingEmoji>,
    onDone: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        emojis.forEach { emoji ->
            key(emoji.id) {
                val progress = remember { Animatable(0f) }
                LaunchedEffect(emoji.id) {
                    progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(1400, easing = FastOutSlowInEasing)
                    )
                    onDone(emoji.id)
                }
                Text(
                    text = emoji.emoji,
                    fontSize = 22.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 120.dp)
                        .graphicsLayer(
                            translationX = (emoji.xFraction - 0.5f) * 120f,
                            translationY = -progress.value * 160f,
                            alpha = 1f - progress.value,
                            scaleX = 0.4f + progress.value * 0.8f,
                            scaleY = 0.4f + progress.value * 0.8f
                        )
                )
            }
        }
    }
}

data class VoiceRoomFloatingEmoji(
    val id: Long,
    val emoji: String,
    val xFraction: Float = 0.5f
)

// === Up Next — compacto ===

@Composable
fun VoiceRoomUpNextStrip(
    seats: List<VoiceRoomSeat>,
    members: List<VoiceRoomMember>,
    modifier: Modifier = Modifier
) {
    val occupiedSeats = seats.filter { it.isOccupied }
    val memberById = members.associateBy { it.userId }
    val queue = occupiedSeats.sortedBy {
        memberById[it.userId]?.joinedAt ?: ""
    }
    if (queue.isEmpty()) return

    Box(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            item {
                Text(
                    "🎤 Próximos:",
                    color = VoiceRoomPalette.Gold,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            items(queue.take(6), key = { it.userId ?: it.index.toString() }) { seat ->
                val displayName = seat.displayName?.takeIf { !it.isNullOrBlank() }
                    ?: memberById[seat.userId]?.displayName
                val avatarUrl = seat.avatarUrl ?: memberById[seat.userId]?.avatarUrl
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(VoiceRoomPalette.DarkSurface.copy(alpha = 0.4f))
                        .border(0.5.dp, VoiceRoomPalette.ActiveCyan.copy(alpha = 0.2f), CircleShape)
                ) {
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Text(
                            text = displayName?.take(1)?.uppercase() ?: "?",
                            color = VoiceRoomPalette.ActiveCyan,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = displayName?.take(6) ?: "Usuario",
                    color = VoiceRoomPalette.TextSecondary,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// === Input bar — cápsula flotante ===

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRoomInputBar(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isSeated: Boolean,
    isMuted: Boolean,
    pendingRequest: Boolean,
    needsPermission: Boolean,
    onRequestSeat: () -> Unit,
    onToggleMute: () -> Unit,
    onEnableMic: () -> Unit,
    onReaction: (String) -> Unit = {},
    onOpenProfile: ((String) -> Unit)? = null
) {
    val listenOnly = isSeated && needsPermission
    val mutedByAdmin = isSeated && isMuted && !needsPermission

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (listenOnly || mutedByAdmin) 44.dp else 48.dp),
            shape = RoundedCornerShape(24.dp),
            color = VoiceRoomPalette.DarkSurface.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, VoiceRoomPalette.ActiveCyan.copy(alpha = 0.2f)),
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
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
                if (listenOnly || mutedByAdmin) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (mutedByAdmin) "Silenciado" else "Solo escucha",
                        color = VoiceRoomPalette.TextSecondary,
                        fontSize = 11.sp
                    )
                } else {
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedTextField(
                        value = value,
                        onValueChange = { onValueChange(it.take(2000)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = {
                            Text("Vamos a platicar...", fontSize = 12.sp, color = VoiceRoomPalette.TextSecondary)
                        },
                        textStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = VoiceRoomPalette.TextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VoiceRoomPalette.TextPrimary,
                            unfocusedTextColor = VoiceRoomPalette.TextPrimary,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onSend,
                    enabled = value.isNotBlank() && !listenOnly && !mutedByAdmin,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Enviar",
                        tint = if (value.isNotBlank()) VoiceRoomPalette.ActiveCyan else VoiceRoomPalette.TextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
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
    val icon = if (needsPermission || (isMuted && isSeated)) Icons.Default.MicOff else Icons.Default.Mic
    val tint = when {
        needsPermission -> VoiceRoomPalette.ActiveCyan
        !isSeated -> if (pendingRequest) Color(0xFF888888) else VoiceRoomPalette.ActiveCyan
        isMuted -> Color(0xFFFF8A80)
        else -> VoiceRoomPalette.ActiveCyan
    }
    val enabled = needsPermission || isSeated || !pendingRequest

    val pulseTransition = rememberInfiniteTransition(label = "micPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Restart),
        label = "micPulseAlpha"
    )
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Restart),
        label = "micPulseScale"
    )

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(VoiceRoomPalette.ActiveCyanSoft)
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
                    .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale, alpha = pulseAlpha)
                    .clip(CircleShape)
                    .background(VoiceRoomPalette.ActiveCyan)
            )
        }
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

// === Diálogos y Settings — preservados con nueva paleta ===

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRoomMembersSheet(
    members: List<VoiceRoomMember>,
    myUserId: String,
    onDismiss: () -> Unit,
    onOpenProfile: ((String) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = { scope.launch { sheetState.hide(); onDismiss() } },
        containerColor = VoiceRoomPalette.DeepBlue,
        contentColor = Color.White,
        sheetState = sheetState,
        dragHandle = null,
        tonalElevation = 12.dp,
        modifier = Modifier.navigationBarsPadding()
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Miembros (${members.size})", color = VoiceRoomPalette.TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { scope.launch { sheetState.hide(); onDismiss() } }) {
                    Icon(Icons.Default.Close, "Cerrar", tint = VoiceRoomPalette.TextPrimary)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(members, key = { it.userId }) { member ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(VoiceRoomPalette.DarkSurface.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                            if (!member.avatarUrl.isNullOrBlank()) {
                                AsyncImage(model = member.avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                            } else {
                                Text(text = member.displayName?.take(1)?.uppercase() ?: "👤", color = VoiceRoomPalette.ActiveCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(text = member.displayName ?: "Usuario", color = VoiceRoomPalette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text = voiceRoomRoleLabel(member.role), color = if (member.role == "owner" || member.role == "admin") VoiceRoomPalette.Gold else VoiceRoomPalette.ActiveCyan, fontSize = 11.sp)
                        }
                        if (member.userId != myUserId && onOpenProfile != null) {
                            TextButton(onClick = { onOpenProfile(member.userId) }) {
                                Text("Ver", color = VoiceRoomPalette.TextSecondary, fontSize = 11.sp)
                            }
                        }
                        HorizontalDivider(color = Color(0x1FFFFFFF))
                    }
                }
            }
        }
    }
}

fun voiceRoomRoleLabel(role: String): String = when (role) {
    "owner" -> "👑 Anfitrión"
    "admin" -> "⚙ Admin"
    "speaker" -> "🎤 Hablando"
    else -> "👂 Oyente"
}

@Composable
fun VoiceRoomAnimatedDialog(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    text: @Composable (() -> Unit)? = null,
    confirmText: String,
    dangerConfirm: Boolean = false,
    onConfirm: () -> Unit,
    dismissText: String? = null,
    onDismissClick: (() -> Unit)? = null,
    containerColor: Color = VoiceRoomPalette.DeepBlue,
    content: (@Composable () -> Unit)? = null
) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.92f) }
    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(200)) }
        launch { scale.animateTo(1f, tween(200, easing = LinearOutSlowInEasing)) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = VoiceRoomPalette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
        text = text ?: content,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (dangerConfirm) Color(0xFFFF6E6E) else VoiceRoomPalette.ActiveCyan, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = if (dismissText != null && onDismissClick != null) {
            { TextButton(onClick = onDismissClick) { Text(dismissText, color = VoiceRoomPalette.TextPrimary) } }
        } else null,
        containerColor = containerColor,
        modifier = modifier.graphicsLayer(scaleX = scale.value, scaleY = scale.value, alpha = alpha.value)
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

    val categories = listOf(
        "general" to "General", "chat" to "Charlar", "meeting" to "Reunión",
        "work" to "Trabajo", "dating" to "Enamorados", "friends" to "Conocer gente",
        "music" to "Música", "gaming" to "Gaming"
    )
    val categoryLabel = categories.firstOrNull { it.first == category }?.second ?: "General"

    ModalBottomSheet(onDismissRequest = { onClose() }, containerColor = VoiceRoomPalette.DeepBlue, contentColor = Color.White, tonalElevation = 16.dp, modifier = Modifier.navigationBarsPadding()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, null, tint = VoiceRoomPalette.ActiveCyan, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Configuración de la sala", color = VoiceRoomPalette.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cerrar", tint = VoiceRoomPalette.TextPrimary) }
            }
            Spacer(modifier = Modifier.height(6.dp))
            message?.let {
                Surface(color = VoiceRoomPalette.ActiveCyan.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
                    Text(it, color = VoiceRoomPalette.ActiveCyan, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (!isHost) {
                Text("Solo el anfitrión puede editar la configuración de la sala.", color = VoiceRoomPalette.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
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
                    Column(Modifier.padding(vertical = 6.dp)) {
                        OutlinedTextField(
                            value = when (editingField) { "name" -> name; "description" -> description; else -> coverUrl },
                            onValueChange = { v -> when (editingField) { "name" -> name = v.take(80); "description" -> description = v.take(280); else -> coverUrl = v.take(500) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = editingField != "description",
                            minLines = if (editingField == "description") 2 else 1,
                            maxLines = if (editingField == "description") 4 else 1,
                            placeholder = { Text(if (editingField == "cover") "https://..." else if (editingField == "description") "Describe tu sala" else "Nombre de la sala") },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VoiceRoomPalette.ActiveCyan, unfocusedBorderColor = Color(0xFF3E3E44), focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { editingField = null }) { Text("Listo", color = VoiceRoomPalette.ActiveCyan) }
                        }
                    }
                }
            }
            Button(onClick = { onSaveSettings(name, description, coverUrl.ifBlank { null }, category, visibility, isLocked) }, enabled = !isSaving, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = VoiceRoomPalette.ActiveCyan, contentColor = VoiceRoomPalette.DeepBlue), shape = RoundedCornerShape(16.dp)) {
                Text(if (isSaving) "Guardando..." else "Guardar cambios", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            SettingsSectionTitle("Miembros y administración", "👥")
            SettingsCard {
                SettingsRow("Miembros (${members.size})", "") { showMembersTab = true }
                SettingsDivider()
                SettingsRow("Baneados (${bannedUsers.size})", "") { showBannedTab = true }
            }
            if (isHost) {
                Spacer(modifier = Modifier.height(10.dp))
                SettingsSectionTitle("Zona peligrosa", "⚠️")
                SettingsCard { SettingsRow("Borrar sala", "Esta acción es permanente", danger = true) { showDeleteConfirm = true } }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showDeleteConfirm) {
        VoiceRoomAnimatedDialog(
            onDismiss = { showDeleteConfirm = false },
            title = "Eliminar sala",
            text = { Text("¿Seguro que quieres borrar esta sala para siempre? Se eliminarán sillones, mensajes, solicitudes y miembros.", color = VoiceRoomPalette.TextSecondary) },
            confirmText = "Eliminar",
            dangerConfirm = true,
            onConfirm = { showDeleteConfirm = false; onDeleteRoom() },
            dismissText = "Cancelar",
            onDismissClick = { showDeleteConfirm = false },
            containerColor = VoiceRoomPalette.DeepBlue
        )
    }

    if (showMembersTab) {
        VoiceRoomAnimatedDialog(onDismiss = { showMembersTab = false }, title = "Miembros de la sala", confirmText = "Cerrar", onConfirm = { showMembersTab = false }, containerColor = VoiceRoomPalette.DeepBlue) {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 420.dp)) {
                members.forEach { member ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(VoiceRoomPalette.DarkSurface.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                            if (!member.avatarUrl.isNullOrBlank()) {
                                AsyncImage(model = member.avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                            } else {
                                Text(text = member.displayName?.take(1) ?: "?", color = VoiceRoomPalette.TextPrimary, fontSize = 14.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(text = member.displayName ?: "Usuario", color = VoiceRoomPalette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(text = voiceRoomRoleLabel(member.role), color = if (member.role == "owner" || member.role == "admin") VoiceRoomPalette.Gold else VoiceRoomPalette.ActiveCyan, fontSize = 11.sp)
                        }
                        if (member.userId != myUserId && member.userId != room?.ownerId) {
                            if (isHost && member.role != "admin") TextButton(onClick = { onSetAdmin(member.userId, true) }) { Text("Hacer admin", color = VoiceRoomPalette.ActiveCyan, fontSize = 11.sp) }
                            if (isHost && member.role == "admin") TextButton(onClick = { onSetAdmin(member.userId, false) }) { Text("Quitar admin", color = VoiceRoomPalette.Gold, fontSize = 11.sp) }
                            TextButton(onClick = { onKick(member.userId) }) { Text("Expulsar", color = Color(0xFFFF8A80), fontSize = 11.sp) }
                        }
                        onOpenProfile?.let { TextButton(onClick = { it(member.userId) }) { Text("Ver", color = VoiceRoomPalette.TextSecondary, fontSize = 11.sp) } }
                        HorizontalDivider(color = Color(0x1FFFFFFF))
                    }
                }
            }
        }
    }

    if (showBannedTab) {
        VoiceRoomAnimatedDialog(onDismiss = { showBannedTab = false }, title = "Usuarios baneados", confirmText = "Cerrar", onConfirm = { showBannedTab = false }, containerColor = VoiceRoomPalette.DeepBlue) {
            if (bannedUsers.isEmpty()) {
                Text("No hay usuarios baneados.", color = VoiceRoomPalette.TextSecondary)
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 420.dp)) {
                    bannedUsers.forEach { ban ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(VoiceRoomPalette.DarkSurface.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                                Text(text = ban.displayName.take(1).ifBlank { "?" }, color = VoiceRoomPalette.TextPrimary, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(text = ban.displayName.ifBlank { "Usuario baneado" }, color = VoiceRoomPalette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(text = ban.reason ?: "Sin motivo", color = VoiceRoomPalette.TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(onClick = { onRemoveBan(ban.userId) }) { Text("Desbanear", color = VoiceRoomPalette.ActiveCyan, fontSize = 11.sp) }
                            HorizontalDivider(color = Color(0x1FFFFFFF))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String, emoji: String) {
    Text("$emoji $title", color = VoiceRoomPalette.TextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = VoiceRoomPalette.DarkSurface.copy(alpha = 0.4f), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) { Column(content = content) }
}

@Composable
fun SettingsDivider() { HorizontalDivider(color = Color(0x1AFFFFFF), modifier = Modifier.padding(horizontal = 14.dp)) }

@Composable
fun SettingsRow(title: String, subtitle: String = "", danger: Boolean = false, onClick: (() -> Unit)? = null) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick!!) else Modifier
    Row(Modifier.fillMaxWidth().then(clickModifier).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text = title, color = if (danger) Color(0xFFFF6E6E) else VoiceRoomPalette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) { Text(text = subtitle, color = VoiceRoomPalette.TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (danger) Text("⚠️", fontSize = 14.sp) else if (onClick != null) Icon(Icons.Default.Check, null, tint = VoiceRoomPalette.TextSecondary, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun SettingsToggleRow(title: String, subtitle: String = "", checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text = title, color = VoiceRoomPalette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) { Text(text = subtitle, color = VoiceRoomPalette.TextSecondary, fontSize = 11.sp) }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = VoiceRoomPalette.ActiveCyan, checkedTrackColor = VoiceRoomPalette.ActiveCyan.copy(alpha = 0.35f), uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFF3E3E44)))
    }
}
