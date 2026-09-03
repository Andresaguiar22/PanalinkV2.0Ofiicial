package com.example.media.feed

import android.content.Context
import android.util.Log
import com.example.data.model.PostDto
import com.example.media.repository.MediaRepository
import com.example.media.storage.MediaStorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object FeedMediaPreloader {
    private const val TAG = "FeedMediaPreloader"
    private const val PRELOAD_COUNT = 1
    private const val COALESCE_DELAY_MS = 120L

    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val completed: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val pendingIndex = AtomicInteger(-1)
    private val workerScheduled = AtomicBoolean(false)
    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Coalesces viewport changes into one IO worker. A fast fling keeps only the
     * newest requested position instead of starting work for every composed item.
     */
    fun preloadNextPostsMedia(
        context: Context,
        posts: List<PostDto>,
        currentIndex: Int,
        scope: CoroutineScope
    ) {
        if (posts.isEmpty() || currentIndex < 0) return

        // currentIndex is the currently visible post; preload only the next post.
        pendingIndex.set(currentIndex)
        if (!workerScheduled.compareAndSet(false, true)) return

        val appContext = context.applicationContext
        preloadScope.launch {
            try {
                while (true) {
                    delay(COALESCE_DELAY_MS)
                    drain(appContext, posts)
                    if (pendingIndex.get() < 0) break
                }
            } catch (e: Exception) {
                Log.w(TAG, "Preload scheduler failed", e)
            } finally {
                workerScheduled.set(false)
                // If a new viewport arrived during shutdown, schedule exactly one
                // replacement worker. Intermediate fling positions remain coalesced.
                if (pendingIndex.get() >= 0 && workerScheduled.compareAndSet(false, true)) {
                    preloadScope.launch {
                        try {
                            while (true) {
                                delay(COALESCE_DELAY_MS)
                                drain(appContext, posts)
                                if (pendingIndex.get() < 0) break
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Coalesced preload failed", e)
                        } finally {
                            workerScheduled.set(false)
                        }
                    }
                }
            }
        }
    }

    private suspend fun drain(context: Context, posts: List<PostDto>) {
        val requestedIndex = pendingIndex.getAndSet(-1)
        if (requestedIndex < 0) return

        val startIndex = (requestedIndex + 1).coerceAtMost(posts.lastIndex)
        val endIndex = (requestedIndex + PRELOAD_COUNT).coerceAtMost(posts.lastIndex)
        if (startIndex > endIndex) return

        val repository = MediaRepository(context, MediaStorageManager(context))
        for (i in startIndex..endIndex) {
            val post = posts.getOrNull(i) ?: continue
            for (url in post.mediaUrls.orEmpty()) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) continue

                val mediaId = "media_${kotlin.math.abs(url.hashCode())}"
                if (completed.contains(mediaId) || !inFlight.add(mediaId)) continue

                try {
                    val type = if (
                        url.endsWith(".mp4", ignoreCase = true) ||
                        url.contains("video", ignoreCase = true)
                    ) "video" else "image"
                    repository.syncManager.syncMedia(mediaId, url, type, post.userId)
                    completed.add(mediaId)
                } catch (e: Exception) {
                    Log.w(TAG, "Preload failed for $url", e)
                } finally {
                    inFlight.remove(mediaId)
                }
            }
        }
    }
}