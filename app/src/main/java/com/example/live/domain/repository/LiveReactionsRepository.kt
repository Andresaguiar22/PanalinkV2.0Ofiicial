package com.example.live.domain.repository

import kotlinx.coroutines.flow.SharedFlow

interface LiveReactionsRepository {
    val reactionEvents: SharedFlow<Unit>
    suspend fun sendReaction(streamId: String)
    fun startListening(streamId: String)
    fun stopListening()
}
