package com.example.live.ui.screen

import android.util.Log

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
import androidx.compose.ui.draw.clip
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
import com.example.live.ui.components.*
import com.example.live.ui.viewmodel.LiveGuestViewModel
import com.example.live.ui.viewmodel.LiveViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveBroadcastScreen(
    onNavigateBack: () -> Unit,
    viewModel: LiveViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    guestViewModel: LiveGuestViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    repository: LiveRoomRepository? = null
) {
    val context = LocalContext.current
    // LiveKitManager debe ser único durante toda la pantalla: si se re-crea en
    // cada recomposición, se generan capturadores de cámara huérfanos y el
    // inicio del directo puede fallar ("cámara ocupada").
    val roomRepository: LiveRoomRepository = repository ?: remember { LiveRoomRepositoryImpl(context) }
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

    val connectionState by roomRepository.connectionState.collectAsStateWithLifecycle()
    val localVideoTrack by roomRepository.localVideoTrack.collectAsStateWithLifecycle()
    val remoteVideoTrack by roomRepository.remoteVideoTrack.collectAsStateWithLifecycle()
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
    var liveSetupError by remember { mutableStateOf<String?>(null) }

    var isMicMuted by remember { mutableStateOf(false) }
    var isCameraOff by remember { mutableStateOf(false) }

    var elapsedSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(isLiveStarted) {
        if (isLiveStarted) {
            elapsedSeconds = 0
            while (true) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    fun stopAndFinish() {
        scope.launch(Dispatchers.IO) {
            try {
                activeStream?.let { stream -> viewModel.endLive(stream.id) }
            } catch (_: Exception) {}
            roomRepository.leaveRoom()
            viewModel.stopStreamSession()
            guestViewModel.stopRealtime()
        }
        onNavigateBack()
    }

    /** Conecta a LiveKit (token + conexión + cámara) sin bloquear la UI de live. */
    fun startLiveInBackground(stream: LiveStream) {
        viewModel.loadComments(stream.id)
        viewModel.startStreamSession(stream.id)
        guestViewModel.loadGuests(stream.id)
        guestViewModel.startRealtime(stream.id)
        scope.launch {
            Log.i("LiveStart", "2/4 Obteniendo token LiveKit...")
            val userId = SupabaseClient.currentUser?.id ?: "host_${System.currentTimeMillis()}"
            val tokenResult = viewModel.getLiveToken(stream.roomName, userId, "publisher")
            if (!tokenResult.isSuccess) {
                Log.e("LiveStart", "Error al obtener token LiveKit: ${tokenResult.exceptionOrNull()?.message}")
                liveSetupError = "No se pudo conectar con el servidor de video. Verifica tu conexión."
                return@launch
            }
            val tokenRes = tokenResult.getOrThrow()
            Log.i("LiveStart", "3/4 Conectando a LiveKit SFU... url=${tokenRes.serverUrl}")
            roomRepository.startBroadcast(tokenRes.serverUrl, tokenRes.token)
            if (roomRepository.connectionState.value is LiveConnectionState.Error) {
                val msg = (roomRepository.connectionState.value as? LiveConnectionState.Error)?.message
                    ?: "LiveKit no pudo conectar"
                Log.e("LiveStart", "Error conexión LiveKit: $msg")
                liveSetupError = "Conexión de video fallida. Revisa tu señal."
            } else {
                Log.i("LiveStart", "4/4 Cámara activada (track=${roomRepository.localVideoTrack.value != null}). ¡En vivo!")
                liveSetupError = null
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activeStream?.let { stream ->
                scope.launch(Dispatchers.IO) {
                    try { viewModel.endLive(stream.id) } catch (_: Exception) {}
                }
            }
            roomRepository.leaveRoom()
            viewModel.stopStreamSession()
            guestViewModel.stopRealtime()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isLiveStarted && activeStream != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LivePulseIndicator(isLive = true)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${formatElapsed(elapsedSeconds)} · 👁 $viewerCount",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text("Transmitir en Vivo", fontWeight = FontWeight.Bold)
                    }
                },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
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

                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                    ) {
                        LiveVideoSurface(
                            videoTrack = localVideoTrack,
                            initRenderer = roomRepository::initVideoRenderer,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (localVideoTrack == null) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = Color(0xFF00A884),
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "La cámara se activará al iniciar",
                                        color = Color.Gray,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

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
                                    // 1) SOLO se crea el stream: operación corta que depende de Supabase.
                                    //    Con timeout propio para no quedarnos colgados si la red falla.
                                    val streamResult = withTimeout(10_000L) {
                                        viewModel.createAndStartLive(titleText, descriptionText)
                                    }
                                    if (streamResult.isSuccess) {
                                        val stream = streamResult.getOrThrow()
                                        activeStream = stream
                                        isStarting = false
                                        // 2) Entramos YA a la pantalla de live. La cámara/mic/Conexión
                                        //    LiveKit se resuelven en background (conectLiveInBackground)
                                        //    y se reflejan en el overlay "Conectando..." de la pantalla
                                        //    de live. Nada corta la publicación por un timeout.
                                        isLiveStarted = true
                                        startLiveInBackground(stream)
                                    } else {
                                        errorMessage = streamResult.exceptionOrNull()?.message ?: "Error al crear transmisión"
                                    }
                                } catch (e: Exception) {
                                    errorMessage = if (e is CancellationException) {
                                        "Tiempo de espera agotado al crear el stream. Revisa tu conexión."
                                    } else {
                                        e.message ?: "Error desconocido"
                                    }
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
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LiveVideoSurface(
                        videoTrack = localVideoTrack,
                        initRenderer = roomRepository::initVideoRenderer,
                        modifier = Modifier.fillMaxSize()
                    )

                    LiveConnectionOverlay(
                        connectionState = connectionState,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    liveSetupError?.let { err ->
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 150.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFEF5350).copy(alpha = 0.92f)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = err,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                TextButton(onClick = {
                                    liveSetupError = null
                                    activeStream?.let { startLiveInBackground(it) }
                                }) {
                                    Text("Reintentar conexión", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
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
                            LivePulseIndicator(isLive = true)
                            Text("EN VIVO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("👁 $viewerCount", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                            Text("· ${formatElapsed(elapsedSeconds)}", color = Color.White, fontSize = 12.sp)
                        }
                    }

                    if (remoteVideoTrack != null) {
                        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
                            Surface(
                                modifier = Modifier.width(140.dp).height(200.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = Color.Black
                            ) {
                                LiveVideoSurface(
                                    videoTrack = remoteVideoTrack,
                                    initRenderer = roomRepository::initVideoRenderer,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Black.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        text = "Co-Host",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.align(Alignment.TopStart).padding(top = 16.dp, start = 16.dp)) {
                        activeStream?.let { stream ->
                            LiveGuestControls(
                                guests = guests,
                                onInvite = { userId -> guestViewModel.inviteGuest(stream.id, userId) },
                                onRemove = { userId -> guestViewModel.removeGuest(stream.id, userId) }
                            )
                        }
                    }

                    Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                        Column {
                            LiveViewerComments(
                                comments = comments,
                                onSendComment = { text -> activeStream?.let { viewModel.postComment(it.id, text) } },
                                isBroadcaster = true,
                                onDeleteComment = { commentId -> viewModel.deleteComment(commentId) },
                                onBlockUser = { userId -> activeStream?.let { viewModel.blockUser(it.id, userId) } },
                                hostId = SupabaseClient.currentUser?.id,
                                modifier = Modifier.fillMaxWidth()
                            )

                            LiveBroadcastControls(
                                isMicMuted = isMicMuted,
                                isCameraOff = isCameraOff,
                                elapsedSeconds = elapsedSeconds,
                                onToggleMic = {
                                    isMicMuted = !isMicMuted
                                    scope.launch { roomRepository.setMicrophoneEnabled(!isMicMuted) }
                                },
                                onToggleCamera = {
                                    isCameraOff = !isCameraOff
                                    scope.launch { roomRepository.setCameraEnabled(!isCameraOff) }
                                },
                                onSwitchCamera = { scope.launch { roomRepository.switchCamera() } },
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

private fun formatElapsed(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%d:%02d", m, s)
    }
}
