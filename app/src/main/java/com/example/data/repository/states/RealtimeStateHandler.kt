package com.example.data.repository.states

import android.util.Log
import com.example.data.database.PanalinkDatabase
import com.example.data.model.Profile
import com.example.data.repository.PublicProfileFetchResult
import com.example.data.repository.PublicProfileRepository
import com.example.data.repository.PublicProfileResolver
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles Realtime updates for stories & reels: live status rows (INSERT/UPDATE)
 * and social interactions (likes/favorites/comments/shares), merging them into Room
 * while preserving per-viewer flags (likedByMe/favoritedByMe/viewedByMe) and deduping
 * duplicate broadcast events（
 */
class RealtimeStateHandler {

    companion object {
        private val processedRealtimeEventIds = java.util.Collections.synchronizedSet(
            object : java.util.LinkedHashMap<String, Boolean>(200, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                    return size > 500
                }
            }.let { java.util.Collections.newSetFromMap(it) }
        )
    }

    private val TAG = "RealtimeStateHandler"

    private val db by lazy { PanalinkDatabase.getDatabase(com.example.PanaApplication.instance) }
    private val statesDao by lazy { db.statesDao() }

    private suspend fun resolveProfileForUser(userId: String): Profile {
        if (userId == SupabaseClient.currentUser?.id && SupabaseClient.currentProfile != null) {
            return SupabaseClient.currentProfile!!
        }
        val publicResult = PublicProfileRepository.getInstance().getPublicProfile(userId)
        return if (publicResult is PublicProfileFetchResult.Success) {
            PublicProfileResolver.toProfile(publicResult.data)
        } else {
            Profile(id = userId, displayName = "", avatarUrl = null)
        }
    }

    suspend fun handleRealtimeStatus(userState: com.example.data.model.UserState) = withContext(Dispatchers.IO) {
        val profile = resolveProfileForUser(userState.userId)
        val item = com.example.data.model.UserStateWithUser(userState, profile)
        val entity = com.example.data.database.StateEntity.fromUserStateWithUser(item)
        val existing = statesDao.getStateById(userState.id)
        val stabilized = StateUrlResolver.stabilizeEntityForRoom(entity, existing)
        val finalEntity = if (existing != null) {
            stabilized.copy(caption = entity.caption ?: existing.caption)
        } else stabilized
        statesDao.insertState(finalEntity)
    }

    private suspend fun fetchAndSaveSingleReel(reelId: String, isReel: Boolean): com.example.data.database.StateEntity? = withContext(Dispatchers.IO) {
        val service = SupabaseClient.apiService ?: return@withContext null
        val apiKey = SupabaseClient.supabaseAnonKey
        val response = runStatesCall(TAG) { b ->
            if (isReel) service.getUserReels(apiKey, b, idFilter = "eq.$reelId")
            else service.getUserStories(apiKey, b, idFilter = "eq.$reelId")
        }
        val rawState = response?.body()?.firstOrNull() ?: return@withContext null
        val state = rawState.copy(type = if (isReel) "reel" else "story")
        val profile = resolveProfileForUser(state.userId)
        val item = com.example.data.model.UserStateWithUser(state, profile)
        val entity = com.example.data.database.StateEntity.fromUserStateWithUser(item)
        val existing = statesDao.getStateById(reelId)
        val stabilized = StateUrlResolver.stabilizeEntityForRoom(entity, existing)
        statesDao.insertState(stabilized)
        stabilized
    }

    private suspend fun ensureReelInRoom(reelId: String, isReel: Boolean): com.example.data.database.StateEntity? {
        val existing = statesDao.getStateById(reelId)
        if (existing != null) return existing
        Log.i(TAG, "Reel/Story $reelId not found in Room. Fetching for reconciliation...")
        return fetchAndSaveSingleReel(reelId, isReel)
    }

    suspend fun handleRealtimeSocialInteraction(
        update: com.example.data.supabase.SupabaseClient.SocialInteractionUpdate,
        interactionType: String
    ) = withContext(Dispatchers.IO) {
        if (update.recordId.isNotEmpty()) {
            if (!processedRealtimeEventIds.add(update.recordId)) {
                Log.d(TAG, "Realtime event ${update.recordId} already processed. Skipping duplicate.")
                return@withContext
            }
        }

        val entity = ensureReelInRoom(update.statusId, update.isReel) ?: return@withContext
        val currentUid = SupabaseClient.currentUser?.id ?: ""
        val record = update.record
        val actorUserId = record.optString("user_id", record.optString("author_id", ""))
        val isCurrentUser = actorUserId.isNotEmpty() && actorUserId == currentUid
        val isDelete = update.eventType == "DELETE"

        when (interactionType) {
            "LIKE" -> {
                val newLiked = if (isCurrentUser) !isDelete else entity.likedByMe
                val newLikesCount = if (isDelete) {
                    if (isCurrentUser) {
                        if (entity.likedByMe) (entity.likesCount - 1).coerceAtLeast(0) else entity.likesCount
                    } else {
                        (entity.likesCount - 1).coerceAtLeast(0)
                    }
                } else {
                    if (isCurrentUser) {
                        if (!entity.likedByMe) entity.likesCount + 1 else entity.likesCount
                    } else {
                        entity.likesCount + 1
                    }
                }
                statesDao.insertState(entity.copy(likesCount = newLikesCount, likedByMe = newLiked))
            }
            "FAVORITE" -> {
                val newFav = if (isCurrentUser) !isDelete else entity.favoritedByMe
                val newFavCount = if (isDelete) {
                    if (isCurrentUser) {
                        if (entity.favoritedByMe) (entity.favoritesCount - 1).coerceAtLeast(0) else entity.favoritesCount
                    } else {
                        (entity.favoritesCount - 1).coerceAtLeast(0)
                    }
                } else {
                    if (isCurrentUser) {
                        if (!entity.favoritedByMe) entity.favoritesCount + 1 else entity.favoritesCount
                    } else {
                        entity.favoritesCount + 1
                    }
                }
                statesDao.insertState(entity.copy(favoritesCount = newFavCount, favoritedByMe = newFav))
            }
            "COMMENT" -> {
                val commentDao = db.commentDao()
                val commentId = update.recordId.ifEmpty { record.optString("id", java.util.UUID.randomUUID().toString()) }
                if (isDelete) {
                    commentDao.deleteById(commentId)
                    val actualCount = commentDao.getCommentCount(update.statusId, update.isReel)
                    statesDao.insertState(entity.copy(commentsCount = actualCount))
                } else {
                    val bodyText = record.optString("body", record.optString("content", record.optString("text", "")))
                    val createdAt = record.optString("created_at", SupabaseClient.getNowIsoString())
                    val rawParentId = record.optString("parent_comment_id", record.optString("parentId", ""))
                    val parentId = rawParentId.takeIf { it.isNotBlank() && it != "null" }
                    if (bodyText.isNotBlank()) {
                        val authorProfile = resolveProfileForUser(actorUserId)
                        val commentEntity = com.example.data.database.CommentEntity(
                            id = commentId,
                            targetId = update.statusId,
                            isReel = update.isReel,
                            authorId = actorUserId,
                            authorName = authorProfile.displayName.ifBlank { "Usuario" },
                            authorAvatarUrl = authorProfile.avatarUrl,
                            content = bodyText,
                            createdAt = createdAt,
                            parentCommentId = parentId,
                            syncStatus = "synced"
                        )
                        commentDao.upsert(commentEntity)
                    }
                    val actualCount = commentDao.getCommentCount(update.statusId, update.isReel)
                    statesDao.insertState(entity.copy(commentsCount = actualCount))
                }
            }
            "SHARE" -> {
                val newSharesCount = if (isDelete) {
                    (entity.sharesCount - 1).coerceAtLeast(0)
                } else {
                    entity.sharesCount + 1
                }
                statesDao.insertState(entity.copy(sharesCount = newSharesCount))
            }
        }
    }

}
