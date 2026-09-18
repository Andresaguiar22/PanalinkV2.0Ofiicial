package com.example.live.ui.screen

import android.util.Log

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
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
import com.example.live.data.LiveCleanupScope
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
    val rendererReady by roomRepository.rendererReady.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val viewerCount by viewModel.viewerCount.collectAsStateWithLifecycle()
    val guests by guestViewModel.guests.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var titleText by remember { mutableStateOf("Mi Transmisión en Vivo") }
    var descriptionText by remember { mutableStateOf("¡Acompañame en este directo!") }
    var activeStream by remember { mutableStateOf<LiveStream?>(null) }
    var isLiveStarted by remember { mutableStateOf(false) }
    // Evita dobles finalizaciones (boton FINALIZAR + onDispose): la navegacion
    // dispara el onDispose y si ambos corren el teardown, la sala se desconecta
    // del room NUEVO cuando el usuario entra a otro directo.
    var isFinishing by remember { mutableStateOf(false) }
    // Se incrementa para volver a enganchar la preview de CameraX si el arranque
    // del directo falla: al liberar el sensor, la preview quedaba en negro.
    var previewRestartKey by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isStarting by remember { mutableStateOf(false) }
    var showEndConfirmation by remember { mutableStateOf(false) }
    var liveSetupError by remember { mutableStateOf<String?>(null) }

    var isMicMuted by remember { mutableStateOf(false) }
    var isCameraOff by remember { mutableStateOf(false) }

    // Controlador de la preview de CameraX del pre-live: permite soltar el sensor
    // antes de que LiveKit lo reclame al iniciar el directo.
    val cameraPreviewController = rememberLiveCameraPreviewController()

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

    /**
     * Finaliza el directo de forma SECUENCIAL para no crear "lives fantasma":
     * 1) Marca ENDED en Supabase (con reintentos por token), 2) recien despues suelta
     * la sala/camara, 3) y por ultimo navega atras. Antes el PATCH se lanzaba en un
     * scope que el navigate cancelaba a mitad -> el row quedaba LIVE para siempre.
     */
    fun stopAndFinish() {
        if (isFinishing) return
        isFinishing = true
        // Scope de aplicacion (no el de composicion): la navegacion cancela el scope
        // de Compose al hacer pop, y eso mataba el PATCH de ENDED a mitad ==> stream
        // fantasma. Con LiveCleanupScope.el flujo termina aunque la pantalla ya no exista.
        LiveCleanupScope.io.launch {
            try {
                activeStream?.let { stream -> viewModel.endLive(stream.id) }
            } catch (_: Exception) {}
            try {
                roomRepository.leaveRoomSuspending()
            } catch (_: Exception) {}
            viewModel.stopStreamSession()
            guestViewModel.stopRealtime()
        }
        onNavigateBack()
    }

    /** Conecta a LiveKit (token + conexión + cámara) sin bloquear la UI de live. */
    fun startLiveInBackground(stream: LiveStream) {
        viewModel.loadComments(stream.id)
        viewModel.startStreamSession(stream.id, isBroadcaster = true)
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
            // Solo deja la sala si el usuario no finalizo formalmente (el boton
            // FINALIZAR ya orquesto end+leave). Sin esta guarda, la navegacion al
            // finalizar un directo disparaba leaveRoom() y podia desconectar la
            // sala de la siguiente entrada.
            if (isFinishing) return@onDispose
            // Salida sin confirmar (ej. back del sistema / gesto): no se puede
            // cancelarle el PATCH de ENDED al scope muerto, asi que se despacha en
            // el scope de aplicacion (sobrevive al pop).
            activeStream?.let { stream ->
                LiveCleanupScope.io.launch {
                    try { viewModel.endLive(stream.id) } catch (_: Exception) {}
                }
            }
            LiveCleanupScope.io.launch {
                try { roomRepository.leaveRoomSuspending() } catch (_: Exception) {}
                try { viewModel.stopStreamSession() } catch (_: Exception) {}
                try { guestViewModel.stopRealtime() } catch (_: Exception) {}
            }
        }
    }

    // En el pre-live el fondo es la preview de camara edge-to-edge, asi que no
    // hay TopAppBar ni padding del Scaffold: cada elemento flota con sus propios
    // insets (statusBarsPadding / navigationBarsPadding).
    Scaffold(
        topBar = {},
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isLiveStarted) Modifier else Modifier.padding(paddingValues)),
            contentAlignment = Alignment.Center
        ) {
            if (!hasPermissions) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF161618)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                }
            } else if (!isLiveStarted) {
                // ------------------------------------------------------------------
                // Pre-live: la preview de CameraX es el fondo edge-to-edge y todos
                // los controles flotan encima (glassmorphism).
                // ------------------------------------------------------------------
                Box(modifier = Modifier.fillMaxSize()) {
                    LiveCameraBackgroundPreview(
                        controller = cameraPreviewController,
                        modifier = Modifier.fillMaxSize(),
                        restartKey = previewRestartKey
                    )

                    // Overlay oscuro translucido: garantiza contraste del contenido
                    // sobre el video de la camara ya difuminado.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                    )

                    LivePreliveTopBar(
                        title = "Transmitir en Vivo",
                        onNavigateBack = onNavigateBack,
                        modifier = Modifier.align(Alignment.TopStart)
                    )

                    LiveGlassPanel(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp)
                    ) {
                        Text(
                            text = "DETALLES DEL DIRECTO",
                            color = PanalinkMint.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.4.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        LiveGlassTextField(
                            value = titleText,
                            onValueChange = { titleText = it },
                            placeholder = "Título de la transmisión",
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LiveGlassDivider()
                        Spacer(modifier = Modifier.height(12.dp))
                        LiveGlassTextField(
                            value = descriptionText,
                            onValueChange = { descriptionText = it },
                            placeholder = "Descripción (opcional)",
                            singleLine = false,
                            minHeight = 64.dp
                        )

                        errorMessage?.let { message ->
                            Spacer(modifier = Modifier.height(14.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFEF5350).copy(alpha = 0.18f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = message,
                                    color = Color(0xFFFF8A80),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    LiveStartBroadcastButton(
                        isStarting = isStarting,
                        enabled = !isStarting && titleText.isNotBlank(),
                        onClick = {
                            if (isStarting) return@LiveStartBroadcastButton
                            isStarting = true
                            errorMessage = null
                            // LiveKit necesita el sensor libre: soltamos la preview
                            // de CameraX antes de pedir el token y conectar.
                            cameraPreviewController.releaseCamera()
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
                                        // El sensor quedó libre pero seguimos en pre-live:
                                        // volvemos a enganchar la preview para no dejar fondo negro.
                                        previewRestartKey++
                                    }
                                } catch (e: Exception) {
                                    errorMessage = if (e is CancellationException) {
                                        "Tiempo de espera agotado al crear el stream. Revisa tu conexión."
                                    } else {
                                        e.message ?: "Error desconocido"
                                    }
                                    previewRestartKey++
                                } finally {
                                    isStarting = false
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            } else {
                // === Directo activo: cámara edge-to-edge + superficies flotantes ===
                // Fondo oscuro de respaldo: la superficie del video es transparente
                // hasta que el track llega, y sin esto se vería el fondo del host.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0E0E10))
                ) {
                    // Capa base: track local de LiveKit (la cámara real ya está
                    // publicada). Sin ella el fondo queda inmersivo en negro.
                    LiveVideoSurface(
                        videoTrack = localVideoTrack,
                        initRenderer = roomRepository::initVideoRenderer,
                        modifier = Modifier.fillMaxSize(),
                        backgroundColor = Color.Transparent
                    )

                    LiveConnectionOverlay(
                        connectionState = connectionState,
                        // Solo se oculta cuando ADEMAS de haber track el renderer esta
                        // inicializado: si el renderer no puede dibujar, la pantalla
                        // quedaria NEGRA y muda sin que el usuario sepa por que.
                        hideWhenTrackReady = localVideoTrack != null && rendererReady,
                        // Mantiene "Activando camara..." visible mientras el track no
                        // llegue, incluso si la sala ya reporto Connected.
                        keepVisibleUntilTrackReady = true,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    // Panel de estado flotante (izquierda superior), dentro de los
                    // insets de la status bar.
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(start = 16.dp, top = 12.dp)
                    ) {
                        LiveStatusPill(
                            elapsedSeconds = elapsedSeconds,
                            viewerCount = viewerCount
                        )
                    }

                    // Invitar Co-Host (OutlinedButton verde neón), debajo del
                    // panel de estado.
                    activeStream?.let { stream ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .statusBarsPadding()
                                .padding(start = 16.dp, top = 64.dp)
                        ) {
                            LiveGuestControls(
                                guests = guests,
                                onInvite = { userId -> guestViewModel.inviteGuest(stream.id, userId) },
                                onRemove = { userId -> guestViewModel.removeGuest(stream.id, userId) }
                            )
                        }
                    }

                    // Distintivo de Co-Host conectado, arriba a la derecha.
                    if (remoteVideoTrack != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(end = 16.dp, top = 12.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(160.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = Color.Black.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
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
                    }

                    liveSetupError?.let { err ->
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 130.dp)
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

                    // Comentarios del directo, sobre la fila de controles.
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 76.dp)
                    ) {
                        LiveViewerComments(
                            comments = comments,
                            onSendComment = { text -> activeStream?.let { viewModel.postComment(it.id, text) } },
                            isBroadcaster = true,
                            onDeleteComment = { commentId -> viewModel.deleteComment(commentId) },
                            onBlockUser = { userId -> activeStream?.let { viewModel.blockUser(it.id, userId) } },
                            hostId = SupabaseClient.currentUser?.id,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Controles flotantes (abajo): iconos circulares glass a la
                    // izquierda y FINALIZAR anclado abajo a la derecha.
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LiveGlassIconButton(
                            icon = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMicMuted) "Activar micrófono" else "Silenciar micrófono",
                            isAlert = isMicMuted,
                            onClick = {
                                isMicMuted = !isMicMuted
                                scope.launch { roomRepository.setMicrophoneEnabled(!isMicMuted) }
                            }
                        )
                        LiveGlassIconButton(
                            icon = if (isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                            contentDescription = if (isCameraOff) "Activar cámara" else "Apagar cámara",
                            isAlert = isCameraOff,
                            onClick = {
                                isCameraOff = !isCameraOff
                                scope.launch { roomRepository.setCameraEnabled(!isCameraOff) }
                            }
                        )
                        LiveGlassIconButton(
                            icon = Icons.Default.Cameraswitch,
                            contentDescription = "Cambiar cámara",
                            onClick = { scope.launch { roomRepository.switchCamera() } }
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        LiveEndPill(onClick = { showEndConfirmation = true })
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
