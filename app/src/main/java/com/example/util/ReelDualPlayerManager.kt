package com.example.util

import android.content.Context
import android.media.MediaCodec
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.core.media.PanaRenderersFactory
import com.example.data.video.CacheDataSourceFactory

/** Stable two-player pool for the Reels feed. */
@UnstableApi
class ReelDualPlayerManager(private val context: Context) {
    companion object { private const val TAG = "ReelDualPlayerManager" }
    enum class Slot { A, B }

    private var slotAPlayer: ExoPlayer? = null
    private var slotBPlayer: ExoPlayer? = null
    private var slotAAssignedId: String? = null
    private var slotBAssignedId: String? = null
    private val slotUrls = mutableMapOf<Slot, String>()
    private var activeSlot: Slot? = null
    private val recoveryAttempts = mutableMapOf<Slot, Int>()

    fun acquire(slot: Slot, id: String, url: String, volume: Float): ExoPlayer {
        val player = when (slot) {
            Slot.A -> slotAPlayer ?: build(false).also { slotAPlayer = it }
            Slot.B -> slotBPlayer ?: build(false).also { slotBPlayer = it }
        }
        if (assignedId(slot) != id) {
            recoveryAttempts[slot] = 0
            player.setMediaItem(MediaItem.fromUri(url))
            player.repeatMode = Player.REPEAT_MODE_ALL
            player.volume = volume
            player.playWhenReady = false
            player.prepare()
            slotUrls[slot] = url
            if (slot == Slot.A) slotAAssignedId = id else slotBAssignedId = id
        } else {
            player.volume = volume
        }
        return player
    }

    fun activate(slot: Slot, volume: Float) {
        val player = playerFor(slot) ?: return
        player.volume = volume
        player.playWhenReady = true
        activeSlot = slot
    }

    fun pause(slot: Slot) {
        playerFor(slot)?.playWhenReady = false
        if (activeSlot == slot) activeSlot = null
    }

    fun assignedId(slot: Slot): String? = if (slot == Slot.A) slotAAssignedId else slotBAssignedId
    fun playerFor(slot: Slot): ExoPlayer? = if (slot == Slot.A) slotAPlayer else slotBPlayer
    fun isPreppedFor(slot: Slot, id: String): Boolean = assignedId(slot) == id
    fun slotFor(id: String): Slot? = when {
        slotAAssignedId == id -> Slot.A
        slotBAssignedId == id -> Slot.B
        else -> null
    }
    fun freeSlot(): Slot? = when {
        slotAAssignedId == null -> Slot.A
        slotBAssignedId == null -> Slot.B
        else -> null
    }

    /** Preloads only into a free slot. No queued preload is auto-promoted during swipes. */
    fun acquireOrReuse(id: String, url: String, active: Boolean, volume: Float): Slot? {
        val existing = slotFor(id)
        if (existing != null) {
            if (active) activate(existing, volume) else pause(existing)
            return existing
        }
        val free = freeSlot()
        if (!active && free == null) return null
        val slot = if (active) Slot.A else free ?: return null
        acquire(slot, id, url, volume)
        if (active) activate(slot, volume) else pause(slot)
        return slot
    }

    fun releaseIfOwned(id: String) {
        when {
            slotAAssignedId == id -> clearSlot(Slot.A)
            slotBAssignedId == id -> clearSlot(Slot.B)
        }
    }

    private fun clearSlot(slot: Slot) {
        val player = playerFor(slot)
        try { player?.stop() } catch (_: IllegalStateException) {}
        try { player?.release() } catch (_: IllegalStateException) {}
        if (slot == Slot.A) {
            slotAPlayer = null
            slotAAssignedId = null
        } else {
            slotBPlayer = null
            slotBAssignedId = null
        }
        slotUrls.remove(slot)
        recoveryAttempts.remove(slot)
        if (activeSlot == slot) activeSlot = null
    }

    sealed interface RecoveryResult {
        data class Recovered(val player: ExoPlayer, val attempt: Int, val rendererMode: String) : RecoveryResult
        data object NotACodecError : RecoveryResult
        data object Exhausted : RecoveryResult
    }

    fun recoverFromPlaybackError(id: String, error: PlaybackException, volume: Float): RecoveryResult {
        val slot = slotFor(id) ?: return RecoveryResult.Exhausted
        if (!isCodecInitializationError(error)) return RecoveryResult.NotACodecError
        val previousAttempts = recoveryAttempts[slot] ?: 0
        if (previousAttempts >= 2) return RecoveryResult.Exhausted
        val attempt = previousAttempts + 1
        recoveryAttempts[slot] = attempt
        val oldPlayer = playerFor(slot) ?: return RecoveryResult.Exhausted
        val url = slotUrls[slot] ?: return RecoveryResult.Exhausted
        val position = oldPlayer.currentPosition.coerceAtLeast(0L)
        val wasPlaying = oldPlayer.playWhenReady
        try { oldPlayer.stop() } catch (_: IllegalStateException) {}
        try { oldPlayer.release() } catch (_: IllegalStateException) {}
        val newPlayer = build(preferSoftware = attempt >= 2)
        if (slot == Slot.A) slotAPlayer = newPlayer else slotBPlayer = newPlayer
        newPlayer.setMediaItem(MediaItem.fromUri(url))
        newPlayer.repeatMode = Player.REPEAT_MODE_ALL
        newPlayer.volume = volume
        newPlayer.playWhenReady = false
        newPlayer.prepare()
        if (position > 0L) newPlayer.seekTo(position)
        if (activeSlot == slot) newPlayer.playWhenReady = wasPlaying
        return RecoveryResult.Recovered(
            newPlayer,
            attempt,
            if (attempt >= 2) "software-preferred" else "hardware-fallback"
        )
    }

    fun isCodecInitializationError(error: PlaybackException): Boolean {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) return false
            cause = cause.cause
        }
        if (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED) return true
        cause = error.cause
        while (cause != null) {
            if (cause is MediaCodec.CodecException) return true
            val name = cause.javaClass.name
            if (name.endsWith("DecoderInitializationException") || name.endsWith("CodecException")) return true
            cause = cause.cause
        }
        return false
    }

    /** Only used for an explicit 401/expired signed-URL recovery. */
    fun refreshActiveUrl(id: String, newUrl: String): Boolean {
        val slot = slotFor(id) ?: return false
        if (slot != activeSlot) return false
        val player = playerFor(slot) ?: return false
        if (slotUrls[slot] == newUrl) return false
        val position = player.currentPosition.coerceAtLeast(0L)
        val wasPlaying = player.playWhenReady
        return try {
            player.setMediaItem(MediaItem.fromUri(newUrl))
            player.prepare()
            if (position > 0L) player.seekTo(position)
            player.playWhenReady = wasPlaying
            slotUrls[slot] = newUrl
            true
        } catch (e: IllegalStateException) {
            Log.w(TAG, "URL refresh skipped because player is invalid", e)
            false
        }
    }

    private fun build(preferSoftware: Boolean): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(8000, 20000, 200, 400)
            .setBackBuffer(3000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val trackSelector = DefaultTrackSelector(context, AdaptiveTrackSelection.Factory()).apply {
            setParameters(buildUponParameters().clearVideoSizeConstraints())
        }
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(
            context,
            CacheDataSourceFactory.getCacheDataSourceFactory(context)
        )
        return ExoPlayer.Builder(context, PanaRenderersFactory.create(context, preferSoftware = preferSoftware))
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .build()
    }

    fun releaseAll() {
        clearSlot(Slot.A)
        clearSlot(Slot.B)
    }
}
