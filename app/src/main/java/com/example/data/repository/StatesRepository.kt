package com.example.data.repository

import android.util.Log
import com.example.data.model.*
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SessionManager
import com.example.data.repository.states.RealtimeStateHandler
import com.example.data.repository.states.SocialInteractionDataSource
import com.example.data.repository.states.StatesRemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Facade over the states domain. Keeps the public API stable while delegating:
 *  - remote fetching/merging  → [StatesRemoteDataSource]
 *  - local-first interactions       → [SocialInteractionDataSource]
 *  - realtime broadcast handling     → [RealtimeStateHandler]
 *  - URL policy                        → [StateUrlResolver]
 *
 * Upload/delete (createState) and local Room caching (saveStateLocally) stay here.
 */
class StatesRepository {

    private val TAG = "StatesRepository"

    private val db by lazy { com.example.data.database.PanalinkDatabase.getDatabase(com.example.PanaApplication.instance) }
    private val statesDao by lazy { db.statesDao() }

    private val remoteDataSource by lazy { StatesRemoteDataSource() }
    private val interactionDataSource by lazy { SocialInteractionDataSource() }
    private val realtimeHandler by lazy { RealtimeStateHandler() }

    fun getLocalStatesFlow(isReel: Boolean): Flow<List<com.example.data.model.UserStateWithUser>> {
        return statesDao.getStatesFlow(isReel).map { entities ->
            entities.map { it.toUserStateWithUser() }
        }
    }

    suspend fun saveStateLocally(item: com.example.data.model.UserStateWithUser, localPath: String? = null) {
        withContext(Dispatchers.IO) {
            // VCDN pointers must stay stable in Room: the signed HLS streamUrl
            // expires, so if we ever persist a .m3u8 (legacy corruption from before
            // the fix) and we know the stable video id, restore the pointer..
            var state = item.state
            val vId = state.vcdnVideoId?.takeIf { it.isNotBlank() }
            if (vId != null && !state.mediaUrl.isNullOrBlank() && state.mediaUrl.contains(".m3u8", ignoreCase = true)) {
                state = state.copy(mediaUrl = "vcdn://$vId")
            }
            statesDao.insertState(com.example.data.database.StateEntity.fromUserStateWithUser(item.copy(state = state), localPath))
        }
    }


    suspend fun getActiveStates(): Result<Unit> = remoteDataSource.fetchActiveStates()

    suspend fun toggleLike(stateId: String, currentLikeState: Boolean, isReel: Boolean): Result<com.example.data.model.ToggleLikeResponseDto> = interactionDataSource.toggleLike(stateId, currentLikeState, isReel)

    suspend fun toggleFavorite(stateId: String, currentFavState: Boolean, isReel: Boolean): Result<com.example.data.model.ToggleFavoriteResponseDto> = interactionDataSource.toggleFavorite(stateId, currentFavState, isReel)

    suspend fun getSavedStates(): Result<List<UserStateWithUser>> = remoteDataSource.fetchSavedStates()

    suspend fun incrementShare(stateId: String, isReel: Boolean): Result<Unit> = interactionDataSource.incrementShare(stateId, isReel)

    fun getCommentsFlow(stateId: String, isReel: Boolean): Flow<List<Comment>> = interactionDataSource.getCommentsFlow(stateId, isReel)

    suspend fun addComment(stateId: String, commentText: String, isReel: Boolean, parentId: String? = null): Result<Unit> = interactionDataSource.addComment(stateId, commentText, isReel, parentId)

    suspend fun getStateComments(stateId: String, isReel: Boolean): Result<List<Comment>> = interactionDataSource.getStateComments(stateId, isReel)

    suspend fun deleteComment(commentId: String, isReel: Boolean): Result<Unit> = interactionDataSource.deleteComment(commentId, isReel)

    suspend fun getStatusViews(stateId: String, isReel: Boolean): Result<List<StatusViewer>> = interactionDataSource.getStatusViews(stateId, isReel)

