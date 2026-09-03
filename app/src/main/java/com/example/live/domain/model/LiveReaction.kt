package com.example.live.domain.model

data class LiveReaction(
    val id: String = java.util.UUID.randomUUID().toString(),
    val streamId: String,
    val userId: String,
    val type: String = "heart"
)
