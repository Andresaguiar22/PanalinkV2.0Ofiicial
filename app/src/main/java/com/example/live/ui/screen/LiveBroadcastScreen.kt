package com.example.live.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Videocam
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
import androidx.core.content.ContextCompat
import com.example.data.supabase.SupabaseClient
import com.example.live.data.repository.LiveRoomRepositoryImpl
import com.example.live.domain.model.LiveConnectionState
import com.example.live.domain.model.LiveStream
import com.example.live.domain.repository.LiveRoomRepository
import com.example.live.ui.components.LiveBroadcastControls
import com.example.live.ui.components.LiveGuestControls
import com.example.live.ui.components.LiveVideoSurface
import com.example.live.ui.components.LiveViewerComments
import com.example.live.ui.viewmodel.LiveGuestViewModel
import com.example.live.ui.viewmodel.LiveViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveBroadcastScreen(
    onNavigateBack: () -> Unit,
    viewModel: LiveViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    guestViewModel: LiveGuestViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    repository: LiveRoomRepository = LiveRoomRepositoryImpl(LocalContext.current)
) {
    val context = LocalContext.current
    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions[Manifest.permission.CAMERA] == true &&
            permissions[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    val connectionState by repository.connectionState.collectAsStateWithLifecycle()
    val localVideoTrack by repository.localVideoTrack.collectAsStateWithLifecycle()
    val remoteVideoTrack by repository.remoteVideoTrack.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val viewerCount by viewModel.viewerCount.collectAsStateWithLifecycle()
    val guests by guestViewModel.guests.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var titleText by remember { mutableStateOf("Mi Transmisión en Vivo") }
    var descriptionText by remember { mutableStateOf("¡Acompañame en este directo!") }
    var activeStream by remember { mutableStateOf<LiveStream?>(null) }
    var isLiveStarted by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isStarting by remember { mutableStateOf(false) }
    var showEndConfirmation by remember { mutableStateOf(false) }

    var isMicMuted by remember { mutableStateOf(false) }
    var isCameraOff by remember { mutableStateOf(false) }

    fun stopAndFinish() {
        scope.launch(Dispatchers.IO) {
            try {
                activeStream?.let { stream -> viewModel.endLive(stream.id) }
            } catch (_: Exception) {}
            repository.leaveRoom()
            viewModel.stopStreamSession()
            guestViewModel.stopRealtime()
        }
        onNavigateBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            activeStream?.let { stream ->
                scope.launch(Dispatchers.IO) {
                    try { viewModel.endLive(stream.id) } catch (_: Exception) {}
                }
            }
            repository.leaveRoom()
            viewModel.stopStreamSession()
            guestViewModel.stopRealtime()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transmitir en Vivo", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { showEndConfirmation = true }) {
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
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (!hasPermissions) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Se requieren permisos de Cámara y Micrófono para transmitir",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A884))
                    ) { Text("Conceder Permisos", color = Color.White) }
                }
            } else if (!isLiveStarted) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color(0xFF00A884),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        label = { Text("Título de la transmisión", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00A884),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = descriptionText,
                        onValueChange = { descriptionText = it },
                        label = { Text("Descripción (opcional)", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00A884),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = errorMessage!!, color = Color(0xFFEF5350), fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = {
                            if (isStarting) return@Button
                            isStarting = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    val streamResult = viewModel.createAndStartLive(titleText, descriptionText)
                                    if (streamResult.isSuccess) {
                                        val stream = streamResult.getOrThrow()
                                        activeStream = stream
                                        viewModel.loadComments(stream.id)
                                        viewModel.startStreamSession(stream.id)
                                        guestViewModel.loadGuests(stream.id)
                                        guestViewModel.startRealtime(stream.id)

                                        val userId = SupabaseClient.currentUser?.id ?: "host_${System.currentTimeMillis()}"
                                        val tokenResult = viewModel.getLiveToken(stream.roomName, userId, "publisher")
                                        if (tokenResult.isSuccess) {
                                            val tokenRes = tokenResult.getOrThrow()
                                            repository.startBroadcast(tokenRes.serverUrl, tokenRes.token)
                                            if (repository.localVideoTrack.value != null) {
                                                isLiveStarted = true
                                            } else {
                                                errorMessage = "LiveKit no pudo obtener la cámara local"
                                                repository.leaveRoom()
                                                viewModel.endLive(stream.id)
                                            }
                                        } else {
                                            errorMessage = tokenResult.exceptionOrNull()?.message ?: "Error al obtener token de LiveKit"
                                            viewModel.endLive(stream.id)
                                        }
                                    } else {
                                        errorMessage = streamResult.exceptionOrNull()?.message ?: "Error al crear transmisión"
                                    }
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "Error desconocido"
                                } finally {
                                    isStarting = false
                                }
                            }
                        },
                        enabled = !isStarting && titleText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A884)),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(25.dp)
                    ) {
                        if (isStarting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text("INICIAR TRANSMISIÓN EN VIVO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        // IMPORTANT: this track comes from the same LiveKitManager that
                        // publishes the broadcast; there is no second disconnected manager.
                        LiveVideoSurface(
                            videoTrack = localVideoTrack,
                            initRenderer = repository::initVideoRenderer,
                            modifier = Modifier.size(240.dp, 320.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when (connectionState) {
                                is LiveConnectionState.Connected -> "Transmitiendo en Vivo (Cámara y Micrófono activos)"
                                is LiveConnectionState.Connecting -> "Iniciando transmisión..."
                                is LiveConnectionState.Reconnecting -> "Reconectando..."
                                is LiveConnectionState.Error -> "Error de conexión"
                                else -> "Transmisión activa"
                            },
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFEF5350),
                                modifier = Modifier.size(8.dp)
                            ) {}
                            Text("EN VIVO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("👁 $viewerCount", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        }
                    }

                    if (remoteVideoTrack != null) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).width(140.dp).height(200.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black
                        ) {
                            LiveVideoSurface(
                                videoTrack = remoteVideoTrack,
                                initRenderer = repository::initVideoRenderer,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Box(
                        modifier = Modifier.align(Alignment.TopStart).padding(top = 16.dp, start = 16.dp)
                    ) {
                        activeStream?.let { stream ->
                            LiveGuestControls(
                                guests = guests,
                                onInvite = { userId -> guestViewModel.inviteGuest(stream.id, userId) },
                                onRemove = { userId -> guestViewModel.removeGuest(stream.id, userId) }
                            )
                        }
                    }

                    Box(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    ) {
                        Column {
                            LiveViewerComments(
                                comments = comments,
                                onSendComment = { text -> activeStream?.let { viewModel.postComment(it.id, text) } },
                                isBroadcaster = true,
                                onDeleteComment = { commentId -> viewModel.deleteComment(commentId) },
                                onBlockUser = { userId -> activeStream?.let { viewModel.blockUser(it.id, userId) } },
                                modifier = Modifier.fillMaxWidth()
                            )

                            LiveBroadcastControls(
                                isMicMuted = isMicMuted,
                                isCameraOff = isCameraOff,
                                onToggleMic = {
                                    isMicMuted = !isMicMuted
                                    scope.launch { repository.setMicrophoneEnabled(!isMicMuted) }
                                },
                                onToggleCamera = {
                                    isCameraOff = !isCameraOff
                                    scope.launch { repository.setCameraEnabled(!isCameraOff) }
                                },
                                onSwitchCamera = { scope.launch { repository.switchCamera() } },
                                onEndLive = { showEndConfirmation = true }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEndConfirmation) {
        AlertDialog(
            onDismissRequest = { showEndConfirmation = false },
            title = { Text("Finalizar Transmisión") },
            text = { Text("¿Estás seguro de que deseas finalizar este Live? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { showEndConfirmation = false; stopAndFinish() }) {
                    Text("Finalizar", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirmation = false }) {
                    Text("Cancelar", color = Color.White)
                }
            },
            containerColor = Color(0xFF161618),
            titleContentColor = Color.White,
            textContentColor = Color.Gray
        )
    }
}
