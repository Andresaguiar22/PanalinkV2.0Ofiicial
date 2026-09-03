package com.example.live.domain.repository

import com.example.live.domain.model.LiveComment
import com.example.live.domain.model.LiveStream

data class LiveTokenResult(
    val token: String,
    val serverUrl: String,
    val roomName: String
)

interface LiveRepository {
    suspend fun createLiveStream(title: String, description: String?, thumbnailUrl: String?): Result<LiveStream>
    suspend fun getLiveStreams(): Result<List<LiveStream>>
    suspend fun getActiveLives(): Result<List<LiveStream>> = getLiveStreams()
    suspend fun getLiveStream(id: String): Result<LiveStream?>
    suspend fun startLiveStream(id: String): Result<Unit>
    suspend fun endLiveStream(id: String): Result<Unit>
    suspend fun getLiveKitToken(roomName: String, identity: String, role: String): Result<LiveTokenResult>
    suspend fun getComments(streamId: String): Result<List<LiveComment>>
    suspend fun postComment(streamId: String, text: String): Result<LiveComment>
}
