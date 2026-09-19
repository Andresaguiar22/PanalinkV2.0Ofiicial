package com.example.live.data.remote

import android.util.Log
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Escucha en tiempo real las invitaciones de Co-Host dirigidas al usuario actual.
 * Realtime de PostgreSQL filtra `guest_user_id=eq.<me>)`; al llegar una fila con
 * estado INVITED (el host invitó) invoca [onPendingInvitation] con el streamId..
 * Es un websocket ligero e independiente del bus central de SupabaseClient para no
 * arriesgar el canal de mensajería; se detiene solo si el usuario ya no está navegando.
 */
class LiveGuestInvitationWatcher(
    private val onPendingInvitation: (String) -> Unit
) {
    private val TAG = "LiveGuestInvitationWatcher"
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private var isRunning = false
    private var seenInvitation: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    fun start() {
        if (isRunning) return
        isRunning = true
        val me = SupabaseClient.currentUser?.id ?: return
        val token = SupabaseClient.currentToken
        var wsUrl = SupabaseClient.supabaseUrl.replace("https://", "wss://").replace("http://", "ws://").removeSuffix("/") + "/realtime/v1/websocket?apikey=${SupabaseClient.supabaseAnonKey}&vsn=1.0.0"
        if (!token.isNullOrEmpty()) wsUrl += "&token=$token"

        val topic = "realtime:public:live_guests:guest_user_id=eq.$me"

        webSocket = client.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                val joinMsg = JSONObject().apply {
                    put("topic", topic)
                    put("event", "phx_join")
                    put("payload", JSONObject().apply {
                        put("config", JSONObject().apply {
                            put("postgres_changes", arrayOf(
                                JSONObject().apply {
                                    put("event", "INSERT")
                                    put("schema", "public")
                                    put("table", "live_guests")
                                    put("filter", "guest_user_id=eq.$me")
                                }
                            ))
                        })
                        if (!token.isNullOrEmpty()) {
                            put("user_token", token)
                            put("access_token", token)
                        }
                    })
                    put("ref", "guest_invite_watch")
                }
                ws.send(joinMsg.toString())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val obj = JSONObject(text)
                    if (obj.optString("event") == "postgres_changes") {
                        val payload = obj.optJSONObject("payload")
                        val dataObj = payload?.optJSONObject("data")
                        val record = dataObj?.optJSONObject("record")
                            ?: dataObj
                            ?: payload?.optJSONObject("record")
                        if (record != null) {
                            val status = record.optString("status")
                            val streamId = record.optString("stream_id")
                            val guestUserId = record.optString("guest_user_id")
                            if (status == "INVITED" && streamId.isNotBlank() && guestUserId == me) {

                                if (seenInvitation.add("$streamId|$guestUserId")) {
                                    // Pequeño delay para que la navegación dejetermine (si el usuario
                                    // acaba de entrar a la app) y evitar carreras con el NavHost.
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                        kotlinx.coroutines.delay(250)
                                        onPendingInvitation(streamId)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing guest invitation", e)
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