package com.example.reels.engine

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.core.media.PanaRenderersFactory
import com.example.data.repository.CdnManager
import com.example.data.video.CacheDataSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TikTok-style player pool rebuilt from scratch.
 *
 * Holds exactly [POOL_SIZE] ExoPlayer instances that are reused across the whole
 * feed (M+1 players for M rendered pages): the active page plays on one slot,
 * while the other slots hold already-prepared, first-frame-rendered videos ready
 * to go on swipe. No player is ever constructed during a swipe — construction
 * only happens once per slot in [acquire].
 *
 * Quality policy (TikTok-like): maximum available variant is forced with
 * [DefaultTrackSelector.Parameters.forceHighestAvailableBitrate]. LoadControl is
 * tuned for fast start (low bufferForPlaybackMs) with a larger after-rebuffer
 * margin so a hiccup does not stall the feed.
 */
@OptIn(UnstableApi::class)
class ReelPlayerPool(private val context: Context) {

    companion object {
        private const val TAG = "ReelPlayerPool"
        const val POOL_SIZE = 3

        // Fast-start friendly: begin playback almost immediately (300 ms) once
        // enough data is buffered; after a rebuffer allow more margin (3 s).
        private const val MIN_BUFFER_MS = 10_000
        private const val MAX_BUFFER_MS = 40_000
        private const val BUFFER_FOR_PLAYBACK_MS = 300
        private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 3_000

        // VCDN signed URLs expire ~60s (BFF TTL). Any cached URL older than this
        // is force-refreshed BEFORE playback starts to avoid a mid-playback 401.
        // Media3 error 2004 is also treated as expired-token and triggers refresh.
        private const val URL_TTL_MS = 45_000L
        private const val PLAYBACK_ERROR_HTTP_401 = 2004
    }

    data class SlotPlayer(val slot: Int, val player: ExoPlayer)

    private val players = arrayOfNulls<ExoPlayer>(POOL_SIZE)

    /** Maps a reel id to the slot that currently owns it (null = not owned). */
    private val ownerByReelId = HashMap<String, Int>()
    private val reelIdBySlot = arrayOfNulls<String>(POOL_SIZE)
    private val urlBySlot = arrayOfNulls<String>(POOL_SIZE)
    private val stableUrlBySlot = arrayOfNulls<String>(POOL_SIZE)
    private val acquiredAtBySlot = LongArray(POOL_SIZE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Builds a fresh, correctly-tuned ExoPlayer. This is the ONLY place players are created. */
    private fun buildSlot(slot: Int): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .setBackBuffer(4_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .clearVideoSizeConstraints()
                    .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                    .setMaxVideoBitrate(Int.MAX_VALUE)
            )
        }

        val dataSourceFactory = DefaultDataSource.Factory(
            context,
            CacheDataSourceFactory.getCacheDataSourceFactory(context)
        )

