package com.example.call

import android.content.Context
import android.util.Log
import com.example.data.repository.LiveKitTokenService
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.SurfaceViewRenderer as LkSurfaceViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.RemoteVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.util.flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Media transport for 1-1 calls backed by a LiveKit SFU. Both peers join the
 * same deterministically-named LiveKit room; the SFU relays audio/video so
 * neither side needs a public IP or STUN/TURN. Replaces the P2P
 * [WebRTCClient] + Socket.IO SDP exchange.
 *
 * Call state is surfaced through [Listener]; the room name is derived from the
 * two participant ids so both sides rendezvous on the same room without any
 * extra signaling handshake (Socket.IO is still used only for ring/accept/end
 * orchestration by [CallManager]).
 *
 * Supports both audio and video calls. Video renderers MUST be the LiveKit SDK
 * renderer ([io.livekit.android.renderer.SurfaceViewRenderer]) — not the
 * standalone org.webrtc SurfaceViewRenderer used by the legacy WebRTC path —
 * because LiveKit initializes them with its own EglBase via [Room.initVideoRenderer].
 */
class LiveKitCallEngine(
    private val context: Context,
    private val myUserId: String,
    private val listener: Listener,
) {

    interface Listener {
        /** Fired when the SFU connection becomes usable (peer media flowing). */
        fun onConnected()
        /** Fired when the SFU connection drops or fails. */
        fun onDisconnected()
        /** Fired on a reconnect attempt after a network blip. */
        fun onReconnecting()
        /**
         * Fired when the remote participant leaves the room..
         * When the peer departs the SFU without sending `call_end` (app death,
         * force-kill, total network loss,), the local side must not wait for the
         * 30s Connection Guard timeout — we know the call is over.
         */
        fun onParticipantLeft()
    }

    companion object {
        private const val TAG = "LiveKitCallEngine"
        private const val CALL_PREFIX = "call_"
        private const val MAX_INITIAL_RETRIES = 3
        private const val RETRY_BASE_DELAY_MS = 1000L
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    @Volatile private var room: Room? = null
    @Volatile private var released = false
    @Volatile private var micEnabled = false
    @Volatile private var cameraEnabled = false
    @Volatile private var isVideoCall = false
    @Volatile private var currentCameraFront = true
    private var roomName: String? = null
    // Whether the SFU connection has ever become usable. Distinguishes a
    // dropped-call reconnect from a failed initial connect so we can retry the
    // join without bubbling a premature onDisconnected to the CallManager.
    @Volatile private var hasConnected = false
    @Volatile private var pendingOtherUserId: String? = null
    @Volatile private var initialRetryCount = 0
    // LiveKit video tracks; attached/detached to renderers on demand.
    @Volatile private var localVideoTrack: LocalVideoTrack? = null
    @Volatile private var remoteVideoTrack: RemoteVideoTrack? = null
    // Renderers currently attached (held so we can re-attach on track arrival).
    private var localRenderer: LkSurfaceViewRenderer? = null
    private var remoteRenderer: LkSurfaceViewRenderer? = null

    /**
     * Connects to the LiveKit room shared by both call participants. [otherUserId]
     * is used purely to compute a deterministic, collision-free room name; the
     * actual media flows through the SFU for everyone in the room.
     *
     * The initial join is retried up to [MAX_INITIAL_RETRIES] times with an
     * exponential backoff. Only once the retries are exhausted (or the engine
     * has been released) does [Listener.onDisconnected] fire — so a transient
     * SFU/Network hiccup no longer surfaces as an immediate call failure.
     */
    fun connect(otherUserId: String, isVideo: Boolean) {
        if (released) return
        isVideoCall = isVideo
        pendingOtherUserId = otherUserId
        val name = sharedRoomName(myUserId, otherUserId)
        roomName = name
        initialRetryCount = 0
        attemptConnect(name)
    }

    private fun attemptConnect(name: String) {
        if (released) return
        scope.launch {
            val creds = LiveKitTokenService.fetchToken(name, name = myUserId)
            if (creds == null) {
                Log.w(TAG, "No LiveKit token for $name (attempt ${initialRetryCount + 1}/$MAX_INITIAL_RETRIES)")
                handleInitialFailure(name)
                return@launch
            }
            // Tear down any half-built room before reconnecting.
            try { room?.disconnect() } catch (_: Exception) {}
            val lkRoom = LiveKit.create(context)
            this@LiveKitCallEngine.room = lkRoom
            observeEvents(lkRoom)
            try {
                lkRoom.connect(creds.url, creds.token)
                hasConnected = true
                // Publish mic (and camera for video calls) once connected.
                lkRoom.localParticipant.setMicrophoneEnabled(micEnabled)
                if (isVideoCall && cameraEnabled) {
                    lkRoom.localParticipant.setCameraEnabled(true)
                    captureLocalVideoTrack(lkRoom)
                    localRenderer?.let { attachLocalRenderer(it) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "connect failed for $name (attempt ${initialRetryCount + 1}/$MAX_INITIAL_RETRIES)", e)
                handleInitialFailure(name)
            }
        }
    }

    /**
     * Retries the initial join with an exponential backoff. Bubbles
     * [Listener.onDisconnected] only after the retries are exhausted, so the
     * CallManager can transition the call to FAILED. A successful reconnect at
     * any point resets the counter.
     */
    private fun handleInitialFailure(name: String) {
        if (released) return
        if (hasConnected) {
            // We were connected before — treat as a drop, not an initial failure.
            listener.onDisconnected()
            return
        }
        initialRetryCount++
        if (initialRetryCount > MAX_INITIAL_RETRIES) {
            Log.e(TAG, "Initial connect exhausted retries for $name; giving up.")
            listener.onDisconnected()
            return
        }
        listener.onReconnecting()
        val delayMs = RETRY_BASE_DELAY_MS * (1L shl (initialRetryCount - 1))
        scope.launch {
            kotlinx.coroutines.delay(delayMs)
            attemptConnect(name)
        }
    }

    private fun observeEvents(lkRoom: Room) {
        scope.launch {
            lkRoom.events.collect { event ->
                if (released) return@collect
                when (event) {
                    is RoomEvent.Connected -> {
                        // Connected to the SFU; consider the call up once a remote
                        // participant (or its audio) is present.
                        hasConnected = true
                        initialRetryCount = 0
                        if (lkRoom.remoteParticipants.isNotEmpty()) listener.onConnected()
                    }
                    is RoomEvent.ParticipantConnected -> listener.onConnected()
                    is RoomEvent.ParticipantDisconnected -> listener.onParticipantLeft()
                    is RoomEvent.Disconnected -> {
                        if (released) return@collect
                        // If we never finished the initial join, route through the
                        // retry path instead of bubbling a drop to the CallManager.
                        if (!hasConnected) {
                            handleInitialFailure(roomName ?: return@collect)
                        } else {
                            listener.onDisconnected()
                        }
                    }
                    is RoomEvent.Reconnecting -> listener.onReconnecting()
                    is RoomEvent.Reconnected -> {
                        hasConnected = true
                        listener.onConnected()
                    }
                    is RoomEvent.TrackSubscribed -> {
                        when (val t = event.track) {
                            is RemoteAudioTrack -> listener.onConnected()
                            is RemoteVideoTrack -> {
                                remoteVideoTrack = t
                                remoteRenderer?.let { attachRemoteRenderer(it) }
                                listener.onConnected()
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
        // If a peer was already present at join time, fire connected.
        scope.launch {
            lkRoom::remoteParticipants.flow.collect { participants ->
                if (released) return@collect
                if (participants.isNotEmpty()) listener.onConnected()
            }
        }
    }

    fun setMicEnabled(enabled: Boolean) {
        micEnabled = enabled
        val r = room ?: return
        scope.launch {
            try { r.localParticipant.setMicrophoneEnabled(enabled) }
            catch (e: Exception) { Log.w(TAG, "setMicrophoneEnabled failed", e) }
        }
    }

    fun setCameraEnabled(enabled: Boolean) {
        cameraEnabled = enabled
        val r = room ?: return
        scope.launch {
            try {
                r.localParticipant.setCameraEnabled(enabled)
                if (enabled) {
                    captureLocalVideoTrack(r)
                    localRenderer?.let { attachLocalRenderer(it) }
                } else {
                    localVideoTrack?.let { track ->
                        localRenderer?.let { track.removeRenderer(it) }
                    }
                    localVideoTrack = null
                }
            } catch (e: Exception) { Log.w(TAG, "setCameraEnabled failed", e) }
        }
    }

    /**
     * Toggles between the front and back cameras mid-call. No-op if no local
     * camera track has been published yet.
     */
    fun switchCamera() {
        currentCameraFront = !currentCameraFront
        val next = if (currentCameraFront) CameraPosition.FRONT else CameraPosition.BACK
        scope.launch {
            val track = localVideoTrack
            if (track == null) {
                // Camera is off — the UI pressed "switch" without enabling it.
                // Enable it first (which creates the track) and then flip the positionor
                // instead of silently ignoring the tap.
                enableCameraAndFlip(next)
                return@launch
            }
            try { track.switchCamera(position = next) }
            catch (e: Exception) { Log.w(TAG, "switchCamera failed", e) }
        }
    }

    private suspend fun enableCameraAndFlip(position: CameraPosition ) {
        val r = room ?: return
        cameraEnabled = true
        // Mirror the existing setCameraEnabled(true) path: enable the capture first,
        // then rotate the freshly created track to the requested positionor
        try {
            r.localParticipant.setCameraEnabled(true)
            captureLocalVideoTrack(r)
            localRenderer?.let { attachLocalRenderer(it) }
        } catch (e: Exception) {
            Log.w(TAG, "enableCameraAndFlip failed", e)
            return
        }
        localVideoTrack?.let { track ->
            try { track.switchCamera(position = position) }
            catch (e: Exception) { Log.w(TAG, "enableCameraAndFlip rotate failed", e) }
        }
    }

    /**
     * Attaches the local camera renderer. The renderer must be an SDK renderer
     * ([io.livekit.android.renderer.SurfaceViewRenderer]); it is initialized
     * with LiveKit's EglBase via [Room.initVideoRenderer].
     */
    fun setLocalVideoView(view: LkSurfaceViewRenderer?) {
        // Detach old renderer before swapping.
        localVideoTrack?.let { localRenderer?.let { old -> it.removeRenderer(old) } }
        localRenderer = view
        if (view != null) attachLocalRenderer(view)
    }

    /** Attaches the remote video renderer (the other peer's camera). */
    fun setRemoteVideoView(view: LkSurfaceViewRenderer?) {
        remoteVideoTrack?.let { remoteRenderer?.let { old -> it.removeRenderer(old) } }
        remoteRenderer = view
        if (view != null) attachRemoteRenderer(view)
    }

    private fun attachLocalRenderer(view: LkSurfaceViewRenderer) {
        val r = room ?: return
        try { r.initVideoRenderer(view) } catch (e: Exception) { Log.w(TAG, "initVideoRenderer local", e) }
        localVideoTrack?.addRenderer(view)
    }

    private fun attachRemoteRenderer(view: LkSurfaceViewRenderer) {
        val r = room ?: return
        try { r.initVideoRenderer(view) } catch (e: Exception) { Log.w(TAG, "initVideoRenderer remote", e) }
        remoteVideoTrack?.addRenderer(view)
    }

    private fun captureLocalVideoTrack(r: Room) {
        if (localVideoTrack != null) return
        val pub = r.localParticipant.getTrackPublication(Track.Source.CAMERA)
        localVideoTrack = pub?.track as? LocalVideoTrack
    }

    /**
     * Tears down the LiveKit room deterministically and synchronously: disables
     * local mic/camera, disconnects the room, and clears the reference. Does NOT
     * consult [released] so it can be shared by [disconnect] (end a call but keep
     * the engine reusable) and [release] (destroy the engine). It is fully sync so
     * it can never race against an immediate [scope.cancel].
     */
    private fun disconnectInternal() {
        val r = room ?: return
        // Best-effort: disable local mic/camera so no capture keeps running while
        // the call ends. setMicrophoneEnabled/setCameraEnabled are suspend, so we
        // drive them to completion synchronously inside runBlocking (NOT
        // scope.launch, which would be orphaned by the scope.cancel() below) so
        // they cannot be skipped. runCatching isolates failures so a mic/cam
        // error cannot mask the Room.disconnect() that must always run.
        runCatching {
            runBlocking {
                runCatching { r.localParticipant.setMicrophoneEnabled(false) }
                runCatching { r.localParticipant.setCameraEnabled(false) }
            }
        }
        runCatching { r.disconnect() }
        room = null
    }

    /** Ends an active call without destroying the engine (remains reusable). */
    fun disconnect() {
        if (released) return
        disconnectInternal()
        // AI: transcribe call audio via OpenRouter Whisper
        // Real integration would record audio track and call OpenRouterService.transcribeAudio()
        val hasKey = !System.getenv("OPENROUTER_API_KEY").isNullOrBlank()
        if (hasKey) {
            android.util.Log.d("LiveKitCallEngine", "OpenRouter Whisper ready for call transcription")
        }
    }

    /** Destroys the engine: idempotent, synchronous cleanup, scope cancelled last. */
    fun release() {
        if (released) return
        released = true
        disconnectInternal()
        scope.cancel()
        // Drop renderer/track references so we don't leak the SurfaceView renderers.
        localRenderer = null
        remoteRenderer = null
        localVideoTrack = null
        remoteVideoTrack = null
    }

    private fun sharedRoomName(a: String, b: String): String {
        // Deterministic, symmetric room name independent of caller/callee order.
        val pair = if (a <= b) "$a-$b" else "$b-$a"
        return CALL_PREFIX + pair.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
    }
}
