package com.example.data.repository.states

import android.util.Log
import com.example.data.database.PanalinkDatabase
import com.example.data.database.StateEntity
import com.example.data.model.Profile
import com.example.data.model.PublicProfile
import com.example.data.model.UserState
import com.example.data.model.UserStateWithUser
import com.example.data.repository.CdnManager
import com.example.data.repository.PublicProfileFetchResult
import com.example.data.repository.PublicProfileRepository
import com.example.data.repository.PublicProfileResolver
import com.example.data.supabase.SupabaseApiService
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Remote data source for stories & reels. Fetches active/favorite states and a user's
 * reels from Supabase, merges pending local actions (smart merge)and persists the
 * SSOT to Room. VCDN pointers (vcdn://{id}) are preserved verbatim: signed HLS
 * streamUrls expire and are minted only at playback time (see [StateUrlResolver]).
 */
class StatesRemoteDataSource {

    private val TAG = "StatesRemoteDataSource"

    private val db by lazy { PanalinkDatabase.getDatabase(com.example.PanaApplication.instance) }
    private val statesDao by lazy { db.statesDao() }

    suspend fun fetchActiveStates(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val service = SupabaseClient.apiService ?: return@withContext Result.failure(Exception("Supabase not configured"))
            
            // 1. Pre-flight session check
            SessionManager.validateAndRefreshSessionIfNeeded()
            
            val currentUid = SupabaseClient.currentUser?.id ?: return@withContext Result.failure(Exception("Not authenticated"))
            val apiKey = SupabaseClient.supabaseAnonKey

            // Load contacts to filter states
            val contactsResponse = runStatesCall(TAG) { b -> service.getContacts(apiKey, b, ownerFilter = "eq.$currentUid") }
            val contactIds = if (contactsResponse != null && contactsResponse.isSuccessful) {
                contactsResponse.body()?.map { it.contactUserId }?.toSet() ?: emptySet()
            } else {
                Log.w(TAG, "Failed loading contacts, cannot filter states securely")
                return@withContext Result.failure(Exception("Failed to load contacts for filtering"))
            }
            val allowedUserIds = contactIds + currentUid

            val nowStr = SupabaseClient.getNowIsoString()
            
            // Fetch reels from user_reels (Public)
            val reelsResponse = runStatesCall(TAG) { b -> service.getUserReels(apiKey, b) }
            
            // Fetch stories from user_stories (Private via RLS)
            val storiesResponse = runStatesCall(TAG) { b -> service.getUserStories(apiKey, b, expiresAtFilter = "gt.$nowStr") }

            val activeStates = mutableListOf<UserState>()
            
            if (reelsResponse != null && reelsResponse.isSuccessful) {
                val reels = reelsResponse.body()?.map { it.copy(type = "reel") } ?: emptyList()
                activeStates.addAll(reels)
            }
            
            if (storiesResponse != null && storiesResponse.isSuccessful) {
                val stories = storiesResponse.body()?.map { it.copy(type = "story") } ?: emptyList()
                activeStates.addAll(stories)
            }

            if (activeStates.isNotEmpty() || (reelsResponse?.isSuccessful == true && storiesResponse?.isSuccessful == true)) {
                // RLS now handles the contact filtering, so we don't need to filter here.
                val filteredStates = activeStates.filter { it.type != "story" || allowedUserIds.contains(it.userId) }


                // Fetch likes and favorites for current user (from both reels and stories)
                val likedStateIds = try {
                    val reelLikesRes = runStatesCall(TAG) { b -> 
                        service.getUserLikes(
                            table = "reel_likes",
                            apiKey = apiKey,
                            authorization = b,
                            filters = mapOf("user_id" to "eq.$currentUid")
                        )
                    }
                    val storyLikesRes = runStatesCall(TAG) { b -> 
                        service.getUserLikes(
                            table = "story_likes",
                            apiKey = apiKey,
                            authorization = b,
                            filters = mapOf("user_id" to "eq.$currentUid")
                        )
                    } ?: runStatesCall(TAG) { b ->
                        service.getUserLikes(
                            table = "story_likes",
                            apiKey = apiKey,
                            authorization = b,
                            filters = mapOf("author_id" to "eq.$currentUid")
                        )
                    } ?: runStatesCall(TAG) { b ->
                        service.getUserLikes(
                            table = "story_likes",
                            apiKey = apiKey,
                            authorization = b,
                            filters = emptyMap()
                        )
                    }
                    
                    val reelLikes = if (reelLikesRes != null && reelLikesRes.isSuccessful) {
                        reelLikesRes.body()?.filter { it.userId.isEmpty() || it.userId == currentUid }?.mapNotNull { it.stateId.takeIf { s -> s.isNotBlank() } }?.toSet() ?: emptySet()
                    } else emptySet()
                    
                    val storyLikes = if (storyLikesRes != null && storyLikesRes.isSuccessful) {
                        storyLikesRes.body()?.filter { it.userId.isEmpty() || it.userId == currentUid }?.mapNotNull { it.stateId.takeIf { s -> s.isNotBlank() } }?.toSet() ?: emptySet()
                    } else emptySet()
                    
                    reelLikes + storyLikes
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching user likes", e)
                    emptySet()
                }

                val favoritedStateIds = try {
                    val reelFavsRes = runStatesCall(TAG) { b -> 
                        service.getUserFavorites(
                            table = "reel_favorites",
                            apiKey = apiKey,
                            authorization = b,
                            filters = mapOf("user_id" to "eq.$currentUid")
                        )
                    }
                    val storyFavsRes = runStatesCall(TAG) { b -> 
                        service.getUserFavorites(
                            table = "story_favorites",
                            apiKey = apiKey,
                            authorization = b,
                            filters = mapOf("user_id" to "eq.$currentUid")
                        )
                    } ?: runStatesCall(TAG) { b ->
                        service.getUserFavorites(
                            table = "story_favorites",
                            apiKey = apiKey,
                            authorization = b,
                            filters = mapOf("author_id" to "eq.$currentUid")
                        )
                    } ?: runStatesCall(TAG) { b ->
                        service.getUserFavorites(
                            table = "story_favorites",
                            apiKey = apiKey,
                            authorization = b,
                            filters = emptyMap()
                        )
                    }
                    
                    val reelFavs = if (reelFavsRes != null && reelFavsRes.isSuccessful) {
                        reelFavsRes.body()?.filter { it.userId.isEmpty() || it.userId == currentUid }?.mapNotNull { it.stateId.takeIf { s -> s.isNotBlank() } }?.toSet() ?: emptySet()
                    } else emptySet()
                    
                    val storyFavs = if (storyFavsRes != null && storyFavsRes.isSuccessful) {
                        storyFavsRes.body()?.filter { it.userId.isEmpty() || it.userId == currentUid }?.mapNotNull { it.stateId.takeIf { s -> s.isNotBlank() } }?.toSet() ?: emptySet()
                    } else emptySet()
                    
                    reelFavs + storyFavs
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching user favorites", e)
                    emptySet()
                }

                // Query viewed states (story_views and reel_views)
                val viewedStateIds = try {
                    val reelViewsRes = runStatesCall(TAG) { b -> 
                        service.getUserLikes(
                            table = "reel_views",
                            apiKey = apiKey,
                            authorization = b,
                            filters = mapOf("viewer_id" to "eq.$currentUid")
                        )
                    }
                    val storyViewsRes = runStatesCall(TAG) { b -> 
                        service.getUserLikes(
                            table = "story_views",
                            apiKey = apiKey,
                            authorization = b,
                            filters = mapOf("viewer_id" to "eq.$currentUid")
                        )
                    } ?: runStatesCall(TAG) { b ->
                        service.getUserLikes(
                            table = "story_views",
                            apiKey = apiKey,
                            authorization = b,
                            filters = mapOf("user_id" to "eq.$currentUid")
                        )
                    } ?: runStatesCall(TAG) { b ->
                        service.getUserLikes(
                            table = "story_views",
                            apiKey = apiKey,
                            authorization = b,
                            filters = emptyMap()
                        )
                    }
                    
                    val reelViews = if (reelViewsRes != null && reelViewsRes.isSuccessful) {
                        reelViewsRes.body()?.mapNotNull { it.stateId.takeIf { s -> s.isNotBlank() } }?.toSet() ?: emptySet()
                    } else emptySet()
                    
                    val storyViews = if (storyViewsRes != null && storyViewsRes.isSuccessful) {
                        storyViewsRes.body()?.mapNotNull { it.stateId.takeIf { s -> s.isNotBlank() } }?.toSet() ?: emptySet()
                    } else emptySet()
                    
                    reelViews + storyViews
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching user views", e)
                    emptySet()
                }

                val authorUserIds = filteredStates.map { it.userId }.filter { it.isNotBlank() }.distinct()
                val publicProfileRepo = PublicProfileRepository.getInstance()
                val publicProfilesMap = when (val res = publicProfileRepo.getPublicProfiles(authorUserIds)) {
                    is PublicProfileFetchResult.Success -> {
                        res.data.mapNotNull { (id, pubResult) ->
                            if (pubResult is PublicProfileFetchResult.Success) id to pubResult.data else null
                        }.toMap()
                    }
                    else -> emptyMap()
                }

                val list = filteredStates.map { state ->
                    val profile = if (state.userId == currentUid && SupabaseClient.currentProfile != null) {
                        SupabaseClient.currentProfile!!
                    } else {
                        val pub = publicProfilesMap[state.userId]
                        if (pub != null) {
                            PublicProfileResolver.toProfile(pub)
                        } else {
                            Profile(id = state.userId, displayName = "", avatarUrl = null)
                        }
                    }
                    val likedByMe = likedStateIds.contains(state.id)
                    val favoritedByMe = favoritedStateIds.contains(state.id)
                    val viewedByMe = viewedStateIds.contains(state.id)
                    val updatedState = state.copy(
                        likedByMe = likedByMe, 
                        favoritedByMe = favoritedByMe,
                        viewedByMe = viewedByMe
                    )
                    UserStateWithUser(updatedState, profile)
                }.sortedByDescending { it.state.createdAt }

                // VCDN pointers (vcdn://{id}) must stay stable in Room: the signed HLS
                // streamUrl expires, so resolving here would persist a dead/mutated
                // URL and drop the thumbnail/poster pipeline. Resolution happens only in
                // runtime (players, Coil) via CdnManager.resolveMediaUrl*.
                val resolvedList = list

                // Save to local database for SSOT using Smart Merge:
                // Distinguishes local pending unsynced actions from remote confirmed state.
                try {
                    val pendingDao = db.pendingSocialActionDao()
                    val pendingActions = try { pendingDao.getPendingActions() } catch (e: Exception) { emptyList() }
                    val pendingLikesMap = pendingActions.filter { it.userId == currentUid && (it.actionType == "LIKE" || it.actionType == "UNLIKE") }
                        .associateBy { it.targetId }
                    val pendingFavsMap = pendingActions.filter { it.userId == currentUid && (it.actionType == "FAVORITE" || it.actionType == "UNFAVORITE") }
                        .associateBy { it.targetId }

                    val finalEntities = resolvedList.map { item ->
                        val newEntity = com.example.data.database.StateEntity.fromUserStateWithUser(item)
                        val pendingLike = pendingLikesMap[item.state.id]
                        val pendingFav = pendingFavsMap[item.state.id]

                        val finalLiked = when {
                            pendingLike != null -> pendingLike.actionType == "LIKE"
                            else -> newEntity.likedByMe
                        }

                        val finalLikesCount = when {
                            pendingLike != null -> {
                                if (pendingLike.actionType == "LIKE") {
                                    if (newEntity.likedByMe) newEntity.likesCount else newEntity.likesCount + 1
                                } else {
                                    if (newEntity.likedByMe) (newEntity.likesCount - 1).coerceAtLeast(0) else newEntity.likesCount
                                }
                            }
                            else -> newEntity.likesCount
                        }

                        val finalFavorited = when {
                            pendingFav != null -> pendingFav.actionType == "FAVORITE"
                            else -> newEntity.favoritedByMe
                        }

                        val finalFavsCount = when {
                            pendingFav != null -> {
                                if (pendingFav.actionType == "FAVORITE") {
                                    if (newEntity.favoritedByMe) newEntity.favoritesCount else newEntity.favoritesCount + 1
                                } else {
                                    if (newEntity.favoritedByMe) (newEntity.favoritesCount - 1).coerceAtLeast(0) else newEntity.favoritesCount
                                }
                            }
                            else -> newEntity.favoritesCount
                        }

                        val commentDao = db.commentDao()
                        val localCommentCount = commentDao.getCommentCount(item.state.id, item.state.isReel)
                        val finalCommentsCount = if (localCommentCount > 0) maxOf(newEntity.commentsCount, localCommentCount) else newEntity.commentsCount

                        val finalSharesCount = newEntity.sharesCount

                        // Preserve the local cached media path (+poster) that a remote refresh
                        // cannot know about. Without this, re-fetching would drop localVideoPath
                        // and the cached reel/story media in ROM would be orphaned, breaking
                        // the offline carousel/TikTok playback after a reconnect.
                        val existingEntity = statesDao.getStateById(newEntity.id)
                        val stabilized = StateUrlResolver.stabilizeEntityForRoom(newEntity, existingEntity)

                        stabilized.copy(
                            likedByMe = finalLiked,
                            likesCount = finalLikesCount,
                            favoritedByMe = finalFavorited,
                            favoritesCount = finalFavsCount,
                            commentsCount = finalCommentsCount,
                            sharesCount = finalSharesCount
                        )
                    }
                    statesDao.insertStates(finalEntities)
                    // Purge expired states only when we have a healthy remote snapshot. If the
                    // fetch ended empty (failed/partial), deleting would erase the offline cache
                    // permanently — exactly what we must never do.
                    if (finalEntities.isNotEmpty()) {
                        // Purge expired states synchronously within this IO dispatcher
                        val now = SupabaseClient.getNowIsoString()
                        try {
                            statesDao.deleteExpired(now)
                        } catch (ex: Exception) {
                            Log.e(TAG, "Error purging expired states", ex)
                        }
                        // Purge states the author deleted remotely: the remote snapshot is
                        // authoritative, so anything cached locally that is no longer in it
                        // must go (a deleted story/reel must disappear for everyone, not just
                        // its author). Snapshot is non-empty => healthy, safe to purge.
                        // favoritedByMe rows are excluded so a user's saved collection survives.
                        try {
                            val keepIds = finalEntities.mapNotNullTo(LinkedHashSet()) { it.id }
                            statesDao.deleteStatesNotIn(keepIds)
                        } catch (ex: Exception) {
                            Log.e(TAG, "Error purging deleted states", ex)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save states to local DB", e)
                }

                SessionManager.setOffline(false)
                Result.success(Unit)
            } else {
                val errorMsg = if (reelsResponse?.isSuccessful == false) reelsResponse.errorBody()?.string()
                              else storiesResponse?.errorBody()?.string()
                Result.failure(Exception("Error al cargar estados: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getActiveStates exception", e)
            SessionManager.setOffline(true)
            Result.failure(e)
        }
    }

    suspend fun fetchSavedStates(): Result<List<UserStateWithUser>> = withContext(Dispatchers.IO) {
        val currentUid = SupabaseClient.currentUser?.id ?: return@withContext Result.failure(Exception("Not authenticated"))
        try {
            val service = SupabaseClient.apiService ?: return@withContext Result.failure(Exception("Supabase not configured"))
            val token = SupabaseClient.currentToken ?: return@withContext Result.failure(Exception("Session expired"))
            val apiKey = SupabaseClient.supabaseAnonKey
            val bearer = "Bearer $token"

            val reelFavsRes = runStatesCall(TAG) { b ->
                service.getUserFavorites(
                    table = "reel_favorites",
                    apiKey = apiKey,
                    authorization = b,
                    filters = mapOf("user_id" to "eq.$currentUid")
                )
            }
            val storyFavsRes = runStatesCall(TAG) { b ->
                service.getUserFavorites(
                    table = "story_favorites",
                    apiKey = apiKey,
                    authorization = b,
                    filters = mapOf("user_id" to "eq.$currentUid")
                )
            }

            val reelFavIds = reelFavsRes?.body()?.mapNotNull { it.stateId.takeIf { s -> s.isNotBlank() } }?.toSet() ?: emptySet()
            val storyFavIds = storyFavsRes?.body()?.mapNotNull { it.stateId.takeIf { s -> s.isNotBlank() } }?.toSet() ?: emptySet()
            val allFavIds = reelFavIds + storyFavIds

            fetchActiveStates()
            val db = com.example.data.database.PanalinkDatabase.getDatabase(com.example.PanaApplication.instance)
            val statesDao = db.statesDao()
            val localStates = statesDao.getAllStatesSync()
            val allStates = localStates.map { it.toUserStateWithUser() }
            val saved = allStates.filter { it.state.id in allFavIds || it.state.favoritedByMe == true }
            Result.success(saved)
        } catch (e: Exception) {
            Log.e(TAG, "Error in getSavedStates", e)
            Result.failure(e)
        }
    }

    suspend fun fetchUserReels(userId: String): Result<List<UserStateWithUser>> = withContext(Dispatchers.IO) {
        try {
            val service = SupabaseClient.apiService ?: return@withContext Result.failure(Exception("Supabase not configured"))
            SessionManager.validateAndRefreshSessionIfNeeded()
            val apiKey = SupabaseClient.supabaseAnonKey

            // Filter by author_id
            val response = runStatesCall(TAG) { b -> service.getUserReels(apiKey, b, authorFilter = "eq.$userId") }

            if (response != null && response.isSuccessful) {
                val allStates = response.body()?.map { it.copy(type = "reel") } ?: emptyList()
                val reels = allStates.filter { it.isReel }

                // Map to UserStateWithUser and resolve URLs
                val profile = resolveProfileForUser(userId)
                val resolvedList = reels.map { state ->
                    val resolvedUrl = CdnManager.resolveMediaUrl(state.mediaUrl)
                    val resolvedState = state.copy(mediaUrl = if (resolvedUrl.isNotEmpty()) resolvedUrl else null)
                    UserStateWithUser(resolvedState, profile)
                }.sortedByDescending { it.state.createdAt }
                Result.success(resolvedList)
            } else {
                Result.failure(Exception("Error loading reels: ${response?.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    /**
     * Fetches only reels from Supabase ordered by [orderBy] (PostgREST order
     * expression, e.g. "likes_count.desc.nullslast,created_at.desc") and persists
     * them in Room. Used by the Reels timeline filter tabs so that tapping
     * "Explorar / Nuevos / Tendencias" queries the remote DB (E2E), not just a
     * local sort. Individual type/profile enrichment mirrors fetchActiveStates.
     */
    suspend fun fetchReelsTimeline(orderBy: String? = null): Result<List<UserStateWithUser>> = withContext(Dispatchers.IO) {
        try {
            val service = SupabaseClient.apiService ?: return@withContext Result.failure(Exception("Supabase not configured"))
            SessionManager.validateAndRefreshSessionIfNeeded()

            val currentUid = SupabaseClient.currentUser?.id ?: return@withContext Result.failure(Exception("Not authenticated"))
            val apiKey = SupabaseClient.supabaseAnonKey

            val response = runStatesCall(TAG) { b -> service.getUserReels(apiKey, b, orderBy = orderBy) }
            if (response == null || !response.isSuccessful) {
                return@withContext Result.failure(Exception("Error loading reels: ${response?.errorBody()?.string()}"))
            }

            val reels = response.body()?.map { it.copy(type = "reel") }?.filter { it.isReel } ?: emptyList()

            // Enrich profiles (current user fast path first, then public profiles).
            val authorIds = reels.map { it.userId }.filter { it.isNotBlank() }.distinct()
            val publicProfileRepo = PublicProfileRepository.getInstance()
            val publicProfilesMap = when (val res = publicProfileRepo.getPublicProfiles(authorIds)) {
                is PublicProfileFetchResult.Success -> {
                    res.data.mapNotNull { (id, pubResult) ->
                        if (pubResult is PublicProfileFetchResult.Success) id to pubResult.data else null
                    }.toMap()
                }
                else -> emptyMap()
            }

            val list = reels.map { state ->
                val profile = if (state.userId == currentUid && SupabaseClient.currentProfile != null) {
                    SupabaseClient.currentProfile!!
                } else {
                    val pub = publicProfilesMap[state.userId]
                    if (pub != null) PublicProfileResolver.toProfile(pub)
                    else Profile(id = state.userId, displayName = "", avatarUrl = null)
                }
                UserStateWithUser(state, profile)
            }

            // Persist to Room (smart-merge keeps pending local likes/favorites).
            try {
                val pendingDao = db.pendingSocialActionDao()
                val pendingActions = try { pendingDao.getPendingActions() } catch (e: Exception) { emptyList() }
                val pendingLikesMap = pendingActions.filter { it.userId == currentUid && (it.actionType == "LIKE" || it.actionType == "UNLIKE") }
                    .associateBy { it.targetId }
                val pendingFavsMap = pendingActions.filter { it.userId == currentUid && (it.actionType == "FAVORITE" || it.actionType == "UNFAVORITE") }
                    .associateBy { it.targetId }

                val finalEntities = list.map { item ->
                    val newEntity = com.example.data.database.StateEntity.fromUserStateWithUser(item)
                    val existingEntity = statesDao.getStateById(newEntity.id)
                    val stabilized = StateUrlResolver.stabilizeEntityForRoom(newEntity, existingEntity)
                    val pendingLike = pendingLikesMap[item.state.id]
                    val pendingFav = pendingFavsMap[item.state.id]
                    stabilized.copy(
                        likedByMe = when {
                            pendingLike != null -> pendingLike.actionType == "LIKE"
                            else -> newEntity.likedByMe
                        },
                        favoritedByMe = when {
                            pendingFav != null -> pendingFav.actionType == "FAVORITE"
                            else -> newEntity.favoritedByMe
                        }
                    )
                }
                if (finalEntities.isNotEmpty()) {
                    statesDao.insertStates(finalEntities)
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchReelsTimeline: failed to save to Room", e)
            }

            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "fetchReelsTimeline exception", e)
            Result.failure(e)
        }
    }

    /**
     * Search-as-you-type over reels, TikTok-style. [query] matches the caption
     * (case-insensitive, via PostgREST ilike) while [tag] (without '#') filters
     * the dedicated hashtags[] column. Both are sent as real remote queries so the
     * results reflect the database, not only what happened to be in Room.
     */
    suspend fun searchReels(query: String? = null, tag: String? = null, limit: Int = 60): Result<List<UserStateWithUser>> =
        withContext(Dispatchers.IO) {
            try {
                val service = SupabaseClient.apiService ?: return@withContext Result.failure(Exception("Supabase not configured"))
                SessionManager.validateAndRefreshSessionIfNeeded()
                SupabaseClient.currentUser?.id ?: return@withContext Result.failure(Exception("Not authenticated"))
                val apiKey = SupabaseClient.supabaseAnonKey

                // Free-text search: a plain `caption=ilike.*q*` filter (a single
                // condition, so there is no PostgREST `or` grammar to escape).
                // The value is passed raw because Retrofit URL-encodes it —
                // pre-encoding with Uri.encode double-encodes spaces and breaks
                // the match ("video juego" -> must not become "video%2520juego").
                val caption = query?.trim()?.takeIf { it.isNotBlank() }?.let { "ilike.*$it*" }

                // Hashtag tap: hashtags are stored WITHOUT the leading '#' (e.g.
                // ["video"]), so match the array with `cs.{tag}` and fall back to
                // the caption ('#tag'). PostgREST requires the parentheses around
                // the `or` argument; without them the request fails to parse and
                // the search always comes back empty.
                val tagValue = tag?.trim()?.removePrefix("#")?.takeIf { it.isNotBlank() }
                    ?.replace(Regex("[,\\(\\):{}]"), "")
                val or = tagValue?.takeIf { it.isNotBlank() }?.let {
                    "(hashtags.cs.{$it},caption.ilike.*#$it*)"
                }

                val response = runStatesCall(TAG) { b ->
                    service.getUserReels(apiKey, b, orderBy = "likes_count.desc.nullslast,created_at.desc", orFilter = or, captionFilter = caption, limit = limit)
                }
                if (response == null || !response.isSuccessful) {
                    return@withContext Result.failure(Exception("Error searching reels: ${response?.errorBody()?.string()}"))
                }
                val reels = response.body()?.map { it.copy(type = "reel") }?.filter { it.isReel } ?: emptyList()
                // Search hits are ephemeral results: enrich profiles for the grid but
                // do NOT persist them in Room so the local feed is never tainted.
                Result.success(enrichWithProfiles(reels, persistRoom = false))
            } catch (e: Exception) {
                Log.e(TAG, "searchReels exception", e)
                Result.failure(e)
            }
        }

    private suspend fun enrichWithProfiles(reels: List<UserState>, persistRoom: Boolean): List<UserStateWithUser> {
        val currentUid = SupabaseClient.currentUser?.id ?: return reels.map { UserStateWithUser(it, Profile(id = it.userId, displayName = "", avatarUrl = null)) }

        // Enrich profiles (current user fast path first, then public profiles).
        val authorIds = reels.map { it.userId }.filter { it.isNotBlank() }.distinct()
        val publicProfileRepo = PublicProfileRepository.getInstance()
        val publicProfilesMap = when (val res = publicProfileRepo.getPublicProfiles(authorIds)) {
            is PublicProfileFetchResult.Success -> {
                res.data.mapNotNull { (id, pubResult) ->
                    if (pubResult is PublicProfileFetchResult.Success) id to pubResult.data else null
                }.toMap()
            }
            else -> emptyMap()
        }

        val list = reels.map { state ->
            val profile = if (state.userId == currentUid && SupabaseClient.currentProfile != null) {
                SupabaseClient.currentProfile!!
            } else {
                val pub = publicProfilesMap[state.userId]
                if (pub != null) PublicProfileResolver.toProfile(pub)
                else Profile(id = state.userId, displayName = "", avatarUrl = null)
            }
            UserStateWithUser(state, profile)
        }

        if (persistRoom) {
            try {
                val pendingDao = db.pendingSocialActionDao()
                val pendingActions = try { pendingDao.getPendingActions() } catch (e: Exception) { emptyList() }
                val pendingLikesMap = pendingActions.filter { it.userId == currentUid && (it.actionType == "LIKE" || it.actionType == "UNLIKE") }
                    .associateBy { it.targetId }
                val pendingFavsMap = pendingActions.filter { it.userId == currentUid && (it.actionType == "FAVORITE" || it.actionType == "UNFAVORITE") }
                    .associateBy { it.targetId }

                val finalEntities = list.map { item ->
                    val newEntity = com.example.data.database.StateEntity.fromUserStateWithUser(item)
                    val existingEntity = statesDao.getStateById(newEntity.id)
                    val stabilized = StateUrlResolver.stabilizeEntityForRoom(newEntity, existingEntity)
                    val pendingLike = pendingLikesMap[item.state.id]
                    val pendingFav = pendingFavsMap[item.state.id]
                    stabilized.copy(
                        likedByMe = when {
                            pendingLike != null -> pendingLike.actionType == "LIKE"
                            else -> newEntity.likedByMe
                        },
                        favoritedByMe = when {
                            pendingFav != null -> pendingFav.actionType == "FAVORITE"
                            else -> newEntity.favoritedByMe
                        }
                    )
                }
                if (finalEntities.isNotEmpty()) {
                    statesDao.insertStates(finalEntities)
                }
            } catch (e: Exception) {
                Log.e(TAG, "enrichWithProfiles: failed to save to Room", e)
            }
        }
        return list
    }

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
}
