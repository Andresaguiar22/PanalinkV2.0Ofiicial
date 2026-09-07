package com.example.call

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.example.data.repository.LiveKitFeatureGate
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import org.webrtc.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * CallManager is the master coordinator for all signaling, peer connection management,
 * call states, audio devices (speaker, earpiece, mic), and durations.
 */
class CallManager private constructor(private val context: Context) : WebRTCClient.WebRTCListener {

    companion object {
        private const val TAG = "CallManager"
        @Volatile
        private var instance: CallManager? = null

        private const val INITIAL_CONNECTION_TIMEOUT = 20000L
        private const val ICE_RESTART_TIMEOUT = 30000L
        // How long the caller waits for the peer to answer before declaring
        // the call unanswered ("no disponible"). Generous enough to allow the
        // recipient to be notified (Realtime/FCM) and tap Accept.
        private const val NO_ANSWER_TIMEOUT_MS = 45000L

        fun getInstance(context: Context): CallManager {
            return instance ?: synchronized(this) {
                instance ?: CallManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var signalingClient: CallSignaling? = null
    private var webRtcClient: WebRTCClient? = null
    // LiveKit SFU media transport, used for audio calls when LiveKitFeatureGate is ON.
    private var liveKitCallEngine: LiveKitCallEngine? = null
    private val audioController = AudioController(context)

    // State flows representing call details
    private val _callState = MutableStateFlow<CallState>(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState

    private val _callType = MutableStateFlow(CallType.AUDIO)
    val callType: StateFlow<CallType> = _callType

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration


    private val _opponentName = MutableStateFlow<String?>(null)
    private val _opponentId = MutableStateFlow<String?>(null)
    val opponentId: StateFlow<String?> = _opponentId
    val opponentName: StateFlow<String?> = _opponentName

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn

    private val _isCameraOn = MutableStateFlow(true)
    val isCameraOn: StateFlow<Boolean> = _isCameraOn

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    // Local & Remote view renderers (referenced from Compose views)
    private var localVideoView: SurfaceViewRenderer? = null
    private var remoteVideoView: SurfaceViewRenderer? = null
    private var remoteVideoTrack: VideoTrack? = null

    // EglBase for video context sharing
    val eglBaseContext: EglBase.Context by lazy { EglBase.create().eglBaseContext }

    private var durationJob: Job? = null
    // Set when a call_end/rejected/busy arrives while an incoming call offer is
    // still en-route (race condition): guards against starting the incoming
    // foreground service (ringtone+vibration) after the caller already hung up.
    @Volatile
    private var incomingCallCancelled = false
    private var currentUserId: String? = null
    private var incomingSdpOffer: String? = null

    // WebRTC connection state hardening
    private var isInitiator = false
    private var remoteDescriptionSet = false
    private val pendingIceCandidates = mutableListOf<IceCandidate>()

    // Connection Guard Timer
    private var connectionGuardJob: Job? = null
    private var isCleaningCall = false

    /**
     * Initializes signaling for the logged-in user.
     */
    fun initialize(userId: String) {
        if (currentUserId == userId && signalingClient != null) {
            // If already initialized but signaling is down, try to reconnect
            if (!_isConnected.value) {
                Log.d(TAG, "Already initialized for $userId but disconnected. Reconnecting signaling client.")
                signalingClient?.connect()
            }
            return
        }
        currentUserId = userId
        
        Log.d(TAG, "Initializing Signaling Client for $userId")
        signalingClient?.disconnect()
        signalingClient = CallSignaling(userId)
        observeSignalingEvents()
    }

    /**
     * Force a full reconnection of the signaling engine.
     */
    fun forceReconnect() {
        val userId = currentUserId ?: return
        Log.i(TAG, "Forcing Signaling Reconnection for $userId")
        signalingClient?.disconnect()
        signalingClient = null
        initialize(userId)
    }

    fun handleFCMIncomingCall(callerId: String, callerName: String, typeStr: String, sdp: String? = null) {
        if (_callState.value == CallState.IDLE) {
            try {
                Log.d(TAG, "Handling incoming call from FCM directly: $callerId")
                _opponentId.value = callerId
                _opponentName.value = callerName
                _opponentId.value = callerId
                val isVideo = typeStr == "video"
                updateCallState(CallState.RINGING)
                
                if (!sdp.isNullOrEmpty()) {
                    incomingSdpOffer = sdp
                }

                audioController.setMode(AudioManager.MODE_RINGTONE)

                // Start foreground service for incoming call with FullScreenIntent
                CallForegroundService.startIncomingCall(context, callerName, isVideo)
            } catch (e: Exception) {
                Log.e(TAG, "Critical error in handleFCMIncomingCall", e)
                resetCall()
            }
        }
    }

    private fun observeSignalingEvents() {
        val client = signalingClient ?: return
        
        mainScope.launch {
            client.connectionStatusFlow.collect { connected ->
                _isConnected.value = connected
                Log.d(TAG, "Signaling connection changed: $connected")
            }
        }

        mainScope.launch {
            client.incomingCallFlow.collect { incoming ->
                Log.d(TAG, "Processing incoming call from: ${incoming.callerId}")
                if (_callState.value != CallState.IDLE) {
                    if ((_callState.value == CallState.RINGING || _callState.value is CallState.CONNECTING || _callState.value == CallState.CONNECTED || _callState.value == CallState.RECONNECTING) && _opponentId.value == incoming.callerId) {
                        Log.d(TAG, "Already ringing/connecting/connected for the same caller, updating incoming SDP offer")
                        incomingSdpOffer = incoming.sdp
                        if ((_callState.value is CallState.CONNECTING || _callState.value == CallState.CONNECTED || _callState.value == CallState.RECONNECTING) && !incoming.sdp.isNullOrEmpty()) {
                            Log.d(TAG, "Recipient in active/connecting/reconnecting call, applying newly received remote offer SDP")
                            remoteDescriptionSet = false
                            webRtcClient?.setRemoteDescription(
                                SessionDescription(SessionDescription.Type.OFFER, incoming.sdp),
                                object : SdpObserver {
                                    override fun onCreateSuccess(p0: SessionDescription?) {}
                                    override fun onSetSuccess() {
                                        Log.d(TAG, "Remote description set, creating WebRTC Answer")
                                        mainScope.launch {
                                            remoteDescriptionSet = true
                                            flushPendingIceCandidates()
                                        }
                                        webRtcClient?.createAnswer(object : SdpObserver {
                                            override fun onCreateSuccess(answerSdp: SessionDescription?) {
                                                answerSdp?.let {
                                                    signalingClient?.sendAnswer(incoming.callerId, it.description)
                                                }
                                            }
                                            override fun onSetSuccess() {}
                                            override fun onCreateFailure(p0: String?) {}
                                            override fun onSetFailure(p0: String?) {}
                                        })
                                    }
                                    override fun onCreateFailure(p0: String?) {}
                                    override fun onSetFailure(p0: String?) {}
                                }
                            )
                        }
                        return@collect
                    }
                    Log.d(TAG, "User busy, automatically rejecting call with call_busy")
                    client.sendBusy(incoming.callerId)
                    return@collect
                }
                
                // Race-guard: if a call_end/rejected/busy arrived while this offer
                // was en route, do NOT start the incoming FGS (ringtone+vibration).
                if (incomingCallCancelled) {
                    incomingCallCancelled = false
                    return@collect
                }

                try {
                    _opponentId.value = incoming.callerId
                    _opponentName.value = incoming.callerName
                    _opponentId.value = incoming.callerId
                    val isVideo = incoming.callType == "video"
                    updateCallState(CallState.RINGING)
                    
                    incomingSdpOffer = incoming.sdp
                    
                    // Initialize audio manager for ringtone path
                    audioController.setMode(AudioManager.MODE_RINGTONE)

                    // Start foreground service for incoming call
                    CallForegroundService.startIncomingCall(context, incoming.callerName, isVideo)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing incoming call flow", e)
                    resetCall()
                }
            }
        }

        mainScope.launch {
            client.callAnswerFlow.collect { data ->
                Log.d(TAG, "Call accepted by peer — stopping ringback, moving to CONNECTING")
                stopRingbackTone()
                updateCallState(CallState.CONNECTING)
                outgoingCallJob?.cancel()
                outgoingCallJob = null
                val sdpStr = data.optString("sdp")
                webRtcClient?.setRemoteDescription(
                    SessionDescription(SessionDescription.Type.ANSWER, sdpStr),
                    object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(TAG, "Remote description set successfully on initiator side")
                            mainScope.launch {
                                remoteDescriptionSet = true
                                flushPendingIceCandidates()
                            }
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(err: String?) {
                            Log.e(TAG, "Answer SDP setRemoteDescription failed: $err")
                        }
                    }
                )
            }
        }

        mainScope.launch {
            client.callRejectedFlow.collect { data ->
                Log.d(TAG, "Call rejected by peer — playing busy tone")
                stopRingbackTone()
                CallForegroundService.stopService(context)
                incomingCallCancelled = true
                playBusyTone()
                updateCallState(CallState.BUSY)
                delay(1500)
                resetCall()
            }
        }

        mainScope.launch {
            client.callEndedFlow.collect { data ->
                Log.d(TAG, "Call ended by peer")
                stopRingbackTone()
                CallForegroundService.stopService(context)
                incomingCallCancelled = true
                updateCallState(CallState.ENDED)
                delay(1000)
                resetCall()
            }
        }

        mainScope.launch {
            client.callBusyFlow.collect { data ->
                Log.d(TAG, "Peer is busy — playing busy tone")
                stopRingbackTone()
                CallForegroundService.stopService(context)
                incomingCallCancelled = true
                playBusyTone()
                updateCallState(CallState.BUSY)
                delay(1500)
                resetCall()
            }
        }

        mainScope.launch {
            client.iceCandidateReceivedFlow.collect { data ->
                val sdpMid = data.optString("sdpMid")
                val sdpMLineIndex = data.optInt("sdpMLineIndex")
                val sdp = data.optString("candidate")
                val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                if (remoteDescriptionSet) {
                    webRtcClient?.addIceCandidate(candidate)
                } else {
                    Log.d(TAG, "Queueing ICE candidate prior to remote description: $sdp")
                    pendingIceCandidates.add(candidate)
                }
            }
        }
    }

    private var outgoingCallJob: Job? = null

    /**
     * Start an outgoing call.
     *
     * The call is always allowed to be placed even if the recipient appears
     * offline — presence is best-effort and frequently stale (a user who just
     * came online may not yet be in the presence map). If the peer never
     * answers within the no-answer timeout, the call fails with a friendly
     * "no disponible" message rather than blocking the dial outright.
     */
    fun startCall(targetUserId: String, targetUserName: String, type: CallType) {
        if (_callState.value != CallState.IDLE) return

        com.example.data.repository.PresenceRepository.updateMyStatus(com.example.data.repository.UserPresenceStatus.BUSY)

        _opponentId.value = targetUserId
        _opponentName.value = targetUserName
        _opponentId.value = targetUserId
        _callType.value = type

        // Surface the outgoing state immediately so the in-call UI renders
        // (the call screen is gated on callState != IDLE). Without this the
        // screen never appeared while the call was ringing/connecting.
        updateCallState(CallState.OUTGOING(targetUserId))

        // Play the in-call ringback tone ("tu-tu-tu") while the peer hasn't
        // answered. Stopped on CONNECTED/ended/rejected/busy/no-answer.
        startRingbackTone()

        outgoingCallJob?.cancel()
        outgoingCallJob = mainScope.launch {
            delay(NO_ANSWER_TIMEOUT_MS)
            if (_callState.value is CallState.OUTGOING || _callState.value == CallState.CONNECTING) {
                // Peer never picked up — treat as unavailable.
                stopRingbackTone()
                signalingClient?.endCall(targetUserId) // cancel any pending ring on the peer
                updateCallState(CallState.FAILED)
                saveCallLog(com.example.data.model.CallLogStatus.MISSED)
                delay(2500)
                resetCall()
            }
        }
        
        audioController.startCall()
        
        // Start foreground service for outgoing call
        CallForegroundService.startService(context, targetUserName, type == CallType.VIDEO)

        // Send metadata call_request first so recipient starts ringing/receiving immediately
        val callerName = SupabaseClient.currentProfile?.displayName 
            ?: SupabaseClient.currentUser?.email 
            ?: "Panalink User"
        signalingClient?.sendCallRequest(
            targetUserId = targetUserId,
            callerName = callerName,
            callType = if (type == CallType.VIDEO) "video" else "voice"
        )

        // Fire an FCM push to the recipient so the call rings even when their
        // app is closed (Realtime broadcast only reaches devices with an open
        // WS). The push is data-only with high priority so onMessageReceived
        // runs and starts the ringtone + incoming-call screen directly.
        triggerIncomingCallPush(targetUserId, callerName, type)

        // Start WebRTC/LiveKit session as initiator right away.
        startWebRTCSession(isInitiator = true)
    }

    /**
     * Accept incoming call.
     *
     * Allowed whenever a call is in progress (RINGING primarily). The strict
     * `!is RINGING` guard silently no-oped when the state had already advanced
     * to CONNECTING (e.g. duplicate Realtime emission), leaving the user unable
     * to accept. We now accept from any active incoming state.
     */
    fun acceptCall() {
        val state = _callState.value
        if (state == CallState.IDLE || state is CallState.OUTGOING ||
            state == CallState.CONNECTED || state == CallState.RECONNECTING) {
            Log.w(TAG, "acceptCall ignored in state $state")
            return
        }
        updateCallState(CallState.CONNECTING)

        // Stop the incoming-call ringtone and switch to the active-call notification.
        stopRingbackTone()
        CallForegroundService.stopService(context)
        CallForegroundService.startService(context, _opponentName.value ?: "Llamada PanaLink", _callType.value == CallType.VIDEO)

        val target = _opponentId.value ?: ""
        if (target.isNotEmpty()) signalingClient?.acceptCall(target)
        
        audioController.startCall()
        
        // Start WebRTC session as non-initiator to apply remote offer and create/send answer
        startWebRTCSession(isInitiator = false)
    }

    /**
     * Reject incoming call.
     */
    fun rejectCall() {
        val state = _callState.value
        if (state == CallState.IDLE) {
            Log.w(TAG, "rejectCall ignored in state IDLE")
            return
        }
        stopRingbackTone()
        val target = _opponentId.value ?: ""
        if (target.isNotEmpty()) signalingClient?.rejectCall(target)
        
        // Log missed/rejected call
        saveCallLog(com.example.data.model.CallLogStatus.REJECTED)
        
        resetCall()
    }

    /**
     * End ongoing call. Always tears down local state and notifies the peer.
     *
     * This is the single, guaranteed teardown entry point: it stops the
     * ringback, releases the LiveKit room / WebRTC peer connection, returns
     * audio focus and stops the foreground service — regardless of the current
     * call state. Safe to call repeatedly (idempotent): once the call is back to
     * IDLE a second call is a no-op.
     */
    fun endCall() {
        val state = _callState.value
        // If we're already idle there's nothing to tear down.
        if (state == CallState.IDLE) {
            stopRingbackTone()
            return
        }
        stopRingbackTone()
        val target = _opponentId.value
        if (target != null && state != CallState.IDLE) {
            signalingClient?.endCall(target)
        }

        // Log completed call with duration
        if (state == CallState.CONNECTED) {
            saveCallLog(com.example.data.model.CallLogStatus.COMPLETED)
        } else if (state is CallState.OUTGOING) {
            saveCallLog(com.example.data.model.CallLogStatus.CANCELLED)
        }

        resetCall()
    }

    private fun startWebRTCSession(isInitiator: Boolean) {
        this.isInitiator = isInitiator
        Log.d(TAG, "Starting WebRTC Session, isInitiator=$isInitiator")

        // LiveKit SFU path for audio AND video calls: both peers join a shared
        // LiveKit room; the SDP/ICE exchange below is skipped entirely. When the
        // gate is OFF, both call types fall through to the legacy P2P WebRTC flow.
        if (LiveKitFeatureGate.isEnabled()) {
            startLiveKitSession()
            return
        }

        webRtcClient?.close()
        webRtcClient = WebRTCClient(context, eglBaseContext, this)

        val hasAudio = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCamera = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED

        // Standard setup: start audio capturer if permission granted
        if (hasAudio) {
            webRtcClient?.startLocalAudio()
        } else {
            Log.w(TAG, "Missing RECORD_AUDIO permission, starting in receive-only mode for audio")
        }

        // If video call, start local camera and render it if permission granted
        if (_callType.value == CallType.VIDEO) {
            if (hasCamera) {
                localVideoView?.let { view ->
                    webRtcClient?.startLocalVideo(view)
                }
            } else {
                Log.w(TAG, "Missing CAMERA permission, starting in receive-only mode for video")
            }
        }

        if (isInitiator) {
            webRtcClient?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(offerSdp: SessionDescription?) {
                    offerSdp?.let {
                        val callerName = SupabaseClient.currentProfile?.displayName 
                            ?: SupabaseClient.currentUser?.email 
                            ?: "Panalink User"
                        signalingClient?.sendOffer(
                            targetUserId = _opponentId.value ?: "",
                            offerSdp = it.description,
                            callerName = callerName,
                            callType = if (_callType.value == CallType.VIDEO) "video" else "voice"
                        )
                    }
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(p0: String?) {}
                override fun onSetFailure(p0: String?) {}
            })
        } else {
            val sdpStr = incomingSdpOffer
            if (!sdpStr.isNullOrEmpty()) {
                Log.d(TAG, "Applying remote offer SDP on receiver side")
                remoteDescriptionSet = false
                webRtcClient?.setRemoteDescription(
                    SessionDescription(SessionDescription.Type.OFFER, sdpStr),
                    object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(TAG, "Remote description set, creating WebRTC Answer")
                            mainScope.launch {
                                remoteDescriptionSet = true
                                flushPendingIceCandidates()
                            }
                            webRtcClient?.createAnswer(object : SdpObserver {
                                override fun onCreateSuccess(answerSdp: SessionDescription?) {
                                    answerSdp?.let {
                                        signalingClient?.sendAnswer(_opponentId.value ?: "", it.description)
                                    }
                                }
                                override fun onSetSuccess() {}
                                override fun onCreateFailure(p0: String?) {}
                                override fun onSetFailure(p0: String?) {}
                            })
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(err: String?) {
                            Log.e(TAG, "Offer SDP setRemoteDescription failed: $err")
                        }
                    }
                )
            } else {
                Log.e(TAG, "Error: incomingSdpOffer is null or empty when starting receiver WebRTC session!")
            }
        }
    }

    /**
     * Joins a shared LiveKit room to carry the 1-1 audio call through the SFU.
     * Both the caller and callee derive the same room name, so no SDP/ICE
     * signaling is needed; ring/accept/end are still handled via Socket.IO by
     * the caller flow. Connection state is driven by [LiveKitCallEngine].
     */
    private fun startLiveKitSession() {
        liveKitCallEngine?.release()
        val myId = currentUserId
        val otherId = _opponentId.value
        if (myId.isNullOrEmpty() || otherId.isNullOrEmpty()) {
            Log.e(TAG, "Cannot start LiveKit session: missing participant ids")
            updateCallState(CallState.FAILED)
            return
        }
        val isVideo = _callType.value == CallType.VIDEO
        liveKitCallEngine = LiveKitCallEngine(
            context = context,
            myUserId = myId,
            object : LiveKitCallEngine.Listener {
                override fun onConnected() {
                    mainScope.launch {
                        if (_callState.value != CallState.CONNECTED) {
                            updateCallState(CallState.CONNECTED)
                            startDurationTimer()
                        }
                    }
                }
                override fun onReconnecting() {
                    mainScope.launch {
                        if (_callState.value != CallState.CONNECTED) {
                            // Initial connect still in progress — keep the call in
                            // CONNECTING so the Connection Guard stays armed while
                            // the engine retries, rather than bouncing to FAILED.
                            if (_callState.value !is CallState.CONNECTING &&
                                _callState.value !is CallState.OUTGOING) {
                                updateCallState(CallState.RECONNECTING)
                            }
                        } else {
                            updateCallState(CallState.RECONNECTING)
                        }
                    }
                }
                override fun onDisconnected() {
                    mainScope.launch {
                        val state = _callState.value
                        when {
                            // A live call dropped — try to recover.
                            state == CallState.CONNECTED || state == CallState.RECONNECTING -> {
                                updateCallState(CallState.RECONNECTING)
                            }
                            // The engine exhausted its initial-connect retries
                            // before the call ever came up — declare failure.
                            state is CallState.CONNECTING || state is CallState.OUTGOING || state == CallState.RINGING -> {
                                terminateCallWithFailure()
                            }
                            else -> {
                                // IDLE/ENDED/FAILED — nothing to do.
                            }
                        }
                    }
                }
                override fun onParticipantLeft() {
                    mainScope.launch {
                        // The peer left the SFU without signaling `call_end` (app
                        // killed, network dropped). Treat it as the call being over instead
                        // of waiting for the Connection Guard timeout.

                        if (_callState.value == CallState.CONNECTED ||
                            _callState.value == CallState.RECONNECTING) {
                            // Log BEFORE flipping the state so saveCallLog still sees
                            // OUTGOING/CONNECTED and derives caller/receiver correctly.
                            saveCallLog(com.example.data.model.CallLogStatus.COMPLETED)
                            updateCallState(CallState.ENDED)
                            // Skip signaling:the peer already left the room. The rest of the
                            // teardown (FGS, WebRTC/LiveKit, audio focus, timer) lives in
                            // resetCall()..
                            delay(1000)
                            resetCall()
                        }
                    }
                }
            },
        ).apply {
            setMicEnabled(true)
            // For video calls the camera starts on; the UI toggles it afterwards.
            if (isVideo) setCameraEnabled(_isCameraOn.value)
            connect(otherId, isVideo = isVideo)
        }
    }


    /**
     * Set rendering views from Compose UI for local/remote video.
     */
    fun setVideoViews(local: SurfaceViewRenderer?, remote: SurfaceViewRenderer?) {
        localVideoView = local
        remoteVideoView = remote
        
        // Re-add remote sink if video track is already running
        remoteVideoTrack?.let { track ->
            remote?.let { view -> track.addSink(view) }
        }
    }

    /** Whether the active call is carried over the LiveKit SFU. */
    fun isLiveKitActive(): Boolean = liveKitCallEngine != null

    /**
     * Attach LiveKit SDK renderers for a video call routed through the SFU. The
     * renderers MUST be [io.livekit.android.renderer.SurfaceViewRenderer] (the
     * SDK subclass), not the standalone org.webrtc SurfaceViewRenderer used by
     * the legacy WebRTC path.
     */
    fun setLiveKitVideoViews(
        local: io.livekit.android.renderer.SurfaceViewRenderer?,
        remote: io.livekit.android.renderer.SurfaceViewRenderer?,
    ) {
        liveKitCallEngine?.setLocalVideoView(local)
        liveKitCallEngine?.setRemoteVideoView(remote)
    }

    fun toggleMute() {
        val newMute = !_isMuted.value
        _isMuted.value = newMute
        liveKitCallEngine?.let { it.setMicEnabled(!newMute); return }
        webRtcClient?.toggleMic(!newMute)
    }

    fun toggleSpeaker() {
        val newSpeaker = !_isSpeakerOn.value
        _isSpeakerOn.value = newSpeaker
        audioController.setAudioDevice(if (newSpeaker) AudioDevice.SPEAKER else AudioDevice.EARPIECE)
    }

    fun toggleCamera() {
        val newCamera = !_isCameraOn.value
        _isCameraOn.value = newCamera
        liveKitCallEngine?.let { it.setCameraEnabled(newCamera); return }
        webRtcClient?.toggleVideo(newCamera)
    }

    fun switchCamera() {
        liveKitCallEngine?.let { it.switchCamera(); return }
        webRtcClient?.switchCamera()
    }

    private fun startDurationTimer() {
        durationJob?.cancel()
        _duration.value = 0L
        durationJob = mainScope.launch {
            while (isActive && _callState.value == CallState.CONNECTED) {
                delay(1000)
                _duration.value = _duration.value + 1
            }
        }
    }

    private fun updateCallState(newState: CallState) {
        if (_callState.value == newState) return
        // The in-call ringback ("tu-tu-tu") and any leftover busy tone MUST stop
        // the moment the call becomes connected — otherwise the cadence keeps
        // playing over the live voice audio (echo/distortion) when the peer
        // joined the LiveKit room without the accept signal reaching us first.
        if (newState == CallState.CONNECTED) {
            stopRingbackTone()
            try { busyTone?.stopTone() } catch (_: Exception) {}
            try { busyTone?.release() } catch (_: Exception) {}
            busyTone = null
        }
        _callState.value = newState
        evaluateConnectionGuard(newState)
    }

    private fun evaluateConnectionGuard(state: CallState) {
        when (state) {
            is CallState.CONNECTING -> {
                Log.d(TAG, "Connection Guard: Transitioned to CONNECTING. Starting $INITIAL_CONNECTION_TIMEOUT ms timer.")
                startConnectionGuard(INITIAL_CONNECTION_TIMEOUT)
            }
            is CallState.RECONNECTING -> {
                Log.d(TAG, "Connection Guard: Transitioned to RECONNECTING. Starting $ICE_RESTART_TIMEOUT ms timer.")
                startConnectionGuard(ICE_RESTART_TIMEOUT)
            }
            is CallState.OUTGOING -> {
                // No connection guard while ringing the peer — the dedicated
                // no-answer timeout (NO_ANSWER_TIMEOUT_MS) in startCall handles
                // the "peer never picked up" case. The 20s guard would otherwise
                // kill the call before the recipient even sees the notification.
                stopConnectionGuard()
            }
            CallState.CONNECTED -> {
                Log.d(TAG, "Connection Guard: Connected successfully. Stopping timer.")
                stopConnectionGuard()
            }
            CallState.IDLE, CallState.FAILED, CallState.ENDED, CallState.CANCELLED, CallState.BUSY -> {
                Log.d(TAG, "Connection Guard: State is inactive ($state). Stopping timer.")
                stopConnectionGuard()
            }
            else -> {
                // For other states (e.g., RINGING), stop if active or no-op
                stopConnectionGuard()
            }
        }
    }

    private fun startConnectionGuard(timeoutMs: Long) {
        connectionGuardJob?.cancel()
        connectionGuardJob = mainScope.launch {
            delay(timeoutMs)
            terminateCallWithFailure()
        }
    }

    private fun stopConnectionGuard() {
        connectionGuardJob?.cancel()
        connectionGuardJob = null
    }

    private fun terminateCallWithFailure() {
        if (_callState.value == CallState.CONNECTED) {
            Log.d(TAG, "Connection Guard: Call is already connected. Aborting timeout.")
            return
        }
        if (isCleaningCall) return
        isCleaningCall = true

        Log.e(TAG, "Connection Guard triggered! Call setup/reconnection timed out.")
        updateCallState(CallState.FAILED)
        saveCallLog(com.example.data.model.CallLogStatus.MISSED)
        
        mainScope.launch {
            delay(3000)
            resetCall()
        }
    }

    private fun resetCall() {
        Log.d(TAG, "Resetting Call Manager states")
        stopRingbackTone()
        try { busyTone?.stopTone() } catch (_: Exception) {}
        try { busyTone?.release() } catch (_: Exception) {}
        busyTone = null
        outgoingCallJob?.cancel()
        outgoingCallJob = null
        com.example.data.repository.PresenceRepository.updateMyStatus(com.example.data.repository.UserPresenceStatus.ONLINE)
        stopConnectionGuard()
        isCleaningCall = false

        // Stop foreground service
        CallForegroundService.stopService(context)

        durationJob?.cancel()
        durationJob = null
        _duration.value = 0L
        incomingSdpOffer = null

        isInitiator = false
        remoteDescriptionSet = false
        pendingIceCandidates.clear()

        // Hard-tear-down the media transport FIRST so no peer audio keeps
        // streaming in the background once the UI leaves the call screen.
        try { webRtcClient?.close() } catch (e: Exception) { Log.w(TAG, "webRtcClient.close error", e) }
        webRtcClient = null
        try { liveKitCallEngine?.release() } catch (e: Exception) { Log.w(TAG, "liveKitCallEngine.release error", e) }
        liveKitCallEngine = null

        updateCallState(CallState.IDLE)
        _opponentId.value = null
        _opponentName.value = null
        _opponentId.value = null
        _isMuted.value = false
        _isSpeakerOn.value = false
        _isCameraOn.value = true

        localVideoView = null
        remoteVideoView = null
        remoteVideoTrack = null

        // Return audio focus + restore normal audio mode so the earpiece stops
        // being claimed and the device goes back to normal playback.
        audioController.stopCall()
    }

    private fun flushPendingIceCandidates() {
        Log.d(TAG, "Flushing ${pendingIceCandidates.size} pending ICE candidates to WebRTCClient")
        pendingIceCandidates.forEach { candidate ->
            webRtcClient?.addIceCandidate(candidate)
        }
        pendingIceCandidates.clear()
    }

    // ------------------------------------------------------------------
    // In-call tones: ringback ("tu-tu-tu" while the peer hasn't answered)
    // and busy tone (when the peer rejects or is already in a call).
    // Uses ToneGenerator on the voice-call stream so it plays through the
    // earpiece/speaker the call audio is routed to.
    // ------------------------------------------------------------------
    private var ringbackTone: android.media.ToneGenerator? = null
    private var ringbackJob: Job? = null
    private var busyTone: android.media.ToneGenerator? = null

    /**
     * Plays the international ringback cadence (1s on, 3s off) until
     * [stopRingbackTone] is called. Safe to call repeatedly.
     */
    private fun startRingbackTone() {
        if (ringbackTone != null) return
        try {
            val tone = android.media.ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
            ringbackTone = tone
            ringbackJob?.cancel()
            ringbackJob = mainScope.launch {
                while (isActive && ringbackTone != null) {
                    try { tone.startTone(android.media.ToneGenerator.TONE_SUP_RINGTONE) } catch (_: Exception) {}
                    delay(2000) // audible burst ~2s, then silence ~2s (cadence ~4s)
                    try { tone.stopTone() } catch (_: Exception) {}
                    delay(2000)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not start ringback tone", e)
            ringbackTone = null
        }
    }

    private fun stopRingbackTone() {
        ringbackJob?.cancel()
        ringbackJob = null
        try { ringbackTone?.stopTone() } catch (_: Exception) {}
        try { ringbackTone?.release() } catch (_: Exception) {}
        ringbackTone = null
    }

    /**
     * Plays a short busy/congestion tone (~1.5s) when the peer rejects or is busy.
     */
    private fun playBusyTone() {
        try {
            val tone = android.media.ToneGenerator(AudioManager.STREAM_VOICE_CALL, 100)
            busyTone = tone
            tone.startTone(android.media.ToneGenerator.TONE_CDMA_SIGNAL_OFF)
            mainScope.launch {
                delay(1500)
                try { tone.stopTone() } catch (_: Exception) {}
                try { tone.release() } catch (_: Exception) {}
                busyTone = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not play busy tone", e)
        }
    }

    /**
     * Fires an FCM data-only push to the recipient so the incoming call rings
     * even when their app is closed (the Realtime broadcast only reaches
     * devices with an open websocket). Best-effort: failures are logged but do
     * not block the call flow, since the Realtime path covers the open-app case.
     */
    private fun triggerIncomingCallPush(receiverId: String, callerName: String, type: CallType) {
        val myId = currentUserId ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val baseUrl = SupabaseClient.supabaseUrl
                val url = if (baseUrl.contains(".supabase.co")) {
                    baseUrl.trimEnd('/') + "/functions/v1/send-call-push"
                } else {
                    "${baseUrl.trimEnd('/')}/functions/v1/send-call-push"
                }
                val token = SupabaseClient.currentToken
                val payload = JSONObject().apply {
                    put("receiver_id", receiverId)
                    put("caller_id", myId)
                    put("caller_name", callerName)
                    put("call_type", if (type == CallType.VIDEO) "video" else "voice")
                }
                val jsonBody = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .post(jsonBody)
                    .apply {
                        addHeader("apikey", SupabaseClient.supabaseAnonKey)
                        if (!token.isNullOrEmpty()) addHeader("Authorization", "Bearer $token")
                    }
                    .build()
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val resp = client.newCall(request).execute()
                Log.d(TAG, "send-call-push response: ${resp.code} ${resp.body?.string()?.take(200)}")
            } catch (e: Exception) {
                Log.w(TAG, "send-call-push failed (best-effort)", e)
            }
        }
    }

    private fun triggerIceRestart() {
        if (isInitiator) {
            Log.d(TAG, "We are the initiator. Executing restartIce and creating new offer...")
            webRtcClient?.restartIce()
            webRtcClient?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(offerSdp: SessionDescription?) {
                    offerSdp?.let {
                        val callerName = SupabaseClient.currentProfile?.displayName 
                            ?: SupabaseClient.currentUser?.email 
                            ?: "Panalink User"
                        signalingClient?.sendOffer(
                            targetUserId = _opponentId.value ?: "",
                            offerSdp = it.description,
                            callerName = callerName,
                            callType = if (_callType.value == CallType.VIDEO) "video" else "voice"
                        )
                    }
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(p0: String?) {}
                override fun onSetFailure(p0: String?) {}
            })
        } else {
            Log.d(TAG, "We are the receiver. Waiting for the initiator to restart ICE...")
        }
    }

    /**
     * Saves a call log event and optionally triggers a chat message.
     */
    private fun saveCallLog(status: com.example.data.model.CallLogStatus) {
        val opponentId = _opponentId.value ?: return
        val currentUid = SupabaseClient.currentUser?.id ?: return
        val duration = _duration.value
        val isVideo = _callType.value == CallType.VIDEO

        val log = com.example.data.model.CallLog(
            id = java.util.UUID.randomUUID().toString(),
            callerId = if (_callState.value is CallState.OUTGOING) currentUid else opponentId,
            receiverId = if (_callState.value is CallState.OUTGOING) opponentId else currentUid,
            type = if (isVideo) com.example.data.model.CallLogType.VIDEO else com.example.data.model.CallLogType.VOICE,
            status = status,
            durationSeconds = duration,
            timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date())
        )

        // Badge de llamadas perdidas en la bottom bar (tab Llamadas)
        if (log.receiverId == currentUid &&
            (status == com.example.data.model.CallLogStatus.MISSED || status == com.example.data.model.CallLogStatus.REJECTED)
        ) {
            com.example.data.repository.BadgeCenter.recordMissedCall(context)
        }

        // In a real app, we would save this to Room and sync with Supabase.
        // For this task, we will simulate adding a message to the chat so it shows up in history.
        mainScope.launch(Dispatchers.IO) {
            try {
                val moshi = com.squareup.moshi.Moshi.Builder()
                    .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                    .build()
                val adapter = moshi.adapter(com.example.data.model.CallLog::class.java)
                val jsonLog = adapter.toJson(log)

                // Resolve chatId and save as a special "call" message
                val chatsRepo = com.example.data.repository.ChatsRepository()
                val chatId = chatsRepo.getChatIdByOtherUserId(opponentId)
                
                if (!chatId.isNullOrEmpty()) {
                    val messagesRepo = com.example.data.repository.MessagesRepository.getInstance()
                    messagesRepo.sendMessage(
                        chatId = chatId,
                        content = jsonLog,
                        messageType = "call",
                        receiverUid = opponentId
                    )
                    Log.d(TAG, "Call log saved successfully as a message in chat $chatId")
                } else {
                    Log.w(TAG, "Could not find chatId to save call log for opponent $opponentId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving call log", e)
            }
        }
    }

    // WebRTCListener implementations
    override fun onIceCandidateCreated(candidate: IceCandidate) {
        val target = _opponentId.value ?: return
        signalingClient?.sendIceCandidate(
            targetUserId = target,
            sdpMid = candidate.sdpMid ?: "",
            sdpMLineIndex = candidate.sdpMLineIndex,
            candidate = candidate.sdp ?: ""
        )
    }

    override fun onRemoteTrackAdded(transceiver: RtpTransceiver) {
        val track = transceiver.receiver.track()
        if (track is VideoTrack) {
            Log.d(TAG, "Remote VideoTrack received and bound")
            remoteVideoTrack = track
            remoteVideoView?.let { view ->
                track.addSink(view)
            }
        }
    }

    override fun onIceConnectionStateChanged(state: PeerConnection.IceConnectionState) {
        mainScope.launch {
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> {
                    if (_callState.value != CallState.CONNECTED) {
                        updateCallState(CallState.CONNECTED)
                        startDurationTimer()
                    }
                }
                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    Log.d(TAG, "ICE Connection DISCONNECTED. Transitioning to RECONNECTING state.")
                    updateCallState(CallState.RECONNECTING)
                    delay(4000)
                    if (_callState.value == CallState.RECONNECTING) {
                        Log.d(TAG, "Still disconnected after 4 seconds. Triggering ICE restart...")
                        triggerIceRestart()
                    }
                }
                PeerConnection.IceConnectionState.FAILED -> {
                    Log.d(TAG, "ICE Connection FAILED. Transitioning to FAILED state.")
                    updateCallState(CallState.FAILED)
                    delay(3000)
                    if (_callState.value == CallState.FAILED) {
                        resetCall()
                    }
                }
                PeerConnection.IceConnectionState.CLOSED -> {
                    resetCall()
                }
                else -> {}
            }
        }
    }

    fun formattedDuration(): String {
        val sec = _duration.value
        val minutes = TimeUnit.SECONDS.toMinutes(sec)
        val seconds = sec - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%02d:%02d", minutes, seconds)
    }

    fun release() {
        resetCall()
        audioController.release()
        signalingClient?.disconnect()
        signalingClient = null
        currentUserId = null
    }
}
