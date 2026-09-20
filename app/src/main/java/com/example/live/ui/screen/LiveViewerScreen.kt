package com.example.live.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.supabase.SupabaseClient
import com.example.live.data.repository.LiveGuestRepositoryImpl
import com.example.live.data.repository.LiveRoomRepositoryImpl
import com.example.live.domain.model.GuestStatus
import com.example.live.domain.model.LiveGuest
import com.example.live.domain.model.LiveStream
import com.example.live.domain.repository.LiveGuestRepository
import com.example.live.domain.repository.LiveRoomRepository
import com.example.live.ui.components.*
import com.example.live.ui.formatLiveCount
import com.example.live.ui.viewmodel.LiveViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.ui.theme.PanalinkPalette

private enum class LiveSheet { None, Gifts, Requests, Studio, More }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveViewerScreen(
    liveId: String,
    onNavigateBack: () -> Unit,
    viewModel: LiveViewModel = viewModel(),
    repository: LiveRoomRepository? = null
) {
    val context = LocalContext.current
    // LiveKitManager único durante toda la pantalla (ver LiveBroadcastScreen).
    val roomRepository: LiveRoomRepository = repository ?: remember { LiveRoomRepositoryImpl(context) }
    val connectionState by roomRepository.connectionState.collectAsStateWithLifecycle()
    val videoTrack by roomRepository.remoteVideoTrack.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val viewerCount by viewModel.viewerCount.collectAsStateWithLifecycle()
    val streamEnded by viewModel.streamEnded.collectAsStateWithLifecycle()

    val likeCount by viewModel.likeCount.collectAsStateWithLifecycle()
    val giftCoins by viewModel.giftCoins.collectAsStateWithLifecycle()
    val walletBalance by viewModel.walletBalance.collectAsStateWithLifecycle()
    val giftCatalog by viewModel.giftCatalog.collectAsStateWithLifecycle()
    val giftPulse by viewModel.giftPulse.collectAsStateWithLifecycle()
    val giftFeed by viewModel.giftFeed.collectAsStateWithLifecycle()
    val reactionPulse by viewModel.reactionPulse.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val sendingGift by viewModel.sendingGift.collectAsStateWithLifecycle()
    val presentUsers by viewModel.presentUsers.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var liveStream by remember { mutableStateOf<LiveStream?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var sheet by remember { mutableStateOf(LiveSheet.None) }
    var showChatPanel by remember { mutableStateOf(true) }
    var requests by remember { mutableStateOf<List<LiveGuest>?>(null) }

    val guestRepository: LiveGuestRepository = remember {
        LiveGuestRepositoryImpl(context)
    }
    val myId = SupabaseClient.currentUser?.id
    val isBroadcaster = liveStream?.hostId == myId
    val hostName = rememberLiveIdentity(liveStream?.hostId ?: "").displayNameOr(liveStream?.hostId ?: "")

    LaunchedEffect(liveId) {
        scope.launch {
            val stream = viewModel.getLiveStream(liveId)
            liveStream = stream
            if (stream != null && stream.status == "LIVE") {
                viewModel.loadComments(liveId)
                viewModel.startStreamSession(liveId)

                val userId = SupabaseClient.currentUser?.id ?: "viewer_${System.currentTimeMillis()}"
                val tokenResult = viewModel.getLiveToken(stream.roomName, userId, "subscriber")
                if (tokenResult.isSuccess) {
                    val result = tokenResult.getOrThrow()
                    roomRepository.joinRoom(result.serverUrl, result.token)
                } else {
                    errorMessage = tokenResult.exceptionOrNull()?.message ?: "Error al obtener token de LiveKit"
                }
                requests = guestRepository.getGuests(stream.id).getOrNull()
            } else {
                errorMessage = "Transmisión no disponible o finalizada"
            }
        }
    }

    LaunchedEffect(streamEnded) {
        if (streamEnded) {
            roomRepository.leaveRoom()
            viewModel.stopStreamSession()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopStreamSession()
            roomRepository.leaveRoom()
        }
    }

    LaunchedEffect(liveId) {
        while (true) {
            delay(1000)
            elapsedSeconds++
        }
    }

    LaunchedEffect(notice) {
        notice?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearNotice()
        }
    }

    fun close() {
        viewModel.stopStreamSession()
        roomRepository.leaveRoom()
        onNavigateBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(liveId) {
                detectTapGestures(
                    onDoubleTap = { viewModel.tapLike(liveId) },
                    onTap = {}
                )
            }
    ) {
        LiveVideoSurface(
            videoTrack = videoTrack,
            initRenderer = roomRepository::initVideoRenderer,
            modifier = Modifier.fillMaxSize()
        )

        LiveConnectionOverlay(
            connectionState = connectionState,
            modifier = Modifier.align(Alignment.Center)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                    )
                )
        )

        LiveViewerHeader(
            liveStream = liveStream,
            viewerCount = viewerCount,
            likeCount = likeCount,
            elapsedSeconds = elapsedSeconds,
            onClose = { close() },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 6.dp)
        )

        LiveGiftEffectsOverlay(
            pulse = giftPulse,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize()
        )

        AnimatedVisibility(
            visible = showChatPanel,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 72.dp)
                .fillMaxWidth(0.62f)
                .heightIn(max = 250.dp)
        ) {
            if (comments.isNotEmpty()) {
                LiveViewerComments(
                    comments = comments.takeLast(50),
                    onDeleteComment = { viewModel.deleteComment(it) },
                    onBlockUser = { viewModel.blockUser(liveId, it) },
                    isBroadcaster = isBroadcaster,
                    hostId = liveStream?.hostId,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x55222222))
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }

        LiveFloatingHeartsOverlay(
            trigger = reactionPulse,
            burst = 2,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp, bottom = 180.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp, bottom = 150.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            LiveRailHeartButton(
                likeCount = likeCount,
                onTap = { viewModel.tapLike(liveId) }
            )

            LiveRailAction(
                emoji = "🎁",
                label = "Regalos",
                onClick = { sheet = LiveSheet.Gifts }
            )

            LiveRailAction(
                emoji = "👥",
                label = "Ver",
                onClick = { sheet = LiveSheet.Requests }
            )

            LiveRailAction(
                emoji = "📤",
                label = "Compartir",
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Mira mi directo en PanaLink")
                    }
                    context.startActivity(Intent.createChooser(intent, "Compartir directo"))
                }
            )
        }

        LiveViewerBottomBar(
            unreadCount = 0,
            onSendComment = { text -> viewModel.postComment(liveId, text) },
            onOpenStudio = { sheet = LiveSheet.Studio },
            onOpenGifts = { sheet = LiveSheet.Gifts },
            onOpenRequests = { sheet = LiveSheet.Requests },
            onToggleChat = { showChatPanel = !showChatPanel },
            onOpenMore = { sheet = LiveSheet.More },
            requestPending = requests?.any { it.status == GuestStatus.PENDING } == true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 10.dp, vertical = 10.dp)
        )

        AnimatedVisibility(
            visible = streamEnded,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "🔴 La transmisión terminó",
                        color = PanalinkPalette.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(onClick = { close() }) {
                        Text("Salir", color = PanalinkPalette.textPrimary)
                    }
                }
            }
        }

        errorMessage?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xE6222222))) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = message,
                            color = PanalinkPalette.textPrimary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { close() }) {
                            Text("Volver", color = PanalinkPalette.textPrimary)
                        }
                    }
                }
            }
        }
    }

    when (sheet) {
        LiveSheet.Gifts -> LiveGiftSheet(
            gifts = giftCatalog,
            balance = walletBalance,
            sending = sendingGift,
            onDismiss = { sheet = LiveSheet.None },
            onSend = { gift, qty -> viewModel.sendGift(liveId, gift, qty) }
        )

        LiveSheet.Requests -> LiveRequestsSheet(
            isBroadcaster = isBroadcaster,
            guests = requests.orEmpty(),
            myUserId = myId,
            presentUsers = presentUsers,
            onDismiss = { sheet = LiveSheet.None },
            onRequestToJoin = {
                scope.launch {
                    guestRepository.requestToJoin(liveId)
                    requests = guestRepository.getGuests(liveId).getOrNull()
                }
            },
            onLeaveAsGuest = {
                scope.launch {
                    guestRepository.leaveLive(liveId)
                    requests = guestRepository.getGuests(liveId).getOrNull()
                }
            },
            onAccept = { guestId ->
                scope.launch {
                    guestRepository.acceptInvitation(liveId, guestId)
                    requests = guestRepository.getGuests(liveId).getOrNull()
                }
            },
            onReject = { guestId ->
                scope.launch {
                    guestRepository.rejectInvitation(liveId, guestId)
                    requests = guestRepository.getGuests(liveId).getOrNull()
                }
            },
            onRemove = { guestId ->
                scope.launch {
                    guestRepository.removeGuest(liveId, guestId)
                    requests = guestRepository.getGuests(liveId).getOrNull()
                }
            }
        )

        LiveSheet.Studio -> LiveStudioSheet(
            hostId = liveStream?.hostId,
            hostName = hostName,
            viewerCount = viewerCount,
            likeCount = likeCount,
            giftCoins = giftCoins,
            presentUsers = presentUsers,
            myUserId = myId,
            isBroadcaster = isBroadcaster,
            onDismiss = { sheet = LiveSheet.None },
            onStartOwnLive = {
                sheet = LiveSheet.None
                onNavigateBack()
            },
            onRequestCoHost = {
                sheet = LiveSheet.None
                scope.launch {
                    guestRepository.requestToJoin(liveId)
                    Toast.makeText(context, "Solicitud enviada", Toast.LENGTH_SHORT).show()
                }
            }
        )

        LiveSheet.More -> LiveMoreSheet(
            onDismiss = { sheet = LiveSheet.None },
            onShare = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Mira mi directo en PanaLink")
                }
                context.startActivity(Intent.createChooser(intent, "Compartir directo"))
            },
            onCopyLink = {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("live_link", "https://panalink.app/live/$liveId"))
                Toast.makeText(context, "Enlace copiado", Toast.LENGTH_SHORT).show()
            },
            onReport = { reason ->
                viewModel.report(liveId, null, null, reason)
                sheet = LiveSheet.None
            }
        )

        LiveSheet.None -> {}
    }
}

@Composable
private fun LiveRailHeartButton(
    likeCount: Int,
    onTap: () -> Unit
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.width(52.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color(0x66222222))
                .clickable {
                    onTap()
                    scope.launch {
                        scale.snapTo(0.7f)
                        scale.animateTo(1f, tween(220))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "❤️",
                fontSize = 25.sp,
                modifier = Modifier.scale(scale.value)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatLiveCount(likeCount),
            color = PanalinkPalette.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LiveRailAction(
    emoji: String,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(52.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color(0x66222222)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = 21.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = PanalinkPalette.textPrimary,
            fontSize = 10.sp
        )
    }
}