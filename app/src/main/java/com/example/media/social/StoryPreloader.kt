package com.example.media.social

import android.content.Context
import android.util.Log
import com.example.data.model.UserState
import com.example.data.model.UserStateWithUser
import com.example.media.repository.MediaRepository
import com.example.media.storage.MediaStorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object StoryPreloader {
    private const val TAG = "StoryPreloader"

    private val preloadedIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun preloadStories(
        context: Context,
        userStates: List<UserStateWithUser>,
        currentUserIndex: Int,
        currentStoryIndex: Int,
        scope: CoroutineScope
    ) {
        if (userStates.isEmpty() || currentUserIndex < 0 || currentUserIndex >= userStates.size) return

        val appCtx = context.applicationContext
        scope.launch(Dispatchers.IO) {
            // Offline: no descargar nada; las miniaturas cacheadas se renderizan
            // desde el disco y cuando vuelva la red se repetirá el preload..
            if (!com.example.util.NetworkMonitor.isOnline.value) {
                Log.d(TAG, "Offline: skipping story preload")
                return@launch
            }
            val repository = MediaRepository(appCtx, MediaStorageManager(appCtx))

            val currentGroup = userStates.getOrNull(currentUserIndex)
            val nextGroup = userStates.getOrNull(currentUserIndex + 1)

            val targets = mutableListOf<UserState>()

            // Current user story
            currentGroup?.state?.let { state ->
                targets.add(state)
            }

            // Next user story
            nextGroup?.state?.let { state ->
                targets.add(state)
            }

            for (target in targets) {
                val remoteUrl = com.example.data.repository.CdnManager.resolveMediaUrlSync(target.mediaUrl) ?: continue
                // HLS manifests (.m3u8) cannot be cached as plain local files — the
                // playlist text would be saved as a .bin that Coil can't render. VCDN
                // posters are resolved by the carousel, not preloaded herehar.
                if (remoteUrl.endsWith(".m3u8", ignoreCase = true) || remoteUrl.contains(".m3u8?") || remoteUrl.startsWith("vcdn://")) continue
                if (remoteUrl.startsWith("http://") || remoteUrl.startsWith("https://")) {
                    val mediaId = "story_${target.id}_${kotlin.math.abs(remoteUrl.hashCode())}"
                    val type = if (target.mediaType == "video" || remoteUrl.endsWith(".mp4")) "video" else "image"
                    try {
                        Log.i(TAG, "Preloading story: $mediaId")
                        repository.syncManager.syncMedia(mediaId, remoteUrl, type, target.userId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Preload story failed for $mediaId", e)
                    }
                }
            }
        }
    }

    /**
     * Pre-downloads the first media item of every story group to local storage in
     * the background so the story carousel can render thumbnails instantly on the
     * next app entry (cache-first), exactly like chats load from Room. Idempotent:
     * each story is synced at most once per process via an in-memory dedupe set.
     */
    fun preloadAllStories(
        context: Context,
        userStates: List<UserStateWithUser>,
        scope: CoroutineScope
    ) {
        if (userStates.isEmpty()) return
        val appCtx = context.applicationContext
        // One thumbnail per user group keeps the cache cheap; dedupe by user.
        val targets = userStates.distinctBy { it.state.userId }
        scope.launch(Dispatchers.IO) {
            // Offline: no descargar nada; las miniaturas cacheadas ya están en disco.
            if (!com.example.util.NetworkMonitor.isOnline.value) {
                Log.d(TAG, "Offline: skipping preloadAllStories")
                return@launch
            }
            val repository = MediaRepository(appCtx, MediaStorageManager(appCtx))
            for (item in targets) {
                val state = item.state
                val remoteUrl = com.example.data.repository.CdnManager.resolveMediaUrlSync(state.mediaUrl) ?: continue
                // HLS manifests (.m3u8) cannot be cached as plain local files — VCDN
                // posters are resolved by the carousel at runtime; preloading the HLS
                // manifest text as a .bin would corrupt the thumbnail cache..
                if (remoteUrl.endsWith(".m3u8", ignoreCase = true) || remoteUrl.contains(".m3u8?") || remoteUrl.startsWith("vcdn://")) continue

                if (!remoteUrl.startsWith("http://") && !remoteUrl.startsWith("https://")) continue
                val mediaId = "story_${state.id}_${kotlin.math.abs(remoteUrl.hashCode())}"
                if (!preloadedIds.add(mediaId)) continue
                val type = if (state.mediaType == "video" || remoteUrl.endsWith(".mp4", ignoreCase = true)) "video" else "image"
                try {
                    repository.syncManager.syncMedia(mediaId, remoteUrl, type, state.userId)
                } catch (e: Exception) {
                    Log.e(TAG, "preloadAllStories failed for $mediaId", e)
                    preloadedIds.remove(mediaId)
                }
            }
        }
    }
}
