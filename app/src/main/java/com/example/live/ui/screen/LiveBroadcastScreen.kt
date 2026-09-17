package com.example.live.ui.screen

import android.util.Log

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
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
import com.example.live.ui.LiveCardShape
import com.example.live.ui.LiveEndRed
import com.example.live.ui.LiveHudBorder
import com.example.live.ui.components.*
import com.example.live.ui.viewmodel.LiveGuestViewModel
import com.example.live.ui.viewmodel.LiveViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout

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

    // Los comentarios se pueden ocultar desde el HUD inferior para despejar el video.
    var commentsVisible by remember { mutableStateOf(true) }

    // La preview de CameraX y LiveKit no pueden tener la cámara a la vez: cuando arranca
    // el directo esta bandera suelta la preview antes de conectar (si no, "cámara ocupada").
    var cameraPreviewActive by remember { mutableStateOf(true) }

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

    /** Crea el stream y arranca el directo. Suelta antes la preview de CameraX. */
    fun beginBroadcast() {
        if (isStarting) return
        isStarting = true
        errorMessage = null
        scope.launch {
            try {
                cameraPreviewActive = false
                delay(CAMERA_RELEASE_DELAY_MS)

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
                    //    LiveKit se resuelven en background (startLiveInBackground)
                    //    y se reflejan en el overlay "Conectando..." de la pantalla
                    //    de live. Nada corta la publicación por un timeout.
                    isLiveStarted = true
                    startLiveInBackground(stream)
                } else {
                    errorMessage = streamResult.exceptionOrNull()?.message ?: "Error al crear transmisión"
                    cameraPreviewActive = true
                }
            } catch (e: Exception) {
                errorMessage = if (e is CancellationException) {
                    "Tiempo de espera agotado al crear el stream. Revisa tu conexión."
                } else {
                    e.message ?: "Error desconocido"
                }
                cameraPreviewActive = true
            } finally {
                isStarting = false
            }
        }
    }

    if (!isLiveStarted) {
        LiveBroadcastSetup(
            hasPermissions = hasPermissions,
            titleText = titleText,
            onTitleChange = { titleText = it },
            descriptionText = descriptionText,
            onDescriptionChange = { descriptionText = it },
            isStarting = isStarting,
            errorMessage = errorMessage,
            cameraPreviewActive = cameraPreviewActive,
            onBack = { showEndConfirmation = true },
            onRequestPermissions = {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                )
            },
            onStart = { beginBroadcast() },
        )

        // En configuración todavía no hay directo activo: si el usuario confirma, se
        // libera la preview y se vuelve atrás.
        if (showEndConfirmation) {
            EndLiveDialog(
                onDismiss = { showEndConfirmation = false },
                onConfirm = {
                    showEndConfirmation = false
                    stopAndFinish()
                },
            )
        }
        return
    }

    // --- Directo activo: el video de cámara (track local de LiveKit) es la capa base
    //     a pantalla completa y todo lo demás flota encima. Sin Scaffold ni TopAppBar:
    //     los contenedores sólidos tapaban el video y rompían el look inmersivo.
    Box(modifier = Modifier.fillMaxSize()) {
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
                // El overlay NO debe tapar la preview cuando la cámara ya
                // está: aunque el evento Connected de LiveKit tarde, si el
                // track local está, la preview es visible.
                hideWhenTrackReady = localVideoTrack != null,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // ---------------- HUD superior ----------------
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = HudHorizontalPadding, end = HudHorizontalPadding, top = HudTopPadding)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveStatusPill(
                    elapsedSeconds = elapsedSeconds,
                    viewerCount = viewerCount,
                )

                Spacer(modifier = Modifier.weight(1f))

                LiveGlassCircleButton(
                    icon = Icons.Rounded.Home,
                    contentDescription = "Salir del directo",
                    onClick = { showEndConfirmation = true },
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            activeStream?.let { stream ->
                LiveGuestControls(
                    guests = guests,
                    onInvite = { userId -> guestViewModel.inviteGuest(stream.id, userId) },
                    onRemove = { userId -> guestViewModel.removeGuest(stream.id, userId) }
                )
            }
        }

        // ---------------- Co-Host (PiP) ----------------
        // Va arriba a la derecha, debajo del botón de salir, para no chocar con los
        // controles ni con los comentarios.
        if (remoteVideoTrack != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = CoHostPipTop, end = HudHorizontalPadding)
            ) {
                Surface(
                    modifier = Modifier.width(118.dp).height(170.dp),
                    shape = LiveCardShape,
                    color = Color.Black,
                    border = BorderStroke(1.dp, LiveHudBorder),
                ) {
                    LiveVideoSurface(
                        videoTrack = remoteVideoTrack,
                        initRenderer = roomRepository::initVideoRenderer,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = "Co-Host",
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // ---------------- Comentarios ----------------
        // Anclados abajo para que crezcan hacia arriba, dejando libre la franja de
        // controles flotantes.
        if (commentsVisible) {
            LiveViewerComments(
                comments = comments,
                onSendComment = { text -> activeStream?.let { viewModel.postComment(it.id, text) } },
                isBroadcaster = true,
                onDeleteComment = { commentId -> viewModel.deleteComment(commentId) },
                onBlockUser = { userId -> activeStream?.let { viewModel.blockUser(it.id, userId) } },
                hostId = SupabaseClient.currentUser?.id,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = HudHorizontalPadding, end = HudHorizontalPadding, bottom = CommentsBottomPadding)
                    .heightIn(max = 240.dp)
            )
        }

        // ---------------- Aviso de error de conexión ----------------
        liveSetupError?.let { err ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = CommentsBottomPadding)
                    .fillMaxWidth()
                    .padding(horizontal = HudHorizontalPadding),
                shape = RoundedCornerShape(16.dp),
                color = LiveEndRed.copy(alpha = 0.92f)
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

        // ---------------- HUD inferior ----------------
        LiveBroadcastControls(
            isMicMuted = isMicMuted,
            isCameraOff = isCameraOff,
            commentsVisible = commentsVisible,
            onToggleMic = {
                isMicMuted = !isMicMuted
                scope.launch { roomRepository.setMicrophoneEnabled(!isMicMuted) }
            },
            onToggleCamera = {
                isCameraOff = !isCameraOff
                scope.launch { roomRepository.setCameraEnabled(!isCameraOff) }
            },
            onSwitchCamera = { scope.launch { roomRepository.switchCamera() } },
            onToggleComments = { commentsVisible = !commentsVisible },
            onEndLive = { showEndConfirmation = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = HudHorizontalPadding, end = HudHorizontalPadding, bottom = HudBottomPadding)
        )
    }

    if (showEndConfirmation) {
        EndLiveDialog(
            onDismiss = { showEndConfirmation = false },
            onConfirm = {
                showEndConfirmation = false
                stopAndFinish()
            },
        )
    }
}

@Composable
private fun EndLiveDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finalizar Transmisión") },
        text = { Text("¿Estás seguro de que deseas finalizar este Live? Esta acción no se puede deshacer.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Finalizar", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.White)
            }
        },
        containerColor = Color(0xFF161618),
        titleContentColor = Color.White,
        textContentColor = Color.Gray
    )
}

/** Margen para que CameraX suelte la cámara antes de que LiveKit abra la suya. */
private const val CAMERA_RELEASE_DELAY_MS = 350L

// Márgenes del HUD flotante. Se usan dp plano (NO statusBarsPadding/
// navigationBarsPadding): la ventana de la app NO es edge-to-edge porque
// MainActivity.onResume fuerza setDecorFitsSystemWindows(true), así que el sistema
// ya reserva el espacio de las barras y volver a aplicar los insets lo duplicaría.
private val HudHorizontalPadding = 20.dp
private val HudTopPadding = 14.dp
private val HudBottomPadding = 14.dp

/** Reserva la altura del HUD inferior para que los comentarios no queden debajo. */
private val CommentsBottomPadding = 84.dp

/** El PiP del Co-Host se coloca debajo del botón de salir. */
private val CoHostPipTop = 78.dp
