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
    private val onCommentReceived: ((LiveComment) -> Unit)? = null,
    private val onCommentUpdated: ((LiveComment) -> Unit)? = null,
    private val liveStreamId: String? = null
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
                ws.send(buildJoin("live_streams", "live_streams_1", token, liveStreamId))
                val joinedStreamId = streamId
                if (!joinedStreamId.isNullOrEmpty()) {
                    ws.send(buildJoin("live_comments", "live_comments_1", token, joinedStreamId))
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
                                val comment = parseComment(record)
                                if (data.optString("type") == "UPDATE") {
                                    onCommentUpdated?.invoke(comment)
                                } else {
                                    onCommentReceived?.invoke(comment)
                                }
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

    private fun buildJoin(table: String, ref: String, token: String?, filterValue: String?): String {
        val filterColumn = if (table == "live_comments") "stream_id" else "id"
        return JSONObject().apply {
            put("topic", "realtime:public:$table")
            put("event", "phx_join")
            put("payload", JSONObject().apply {
                put("config", JSONObject().apply {
                    put("postgres_changes", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("event", "*")
                            put("schema", "public")
                            put("table", table)
                            if (!filterValue.isNullOrEmpty()) {
                                put("filter", "$filterColumn=eq.$filterValue")
                            }
                        })
                    })
                })
                if (!token.isNullOrEmpty()) {
                    put("user_token", token)
                    put("access_token", token)
                }
            })
            put("ref", ref)
        }.toString()
    }

    private fun parseComment(record: JSONObject): LiveComment = LiveComment(
        id = record.optString("id"),
        streamId = record.optString("stream_id"),
        userId = record.optString("user_id"),
        text = record.optString("text"),
        createdAt = record.optString("created_at"),
        isDeleted = record.optBoolean("is_deleted", false),
        kind = record.optString("kind", LiveComment.KIND_CHAT).ifEmpty { LiveComment.KIND_CHAT }
    )

    fun stop() {
        try {
            webSocket?.close(1000, "leave")
        } catch (_: Exception) {}
        webSocket = null
        isConnected = false
    }
}
