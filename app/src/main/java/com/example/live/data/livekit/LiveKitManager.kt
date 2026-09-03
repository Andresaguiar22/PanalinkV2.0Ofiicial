package com.example.live.data.livekit

import android.content.Context
import android.util.Log
import com.example.live.domain.model.LiveConnectionState
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LiveKitManager(private val context: Context) {
    private val TAG = "PanalinkLive"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var room: Room? = null
    private var eventsJob: Job? = null

    private val _connectionState = MutableStateFlow<LiveConnectionState>(LiveConnectionState.Disconnected)
    val connectionState: StateFlow<LiveConnectionState> = _connectionState.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    suspend fun connect(url: String, token: String) = withContext(Dispatchers.IO) {
        if (room != null && _connectionState.value is LiveConnectionState.Connected) return@withContext
        try {
            disconnectInternal()
            _connectionState.value = LiveConnectionState.Connecting
            val currentRoom = LiveKit.create(context)
            room = currentRoom
            collectRoomEvents(currentRoom, false)
            currentRoom.connect(url, token)
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to LiveKit room", e)
            _connectionState.value = LiveConnectionState.Error(e.message ?: "Error de conexión")
        }
    }

    suspend fun startBroadcasting(url: String, token: String) = withContext(Dispatchers.IO) {
        if (room != null && _connectionState.value is LiveConnectionState.Connected) return@withContext
        try {
            disconnectInternal()
            _connectionState.value = LiveConnectionState.Connecting
            _localVideoTrack.value = null

            val currentRoom = LiveKit.create(context)
            room = currentRoom
            collectRoomEvents(currentRoom, true)
            currentRoom.connect(url, token)

            Log.d(TAG, "Enabling local camera and microphone for broadcast")
            currentRoom.localParticipant.setCameraEnabled(true)
            currentRoom.localParticipant.setMicrophoneEnabled(true)

            // setCameraEnabled() publishes asynchronously; wait for the actual track.
            var localTrack: VideoTrack? = null
            repeat(60) {
                val publication = currentRoom.localParticipant.getTrackPublication(Track.Source.CAMERA)
                localTrack = publication?.track as? VideoTrack
                if (localTrack != null) return@repeat
                delay(50)
            }
            if (localTrack == null) {
                throw IllegalStateException("La cámara se activó, pero LiveKit no entregó el VideoTrack local")
            }
            _localVideoTrack.value = localTrack
            Log.i(TAG, "Local camera track ready: ${localTrack?.sid}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting broadcast", e)
            _localVideoTrack.value = null
            _connectionState.value = LiveConnectionState.Error(e.message ?: "Error al iniciar transmisión")
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
            disconnectInternal()
            _connectionState.value = LiveConnectionState.Connecting
            _localVideoTrack.value = null
            val currentRoom = LiveKit.create(context)
            room = currentRoom
            collectRoomEvents(currentRoom, false)
            currentRoom.connect(url, token)
            currentRoom.localParticipant.setCameraEnabled(true)
            currentRoom.localParticipant.setMicrophoneEnabled(true)
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

    private fun disconnectInternal() {
        try {
            eventsJob?.cancel()
            eventsJob = null
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

    fun initVideoRenderer(renderer: SurfaceViewRenderer) {
        val currentRoom = room ?: return
        try {
            currentRoom.initVideoRenderer(renderer)
            Log.d(TAG, "LiveKit video renderer initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing LiveKit video renderer", e)
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
