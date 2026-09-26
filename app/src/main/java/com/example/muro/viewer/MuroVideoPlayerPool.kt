package com.example.muro.viewer

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.core.media.VideoPlaybackEngine
import com.example.data.repository.CdnManager
import com.example.data.repository.VcdnUrlResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Player pool for the vertical Muro video viewer.
 *
 * Deliberately small (current + next) so scrolling the Muro never keeps more than
 * two videos alive and never two playing at once. It reuses [VideoPlaybackEngine]
 * with the VIEWER profile, so a video watched here decodes with hardware and a
 * capped resolution — the same pipeline as reels and the feed.
 *
 * URL lifetime: VCDN signed URLs expire after ~60s. Instead of polling, the pool
 * schedules a refresh shortly before the real expiry and re-applies it on the SAME
 * player preserving position, so a long video neither freezes nor restarts.
 */
@OptIn(UnstableApi::class)
class MuroVideoPlayerPool(private val context: Context) {

    companion object {
        private const val TAG = "MuroVideoPlayerPool"
        const val POOL_SIZE = 2

        /** Refresh the signed URL this long before it actually expires. */
        private const val REFRESH_AHEAD_MS = 20_000L
        private const val REFRESH_COOLDOWN_MS = 45_000L
        private const val FALLBACK_TTL_MS = 45_000L
        private const val PLAYBACK_ERROR_HTTP_401 = 2004
        private const val MAX_401_RETRIES = 2
    }

    private val players = arrayOfNulls<ExoPlayer>(POOL_SIZE)
    private val postIdBySlot = arrayOfNulls<String>(POOL_SIZE)
    private val stableUrlBySlot = arrayOfNulls<String>(POOL_SIZE)
    private val refreshAtBySlot = LongArray(POOL_SIZE)
    private val retriesBySlot = IntArray(POOL_SIZE)
    private val refreshJobs = arrayOfNulls<kotlinx.coroutines.Job>(POOL_SIZE)
    private val listeners = arrayOfNulls<Player.Listener>(POOL_SIZE)

    /** Compose-observable: postId -> prepared player. Drives the UI. */
    private val livePlayers: SnapshotStateMap<String, ExoPlayer> = mutableStateMapOf()

    /**
     * Compose-observable: posts currently rebuffering. The UI shows a small spinner
     * instead of a frozen frame, which is what makes a hiccup look like a stall.
     */
    private val bufferingPosts: SnapshotStateMap<String, Boolean> = mutableStateMapOf()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** The post the UI wants playing. Used to auto-start once a player is READY. */
    private var targetPostId: String? = null

    /** Viewer-wide mute state, applied to every player the pool hands out. */
    private var muted = false

    fun isBuffering(postId: String): Boolean = bufferingPosts[postId] == true

    fun setMuted(value: Boolean) {
        muted = value
        livePlayers.values.forEach { it.volume = if (value) 0f else 1f }
    }

