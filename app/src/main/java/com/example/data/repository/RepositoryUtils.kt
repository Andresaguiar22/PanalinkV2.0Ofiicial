package com.example.data.repository

import com.example.data.supabase.SupabaseClient
import retrofit2.Response
import android.util.Log

suspend fun <T> runCall(block: suspend (String) -> Response<T>): Response<T>? {
    val auth = if (!SupabaseClient.currentToken.isNullOrEmpty()) {
        "Bearer ${SupabaseClient.currentToken}"
    } else {
        "Bearer ${SupabaseClient.supabaseAnonKey}"
    }
    return try {
        block(auth)
    } catch (e: Exception) {
        Log.e("RepositoryUtils", "runCall failed", e)
        null
    }
}
