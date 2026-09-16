package com.example.live.data.repository

import android.content.Context
import com.example.data.supabase.SupabaseClient
import com.example.live.domain.repository.LiveReactionsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LiveReactionsRepositoryImpl(private val context: Context) : LiveReactionsRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _reactionEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
    override val reactionEvents: SharedFlow<Unit> = _reactionEvents.asSharedFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private var currentStreamId: String? = null

    override suspend fun sendReaction(streamId: String) {
        _reactionEvents.emit(Unit)
        try {
            val senderId = SupabaseClient.currentUser?.id
            val payload = JSONObject().apply {
                put("topic", "realtime:live_reactions:$streamId")
                put("event", "broadcast")
                put("payload", JSONObject().apply {
                    put("type", "broadcast")
                    put("event", "reaction")
                    put("payload", JSONObject().apply {
                        put("streamId", streamId)
                        put("senderId", senderId)
                        put("quantity", 1)
                        put("sentAt", System.currentTimeMillis())
                    })
                })
                put("ref", "react_send")
            }
            webSocket?.send(payload.toString())
        } catch (_: Exception) {}
    }

    override fun startListening(streamId: String) {
        currentStreamId = streamId
        val token = SupabaseClient.currentToken
        var wsUrl = SupabaseClient.supabaseUrl.replace("https://", "wss://").replace("http://", "ws://").removeSuffix("/") + "/realtime/v1/websocket?apikey=${SupabaseClient.supabaseAnonKey}&vsn=1.0.0"
        if (!token.isNullOrEmpty()) wsUrl += "&token=$token"

        val topic = "realtime:live_reactions:$streamId"

        webSocket = client.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                val joinMsg = JSONObject().apply {
                    put("topic", topic)
                    put("event", "phx_join")
                    put("payload", JSONObject().apply {
                        put("config", JSONObject().apply {
                            put("broadcast", JSONObject().apply { put("ack", false); put("self", false) })
                            put("private", false)
                        })
                        if (!token.isNullOrEmpty()) {
                            put("user_token", token)
                            put("access_token", token)
                        }
                    })
                    put("ref", "react_join")
                }
                ws.send(joinMsg.toString())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val obj = JSONObject(text)
                    if (obj.optString("event") == "broadcast") {
                        val payload = obj.optJSONObject("payload")
                        if (payload?.optString("event") == "reaction") {
                            scope.launch { _reactionEvents.emit(Unit) }
                        }
                    }
                } catch (_: Exception) {}
            }
        })
    }

    override fun stopListening() {
        try {
            webSocket?.close(1000, "leave")
        } catch (_: Exception) {}
        webSocket = null
        currentStreamId = null
    }
}
