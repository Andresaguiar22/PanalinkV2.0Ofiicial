package com.example.live.domain.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.OffsetDateTime

@JsonClass(generateAdapter = true)
data class LiveGuest(
    @Json(name = "guest_user_id") val userId: String,
    @Json(name = "stream_id") val streamId: String,
    @Json(name = "status") val status: GuestStatus,
    @Json(name = "joined_at") val joinedAt: OffsetDateTime? = null
)

enum class GuestStatus {
    PENDING,
    INVITED,
    ACCEPTED,
    REJECTED,
    ACTIVE,
    CONNECTED,
    DISCONNECTED,
    REMOVED,
    LEFT
}
