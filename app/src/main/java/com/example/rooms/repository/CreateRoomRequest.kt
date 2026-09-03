package com.example.rooms.repository

data class CreateRoomRequest(
    val name: String,
    val description: String = "",
    val coverUrl: String? = null,
    val category: String = "general",
    val visibility: String = "public"
)
