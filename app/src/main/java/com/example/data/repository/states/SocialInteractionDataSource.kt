package com.example.data.repository.states

import android.util.Log
import com.example.data.database.PanalinkDatabase
import com.example.data.model.Comment
import com.example.data.model.StatusViewer
import com.example.data.model.ToggleFavoriteResponseDto
import com.example.data.model.ToggleLikeResponseDto
import com.example.data.repository.CdnManager
import com.example.data.repository.PublicProfileFetchResult
import com.example.data.repository.PublicProfileRepository
import com.example.data.repository.PublicProfileResolver
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Local-first social interactions for stories & reels: likes, favorites, shares,
 * comments and views. Remote writes are queued via [SocialSyncWorker] (Room
 * pending actions); reads are cached in Room first, refreshed from Supabase when needed.
 */
class SocialInteractionDataSource {

    private val TAG = "SocialInteractionDataSource"

    private val db by lazy { PanalinkDatabase.getDatabase(com.example.PanaApplication.instance) }
    private val statesDao by lazy { db.statesDao() }

    suspend fun toggleLike(stateId: String, currentLikeState: Boolean, isReel: Boolean): Result<com.example.data.model.ToggleLikeResponseDto> = withContext(Dispatchers.IO) {
        val currentUid = SupabaseClient.currentUser?.id ?: return@withContext Result.failure(Exception("Not authenticated"))
        
        // 1. Update local Room state immediately
        val existing = statesDao.getStateById(stateId)
        val actualCurrentLike = existing?.likedByMe ?: currentLikeState
        val actualCount = existing?.likesCount ?: 0
        
        val newLiked = !actualCurrentLike
        val newCount = if (actualCurrentLike) (actualCount - 1).coerceAtLeast(0) else actualCount + 1
        
        if (existing != null) {
            statesDao.insertState(existing.copy(likedByMe = newLiked, likesCount = newCount))
        }

        // 2. Coalesce/queue action locally
        val pendingDao = db.pendingSocialActionDao()
        pendingDao.deleteLikeActionsForTarget(currentUid, stateId)
        val actionType = if (actualCurrentLike) "UNLIKE" else "LIKE"
        val action = com.example.data.database.PendingSocialActionEntity(
            localActionId = java.util.UUID.randomUUID().toString(),
            userId = currentUid,
            targetId = stateId,
            actionType = actionType,
            payload = null,
            isReel = isReel
        )
        pendingDao.insertAction(action)

        // 3. Enqueue Background Sync
        com.example.worker.SocialSyncWorker.enqueue(com.example.PanaApplication.instance)

        Result.success(com.example.data.model.ToggleLikeResponseDto(liked = newLiked, likesCount = newCount))
    }

    suspend fun toggleFavorite(stateId: String, currentFavState: Boolean, isReel: Boolean): Result<com.example.data.model.ToggleFavoriteResponseDto> = withContext(Dispatchers.IO) {
        val currentUid = SupabaseClient.currentUser?.id ?: return@withContext Result.failure(Exception("Not authenticated"))
        
        // 1. Update local Room state immediately
        val existing = statesDao.getStateById(stateId)
        val actualCurrentFav = existing?.favoritedByMe ?: currentFavState
        val actualCount = existing?.favoritesCount ?: 0
        
        val newFav = !actualCurrentFav
        val newCount = if (actualCurrentFav) (actualCount - 1).coerceAtLeast(0) else actualCount + 1
        
        if (existing != null) {
            statesDao.insertState(existing.copy(favoritedByMe = newFav, favoritesCount = newCount))
        }

        // 2. Coalesce/queue action locally
        val pendingDao = db.pendingSocialActionDao()
        pendingDao.deleteFavoriteActionsForTarget(currentUid, stateId)
        val actionType = if (actualCurrentFav) "UNFAVORITE" else "FAVORITE"
        val action = com.example.data.database.PendingSocialActionEntity(
            localActionId = java.util.UUID.randomUUID().toString(),
            userId = currentUid,
            targetId = stateId,
            actionType = actionType,
            payload = null,
            isReel = isReel
        )
        pendingDao.insertAction(action)

        // 3. Enqueue Background Sync
        com.example.worker.SocialSyncWorker.enqueue(com.example.PanaApplication.instance)

        Result.success(com.example.data.model.ToggleFavoriteResponseDto(favorited = newFav, favoritesCount = newCount))
    }

    suspend fun incrementShare(stateId: String, isReel: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val currentUid = SupabaseClient.currentUser?.id ?: return@withContext Result.failure(Exception("Not authenticated"))
        
        // 1. Update local Room state immediately
        val existing = statesDao.getStateById(stateId)
        if (existing != null) {
            val newSharesCount = existing.sharesCount + 1
            statesDao.insertState(existing.copy(sharesCount = newSharesCount))
        }

        // 2. Queue the action locally
        val pendingDao = db.pendingSocialActionDao()
        val action = com.example.data.database.PendingSocialActionEntity(
            localActionId = java.util.UUID.randomUUID().toString(),
            userId = currentUid,
            targetId = stateId,
            actionType = "SHARE",
            payload = null,
            isReel = isReel
        )
        pendingDao.insertAction(action)

        // 3. Enqueue Background Sync
        com.example.worker.SocialSyncWorker.enqueue(com.example.PanaApplication.instance)

        Result.success(Unit)
    }

