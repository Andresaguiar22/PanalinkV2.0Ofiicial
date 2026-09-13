package com.example.feature.chat.presentation

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.example.core.logger.AppLogger
import com.example.feature.diagnostics.ChatDiagnostics
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Message
import com.example.data.model.Profile
import com.example.data.repository.MessagesRepository
import com.example.data.repository.ProfilesRepository
import com.example.data.repository.StickerRepository
import com.example.util.AudioPlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@Stable
sealed class ChatUiState {
    @Immutable
    object Loading : ChatUiState()
    @Immutable
    data class Success(
        val messages: List<Message>,
        val otherUser: Profile? = null
    ) : ChatUiState()
    @Immutable
    data class Error(val message: String) : ChatUiState()
}

enum class RecordState {
    IDLE,
    RECORDING,
    LOCKED_RECORDING,
    PREVIEWING,
    SENDING,
    CANCELING
}

class ChatViewModel : ViewModel() {
    private val messagesRepo = MessagesRepository.getInstance()
    private val publicProfileRepo = com.example.data.repository.PublicProfileRepository.getInstance()
    private val chatsRepo = com.example.data.repository.ChatsRepository()
    private val playlistRepo = com.example.media.playlist.PlaylistRepository(
        com.example.data.database.PanalinkDatabase.getDatabase(com.example.PanaApplication.instance).playlistDao(),
        com.example.data.database.PanalinkDatabase.getDatabase(com.example.PanaApplication.instance).collaboratorDao()
    )

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** Progreso real de subida por messageId: bytesWritten + totalBytes desde MediaUploadWorker. */
    private val _uploadProgress = MutableStateFlow<Map<String, Pair<Long, Long>>>(emptyMap())
    val uploadProgress: StateFlow<Map<String, Pair<Long, Long>>> = _uploadProgress.asStateFlow()
    private var uploadProgressJob: kotlinx.coroutines.Job? = null

    fun observeUploadProgress(messageIds: List<String>, enabled: Boolean = true) {
        if (!enabled) {
            uploadProgressJob?.cancel()
            uploadProgressJob = null
            _uploadProgress.value = emptyMap()
            return
        }
        val pendingIds = messageIds.filter { id ->
            _uiState.value.let { it is ChatUiState.Success && it.messages.any { m -> m.id == id && m.isPendingUpload() } }
        }
        if (pendingIds.isEmpty()) {
            uploadProgressJob?.cancel()
            uploadProgressJob = null
            _uploadProgress.value = emptyMap()
            return
        }

        uploadProgressJob?.cancel()
        uploadProgressJob = viewModelScope.launch {
            while (true) {
                val updates = mutableMapOf<String, Pair<Long, Long>>()
                try {
                    pendingIds.forEach { id ->
                        val infos = androidx.work.WorkManager.getInstance(com.example.PanaApplication.instance)
                            .getWorkInfosForUniqueWork("upload_$id").get()
                        val info = infos.firstOrNull() ?: return@forEach
                        if (info.state != androidx.work.WorkInfo.State.RUNNING) return@forEach
                        val written = info.progress.getLong("bytesWritten", 0L)
                        val total = info.progress.getLong("totalBytes", 0L)
                        if (total > 0) updates[id] = written to total
                    }
                } catch (_: Exception) {}
                _uploadProgress.value = updates
                kotlinx.coroutines.delay(700)
            }
        }
    }

    private fun Message.isPendingUpload(): Boolean =
        status == "sending" || status == "pending" || status == "pending_media"

    private val _myPlaylists = MutableStateFlow<List<com.example.media.playlist.PlaylistEntity>>(emptyList())
    val myPlaylists: StateFlow<List<com.example.media.playlist.PlaylistEntity>> = _myPlaylists.asStateFlow()

    fun loadMyPlaylists() {
        viewModelScope.launch {
            playlistRepo.getPlaylistsByUser(currentUserId).collect {
                _myPlaylists.value = it
            }
        }
    }

    val currentUserId: String
        get() = com.example.data.supabase.SupabaseClient.currentUser?.id ?: ""

    val currentAvatarUrl: String?
        get() = com.example.data.supabase.SupabaseClient.currentProfile?.avatarUrl

    private val _inputMessage = MutableStateFlow("")
    val inputMessage: StateFlow<String> = _inputMessage.asStateFlow()

    private val _isGhostMode = MutableStateFlow(false)
    val isGhostMode: StateFlow<Boolean> = _isGhostMode.asStateFlow()

    private val _emojiMedia = MutableStateFlow(EmojiMediaUiState())
    val emojiMedia: StateFlow<EmojiMediaUiState> = _emojiMedia.asStateFlow()

    private val _typingUsers = MutableStateFlow<List<String>>(emptyList())
    val typingUsers: StateFlow<List<String>> = _typingUsers.asStateFlow()

    private val _userPresence = MutableStateFlow<Map<String, String>>(emptyMap())
    val userPresence: StateFlow<Map<String, String>> = _userPresence.asStateFlow()

    init {
        viewModelScope.launch {
            com.example.data.repository.PresenceRepository.presenceMap.collect { presenceMap ->
                val stringMap = presenceMap.mapValues { entry ->
                    val info = entry.value
                    if (info.secondaryStatus != com.example.data.repository.SecondaryPresenceStatus.NONE) {
                        info.secondaryStatus.label
                    } else {
                        com.example.util.PresenceTimeFormatter.formatLastSeen(
                            status = info.status,
                            lastSeenMs = info.lastSeen
                        )
                    }
                }
                _userPresence.value = stringMap
            }
        }

        // Offline-first: al recuperar conectividad recargamos los mensajes del chat
        // abierto (Room emite el resultado automáticamente al finalizar el fetch)
        viewModelScope.launch {
            var wasOnline = com.example.util.NetworkMonitor.isOnline.value
            com.example.util.NetworkMonitor.isOnline.collect { isOnline ->
                if (isOnline && !wasOnline) {
                    val chatId = currentChatId
                    if (!chatId.isNullOrEmpty()) {
                        launch(Dispatchers.IO) {
                            try {
                                messagesRepo.getMessagesForChatPaged(chatId)
                            } catch (e: Exception) {
                                AppLogger.e(message = "Error refreshing chat on connectivity restore", throwable = e)
                            }
                        }
                    }
                }
                wasOnline = isOnline
            }
        }
    }

    private val _editedMessages = MutableStateFlow<Map<String, String>>(emptyMap())
    val editedMessages: StateFlow<Map<String, String>> = _editedMessages.asStateFlow()

