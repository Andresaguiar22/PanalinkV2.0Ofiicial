package com.example.live.data.repository

import android.content.Context
import android.util.Log
import com.example.data.supabase.SupabaseClient
import com.example.live.data.remote.LiveSupabaseApi
import com.example.live.domain.model.LiveComment
import com.example.live.domain.model.LiveStream
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

    override suspend fun endLiveStream(id: String): Result<Unit> {
        return try {
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
                Result.success(Unit)
            } else {
                Result.failure(Exception("Error al finalizar transmisión: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception ending live stream", e)
            Result.failure(e)
        }
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
}
