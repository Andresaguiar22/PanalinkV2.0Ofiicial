package com.example.live.data.repository

import android.content.Context
import com.example.data.supabase.SupabaseClient
import com.example.live.domain.model.LiveGuest
import com.example.live.domain.repository.LiveGuestRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LiveGuestRepositoryImpl(private val context: Context) : LiveGuestRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val moshi = Moshi.Builder().build()

    override suspend fun inviteGuest(streamId: String, guestUserId: String): Result<LiveGuest> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "${SupabaseClient.supabaseUrl}/rest/v1/live_guests"
                val token = SupabaseClient.currentToken ?: return@withContext Result.failure(Exception("No token"))
                val body = JSONObject().apply {
                    put("stream_id", streamId)
                    put("guest_user_id", guestUserId)
                    put("status", "INVITED")
                }.toString().toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .header("apikey", SupabaseClient.supabaseAnonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=representation")
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    if (response.isSuccessful && responseBody.isNotBlank()) {
                        val adapter = moshi.adapter<List<LiveGuest>>(Types.newParameterizedType(List::class.java, LiveGuest::class.java))
                        val list = adapter.fromJson(responseBody)
                        val guest = list?.firstOrNull() ?: return@use Result.failure(Exception("Guest not created"))
                        Result.success(guest)
                    } else {
                        Result.failure(Exception("Error inviting guest: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun requestToJoin(streamId: String): Result<LiveGuest> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "${SupabaseClient.supabaseUrl}/rest/v1/live_guests"
                val token = SupabaseClient.currentToken ?: return@withContext Result.failure(Exception("No token"))
                val body = JSONObject().apply {
                    put("stream_id", streamId)
                    put("guest_user_id", SupabaseClient.currentUser?.id ?: "")
                    put("status", "PENDING")
                }.toString().toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .header("apikey", SupabaseClient.supabaseAnonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Prefer", "return=representation")
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    if (response.isSuccessful && responseBody.isNotBlank()) {
                        val adapter = moshi.adapter<List<LiveGuest>>(Types.newParameterizedType(List::class.java, LiveGuest::class.java))
                        val list = adapter.fromJson(responseBody)
                        val guest = list?.firstOrNull() ?: return@use Result.failure(Exception("Guest not created"))
                        Result.success(guest)
                    } else {
                        Result.failure(Exception("Error requesting to join: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun leaveLive(streamId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = "${SupabaseClient.supabaseUrl}/rest/v1/live_guests?stream_id=eq.$streamId&guest_user_id=eq.${SupabaseClient.currentUser?.id ?: ""}"
                val token = SupabaseClient.currentToken ?: return@withContext Result.failure(Exception("No token"))

                val activeBody = JSONObject().put("status", "DISCONNECTED").toString().toRequestBody(jsonMediaType)
                val pendingBody = JSONObject().put("status", "REJECTED").toString().toRequestBody(jsonMediaType)

                client.newCall(
                    Request.Builder().url("$baseUrl&status=in.(ACCEPTED,ACTIVE,CONNECTED)").patch(activeBody)
                        .header("apikey", SupabaseClient.supabaseAnonKey)
                        .header("Authorization", "Bearer $token")
                        .header("Content-Type", "application/json")
                        .header("Prefer", "return=minimal").build()
                ).execute().use { response ->
                    if (!response.isSuccessful) return@withContext Result.failure(Exception("Error leaving live: ${response.code}"))
                }

                client.newCall(
                    Request.Builder().url("$baseUrl&status=eq.PENDING").patch(pendingBody)
                        .header("apikey", SupabaseClient.supabaseAnonKey)
                        .header("Authorization", "Bearer $token")
                        .header("Content-Type", "application/json")
                        .header("Prefer", "return=minimal").build()
                ).execute().use { response ->
                    if (response.isSuccessful) Result.success(Unit)
                    else Result.failure(Exception("Error cancelling request: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun acceptInvitation(streamId: String, guestUserId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "${SupabaseClient.supabaseUrl}/rest/v1/live_guests?stream_id=eq.$streamId&guest_user_id=eq.$guestUserId"
                val token = SupabaseClient.currentToken ?: return@withContext Result.failure(Exception("No token"))
                val body = JSONObject().apply {
                    put("status", "ACCEPTED")
                    put("joined_at", java.time.Instant.now().toString())
                }.toString().toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url(url)
                    .patch(body)
                    .header("apikey", SupabaseClient.supabaseAnonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) Result.success(Unit)
                    else Result.failure(Exception("Error accepting invitation: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun rejectInvitation(streamId: String, guestUserId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "${SupabaseClient.supabaseUrl}/rest/v1/live_guests?stream_id=eq.$streamId&guest_user_id=eq.$guestUserId"
                val token = SupabaseClient.currentToken ?: return@withContext Result.failure(Exception("No token"))
                val body = JSONObject().put("status", "REJECTED").toString().toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url(url)
                    .patch(body)
                    .header("apikey", SupabaseClient.supabaseAnonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) Result.success(Unit)
                    else Result.failure(Exception("Error rejecting invitation: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun removeGuest(streamId: String, guestUserId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "${SupabaseClient.supabaseUrl}/rest/v1/live_guests?stream_id=eq.$streamId&guest_user_id=eq.$guestUserId"
                val token = SupabaseClient.currentToken ?: return@withContext Result.failure(Exception("No token"))
                val body = JSONObject().put("status", "REMOVED").toString().toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url(url)
                    .patch(body)
                    .header("apikey", SupabaseClient.supabaseAnonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) Result.success(Unit)
                    else Result.failure(Exception("Error removing guest: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getGuests(streamId: String): Result<List<LiveGuest>> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "${SupabaseClient.supabaseUrl}/rest/v1/live_guests?stream_id=eq.$streamId"
                val token = SupabaseClient.currentToken ?: return@withContext Result.failure(Exception("No token"))

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header("apikey", SupabaseClient.supabaseAnonKey)
                    .header("Authorization", "Bearer $token")
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    if (response.isSuccessful && responseBody.isNotBlank()) {
                        val adapter = moshi.adapter<List<LiveGuest>>(Types.newParameterizedType(List::class.java, LiveGuest::class.java))
                        val list = adapter.fromJson(responseBody) ?: emptyList()
                        Result.success(list)
                    } else {
                        Result.success(emptyList())
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override fun observeGuests(streamId: String): kotlinx.coroutines.flow.Flow<List<LiveGuest>> {
        return kotlinx.coroutines.flow.callbackFlow {
            val guests = mutableMapOf<String, LiveGuest>()
            val manager = com.example.live.data.remote.LiveGuestRealtimeManager(streamId) { guest ->
                guests[guest.userId] = guest
                trySend(guests.values.toList())
            }
            
            // Initial fetch
            launch {
                getGuests(streamId).getOrNull()?.forEach { guests[it.userId] = it }
                trySend(guests.values.toList())
            }
            
            manager.start()
            awaitClose { manager.stop() }
        }
    }
}
