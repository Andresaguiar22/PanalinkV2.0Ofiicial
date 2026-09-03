package com.example.media.playlist

import android.util.Log
import com.example.data.repository.UploadRepository
import com.example.media.audio.AudioRepository
import com.example.media.audio.AudioTrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Builds rich playlist-share payloads (uploading local audio to the CDN so the
 * recipient can actually stream it) and imports shared playlists into the
 * recipient's local library.
 */
class PlaylistShareManager(
    private val playlistRepository: PlaylistRepository,
    private val audioRepository: AudioRepository
) {
    private val TAG = "PlaylistShareManager"
    private val uploadRepository = UploadRepository()

    suspend fun buildRichPayload(
        playlist: PlaylistEntity,
        tracks: List<AudioTrackEntity>,
        sharedBy: String
    ): PlaylistSharePayload = withContext(Dispatchers.IO) {
        val coverUrl = resolveRemoteUrl(playlist.coverPath, sharedBy, "image/jpeg", "plcover")

        val sharedTracks = tracks.map { track ->
            val remoteUrl = resolveRemoteUrl(track.filePath, sharedBy, mimeFor(track.filePath), "pltrack")
            SharedTrackInfo(
                id = track.id,
                title = track.title,
                artist = track.artist,
                durationMs = track.durationMs,
                remoteUrl = remoteUrl
            )
        }

        PlaylistSharePayload(
            playlistId = playlist.id,
            title = playlist.name,
            description = playlist.description,
            coverPath = coverUrl ?: playlist.coverPath,
            trackCount = tracks.size,
            durationMs = tracks.sumOf { it.durationMs },
            trackIds = tracks.map { it.id },
            sharedBy = sharedBy,
            tracks = sharedTracks
        )
    }

    /** Returns an http(s) URL for the file, uploading it when it only exists locally. */
    private suspend fun resolveRemoteUrl(path: String?, userId: String, mime: String, prefix: String): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null
        return try {
            uploadRepository.uploadVideo(file, mime, "", userId, fileNamePrefix = prefix, type = "audio")
                .getOrNull()?.url
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed for $path", e)
            null
        }
    }

    private fun mimeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "flac" -> "audio/flac"
        else -> "audio/mpeg"
    }

    /**
     * Imports a shared playlist into the local library.
     * Returns true when the playlist was created, false when it already existed.
     */
    suspend fun importSharedPlaylist(payload: PlaylistSharePayload, currentUserId: String): Boolean =
        withContext(Dispatchers.IO) {
            if (playlistRepository.getPlaylistById(payload.playlistId) != null) return@withContext false

            playlistRepository.createPlaylist(
                PlaylistEntity(
                    id = payload.playlistId,
                    ownerId = currentUserId,
                    name = payload.title,
                    description = payload.description ?: "Compartida por ${payload.sharedBy}",
                    coverPath = payload.coverPath,
                    createdAt = payload.sharedAt,
                    updatedAt = System.currentTimeMillis(),
                    isDirty = false
                )
            )

            payload.tracks.forEachIndexed { index, info ->
                val url = info.remoteUrl ?: return@forEachIndexed
                val trackId = "shared_${payload.playlistId}_$index"
                if (audioRepository.getTrackById(trackId) == null) {
                    audioRepository.saveTrack(
                        AudioTrackEntity(
                            id = trackId,
                            userId = currentUserId,
                            title = info.title,
                            artist = info.artist,
                            album = payload.title,
                            coverPath = payload.coverPath,
                            durationMs = info.durationMs,
                            filePath = url,
                            trackType = "SHARED"
                        )
                    )
                }
                playlistRepository.upsertTrackWithPosition(payload.playlistId, trackId, index)
            }
            true
        }
}
