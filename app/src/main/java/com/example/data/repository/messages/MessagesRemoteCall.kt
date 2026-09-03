package com.example.data.repository.messages

import android.util.Log
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SessionManager

/**
 * Compartido token/retry helper para the messages data sources (mismo patron que
 * [runCall] en the facade MessagesRepository, pero top-level para no depender
 * de la instancia del repositorio).
 */
internal suspend fun <R> runMessagesCall(
    tag: String,
    call: suspend (authorization: String) -> retrofit2.Response<R>
): retrofit2.Response<R>? {
    return com.example.util.Resilience.retry(
        times = 5,
        initialDelay = 1000L,
        maxDelay = 10000L,
        factor = 2.0,
        retryCondition = { it is java.io.IOException || (it is retrofit2.HttpException && it.code() in 500..599) || (it is retrofit2.HttpException && it.code() == 408) }
    ) {
        SessionManager.validateAndRefreshSessionIfNeeded()
        var token = SupabaseClient.currentToken ?: return@retry null
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