package com.example.live.data.remote

import android.util.Log
import com.example.data.supabase.SupabaseClient
import com.example.live.domain.model.LivePresenceEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LivePresenceManager(
    private val streamId: String,
    private val userId: String
) {
    private val TAG = "LivePresenceManager"

    private val _viewerCount = MutableStateFlow(1)
    val viewerCount: StateFlow<Int> = _viewerCount.asStateFlow()

    private val _presenceEvents = MutableSharedFlow<LivePresenceEvent>(extraBufferCapacity = 64)
    val presenceEvents: SharedFlow<LivePresenceEvent> = _presenceEvents.asSharedFlow()

    /** Miembros presentes ahora mismo (claves de presencia = user id). */
    private val present = ConcurrentHashMap.newKeySet<String>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private var isJoined = false
    @Volatile private var intentionallyStopped = false
    private var reconnectJob: Job? = null
    private val reconnectScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectAttempt = 0

    fun start() {
        if (isJoined) return
        intentionallyStopped = false
        isJoined = true
        reconnectAttempt = 0
        connectSocket()
    }

    private fun connectSocket() {
        if (intentionallyStopped) return
        val token = SupabaseClient.currentToken
        var wsUrl = SupabaseClient.supabaseUrl.replace("https://", "wss://").replace("http://", "ws://").removeSuffix("/") + "/realtime/v1/websocket?apikey=${SupabaseClient.supabaseAnonKey}&vsn=1.0.0"
        if (!token.isNullOrEmpty()) wsUrl += "&token=$token"

        val topic = "realtime:live_presence:$streamId"

        webSocket = client.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                reconnectAttempt = 0
                val joinMsg = JSONObject().apply {
                    put("topic", topic)
                    put("event", "phx_join")
                    put("payload", JSONObject().apply {
                        put("config", JSONObject().apply {
                            put("presence", JSONObject().apply { put("key", userId) })
                            put("broadcast", JSONObject().apply { put("ack", false); put("self", true) })
                            put("private", false)
                        })
                        if (!token.isNullOrEmpty()) {
                            put("user_token", token)
                            put("access_token", token)
                        }
                    })
                    put("ref", "presence_join")
                }
                ws.send(joinMsg.toString())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val obj = JSONObject(text)
                    when (obj.optString("event")) {
                        "presence_state" -> handlePresenceState(obj.optJSONObject("payload"))
                        "presence_diff" -> handlePresenceDiff(obj.optJSONObject("payload"))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing presence message", e)
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Presence socket failure: ${t.message}")
                scheduleReconnect()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (!intentionallyStopped) scheduleReconnect()
            }
        })
    }

    private fun handlePresenceState(payload: JSONObject?) {
        val state = payload ?: return
        val keys = state.keys().asSequence().toList()
        present.clear()
        present.addAll(keys)
        publishCount()
    }

    private fun handlePresenceDiff(payload: JSONObject?) {
        val diff = payload ?: return
        diff.optJSONObject("joins")?.let { joins ->
            joins.keys().asSequence().forEach { key ->
                val isNew = present.add(key)
                if (isNew && key != userId) {
                    _presenceEvents.tryEmit(LivePresenceEvent.Joined(key))
                }
            }
        }
        diff.optJSONObject("leaves")?.let { leaves ->
            leaves.keys().asSequence().forEach { key ->
                val removed = present.remove(key)
                if (removed && key != userId) {
                    _presenceEvents.tryEmit(LivePresenceEvent.Left(key))
                }
            }
        }
        publishCount()
    }

    private fun publishCount() {
        _viewerCount.value = present.size.coerceAtLeast(1)
    }

    private fun scheduleReconnect() {
        if (intentionallyStopped || !isJoined || reconnectJob?.isActive == true) return
        reconnectJob = reconnectScope.launch {
            val attempt = reconnectAttempt.coerceAtMost(5)
            val delayMs = (1000L shl attempt).coerceAtMost(30_000L)
            reconnectAttempt = (attempt + 1).coerceAtMost(5)
            delay(delayMs)
            if (!intentionallyStopped && isJoined) connectSocket()
        }
    }

    fun stop() {
        intentionallyStopped = true
        reconnectJob?.cancel()
        reconnectJob = null
        if (!isJoined) return
        isJoined = false
        try {
            webSocket?.close(1000, "leave")
        } catch (_: Exception) {}
        webSocket = null
        present.clear()
    }
}
