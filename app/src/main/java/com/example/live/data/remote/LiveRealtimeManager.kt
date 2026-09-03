package com.example.live.data.remote

import android.util.Log
import com.example.data.supabase.SupabaseClient
import com.example.live.domain.model.LiveComment
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LiveRealtimeManager(
    private val onLiveStreamChanged: () -> Unit,
    private val streamId: String? = null,
    private val onCommentReceived: ((LiveComment) -> Unit)? = null
) {
    private val TAG = "LiveRealtimeManager"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private var webSocket: WebSocket? = null
    private var isConnected = false

    fun start() {
        if (isConnected) return
        val token = SupabaseClient.currentToken
        var wsUrl = SupabaseClient.supabaseUrl.replace("https://", "wss://").replace("http://", "ws://").removeSuffix("/") + "/realtime/v1/websocket?apikey=${SupabaseClient.supabaseAnonKey}&vsn=1.0.0"
        if (!token.isNullOrEmpty()) wsUrl += "&token=$token"

        webSocket = client.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                isConnected = true
                val joinStreamsMsg = JSONObject().apply {
                    put("topic", "realtime:public:live_streams")
                    put("event", "phx_join")
                    put("payload", JSONObject().apply {
                        put("config", JSONObject().apply {
                            put("postgres_changes", org.json.JSONArray().apply {
                                put(JSONObject().apply {
                                    put("event", "*")
                                    put("schema", "public")
                                    put("table", "live_streams")
                                })
                            })
                        })
                        if (!token.isNullOrEmpty()) {
                            put("user_token", token)
                            put("access_token", token)
                        }
                    })
                    put("ref", "live_streams_1")
                }
                ws.send(joinStreamsMsg.toString())

                if (!streamId.isNullOrEmpty()) {
                    val joinCommentsMsg = JSONObject().apply {
                        put("topic", "realtime:public:live_comments")
                        put("event", "phx_join")
                        put("payload", JSONObject().apply {
                            put("config", JSONObject().apply {
                                put("postgres_changes", org.json.JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("event", "INSERT")
                                        put("schema", "public")
                                        put("table", "live_comments")
                                        put("filter", "stream_id=eq.$streamId")
                                    })
                                })
                            })
                            if (!token.isNullOrEmpty()) {
                                put("user_token", token)
                                put("access_token", token)
                            }
                        })
                        put("ref", "live_comments_1")
                    }
                    ws.send(joinCommentsMsg.toString())
                }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val obj = JSONObject(text)
                    val event = obj.optString("event")
                    if (event == "postgres_changes") {
                        val payload = obj.optJSONObject("payload") ?: return
                        val data = payload.optJSONObject("data") ?: return
                        val table = data.optString("table")
                        if (table == "live_streams") {
                            onLiveStreamChanged()
                        } else if (table == "live_comments") {
                            val record = data.optJSONObject("record")
                            if (record != null) {
                                val id = record.optString("id")
                                val sId = record.optString("stream_id")
                                val uId = record.optString("user_id")
                                val txt = record.optString("text")
                                val createdAt = record.optString("created_at")
                                val comment = LiveComment(id, sId, uId, txt, createdAt)
                                onCommentReceived?.invoke(comment)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing realtime message", e)
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                isConnected = false
            }
        })
    }

    fun stop() {
        try {
            webSocket?.close(1000, "leave")
        } catch (_: Exception) {}
        webSocket = null
        isConnected = false
    }
}
