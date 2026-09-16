package com.example.live.domain.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LiveStream(
    @Json(name = "id") val id: String,
    @Json(name = "host_id") val hostId: String,
    @Json(name = "room_name") val roomName: String,
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String?,
    @Json(name = "thumbnail_url") val thumbnailUrl: String?,
    @Json(name = "status") val status: String,
    @Json(name = "viewer_count") val viewerCount: Int = 0,
    @Json(name = "started_at") val startedAt: String?,
    @Json(name = "ended_at") val endedAt: String?,
    @Json(name = "live_stream_stats") val stats: LiveStreamStats? = null
)

/** Contadores agregados de una transmisión (tabla public.live_stream_stats). */
@JsonClass(generateAdapter = true)
data class LiveStreamStats(
    @Json(name = "stream_id") val streamId: String,
    @Json(name = "like_count") val likeCount: Int = 0,
    @Json(name = "gift_count") val giftCount: Int = 0,
    @Json(name = "gift_coins") val giftCoins: Long = 0,
    @Json(name = "viewer_count") val viewerCount: Int = 0
)
