package com.example.media.playlist

import kotlinx.serialization.Serializable

/**
 * Lightweight track descriptor embedded in a playlist share message.
 * [remoteUrl] lets the recipient stream the song even if it never had the file locally.
 */
@Serializable
data class SharedTrackInfo(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long = 0L,
    val remoteUrl: String? = null
)

/**
 * P6.7.5 - Playlist Share Payload
 * Represents a secure abstraction of a playlist for internal sharing in PanaLink.
 */
@Serializable
data class PlaylistSharePayload(
    val playlistId: String,
    val title: String,
    val description: String?,
    val coverPath: String?,
    val trackCount: Int,
    val durationMs: Long,
    val trackIds: List<String>,
    val sharedBy: String,
    val sharedAt: Long = System.currentTimeMillis(),
    val tracks: List<SharedTrackInfo> = emptyList()
)