    private fun buildSlot(slot: Int): ExoPlayer =
        VideoPlaybackEngine.build(context, VideoPlaybackEngine.Profile.VIEWER).also { player ->
            player.playWhenReady = false
            player.volume = if (muted) 0f else 1f
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    val id = postIdBySlot[slot]
                    if (id != null) {
                        bufferingPosts[id] = playbackState == Player.STATE_BUFFERING
                    }
                    if (playbackState == Player.STATE_READY && id != null && id == targetPostId) {
                        player.playWhenReady = true
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    handleError(slot, error)
                }
            }
            listeners[slot] = listener
            player.addListener(listener)
        }

    fun playerFor(postId: String): ExoPlayer? = livePlayers[postId]

    /** Resolves the stable `vcdn://` pointer off the main thread, then acquires. */
    fun acquireAsync(postId: String, stableUrl: String) {
        if (livePlayers[postId] != null) return
        scope.launch {
            val resolved = runCatching { CdnManager.resolveMediaUrl(stableUrl) }.getOrNull()
            if (resolved.isNullOrBlank()) {
                Log.w(TAG, "no se pudo resolver el video del post $postId")
                return@launch
            }
            acquire(postId, resolved, stableUrl)
        }
    }

    fun acquire(postId: String, url: String, stableUrl: String) {
        if (livePlayers.containsKey(postId)) return

        val slot = (0 until POOL_SIZE).firstOrNull { players[it] == null }
            // With POOL_SIZE=2 the slot not holding the target is always the right
            // one to reuse; a null target means nothing plays yet, so reuse slot 0.
            ?: (0 until POOL_SIZE).firstOrNull { postIdBySlot[it] != targetPostId } ?: 0

        val player = players[slot] ?: buildSlot(slot).also { players[slot] = it }

        // The evicted post must stop producing frames and be forgotten by the UI.
        postIdBySlot[slot]?.let { previous ->
            if (previous != postId) {
                refreshJobs[slot]?.cancel()
                livePlayers.remove(previous)
                player.stop()
                player.clearMediaItems()
            }
        }

        player.setMediaItem(MediaItem.fromUri(url))
        player.playWhenReady = false
        player.volume = if (muted) 0f else 1f
        player.prepare()

        postIdBySlot[slot] = postId
        stableUrlBySlot[slot] = stableUrl
        retriesBySlot[slot] = 0
        refreshAtBySlot[slot] = refreshDeadlineFor(url)
        livePlayers[postId] = player

        // Already ready (media served from cache) -> start now; otherwise the
        // playback-state listener starts it when it becomes READY.
        if (postId == targetPostId && player.playbackState == Player.STATE_READY) {
            player.playWhenReady = true
        }
    }

    /** Marks [postId] as the one that should play; pauses everything else. */
    fun setTarget(postId: String) {
        targetPostId = postId
        for ((id, player) in livePlayers) {
            if (id == postId) {
                if (player.playbackState == Player.STATE_READY) player.playWhenReady = true
            } else {
                player.playWhenReady = false
            }
        }
        scheduleRefreshFor(postId)
    }

    fun pauseAll() {
        targetPostId = null
        livePlayers.values.forEach { it.playWhenReady = false }
    }

    /** User tap: toggles play/pause for [postId]. */
    fun setUserPaused(postId: String, paused: Boolean) {
        livePlayers[postId]?.playWhenReady = !paused
    }

    fun isPlaying(postId: String): Boolean = livePlayers[postId]?.playWhenReady == true

    fun releaseAll() {
        targetPostId = null
        for (slot in 0 until POOL_SIZE) {
            refreshJobs[slot]?.cancel()
            refreshJobs[slot] = null
            players[slot]?.let {
                listeners[slot]?.let { listener -> it.removeListener(listener) }
                it.stop()
                it.clearMediaItems()
                it.release()
            }
            listeners[slot] = null
            players[slot] = null
            postIdBySlot[slot] = null
            stableUrlBySlot[slot] = null
            refreshAtBySlot[slot] = 0L
        }
        bufferingPosts.clear()
        livePlayers.clear()
    }

    /** Frees the player of a post that is no longer near the viewport. */
    fun release(postId: String) {
        for (slot in 0 until POOL_SIZE) {
            if (postIdBySlot[slot] != postId) continue
            refreshJobs[slot]?.cancel()
            refreshJobs[slot] = null
            players[slot]?.let { player ->
                player.stop()
                player.clearMediaItems()
            }
            postIdBySlot[slot] = null
            stableUrlBySlot[slot] = null
            refreshAtBySlot[slot] = 0L
            bufferingPosts.remove(postId)
            livePlayers.remove(postId)
        }
    }

    private fun refreshDeadlineFor(url: String): Long {
        val own = VcdnUrlResolver.expiresAtMillisOf(url)
        val signature = com.example.data.repository.VcdnSignatureUtils.expiresAtEpochMillisOf(url)
        return when {
            signature > 0L -> signature - REFRESH_AHEAD_MS
            own > 0L -> own - REFRESH_AHEAD_MS
            else -> System.currentTimeMillis() + FALLBACK_TTL_MS
        }
    }

    /**
     * Refreshes the signed URL of the target post shortly before it expires,
     * preserving the playback position. Without this, a video longer than the token
     * lifetime would stall at the expiry mark.
     */
    private fun scheduleRefreshFor(postId: String) {
        val slot = (0 until POOL_SIZE).firstOrNull { postIdBySlot[it] == postId } ?: return
        val delay = refreshAtBySlot[slot] - System.currentTimeMillis()
        if (delay <= 0L) return
        refreshJobs[slot]?.cancel()
        refreshJobs[slot] = scope.launch {
            kotlinx.coroutines.delay(delay)
            // The user may have moved on while we waited.
            if (postIdBySlot[slot] != postId || targetPostId != postId) return@launch
            refreshUrl(slot)
            refreshAtBySlot[slot] = System.currentTimeMillis() + REFRESH_COOLDOWN_MS
        }
    }

    private fun refreshUrl(slot: Int) {
        val stable = stableUrlBySlot[slot] ?: return
        val player = players[slot] ?: return
        val owner = postIdBySlot[slot]
        scope.launch {
            val fresh = runCatching { CdnManager.resolveMediaUrlFresh(stable) }.getOrNull()
            // Guard against the slot being re-assigned to another post meanwhile.
            if (fresh.isNullOrBlank() || postIdBySlot[slot] != owner) return@launch
            try {
                val position = player.currentPosition
                val wasPlaying = player.playWhenReady
                player.setMediaItem(MediaItem.fromUri(fresh), false)
                player.prepare()
                player.seekTo(position)
                player.playWhenReady = wasPlaying
            } catch (e: Exception) {
                Log.w(TAG, "no se pudo refrescar la URL: ${e.message}")
            }
        }
    }

    private fun handleError(slot: Int, error: PlaybackException) {
        val postId = postIdBySlot[slot] ?: return
        if (error.errorCode == PLAYBACK_ERROR_HTTP_401 && retriesBySlot[slot] < MAX_401_RETRIES) {
            retriesBySlot[slot]++
            Log.i(TAG, "401 en post $postId; reintentando con URL fresca (${retriesBySlot[slot]})")
            refreshUrl(slot)
            return
        }
        Log.e(TAG, "error definitivo en post $postId code=${error.errorCode}")
        // A broken source must not leave a frozen frame pretending to play.
        players[slot]?.playWhenReady = false
    }
}
