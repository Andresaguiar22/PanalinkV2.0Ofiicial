package com.example.live.domain.repository

import com.example.live.domain.model.LiveConnectionState
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.flow.StateFlow

interface LiveRoomRepository {
    val connectionState: StateFlow<LiveConnectionState>
    val localVideoTrack: StateFlow<VideoTrack?>
    val remoteVideoTrack: StateFlow<VideoTrack?>

    suspend fun joinRoom(url: String, token: String)
    suspend fun startBroadcast(url: String, token: String)
    suspend fun switchCamera()
    suspend fun setMicrophoneEnabled(enabled: Boolean)
    suspend fun setCameraEnabled(enabled: Boolean)
    fun initVideoRenderer(renderer: SurfaceViewRenderer)
    fun leaveRoom()
}
