package com.example.live.data.repository

import android.content.Context
import com.example.data.supabase.SupabaseClient
import com.example.live.domain.repository.LiveReportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LiveReportRepositoryImpl(private val context: Context) : LiveReportRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun report(
        streamId: String,
        reportedUserId: String?,
        commentId: String?,
        reason: String
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "${SupabaseClient.supabaseUrl}/rest/v1/live_reports"
                val token = SupabaseClient.currentToken ?: return@withContext Result.failure(Exception("No token"))
                val body = JSONObject().apply {
                    put("stream_id", streamId)
                    put("reporter_user_id", SupabaseClient.currentUser?.id ?: "")
                    if (reportedUserId != null) put("reported_user_id", reportedUserId)
                    if (commentId != null) put("comment_id", commentId)
                    put("reason", reason)
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
                    else Result.failure(Exception("Error submitting report: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
