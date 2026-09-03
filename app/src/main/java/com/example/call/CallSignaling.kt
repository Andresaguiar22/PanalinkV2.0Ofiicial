package com.example.call

import android.util.Log
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Call signaling over Supabase Realtime broadcast channels, replacing the
 * legacy Socket.IO client. The Socket.IO server lived behind a Cloudflare
 * tunnel that is frequently down; Realtime is the same Supabase infra the app
 * already relies on for chat/voice-room events, so call orchestration
 * (ring/accept/reject/end/busy) no longer depends on an external tunnel.
 *
 * The media itself flows through the LiveKit SFU (both peers join a shared
 * room), so SDP/ICE are no longer required for LiveKit-routed calls — the
 * matching send* methods are kept for parity but are unused on that path.
 *
 * Each user listens on a personal inbox channel `realtime:call_inbox:{myId}`
 * and broadcasts to the peer's inbox. Realtime broadcast needs the `realtime:`
 * topic prefix and works with the anon key (no JWT policy required).
 */
class CallSignaling(private val userId: String) {
    companion object {
        private const val TAG = "CallSignaling"
        private const val INBOX_PREFIX = "realtime:call_inbox:"
        fun inboxTopic(userId: String) = "$INBOX_PREFIX$userId"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var intentionallyClosed = false
    private var refCounter = 0
    private var reconnectAttempts = 0

    private val _incomingCallFlow = MutableSharedFlow<IncomingCallData>(extraBufferCapacity = 64)
    val incomingCallFlow: SharedFlow<IncomingCallData> = _incomingCallFlow

    private val _callAnswerFlow = MutableSharedFlow<JSONObject>(extraBufferCapacity = 64)
    val callAnswerFlow: SharedFlow<JSONObject> = _callAnswerFlow

    private val _callRejectedFlow = MutableSharedFlow<JSONObject>(extraBufferCapacity = 64)
    val callRejectedFlow: SharedFlow<JSONObject> = _callRejectedFlow

    private val _callEndedFlow = MutableSharedFlow<JSONObject>(extraBufferCapacity = 64)
    val callEndedFlow: SharedFlow<JSONObject> = _callEndedFlow

    private val _callBusyFlow = MutableSharedFlow<JSONObject>(extraBufferCapacity = 64)
    val callBusyFlow: SharedFlow<JSONObject> = _callBusyFlow

    private val _iceCandidateReceivedFlow = MutableSharedFlow<JSONObject>(extraBufferCapacity = 64)
    val iceCandidateReceivedFlow: SharedFlow<JSONObject> = _iceCandidateReceivedFlow

    private val _connectionStatusFlow = MutableStateFlow(false)
    val connectionStatusFlow: StateFlow<Boolean> = _connectionStatusFlow

    init {
        connect()
    }

    fun connect() {
        if (webSocket != null) return
        intentionallyClosed = false
        val token = SupabaseClient.currentToken
        var wsUrl = SupabaseClient.supabaseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .removeSuffix("/") + "/realtime/v1/websocket?apikey=${SupabaseClient.supabaseAnonKey}&vsn=1.0.0"
        if (!token.isNullOrEmpty()) wsUrl += "&token=$token"

        Log.d(TAG, "Connecting Realtime call inbox for $userId")
        webSocket = client.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                reconnectAttempts = 0
                joinInbox(ws, token)
                startHeartbeat(ws)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleFrame(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Realtime WS failure: ${t.message}")
                _connectionStatusFlow.value = false
                webSocket = null
                scheduleReconnect()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                _connectionStatusFlow.value = false
                webSocket = null
                if (!intentionallyClosed) scheduleReconnect()
            }
        })
    }

    private fun joinInbox(ws: WebSocket, token: String?) {
        val join = JSONObject().apply {
            put("topic", inboxTopic(userId))
            put("event", "phx_join")
            put("payload", JSONObject().apply {
                put("config", JSONObject().apply {
                    put("broadcast", JSONObject().apply { put("ack", false); put("self", false) })
                    put("private", true)
                })
                if (!token.isNullOrEmpty()) {
                    put("user_token", token)
                    put("access_token", token)
                }
            })
            put("ref", "call_${refCounter++}")
        }
        ws.send(join.toString())
        // Inbox joined — mark connected so the UI stops showing "reconectando".
        _connectionStatusFlow.value = true
    }

    private fun handleFrame(text: String) {
        try {
            val obj = JSONObject(text)
            val event = obj.optString("event")
            val topic = obj.optString("topic", "")
            if (event != "broadcast" || !topic.startsWith(INBOX_PREFIX)) return
            val payload = obj.optJSONObject("payload") ?: return
            val eventName = payload.optString("event")
            val body = payload.optJSONObject("payload") ?: return
            val from = body.optString("from")
            val to = body.optString("to")
            // Ignore our own broadcasts and messages not addressed to us.
            if (from == userId) return
            if (to.isNotEmpty() && to != userId) return

            when (eventName) {
                "call_request", "webrtc_offer" -> {
                    val callerId = if (from.isNotEmpty()) from else body.optString("callerId")
                    val callerName = body.optString("callerName")
                    val callType = body.optString("callType", "voice")
                    val sdp = body.optString("sdp").ifEmpty { null }
                    scope.launch { _incomingCallFlow.emit(IncomingCallData(callerId, callerName, callType, sdp)) }
                }
                "call_accept", "call_accepted", "webrtc_answer" -> {
                    scope.launch { _callAnswerFlow.emit(body) }
                }
                "call_reject", "call_rejected" -> {
                    scope.launch { _callRejectedFlow.emit(body) }
                }
                "call_end", "call_ended" -> {
                    scope.launch { _callEndedFlow.emit(body) }
                }
                "call_busy" -> {
                    scope.launch { _callBusyFlow.emit(body) }
                }
                "ice_candidate" -> {
                    scope.launch { _iceCandidateReceivedFlow.emit(body) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Realtime frame", e)
        }
    }

    private fun broadcastTo(targetUserId: String, eventName: String, body: JSONObject) {
        val ws = webSocket ?: run {
            Log.w(TAG, "Cannot broadcast '$eventName': realtime socket not open")
            return
        }
        body.put("from", userId)
        body.put("to", targetUserId)
        val msg = JSONObject().apply {
            put("topic", inboxTopic(targetUserId))
            put("event", "broadcast")
            put("payload", JSONObject().apply {
                put("type", "broadcast")
                put("event", eventName)
                put("payload", body)
            })
            put("ref", "call_${refCounter++}")
        }
        ws.send(msg.toString())
    }

    fun sendCallRequest(targetUserId: String, callerName: String, callType: String) {
        broadcastTo(targetUserId, "call_request", JSONObject().apply {
            put("callerId", userId)
            put("callerName", callerName)
            put("callType", callType)
        })
    }

    fun sendOffer(targetUserId: String, offerSdp: String, callerName: String, callType: String) {
        broadcastTo(targetUserId, "webrtc_offer", JSONObject().apply {
            put("callerId", userId)
            put("callerName", callerName)
            put("callType", callType)
            put("sdp", offerSdp)
        })
    }

    fun sendAnswer(targetUserId: String, answerSdp: String) {
        broadcastTo(targetUserId, "webrtc_answer", JSONObject().apply {
            put("sdp", answerSdp)
        })
    }

    fun acceptCall(callerId: String) {
        broadcastTo(callerId, "call_accept", JSONObject())
    }

    fun sendIceCandidate(targetUserId: String, sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        broadcastTo(targetUserId, "ice_candidate", JSONObject().apply {
            put("sdpMid", sdpMid)
            put("sdpMLineIndex", sdpMLineIndex)
            put("candidate", candidate)
        })
    }

    fun rejectCall(callerId: String) {
        broadcastTo(callerId, "call_reject", JSONObject())
    }

    fun endCall(targetUserId: String) {
        broadcastTo(targetUserId, "call_end", JSONObject())
    }

    fun sendBusy(targetUserId: String) {
        broadcastTo(targetUserId, "call_busy", JSONObject())
    }

    private fun startHeartbeat(ws: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(30000)
                try {
                    ws.send(JSONObject().apply {
                        put("topic", "phoenix")
                        put("event", "heartbeat")
                        put("payload", JSONObject())
                        put("ref", "call_hb_${System.currentTimeMillis()}")
                    }.toString())
                } catch (_: Exception) {}
            }
        }
    }

    private fun scheduleReconnect() {
        if (intentionallyClosed) return
        if (reconnectJob?.isActive == true) return
        reconnectAttempts++
        val delayMs = (1000L * reconnectAttempts).coerceAtMost(15000L)
        reconnectJob = scope.launch {
            delay(delayMs)
            if (!intentionallyClosed) connect()
        }
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting Realtime call signaling")
        intentionallyClosed = true
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        try { webSocket?.close(1000, "disconnect") } catch (_: Exception) {}
        webSocket = null
        _connectionStatusFlow.value = false
    }
}

data class IncomingCallData(
    val callerId: String,
    val callerName: String,
    val callType: String,
    val sdp: String?
)
