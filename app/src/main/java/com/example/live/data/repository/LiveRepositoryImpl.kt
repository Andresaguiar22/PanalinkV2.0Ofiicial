package com.example.live.data.repository

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import com.example.data.supabase.SessionManager
import com.example.data.supabase.SupabaseClient
import com.example.live.data.remote.LiveSupabaseApi
import com.example.live.domain.model.LiveComment
import com.example.live.domain.model.LiveGift
import com.example.live.domain.model.LiveGiftResult
import com.example.live.domain.model.LiveHeartbeatRequest
import com.example.live.domain.model.LiveJoinStreamRequest
import com.example.live.domain.model.LiveSendGiftRequest
import com.example.live.domain.model.LiveSendLikeRequest
import com.example.live.domain.model.LiveSetViewerCountRequest
import com.example.live.domain.model.LiveStream
import com.example.live.domain.model.LiveStreamStats
import com.example.live.domain.repository.LiveRepository
import com.example.live.domain.repository.LiveTokenResult
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.UUID

class LiveRepositoryImpl(private val context: Context) : LiveRepository {
    private val TAG = "PanalinkLive"

    private val api: LiveSupabaseApi by lazy {
        val baseUrl = if (SupabaseClient.supabaseUrl.endsWith("/")) SupabaseClient.supabaseUrl else "${SupabaseClient.supabaseUrl}/"
        val client = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("apikey", SupabaseClient.supabaseAnonKey)
                    .build()
                chain.proceed(request)
            }
            .build()

        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(SupabaseClient.moshi))
            .build()
            .create(LiveSupabaseApi::class.java)
    }

    private fun getAuthHeader(): String {
        val token = SupabaseClient.currentToken ?: ""
        return if (token.startsWith("Bearer ")) token else "Bearer $token"
    }

    override suspend fun createLiveStream(title: String, description: String?, thumbnailUrl: String?): Result<LiveStream> {
        return try {
            val user = SupabaseClient.currentUser
            if (user == null) {
                return Result.failure(Exception("Usuario no autenticado"))
            }
            val roomName = "live_${UUID.randomUUID()}"
            val body = mapOf(
                "host_id" to user.id,
                "room_name" to roomName,
                "title" to title,
                "description" to description,
                "thumbnail_url" to thumbnailUrl,
                "status" to "CREATED"
            )
            val response = api.createLiveStream(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader(),
                liveStream = body
            )
            if (response.isSuccessful && response.body()?.isNotEmpty() == true) {
                Result.success(response.body()!![0])
            } else {
                Result.failure(Exception("Error al crear la transmisión: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception creating live stream", e)
            Result.failure(e)
        }
    }

    override suspend fun getLiveStreams(): Result<List<LiveStream>> {
        return try {
            val response = api.getLiveStreams(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader(),
                status = "eq.LIVE"
            )
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception("Error al obtener transmisiones: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching live streams", e)
            Result.failure(e)
        }
    }

    override suspend fun getLiveStream(id: String): Result<LiveStream?> {
        return try {
            val response = api.getLiveStreamById(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader(),
                idFilter = "eq.$id"
            )
            if (response.isSuccessful) {
                val list = response.body()
                Result.success(list?.firstOrNull())
            } else {
                Result.failure(Exception("Error al obtener transmisión: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching live stream by id", e)
            Result.failure(e)
        }
    }

    override suspend fun startLiveStream(id: String): Result<Unit> {
        return try {
            val updates = mapOf(
                "status" to "LIVE",
                "started_at" to java.time.Instant.now().toString()
            )
            val response = api.updateLiveStream(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader(),
                idFilter = "eq.$id",
                updates = updates
            )
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Error al iniciar transmisión: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting live stream", e)
            Result.failure(e)
        }
    }

    /**
     * Marca el stream como ENDED en Supabase. Este PATCH es la unica via que tienen
     * los espectadores para ver el live apagado; si falla (token vencido, red), el
     * stream quedaria "fantasma" (LIVE para siempre en el feed) exactamente como se
     * reporto en produccion.
     *
     * Por eso: (1) se reintenta 2 veces con Semantic=refresh del JWT si el primer
     * intento da 401/403, y (2) se loguea el fallo. Es @WorkerThread: llamadas desde
     * corrutinas IO.
     */
    @WorkerThread
    override suspend fun endLiveStream(id: String): Result<Unit> {
        var lastFailure: Exception? = null
        repeat(3) { attempt ->
            try {
                val updates = mapOf(
                    "status" to "ENDED",
                    "ended_at" to java.time.Instant.now().toString()
                )
                val response = api.updateLiveStream(
                    apiKey = SupabaseClient.supabaseAnonKey,
                    authorization = getAuthHeader(),
                    idFilter = "eq.$id",
                    updates = updates
                )
                if (response.isSuccessful) {
                    Log.i(TAG, "Stream $id marcado ENDED (intento ${attempt + 1})")
                    return Result.success(Unit)
                }
                lastFailure = Exception("Supabase ${response.code()} al finalizar transmisión: ${response.errorBody()?.string()}")
                Log.w(TAG, "endLiveStream intento ${attempt + 1} falló: ${response.code()}", lastFailure)
                val code = response.code()
                if (code == 401 || code == 403) {
                    val refreshed = SessionManager.refreshSession()
                    if (!refreshed) {
                        return Result.failure(lastFailure ?: Exception("Error al finalizar transmisión"))
                    }
                    // Token renovado: seguimos al siguiente intento del bucle.
                    Log.i(TAG, "JWT refrescado, reintentando endLiveStream")
                } else {
                    return Result.failure(lastFailure ?: Exception("Error al finalizar transmisión"))
                }
            } catch (e: Exception) {
                lastFailure = e
                Log.w(TAG, "endLiveStream intento ${attempt + 1} excepción", e)
            }
        }
        Log.e(TAG, "endLiveStream agotó reintentos", lastFailure)
        return Result.failure(lastFailure ?: Exception("Error al finalizar transmisión"))
    }

    override suspend fun getLiveKitToken(roomName: String, identity: String, role: String): Result<LiveTokenResult> {
        return try {
            val url = "${SupabaseClient.supabaseUrl}/functions/v1/livekit-token"
            val body = mapOf(
                "room" to roomName,
                "identity" to identity,
                "role" to role
            )
            val response = api.callEdgeFunction(
                url = url,
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader(),
                body = body
            )
            if (response.isSuccessful) {
                val responseBodyStr = response.body()?.string() ?: ""
                val jsonAdapter = SupabaseClient.moshi.adapter(Map::class.java)
                val map = jsonAdapter.fromJson(responseBodyStr) as? Map<String, Any>
                val token = map?.get("token") as? String
                val serverUrl = map?.get("url") as? String ?: "wss://tivqjfgjdxgzicrridaz.livekit.cloud"
                val room = map?.get("room") as? String ?: roomName

                if (!token.isNullOrEmpty()) {
                    Result.success(LiveTokenResult(token, serverUrl, room))
                } else {
                    Result.failure(Exception("Token de LiveKit no recibido en respuesta"))
                }
            } else {
                Result.failure(Exception("Error en Edge Function livekit-token: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting LiveKit token", e)
            Result.failure(e)
        }
    }

    override suspend fun getComments(streamId: String): Result<List<LiveComment>> {
        return try {
            val response = api.getComments(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader(),
                streamIdFilter = "eq.$streamId"
            )
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception("Error al obtener comentarios: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching live comments", e)
            Result.failure(e)
        }
    }

    override suspend fun postComment(streamId: String, text: String): Result<LiveComment> {
        return try {
            val user = SupabaseClient.currentUser
            if (user == null) {
                return Result.failure(Exception("Usuario no autenticado"))
            }
            val body = mapOf(
                "stream_id" to streamId,
                "user_id" to user.id,
                "text" to text
            )
            val response = api.postComment(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader(),
                comment = body
            )
            if (response.isSuccessful && response.body()?.isNotEmpty() == true) {
                Result.success(response.body()!![0])
            } else {
                Result.failure(Exception("Error al enviar comentario: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception posting live comment", e)
            Result.failure(e)
        }
    }

    // --- Engagement real ---------------------------------------------------------

    private fun parseJsonMap(raw: String?): Map<String, Any>? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return try {
            @Suppress("UNCHECKED_CAST")
            SupabaseClient.moshi.adapter(Map::class.java).fromJson(text) as? Map<String, Any>
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo parsear la respuesta RPC: $text", e)
            null
        }
    }

    private fun asBalance(map: Map<String, Any>?): Int =
        (map?.get("balance") as? Number)?.toInt() ?: 0

    private fun asInt(map: Map<String, Any>?, key: String): Int =
        (map?.get(key) as? Number)?.toInt() ?: 0

    private fun asBool(map: Map<String, Any>?, key: String): Boolean =
        (map?.get(key) as? Boolean) ?: false

    override suspend fun getStats(streamId: String): Result<LiveStreamStats> {
        return try {
            val response = api.getLiveStreamStats(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader(),
                streamIdFilter = "eq.$streamId"
            )
            if (response.isSuccessful) {
                val stats = response.body()?.firstOrNull()
                    ?: LiveStreamStats(streamId = streamId)
                Result.success(stats)
            } else {
                Result.failure(Exception("Error al obtener estadísticas: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching live stats", e)
            Result.failure(e)
        }
    }

    override suspend fun sendLikes(streamId: String, quantity: Int): Result<Int> {
        if (quantity <= 0) return Result.success(0)
        return try {
            val response = api.rpcSendLike(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader(),
                body = LiveSendLikeRequest(streamId = streamId, quantity = quantity)
            )
            if (response.isSuccessful) {
                val map = parseJsonMap(response.body()?.string())
                Result.success(asInt(map, "like_count"))
            } else {
                Result.failure(Exception("Error al enviar reacciones: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception sending live likes", e)
            Result.failure(e)
        }
    }

    override suspend fun getGiftCatalog(): Result<List<LiveGift>> {
        return try {
            val response = api.getGiftCatalog(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader()
            )
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception("Error al obtener el catálogo de regalos: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching gift catalog", e)
            Result.failure(e)
        }
    }

    override suspend fun getWalletBalance(): Result<Int> {
        return try {
            val response = api.rpcWalletBalance(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader()
            )
            if (response.isSuccessful) {
                Result.success(asBalance(parseJsonMap(response.body()?.string())))
            } else {
                Result.failure(Exception("Error al obtener el saldo: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching wallet balance", e)
            Result.failure(e)
        }
    }

    override suspend fun sendGift(streamId: String, giftCode: String, quantity: Int): Result<LiveGiftResult> {
        return try {
            val response = api.rpcSendGift(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader(),
                body = LiveSendGiftRequest(
                    streamId = streamId,
                    giftCode = giftCode,
                    quantity = quantity
                )
            )
            if (response.isSuccessful) {
                val map = parseJsonMap(response.body()?.string())
                Result.success(
                    LiveGiftResult(
                        ok = asBool(map, "ok"),
                        balance = asBalance(map),
                        total = asInt(map, "total"),
                        reason = map?.get("reason") as? String
                    )
                )
            } else {
                Result.failure(Exception("Error al enviar el regalo: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception sending live gift", e)
            Result.failure(e)
        }
    }

    override suspend fun setViewerCount(streamId: String, count: Int): Result<Unit> {
        return try {
            val response = api.rpcSetViewerCount(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader(),
                body = LiveSetViewerCountRequest(streamId = streamId, count = count)
            )
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Error al publicar espectadores: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception publishing viewer count", e)
            Result.failure(e)
        }
    }

    override suspend fun sendHeartbeat(streamId: String): Result<Unit> {
        return try {
            val response = api.rpcLiveHeartbeat(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader(),
                body = LiveHeartbeatRequest(streamId = streamId)
            )
            if (response.isSuccessful) Result.success(Unit)
            else {
                // 404 = la columna/RPC aún no desplegada en el remote (migración vieja).
                // Se traga el fallo: el auto-end por heartbeat NUNCA debe tumbar un live.
                Log.w(TAG, "Heartbeat rechazado (${response.code()}): ${response.errorBody()?.string()}")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception sending live heartbeat", e)
            // Fire-and-forget: el heartbeat no debe interrumpir el directo.
            Result.success(Unit)
        }
    }

    override suspend fun registerJoin(streamId: String): Result<Boolean> {
        return try {
            val response = api.rpcJoinStream(
                apiKey = SupabaseClient.supabaseAnonKey,
                authorization = getAuthHeader(),
                body = LiveJoinStreamRequest(streamId = streamId)
            )
            if (response.isSuccessful) {
                val map = parseJsonMap(response.body()?.string())
                Result.success(asBool(map, "inserted"))
            } else {
                Result.failure(Exception("Error al registrar la entrada: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception registering live join", e)
            Result.failure(e)
        }
    }
}
