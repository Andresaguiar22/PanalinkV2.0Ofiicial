package com.example.data.repository.states

import android.util.Log
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SessionManager

/** Shared token/retry helper for the states remote data sources. */
internal suspend fun <R> runStatesCall(
    tag: String,
    call: suspend (authorization: String) -> retrofit2.Response<R>
): retrofit2.Response<R>? {
    return com.example.util.Resilience.retry(
        times = 3,
        initialDelay = 500L,
        retryCondition = { it is java.io.IOException || (it is retrofit2.HttpException && it.code() in 500..599) }
    ) {
        val token = SupabaseClient.currentToken ?: ""
        var bearer = "Bearer $token"

        var response = try {
            call(bearer)
        } catch (e: Exception) {
            Log.e(tag, "Network call failed", e)
            throw e
        }

        if (response != null && response.code() == 401) {
            Log.i(tag, "401/JWT expired detected. Triggering refresh session...")
            val refreshed = SessionManager.refreshSession()
            if (refreshed) {
                val newToken = SupabaseClient.currentToken ?: ""
                bearer = "Bearer $newToken"
                response = try {
                    call(bearer)
                } catch (e: Exception) {
                    Log.e(tag, "Retry call failed", e)
                    throw e
                }
            }
        }
        response
    }
}
