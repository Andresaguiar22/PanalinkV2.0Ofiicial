package com.example.live.data.remote

import android.util.Log
import com.example.data.supabase.SupabaseClient
import com.example.live.domain.model.LiveGiftEvent
import com.example.live.domain.model.LiveStreamStats
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Escucha en un único websocket los contadores agregados de la transmisión
 * (public.live_stream_stats) y los regalos enviados (public.live_gift_events),
 * ambos filtrados por stream_id. Es independiente del bus central de mensajería.
 */
class LiveEngagementRealtimeManager(
    private val streamId: String,
    private val onStats: (LiveStreamStats) -> Unit,
    private val onGiftEvent: (LiveGiftEvent) -> Unit
) {
    private val TAG = "LiveEngagementRealtime"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val moshi = Moshi.Builder().build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true

        val token = SupabaseClient.currentToken
        var wsUrl = SupabaseClient.supabaseUrl.replace("https://", "wss://").replace("http://", "ws://").removeSuffix("/") + "/realtime/v1/websocket?apikey=${SupabaseClient.supabaseAnonKey}&vsn=1.0.0"
        if (!token.isNullOrEmpty()) wsUrl += "&token=$token"

        webSocket = client.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                ws.send(buildJoin("live_stream_stats", "stats_join", token))
                ws.send(buildJoin("live_gift_events", "gifts_join", token))
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val obj = JSONObject(text)
                    if (obj.optString("event") != "postgres_changes") return
                    val data = obj.optJSONObject("payload")?.optJSONObject("data") ?: return
                    val record = data.optJSONObject("record") ?: return
                    when (data.optString("table")) {
                        "live_stream_stats" -> {
                            val stats = moshi.adapter(LiveStreamStats::class.java).fromJson(record.toString())
                            if (stats != null) scope.launch { onStats(stats) }
                        }
                        "live_gift_events" -> {
                            val gift = moshi.adapter(LiveGiftEvent::class.java).fromJson(record.toString())
                            if (gift != null) scope.launch { onGiftEvent(gift) }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing engagement message", e)
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Engagement socket failure: ${t.message}")
            }
        })
    }

    private fun buildJoin(table: String, ref: String, token: String?): String =
        JSONObject().apply {
            put("topic", "realtime:public:$table")
            put("event", "phx_join")
            put("payload", JSONObject().apply {
                put("config", JSONObject().apply {
                    put("postgres_changes", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("event", "*")
                            put("schema", "public")
                            put("table", table)
                            put("filter", "stream_id=eq.$streamId")
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

    fun stop() {
        if (!isRunning) return
        isRunning = false
        try {
            webSocket?.close(1000, "leave")
        } catch (_: Exception) {}
        webSocket = null
    }
}
