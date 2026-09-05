package com.example.data.repository.feed

import android.util.Log
import com.example.data.database.PostDao
import com.example.data.database.PostEntity
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.LinkedHashMap

class PostRealtimeHandler private constructor(
    private val postDao: PostDao,
    private val scope: CoroutineScope
) {
    private val TAG = "PostRealtimeHandler"

    companion object {
        @Volatile
        private var INSTANCE: PostRealtimeHandler? = null

        /**
         * Production entry point. The handler owns an application-lifetime scope so it
         * cannot accidentally inherit the lifecycle of a short-lived repository instance.
         * The second overload is retained only for source compatibility with existing
         * callers and deliberately ignores the repository scope.
         */
        fun getInstance(postDao: PostDao): PostRealtimeHandler {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PostRealtimeHandler(
                    postDao = postDao,
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                ).also { INSTANCE = it }
            }
        }

        @Deprecated("Use getInstance(postDao). Repository scope must not own the realtime handler.")
        fun getInstance(postDao: PostDao, @Suppress("UNUSED_PARAMETER") repositoryScope: CoroutineScope): PostRealtimeHandler =
            getInstance(postDao)

        internal fun getInstanceForTest(postDao: PostDao, testScope: CoroutineScope): PostRealtimeHandler {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PostRealtimeHandler(postDao, testScope).also { INSTANCE = it }
            }
        }

        fun resetForTest() {
            INSTANCE?.close()
            INSTANCE = null
            clearProcessedEvents()
        }

        private val processedRealtimeEventIds = object : LinkedHashMap<String, Unit>(500, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean = size > 500
        }

        private fun markIfAlreadyProcessed(eventKey: String): Boolean {
            synchronized(processedRealtimeEventIds) {
                if (processedRealtimeEventIds.containsKey(eventKey)) {
                    processedRealtimeEventIds[eventKey] // touch entry for true LRU semantics
                    return true
                }
                processedRealtimeEventIds[eventKey] = Unit
                return false
            }
        }

        fun clearProcessedEvents() {
            synchronized(processedRealtimeEventIds) {
                processedRealtimeEventIds.clear()
            }
        }
    }

    private fun close() {
        scope.cancel()
    }

    private fun buildEventKey(
        eventType: String,
        recordId: String,
        record: org.json.JSONObject
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(record.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "$recordId|$eventType|$digest"
    }

    init {
        scope.launch {
            launch {
                SupabaseClient.realtimePostDeletions.collect { postId ->
                    try {
                        if (postId.isNotBlank()) {
                            postDao.deletePostById(postId)
                            Log.d(TAG, "Realtime: Deleted post $postId from local DB")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting post $postId from Room upon realtime signal", e)
                    }
                }
            }

            launch {
                SupabaseClient.realtimePosts.collect { update ->
                    try {
                        if (update.eventType != "INSERT" && update.eventType != "UPDATE") {
                            Log.w(TAG, "Ignoring unsupported post realtime event: ${update.eventType}")
                            return@collect
                        }

                        val record = update.record
                        val postId = record.optString("id", update.recordId)
                        if (postId.isBlank()) {
                            return@collect
                        }

                        val eventKey = buildEventKey(update.eventType, postId, record)
                        if (markIfAlreadyProcessed(eventKey)) {
                            Log.d(TAG, "Realtime post event already processed. Skipping duplicate/replay: $eventKey")
                            return@collect
                        }

                        val authorId = record.optString("user_id", record.optString("author_id", ""))
                        val type = record.optString("type", "TEXT")
                        val content = record.optString("content", "")
                        val audioUrl = record.optString("audio_url", "").takeIf { it.isNotEmpty() }
                        val privacy = record.optString("privacy", "PUBLIC")
                        val likesCount = record.optInt("likes_count", 0)
                        val commentsCount = record.optInt("comments_count", 0)
                        val sharesCount = record.optInt("shares_count", record.optInt("share_count", 0))
                        val createdAt = record.optString("created_at", null)
                        val updatedAt = record.optString("updated_at", null)

                        val mediaUrlsJson = try {
                            record.getJSONArray("media_urls").let { arr ->
                                org.json.JSONArray().apply {
                                    for (i in 0 until arr.length()) put(arr.get(i))
                                }.toString()
                            }
                        } catch (_: Exception) {
                            "[]"
                        }

                        val customMediaIdsJson = record.optJSONArray("media_ids")?.let { arr ->
                            org.json.JSONArray().apply {
                                for (i in 0 until arr.length()) put(arr.get(i))
                            }.toString()
                        }

                        val previewMetadataJson = record.optJSONObject("preview_metadata")?.toString()

                        val remoteEntity = PostEntity(
                            id = postId,
                            authorId = authorId,
                            type = type,
                            content = content,
                            mediaUrlsJson = mediaUrlsJson,
                            audioUrl = audioUrl,
                            privacy = privacy,
                            likesCount = likesCount,
                            commentsCount = commentsCount,
                            shareCount = sharesCount,
                            currentUserLiked = false,
                            createdAt = createdAt,
                            updatedAt = updatedAt,
                            previewMetadataJson = previewMetadataJson,
                            customMediaIdsJson = customMediaIdsJson
                        )

                        val local = postDao.getPostById(postId)
                        val preserveLike = local != null && (local.currentUserLiked || postDao.hasPendingLikeAction(postId))
                        val preserveComment = local != null && postDao.hasPendingCommentAction(postId)

                        val mergedEntity = if (local != null) {
                            remoteEntity.copy(
                                currentUserLiked = if (preserveLike) local.currentUserLiked else remoteEntity.currentUserLiked,
                                likesCount = if (preserveLike) kotlin.math.max(local.likesCount, remoteEntity.likesCount) else remoteEntity.likesCount,
                                commentsCount = if (preserveComment) kotlin.math.max(local.commentsCount, remoteEntity.commentsCount) else remoteEntity.commentsCount
                            )
                        } else {
                            remoteEntity
                        }

                        postDao.upsert(mergedEntity)
                        Log.d(TAG, "Processed ${update.eventType} for post $postId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing incoming post update", e)
                    }
                }
            }
        }
    }
}
