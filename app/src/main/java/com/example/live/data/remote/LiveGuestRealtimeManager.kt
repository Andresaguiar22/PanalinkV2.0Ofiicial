package com.example.live.data.remote

import android.util.Log
import com.example.data.supabase.SupabaseClient
import com.example.live.domain.model.LiveGuest
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LiveGuestRealtimeManager(
    private val streamId: String,
    private val onGuestChanged: (LiveGuest) -> Unit
) {
    private val TAG = "LiveGuestRealtimeManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private val moshi = Moshi.Builder().build()
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true

        val token = SupabaseClient.currentToken
        var wsUrl = SupabaseClient.supabaseUrl.replace("https://", "wss://").replace("http://", "ws://").removeSuffix("/") + "/realtime/v1/websocket?apikey=${SupabaseClient.supabaseAnonKey}&vsn=1.0.0"
        if (!token.isNullOrEmpty()) wsUrl += "&token=$token"

        val topic = "realtime:public:live_guests:stream_id=eq.$streamId"

        webSocket = client.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                val joinMsg = JSONObject().apply {
                    put("topic", topic)
                    put("event", "phx_join")
                    put("payload", JSONObject().apply {
                        put("config", JSONObject().apply {
                            put("postgres_changes", arrayOf(
                                JSONObject().apply {
                                    put("event", "*")
                                    put("schema", "public")
                                    put("table", "live_guests")
                                    put("filter", "stream_id=eq.$streamId")
                                }
                            ))
                        })
                        if (!token.isNullOrEmpty()) {
                            put("user_token", token)
                            put("access_token", token)
                        }
                    })
                    put("ref", "guest_join")
                }
                ws.send(joinMsg.toString())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val obj = JSONObject(text)
                    if (obj.optString("event") == "postgres_changes") {
                        val payload = obj.optJSONObject("payload")
                        val newData = payload?.optJSONObject("data")?.optJSONObject("record")
                        if (newData != null) {
                            val adapter = moshi.adapter(LiveGuest::class.java)
                            val guest = adapter.fromJson(newData.toString())
                            if (guest != null) {
                                scope.launch { onGuestChanged(guest) }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing guest realtime message", e)
                }
            }
        })
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        try {
            webSocket?.close(1000, "leave")
        } catch (_: Exception) {}
        webSocket = null
    }
}
