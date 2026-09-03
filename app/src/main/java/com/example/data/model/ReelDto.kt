package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ReelDto(
    @Json(name = "id") val id: String,
    @Json(name = "author_id") val authorId: String,
    @Json(name = "media_url") val mediaUrl: String,
    @Json(name = "media_type") val mediaType: String,
    @Json(name = "caption") val caption: String?,
    @Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "vcdn_video_id") val vcdnVideoId: String? = null,
    @Json(name = "vcdn_poster_url") val vcdnPosterUrl: String? = null
)
