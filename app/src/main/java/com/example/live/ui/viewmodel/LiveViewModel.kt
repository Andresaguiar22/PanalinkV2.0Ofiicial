package com.example.live.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.supabase.SupabaseClient
import com.example.live.data.remote.LiveEngagementRealtimeManager
import com.example.live.data.remote.LivePresenceManager
import com.example.live.data.remote.LiveRealtimeManager
import com.example.live.data.repository.LiveModerationRepositoryImpl
import com.example.live.data.repository.LiveReactionsRepositoryImpl
import com.example.live.data.repository.LiveReportRepositoryImpl
import com.example.live.data.repository.LiveRepositoryImpl
import com.example.live.domain.model.LiveComment
import com.example.live.domain.model.LiveGift
import com.example.live.domain.model.LiveGiftEvent
import com.example.live.domain.model.LivePresenceEvent
import com.example.live.domain.model.LiveStream
import com.example.live.domain.model.LiveStreamStats
import com.example.live.domain.repository.LiveModerationRepository
import com.example.live.domain.repository.LiveReactionsRepository
import com.example.live.domain.repository.LiveReportRepository
import com.example.live.domain.repository.LiveRepository
import com.example.live.domain.repository.LiveTokenResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

/** Pulso de animación para un regalo recién llegado. */
data class LiveGiftPulse(
    val id: Long,
    val code: String?,
    val emoji: String,
    val name: String,
    val quantity: Int,
    val coinsTotal: Long,
    val senderId: String
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

    private val _likeCount = MutableStateFlow(0)
    val likeCount: StateFlow<Int> = _likeCount.asStateFlow()

    private val _giftCoins = MutableStateFlow(0L)
    val giftCoins: StateFlow<Long> = _giftCoins.asStateFlow()

    private val _giftCount = MutableStateFlow(0)
    val giftCount: StateFlow<Int> = _giftCount.asStateFlow()

    private val _walletBalance = MutableStateFlow<Int?>(null)
    val walletBalance: StateFlow<Int?> = _walletBalance.asStateFlow()

    private val _giftCatalog = MutableStateFlow<List<LiveGift>>(emptyList())
    val giftCatalog: StateFlow<List<LiveGift>> = _giftCatalog.asStateFlow()

    private val _giftFeed = MutableStateFlow<List<LiveGiftEvent>>(emptyList())
    val giftFeed: StateFlow<List<LiveGiftEvent>> = _giftFeed.asStateFlow()

    private val _giftPulse = MutableStateFlow<LiveGiftPulse?>(null)
    val giftPulse: StateFlow<LiveGiftPulse?> = _giftPulse.asStateFlow()

    /** Contador monótono: cada incremento representa una reacción nueva (corazones). */
    private val _reactionPulse = MutableStateFlow(0L)
    val reactionPulse: StateFlow<Long> = _reactionPulse.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _sendingGift = MutableStateFlow(false)
    val sendingGift: StateFlow<Boolean> = _sendingGift.asStateFlow()

    private val _presentUsers = MutableStateFlow<List<String>>(emptyList())
    val presentUsers: StateFlow<List<String>> = _presentUsers.asStateFlow()

    private var pendingLikes = 0
    private var likeFlushJob: Job? = null
    private var presenceReportJob: Job? = null
    private var lastReportedViewers = -1
    private var lastLocalGiftCode: String? = null
    private var lastLocalGiftAt = 0L
    private var activeStreamId: String? = null

    private val feedRealtimeManager = LiveRealtimeManager(
        onLiveStreamChanged = { scheduleFeedReload() }
    )

    private var commentsRealtimeManager: LiveRealtimeManager? = null
    private var streamStatusRealtimeManager: LiveRealtimeManager? = null
    private var presenceManager: LivePresenceManager? = null
    private var engagementRealtimeManager: LiveEngagementRealtimeManager? = null
    private var pulseCollectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var feedReloadJob: Job? = null

    init {
        loadActiveLives()
        feedRealtimeManager.start()
    }

    /** Recarga el feed con retardo para no golpear la API en cada evento Realtime. */
    private fun scheduleFeedReload() {
        feedReloadJob?.cancel()
        feedReloadJob = viewModelScope.launch {
            delay(FEED_RELOAD_DEBOUNCE_MS)
            loadActiveLives()
        }
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
            val result = reportRepository.report(streamId, reportedUserId, commentId, reason)
            _notice.value = if (result.isSuccess) "Reporte enviado" else "No se pudo enviar el reporte"
        }
    }

    /** Doble toque / botón de corazón: suma un me gusta real (agrupado antes de enviarse). */
    fun tapLike(streamId: String) {
        if (streamId.isBlank()) return
        _likeCount.value = _likeCount.value + 1
        pendingLikes += 1
        viewModelScope.launch { reactionsRepository.sendReaction(streamId) }
        if (likeFlushJob?.isActive != true) {
            likeFlushJob = viewModelScope.launch {
                while (pendingLikes > 0) {
                    delay(LIKE_FLUSH_INTERVAL_MS)
                    flushLikes(streamId)
                }
            }
        }
    }

    private suspend fun flushLikes(streamId: String) {
        val sending = pendingLikes
        if (sending <= 0) return
        pendingLikes = 0
        repository.sendLikes(streamId, sending)
            .onSuccess { serverCount ->
                _likeCount.value = maxOf(serverCount + pendingLikes, _likeCount.value)
            }
            .onFailure {
                pendingLikes += sending
            }
    }

    fun loadEngagement(streamId: String) {
        viewModelScope.launch {
            repository.getStats(streamId).onSuccess { applyStats(it, initial = true) }
        }
        viewModelScope.launch {
            repository.getGiftCatalog().onSuccess { _giftCatalog.value = it }
        }
        viewModelScope.launch {
            repository.getWalletBalance().onSuccess { _walletBalance.value = it }
        }
    }

    fun refreshWallet() {
        viewModelScope.launch {
            repository.getWalletBalance().onSuccess { _walletBalance.value = it }
        }
    }

    fun sendGift(streamId: String, gift: LiveGift, quantity: Int = 1) {
        if (_sendingGift.value) return
        _sendingGift.value = true
        viewModelScope.launch {
            val result = repository.sendGift(streamId, gift.code, quantity)
            val value = result.getOrNull()
            if (value == null) {
                _notice.value = result.exceptionOrNull()?.message ?: "No se pudo enviar el regalo"
            } else {
                _walletBalance.value = value.balance
                if (value.ok) {
                    lastLocalGiftCode = gift.code
                    lastLocalGiftAt = System.currentTimeMillis()
                    _notice.value = "Enviaste ${gift.emoji} ${gift.name} x$quantity"
                    _giftPulse.value = LiveGiftPulse(
                        id = System.currentTimeMillis(),
                        code = gift.code,
                        emoji = gift.emoji,
                        name = gift.name,
                        quantity = quantity,
                        coinsTotal = value.total.toLong(),
                        senderId = SupabaseClient.currentUser?.id ?: ""
                    )
                } else {
                    _notice.value = when (value.reason) {
                        "insufficient_funds" -> "Saldo insuficiente: te quedan ${value.balance} monedas"
                        else -> "No se pudo enviar el regalo"
                    }
                }
            }
            _sendingGift.value = false
        }
    }

    fun clearNotice() {
        _notice.value = null
    }

    private fun applyStats(stats: LiveStreamStats, initial: Boolean = false) {
        _likeCount.value = maxOf(stats.likeCount + pendingLikes, _likeCount.value)
        _giftCoins.value = stats.giftCoins
        _giftCount.value = stats.giftCount
        if (initial && stats.viewerCount > 0 && _presentUsers.value.isEmpty()) {
            _viewerCount.value = maxOf(_viewerCount.value, stats.viewerCount)
        }
    }

    private fun applyGiftEvent(event: LiveGiftEvent, fromRealtime: Boolean) {
        _giftFeed.value = (listOf(event) + _giftFeed.value).take(MAX_GIFT_FEED)
        val mine = event.senderId == SupabaseClient.currentUser?.id
        val echoOfLocalSend = fromRealtime && mine &&
            event.giftCode == lastLocalGiftCode &&
            System.currentTimeMillis() - lastLocalGiftAt < LOCAL_GIFT_ECHO_WINDOW_MS
        if (!echoOfLocalSend) {
            val gift = _giftCatalog.value.firstOrNull { it.code == event.giftCode }
            _giftPulse.value = LiveGiftPulse(
                id = System.currentTimeMillis(),
                code = gift?.code,
                emoji = gift?.emoji ?: DEFAULT_GIFT_EMOJI,
                name = gift?.name ?: event.giftCode,
                quantity = event.quantity,
                coinsTotal = event.coinsTotal,
                senderId = event.senderId
            )
        }
    }

    fun startStreamSession(streamId: String, isBroadcaster: Boolean = false) {
        activeStreamId = streamId
        val userId = SupabaseClient.currentUser?.id ?: "user_${System.currentTimeMillis()}"

        loadEngagement(streamId)

        if (!isBroadcaster) {
            // Registra una sola vez mi entrada al directo como evento real del chat.
            viewModelScope.launch { repository.registerJoin(streamId) }
        }

        presenceManager?.stop()
        presenceManager = LivePresenceManager(streamId, userId).apply { start() }
        presenceManager?.let { manager ->
            viewModelScope.launch {
                manager.viewerCount.collect { count ->
                    _viewerCount.value = count
                    if (isBroadcaster) {
                        maybeReportViewerCount(streamId, count)
                    }
                }
            }
            viewModelScope.launch {
                manager.presenceEvents.collect { event -> handlePresenceEvent(event) }
            }
        }

        // Heartbeat del HOST: mantiene last_seen_at fresco para que el auto-end por
        // TTL (pg_cron `live-auto-end-stale`) no tumbe un live sano. Cada 25 s.
        // Solo para el broadcaster: un viewer no debe sostener la sala.
        heartbeatJob?.cancel()
        if (isBroadcaster) {
            heartbeatJob = viewModelScope.launch {
                // Lanzar inmediatamente para poblar last_seen_at al empezar.
                try { repository.sendHeartbeat(streamId) } catch (_: Exception) {}
                while (true) {
                    delay(HEARTBEAT_INTERVAL_MS)
                    try { repository.sendHeartbeat(streamId) } catch (_: Exception) {}
                }
            }
        }

        pulseCollectJob?.cancel()
        pulseCollectJob = viewModelScope.launch {
            reactionsRepository.reactionEvents.collect {
                _reactionPulse.value = _reactionPulse.value + 1
            }
        }

        commentsRealtimeManager?.stop()
        commentsRealtimeManager = LiveRealtimeManager(
            onLiveStreamChanged = {},
            streamId = streamId,
            onCommentReceived = { comment -> upsertComment(comment) },
            onCommentUpdated = { comment ->
                if (comment.isDeleted) {
                    _comments.value = _comments.value.filter { it.id != comment.id }
                } else {
                    upsertComment(comment)
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
            },
            liveStreamId = streamId
        )
        streamStatusRealtimeManager?.start()

        engagementRealtimeManager?.stop()
        engagementRealtimeManager = LiveEngagementRealtimeManager(
            streamId = streamId,
            onStats = { applyStats(it) },
            onGiftEvent = { applyGiftEvent(it, fromRealtime = true) }
        )
        engagementRealtimeManager?.start()

        reactionsRepository.startListening(streamId)
    }

    private fun upsertComment(comment: LiveComment) {
        if (comment.isDeleted) {
            _comments.value = _comments.value.filter { it.id != comment.id }
            return
        }
        val current = _comments.value.toMutableList()
        val index = current.indexOfFirst { it.id == comment.id }
        if (index >= 0) {
            current[index] = comment
        } else {
            current.add(comment)
        }
        _comments.value = current
    }

    /** Publica el conteo de espectadores (con throttle) para que el feed lo muestre. */
    private fun maybeReportViewerCount(streamId: String, count: Int) {
        if (count == lastReportedViewers) return
        if (presenceReportJob?.isActive == true) return
        lastReportedViewers = count
        presenceReportJob = viewModelScope.launch {
            repository.setViewerCount(streamId, count)
        }
    }

    private fun handlePresenceEvent(event: LivePresenceEvent) {
        val current = _presentUsers.value.toMutableList()
        when (event) {
            is LivePresenceEvent.Joined -> if (!current.contains(event.userId)) current.add(event.userId)
            is LivePresenceEvent.Left -> current.remove(event.userId)
        }
        _presentUsers.value = current.takeLast(MAX_PRESENT_TRACKED)
    }

    fun stopStreamSession() {
        likeFlushJob?.cancel()
        likeFlushJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        val streamId = activeStreamId
        val pending = pendingLikes
        if (streamId != null && pending > 0) {
            pendingLikes = 0
            viewModelScope.launch { repository.sendLikes(streamId, pending) }
        }
        commentsRealtimeManager?.stop()
        commentsRealtimeManager = null
        streamStatusRealtimeManager?.stop()
        streamStatusRealtimeManager = null
        engagementRealtimeManager?.stop()
        engagementRealtimeManager = null
        pulseCollectJob?.cancel()
        pulseCollectJob = null
        presenceManager?.stop()
        presenceManager = null
        reactionsRepository.stopListening()
        activeStreamId = null
    }

    override fun onCleared() {
        super.onCleared()
        feedReloadJob?.cancel()
        feedRealtimeManager.stop()
        stopStreamSession()
    }

    companion object {
        private const val LIKE_FLUSH_INTERVAL_MS = 1500L
        private const val FEED_RELOAD_DEBOUNCE_MS = 2500L
        private const val HEARTBEAT_INTERVAL_MS = 25_000L
        private const val LOCAL_GIFT_ECHO_WINDOW_MS = 4000L
        private const val MAX_GIFT_FEED = 30
        private const val MAX_PRESENT_TRACKED = 50
        private const val DEFAULT_GIFT_EMOJI = "🎁"
    }
}
