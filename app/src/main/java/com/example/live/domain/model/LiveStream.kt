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
    @Json(name = "ended_at") val endedAt: String?
)
