package com.example.media.social

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UserState
import com.example.data.repository.CdnManager
import com.example.data.repository.VcdnUrlResolver
import com.example.media.model.MediaResource
import com.example.media.repository.MediaRepository
import com.example.media.storage.MediaStorageManager
import com.example.ui.components.rememberAsyncMediaUrl
import java.io.File

object StoryMediaResolver {

    @Composable
    fun rememberResolvedStoryMediaResource(
        state: UserState
    ): MediaResource {
        val rawRemoteUrl = state.mediaUrl
        val localVideoPath = state.localVideoPath

        // Local disk always wins. This is the critical offline path: content already
        // cached by Room/MediaStorage must remain usable with zero network access.
        if (!localVideoPath.isNullOrBlank()) {
            val file = File(localVideoPath)
            if (file.exists() && file.length() > 0) {
                return MediaResource.Local(file.absolutePath)
            }
        }

        if (rawRemoteUrl.isNullOrBlank()) {
            return MediaResource.Missing
        }

        if (rawRemoteUrl.startsWith("file://") || rawRemoteUrl.startsWith("/")) {
            val cleanPath = rawRemoteUrl.removePrefix("file://")
            val file = File(cleanPath)
            return if (file.exists() && file.length() > 0) {
                MediaResource.Local(file.absolutePath)
            } else {
                MediaResource.Missing
            }
        }

        // vcdn:// is a stable pointer, not an image URL. Keep the pointer until the
        // async media resolver can replace it; never block Compose with runBlocking.
        if (VcdnUrlResolver.isVcdnUrl(rawRemoteUrl)) {
            return MediaResource.Remote(rawRemoteUrl)
        }

        // All remote resolution runs outside Compose/Main. CdnManager.resolveMediaUrl
        // is suspend and may perform CDN/VCDN network I/O.
        val remoteUrl = rememberAsyncMediaUrl(rawRemoteUrl)
        if (remoteUrl.isBlank()) {
            return MediaResource.Missing
        }

        val context = LocalContext.current
        val repository = remember {
            val storage = MediaStorageManager(context.applicationContext)
            MediaRepository(context.applicationContext, storage)
        }

        // HLS manifests (.m3u8) cannot be cached as a plain local file. The story
        // viewer/player handles the stream directly while thumbnails use posters.
        if (remoteUrl.endsWith(".m3u8", ignoreCase = true) || remoteUrl.contains(".m3u8?")) {
            return MediaResource.Remote(remoteUrl)
        }

        val mediaId = remember(rawRemoteUrl, state.id) {
            "story_${state.id}_${kotlin.math.abs(rawRemoteUrl.hashCode())}"
        }
        val mediaState by repository.observeMedia(mediaId)
            .collectAsStateWithLifecycle(initialValue = null)

        LaunchedEffect(remoteUrl, state.id) {
            // Offline: do not even start a sync attempt. If Room/disk has a cached
            // asset, the state above will use it; otherwise the UI safely keeps the
            // remote URL as an unavailable fallback.
            if (!com.example.util.NetworkMonitor.isOnline.value) return@LaunchedEffect

            if (mediaState == null || mediaState?.localPath.isNullOrBlank() || !File(mediaState?.localPath ?: "").exists()) {
                val type = if (
                    state.mediaType.equals("video", ignoreCase = true) ||
                    remoteUrl.endsWith(".mp4", ignoreCase = true) ||
                    remoteUrl.contains("/video/")
                ) "video" else "image"
                runCatching {
                    repository.syncManager.syncMedia(mediaId, remoteUrl, type, state.userId)
                }.onFailure { error ->
                    android.util.Log.w("StoryMediaResolver", "Offline-safe media sync failed for $mediaId", error)
                }
            }
        }

        return remember(mediaState, remoteUrl) {
            val localPath = mediaState?.localPath
            if (!localPath.isNullOrBlank()) {
                val file = File(localPath)
                if (file.exists() && file.length() > 0) {
                    MediaResource.Local(file.absolutePath)
                } else {
                    MediaResource.Remote(remoteUrl)
                }
            } else {
                MediaResource.Remote(remoteUrl)
            }
        }
    }
}
