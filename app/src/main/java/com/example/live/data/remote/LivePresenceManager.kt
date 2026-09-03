package com.example.live.data.remote

import android.util.Log
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LivePresenceManager(
    private val streamId: String,
    private val userId: String
) {
    private val TAG = "LivePresenceManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _viewerCount = MutableStateFlow(1)
    val viewerCount: StateFlow<Int> = _viewerCount.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private var isJoined = false

    fun start() {
        if (isJoined) return
        isJoined = true
        val token = SupabaseClient.currentToken
        var wsUrl = SupabaseClient.supabaseUrl.replace("https://", "wss://").replace("http://", "ws://").removeSuffix("/") + "/realtime/v1/websocket?apikey=${SupabaseClient.supabaseAnonKey}&vsn=1.0.0"
        if (!token.isNullOrEmpty()) wsUrl += "&token=$token"

        val topic = "realtime:live_presence:$streamId"

        webSocket = client.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
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
                    val event = obj.optString("event")
                    if (event == "presence_state" || event == "presence_diff") {
                        val payload = obj.optJSONObject("payload")
                        val count = payload?.keys()?.asSequence()?.count() ?: 1
                        scope.launch { _viewerCount.value = count.coerceAtLeast(1) }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing presence message", e)
                }
            }
        })
    }

    fun stop() {
        if (!isJoined) return
        isJoined = false
        try {
            webSocket?.close(1000, "leave")
        } catch (_: Exception) {}
        webSocket = null
    }
}
