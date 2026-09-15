package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.UserState
import com.example.data.model.UserStateWithUser
import com.example.data.model.Comment
import com.example.data.model.StatusViewer
import com.example.data.repository.StatesRepository
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.util.Collections

sealed class StatesUiState { object Loading : StatesUiState(); data class Success(val states: List<UserStateWithUser>) : StatesUiState(); data class Error(val message: String) : StatesUiState() }
sealed class CreateStateUiState { object Idle : CreateStateUiState(); data class Loading(val message: String = "Preparando...") : CreateStateUiState(); object Success : CreateStateUiState(); data class Error(val message: String) : CreateStateUiState() }

class StatesViewModel(private val statesRepository: StatesRepository = StatesRepository()) : ViewModel() {
    private val errorHandler = com.example.util.Resilience.globalExceptionHandler("StatesViewModel")
    private var isActiveStatesLoading = false
    private val processingIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val localActionTimestamps = mutableMapOf<String, Long>()

    // Audio personalizado de Historias: subida durable via cola persistente.

    private val _storyAudioUploadId = MutableStateFlow<String?>(null)
    val storyAudioUploadId: StateFlow<String?> = _storyAudioUploadId.asStateFlow()

    private val _storyAudioUploadError = MutableStateFlow<String?>(null)
    val storyAudioUploadError: StateFlow<String?> = _storyAudioUploadError.asStateFlow()

    // Cache-first: Room vacío offline NO debe quedar en Loading eterno (shimmer infinito).
    // Se emite Success(list) siempre y los screens muestran el estado vacío/offline controlado.

