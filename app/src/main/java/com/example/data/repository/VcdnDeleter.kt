package com.example.data.repository

import android.net.Uri
import android.util.Log
import com.example.data.supabase.SessionManager
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object VcdnDeleter {
    private const val TAG = "VcdnDeleter"
    private const val FUNCTION = "/functions/v1/vcdn-delete"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun deleteVideos(
        mediaUrls: Collection<String?>? = null,
        videoIds: Collection<String?>? = null
    ) {
        val ids = LinkedHashSet<String>()
        mediaUrls?.forEach { url ->
            tryVcdnId(url?.trim()).also { id ->
                if (id != null) ids.add(id)
            }
        }
        videoIds?.forEach { id ->
            val clean = id?.trim().orEmpty()
            if (clean.isNotEmpty()) ids.add(clean)
        }

        if (ids.isEmpty()) return
        ids.forEach { videoId ->
            try {
                withContext(Dispatchers.IO) {
                    callDelete(videoId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "VCDN delete failed for $videoId: ${e.localizedMessage}", e)
            }
        }
    }

    private fun tryVcdnId(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        if (raw.startsWith("vcdn://")) {
            val host = Uri.parse(raw).host
            return host?.trim().orEmpty().ifBlank { null }
        }
        if (raw.contains("vcdn.me")) {
            return raw.substringAfterLast("/").trim().ifBlank { null }
        }
        return null
    }

    private suspend fun callDelete(videoId: String) {
        val token: String? = SessionManager.getUserAuthToken() ?: SupabaseClient.currentToken
        if (token.isNullOrBlank()) throw Exception("no auth")

        val body = JSONObject().put("videoId", videoId).toString()
            .toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(SupabaseClient.supabaseUrl.trimEnd('/') + FUNCTION)
            .header("apikey", SupabaseClient.supabaseAnonKey)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(body)
            .build()
        val call = client.newCall(request)
        val resp = call.execute()
        val b = resp.body?.string().orEmpty()
        resp.close()
        if (!resp.isSuccessful) {
            throw Exception("HTTP ${resp.code}: ${b.take(300)}")
        }
    }
}