    private val _replyingToMessage = MutableStateFlow<Message?>(null)
    val replyingToMessage: StateFlow<Message?> = _replyingToMessage.asStateFlow()

    private val _editingMessage = MutableStateFlow<Message?>(null)
    val editingMessage: StateFlow<Message?> = _editingMessage.asStateFlow()

    private val _recordState = MutableStateFlow(RecordState.IDLE)
    val recordState: StateFlow<RecordState> = _recordState.asStateFlow()

    private val _isPreviewSending = MutableStateFlow(false)
    val isPreviewSending: StateFlow<Boolean> = _isPreviewSending.asStateFlow()

    private val voiceController by lazy { com.example.ui.components.chat.voice.VoiceRecordingController(com.example.PanaApplication.instance) }
    private val previewAudioPlayer by lazy { com.example.util.AudioPlayer() }

    private val _voiceAmplitudes = MutableStateFlow<List<Float>>(emptyList())
    val voiceAmplitudes: StateFlow<List<Float>> = _voiceAmplitudes.asStateFlow()

    private val _previewPlayerState = MutableStateFlow(com.example.util.AudioPlayerState())
    val previewPlayerState: StateFlow<com.example.util.AudioPlayerState> = _previewPlayerState.asStateFlow()

    private val _previewWaveform = MutableStateFlow<List<Float>>(emptyList())
    val previewWaveform: StateFlow<List<Float>> = _previewWaveform.asStateFlow()

    var previewFile: java.io.File? = null
    var previewDurationSeconds: Int = 0

    init {
        viewModelScope.launch {
            voiceController.amplitudes.collect {
                _voiceAmplitudes.value = it
            }
        }
        viewModelScope.launch {
            previewAudioPlayer.playerState.collect {
                _previewPlayerState.value = it
            }
        }
    }

    private val _playNotificationSound = MutableSharedFlow<Unit>()
    val playNotificationSound: SharedFlow<Unit> = _playNotificationSound

    private val _playOutgoingSound = MutableSharedFlow<Unit>()
    val playOutgoingSound: SharedFlow<Unit> = _playOutgoingSound

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private var currentChatId: String? = null
    private var currentOtherUserId: String? = null
    private var audioRecorder: com.example.util.AudioRecorder? = null

private var chatJob: kotlinx.coroutines.Job? = null
    private var typingJob: kotlinx.coroutines.Job? = null
    private var loadJob: kotlinx.coroutines.Job? = null

    fun loadChatHistory(chatId: String, otherUserId: String) {
        val correlationId = ChatDiagnostics.correlationId()
        val startedAtMs = System.currentTimeMillis()
        ChatDiagnostics.started(name = "chat.history.load.started", correlationId = correlationId)
        // UN SOLO chat por pareja: el identificador canónico del DM es thread_id.
        // 1. getChatByThreadId(chatId)
        // 2. Si no existe, getChatById(chatId)
        // 3. Si la entidad tiene threadId, utilizar threadId
        // 4. Solo utilizar el id de Room si ese ID es realmente la identidad local canónica
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbChats = com.example.data.database.PanalinkDatabase.getDatabase(com.example.PanaApplication.instance).chatDao()
                val canonicalChatId = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val byThread = dbChats.getChatByThreadId(chatId)
                    if (byThread != null) {
                        byThread.threadId?.takeIf { it.isNotBlank() } ?: byThread.id
                    } else {
                        val byId = dbChats.getChatById(chatId)
                        byId?.threadId?.takeIf { it.isNotBlank() } ?: byId?.id ?: chatId
                    }
                }
                ChatDiagnostics.completed(name = "chat.history.canonical_id.resolved", startedAtMs = startedAtMs, correlationId = correlationId)
                val effectiveChatId = canonicalChatId
                currentChatId = effectiveChatId

                // Mark active chat globally in SupabaseClient for realtime read updates
                com.example.data.supabase.SupabaseClient.activeChatId = effectiveChatId
                com.example.data.supabase.SupabaseClient.isChatScreenActive = true

