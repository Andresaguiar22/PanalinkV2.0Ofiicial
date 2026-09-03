package com.example.core.media

import java.util.Collections

/**
 * In-memory LRU cache for video rotation metadata extracted via MediaMetadataRetriever.
 * Prevents repeated network metadata fetches when the same video re-enters composition
 * during fast scrolling.
 */
object VideoMetadataCache {
    private const val MAX_ENTRIES = 128

    private val rotationCache: MutableMap<String, Float> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Float>(MAX_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?): Boolean =
                size > MAX_ENTRIES
        }
    )

    fun getRotation(url: String): Float? = rotationCache[url]

    fun putRotation(url: String, rotationDegrees: Float) {
        rotationCache[url] = rotationDegrees
    }
}
