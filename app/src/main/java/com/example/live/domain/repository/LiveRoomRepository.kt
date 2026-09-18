package com.example.live.domain.repository

import com.example.live.domain.model.LiveConnectionState
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.flow.StateFlow

interface LiveRoomRepository {
    val connectionState: StateFlow<LiveConnectionState>
    val localVideoTrack: StateFlow<VideoTrack?>
    val remoteVideoTrack: StateFlow<VideoTrack?>

    /**
     * true cuando el renderer de video ya quedo inicializado contra la Room
     * vigente. Mientras sea false el renderer NO puede dibujar frames, asi que la UI
     * debe seguir mostrando el aviso de "Activando camara" en vez de una pantalla
     * negra muda (ver LiveKitManager.initVideoRenderer).
     */
    val rendererReady: StateFlow<Boolean>

    suspend fun joinRoom(url: String, token: String)
    suspend fun startBroadcast(url: String, token: String)
    suspend fun switchCamera()
    suspend fun setMicrophoneEnabled(enabled: Boolean)
    suspend fun setCameraEnabled(enabled: Boolean)
    fun initVideoRenderer(renderer: SurfaceViewRenderer)
    fun leaveRoom()

    /**
     * Version que garantiza que el teardown de la sala/camara/mic termino antes de
     * devolver. Usarla en flujos que necesitan certeza (finalizar transmision,
     * salir de la pantalla) para no dejar el capturer de camara vivo.
     */
    suspend fun leaveRoomSuspending()
}
