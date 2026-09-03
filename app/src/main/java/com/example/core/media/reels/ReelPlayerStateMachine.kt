package com.example.core.media.reels

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ReelPlaybackState { IDLE, BUFFERING, READY, ENDED }

/**
 * Pure, unit-testable state machine for a single reel player page
 * (no Android/ExoPlayer references so it runs on the JVM test suite).
 *
 * Tracks the exact transitions the feed cares about:
 *  - BUFFERING -> READY (first frame available)
 *  - error -> automatic retry (up to [maxRetries])-> error UI
 *  - explicit retry reset (user taps Reintentar).
 */
data class ReelPlayerState(
    val isBuffering: Boolean = true,
    val hasError: Boolean = false,
    val isReady: Boolean = false,
    val retriesUsed: Int = 0
) {
    val isRecoveringWithRetry: Boolean get() = hasError == false && retriesUsed > 0 && !isReady
}

class ReelPlayerStateMachine(private val maxRetries: Int = 2) {
    private val _state = MutableStateFlow(ReelPlayerState())
    val state: StateFlow<ReelPlayerState> = _state.asStateFlow()

    val hasError: Boolean get() = _state.value.hasError
    val isBuffering: Boolean get() = _state.value.isBuffering

    fun onPlaybackStateChanged(playbackState: ReelPlaybackState) {
        val s = _state.value
        _state.value = when (playbackState) {
            ReelPlaybackState.BUFFERING -> s.copy(isBuffering = true, isReady = false, hasError = false, retriesUsed = 0)
            ReelPlaybackState.READY -> s.copy(isBuffering = false, isReady = true, hasError = false, retriesUsed = 0)
            ReelPlaybackState.ENDED -> s.copy(isBuffering = false, isReady = true)
            ReelPlaybackState.IDLE -> s.copy(isBuffering = true, isReady = false)
        }
    }

    /**
     * A playback failure.  Auto-retries up to [maxRetries] times, then flags
     * [ReelPlayerState.hasError] so the UI can show the elegant error view.claim
     * Returns true when the failure was absorbed by a retry (caller re-resolves
     * the URL and re-prepares the media); false when the UI must show the error.


     */
    fun onPlayerError(): Boolean {
        val s = _state.value
        return if (s.retriesUsed < maxRetries) {
            _state.value = s.copy(isBuffering = true, hasError = false, isReady = false, retriesUsed = s.retriesUsed + 1)
            true
        } else {
            _state.value = s.copy(hasError = true, isBuffering = false, isReady = false)
            false
        }
    }

    /** A failed URL resolution (vcdn:// could not be resolved to an http streamUrl). */
    fun onResolveFailed() {
        _state.value = _state.value.copy(hasError = true, isBuffering = false, isReady = false, retriesUsed = 0)
    }

    /** User tapped Reintentar: full reset so the pipeline can re-resolve/re-prepare. */
    fun onRetryReset() {
        _state.value = ReelPlayerState(isBuffering = true)
    }
}