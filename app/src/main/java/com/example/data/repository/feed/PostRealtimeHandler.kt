package com.example.data.repository.feed

import android.util.Log
import com.example.data.database.PostDao
import com.example.data.database.PostEntity
import com.example.data.model.PostDto
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.LinkedHashMap

class PostRealtimeHandler private constructor(
    private val postDao: PostDao,
    private val scope: CoroutineScope
) {
    private val TAG = "PostRealtimeHandler"

    companion object {
        @Volatile
        private var INSTANCE: PostRealtimeHandler? = null

        fun getInstance(postDao: PostDao, scope: CoroutineScope): PostRealtimeHandler {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PostRealtimeHandler(postDao, scope).also { INSTANCE = it }
            }
        }

        fun resetForTest() {
            INSTANCE = null
            clearProcessedEvents()
        }

        private val processedRealtimeEventIds = Collections.synchronizedSet(
            object : LinkedHashMap<String, Boolean>(200, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                    return size > 500
                }
            }.let { Collections.newSetFromMap(it) }
        )

        fun clearProcessedEvents() {
            processedRealtimeEventIds.clear()
        }
    }

    init {
        scope.launch {
            launch {
                SupabaseClient.realtimePostDeletions.collect { postId ->
                    try {
                        postDao.deletePostById(postId)
                        Log.d(TAG, "Realtime: Deleted post $postId from local DB")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting post $postId from Room upon realtime signal", e)
                    }
                }
            }

            launch {
                SupabaseClient.realtimePosts.collect { update ->
                    Log.d(TAG, "Collected post update: $update")
                    try {
                        val eventKey = "${update.recordId}_${update.eventType}_${update.record.optString("updated_at", update.record.optString("created_at", ""))}"
                        if (update.recordId.isNotEmpty() && !processedRealtimeEventIds.add(eventKey)) {
                            Log.d(TAG, "Realtime post event $eventKey already processed. Skipping duplicate/replay.")
                            return@collect
                        }

                        val record = update.record
                        val postId = record.optString("id", update.recordId)
                        if (postId.isNotEmpty()) {
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
                            } catch (e: Exception) {
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
                            val preserveComment = local != null && (postDao.hasPendingCommentAction(postId) || local.commentsCount > remoteEntity.commentsCount)

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
                            Log.d(TAG, "PostRealtimeHandler: Processed ${update.eventType} for post $postId")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "PostRealtimeHandler: Error processing incoming post update", e)
                    }
                }
            }
        }
    }
}
