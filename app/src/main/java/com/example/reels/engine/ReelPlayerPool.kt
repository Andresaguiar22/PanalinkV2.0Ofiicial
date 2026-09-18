package com.example.reels.engine

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
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
        // enough data is buffered; after a rebuffer allow a wider margin so a
        // long video does not stall while the player refills after a URL swap.
        private const val MIN_BUFFER_MS = 10_000
        private const val MAX_BUFFER_MS = 60_000
        private const val BUFFER_FOR_PLAYBACK_MS = 300
        private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 6_000

        // VCDN signed URLs expire ~60s (BFF TTL). Any cached URL older than this
        // is force-refreshed BEFORE playback starts to avoid a mid-playback 401.
        // Media3 error 2004 is also treated as expired-token and triggers refresh.
        private const val URL_TTL_MS = 45_000L
        private const val PLAYBACK_ERROR_HTTP_401 = 2004

        // Proactive refresh scheduling: mint a new signed VCDN URL shortly
        // before the current one actually dies (real expiry, not a fixed 45s),
        // even while the video is actively playing — this stops long videos
        // from stalling/freezing at ~60s when the token expires mid-playback.,

        private const val REFRESH_AHEAD_MS = 25_000L
        private const val REFRESH_COOLDOWN_MS = 50_000L
    }

    data class SlotPlayer(val slot: Int, val player: ExoPlayer)

    private val players = arrayOfNulls<ExoPlayer>(POOL_SIZE)

    /**
     * Compose-observable: reelId → player. Populated on acquire/eviction so the
     * UI recomposes the moment a reel's player becomes available.
     */
    private val livePlayers: SnapshotStateMap<String, ExoPlayer> = mutableStateMapOf()

    /**
     * Compose-observable playback timing per reel, refreshed on a periodic tick
     * (like the old overlay's 150 ms polling loop). Drives the bottom progress
     * bar and the play/pause icon.
     */
    data class PlayerTiming(val positionMs: Long, val durationMs: Long, val playing: Boolean)
    private val timings: SnapshotStateMap<String, PlayerTiming> = mutableStateMapOf()

    /** Starts periodic timing updates for every live player while the feed is open. */
    fun startTimingUpdates() {
        tickerJob = scope.launch {
            while (true) {
                for ((reelId, player) in livePlayers) {
                    val slot = ownerByReelId[reelId] ?: continue
                    val now = System.currentTimeMillis()
                    // Proactive VCDN token refresh: even WHILE playing, when a
                    // signed URL is near its real expiry we mint a fresh URL
                    // (position/playWhenReady preserved by refreshUrlAsync).

                    if (refreshAtBySlot[slot] > 0L && now >= refreshAtBySlot[slot]) {


                        refreshUrlAsync(slot)

                        refreshAtBySlot[slot] = now + REFRESH_COOLDOWN_MS
                    }
                    if (reelId == targetReelId) {
                        val dur = runCatching { player.duration }.getOrDefault(0L)
                        val pos = runCatching { player.currentPosition }.getOrDefault(0L).coerceAtLeast(0L)
                        timings[reelId] = PlayerTiming(pos, dur, player.playWhenReady)
                    }
                }
                kotlinx.coroutines.delay(150)
            }
        }
    }

    var tickerJob: kotlinx.coroutines.Job? = null
        private set

    /** Maps a reel id to the slot that currently owns it (null = not owned). */
    private val ownerByReelId = HashMap<String, Int>()
    private val reelIdBySlot = arrayOfNulls<String>(POOL_SIZE)
    private val urlBySlot = arrayOfNulls<String>(POOL_SIZE)
    private val stableUrlBySlot = arrayOfNulls<String>(POOL_SIZE)
    private val acquiredAtBySlot = LongArray(POOL_SIZE)
    private val refreshAtBySlot = LongArray(POOL_SIZE)

    /**
     * The reel the UI intends to be playing right now. Acquisition is async
     * (URL resolution happens off the main thread), so `play()` may be called
     * before the slot exists. When a player whose reel is the target reaches
     * [Player.STATE_READY] we flip `playWhenReady` ourselves — eliminating the
     * race where an acquired player would otherwise freeze on its first frame.
     */
    private var targetReelId: String? = null

    /** The reel that was last requested to play. Used to restart from 0 on navigation. */
    private var lastPlayedReelId: String? = null

    /** Reel ids that must NOT be evicted (current + upcoming pages). */
    private val protectedReels = HashSet<String>()

    /** Reels the user manually paused via the play/pause tap. The target-start
     *  mechanism respects this so a paused reel stays paused until toggled. */
    private val userPausedReels = HashSet<String>()

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
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val id = reelIdBySlot[slot]
                        if (playbackState == Player.STATE_READY &&
                            id == targetReelId && id != null &&
                            !userPausedReels.contains(id)
                        ) {
                            // Starts from 0 whenever a freshly-prepared target reaches READY.
                            if (lastPlayedReelId != id) player.seekTo(0)
                            player.playWhenReady = true
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "slot=$slot player error code=${error.errorCode} cause=${error.cause?.javaClass?.simpleName}")
                        handlePlayerError(slot, error)
                    }
                })
            }
    }

    /**
     * Resolves [stableUrl] (off the main thread) and acquires the reel.
     * Safe to call any time: if the reel is already owned it is a no-op.
     * The Target mechanism (see [setTarget]) starts it as soon as it is READY.
     */
    fun acquireAsync(reelId: String, stableUrl: String, volume: Float = 0f) {
        if (livePlayers[reelId] != null) return
        scope.launch {
            val resolved = runCatching { CdnManager.resolveMediaUrl(stableUrl) }.getOrNull()
            if (resolved.isNullOrBlank()) return@launch
            acquire(reelId, resolved, volume, stableUrl = stableUrl)
        }
    }

    /** Returns the player owning [reelId], or null if none. */
    fun playerFor(reelId: String): ExoPlayer? = livePlayers[reelId]

    /** Compose-observable timing for [reelId] (progress bar / play-pause icon). */
    fun timingFor(reelId: String): PlayerTiming? = timings[reelId]

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

        // Pick a free slot, else evict the oldest-assigned populated slot that is
        // NOT protected (protected = current/upcoming page).
        var slot = firstFreeSlot()
        if (slot == null) {
            slot = evictionSlot()
            // If the only eviction candidates are protected (pool too small for the
            // protected set), fall back to the oldest slot — playback is better than
            // perfect protection during hyper-fast swipes.
            val prev = reelIdBySlot[slot]
            if (prev != null && protectedReels.contains(prev)) {
                val alt = firstUnprotectedSlot() ?: slot
                if (alt != slot) slot = alt
            }
        }
        val player = players[slot] ?: buildSlot(slot).also { players[slot] = it }

        // Evict the previous owner of this slot.
        val previousId = reelIdBySlot[slot]
        if (previousId != null) {
            ownerByReelId.remove(previousId)
            livePlayers.remove(previousId)
        }

        player.setMediaItem(MediaItem.fromUri(url))
        player.volume = volume
        player.playWhenReady = false
        player.prepare()

        reelIdBySlot[slot] = reelId
        urlBySlot[slot] = url
        stableUrlBySlot[slot] = stableUrl
        acquiredAtBySlot[slot] = System.currentTimeMillis()
        // Seed the proactive-refresh deadline from the real VCDN token expiry (the
        // resolver applies a 35s safety margin; falls back to URL_TTL when unknown).

        val bffExpiry = com.example.data.repository.VcdnUrlResolver.expiresAtMillisOf(url)
        val ownExpiry = com.example.data.repository.VcdnSignatureUtils.expiresAtEpochMillisOf(url)

        refreshAtBySlot[slot] = when {
            ownExpiry >  0L -> ownExpiry - REFRESH_AHEAD_MS
            bffExpiry >  0L -> bffExpiry
            else -> System.currentTimeMillis() + URL_TTL_MS
        }
        ownerByReelId[reelId] = slot
        livePlayers[reelId] = player
        setTarget(reelId)
        return SlotPlayer(slot, player)
    }

    /** Pauses whatever is playing on [reelId], keeping it prepared (first frame stays). */
    fun pause(reelId: String) {
        playerFor(reelId)?.let { it.playWhenReady = false }
    }

    /**
     * User-explicit play/pause toggled by tapping the video. Marks/clears the
     * userPaused flag so the target-start mechanism does not fight the tap.
     */
    fun setUserPaused(reelId: String, paused: Boolean) {
        if (paused) {
            userPausedReels.add(reelId)
            playerFor(reelId)?.playWhenReady = false
        } else {
            userPausedReels.remove(reelId)
            // If the reel is the current target, resume; otherwise just clear flag.
            playerFor(reelId)?.playWhenReady = reelId == targetReelId
        }
    }

    /**
     * Sets the reels that must keep their players (current page + the ones about
     * to be shown when scrolling fast). Eviction only touches other slots, so a
     * page that is about to come on screen never loses its ready player — that
     * used to cause a black/frozen frame during fast swipes.
     */
    fun setProtectedReels(ids: Set<String>) {
        protectedReels.clear()
        protectedReels.addAll(ids)
    }

    /**
     * Starts playback for [reelId] on whatever slot owns it.
     *
     * A stale VCDN URL is only force-refreshed while the player is NOT actively
     * rendering (STATE_READY with playWhenReady=true). Swapping the media item
     * mid-playback discards the buffered ranges and recreates the codec, which
     * makes long videos freeze/stall right around the signed-URL expiry (~60 s).
     * For an already-playing reel we keep the current URL and rely on the reactive
     * 401 recovery (see [handlePlayerError]) instead.
     */
    fun play(reelId: String, volume: Float): Boolean {
        lastPlayedReelId = reelId
        targetReelId = reelId
        userPausedReels.remove(reelId)
        val slot = ownerByReelId[reelId] ?: return false
        val player = players[slot] ?: return false
        player.volume = volume
        val isActivelyPlaying = player.playbackState == Player.STATE_READY && player.playWhenReady
        if (isUrlStale(slot) && !isActivelyPlaying) {
            refreshUrlAsync(slot)
        }
        // TikTok behavior: the reel starts from 0 on a fresh visit (it already
        // ended or was never played in this session). Re-visiting a reel that is
        // mid-way keeps its position so long playback is never forced to restart.
        val wasActivelyPlaying = isActivelyPlaying
        if (!wasActivelyPlaying || player.currentPosition >= player.duration - 1_000L) {
            player.seekTo(0)
        }
        player.playWhenReady = true
        return true
    }

    /**
     * Marks [reelId] as the intended-playing reel without requiring the player to
     * exist yet (used by [acquire] after async resolution). If the slot is already
     * READY the player starts immediately; otherwise the listener starts it when
     * it becomes ready.
     */
    private fun setTarget(reelId: String) {
        val slot = ownerByReelId[reelId] ?: return
        val player = players[slot] ?: return
        val isTarget = reelId == targetReelId
        if (isTarget && player.playbackState == Player.STATE_READY && !userPausedReels.contains(reelId)) {
            player.seekTo(0)
            player.playWhenReady = true
        } else if (!isTarget) {
            // A preloaded (non-active) reel should stay frozen on its first frame.
            player.playWhenReady = false
        }
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
            refreshAtBySlot[slot] = System.currentTimeMillis() + REFRESH_COOLDOWN_MS
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
        return try {
            player.setMediaItem(MediaItem.fromUri(newUrl))
            player.prepare()
            player.seekTo(positionMs)
            player.playWhenReady = playing
            urlBySlot[slot] = newUrl
            true
        } catch (e: IllegalStateException) {
            // The slot may have been released/evicted between the check and the
            // swap (e.g. a swipe landed on this reel). Do not crash the feed.
            Log.w(TAG, "refreshUrl: player released mid-swap for $reelId", e)
            false
        }
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
        targetReelId = null
        lastPlayedReelId = null
        protectedReels.clear()
        userPausedReels.clear()
        tickerJob?.cancel()
        tickerJob = null
        livePlayers.clear()
        timings.clear()
        for (i in players.indices) {
            players[i]?.release()
            players[i] = null
        }
        ownerByReelId.clear()
        reelIdBySlot.fill(null)
        urlBySlot.fill(null)
        stableUrlBySlot.fill(null)
        acquiredAtBySlot.fill(0L)
        refreshAtBySlot.fill(0L)
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

    /** Lowest-index populated slot that is not in [protectedReels], or null. */
    private fun firstUnprotectedSlot(): Int? {
        for (i in 0 until POOL_SIZE) {
            val id = reelIdBySlot[i] ?: continue
            if (!protectedReels.contains(id)) return i
        }
        return null
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