    fun getCommentsFlow(stateId: String, isReel: Boolean): Flow<List<Comment>> {
        return db.commentDao().getCommentsFlow(stateId, isReel).map { entities ->
            entities.map { it.toStateComment() }
        }
    }

    suspend fun addComment(stateId: String, commentText: String, isReel: Boolean, parentId: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        if (commentText.isBlank()) return@withContext Result.failure(Exception("Comment cannot be empty"))
        val currentUid = SupabaseClient.currentUser?.id ?: return@withContext Result.failure(Exception("Not authenticated"))
        
        val commentDao = db.commentDao()
        val pendingDao = db.pendingSocialActionDao()

        val tempCommentId = java.util.UUID.randomUUID().toString()
        val timestamp = com.example.data.supabase.SupabaseClient.getNowIsoString()

        // 1. Increment local comments count
        val existing = statesDao.getStateById(stateId)
        if (existing != null) {
            statesDao.insertState(existing.copy(commentsCount = existing.commentsCount + 1))
        }

        // Get my profile info if available
        val myProfileEntity = db.profileDao().getProfile(currentUid)
        val myProfile = myProfileEntity?.toProfile()

        // 2. Insert temporary comment locally
        val tempCommentEntity = com.example.data.database.CommentEntity(
            id = tempCommentId,
            targetId = stateId,
            authorId = currentUid,
            authorName = myProfile?.displayName ?: "",
            authorAvatarUrl = myProfile?.avatarUrl,
            content = commentText,
            createdAt = timestamp,
            isReel = isReel,
            parentCommentId = parentId,
            syncStatus = "pending_add"
        )
        commentDao.upsert(tempCommentEntity)

        // 3. Queue action locally
        val payloadJson = org.json.JSONObject().apply {
            put("text", commentText)
            put("parentId", parentId ?: org.json.JSONObject.NULL)
            put("localCommentId", tempCommentId)
        }.toString()

        val action = com.example.data.database.PendingSocialActionEntity(
            localActionId = tempCommentId,
            userId = currentUid,
            targetId = stateId,
            actionType = "COMMENT",
            payload = payloadJson,
            isReel = isReel
        )
        pendingDao.insertAction(action)

        // 4. Enqueue Background Sync
        com.example.worker.SocialSyncWorker.enqueue(com.example.PanaApplication.instance)

        Result.success(Unit)
    }