                var resolvedOtherUserId = otherUserId
                if (resolvedOtherUserId.isBlank() || resolvedOtherUserId == "unknown") {
                    try {
                        val chatEntity = dbChats.getChatByThreadId(effectiveChatId) ?: dbChats.getChatById(effectiveChatId)

                        val fromEntity = chatEntity?.otherUserId
                        if (!fromEntity.isNullOrBlank()) {
                            resolvedOtherUserId = fromEntity
                        } else {
                            // Last resort: infer from the most recent incoming message's sender
                            val lastIncoming = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                com.example.data.database.PanalinkDatabase.getDatabase(com.example.PanaApplication.instance).messageDao().getMessagesForChat(effectiveChatId)
                                    .firstOrNull { it.senderId != com.example.data.supabase.SupabaseClient.currentUser?.id && !it.senderId.isNullOrBlank() }?.senderId
                            }
                            if (lastIncoming != null) resolvedOtherUserId = lastIncoming
                        }
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Error resolving otherUserId for chat $effectiveChatId", e)
                    }
                }
                withContext(Dispatchers.Main) {
                    currentOtherUserId = resolvedOtherUserId
                    continueLoadChatHistory(effectiveChatId, resolvedOtherUserId)
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error loading chat history for $chatId", e)
                ChatDiagnostics.failed(name = "chat.history.load.failed", startedAtMs = startedAtMs, correlationId = correlationId, throwable = e)
            }
        }
    }


    private fun continueLoadChatHistory(chatId: String, otherUserId: String) {
        val correlationId = ChatDiagnostics.correlationId()
        val startedAtMs = System.currentTimeMillis()
        // Mark thread read & delivered.
        // "Lectura Inteligente" OFF: no automatic read ack on open — read is only
        // sent when the user replies. Local Room state still clears the badge.
        viewModelScope.launch {
            try {
                messagesRepo.markThreadDelivered(chatId)
                ChatDiagnostics.completed(name = "chat.messages.delivered.completed", startedAtMs = startedAtMs, correlationId = correlationId)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error marking thread delivered on load", e)
                ChatDiagnostics.failed(name = "chat.messages.delivered.failed", startedAtMs = startedAtMs, correlationId = correlationId, throwable = e)
            }
            if (!isSmartReadEnabled()) {
                try {
                    messagesRepo.markThreadReadLocally(chatId)
                    ChatDiagnostics.completed(name = "chat.messages.read.completed", startedAtMs = startedAtMs, correlationId = correlationId)
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error marking thread read on load", e)
                    ChatDiagnostics.failed(name = "chat.messages.read.failed", startedAtMs = startedAtMs, correlationId = correlationId, throwable = e)
                }
            }
        }

        // Collect typing status updates for this chat
        typingJob?.cancel()
        _typingUsers.value = emptyList()
        typingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                com.example.data.supabase.SupabaseClient.realtimeTyping.collect { status ->
                    // Ignore our own typing echo (the channel broadcasts with self=true)
                    // so we never show a "typing" indicator for the current user.
                    val myUid = com.example.data.supabase.SupabaseClient.currentUser?.id
                    if (status.userId == myUid) return@collect
                    if (status.chatId == chatId) {
                        val currentList = _typingUsers.value.toMutableList()
                        if (status.isTyping) {
                            if (!currentList.contains(status.userId)) {
                                currentList.add(status.userId)
                            }
                            // Auto-timeout typing indicator after 5 seconds
                            launch {
                                kotlinx.coroutines.delay(5000)
                                val listAfterDelay = _typingUsers.value.toMutableList()
                                if (listAfterDelay.contains(status.userId)) {
                                    listAfterDelay.remove(status.userId)
                                    _typingUsers.value = listAfterDelay
                                }
                            }
                        } else {
                            currentList.remove(status.userId)
                        }
                        _typingUsers.value = currentList
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Ignore normal cancellation
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error collecting typing status", e)
                ChatDiagnostics.failed(name = "chat.typing.collector.failed", throwable = e)
            }
        }

        chatJob?.cancel()
        chatJob = viewModelScope.launch {
            // 1. Load local profile first from PublicProfileRepository / Room (Offline-First)
            var currentOtherProfile: Profile? = null
            if (otherUserId.isNotBlank()) {
                val cachedPublic = publicProfileRepo.getPublicProfile(otherUserId, forceRefresh = false)
                if (cachedPublic is com.example.data.repository.PublicProfileFetchResult.Success) {
                    val pub = cachedPublic.data
                    currentOtherProfile = com.example.data.repository.PublicProfileResolver.toProfile(pub)
                }
            }

            // 2. Load cached messages list
            val cachedMessages = messagesRepo.getCachedMessages(chatId)
            ChatDiagnostics.event(name = "chat.history.cache.loaded", details = "count=${cachedMessages.size}")

            if (cachedMessages.isNotEmpty()) {
                // Instantly emit success with the local cache! No spinner!
                _uiState.value = ChatUiState.Success(
                    messages = cachedMessages,
                    otherUser = currentOtherProfile
                )
            } else {
                // No local messages, default to loading
                _uiState.value = ChatUiState.Loading
            }

            // 3. Launch background job to fetch remote public profile and update cache & State
            if (otherUserId.isNotBlank()) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val result = publicProfileRepo.getPublicProfile(otherUserId, forceRefresh = true)
                        if (result is com.example.data.repository.PublicProfileFetchResult.Success) {
                            val pub = result.data
                            val remoteProfile = com.example.data.repository.PublicProfileResolver.toProfile(pub)
                            currentOtherProfile = remoteProfile

                            // Persist the resolved identity locally so avatars (bubbles/top bar)
                            // survive cold starts and offline opens of this chat..
                            val identityRepo = com.example.identity.repository.IdentityRepository(com.example.PanaApplication.instance.applicationContext)
                            identityRepo.saveProfile(
                                com.example.identity.model.IdentityUiState(
                                    userId = pub.id,
                                    displayName = remoteProfile.displayName,
                                    avatarUrl = com.example.data.repository.CdnManager.resolveAvatarUrl(remoteProfile.avatarUrl),
                                    avatarLocalPath = null
                                )
                           )

                            // Update UI state if it's currently Success
                            val currentState = _uiState.value
                            if (currentState is ChatUiState.Success) {
                                _uiState.value = currentState.copy(otherUser = remoteProfile)
                            }
                        } else if (result is com.example.data.repository.PublicProfileFetchResult.NotFound) {
                            Log.w("ChatViewModel", "Public profile not found for $otherUserId")
                        } else {
                            Log.w("ChatViewModel", "Public profile fetch error for $otherUserId: $result")
                        }
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Error updating remote profile", e)
                    }
                }
            }

            // 4. Collect the Flow from Room local database in real-time
            messagesRepo.getMessagesFlow(chatId).collect { messages ->
                _uiState.value = ChatUiState.Success(
                    messages = messages,
                    otherUser = currentOtherProfile
                )
            }
        }
        
        // Fetch remote messages in background to update local DB on opening chat
        viewModelScope.launch(Dispatchers.IO) {
            val remoteStartedAtMs = System.currentTimeMillis()
            ChatDiagnostics.started(name = "chat.history.remote.started")
            try {
                messagesRepo.getMessagesForChatPaged(chatId)
                ChatDiagnostics.completed(name = "chat.history.remote.completed", startedAtMs = remoteStartedAtMs)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error fetching remote paged messages", e)
                ChatDiagnostics.failed(name = "chat.history.remote.failed", startedAtMs = remoteStartedAtMs, throwable = e)
            }
        }
    }

    fun loadMoreMessages(chatId: String) {
        val currentState = _uiState.value
        if (currentState !is ChatUiState.Success) return
        if (_isLoadingMore.value) return

        val currentCount = currentState.messages.size
        if (currentCount < 10) return

        _isLoadingMore.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val oldestTimestamp = currentState.messages.firstOrNull()?.createdAt
                messagesRepo.getMessagesForChatPaged(chatId, limit = 50, oldestTimestamp = oldestTimestamp)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error loading more paged messages", e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }
    fun sharePlaylist(chatId: String, playlist: com.example.media.playlist.PlaylistEntity) {
        viewModelScope.launch {
            messagesRepo.sendPlaylistShareMessage(
                chatId = chatId,
                playlistId = playlist.id,
                playlistName = playlist.name,
                coverUrl = playlist.coverPath
            )
        }
    }

    /** True when "Lectura Inteligente" is enabled in the Privacy center. */
    private fun isSmartReadEnabled(): Boolean {
        return try {
            val uid = com.example.data.supabase.SupabaseClient.currentUser?.id ?: "guest"
            val prefs = com.example.PanaApplication.instance
                .getSharedPreferences("panalink_prefs", android.content.Context.MODE_PRIVATE)
            prefs.getBoolean("profile_smart_read_$uid", true)
        } catch (e: Exception) {
            true
        }
    }

    fun markMessagesAsRead(visibleIds: List<String>) {
        val chatId = currentChatId ?: return
        viewModelScope.launch {
            try {
                if (isSmartReadEnabled()) {
                    messagesRepo.markVisibleMessagesAsRead(chatId, visibleIds)
                } else {
                    messagesRepo.markThreadReadLocally(chatId)
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error marking visible messages as read", e)
            }
        }
    }
    fun sendTypingStatus(isTyping: Boolean) {
        val chatId = currentChatId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            com.example.data.supabase.SupabaseClient.sendTypingStatus(chatId, isTyping)
        }
    }
    
    fun setReplyingToMessage(msg: Message?) {
        _replyingToMessage.value = msg
    }

    fun setEditingMessage(msg: Message?) {
        _editingMessage.value = msg
    }

    fun clearReplyAndEdit() {
        _replyingToMessage.value = null
        _editingMessage.value = null
    }

    fun onInputMessageChange(text: String) {
        _inputMessage.value = text
    }

    fun deleteLastChar() {
        val current = _inputMessage.value
        if (current.isNotEmpty()) {
            _inputMessage.value = current.substring(0, current.length - 1)
        }
    }

    // --- Emoji / GIF / Sticker sheet state (EmojiMediaUiState) ---

    fun onEmojiTabSelected(tab: Int) {
        _emojiMedia.value = _emojiMedia.value.copy(selectedTab = tab)

        // Replica el LaunchedEffect original: al cambiar de pestaña se recargan
        // SOLO los datos de la pestaña activa.
        if (tab == 1) loadGifs(query = _emojiMedia.value.searchQuery.ifEmpty { "funny" })
        if (tab == 2) loadEmojiStickers(query = _emojiMedia.value.searchQuery.ifEmpty { null })
    }

    fun setEmojiSearchActive(isActive: Boolean) {
        _emojiMedia.value = _emojiMedia.value.copy(isSearchActive = isActive, searchQuery = if (isActive) _emojiMedia.value.searchQuery else "")

        // Al cerrar búsqueda, el grid vuelve a la categoría seleccionada y los GIFs
        // al trending (mismo efecto que el LaunchedEffect keyed on debouncedSearchQuery).
        if (!isActive) {
            loadEmojiStickers(query = null)
            loadGifs(query = "funny")
        }
    }

    fun setEmojiSearchQuery(query: String) {
        _emojiMedia.value = _emojiMedia.value.copy(searchQuery = query)



        // Debounce 300ms igual que el LaunchedEffect original.
        emojiSearchDebounceJob?.cancel()
        emojiSearchDebounceJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            val state = _emojiMedia.value
            if (state.selectedTab == 1) loadGifs(query = state.searchQuery.ifEmpty { "funny" })
            if (state.selectedTab == 2) loadEmojiStickers(query = state.searchQuery.ifEmpty { null })
        }
    }

    fun setSelectedStickerCategory(category: String) {
        _emojiMedia.value = _emojiMedia.value.copy(selectedStickerCategory = category)
        loadEmojiStickers(query = null)
    }

    fun loadEmojiStickers(query: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _emojiMedia.value = _emojiMedia.value.copy(isStickersLoading = true)
            val ctx = com.example.PanaApplication.instance
            val result = if (!query.isNullOrEmpty()) {
                StickerRepository.getStickers(ctx, query)
            } else {
                when (_emojiMedia.value.selectedStickerCategory) {
                    "recents" -> StickerRepository.getRecentStickers(ctx)
                    "favorites" -> StickerRepository.getFavoriteStickers(ctx)
                    else -> StickerRepository.getStickers(ctx, null)
                }
            }
            _emojiMedia.value = _emojiMedia.value.copy(stickers = result, isStickersLoading = false)
        }
    }

    fun loadGifs(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _emojiMedia.value = _emojiMedia.value.copy(isGifsLoading = true)
            val result = StickerRepository.searchGifs(query = query.ifEmpty { "funny" })
            _emojiMedia.value = _emojiMedia.value.copy(gifs = result, isGifsLoading = false)
        }
    }

    private var emojiSearchDebounceJob: kotlinx.coroutines.Job? = null

    fun clearActiveChat() {
        com.example.data.supabase.SupabaseClient.activeChatId = null
        com.example.data.supabase.SupabaseClient.isChatScreenActive = false
        currentChatId = null
        currentOtherUserId = null
        loadJob?.cancel()
        loadJob = null
        chatJob?.cancel()
        chatJob = null
        typingJob?.cancel()
        typingJob = null
        _typingUsers.value = emptyList()
    }

    fun sendMessage(text: String, replyToId: String? = null, context: Context? = null) {
        val chatId = currentChatId ?: return
        val otherId = currentOtherUserId
        if (context != null) {
            com.example.service.NotificationHelper.playOutgoingSound(context)
        }
        val correlationId = ChatDiagnostics.correlationId()
        val startedAtMs = System.currentTimeMillis()
        ChatDiagnostics.started(
            name = "chat.message.send.started",
            correlationId = correlationId,
            details = "reply=${replyToId != null}, ghost=${_isGhostMode.value}"
        )
        viewModelScope.launch {
            val result = messagesRepo.sendMessage(chatId, text, replyToId = replyToId, receiverUid = otherId, isGhost = _isGhostMode.value)
            if (result.isSuccess) {
                _inputMessage.value = ""
                ChatDiagnostics.completed(
                    name = "chat.message.send.completed",
                    startedAtMs = startedAtMs,
                    correlationId = correlationId
                )
            } else {
                ChatDiagnostics.failed(
                    name = "chat.message.send.failed",
                    startedAtMs = startedAtMs,
                    correlationId = correlationId,
                    throwable = result.exceptionOrNull()
                )
                context?.let {
                    Toast.makeText(
                        it,
                        "No se pudo enviar. El mensaje quedó disponible para reintentar.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            // Replying implicitly confirms reading: with "Lectura Inteligente" OFF
            // this is the only moment the read ack is sent to the server.
            if (result.isSuccess && !isSmartReadEnabled()) {
                try {
                    messagesRepo.markThreadRead(chatId)
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error marking read on reply", e)
                }
            }
        }
    }

    fun sendPlaylistMessage(payload: com.example.media.playlist.PlaylistSharePayload) {
        val chatId = currentChatId ?: return
        val otherId = currentOtherUserId
        val json = Json.encodeToString(payload)
        val correlationId = ChatDiagnostics.correlationId()
        val startedAtMs = System.currentTimeMillis()
        ChatDiagnostics.started(
            name = "chat.playlist.send.started",
            correlationId = correlationId,
            details = "hasCover=${!payload.coverPath.isNullOrBlank()}, hasDuration=${payload.durationMs > 0}"
        )
        viewModelScope.launch {
            val result = messagesRepo.sendMessage(
                chatId = chatId,
                content = json,
                receiverUid = otherId,
                messageType = "playlist",
                mediaUrl = payload.coverPath,
                duration = payload.durationMs
            )
            if (result.isSuccess) {
                ChatDiagnostics.completed(name = "chat.playlist.send.completed", startedAtMs = startedAtMs, correlationId = correlationId)
            } else {
                ChatDiagnostics.failed(name = "chat.playlist.send.failed", startedAtMs = startedAtMs, correlationId = correlationId, throwable = result.exceptionOrNull())
            }
        }
    }

    fun deleteMessageDefinitively(id: String) {
        val correlationId = ChatDiagnostics.correlationId()
        val startedAtMs = System.currentTimeMillis()
        ChatDiagnostics.started(name = "chat.message.delete_definitive.started", correlationId = correlationId)
        viewModelScope.launch {
            try {
                val result = messagesRepo.deleteMessageDefinitively(id)
                if (result.isSuccess) {
                    ChatDiagnostics.completed(name = "chat.message.delete_definitive.completed", startedAtMs = startedAtMs, correlationId = correlationId)
                } else {
                    ChatDiagnostics.failed(name = "chat.message.delete_definitive.failed", startedAtMs = startedAtMs, correlationId = correlationId, throwable = result.exceptionOrNull())
                }
            } catch (e: Exception) {
                ChatDiagnostics.failed(name = "chat.message.delete_definitive.failed", startedAtMs = startedAtMs, correlationId = correlationId, throwable = e)
            }
        }
    }

    fun deleteMessageForMe(id: String) {
        val correlationId = ChatDiagnostics.correlationId()
        val startedAtMs = System.currentTimeMillis()
        ChatDiagnostics.started(name = "chat.message.delete_for_me.started", correlationId = correlationId)
        viewModelScope.launch {
            try {
                val result = messagesRepo.deleteMessageForMe(id)
                if (result.isSuccess) {
                    ChatDiagnostics.completed(name = "chat.message.delete_for_me.completed", startedAtMs = startedAtMs, correlationId = correlationId)
                } else {
                    ChatDiagnostics.failed(name = "chat.message.delete_for_me.failed", startedAtMs = startedAtMs, correlationId = correlationId, throwable = result.exceptionOrNull())
                }
            } catch (e: Exception) {
                ChatDiagnostics.failed(name = "chat.message.delete_for_me.failed", startedAtMs = startedAtMs, correlationId = correlationId, throwable = e)
            }
        }
    }

    fun deleteMessageForEveryone(id: String) {
        val correlationId = ChatDiagnostics.correlationId()
        val startedAtMs = System.currentTimeMillis()
        ChatDiagnostics.started(name = "chat.message.delete_for_everyone.started", correlationId = correlationId)
        viewModelScope.launch {
            try {
                val result = messagesRepo.deleteMessageForEveryone(id)
                if (result.isSuccess) {
                    ChatDiagnostics.completed(name = "chat.message.delete_for_everyone.completed", startedAtMs = startedAtMs, correlationId = correlationId)
                } else {
                    ChatDiagnostics.failed(name = "chat.message.delete_for_everyone.failed", startedAtMs = startedAtMs, correlationId = correlationId, throwable = result.exceptionOrNull())
                }
            } catch (e: Exception) {
                ChatDiagnostics.failed(name = "chat.message.delete_for_everyone.failed", startedAtMs = startedAtMs, correlationId = correlationId, throwable = e)
            }
        }
    }

    fun addReaction(msgId: String, emoji: String) {
        val chatId = currentChatId ?: return
        val myUserId = com.example.data.supabase.SupabaseClient.currentUser?.id ?: return
        val correlationId = ChatDiagnostics.correlationId()
        val startedAtMs = System.currentTimeMillis()
        ChatDiagnostics.started(name = "chat.reaction.add.started", correlationId = correlationId, details = "hasEmoji=${!emoji.isNullOrBlank()}")
        viewModelScope.launch {
            try {
                messagesRepo.saveReaction(msgId, chatId, myUserId, emoji)
                ChatDiagnostics.completed(name = "chat.reaction.add.completed", startedAtMs = startedAtMs, correlationId = correlationId)
            } catch (e: Exception) {
                ChatDiagnostics.failed(name = "chat.reaction.add.failed", startedAtMs = startedAtMs, correlationId = correlationId, throwable = e)
            }
        }
    }

    fun clearChat() {
        val chatId = currentChatId ?: return
        val current = _uiState.value
        val count = (current as? ChatUiState.Success)?.messages?.size ?: 0
        if (current is ChatUiState.Success) {
            _uiState.value = current.copy(messages = emptyList())
        }
        val correlationId = ChatDiagnostics.correlationId()
        val startedAtMs = System.currentTimeMillis()
        ChatDiagnostics.started(name = "chat.clear.started", correlationId = correlationId, details = "count=$count")
        viewModelScope.launch {
            try {
                messagesRepo.clearChat(chatId)
                ChatDiagnostics.completed(name = "chat.clear.completed", startedAtMs = startedAtMs, correlationId = correlationId)
            } catch (e: Exception) {
                ChatDiagnostics.failed(name = "chat.clear.failed", startedAtMs = startedAtMs, correlationId = correlationId, throwable = e)
            }
        }
    }

    fun deleteChat(onBack: () -> Unit) {
        val chatId = currentChatId ?: return
        val correlationId = ChatDiagnostics.correlationId()
        val startedAtMs = System.currentTimeMillis()
        ChatDiagnostics.started(name = "chat.delete.started", correlationId = correlationId)
        viewModelScope.launch {
            val result = chatsRepo.deleteChatLocallyAndRemotely(chatId)
            if (result.isSuccess) {
                ChatDiagnostics.completed(name = "chat.delete.completed", startedAtMs = startedAtMs, correlationId = correlationId)
            } else {
                ChatDiagnostics.failed(name = "chat.delete.failed", startedAtMs = startedAtMs, correlationId = correlationId, throwable = result.exceptionOrNull())
            }
            onBack()
        }
    }
    
    fun toggleFavorite(msg: Message) {
        val correlationId = ChatDiagnostics.correlationId()
        val startedAtMs = System.currentTimeMillis()
        ChatDiagnostics.started(name = "chat.favorite.toggle.started", correlationId = correlationId)
        viewModelScope.launch {
            try {
                messagesRepo.toggleMessageFavorite(msg)
                ChatDiagnostics.completed(name = "chat.favorite.toggle.completed", startedAtMs = startedAtMs, correlationId = correlationId)
            } catch (e: Exception) {
                ChatDiagnostics.failed(name = "chat.favorite.toggle.failed", startedAtMs = startedAtMs, correlationId = correlationId, throwable = e)
            }
        }
    }

    fun toggleGhostMode() {
        _isGhostMode.value = !_isGhostMode.value
    }
    
    fun consumeGhostMessage(id: String) {
        viewModelScope.launch {
            messagesRepo.consumeGhostMessage(id)
        }
    }

    fun onVoiceGestureEvent(
        event: com.example.ui.components.chat.voice.VoiceGestureEvent,
        context: android.content.Context,
        replyToId: String? = null,
        fallbackDurationSeconds: Int = 0,
        onProgress: (Boolean) -> Unit = {}
    ) {
        when (event) {
            com.example.ui.components.chat.voice.VoiceGestureEvent.StartRecording -> {
                if (_recordState.value != RecordState.IDLE) return
                val file = voiceController.start()
                if (file != null) {
                    _recordState.value = RecordState.RECORDING
                    com.example.util.PanaLinkSoundManager.play(context, com.example.util.PanaSoundEvent.VOICE_START)
                    ChatDiagnostics.event(name = "chat.voice.record.started")
                }
            }
            com.example.ui.components.chat.voice.VoiceGestureEvent.LockRecording -> {
                if (_recordState.value == RecordState.RECORDING) {
                    _recordState.value = RecordState.LOCKED_RECORDING
                    com.example.util.PanaLinkSoundManager.play(context, com.example.util.PanaSoundEvent.VOICE_LOCK)
                    ChatDiagnostics.event(name = "chat.voice.record.locked")
                }
            }
            com.example.ui.components.chat.voice.VoiceGestureEvent.CancelRecording -> {
                if (_recordState.value == RecordState.RECORDING || _recordState.value == RecordState.LOCKED_RECORDING) {
                    voiceController.cancel()
                    _recordState.value = RecordState.IDLE
                    _voiceAmplitudes.value = emptyList()
                    com.example.util.PanaLinkSoundManager.play(context, com.example.util.PanaSoundEvent.VOICE_CANCEL)
                    ChatDiagnostics.event(name = "chat.voice.record.cancelled")
                }
            }
            com.example.ui.components.chat.voice.VoiceGestureEvent.FinishRecording -> {
                if (_recordState.value == RecordState.LOCKED_RECORDING) return // User must click send or stop in locked mode

                // Estilo Telegram: soltar el dedo mientras se graba envía directamente.
                // Preescuchar queda disponible en el modo manos libres (bloqueado) via StopAndPreviewRecording.
                if (_recordState.value == RecordState.RECORDING) {
                    val result = voiceController.stopAndValidate(fallbackDurationSeconds = fallbackDurationSeconds)
                    _voiceAmplitudes.value = emptyList()

                    if (result is com.example.ui.components.chat.voice.VoiceRecordingResult.Success) {
                        previewFile = result.file
                        previewDurationSeconds = result.durationSeconds
                        _previewWaveform.value = emptyList()
                        sendPreviewRecording(context, replyToId, onProgress)
                    } else {
                        _recordState.value = RecordState.IDLE
                    }
                }
            }
            com.example.ui.components.chat.voice.VoiceGestureEvent.PauseRecording -> {
                voiceController.pause()
                ChatDiagnostics.event(name = "chat.voice.record.paused")
            }
            com.example.ui.components.chat.voice.VoiceGestureEvent.ResumeRecording -> {
                voiceController.resume()
                ChatDiagnostics.event(name = "chat.voice.record.resumed")
            }
            com.example.ui.components.chat.voice.VoiceGestureEvent.SendLockedRecording -> {
                if (_recordState.value == RecordState.LOCKED_RECORDING) {
                    val result = voiceController.stopAndValidate(fallbackDurationSeconds = fallbackDurationSeconds)
                    _voiceAmplitudes.value = emptyList()
                    if (result is com.example.ui.components.chat.voice.VoiceRecordingResult.Success) {
                        previewFile = result.file
                        previewDurationSeconds = result.durationSeconds
                        _previewWaveform.value = emptyList()
                        sendPreviewRecording(context, replyToId, onProgress)
                    } else {
                        _recordState.value = RecordState.IDLE
                    }
                }
            }
            com.example.ui.components.chat.voice.VoiceGestureEvent.StopAndPreviewRecording -> {
                if (_recordState.value == RecordState.RECORDING || _recordState.value == RecordState.LOCKED_RECORDING) {
                    val result = voiceController.stopAndValidate(fallbackDurationSeconds = fallbackDurationSeconds)
                    _voiceAmplitudes.value = emptyList()
                    if (result is com.example.ui.components.chat.voice.VoiceRecordingResult.Success) {
                        previewFile = result.file
                        previewDurationSeconds = result.durationSeconds
                        _recordState.value = RecordState.PREVIEWING
                        ChatDiagnostics.event(name = "chat.voice.record.previewed", details = "duration=${result.durationSeconds}s")
                        viewModelScope.launch {
                            _previewWaveform.value = com.example.ui.components.chat.voice.AudioWaveformAnalyzer.analyze(result.file)
                        }
                    } else {
                        _recordState.value = RecordState.IDLE
                    }
                }
            }
        }
    }

    fun cancelPreviewRecording(context: android.content.Context? = null) {
        previewAudioPlayer.release()
        try {
            previewFile?.delete()
        } catch (_: Exception) {}
        previewFile = null
        _previewWaveform.value = emptyList()
        _recordState.value = RecordState.IDLE
        _isPreviewSending.value = false
        if (context != null) {
            com.example.util.PanaLinkSoundManager.play(context, com.example.util.PanaSoundEvent.VOICE_CANCEL)
        }
    }

    fun getRecordingElapsedSeconds(): Int {
        return (voiceController.getElapsedMillis() / 1000).toInt()
    }

    fun cleanOldCache(context: android.content.Context) {
        viewModelScope.launch {
            com.example.util.VoiceNoteCacheCleaner.cleanOldVoiceNotes(context)
        }
    }
    
    fun pausePreviewAudio() {
        previewAudioPlayer.pause()
    }

    fun playPreviewAudio() {
        val file = previewFile ?: return
        if (previewAudioPlayer.playerState.value.isPrepared) {
            previewAudioPlayer.resume()
        } else {
            previewAudioPlayer.play(file.absolutePath)
        }
    }

    fun togglePreviewSpeed() {
        val currentSpeed = previewPlayerState.value.playbackSpeed
        val nextSpeed = when (currentSpeed) {
            1f -> 1.5f
            1.5f -> 2f
            else -> 1f
        }
        previewAudioPlayer.setSpeed(nextSpeed)
    }

    fun seekPreviewAudio(posMs: Long) {
        previewAudioPlayer.seekTo(posMs.toInt())
    }

    fun sendPreviewRecording(
        context: android.content.Context,
        replyToId: String? = null,
        onProgress: (Boolean) -> Unit = {}
    ) {
        val file = previewFile
        if (_isPreviewSending.value || _recordState.value == RecordState.SENDING || file == null || !file.exists()) {
            Log.w("ChatViewModel", "sendPreviewRecording ignored: file null/missing or already sending.")
            return
        }
        val correlationId = ChatDiagnostics.correlationId()
        val startedAtMs = System.currentTimeMillis()
        ChatDiagnostics.started(name = "chat.voice.send.queued", correlationId = correlationId, details = "typeLabel=Voice")
        _isPreviewSending.value = true
        _recordState.value = RecordState.SENDING
        com.example.util.PanaLinkSoundManager.play(context, com.example.util.PanaSoundEvent.VOICE_SEND)
        previewAudioPlayer.release()
        
        val fileToSend = file
        previewFile = null
        
        uploadAndSendMedia(
            file = fileToSend,
            mimeType = "audio/mp4",
            typeLabel = "Voice",
            replyToId = replyToId,
            context = context,
            onProgress = { isUploading ->
                onProgress(isUploading)
                if (!isUploading) {
                    _isPreviewSending.value = false
                    _recordState.value = RecordState.IDLE
                }
            }
        )
    }
    
    private fun getFileFromUri(context: Context, uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(context.cacheDir, "upload_${UUID.randomUUID()}")
            val outputStream = FileOutputStream(tempFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Envia varias imagenes como UN solo mensaje album (grid), no por burbujas sueltas. */
    fun uploadAndSendImageAlbum(
        uris: List<android.net.Uri>,
        replyToId: String?,
        context: android.content.Context,
        onProgress: (Boolean) -> Unit
    ) {
        val chatId = currentChatId ?: return
        val correlationId = ChatDiagnostics.correlationId()
        val startedAtMs = System.currentTimeMillis()
        ChatDiagnostics.started(name = "chat.album.send.started", correlationId = correlationId)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            onProgress(true)
            val result = messagesRepo.sendImageAlbum(
                chatId = chatId,
                context = context,
                uris = uris,
                replyToId = replyToId,
                receiverId = currentOtherUserId
            )
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onProgress(false)
                if (result.isFailure) {
                    ChatDiagnostics.failed(name = "chat.album.send.failed", startedAtMs = startedAtMs, correlationId = correlationId, throwable = result.exceptionOrNull())
                    android.widget.Toast.makeText(context, "Error enviando album", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    ChatDiagnostics.completed(name = "chat.album.send.completed", startedAtMs = startedAtMs, correlationId = correlationId)
                }
            }
        }
    }

    fun uploadAndSendMedia(
        uri: android.net.Uri? = null,
        file: java.io.File? = null,
        mimeType: String,
        typeLabel: String,
        replyToId: String?,
        context: android.content.Context,
        fileName: String? = null,
        onProgress: (Boolean) -> Unit
    ) {
        val chatId = currentChatId ?: return
        val correlationId = ChatDiagnostics.correlationId()
        val startedAtMs = System.currentTimeMillis()
        val isVoice = mimeType.startsWith("audio/") || typeLabel == "Voice"
        if (isVoice) {
            ChatDiagnostics.started(name = "chat.voice.send.started", correlationId = correlationId)
        }
        ChatDiagnostics.started(name = "chat.media.send.started", correlationId = correlationId)

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            onProgress(true)

            val result = messagesRepo.sendMultimediaMessage(
                chatId = chatId,
                context = context,
                sourceUri = uri,
                sourceFile = file,
                mimeType = mimeType,
                typeLabel = typeLabel,
                content = "[$typeLabel]",
                replyToId = replyToId,
                isGhost = _isGhostMode.value,
                receiverId = currentOtherUserId
            )

            val message = result.getOrNull()
            val finalMessage = if (message != null) {
                kotlinx.coroutines.withTimeoutOrNull(120_000L) {
                    messagesRepo.getMessagesFlow(chatId)
                        .mapNotNull { messages ->
                            messages.firstOrNull { candidate ->
                                candidate.clientMessageUuid == message.clientMessageUuid &&
                                    candidate.status in setOf("sent", "delivered", "seen", "failed")
                            }
                        }
                        .first()
                }
            } else {
                null
            }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onProgress(false)

                when {
                    result.isFailure -> {
                        if (isVoice) {
                            ChatDiagnostics.failed(name = "chat.voice.send.failed", startedAtMs = startedAtMs, correlationId = correlationId, throwable = result.exceptionOrNull())
                        }
                        ChatDiagnostics.failed(name = "chat.media.send.failed", startedAtMs = startedAtMs, correlationId = correlationId, throwable = result.exceptionOrNull())
                        try {
                            android.widget.Toast.makeText(
                                context,
                                "Error procesando archivo",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        } catch (t: Throwable) {}
                    }
                    finalMessage?.status == "failed" -> {
                        if (isVoice) {
                            ChatDiagnostics.failed(name = "chat.voice.send.failed", startedAtMs = startedAtMs, correlationId = correlationId)
                        }
                        ChatDiagnostics.failed(name = "chat.media.send.failed", startedAtMs = startedAtMs, correlationId = correlationId)
                        try {
                            android.widget.Toast.makeText(
                                context,
                                "No se pudo enviar el archivo",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        } catch (t: Throwable) {}
                    }
                    finalMessage == null && message != null -> {
                        if (isVoice) {
                            ChatDiagnostics.failed(name = "chat.voice.send.timeout", startedAtMs = startedAtMs, correlationId = correlationId)
                        }
                        ChatDiagnostics.failed(name = "chat.media.send.timeout", startedAtMs = startedAtMs, correlationId = correlationId)
                        // The worker remains persisted in WorkManager. Do not
                        // leave a screen-level spinner running forever if the
                        // device is offline or the upload is unusually slow.
                        Log.w(
                            "ChatViewModel",
                            "Media send observer timed out for ${message.clientMessageUuid}; worker continues in background"
                        )
                    }
                    finalMessage != null -> {
                        if (isVoice) {
                            ChatDiagnostics.completed(name = "chat.voice.send.completed", startedAtMs = startedAtMs, correlationId = correlationId)
                        }
                        ChatDiagnostics.completed(name = "chat.media.send.completed", startedAtMs = startedAtMs, correlationId = correlationId)
                    }
                }
            }
        }
    }
    
    fun editMessage(id: String, newContent: String) {
        viewModelScope.launch {
            messagesRepo.editMessage(id, newContent)
        }
    }

fun sendSticker(url: String, preview: String?, replyToId: String?) {
        val chatId = currentChatId ?: return
        val otherUserId = currentOtherUserId
        viewModelScope.launch {
            // Local stickers (Panalink default pack / studio creations saved offline)
            // are uploaded to the CDN on first send so every contact can render them.
            val sendUrl = if (com.example.features.stickers.studio.PanalinkDefaultStickers.isLocalStickerPath(url)) {
                com.example.features.stickers.studio.PanalinkDefaultStickers.resolveRemoteUrl(context = com.example.PanaApplication.instance, localPath = url) ?: url
            } else url
            val isGif = sendUrl.lowercase().contains(".gif")
            val msgId = "temp_${java.util.UUID.randomUUID()}"
            val nowStr = com.example.data.supabase.SupabaseClient.getNowIsoString()
            val mType = if (isGif) "gif" else "sticker"
            val mMime = if (isGif) "image/gif" else "image/webp"

            // Optimistic UI for Sticker/GIF (local preview renders instantly).
            // IMPORTANTE: mediaUrl debe ser la URL DEFINITIVA (la subida al CDN
            // para stickers locales) para que la burbuja del emisor renderice la
            // misma imagen que recibirá el destinatario — si se usa la ruta local
            // original, la burbuja muestra "Sticker no disponible" cuando la ruta
            // temporal ya no existe tras el sync.
            val optimisticMsg = com.example.data.model.Message(
                id = msgId,
                chatId = chatId,
                senderId = com.example.data.supabase.SupabaseClient.currentUser?.id ?: "",
                content = if (isGif) "[GIF]" else "[Sticker]",
                createdAt = nowStr,
                status = "sending",
                replyToMessageId = replyToId,
                mediaUrl = sendUrl,
                thumbnailUrl = preview ?: sendUrl,
                mediaMime = mMime,
                messageType = mType,
                isGhost = _isGhostMode.value
            )
            messagesRepo.insertLocalMessage(optimisticMsg)

            messagesRepo.sendMessage(
                chatId = chatId,
                content = optimisticMsg.content ?: "",
                replyToId = replyToId,
                receiverUid = otherUserId,
                messageType = mType,
                mediaUrl = sendUrl,
                thumbnailUrl = preview ?: sendUrl,
                mediaMime = mMime,
                isGhost = _isGhostMode.value,
                messageId = msgId
            )
        }
    }

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isPinned = MutableStateFlow(false)
    val isPinned: StateFlow<Boolean> = _isPinned.asStateFlow()

    fun loadChatMuteStatus(chatId: String, context: Context) {
        val correlationId = ChatDiagnostics.correlationId()
        val startedAtMs = System.currentTimeMillis()
        ChatDiagnostics.started(name = "chat.mute.load.started", correlationId = correlationId)
        // Both muted and pinned state are loaded in a single local lookup,
        // so pin events are recorded alongside mute events (preserving the
        // original behavior where loadChatPinStatus delegates to this method).
        ChatDiagnostics.started(name = "chat.pin.load.started", correlationId = correlationId)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val chat = chatsRepo.getLocalChat(chatId)
                _isMuted.value = chat?.isMuted == true
                _isPinned.value = chat?.isPinned == true
                ChatDiagnostics.completed(name = "chat.mute.load.completed", startedAtMs = startedAtMs, correlationId = correlationId)
                ChatDiagnostics.completed(name = "chat.pin.load.completed", startedAtMs = startedAtMs, correlationId = correlationId)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error loading mute/pin status: ${e.localizedMessage}")
                ChatDiagnostics.failed(name = "chat.mute.load.failed", startedAtMs = startedAtMs, correlationId = correlationId, throwable = e)
                ChatDiagnostics.failed(name = "chat.pin.load.failed", startedAtMs = startedAtMs, correlationId = correlationId, throwable = e)
            }
        }
    }

    fun loadChatPinStatus(chatId: String, context: Context) {
        loadChatMuteStatus(chatId, context)
    }

    fun muteChat(chatId: String, muted: Boolean) {
        _isMuted.value = muted
        val correlationId = ChatDiagnostics.correlationId()
        val startedAtMs = System.currentTimeMillis()
        ChatDiagnostics.started(name = "chat.mute.toggle.started", correlationId = correlationId, details = "muted=$muted")
        viewModelScope.launch(Dispatchers.IO) {
            val result = chatsRepo.muteChat(chatId, muted)
            if (result.isSuccess) {
                ChatDiagnostics.completed(name = "chat.mute.toggle.completed", startedAtMs = startedAtMs, correlationId = correlationId)
            } else {
                ChatDiagnostics.failed(name = "chat.mute.toggle.failed", startedAtMs = startedAtMs, correlationId = correlationId, throwable = result.exceptionOrNull())
            }
        }
    }

    fun pinChat(chatId: String, pinned: Boolean) {
        _isPinned.value = pinned
        val correlationId = ChatDiagnostics.correlationId()
        val startedAtMs = System.currentTimeMillis()
        ChatDiagnostics.started(name = "chat.pin.toggle.started", correlationId = correlationId, details = "pinned=$pinned")
        viewModelScope.launch(Dispatchers.IO) {
            val result = chatsRepo.pinChat(chatId, pinned)
            if (result.isSuccess) {
                ChatDiagnostics.completed(name = "chat.pin.toggle.completed", startedAtMs = startedAtMs, correlationId = correlationId)
            } else {
                ChatDiagnostics.failed(name = "chat.pin.toggle.failed", startedAtMs = startedAtMs, correlationId = correlationId, throwable = result.exceptionOrNull())
            }
        }
    }

    fun saveSticker(stickerUrl: String, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = com.example.data.model.StickerResult(url = stickerUrl, preview = stickerUrl)
            StickerRepository.saveSticker(context, result)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Sticker guardado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleStickerFavorite(stickerUrl: String, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = com.example.data.model.StickerResult(url = stickerUrl, preview = stickerUrl)
            val isFav = StickerRepository.toggleFavoriteSticker(context, result)
            withContext(Dispatchers.Main) {
                val msg = if (isFav) "Añadido a favoritos" else "Quitado de favoritos"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            voiceController.release()
        } catch (_: Exception) {}
        try {
            previewAudioPlayer.release()
        } catch (_: Exception) {}
        try {
            previewFile?.delete()
            previewFile = null
        } catch (_: Exception) {}
    }
}
