package com.example.live.data.repository

import android.content.Context
import com.example.data.supabase.SupabaseClient
import com.example.live.domain.repository.LiveModerationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LiveModerationRepositoryImpl(private val context: Context) : LiveModerationRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun deleteComment(commentId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "${SupabaseClient.supabaseUrl}/rest/v1/live_comments?id=eq.$commentId"
                val token = SupabaseClient.currentToken ?: return@withContext Result.failure(Exception("No token"))
                val requestBody = JSONObject().put("is_deleted", true).toString().toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url(url)
                    .patch(requestBody)
                    .header("apikey", SupabaseClient.supabaseAnonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) Result.success(Unit)
                    else Result.failure(Exception("Error deleting comment: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun blockUser(streamId: String, userId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "${SupabaseClient.supabaseUrl}/rest/v1/live_blocked_users"
                val token = SupabaseClient.currentToken ?: return@withContext Result.failure(Exception("No token"))
                val body = JSONObject().apply {
                    put("stream_id", streamId)
                    put("user_id", userId)
                }.toString().toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .header("apikey", SupabaseClient.supabaseAnonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) Result.success(Unit)
                    else Result.failure(Exception("Error blocking user: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun muteUser(streamId: String, userId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "${SupabaseClient.supabaseUrl}/rest/v1/live_muted_users"
                val token = SupabaseClient.currentToken ?: return@withContext Result.failure(Exception("No token"))
                val body = JSONObject().apply {
                    put("stream_id", streamId)
                    put("user_id", userId)
                }.toString().toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .header("apikey", SupabaseClient.supabaseAnonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) Result.success(Unit)
                    else Result.failure(Exception("Error muting user: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

}
