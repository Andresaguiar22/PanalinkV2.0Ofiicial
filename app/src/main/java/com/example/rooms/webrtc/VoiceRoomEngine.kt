package com.example.rooms.webrtc

import org.webrtc.IceCandidate

/**
 * Abstraction over the voice-room media transport so the mesh WebRTC engine and
 * a future SFU (LiveKit) engine are interchangeable. The ViewModel talks only
 * to this interface; the SDP/ICE methods are no-ops under an SFU where the
 * server performs signaling, but they remain in the contract so the caller does
 * not branch on the engine type.
 */
interface VoiceRoomEngine {
    interface Listener {
        fun onLocalIceCandidate(toUserId: String, candidate: IceCandidate)
        fun onPeerSpeaking(userId: String, speaking: Boolean)
        fun onPeerConnectionStateChanged(userId: String, connected: Boolean)
        suspend fun sendOfferTo(userId: String, sdp: String)
        suspend fun sendAnswerTo(userId: String, sdp: String)
    }

    fun initialize()
    fun setMicEnabled(enabled: Boolean)
    fun onPeerJoined(remoteUserId: String)
    fun onPeerLeft(remoteUserId: String)
    suspend fun onRemoteOffer(fromUserId: String, sdp: String)
    suspend fun onRemoteAnswer(fromUserId: String, sdp: String)
    fun onRemoteIceCandidate(fromUserId: String, sdpMid: String, sdpMLineIndex: Int, candidate: String)
    fun resetPeers()
    fun release()
}
