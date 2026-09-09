package com.example.rooms.webrtc

import android.content.Context
import android.util.Log
import com.example.call.IceServerProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.coroutines.resume

/**
 * Motor WebRTC de la Sala de Voz. Malla P2P audio-only: una PeerConnection por
 * participante sentado (max 6 conexiones salientes/entrantes con 7 sillones).
 *
 * ⚠️ PROTOTIPO: una malla P2P de 7 participantes NO es la arquitectura final de
 * produccion (costo de uplink y CPU crece por par). La clase encapsula TODO el
 * WebRTC detras de esta interfaz para poder migrar a SFU despues sin tocar
 * VoiceRoomScreen ni el modelo de datos.
 *
 * Regla de negociacion: el userId lexicograficamente menor inicia la oferta.
 * Mute = AudioTrack.setEnabled(false): la PeerConnection NO se destruye.
 */
class VoiceRoomWebRtcEngine(
    private val context: Context,
    private val myUserId: String,
    private val listener: VoiceRoomEngine.Listener
) : VoiceRoomEngine {

    companion object {
        private const val TAG = "VoiceRoomRtcEngine"
        private const val AUDIO_TRACK_ID = "VR_AUDIO0"
        private const val STREAM_ID = "VR_STREAM"
        private const val SPEAKING_THRESHOLD = 0.02 // audioLevel RTP (0..1)
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var factory: PeerConnectionFactory? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    // Ciclo de vida de peers (anti-zombies) verificable en tests JVM puros.
    private val registry = VoiceRoomPeerRegistry<PeerConnection, IceCandidate>()

    @Volatile private var released = false

    @Synchronized
    override fun initialize() {
        if (factory != null || released) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()

        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        }
        audioSource = factory?.createAudioSource(audioConstraints)
        localAudioTrack = factory?.createAudioTrack(AUDIO_TRACK_ID, audioSource)
        localAudioTrack?.setEnabled(false) // se habilita al tomar sillon
    }

    /** Mute/unmute sin destruir ninguna PeerConnection. */
    override fun setMicEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    /** Llamado cuando un usuario remoto se sienta: decide quien ofrece. */
    override fun onPeerJoined(remoteUserId: String) {
        if (remoteUserId == myUserId || released) return
        ensurePeer(remoteUserId)
        // El menor userId ofrece: evita glare (doble oferta simultanea).
        if (myUserId < remoteUserId) {
            scope.launch { createAndSendOffer(remoteUserId) }
        }
    }

    override fun onPeerLeft(remoteUserId: String) {
        removePeer(remoteUserId)
    }

    override suspend fun onRemoteOffer(fromUserId: String, sdp: String) {
        if (released) return
        val pc = ensurePeer(fromUserId) ?: return
        setRemoteDescription(pc, SessionDescription(SessionDescription.Type.OFFER, sdp))
        drainPendingIce(fromUserId, pc)
        if (released) return
        val answer = createAnswer(pc) ?: return
        setLocalDescription(pc, answer)
        if (!released) listener.sendAnswerTo(fromUserId, answer.description)
    }

    override suspend fun onRemoteAnswer(fromUserId: String, sdp: String) {
        if (released) return
        val pc = registry.get(fromUserId) ?: return
        setRemoteDescription(pc, SessionDescription(SessionDescription.Type.ANSWER, sdp))
        drainPendingIce(fromUserId, pc)
    }

    override fun onRemoteIceCandidate(fromUserId: String, sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        if (released) return
        val ice = IceCandidate(sdpMid, sdpMLineIndex, candidate)
        val pc = registry.get(fromUserId)
        if (pc != null && pc.remoteDescription != null) {
            pc.addIceCandidate(ice)
        } else {
            registry.bufferIce(fromUserId, ice)
        }
    }

    @Synchronized
    private fun ensurePeer(remoteUserId: String): PeerConnection? {
        if (released) return null
        registry.get(remoteUserId)?.let { return it }
        val f = factory ?: return null

        val rtcConfig = PeerConnection.RTCConfiguration(IceServerProvider.getIceServers()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        val pc = f.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (released) return
                candidate?.let { listener.onLocalIceCandidate(remoteUserId, it) }
            }
            override fun onTrack(transceiver: RtpTransceiver?) {
                if (!released) startSpeakingMonitor(remoteUserId)
            }
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dc: org.webrtc.DataChannel?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE $remoteUserId -> $state")
                if (released) return
                listener.onPeerConnectionStateChanged(
                    remoteUserId,
                    state == PeerConnection.IceConnectionState.CONNECTED
                )
            }
            override fun onIceConnectionReceivingChange(r: Boolean) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(c: Array<out IceCandidate>?) {}
            override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        }) ?: return null

        localAudioTrack?.let { pc.addTrack(it, listOf(STREAM_ID)) }
        registry.getOrPut(remoteUserId) { pc }
        return pc
    }

    private suspend fun createAndSendOffer(remoteUserId: String) {
        val pc = registry.get(remoteUserId) ?: return
        val offer = createOffer(pc) ?: return
        setLocalDescription(pc, offer)
        if (!released) listener.sendOfferTo(remoteUserId, offer.description)
    }

    private fun drainPendingIce(remoteUserId: String, pc: PeerConnection) {
        registry.drainIce(remoteUserId).forEach { pc.addIceCandidate(it) }
    }

    // Un monitor por peer: una renegociacion (onTrack repetido) no debe
    // apilar monitores duplicados sobre el mismo usuario.
    private val speakingMonitors = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    /** Monitor de actividad de voz via stats RTP entrantes (mejor esfuerzo). */
    private fun startSpeakingMonitor(remoteUserId: String) {
        speakingMonitors[remoteUserId]?.cancel()
        speakingMonitors[remoteUserId] = scope.launch {
            var lastSpeaking = false
            while (!released && registry.contains(remoteUserId)) {
                try {
                    val pc = registry.get(remoteUserId) ?: break
                    pc.getStats { report ->
                        val level = report.statsMap.values
                            .filter { it.type == "inbound-rtp" }
                            .mapNotNull { stat ->
                                @Suppress("UNCHECKED_CAST")
                                val m = stat.members as? Map<String, Any>
                                val audioLevel = m?.get("audioLevel") as? Double
                                val audioEnergy = m?.get("totalAudioEnergy") as? Double
                                if (audioLevel != null && audioLevel > 0.0) audioLevel
                                else audioEnergy ?: 0.0
                            }
                            .maxOrNull() ?: 0.0
                        // audioLevel is a 0..1 sample; totalAudioEnergy grows over time,
                        // so a plain threshold is meaningless for it: treat any positive
                        // energy burst as speaking (the monitor polls at 500ms).
                        val speaking = level > SPEAKING_THRESHOLD
                        if (speaking != lastSpeaking) {
                            lastSpeaking = speaking
                            listener.onPeerSpeaking(remoteUserId, speaking)
                        }
                    }
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(500)
            }
        }
    }

    /**
     * Quita y dispone la PeerConnection de un peer (DELETE de voice_room_seats,
     * abandono de sillon, etc.). Tambien limpia sus ICE pendientes y el
     * indicador de "hablando" para no dejar UI ni conexiones zombie.
     * Si el mismo usuario vuelve a sentarse, la proxima negociacion crea una
     * PeerConnection NUEVA desde cero.
     */
    private fun removePeer(remoteUserId: String) {
        speakingMonitors.remove(remoteUserId)?.cancel()
        val pc = registry.remove(remoteUserId) ?: return
        try { pc.dispose() } catch (_: Exception) {}
        if (!released) {
            listener.onPeerSpeaking(remoteUserId, false)
            listener.onPeerConnectionStateChanged(remoteUserId, false)
        }
    }

    /**
     * Descarta TODAS las PeerConnections actuales pero conserva factory, track
     * local y scope: el engine queda listo para renegociar desde cero. Se usa
     * tras una reconexion del socket de senalizacion, donde los SDP/ICE
     * intercambiados antes de la caida ya no son validos.
     */
    override fun resetPeers() {
        if (released) return
        speakingMonitors.values.forEach { it.cancel() }
        speakingMonitors.clear()
        registry.removeAll().forEach { try { it.dispose() } catch (_: Exception) {} }
    }

    /**
     * Libera TODO: peers, ICE pendientes, track/source/factory y el scope de
     * corrutinas (monitores de speaking, ofertas en vuelo). El engine no es
     * reutilizable despues de release(): el ViewModel crea una instancia nueva.
     */
    override fun release() {
        val toDispose: List<PeerConnection>
        synchronized(this) {
            if (released) return
            released = true
            toDispose = registry.removeAll()
        }
        scope.cancel() // corta monitores de speaking y ofertas/answers en vuelo
        speakingMonitors.clear()
        toDispose.forEach { try { it.dispose() } catch (_: Exception) {} }
        try { localAudioTrack?.dispose() } catch (_: Exception) {}
        try { audioSource?.dispose() } catch (_: Exception) {}
        try { factory?.dispose() } catch (_: Exception) {}
        localAudioTrack = null
        audioSource = null
        factory = null
    }

    // --- Helpers SDP suspend ---

    private suspend fun createOffer(pc: PeerConnection): SessionDescription? =
        suspendCancellableCoroutine { cont ->
            pc.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) { cont.resume(sdp) }
                override fun onCreateFailure(err: String?) { Log.e(TAG, "createOffer: $err"); cont.resume(null) }
                override fun onSetSuccess() {}
                override fun onSetFailure(p0: String?) {}
            }, MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            })
        }

    private suspend fun createAnswer(pc: PeerConnection): SessionDescription? =
        suspendCancellableCoroutine { cont ->
            pc.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) { cont.resume(sdp) }
                override fun onCreateFailure(err: String?) { Log.e(TAG, "createAnswer: $err"); cont.resume(null) }
                override fun onSetSuccess() {}
                override fun onSetFailure(p0: String?) {}
            }, MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            })
        }

    private suspend fun setLocalDescription(pc: PeerConnection, sdp: SessionDescription) =
        suspendCancellableCoroutine { cont ->
            pc.setLocalDescription(object : SdpObserver {
                override fun onSetSuccess() { cont.resume(Unit) }
                override fun onSetFailure(err: String?) { Log.e(TAG, "setLocal: $err"); cont.resume(Unit) }
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
            }, sdp)
        }

    private suspend fun setRemoteDescription(pc: PeerConnection, sdp: SessionDescription) =
        suspendCancellableCoroutine { cont ->
            pc.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() { cont.resume(Unit) }
                override fun onSetFailure(err: String?) { Log.e(TAG, "setRemote: $err"); cont.resume(Unit) }
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
            }, sdp)
        }
}