    val statesState: StateFlow<StatesUiState> = statesRepository.getLocalStatesFlow(isReel = false).map { list -> StatesUiState.Success(list) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatesUiState.Loading)
    val storiesState: StateFlow<StatesUiState> = statesRepository.getLocalStatesFlow(isReel = false).map { StatesUiState.Success(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatesUiState.Loading)
    val reelsState: StateFlow<StatesUiState> = statesRepository.getLocalStatesFlow(isReel = true).map { StatesUiState.Success(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatesUiState.Loading)
    private val _reelsTimeline = MutableStateFlow<List<UserStateWithUser>>(emptyList())
    val reelsTimeline: StateFlow<List<UserStateWithUser>> = _reelsTimeline.asStateFlow()
    private val _searchResults = MutableStateFlow<List<UserStateWithUser>>(emptyList())
    val searchResults: StateFlow<List<UserStateWithUser>> = _searchResults.asStateFlow()
    private val _createStateFlow = MutableStateFlow<CreateStateUiState>(CreateStateUiState.Idle)
    val createStateFlow: StateFlow<CreateStateUiState> = _createStateFlow
    private val commentsJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private var activeCommentsId: String? = null
    private val _currentComments = MutableStateFlow<List<Comment>>(emptyList())
    val currentComments: StateFlow<List<Comment>> = _currentComments
    private val _currentSpectators = MutableStateFlow<List<StatusViewer>>(emptyList())
    val currentSpectators: StateFlow<List<StatusViewer>> = _currentSpectators
    private val draftsRepository = com.example.data.repository.DraftsRepository()
    private val _storyDraft = MutableStateFlow("")
    val storyDraft: StateFlow<String> = _storyDraft

    init { observeUploadSuccess() }
    private fun observeUploadSuccess() { viewModelScope.launch(errorHandler + Dispatchers.IO) { com.example.data.repository.UploadRepository.uploadSuccessEvent.collect { loadActiveStates() } } }
    fun onStoryDraftChange(text: String) { _storyDraft.value = text; viewModelScope.launch(errorHandler + Dispatchers.IO) { draftsRepository.saveChatDraft("story_draft_id", text) } }
    fun loadStoryDraft() { viewModelScope.launch(errorHandler + Dispatchers.IO) { _storyDraft.value = draftsRepository.getChatDraft("story_draft_id") ?: "" } }
    fun clearStoryDraft() { _storyDraft.value = ""; viewModelScope.launch(errorHandler + Dispatchers.IO) { draftsRepository.deleteChatDraft("story_draft_id") } }
    private val _uiLoadingState = MutableStateFlow<String?>(null)
    val uiLoadingState: StateFlow<String?> = _uiLoadingState

    fun loadActiveStates(showLoading: Boolean = false) {
        if (isActiveStatesLoading) return
        if (!com.example.util.NetworkMonitor.isOnline.value) {
            Log.d("StatesViewModel", "Offline, skipping remote refresh")
            return
        }
        isActiveStatesLoading = true
        if (showLoading) _uiLoadingState.value = "Cargando..."
        viewModelScope.launch(errorHandler + Dispatchers.IO) {
            statesRepository.getActiveStates().onSuccess { _uiLoadingState.value = null; isActiveStatesLoading = false }.onFailure { error -> _uiLoadingState.value = null; isActiveStatesLoading = false; Log.e("StatesViewModel", "Refresh states failed", error) }
        }
    }

    // Forced reels refresh: bypasses the "already loading" guard so the feed's
    // refresh button always re-fetches, and reports completion to the caller so
    // the UI spinner reflects the real network round-trip.
    fun refreshReels(onComplete: () -> Unit = {}) {
        viewModelScope.launch(errorHandler + Dispatchers.IO) {
            if (!com.example.util.NetworkMonitor.isOnline.value) {
                Log.d("StatesViewModel", "Offline, skipping forced reels refresh")
            } else {
                isActiveStatesLoading = true
                try {
                    statesRepository.getActiveStates()
                } catch (e: Exception) {
                    Log.e("StatesViewModel", "Forced reels refresh failed", e)
                } finally {
                    isActiveStatesLoading = false
                }
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete() }
        }
    }

    // Reels timeline tabs: fetches the reel list straight from Supabase with a
    // PostgREST order query (E2E), then persists into Room and publishes the
    // server-ordered list in [reelsTimeline] for the feed to render verbatim.
    fun loadReelsTimeline(orderBy: String?, onComplete: () -> Unit = {}) {
        viewModelScope.launch(errorHandler + Dispatchers.IO) {
            try {
                if (!com.example.util.NetworkMonitor.isOnline.value) {
                    Log.d("StatesViewModel", "Offline, skipping reels timeline fetch")
                } else {
                    statesRepository.fetchReelsTimeline(orderBy).onSuccess { list ->
                        _reelsTimeline.value = list
                    }
                }
            } catch (e: Exception) {
                Log.e("StatesViewModel", "loadReelsTimeline failed", e)
            } finally {
                kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }

    fun clearReelSearch() {
        _searchResults.value = emptyList()
    }

    // TikTok-style search-as-you-type: queries Supabase directly (caption ilike /
    // hashtag match) and publishes the results in [searchResults].
    fun searchReels(query: String? = null, tag: String? = null, onComplete: () -> Unit = {}) {
        viewModelScope.launch(errorHandler + Dispatchers.IO) {
            try {
                if (!com.example.util.NetworkMonitor.isOnline.value) {
                    Log.d("StatesViewModel", "Offline, skipping reels search")
                } else {
                    statesRepository.searchReels(query = query, tag = tag).onSuccess { list ->
                        _searchResults.value = list
                    }
                }
            } catch (e: Exception) {
                Log.e("StatesViewModel", "searchReels failed", e)
            } finally {
                kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }

    fun toggleLike(stateId: String, currentLikeState: Boolean, onError: ((String) -> Unit)? = null) {
        val now = System.currentTimeMillis(); if (now - (localActionTimestamps[stateId] ?: 0L) < 500) return; localActionTimestamps[stateId] = now
        if (!processingIds.add(stateId)) return
        viewModelScope.launch(errorHandler + Dispatchers.IO) { try { val currentState = findState(stateId) ?: return@launch; statesRepository.toggleLike(stateId, currentState.state.likedByMe ?: currentLikeState, isReelState(currentState.state)).onFailure { onError?.invoke(it.localizedMessage ?: "Error al dar me gusta") } } finally { processingIds.remove(stateId) } }
    }
    fun toggleFavorite(stateId: String, currentFavState: Boolean, onError: ((String) -> Unit)? = null) {
        if (!processingIds.add(stateId)) return
        viewModelScope.launch(errorHandler + Dispatchers.IO) { try { val currentState = findState(stateId) ?: return@launch; statesRepository.toggleFavorite(stateId, currentFavState, isReelState(currentState.state)).onFailure { onError?.invoke(it.localizedMessage ?: "Error al guardar favorito") } } finally { processingIds.remove(stateId) } }
    }

    fun incrementShare(stateId: String, onError: ((String) -> Unit)? = null) { viewModelScope.launch(errorHandler + Dispatchers.IO) { val currentState = findState(stateId) ?: return@launch; statesRepository.incrementShare(stateId, isReelState(currentState.state)).onFailure { onError?.invoke(it.localizedMessage ?: "Error al registrar compartir") } } }
    fun addComment(stateId: String, commentText: String, parentId: String? = null, onError: ((String) -> Unit)? = null) {
        if (commentText.isBlank()) return
        viewModelScope.launch(errorHandler + Dispatchers.IO) { val currentState = findState(stateId) ?: return@launch; val isReel = isReelState(currentState.state); val authorId = currentState.state.userId; statesRepository.addComment(stateId, commentText.trim(), isReel, parentId).onSuccess { if (authorId.isNotEmpty() && authorId != SupabaseClient.currentUser?.id) com.example.data.repository.NotificationsRepository().createNotification(authorId, "comment", stateId) }.onFailure { onError?.invoke(it.localizedMessage ?: "Error al comentar") } }
    }
    fun sendQuickReplyToAuthor(authorId: String, messageText: String, onSuccess: () -> Unit = {}, onError: ((String) -> Unit)? = null, storyId: String? = null, storyThumbnailUrl: String? = null) {
        if (messageText.isBlank()) return
        viewModelScope.launch { try { val chatsRepo = com.example.data.repository.ChatsRepository(); val messagesRepo = com.example.data.repository.MessagesRepository.getInstance(); val chatResult = chatsRepo.createDirectChat(authorId); if (chatResult.isSuccess) { val chat = chatResult.getOrThrow(); val sendResult = messagesRepo.sendMessage(chatId = chat.id, content = messageText, receiverUid = authorId, replyStoryId = storyId, thumbnailUrl = storyThumbnailUrl); if (sendResult.isSuccess) onSuccess() else onError?.invoke(sendResult.exceptionOrNull()?.localizedMessage ?: "Error al enviar mensaje por DM") } else onError?.invoke(chatResult.exceptionOrNull()?.localizedMessage ?: "No se pudo iniciar el chat con el autor") } catch (e: Exception) { onError?.invoke(e.localizedMessage ?: "Error inesperado al enviar DM") } }
    }
    fun loadComments(stateId: String) {
        activeCommentsId = stateId
        val existingTemps = _currentComments.value.filter { it.stateId == stateId && it.id.startsWith("temp_") }
        commentsJobs.values.forEach { it.cancel() }
        commentsJobs.clear()
        commentsJobs[stateId] = viewModelScope.launch(errorHandler + Dispatchers.IO) { val currentState = findState(stateId); val isReel = currentState?.let { isReelState(it.state) } ?: false; launch { statesRepository.getCommentsFlow(stateId, isReel).collect { comments -> if (activeCommentsId != stateId) return@collect; val ids = comments.map { it.id }.toSet(); _currentComments.value = (comments + existingTemps.filter { it.id !in ids }).sortedByDescending { it.createdAt } } }; statesRepository.getStateComments(stateId, isReel) }
    }
    fun loadSpectators(stateId: String) { viewModelScope.launch(errorHandler + Dispatchers.IO) { val currentState = findState(stateId); val isReel = currentState?.let { isReelState(it.state) } ?: false; statesRepository.getStatusViews(stateId, isReel).onSuccess { _currentSpectators.value = it.sortedBy { viewer -> viewer.viewedAt } }.onFailure { _currentSpectators.value = emptyList() } } }
    fun deleteStateForMe(stateId: String, onSuccess: () -> Unit) { viewModelScope.launch { com.example.data.database.PanalinkDatabase.getDatabase(com.example.PanaApplication.instance).statesDao().deleteById(stateId); onSuccess() } }
fun deleteState(stateId: String, onSuccess: () -> Unit) { viewModelScope.launch(errorHandler + Dispatchers.IO) {
        val currentState = findState(stateId) ?: return@launch
        val isReel = isReelState(currentState.state)
        val state = currentState.state
        val db = com.example.data.database.PanalinkDatabase.getDatabase(com.example.PanaApplication.instance)
        db.statesDao().deleteById(stateId)
        statesRepository.deleteUserStatus(
            stateId = stateId,
            isReel = isReel,
            mediaUrl = state.mediaUrl,
            vcdnVideoId = state.vcdnVideoId,
            vcdnPosterUrl = state.vcdnPosterUrl
        ).onSuccess {
            state.mediaUrl?.let { com.example.data.video.VideoCacheManager.removeVideoCache(it) }
            onSuccess()
        }
    } }    fun deleteComment(stateId: String, commentId: String) { viewModelScope.launch(errorHandler + Dispatchers.IO) { val currentState = findState(stateId) ?: return@launch; statesRepository.deleteComment(commentId, isReelState(currentState.state)).onSuccess { loadComments(stateId); loadActiveStates(false) } } }
    fun registerView(stateId: String) { viewModelScope.launch(errorHandler + Dispatchers.IO) { val currentState = findState(stateId) ?: return@launch; val isReel = isReelState(currentState.state); val authorId = currentState.state.userId; statesRepository.registerView(stateId, isReel).onSuccess { if (authorId.isNotEmpty()) com.example.data.repository.NotificationsRepository().createNotification(authorId, "view", stateId); statesRepository.saveStateLocally(currentState.copy(state = currentState.state.copy(viewedByMe = true))) } } }
    fun publishTextState(caption: String, isReel: Boolean = false, audioUrl: String? = null) { if (caption.isBlank()) return; _createStateFlow.value = CreateStateUiState.Loading("Preparando estado..."); viewModelScope.launch(errorHandler) { statesRepository.createState("text", caption, null, null, isReel = isReel, audioUrl = audioUrl).onSuccess { _createStateFlow.value = CreateStateUiState.Success; clearStoryDraft(); loadActiveStates() }.onFailure { _createStateFlow.value = CreateStateUiState.Error(it.localizedMessage ?: "Error publicando estado") } } }
    fun publishStoryBackground(context: android.content.Context, uri: android.net.Uri?, mimeType: String, caption: String?, audioUrl: String? = null, mediaFile: java.io.File? = null) {
        viewModelScope.launch(errorHandler + Dispatchers.IO) {
            try {
                val pendingMediaDir = java.io.File(context.filesDir, "pending_media")
                if (!pendingMediaDir.exists()) pendingMediaDir.mkdirs()
                val tempFile = if (mediaFile?.exists() == true) mediaFile else {
                    val extension = mimeType.substringAfter('/', "bin")
                    java.io.File.createTempFile("upload_story_", ".$extension", pendingMediaDir).also { file ->
                        context.contentResolver.openInputStream(uri ?: return@also)?.use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
                require(tempFile.length() > 0) { "Could not write story media to temp file" }
                val uploadId = java.util.UUID.randomUUID().toString()
                val userId = SupabaseClient.currentUser?.id ?: return@launch
                val db = com.example.data.database.PanalinkDatabase.getDatabase(context)
                db.pendingUploadDao().insertUpload(com.example.data.database.PendingUploadEntity(
                    id = uploadId, userId = userId, uploadType = "STATE",
                    localFilePath = tempFile.absolutePath, mimeType = mimeType, caption = caption,
                    metadataJson = audioUrl?.let { """{"audioUrl":"$it"}""" },
                    status = "pending"))
                val request = androidx.work.OneTimeWorkRequestBuilder<com.example.worker.SocialMediaUploadWorker>()
                    .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                    .setInputData(androidx.work.workDataOf("uploadId" to uploadId))
                    .addTag("social_upload").addTag("upload_$uploadId").addTag("social_upload_$uploadId")
                    .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, androidx.work.WorkRequest.MIN_BACKOFF_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .build()
                androidx.work.WorkManager.getInstance(context).enqueueUniqueWork("social_upload_$uploadId", androidx.work.ExistingWorkPolicy.KEEP, request)
            } catch (e: Exception) {
                Log.e("StatesViewModel", "Error scheduling story upload", e)
                _createStateFlow.value = CreateStateUiState.Error(e.localizedMessage ?: "Error programando historia")
            }
        }
    }
    fun publishReelBackground(context: android.content.Context, caption: String?, imageBytes: ByteArray, mimeType: String, uri: android.net.Uri? = null, isReel: Boolean = true, mediaFile: java.io.File? = null) {
        viewModelScope.launch(errorHandler + Dispatchers.IO) { try { val pendingMediaDir = java.io.File(context.filesDir, "pending_media"); if (!pendingMediaDir.exists()) pendingMediaDir.mkdirs(); val tempFile = if (mediaFile?.exists() == true) mediaFile else { val extension = mimeType.substringAfter('/', "bin"); java.io.File.createTempFile("upload_temp_", ".$extension", pendingMediaDir).also { file -> when { uri != null -> context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output -> input.copyTo(output) } }; imageBytes.isNotEmpty() -> file.writeBytes(imageBytes) } } }; require(tempFile.length() > 0) { "Could not write media data to temp file" }; val uploadId = java.util.UUID.randomUUID().toString(); val userId = SupabaseClient.currentUser?.id ?: return@launch; val db = com.example.data.database.PanalinkDatabase.getDatabase(context); db.pendingUploadDao().insertUpload(com.example.data.database.PendingUploadEntity(id = uploadId, userId = userId, uploadType = "REEL", localFilePath = tempFile.absolutePath, mimeType = mimeType, caption = caption, status = "pending")); val request = androidx.work.OneTimeWorkRequestBuilder<com.example.worker.SocialMediaUploadWorker>().setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()).setInputData(androidx.work.workDataOf("uploadId" to uploadId)).addTag("social_upload").addTag("upload_$uploadId").addTag("social_upload_$uploadId").setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, androidx.work.WorkRequest.MIN_BACKOFF_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS).build(); androidx.work.WorkManager.getInstance(context).enqueueUniqueWork("social_upload_$uploadId", androidx.work.ExistingWorkPolicy.KEEP, request) } catch (e: Exception) { Log.e("StatesViewModel", "Error scheduling upload worker", e); _createStateFlow.value = CreateStateUiState.Error(e.localizedMessage ?: "Error programando publicación") } }
    }
    /** Subida durable del audio personalizado de Historias: copia streaming a archivo
     *  (sin readBytes() ni red desde Compose)y encola PendingUploadEntity +
     *  SocialMediaUploadWorker. El resultado queda observable via [storyAudioUploadId]. */
fun enqueueStoryAudio(context: android.content.Context, uri: android.net.Uri?, mimeType: String, audioName: String? = null) {
    viewModelScope.launch(errorHandler + Dispatchers.IO) {

        // Validar usuario ANTES de crear/copiar archivos: sin sesión no hay cola durable.

        val userId = SupabaseClient.currentUser?.id ?: return@launch
        if (userId.isBlank()) return@launch

        var tempFile: java.io.File? = null
        try {
            val pendingMediaDir = java.io.File(context.filesDir, "pending_media")
            if (!pendingMediaDir.exists()) pendingMediaDir.mkdirs()
            val extension = mimeType.substringAfter('/', "bin")
            tempFile = java.io.File.createTempFile("story_audio_", ".$extension", pendingMediaDir).also { file ->
                context.contentResolver.openInputStream(uri ?: return@also)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            }
            require(tempFile!!.length() > 0) { "Could not write story audio to temp file" }
            val uploadId = java.util.UUID.randomUUID().toString()
            val db = com.example.data.database.PanalinkDatabase.getDatabase(context)
            db.pendingUploadDao().insertUpload(com.example.data.database.PendingUploadEntity(
                id = uploadId,
                userId = userId,
                uploadType = "AUDIO",
                localFilePath = tempFile!!.absolutePath,
                mimeType = mimeType,
                caption = audioName?.let { "Audio de historia: $it" },
                status = "pending"))
            val request = androidx.work.OneTimeWorkRequestBuilder<com.example.worker.SocialMediaUploadWorker>()
                .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                .setInputData(androidx.work.workDataOf("uploadId" to uploadId))
                .addTag("social_upload").addTag("upload_$uploadId").addTag("social_upload_$uploadId")
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, androidx.work.WorkRequest.MIN_BACKOFF_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork("social_upload_$uploadId", androidx.work.ExistingWorkPolicy.KEEP, request)
            _storyAudioUploadId.value = uploadId
        } catch (e: Exception) {
            Log.e("StatesViewModel", "Error programando audio de historia", e)
            try { tempFile?.delete() } catch (_: Exception) {}
            _storyAudioUploadError.value = e.localizedMessage ?: "Error programando audio"
        }
    }
}
    fun clearStoryAudioUploadError() { _storyAudioUploadError.value = null }

    fun resetCreateState() { _createStateFlow.value = CreateStateUiState.Idle }
    private fun findState(stateId: String): UserStateWithUser? = (reelsState.value as? StatesUiState.Success)?.states?.find { it.state.id == stateId } ?: (storiesState.value as? StatesUiState.Success)?.states?.find { it.state.id == stateId }

    /** The domain type is authoritative; media type alone must never turn a Story into a Reel. */
    private fun isReelState(state: UserState): Boolean = state.type.equals("reel", ignoreCase = true)
}
