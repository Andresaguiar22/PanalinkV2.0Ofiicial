package com.example.data.video

import android.content.Context
import android.util.Log
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import androidx.media3.common.util.UnstableApi

@UnstableApi
object VideoCacheManager {
    private const val CACHE_DIR_NAME = "media3_video_cache"
    // 2 GB durable media cache: el LRU desaloja los vídeos más viejos, así que el
    // tope es lo que decide cuántos caben (~100 reels de hasta 20 MB, o ~500 cortos).
    // Los vídeos ya vistos siguen reproduciéndose sin red mientras no sean desalojados.
    private const val MAX_CACHE_SIZE_BYTES = 2L * 1024L * 1024L * 1024L // 2 GB

    @Volatile
    private var cache: SimpleCache? = null

    @Volatile
    private var cacheContextPath: String? = null

    fun removeVideoCache(url: String) {
        try {
            val uri = android.net.Uri.parse(url)
            // Match the path-only key used by CacheDataSourceFactory. Signed
            // query parameters rotate and must not identify a new video.
            val key = uri.path?.takeIf { it.isNotBlank() } ?: uri.toString().substringBefore('?')
            cache?.removeResource(key)
        } catch (e: Exception) {
            Log.e("VideoCacheManager", "Error clearing cache for url: $url", e)
        }
    }

    @Synchronized
    fun getCache(context: Context): SimpleCache? {
        val appContext = context.applicationContext
        val durableCacheDir = File(appContext.filesDir, CACHE_DIR_NAME)
        val durablePath = durableCacheDir.absolutePath

        if (cache != null && cacheContextPath == durablePath) {
            return cache
        }

        return try {
            // filesDir is intentional: Android may purge cacheDir when storage is low.
            // Already downloaded media must survive process death and normal cache cleanup.
            durableCacheDir.mkdirs()
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES)
            val databaseProvider = StandaloneDatabaseProvider(appContext)
            cache = SimpleCache(durableCacheDir, evictor, databaseProvider)
            cacheContextPath = durablePath
            cache
        } catch (e: Throwable) {
            Log.e("VideoCacheManager", "Error creating durable SimpleCache", e)
            cache = null
            cacheContextPath = null
            null
        }
    }
}
