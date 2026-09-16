package com.example.live.domain.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LiveComment(
    @Json(name = "id") val id: String,
    @Json(name = "stream_id") val streamId: String,
    @Json(name = "user_id") val userId: String,
    @Json(name = "text") val text: String,
    @Json(name = "created_at") val createdAt: String?,
    @Json(name = "is_deleted") val isDeleted: Boolean = false,
    /** "chat" para mensajes normales, "join" para eventos de entrada al directo. */
    @Json(name = "kind") val kind: String = KIND_CHAT
) {
    val isJoinEvent: Boolean get() = kind == KIND_JOIN

    companion object {
        const val KIND_CHAT = "chat"
        const val KIND_JOIN = "join"
    }
}
