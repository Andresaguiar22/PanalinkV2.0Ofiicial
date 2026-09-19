package com.example.ui.profile.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.data.repository.VcdnUrlResolver

/**
 * Resolves the tile thumbnail for a reel.
 *
 * Priority:
 *  1. A live `thumbnail_url`/`vcdn_poster_url` (persisted and still served).
 *  2. The VCDN BFF poster for the reel's `vcdn://` pointer. This is the real fix:
 *     rows persisted before VCDN moved its posters to cdn.vcdn.me keep a dead
 *     `storage.vcdn.me` URL, so we re-resolve it on the fly instead of leaving the
 *     tile gray. The BFF poster URL is stable, so it is cached in memory.
 *
 * Resolution happens off the main thread (Compose effect), so the grid never blocks.
 */
@Composable
internal fun rememberReelThumbnail(
    thumbnailUrl: String?,
    posterUrl: String?,
    mediaUrl: String?,
    vcdnVideoId: String? = null
): String {
    val candidate = listOfNotNull(thumbnailUrl, posterUrl)
        .firstOrNull { !it.isNullOrBlank() && !VcdnUrlResolver.isDeadPosterHost(it) }

    // The `vcdn://` pointer is the only stable handle. `mediaUrl` usually arrives
    // already resolved to a signed https URL (the profile/repo layer resolves it),
    // which `resolvePoster` cannot interpret — so prefer the stored video id.
    val stablePointer = vcdnVideoId
        ?.takeIf { it.isNotBlank() }
        ?.let { "vcdn://$it" }
        ?: mediaUrl

    // Seed with the live candidate so the very first frame already paints the
    // image: no empty intermediate state (which showed as a gray tile) and no
    // network call at all for rows whose thumbnail is already healthy.
    var resolved by remember(candidate, stablePointer) { mutableStateOf(candidate.orEmpty()) }

    LaunchedEffect(candidate, stablePointer) {
        if (candidate.isNullOrBlank()) {
            // Stored thumbnail is missing or points at the dead host: ask the BFF
            // for the current poster of this specific video.
            resolved = if (VcdnUrlResolver.isVcdnUrl(stablePointer)) {
                VcdnUrlResolver.resolvePoster(stablePointer) ?: ""
            } else {
                ""
            }
        }
    }

    return resolved
}
