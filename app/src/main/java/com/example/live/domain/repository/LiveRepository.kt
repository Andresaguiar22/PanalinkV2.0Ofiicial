package com.example.live.domain.repository

import com.example.live.domain.model.LiveComment
import com.example.live.domain.model.LiveGift
import com.example.live.domain.model.LiveGiftResult
import com.example.live.domain.model.LiveStream
import com.example.live.domain.model.LiveStreamStats

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

    suspend fun getStats(streamId: String): Result<LiveStreamStats>
    suspend fun sendLikes(streamId: String, quantity: Int): Result<Int>
    suspend fun getGiftCatalog(): Result<List<LiveGift>>
    suspend fun getWalletBalance(): Result<Int>
    suspend fun sendGift(streamId: String, giftCode: String, quantity: Int): Result<LiveGiftResult>
    suspend fun setViewerCount(streamId: String, count: Int): Result<Unit>

    /** Heartbeat del host: mantiene vivo el auto-end por TTL de pg_cron. */
    suspend fun sendHeartbeat(streamId: String): Result<Unit>
    suspend fun registerJoin(streamId: String): Result<Boolean>
}
