package com.example.live.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
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
import com.example.live.domain.model.LiveStream
import com.example.live.domain.repository.LiveRoomRepository
import com.example.live.ui.components.LiveVideoSurface
import com.example.live.ui.viewmodel.LiveGuestViewModel
import com.example.live.ui.viewmodel.LiveViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveGuestScreen(
    liveId: String,
    onNavigateBack: () -> Unit,
    viewModel: LiveViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    guestViewModel: LiveGuestViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    repository: LiveRoomRepository? = null
) {
    val context = LocalContext.current
    // LiveKitManager único durante toda la pantalla (ver LiveBroadcastScreen).
    val roomRepository: LiveRoomRepository = repository ?: remember { LiveRoomRepositoryImpl(context) }
    val connectionState by roomRepository.connectionState.collectAsStateWithLifecycle()
    val videoTrack by roomRepository.remoteVideoTrack.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var liveStream by remember { mutableStateOf<LiveStream?>(null) }
    var hasAccepted by remember { mutableStateOf(false) }
    var isMicMuted by remember { mutableStateOf(false) }
    var isCameraOff by remember { mutableStateOf(false) }

    val currentUserId = SupabaseClient.currentUser?.id ?: ""

    LaunchedEffect(liveId) {
        scope.launch {
            val stream = viewModel.getLiveStream(liveId)
            liveStream = stream
        }
    }

    fun leaveCoHost() {
        scope.launch(Dispatchers.IO) {
            try {
                guestViewModel.leaveLive(liveId)
            } catch (_: Exception) {}
            roomRepository.leaveRoom()
        }
        onNavigateBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            roomRepository.leaveRoom()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Co-Host Invitado", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { leaveCoHost() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF161618),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF161618)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (!hasAccepted) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "¡Has sido invitado como Co-Host a este Live!",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val accepted = guestViewModel.acceptInvitation(liveId, currentUserId)
                                    if (accepted.isFailure) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "No se pudo aceptar la invitación. Puede haber expirado.",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                        return@launch
                                    }
                                    hasAccepted = true
                                    liveStream?.let { stream ->
                                        val tokenResult = viewModel.getLiveToken(stream.roomName, currentUserId, "publisher")
                                        if (tokenResult.isSuccess) {
                                            val tokenRes = tokenResult.getOrThrow()
                                            roomRepository.startBroadcast(tokenRes.serverUrl, tokenRes.token)
                                        } else {
                                            android.widget.Toast.makeText(
                                                context,
                                                "No se pudo conectar al directo.",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A884))
                        ) {
                            Text("Aceptar Invitación", color = Color.White)
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    guestViewModel.rejectInvitation(liveId, currentUserId)
                                }
                                onNavigateBack()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350))
                        ) {
                            Text("Rechazar")
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    LiveVideoSurface(
                        videoTrack = videoTrack,
                        modifier = Modifier.fillMaxSize()
                    )

                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF00A884)
                    ) {
                        Text(
                            text = "● CO-HOST EN VIVO",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                isMicMuted = !isMicMuted
                                scope.launch { roomRepository.setMicrophoneEnabled(!isMicMuted) }
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = if (isMicMuted) Color(0xFFEF5350) else Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Micrófono",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                isCameraOff = !isCameraOff
                                scope.launch { roomRepository.setCameraEnabled(!isCameraOff) }
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = if (isCameraOff) Color(0xFFEF5350) else Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = if (isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                                contentDescription = "Cámara",
                                tint = Color.White
                            )
                        }

                        Button(
                            onClick = { leaveCoHost() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                        ) {
                            Text("SALIR", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