    suspend fun registerView(stateId: String, isReel: Boolean): Result<Unit> = interactionDataSource.registerView(stateId, isReel)

    suspend fun getUserReels(userId: String): Result<List<UserStateWithUser>> = remoteDataSource.fetchUserReels(userId)

    suspend fun handleRealtimeStatus(userState: com.example.data.model.UserState) = realtimeHandler.handleRealtimeStatus(userState)

    suspend fun handleRealtimeSocialInteraction(
        update: com.example.data.supabase.SupabaseClient.SocialInteractionUpdate,
        interactionType: String
    ) = realtimeHandler.handleRealtimeSocialInteraction(update, interactionType)

    suspend fun createState(
        mediaType: String, // "text" | "image" | "video"
        caption: String?,
        mediaBytes: ByteArray? = null,
        mediaMimeType: String? = null,
        isReel: Boolean = false,
        presetMediaUrl: String? = null,
        audioUrl: String? = null,
        mediaFile: java.io.File? = null,
        thumbnailUrl: String? = null
    ): Result<UserState> = withContext(Dispatchers.IO) {
        val currentUid = SupabaseClient.currentUser?.id ?: return@withContext Result.failure(Exception("Not authenticated"))
        
        // Calculate expiration: 24h if story, NULL if reel
        val expiresAtStr: String? = if (isReel) {
            null
        } else {
            val cal = Calendar.getInstance()
            cal.add(Calendar.HOUR, 24)
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(cal.time)
        }
        val stateType = if (isReel) "reel" else "story"
 
        val nowStr = SupabaseClient.getNowIsoString()
        val stateId = if (SupabaseClient.isConfigured) {
            UUID.randomUUID().toString()
        } else {
            "state_${UUID.randomUUID()}"
        }
 
        var mediaUrl: String? = presetMediaUrl
        try {
            val service = SupabaseClient.apiService ?: return@withContext Result.failure(Exception("Supabase not configured"))
            val token = SupabaseClient.currentToken ?: return@withContext Result.failure(Exception("Session expired"))
            val apiKey = SupabaseClient.supabaseAnonKey
            val bearer = "Bearer $token"
 
            // 1. Upload to dynamic CDN tunnel using UploadRepository
            if (mediaUrl == null && mediaMimeType != null) {
                val uploadResult = if (mediaFile != null && mediaFile.exists()) {
                    UploadRepository().uploadVideo(mediaFile, mediaMimeType, caption ?: "", currentUid)
                } else if (mediaBytes != null) {
                    UploadRepository().uploadVideo(mediaBytes, mediaMimeType, caption ?: "", currentUid)
                } else {
                    null
                }

                if (uploadResult != null) {
                    if (uploadResult.isSuccess) {
                        mediaUrl = uploadResult.getOrThrow().url
                        Log.d(TAG, "Uploaded successfully to CDN: $mediaUrl")
                    } else {
                        val uploadError = uploadResult.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                        Log.e(TAG, "CDN upload failed: $uploadError")
                        return@withContext Result.failure(Exception("Error al subir archivo al CDN dinámico: $uploadError"))
                    }
                }
            }

            // 2. Insert record in user_reels or user_stories table using a robust DTO
            val vcdnVideoId = if (mediaUrl != null && mediaUrl.startsWith("vcdn://")) {
                VcdnUrlResolver.videoIdOf(mediaUrl)
            } else null
            val vcdnPoster = if (vcdnVideoId != null) thumbnailUrl else null
            val createResponse = if (isReel) {
                val reelDto = com.example.data.model.ReelDto(
                    id = stateId,
                    authorId = currentUid,
                    mediaUrl = mediaUrl ?: "",
                    mediaType = mediaType,
                    caption = caption,
                    thumbnailUrl = thumbnailUrl,
                    createdAt = nowStr,
                    vcdnVideoId = vcdnVideoId,
                    vcdnPosterUrl = vcdnPoster
                )
                service.createReel(apiKey, bearer, reelDto)
            } else {
                // Filtrar nulos: claves ausentes evitan errores 42703 si una columna
                // opcional aún no existe en el esquema remoto.
                val stateMap = mutableMapOf<String, Any?>(
                    "id" to stateId,
                    "author_id" to currentUid,
                    "media_url" to mediaUrl,
                    "media_type" to mediaType,
                    "caption" to caption,
                    "thumbnail_url" to thumbnailUrl,
                    "created_at" to nowStr,
                    "expires_at" to expiresAtStr
                ).filterValues { it != null }.toMutableMap()
                if (vcdnVideoId != null) {
                    stateMap["vcdn_video_id"] = vcdnVideoId
                    if (vcdnPoster != null) stateMap["vcdn_poster_url"] = vcdnPoster
                }
                if (audioUrl != null) {
                    val withAudio = stateMap + ("audio_url" to audioUrl)
                    val response = service.createStory(apiKey, bearer, withAudio)
                    if (!response.isSuccessful && response.code() == 400) {
                        // Columna audio_url no existe en el backend: reintentar sin ella
                        Log.w(TAG, "createStory con audio_url falló (400). Reintentando sin audio_url")
                        service.createStory(apiKey, bearer, stateMap)
                    } else {
                        response
                    }
                } else {
                    service.createStory(apiKey, bearer, stateMap)
                }
            }

            if (createResponse.isSuccessful) {
                val newState = UserState(
                    id = stateId,
                    authorId = currentUid,
                    userIdField = currentUid,
                    mediaUrl = mediaUrl,
                    mediaType = mediaType,
                    caption = caption,
                    visibility = "public",
                    createdAt = nowStr,
                    type = stateType
                )
                Result.success(newState)
            } else {
                val errorBody = createResponse.errorBody()?.string()
                Log.e(TAG, "🚨 [DIAGNOSTIC] Fallo en ${if(isReel) "createReel" else "createStory"}")
                Log.e(TAG, "HTTP STATUS: ${createResponse.code()}")
                Log.e(TAG, "ERROR BODY: $errorBody")
                Log.e(TAG, "PAYLOAD: ID=$stateId, Author=$currentUid, URL=$mediaUrl, Type=$mediaType, Caption=$caption")
                
                Result.failure(Exception("Supabase Error ${createResponse.code()}: $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "createState exception", e)
            Result.failure(e)
        }
    }

    suspend fun deleteUserStatus(stateId: String, isReel: Boolean, mediaUrl: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Physically delete from CDN if mediaUrl is provided
            mediaUrl?.let { url ->
                val fileId = url.substringAfterLast("/")
                if (fileId.isNotEmpty() && fileId.contains("media-")) {
                    try {
                        val cdnUrl = CdnManager.getCDNUrl()
                        val deleteEndpoint = if (cdnUrl.endsWith("/")) "${cdnUrl}delete/$fileId" else "$cdnUrl/delete/$fileId"
                        Log.i(TAG, "Procediendo a BORRADO FÍSICO CDN de archivo: $fileId en endpoint: $deleteEndpoint")
                        val client = OkHttpClient()
                        val request = Request.Builder()
                            .url(deleteEndpoint)
                            .delete()
                            .build()
                        client.newCall(request).execute().use { deleteRes ->
                            Log.i(TAG, "Resultado de borrado físico en CDN: HTTP ${deleteRes.code}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error intentando borrar físicamente el archivo del CDN: ${e.message}", e)
                    }
                }
            }

            val service = SupabaseClient.apiService ?: return@withContext Result.failure(Exception("Supabase not configured"))
            val token = SupabaseClient.currentToken ?: return@withContext Result.failure(Exception("Session expired"))
            val apiKey = SupabaseClient.supabaseAnonKey
            val bearer = "Bearer $token"

            val response = if (isReel) {
                service.deleteReel(apiKey, bearer, "eq.$stateId")
            } else {
                service.deleteStory(apiKey, bearer, "eq.$stateId")
            }
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val errorStr = response.errorBody()?.string()
                Result.failure(Exception(SupabaseClient.parseSupabaseError(errorStr, "Error deleting state")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}
