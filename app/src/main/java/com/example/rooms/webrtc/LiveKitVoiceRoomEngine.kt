package com.example.rooms.webrtc

import android.content.Context
import android.util.Log
import com.example.data.repository.LiveKitTokenService
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.LocalAudioTrackOptions
import io.livekit.android.util.flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate

/**
 * Voice-room media transport backed by a LiveKit SFU. Replaces the P2P mesh
 * ([VoiceRoomWebRtcEngine]): instead of one PeerConnection per seated peer
 * (which degrades past ~6 people), a single connection to the LiveKit room
 * carries all audio. The Supabase state layer (seats/members/chat) is untouched;
 * this engine only owns the audio.
 *
 * The SDP/ICE methods from [VoiceRoomEngine] are no-ops — LiveKit performs its
 * own signaling — so the ViewModel wiring does not branch on engine type.
 *
 * LiveKit room name = `voice_<roomId>` (sanitized). The mic starts disabled and
 * is toggled by [setMicEnabled] when the user takes/leaves a seat.
 */
class LiveKitVoiceRoomEngine(
    private val context: Context,
    private val myUserId: String,
    private val roomId: String,
    private val listener: VoiceRoomEngine.Listener,
) : VoiceRoomEngine {

    companion object {
        private const val TAG = "LiveKitVoiceRoomEngine"
        private const val ROOM_PREFIX = "voice_"
        private const val MAX_CONNECT_RETRIES = 3
        private const val RETRY_BASE_DELAY_MS = 1000L
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val speakingJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    @Volatile private var room: Room? = null
    @Volatile private var released = false
    @Volatile private var micEnabled = false
    @Volatile private var connectAttempts = 0

    override fun initialize() {
        if (released) return
        scope.launch { connect() }
    }

    /**
     * Connects to the LiveKit room for [roomId]. Idempotent. Retries the
     * initial join up to [MAX_CONNECT_RETRIES] times with a backoff so a
     * transient SFU/network blip does not leave the room with dead audio.
     */
    private suspend fun connect() {
        if (released) return
        if (room != null) return

        val roomName = sanitizeRoomName(roomId)
        val creds = LiveKitTokenService.fetchToken(roomName, name = myUserId)
        if (creds == null) {
            Log.w(TAG, "Could not obtain LiveKit token for $roomName (attempt ${connectAttempts + 1})")
            retryOrFail(roomName) { connect() }
            return
        }
        val lkRoom = LiveKit.create(context, options = buildRoomOptions())
        this.room = lkRoom

        // Room-level events: participant join/leave + active speakers.
        scope.launch {
            lkRoom.events.collect { event ->
                if (released) return@collect
                when (event) {
                    is RoomEvent.ParticipantConnected -> {
                        val id = event.participant.identity?.value
                        if (id != null) {
                            listener.onPeerConnectionStateChanged(id, true)
                            observeSpeaking(event.participant as? RemoteParticipant)
                        }
                    }
                    is RoomEvent.ParticipantDisconnected -> {
                        val id = event.participant.identity?.value
                        if (id != null) {
                            listener.onPeerConnectionStateChanged(id, false)
                            listener.onPeerSpeaking(id, false)
                            stopSpeakingMonitor(id)
                        }
                    }
                    is RoomEvent.ActiveSpeakersChanged -> {
                        val active = event.speakers.mapNotNull { it.identity?.value }.toSet()
                        active.forEach { listener.onPeerSpeaking(it, true) }
                        speakingJobs.keys.forEach { id -> if (id !in active) listener.onPeerSpeaking(id, false) }
                    }
                    else -> {}
                }
            }
        }

        try {
            lkRoom.connect(creds.url, creds.token)
            connectAttempts = 0
            // Publish the mic track (disabled until the user takes a seat).
            lkRoom.localParticipant.setMicrophoneEnabled(micEnabled)
            // Observe speaking for participants already in the room when we joined.
            lkRoom.remoteParticipants.values.forEach { observeSpeaking(it) }
            // Observe our own speaking state so the local seat glow reacts too.
            observeLocalSpeaking(lkRoom)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to LiveKit room $roomName (attempt ${connectAttempts + 1})", e)
            // Tear down the half-built room before retrying.
            try { lkRoom.disconnect() } catch (_: Exception) {}
            if (this.room === lkRoom) this.room = null
            retryOrFail(roomName) { connect() }
        }
    }

    /**
     * Retries the connection with a backoff; logs and gives up silently
     * (the engine stays released-aware) once the retries are exhausted.
     */
    private suspend fun retryOrFail(roomName: String, block: suspend () -> Unit) {
        if (released) return
        connectAttempts++
        if (connectAttempts > MAX_CONNECT_RETRIES) {
            Log.e(TAG, "LiveKit connect exhausted retries for $roomName; giving up.")
            return
        }
        val delayMs = RETRY_BASE_DELAY_MS * (1L shl (connectAttempts - 1))
        delay(delayMs)
        if (!released) block()
    }

    /**
     * Per-participant isSpeaking flow gives precise on/off transitions for the
     * seat glow (the UI reconciles the full roster via Supabase seat events).
     */
    private fun observeSpeaking(remote: RemoteParticipant?) {
        if (remote == null || released) return
        val id = remote.identity?.value ?: return
        speakingJobs[id]?.cancel()
        speakingJobs[id] = scope.launch {
            remote::isSpeaking.flow.collect { speaking ->
                if (released) return@collect
                listener.onPeerSpeaking(id, speaking)
            }
        }
    }

    private fun stopSpeakingMonitor(userId: String) {
        speakingJobs.remove(userId)?.cancel()
    }

    /**
     * Tracks the local participant's speaking state so the user's own seat
     * shows the pulsing aura when they talk — without it the self-glow never
     * appears because [RoomEvent.ActiveSpeakersChanged] only reports remotes.
     */
    private fun observeLocalSpeaking(lkRoom: Room) {
        scope.launch {
            lkRoom.localParticipant::isSpeaking.flow.collect { speaking ->
                if (released) return@collect
                listener.onPeerSpeaking(myUserId, speaking)
            }
        }
    }

    private fun disconnect() {
        val r = room ?: return
        try { r.disconnect() } catch (e: Exception) { Log.w(TAG, "disconnect error", e) }
        room = null
    }

    override fun setMicEnabled(enabled: Boolean) {
        micEnabled = enabled
        val r = room ?: return
        scope.launch {
            try { r.localParticipant.setMicrophoneEnabled(enabled) }
            catch (e: Exception) { Log.w(TAG, "setMicrophoneEnabled failed", e) }
        }
    }

    /**
     * Under the SFU there is no per-peer connection to set up; the remote user's
     * audio is auto-subscribed. Kept for interface parity with the mesh engine.
     */
    override fun onPeerJoined(remoteUserId: String) {}

    override fun onPeerLeft(remoteUserId: String) {
        // Audio is auto-unsubscribed when the remote participant leaves the room.
    }

    // SDP/ICE exchange is handled internally by LiveKit; these are no-ops.
    override suspend fun onRemoteOffer(fromUserId: String, sdp: String) {}
    override suspend fun onRemoteAnswer(fromUserId: String, sdp: String) {}
    override fun onRemoteIceCandidate(fromUserId: String, sdpMid: String, sdpMLineIndex: Int, candidate: String) {}

    override fun resetPeers() {
        // The SFU preserves subscriptions across reconnects; nothing to reset.
    }

    override fun release() {
        if (released) return
        released = true
        speakingJobs.values.forEach { it.cancel() }
        speakingJobs.clear()
        disconnect()
        scope.cancel()
    }

    /**
     * Room con captura de audio de voz nítida: cancelación de eco, supresión de
     * ruido, control de ganancia automática y filtro paso-alto para el micrófono.
     *
     * Con esto el usuario escucha solo a los demás (sin ecos de su propia voz):
     * el AEC remueve la señal que vuelve del altavoz al mic y el NS limpia el
     * ruido ambiente. Sin esta configuración el SDK lanza el capturador con
     * opciones vacías y en varios dispositivos el altavoz se cuela al micrófono
     * (eco al hablar).
     */
    private fun buildRoomOptions(): RoomOptions {
        return RoomOptions(
            audioTrackCaptureDefaults = LocalAudioTrackOptions(
                noiseSuppression = true,
                echoCancellation = true,
                autoGainControl = true,
                highPassFilter = true,
                typingNoiseDetection = false
            ),
            // Salas de voz no usan video: no tiene sentido la adaptación de bitrate.
            adaptiveStream = false
        )
    }

    private fun sanitizeRoomName(roomId: String): String {
        // LiveKit room names: alnum, '-', '_'. Prefix to namespace voice rooms.
        val safe = roomId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        return "$ROOM_PREFIX$safe"
    }
}