        return ExoPlayer.Builder(context, PanaRenderersFactory.create(context))
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
            )
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { player ->
                player.playWhenReady = false
                player.repeatMode = Player.REPEAT_MODE_ALL
                player.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "slot=$slot player error code=${error.errorCode} cause=${error.cause?.javaClass?.simpleName}")
                        handlePlayerError(slot, error)
                    }
                })
            }
    }

    /** Returns the player owning [reelId], or null if none. */
    fun playerFor(reelId: String): ExoPlayer? {
        val slot = ownerByReelId[reelId] ?: return null
        return players[slot]
    }

    /**
     * Returns a slot and its player after preparing [reelId] with [url].
     * If the reel is already owned, returns it untouched. [stableUrl] is the
     * `vcdn://...` pointer used to mint fresh URLs later (401/expiry recovery).
     */
    fun acquire(reelId: String, url: String, volume: Float, stableUrl: String? = null): SlotPlayer {
        val existingSlot = ownerByReelId[reelId]
        if (existingSlot != null) {
            val p = players[existingSlot]!!
            p.volume = volume
            return SlotPlayer(existingSlot, p)
        }

        // Pick the slot with no owner, else evict the oldest-assigned populated slot.
        val slot = firstFreeSlot() ?: evictionSlot()
        val player = players[slot] ?: buildSlot(slot).also { players[slot] = it }

        // Evict the previous owner of this slot.
        val previousId = reelIdBySlot[slot]
        if (previousId != null) ownerByReelId.remove(previousId)

        player.setMediaItem(MediaItem.fromUri(url))
        player.volume = volume
        player.playWhenReady = false
        player.prepare()

        reelIdBySlot[slot] = reelId
        urlBySlot[slot] = url
        stableUrlBySlot[slot] = stableUrl
        acquiredAtBySlot[slot] = System.currentTimeMillis()
        ownerByReelId[reelId] = slot
        return SlotPlayer(slot, player)
    }

    /** Pauses whatever is playing on [reelId], keeping it prepared (first frame stays). */
    fun pause(reelId: String) {
        playerFor(reelId)?.let { it.playWhenReady = false }
    }

    /**
     * Starts playback for [reelId] on whatever slot owns it.
     * Before starting, if the cached VCDN URL is older than [URL_TTL_MS] a fresh
     * URL is minted asynchronously and swapped in — a cheap preventive measure
     * that keeps TikTok-like uninterrupted playback on signed HLS.
     */
    fun play(reelId: String, volume: Float): Boolean {
        val slot = ownerByReelId[reelId] ?: return false
        val player = players[slot] ?: return false
        player.volume = volume
        if (isUrlStale(slot)) {
            refreshUrlAsync(slot)
            // Start with the existing (still-valid) URL right away; the refresh
            // swaps in the new one as soon as it is minted.
        }
        player.playWhenReady = true
        return true
    }

    private fun isUrlStale(slot: Int): Boolean {
        val stable = stableUrlBySlot[slot] ?: return false
        if (!stable.startsWith("vcdn://")) return false
        return System.currentTimeMillis() - acquiredAtBySlot[slot] > URL_TTL_MS
    }

    /** Asynchronously mints a fresh URL and swaps it in, preserving position/play state. */
    private fun refreshUrlAsync(slot: Int) {
        val stable = stableUrlBySlot[slot] ?: return
        val player = players[slot] ?: return
        val positionMs = player.currentPosition
        val wasPlaying = player.playWhenReady
        val reelId = reelIdBySlot[slot] ?: return

        scope.launch {
            val fresh = withContext(Dispatchers.IO) {
                runCatching { CdnManager.resolveMediaUrlFresh(stable) }.getOrNull()
            }
            if (fresh.isNullOrBlank() || fresh == urlBySlot[slot]) return@launch
            refreshUrl(reelId, fresh, positionMs)
            // Update the stable-pointer timestamp so we do not re-refresh on every call.
            acquiredAtBySlot[slot] = System.currentTimeMillis()
            Log.d(TAG, "refreshed VCDN URL for $reelId (preventive)")
        }
    }

    /** Synchronously swaps in a fresh [url] for [reelId] preserving position and play state. */
    fun refreshUrl(reelId: String, newUrl: String, positionMs: Long): Boolean {
        val slot = ownerByReelId[reelId] ?: return false
        val player = players[slot] ?: return false
        if (urlBySlot[slot] == newUrl) return false
        val playing = player.playWhenReady
        player.setMediaItem(MediaItem.fromUri(newUrl))
        player.prepare()
        player.seekTo(positionMs)
        player.playWhenReady = playing
        urlBySlot[slot] = newUrl
        return true
    }

    /** Handles a player error: HTTP 401 (2004) => refresh signed URL and resume. */
    private fun handlePlayerError(slot: Int, error: PlaybackException) {
        val stable = stableUrlBySlot[slot] ?: return
        if (error.errorCode != PLAYBACK_ERROR_HTTP_401) return
        val reelId = reelIdBySlot[slot] ?: return
        Log.w(TAG, "HTTP 401 on $reelId — refreshing signed URL")
        refreshUrlAsync(slot)
    }

    /** Releases everything. Called when leaving the feed. */
    fun releaseAll() {
        for (i in players.indices) {
            players[i]?.release()
            players[i] = null
        }
        ownerByReelId.clear()
        reelIdBySlot.fill(null)
        urlBySlot.fill(null)
        stableUrlBySlot.fill(null)
        acquiredAtBySlot.fill(0L)
    }

    // --- internal helpers ---

    private fun firstFreeSlot(): Int? {
        for (i in 0 until POOL_SIZE) if (reelIdBySlot[i] == null) return i
        return null
    }

    /** Evicts the oldest-assigned populated slot (FIFO-ish across the pool). */
    private fun evictionSlot(): Int {
        for (i in 0 until POOL_SIZE) {
            if (reelIdBySlot[i] != null) return i
        }
        return 0
    }

    fun debugDump(): String = buildString {
        append("ReelPlayerPool[")
        for (i in 0 until POOL_SIZE) {
            append("slot$i=").append(reelIdBySlot[i] ?: "-")
            if (i < POOL_SIZE - 1) append(",")
        }
        append("]")
    }
}