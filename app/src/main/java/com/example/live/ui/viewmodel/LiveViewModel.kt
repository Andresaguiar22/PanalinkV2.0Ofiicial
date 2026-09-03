package com.example.live.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.supabase.SupabaseClient
import com.example.live.data.remote.LivePresenceManager
import com.example.live.data.remote.LiveRealtimeManager
import com.example.live.data.repository.LiveReactionsRepositoryImpl
import com.example.live.data.repository.LiveRepositoryImpl
import com.example.live.data.repository.LiveModerationRepositoryImpl
import com.example.live.data.repository.LiveReportRepositoryImpl
import com.example.live.domain.model.LiveComment
import com.example.live.domain.model.LiveStream
import com.example.live.domain.repository.LiveReactionsRepository
import com.example.live.domain.repository.LiveRepository
import com.example.live.domain.repository.LiveModerationRepository
import com.example.live.domain.repository.LiveReportRepository
import com.example.live.domain.repository.LiveTokenResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LiveUiState(
    val isLoading: Boolean = false,
    val activeLives: List<LiveStream> = emptyList(),
    val error: String? = null
)

    class LiveViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: LiveRepository = LiveRepositoryImpl(application)
    private val reactionsRepository: LiveReactionsRepository = LiveReactionsRepositoryImpl(application)
    private val moderationRepository: LiveModerationRepository = LiveModerationRepositoryImpl(application)
    private val reportRepository: LiveReportRepository = LiveReportRepositoryImpl(application)

    private val _uiState = MutableStateFlow(LiveUiState())
    val uiState: StateFlow<LiveUiState> = _uiState.asStateFlow()

    private val _comments = MutableStateFlow<List<LiveComment>>(emptyList())
    val comments: StateFlow<List<LiveComment>> = _comments.asStateFlow()

    private val _viewerCount = MutableStateFlow(1)
    val viewerCount: StateFlow<Int> = _viewerCount.asStateFlow()

    private val _streamEnded = MutableStateFlow(false)
    val streamEnded: StateFlow<Boolean> = _streamEnded.asStateFlow()

    val reactionEvents: SharedFlow<Unit> = reactionsRepository.reactionEvents

    private val feedRealtimeManager = LiveRealtimeManager(
        onLiveStreamChanged = {
            loadActiveLives()
        }
    )

    private var commentsRealtimeManager: LiveRealtimeManager? = null
    private var streamStatusRealtimeManager: LiveRealtimeManager? = null
    private var presenceManager: LivePresenceManager? = null

    init {
        loadActiveLives()
        feedRealtimeManager.start()
    }

    fun loadActiveLives() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.getLiveStreams()
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    activeLives = result.getOrDefault(emptyList())
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Error al cargar transmisiones"
                )
            }
        }
    }

    suspend fun getLiveStream(id: String): LiveStream? {
        val result = repository.getLiveStream(id)
        return result.getOrNull()
    }

    suspend fun getLiveToken(roomName: String, identity: String, role: String): Result<LiveTokenResult> {
        return repository.getLiveKitToken(roomName, identity, role)
    }

    suspend fun createAndStartLive(title: String, description: String?): Result<LiveStream> {
        val createResult = repository.createLiveStream(title, description, null)
        if (createResult.isSuccess) {
            val stream = createResult.getOrThrow()
            repository.startLiveStream(stream.id)
            return Result.success(stream)
        }
        return Result.failure(createResult.exceptionOrNull() ?: Exception("Error al crear stream"))
    }

    suspend fun endLive(id: String) {
        repository.endLiveStream(id)
    }

    fun loadComments(streamId: String) {
        viewModelScope.launch {
            val result = repository.getComments(streamId)
            if (result.isSuccess) {
                _comments.value = result.getOrDefault(emptyList()).filter { !it.isDeleted }
            }
        }
    }

    fun postComment(streamId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.postComment(streamId, text)
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            val result = moderationRepository.deleteComment(commentId)
            if (result.isSuccess) {
                _comments.value = _comments.value.filter { it.id != commentId }
            }
        }
    }

    fun muteUser(streamId: String, userId: String) {
        viewModelScope.launch {
            moderationRepository.muteUser(streamId, userId)
        }
    }

    fun blockUser(streamId: String, userId: String) {
        viewModelScope.launch {
            moderationRepository.blockUser(streamId, userId)
        }
    }


    fun report(streamId: String, reportedUserId: String?, commentId: String?, reason: String) {
        viewModelScope.launch {
            reportRepository.report(streamId, reportedUserId, commentId, reason)
        }
    }

    fun sendReaction(streamId: String) {
        viewModelScope.launch {
            reactionsRepository.sendReaction(streamId)
        }
    }

    fun startStreamSession(streamId: String) {
        val userId = SupabaseClient.currentUser?.id ?: "user_${System.currentTimeMillis()}"
        
        presenceManager?.stop()
        presenceManager = LivePresenceManager(streamId, userId).apply {
            start()
        }
        viewModelScope.launch {
            presenceManager?.viewerCount?.collect { count ->
                _viewerCount.value = count
            }
        }

        commentsRealtimeManager?.stop()
        commentsRealtimeManager = LiveRealtimeManager(
            onLiveStreamChanged = {},
            streamId = streamId,
            onCommentReceived = { comment ->
                if (!comment.isDeleted) {
                    val current = _comments.value.toMutableList()
                    if (current.none { it.id == comment.id }) {
                        current.add(comment)
                        _comments.value = current
                    }
                }
            }
        )
        commentsRealtimeManager?.start()

        streamStatusRealtimeManager?.stop()
        streamStatusRealtimeManager = LiveRealtimeManager(
            onLiveStreamChanged = {
                viewModelScope.launch {
                    val stream = getLiveStream(streamId)
                    if (stream == null || stream.status == "ENDED") {
                        _streamEnded.value = true
                    }
                }
            }
        )
        streamStatusRealtimeManager?.start()

        reactionsRepository.startListening(streamId)
    }

    fun stopStreamSession() {
        commentsRealtimeManager?.stop()
        commentsRealtimeManager = null
        streamStatusRealtimeManager?.stop()
        streamStatusRealtimeManager = null
        presenceManager?.stop()
        presenceManager = null
        reactionsRepository.stopListening()
    }

    override fun onCleared() {
        super.onCleared()
        feedRealtimeManager.stop()
        stopStreamSession()
    }
}
