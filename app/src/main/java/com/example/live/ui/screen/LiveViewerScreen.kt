package com.example.live.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.supabase.SupabaseClient
import com.example.live.data.repository.LiveRoomRepositoryImpl
import com.example.live.domain.model.LiveConnectionState
import com.example.live.domain.model.LiveStream
import com.example.live.domain.repository.LiveRoomRepository
import com.example.live.ui.components.*
import com.example.live.ui.viewmodel.LiveViewModel
import kotlinx.coroutines.launch

@Composable
fun LiveViewerScreen(
    liveId: String,
    onNavigateBack: () -> Unit,
    viewModel: LiveViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    repository: LiveRoomRepository = LiveRoomRepositoryImpl(LocalContext.current)
) {
    val connectionState by repository.connectionState.collectAsStateWithLifecycle()
    val videoTrack by repository.remoteVideoTrack.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val viewerCount by viewModel.viewerCount.collectAsStateWithLifecycle()
    val streamEnded by viewModel.streamEnded.collectAsStateWithLifecycle()
    val reactionTrigger by viewModel.reactionEvents.collectAsStateWithLifecycle(initialValue = Unit)
    val scope = rememberCoroutineScope()

    var liveStream by remember { mutableStateOf<LiveStream?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                    repository.joinRoom(result.serverUrl, result.token)
                } else {
                    errorMessage = tokenResult.exceptionOrNull()?.message ?: "Error al obtener token de LiveKit"
                }
            } else {
                errorMessage = "Transmisión no disponible o finalizada"
            }
        }
    }

    LaunchedEffect(streamEnded) {
        if (streamEnded) {
            repository.leaveRoom()
            viewModel.stopStreamSession()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopStreamSession()
            repository.leaveRoom()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LiveVideoSurface(
            videoTrack = videoTrack,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Cabecera (Alignment.TopCenter)
        LiveViewerHeader(
            liveStream = liveStream,
            viewerCount = viewerCount,
            onClose = {
                viewModel.stopStreamSession()
                repository.leaveRoom()
                onNavigateBack()
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 3. Columna de comentarios (inferior izquierda)
        LiveViewerComments(
            comments = comments,
            onSendComment = { text -> viewModel.postComment(liveId, text) },
            isBroadcaster = false,
            onDeleteComment = { /* No aplica en visor */ },
            onBlockUser = { /* No aplica en visor */ },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 68.dp)
                .widthIn(max = 280.dp)
                .heightIn(max = 240.dp)
        )

        // 4. Corazones flotantes (inferior derecha)
        LiveFloatingHeartsOverlay(
            trigger = reactionTrigger,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 72.dp)
        )

        // 5. Barra de herramientas inferior
        LiveViewerBottomBar(
            onOpenInput = { /* Implementar despliegue de teclado */ },
            onGift = { viewModel.sendReaction(liveId) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        )
    }
}
