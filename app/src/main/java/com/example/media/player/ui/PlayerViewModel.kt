package com.example.media.player.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.media.audio.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * P6.7.3 - Player View Model
 * Centrally manages audio playback state for the professional player UI.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val playerEngine = AudioPlayerProvider.getPlayerEngine(application)
    private val effectsController = AudioPlayerProvider.getEffectsController()
    private val audioLibraryManager: AudioLibraryManager? by lazy {
        try {
            val db = com.example.data.database.PanalinkDatabase.getDatabase(application)
            AudioLibraryManager(db.audioDao())
        } catch (_: Exception) { null }
    }

    val playerState: StateFlow<AudioPlayerState> = playerEngine.state

    // --- Sleep timer (Poweramp-style) ---
    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs.asStateFlow()

    /** Starts a sleep timer that pauses playback when it fires. null cancels it. */
    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (minutes == null || minutes <= 0) {
            _sleepTimerRemainingMs.value = null
            return
        }
        val totalMs = minutes * 60_000L
        _sleepTimerRemainingMs.value = totalMs
        sleepTimerJob = viewModelScope.launch {
            var remaining = totalMs
            while (remaining > 0) {
                kotlinx.coroutines.delay(1_000)
                remaining -= 1_000
                _sleepTimerRemainingMs.value = remaining
            }
            playerEngine.togglePlayPause()
            _sleepTimerRemainingMs.value = null
        }
    }

    // --- EQ presets (visual, wired through AudioEffectsController) ---
    private val _eqPreset = MutableStateFlow("Normal")
    val eqPreset: StateFlow<String> = _eqPreset.asStateFlow()

    fun setEqPreset(preset: String) {
        _eqPreset.value = preset
        effectsController?.setEqualizer(preset)
    }

    fun playTrack(track: AudioTrackEntity) {
        playerEngine.setQueueAndPlay(listOf(track), 0)
    }

    fun playTracks(tracks: List<AudioTrackEntity>, startIndex: Int = 0) {
        playerEngine.setQueueAndPlay(tracks, startIndex)
    }

    /** Stops playback and empties the queue so the global mini-player disappears. */
    fun clearQueue() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingMs.value = null
        playerEngine.stopAndClear()
    }

    fun toggleFavorite(track: AudioTrackEntity) {
        viewModelScope.launch {
            audioLibraryManager?.toggleFavorite(track.id, track.isFavorite)
        }
    }

    fun togglePlayPause() {
        playerEngine.togglePlayPause()
    }

    fun nextTrack() {
        playerEngine.nextTrack()
    }

    fun previousTrack() {
        playerEngine.previousTrack()
    }

    fun seekTo(positionMs: Long) {
        playerEngine.seekTo(positionMs)
    }

    fun toggleShuffle() {
        playerEngine.toggleShuffle()
    }

    fun toggleRepeat() {
        playerEngine.toggleRepeat()
    }

    fun setPlaybackSpeed(speed: Float) {
        playerEngine.setPlaybackSpeed(speed)
    }

    fun removeFromQueue(trackId: String) {
        // Implementation logic depends on engine supporting queue modification
        // For now, we'll need to update the engine to support this if it doesn't
    }

    fun playFromQueue(index: Int) {
        val tracks = playerState.value.queue
        if (index in tracks.indices) {
            playerEngine.setQueueAndPlay(tracks, index)
        }
    }
    
    fun setBassBoost(level: Int) {
        effectsController?.setBassBoost(level.toShort())
    }
    
    fun setBalance(balance: Float) {
        effectsController?.setBalance(balance)
    }
}
