package com.example.data.repository.states

import com.example.data.database.StateEntity
import com.example.data.model.UserState
import com.example.data.repository.VcdnUrlResolver
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * URL resolution policy for states (stories/reels).
 * The signed HLS `.m3u8` streamUrl must NEVER be persisted: it expires. Room
 * and the remote DTOs keep the stable `vcdn://{videoId}` pointer. See [VcdnUrlResolver].
 */
object StateUrlResolver {

    /** Defensive restore: recovers a stable `vcdn://` pointer when a legacy signed
     *  `.m3u8` was persisted and the row now carriesa [UserState.vcdnVideoId]. */
    fun stabilizeForRoom(state: UserState): UserState {
        val vId = state.vcdnVideoId?.takeIf { it.isNotBlank() }
        if (vId == null) return state
        val url = state.mediaUrl.orEmpty()
        return if (url.isBlank() || (state.mediaUrl?.contains(".m3u8", ignoreCase = true)) == true) {
            state.copy(mediaUrl = "vcdn://$vId")
        } else state
    }

    fun stabilizeEntityForRoom(entity: StateEntity, existing: StateEntity? = null): StateEntity {
        val merged = if (existing != null) {
            entity.copy(
                vcdnVideoId = entity.vcdnVideoId ?: existing.vcdnVideoId,
                vcdnPosterUrl = entity.vcdnPosterUrl ?: existing.vcdnPosterUrl,
                localVideoPath = entity.localVideoPath ?: existing.localVideoPath,
                thumbnailUrl = entity.thumbnailUrl ?: existing.thumbnailUrl,
                mediaUrl = entity.mediaUrl.ifBlank { existing.mediaUrl }
            )
        } else entity

        val vId = merged.vcdnVideoId?.takeIf { it.isNotBlank() }
        return if (vId != null) {
            val url = merged.mediaUrl.orEmpty()
            if (url.isBlank() || url.contains(".m3u8", ignoreCase = true)) {
                merged.copy(mediaUrl = "vcdn://$vId")
            } else merged
        } else merged
    }

    /** Resolve a poster/thumbnail for cards without touching the playback URL. */
    suspend fun resolveForDisplay(state: UserState): UserState = withContext(Dispatchers.IO) {
        var s = stabilizeForRoom(state)
        if (VcdnUrlResolver.isVcdnUrl(s.mediaUrl)) {

            val poster = VcdnUrlResolver.resolvePoster(s.mediaUrl)
            val posterFinal = if (poster.isNullOrBlank()) s.vcdnPosterUrl else poster
            if (posterFinal != null && s.vcdnPosterUrl.isNullOrBlank()) {
                s = s.copy(vcdnPosterUrl = posterFinal)
            }
            if (posterFinal != null && s.thumbnailUrl.isNullOrBlank()) {
                s = s.copy(thumbnailUrl = posterFinal)
            }
        }
        s
    }

    /** Synchronous variant for Coil (images/posters) at the UI boundary. */
    fun resolveForDisplayBlocking(state: UserState): UserState = runBlocking { resolveForDisplay(state) }

    /** Token-fetch + session pre-flight shared by the states remote data sources. */
    suspend fun ensureSession(): String? = withContext(Dispatchers.IO) {
        SessionManager.validateAndRefreshSessionIfNeeded()
        return@withContext SupabaseClient.currentToken
    }
}
