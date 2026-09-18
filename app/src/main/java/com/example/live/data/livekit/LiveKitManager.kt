package com.example.live.data.livekit

import android.content.Context
import android.util.Log
import com.example.live.domain.model.LiveConnectionState
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LiveKitManager(private val context: Context) {
    private val TAG = "PanalinkLive"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // LiveKit no trae timeout en connect(); sin esto el boton de iniciar se
    // quedaba colgado para siempre cuando el WebSocket no responde.
    private val CONNECT_TIMEOUT_MS = 10_000L
    private val TRACK_TIMEOUT_MS = 10_000L
    private val CAMERA_WATCH_TIMEOUT_MS = 15_000L

    private var room: Room? = null
    private var eventsJob: Job? = null
    private var cameraWatchJob: Job? = null

    private val _connectionState = MutableStateFlow<LiveConnectionState>(LiveConnectionState.Disconnected)
    val connectionState: StateFlow<LiveConnectionState> = _connectionState.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    suspend fun connect(url: String, token: String) = withContext(Dispatchers.IO) {
        if (room != null && _connectionState.value is LiveConnectionState.Connected) return@withContext
        try {
            disconnectAndRelease()
            _connectionState.value = LiveConnectionState.Connecting
            val currentRoom = LiveKit.create(context)
            room = currentRoom
            collectRoomEvents(currentRoom, false)
            // LiveKit Room.connect() es suspending y NO tiene timeout por defecto:
            // si el WebSocket de LiveKit no responde, la corrutina se cuelga para
            // siempre (spinner infinito en la UI). conTimeout() lo convierte en error.
            withTimeout(CONNECT_TIMEOUT_MS) {
                currentRoom.connect(url, token)
            }
            initPendingRenderer()
        } catch (e: Exception) {
            // TimeoutCancellationException incluido: NO se relanza, para que el
            // caller (boton de iniciar) siga vivo y pueda mostrar el error.
            Log.e(TAG, "Error connecting to LiveKit room", e)
            _connectionState.value = LiveConnectionState.Error(e.message ?: "Error de conexión")
        }
    }

    suspend fun startBroadcasting(url: String, token: String) = withContext(Dispatchers.IO) {
        if (room != null && _connectionState.value is LiveConnectionState.Connected) return@withContext
        try {
            disconnectAndRelease()
            _connectionState.value = LiveConnectionState.Connecting
            _localVideoTrack.value = null

            val currentRoom = LiveKit.create(context)
            room = currentRoom
            collectRoomEvents(currentRoom, true)
            withTimeout(CONNECT_TIMEOUT_MS) {
                currentRoom.connect(url, token)
            }

            // LiveKit/WebRTC exige que la activación de cámara/mic ocurra en el
            // Main thread (EGL + CameraManager). Hacerlo desde IO dejaba la cámara
            // sin publicar en algunos dispositivos → "se queda cargando".
            withContext(Dispatchers.Main) {
                Log.d(TAG, "Enabling local camera and microphone for broadcast")
                try {
                    currentRoom.localParticipant.setCameraEnabled(true)
                    currentRoom.localParticipant.setMicrophoneEnabled(true)
                } catch (e: Exception) {
                    // Suele ser la cámara todavía retenida por CameraX (pantalla de
                    // configuración). Un reintento corto da margen a que el proveedor
                    // termine de soltarla en equipos lentos.
                    Log.e(TAG, "Fallo al activar cámara/mic; reintento tras 500 ms", e)
                    delay(500)
                    currentRoom.localParticipant.setCameraEnabled(true)
                    currentRoom.localParticipant.setMicrophoneEnabled(true)
                }
            }

            // initPendingRenderer tras la conexión: el SurfaceViewRenderer puede
            // haber sido creado por la UI antes de conectar (room todavía null).
            initPendingRenderer()

            // setCameraEnabled() publica de forma asincrona: hay que esperar a que exista
            // la publicacion local de camara y engancharla EN CUANTO aparece.
            //
            // OJO con el bug que habia aqui: era un
            //     repeat(Int.MAX_VALUE) { ...; return@repeat; ... }
            // y `return@repeat` NO rompe el bucle (solo sale de esa iteracion). El bucle
            // seguia hasta agotar el withTimeout de 10 s, y el track solo se publicaba
            // DESPUES de ese bloque: el preview se quedaba NEGRO 10 segundos aunque la
            // camara ya estuviera publicando. Con `while` + `return` el preview aparece
            // en cuanto la publicacion existe (tipicamente <300 ms).
            val published = awaitCameraTrack(currentRoom)
            if (published != null) {
                // Posible race: el SurfaceViewRenderer de la UI pudo crearse DESPUÉS
                // del connect() (el initPendingRenderer() previo no lo vio todavía).
                initPendingRenderer()
                _localVideoTrack.value = published
                _connectionState.value = LiveConnectionState.Connected
                Log.i(TAG, "Local camera track ready: ${published.sid}")
            } else {
                // No aparecio en la ventana sincrona: seguimos vigilando en segundo plano
                // en vez de dejar el preview negro y en silencio.
                Log.w(TAG, "Publicacion de camara ausente tras ${TRACK_TIMEOUT_MS}ms; paso a vigilancia en segundo plano")
                watchForCameraTrack(currentRoom)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting broadcast", e)
            _localVideoTrack.value = null
            _connectionState.value = LiveConnectionState.Error(e.message ?: "Error al iniciar transmisión")
        }
    }

    /**
     * Espera a que exista la publicacion local de camara, con corte por tiempo.
     * Devuelve el track en cuanto la publicacion aparece (no espera al limite).
     */
    private suspend fun awaitCameraTrack(currentRoom: Room): VideoTrack? {
        val deadline = System.currentTimeMillis() + TRACK_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val track = currentRoom.localParticipant
                .getTrackPublication(Track.Source.CAMERA)
                ?.track as? VideoTrack
            if (track != null) return track
            delay(50)
        }
        return null
    }

    /**
     * Ultimo recurso cuando la publicacion de camara no aparecio en la ventana sincrona:
     * la sigue buscando unos segundos mas. Sin esto, un arranque lento (o una publicacion
     * que llega tarde) dejaba el preview NEGRO para siempre y sin ningun aviso, porque el
     * estado ya era Connected y el overlay de conexion no se mostraba.
     */
    private fun watchForCameraTrack(currentRoom: Room) {
        cameraWatchJob?.cancel()
        cameraWatchJob = scope.launch {
            val deadline = System.currentTimeMillis() + CAMERA_WATCH_TIMEOUT_MS
            while (isActive && System.currentTimeMillis() < deadline) {
                // Si la sala cambio (reconexion, salida), este vigilante ya no aplica.
                if (room !== currentRoom) return@launch
                val track = currentRoom.localParticipant
                    .getTrackPublication(Track.Source.CAMERA)
                    ?.track as? VideoTrack
                if (track != null) {
                    initPendingRenderer()
                    _localVideoTrack.value = track
                    Log.i(TAG, "Local camera track detectado por el vigilante: ${track.sid}")
                    return@launch
                }
                delay(200)
            }
            Log.e(TAG, "La camara no publico ningun track en ${CAMERA_WATCH_TIMEOUT_MS}ms")
            _connectionState.value = LiveConnectionState.Error(
                "La cámara no pudo iniciarse. Cierra las apps que la estén usando e inténtalo de nuevo."
            )
        }
    }

    private fun collectRoomEvents(currentRoom: Room, isBroadcast: Boolean) {
        eventsJob?.cancel()
        eventsJob = scope.launch {
            try {
                currentRoom.events.collect { event ->
                    when (event) {
                        is RoomEvent.Connected -> {
                            Log.i(TAG, if (isBroadcast) "Broadcast room connected successfully" else "LiveKit room connected successfully")
                            _connectionState.value = LiveConnectionState.Connected
                        }
                        is RoomEvent.Reconnecting -> _connectionState.value = LiveConnectionState.Reconnecting
                        is RoomEvent.Disconnected -> {
                            _connectionState.value = LiveConnectionState.Disconnected
                            _localVideoTrack.value = null
                            _remoteVideoTrack.value = null
                        }
                        is RoomEvent.TrackSubscribed -> {
                            if (event.track is VideoTrack) _remoteVideoTrack.value = event.track as VideoTrack
                        }
                        is RoomEvent.TrackUnsubscribed -> {
                            if (event.track is VideoTrack && _remoteVideoTrack.value == event.track) _remoteVideoTrack.value = null
                        }
                        // El track LOCAL de cámara/mic no siempre aparece via
                        // getTrackPublication a tiempo; el SDK publica el evento
                        // TrackPublished justo cuando el capturer entrega frames.
                        is RoomEvent.TrackPublished -> {
                            if (event.participant is LocalParticipant &&
                                event.publication.source == Track.Source.CAMERA &&
                                event.publication.track is VideoTrack
                            ) {
                                _localVideoTrack.value = event.publication.track as VideoTrack
                                Log.i(TAG, "Local camera track published (event) -> preview activa")
                            }
                        }
                        is RoomEvent.TrackUnpublished -> {
                            if (event.participant is LocalParticipant &&
                                event.publication.source == Track.Source.CAMERA
                            ) {
                                _localVideoTrack.value = null
                                Log.i(TAG, "Local camera track unpublished")
                            }
                        }
                        else -> Unit
                    }
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                Log.e(TAG, "Error in room events collector", e)
            }
        }
    }

    suspend fun joinAsGuest(url: String, token: String) = withContext(Dispatchers.IO) {
        try {
            disconnectAndRelease()
            _connectionState.value = LiveConnectionState.Connecting
            _localVideoTrack.value = null
            val currentRoom = LiveKit.create(context)
            room = currentRoom
            collectRoomEvents(currentRoom, false)
            withTimeout(CONNECT_TIMEOUT_MS) {
                currentRoom.connect(url, token)
            }
            initPendingRenderer()
            withContext(Dispatchers.Main) {
                currentRoom.localParticipant.setCameraEnabled(true)
                currentRoom.localParticipant.setMicrophoneEnabled(true)
            }
            repeat(60) {
                val track = currentRoom.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
                if (track != null) {
                    _localVideoTrack.value = track
                    return@repeat
                }
                delay(50)
            }
            _connectionState.value = LiveConnectionState.Connected
        } catch (e: Exception) {
            Log.e(TAG, "Error joining as guest", e)
            _connectionState.value = LiveConnectionState.Error(e.message ?: "Error al unirse como invitado")
        }
    }

    /** Versión suspensiva: espera a que el room anterior libere cámara/mic. */
    private suspend fun disconnectAndRelease() {
        try {
            eventsJob?.cancel()
            eventsJob = null
            cameraWatchJob?.cancel()
            cameraWatchJob = null
            val currentRoom = room
            room = null
            if (currentRoom != null) {
                try {
                    currentRoom.localParticipant.setCameraEnabled(false)
                    currentRoom.localParticipant.setMicrophoneEnabled(false)
                } catch (e: Exception) {
                    Log.e(TAG, "Error disabling camera/mic on disconnect", e)
                }
                try {
                    currentRoom.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing room", e)
                }
            }
            _localVideoTrack.value = null
            _remoteVideoTrack.value = null
        } catch (e: Exception) {
            Log.e(TAG, "Error during internal suspend disconnect", e)
        }
    }

    private fun disconnectInternal() {
        try {
            eventsJob?.cancel()
            eventsJob = null
            cameraWatchJob?.cancel()
            cameraWatchJob = null
            val currentRoom = room
            room = null
            scope.launch {
                try {
                    currentRoom?.localParticipant?.setCameraEnabled(false)
                    currentRoom?.localParticipant?.setMicrophoneEnabled(false)
                    currentRoom?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing room", e)
                }
            }
            _localVideoTrack.value = null
            _remoteVideoTrack.value = null
        } catch (e: Exception) {
            Log.e(TAG, "Error during internal disconnect", e)
        }
    }

    private var pendingRenderer: SurfaceViewRenderer? = null

    fun initVideoRenderer(renderer: SurfaceViewRenderer) {
        val currentRoom = room
        if (currentRoom == null) {
            pendingRenderer = renderer
            Log.d(TAG, "Renderer creado antes de conectar; se inicializará al conectarse")
            return
        }
        try {
            currentRoom.initVideoRenderer(renderer)
            Log.d(TAG, "LiveKit video renderer initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing LiveKit video renderer", e)
        }
    }

    private fun initPendingRenderer() {
        val currentRoom = room ?: return
        val renderer = pendingRenderer ?: return
        pendingRenderer = null
        try {
            currentRoom.initVideoRenderer(renderer)
            Log.d(TAG, "LiveKit video renderer initialized after connect")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing pending LiveKit video renderer", e)
        }
    }

    fun switchCamera() {
        scope.launch {
            try {
                val track = room?.localParticipant?.getTrackPublication(Track.Source.CAMERA)?.track
                    as? io.livekit.android.room.track.LocalVideoTrack
                track?.switchCamera()
            } catch (e: Exception) {
                Log.e(TAG, "Error switching camera", e)
            }
        }
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        scope.launch {
            try { room?.localParticipant?.setMicrophoneEnabled(enabled) }
            catch (e: Exception) { Log.e(TAG, "Error setting microphone enabled", e) }
        }
    }

    fun setCameraEnabled(enabled: Boolean) {
        scope.launch {
            try {
                val currentRoom = room ?: return@launch
                currentRoom.localParticipant.setCameraEnabled(enabled)
                if (!enabled) {
                    _localVideoTrack.value = null
                    return@launch
                }
                repeat(60) {
                    val track = currentRoom.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
                    if (track != null) {
                        _localVideoTrack.value = track
                        return@launch
                    }
                    delay(50)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting camera enabled: $enabled", e)
            }
        }
    }

    fun disconnect() {
        scope.launch {
            disconnectInternal()
            _connectionState.value = LiveConnectionState.Disconnected
        }
    }

    fun release() {
        disconnect()
        scope.cancel()
    }
}
