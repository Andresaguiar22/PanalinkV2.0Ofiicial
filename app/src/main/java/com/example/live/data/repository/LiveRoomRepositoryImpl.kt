package com.example.live.data.repository

import android.content.Context
import com.example.live.data.livekit.LiveKitManager
import com.example.live.domain.model.LiveConnectionState
import com.example.live.domain.repository.LiveRoomRepository
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.flow.StateFlow

class LiveRoomRepositoryImpl(context: Context) : LiveRoomRepository {
    private val liveKitManager = LiveKitManager(context)

    override val connectionState: StateFlow<LiveConnectionState> = liveKitManager.connectionState
    override val localVideoTrack: StateFlow<VideoTrack?> = liveKitManager.localVideoTrack
    override val remoteVideoTrack: StateFlow<VideoTrack?> = liveKitManager.remoteVideoTrack
    override val rendererReady: StateFlow<Boolean> = liveKitManager.rendererReady

    override suspend fun joinRoom(url: String, token: String) {
        liveKitManager.connect(url, token)
        // Premium 2.0: ver/participar en un live cuenta para la misión diaria.
        try {
            com.example.premium.domain.PremiumEventBus.publishActivity(
                com.example.premium.domain.MissionActivities.LIVE_JOINED
            )
        } catch (_: Exception) {
        }
    }

    override suspend fun startBroadcast(url: String, token: String) {
        liveKitManager.startBroadcasting(url, token)
        // Premium 2.0: iniciar un live cuenta para la misión diaria.
        try {
            com.example.premium.domain.PremiumEventBus.publishActivity(
                com.example.premium.domain.MissionActivities.LIVE_STARTED
            )
        } catch (_: Exception) {
        }
    }

    override suspend fun switchCamera() {
        liveKitManager.switchCamera()
    }

    override suspend fun setMicrophoneEnabled(enabled: Boolean) {
        liveKitManager.setMicrophoneEnabled(enabled)
    }

    override suspend fun setCameraEnabled(enabled: Boolean) {
        liveKitManager.setCameraEnabled(enabled)
    }

    override fun initVideoRenderer(renderer: SurfaceViewRenderer) {
        liveKitManager.initVideoRenderer(renderer)
    }

    override fun leaveRoom() {
        liveKitManager.disconnect()
    }

    override suspend fun leaveRoomSuspending() {
        liveKitManager.disconnectSuspending()
    }
}