    suspend fun getStateComments(stateId: String, isReel: Boolean): Result<List<Comment>> = withContext(Dispatchers.IO) {
        val commentDao = db.commentDao()

        // 1. Return immediately cached comments
        val cachedEntities = commentDao.getComments(stateId, isReel)
        val cachedComments = cachedEntities.map { it.toStateComment() }

        // 2. Fetch/Refresh from Supabase if configured
        if (SupabaseClient.isConfigured) {
            try {
                val service = SupabaseClient.apiService
                if (service != null) {
                    val token = SupabaseClient.currentToken
                    if (token != null) {
                        val apiKey = SupabaseClient.supabaseAnonKey
                        val bearer = "Bearer $token"

                        val tableName = if (isReel) "reel_comments" else "story_comments"
                        val idColumns = if (isReel) listOf("reel_id") else listOf("story_id", "status_id", "state_id")

                        var response: retrofit2.Response<List<com.example.data.model.StateCommentDto>>? = null
                        for (idCol in idColumns) {
                            response = service.getStateComments(
                                table = tableName,
                                apiKey = apiKey,
                                authorization = bearer,
                                filters = mapOf(idCol to "eq.$stateId")
                            )
                            if (response.isSuccessful) break
                        }

                        if (response != null && response.isSuccessful) {
                            val commentsDto = response.body() ?: emptyList()
                            val baseComments = commentsDto.map { it.toDomain() }

                            // Resolve author identity through public_profiles so the
                            // comment never falls back to generic/blank names.
                            val authorIds = baseComments.map { it.userId }.filter { it.isNotBlank() }.distinct()
                            val profilesMap = if (authorIds.isNotEmpty()) {
                                try {
                                    val profileResult = PublicProfileRepository.getInstance().getPublicProfiles(authorIds)
                                    if (profileResult is PublicProfileFetchResult.Success) {
                                        profileResult.data.mapNotNull { (id, value) ->
                                            if (value is PublicProfileFetchResult.Success) id to value.data else null
                                        }.toMap()
                                    } else emptyMap()
                                } catch (e: Exception) { emptyMap() }
                            } else emptyMap()

                            val comments = baseComments.map { comment ->
                                val pub = profilesMap[comment.userId]
                                if (pub == null) comment else comment.copy(
                                    authorName = PublicProfileResolver.resolveDisplayName(pub, comment.authorName, comment.userId),
                                    avatarUrl = CdnManager.resolveAvatarUrl(pub.avatarUrl) ?: comment.avatarUrl
                                )
                            }

                            // Save to Room
                            val entities = comments.map { com.example.data.database.CommentEntity.fromStateComment(it, isReel) }
                            commentDao.upsertAll(entities)
                            
                            return@withContext Result.success(comments)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync comments failed", e)
            }
        }

        Result.success(cachedComments)
    }

    suspend fun deleteComment(commentId: String, isReel: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val service = SupabaseClient.apiService ?: return@withContext Result.failure(Exception("Supabase not configured"))
            val token = SupabaseClient.currentToken ?: return@withContext Result.failure(Exception("Session expired"))
            val apiKey = SupabaseClient.supabaseAnonKey
            val bearer = "Bearer $token"

            val tableName = if (isReel) "reel_comments" else "story_comments"
            val updates = mapOf("deleted_at" to SupabaseClient.getNowIsoString())
            val response = service.patchComment(tableName, apiKey, bearer, "eq.$commentId", updates)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val errorStr = response.errorBody()?.string()
                Result.failure(Exception(SupabaseClient.parseSupabaseError(errorStr, "Error deleting comment")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStatusViews(stateId: String, isReel: Boolean): Result<List<StatusViewer>> = withContext(Dispatchers.IO) {
        try {
            val service = SupabaseClient.apiService ?: return@withContext Result.failure(Exception("Supabase not configured"))
            val token = SupabaseClient.currentToken ?: return@withContext Result.failure(Exception("Session expired"))
            val apiKey = SupabaseClient.supabaseAnonKey
            val bearer = "Bearer $token"

            val tableName = if (isReel) "reel_views" else "story_views"
            val idColumn = if (isReel) "reel_id" else "story_id"
            val response = service.getStatusViews(
                table = tableName,
                apiKey = apiKey,
                authorization = bearer,
                filters = mapOf(idColumn to "eq.$stateId")
            )
            if (response.isSuccessful) {
                val viewsDto = response.body() ?: emptyList()
                val viewerIds = viewsDto.map { it.viewerId }.filter { it.isNotBlank() }.distinct()
                val publicResult = PublicProfileRepository.getInstance().getPublicProfiles(viewerIds)
                val publicProfilesMap = if (publicResult is PublicProfileFetchResult.Success) {
                    publicResult.data.mapNotNull { (id, pubResult) ->
                        if (pubResult is PublicProfileFetchResult.Success) id to pubResult.data else null
                    }.toMap()
                } else emptyMap()

                val views = viewsDto.map { dto ->
                    val pub = publicProfilesMap[dto.viewerId]
                    StatusViewer(
                        viewerId = dto.viewerId,
                        name = PublicProfileResolver.resolveDisplayName(pub, dto.profiles?.displayName, dto.viewerId),
                        avatarUrl = CdnManager.resolveAvatarUrl(pub?.avatarUrl ?: dto.profiles?.avatarUrl),
                        viewedAt = dto.createdAt
                    )
                }
                Result.success(views)
            } else {
                val errorStr = response.errorBody()?.string()
                Result.failure(Exception(SupabaseClient.parseSupabaseError(errorStr, "Error fetching views")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerView(stateId: String, isReel: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val currentUid = SupabaseClient.currentUser?.id ?: return@withContext Result.failure(Exception("Not authenticated"))
        try {
            val service = SupabaseClient.apiService ?: return@withContext Result.failure(Exception("Supabase not configured"))
            val token = SupabaseClient.currentToken ?: return@withContext Result.failure(Exception("Session expired"))
            val apiKey = SupabaseClient.supabaseAnonKey
            val bearer = "Bearer $token"

            val tableName = if (isReel) "reel_views" else "story_views"
            val idColumn = if (isReel) "reel_id" else "story_id"
            val bodyMap = mapOf(
                idColumn to stateId,
                "viewer_id" to currentUid
            )
            Log.d("AUDIT_VIEW", "Proceeding to VIEW. POST /rest/v1/$tableName with body: $bodyMap")
            val response = service.viewStatus(tableName, apiKey, bearer, bodyMap)
            
            if (response.isSuccessful) {
                Log.d("AUDIT_VIEW", "VIEW Response: HTTP ${response.code()} ${response.message()}")
                Result.success(Unit)
            } else {
                val errorStr = response.errorBody()?.string()
                if (errorStr?.contains("23505") == true || errorStr?.contains("duplicate") == true) {
                    Log.d("AUDIT_VIEW", "VIEW Response: Already viewed (duplicate key 23505). HTTP ${response.code()}")
                    Result.success(Unit)
                } else {
                    Log.e("AUDIT_VIEW", "VIEW Error Body: $errorStr")
                    Result.failure(Exception(SupabaseClient.parseSupabaseError(errorStr, "Error registering view")))
                }
            }
        } catch (e: Exception) {
            Log.e("AUDIT_VIEW", "Exception in registerView", e)
            Result.failure(e)
        }
    }

}
