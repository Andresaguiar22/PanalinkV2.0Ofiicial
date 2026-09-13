package com.example.util

import android.content.Context
import android.media.MediaCodec
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.core.media.PanaRenderersFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

/**
 * Double-buffered reel player pool: exactly two ExoPlayer instances are
 * reused across the whole feed (A = currently playing; the other slot holds the
 * next preloaded reel whenever one is free(. Preloads are NEVER prepared on the
 * currently-playing slot: doing so calls setMediaItem+prepare on the visible
 * player and produces the black-screen-after-scroll bug. If both slots are busy,
 * the incoming preload is skipped and re-acquired when that page turns active.

 *
 *  - A page that becomes [ReelPlayerSlot.Active] acquires slot A and prepares
 *    its media only once on first composition.  When the next page is swiped in,
 *    the previously preloaded slot B simply sets playWhenReady=true (its first
 *    frame was already rendered into the page's PlayerView during the preload phase,
 *    so there is no black screen.
 *
 *  - When the current page moves on, slot A (which held the previous media)
 *    is re-prepped with the next-next media to become the new preload slot.If
 *    keeps total players at exactly two and guarantees zero player construction
 *    (codec teardown/re-init) during swipes.

 *  Memory/footprint: two ExoPlayers max, each with the shared SimpleCache
 *    data source; buffered data for a slot is released when that slot is re-prepped
 *    with a different media (setMediaItem+prepare discards the old buffered ranges.

 * @param url the already-resolved, playable URL (never a vcdn:// pointer.
 */
@UnstableApi
class ReelDualPlayerManager(private val context: Context) {
    companion object {
        private const val TAG = "ReelDualPlayerManager"
    }
    enum class Slot { A, B }

    private var slotAPlayer: ExoPlayer? = null
    private var slotBPlayer: ExoPlayer? = null
    private var slotAAssignedId: String? = null
    private var slotBAssignedId: String? = null
    private val slotUrls = mutableMapOf<Slot, String>()
    private var activeSlot: Slot? = null

    /** Pending URL update for the active slot that cannot be re-prepared immediately.
     *  Applied when the slot transitions to preload/inactive. */
    private val pendingUrlUpdates = mutableMapOf<Slot, String>()

    /** Per-slot codec-recovery attempt counter for the currently-assigned reel.
     *  Reset whenever a slot is assigned a new reel id (see [acquire]/[clearSlot]).
     *  Attempt 1 rebuilds with hardware decoders; attempt 2 escalates to the
     *  FFmpeg/software-preferred renderer set so a poisoned hardware codec instance
     *  is avoided. */
    private val recoveryAttempts = mutableMapOf<Slot, Int>()

    /** Acquires the player for [slot] and prepares [url]. If the slot already held this
     *  media, it is returned untouched (first frame already rendered. */
    fun acquire(slot: Slot, id: String, url: String, volume: Float): ExoPlayer {
        val player = when (slot) {
            Slot.A -> slotAPlayer ?: build().also { slotAPlayer = it }
            Slot.B -> slotBPlayer ?: build().also { slotBPlayer = it }
        }
        val assigned = if (slot == Slot.A) slotAAssignedId else slotBAssignedId
        if (assigned != id) {
            // Fresh reel on this slot ⇒ reset the codec-recovery budget so a
            // brand-new player starts with a clean slate (2 attempts).
            recoveryAttempts[slot] = 0
            val mediaItem = MediaItem.fromUri(url)
            player.setMediaItem(mediaItem)
            player.repeatMode = Player.REPEAT_MODE_ALL
            player.volume = volume
            player.playWhenReady = false
            try {
                player.prepare()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Player prepare failed (stale/released) for slot $slot id=$id", e)
            }
            slotUrls[slot] = url
            if (slot == Slot.A) slotAAssignedId = id else slotBAssignedId = id
        } else {
            player.volume = volume
        }
        return player
    }

    /** Promotes [slot] to actively playing. */
    fun activate(slot: Slot, volume: Float) {
        val player = if (slot == Slot.A) slotAPlayer ?: return else slotBPlayer ?: return
        player.volume = volume
        player.playWhenReady = true
        activeSlot = slot
    }

    /** Pauses whatever is playing (used when the page stops being active). */
    fun pause(slot: Slot) {
        val player = if (slot == Slot.A) slotAPlayer ?: return else slotBPlayer ?: return
        // Apply any deferred URL update now that the slot is no longer active.
        val pendingUrl = pendingUrlUpdates.remove(slot)
        if (pendingUrl != null) {
            val savedPosition = player.currentPosition
            val savedPlayWhenReady = player.playWhenReady
            try {
                player.setMediaItem(MediaItem.fromUri(pendingUrl))
                player.prepare()
                player.seekTo(savedPosition)
                player.playWhenReady = savedPlayWhenReady
                slotUrls[slot] = pendingUrl
                Log.d(TAG, "Applied deferred URL update for slot $slot") // redacted: signed HLS URL
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Player stale during deferred URL update for slot $slot", e)
            }
        }
        player.playWhenReady = false
        activeSlot = null
    }

    /** Result of asking the manager to recover from a playback error. */
    sealed interface RecoveryResult {
        /** A brand-new ExoPlayer was built and prepared; hand it to the PlayerView. */
        data class Recovered(val player: ExoPlayer, val attempt: Int, val rendererMode: String) : RecoveryResult
        /** The error was not a codec/decoder failure — the caller should handle it as a network error. */
        object NotACodecError : RecoveryResult
        /** All automatic recovery attempts are spent; show the definitive error UI. */
        object Exhausted : RecoveryResult
    }

    /**
     * Centralized codec-recovery entry point for the reel feed.
     *
     * For a [PlaybackException] that originates in a MediaCodec/decoder failure
     * ([CodecException] / decoder initialization error), this performs a REAL
     * recovery — never `player.prepare()` on the same poisoned decoder:
     *   1. stop() + release() the affected player (its decoder may be in a
     *      stuck state);
     *   2. build a fresh ExoPlayer for the same slot;
     *   3. re-set the MediaItem from the already-resolved URL;
     *   4. restore playWhenReady according to the prior playing state.
     *
     * Attempt 1 reuses the same (hardware + FFmpeg-fallback) renderers that
     * [build] originally produced. Attempt 2 escalates to a software-preferred
     * renderer set (FFmpeg decoders preferred over the platform adapter) so a
     * chip that cannot initialize a specific codec profile falls through to the
     * bundled native FFmpeg decoder with correct color conversion.
     *
     * A per-slot attempt counter (reset on new reel assignment) caps automatic
     * recovery at 2 attempts to honour the "no infinite loops" rule. Only the
     * single broken slot is ever rebuilt — the other slot (preload) is never
     * touched, keeping the dual-buffer invariant.
     *
     * @return [RecoveryResult.Recovered] with the new player, or
     *         [RecoveryResult.Exhausted]/[RecoveryResult.NotACodecError].
     */
    fun recoverFromPlaybackError(id: String, error: PlaybackException, volume: Float): RecoveryResult {
        val slot = slotFor(id) ?: run {
            Log.w(TAG, "recoverFromPlaybackError: no slot owns id=$id")
            return RecoveryResult.Exhausted
        }
        if (!isCodecInitializationError(error)) {
            return RecoveryResult.NotACodecError
        }

        val currentAttempts = recoveryAttempts[slot] ?: 0
        if (currentAttempts >= 2) {
            Log.w(TAG, "recoverFromPlaybackError: attempts exhausted for slot $slot (id=$id)")
            return RecoveryResult.Exhausted
        }
        recoveryAttempts[slot] = currentAttempts + 1
        val attempt = recoveryAttempts[slot]!!
        // Attempt 1: hardware (with FFmpeg fallback). Attempt 2: FFmpeg preferred.
        val preferSoftware = attempt >= 2

        val player = playerFor(slot) ?: return RecoveryResult.Exhausted
        val savedPosition = player.currentPosition
        val savedPlayWhenReady = player.playWhenReady
        val url = slotUrls[slot]
        if (url == null) {
            Log.w(TAG, "recoverFromPlaybackError: no URL for slot $slot (id=$id)")
            return RecoveryResult.Exhausted
        }

        Log.d(TAG, "recoverFromPlaybackError: slot=$slot attempt=$attempt preferSoftware=$preferSoftware id=$id")

        // 1) stop() the affected player cleanly, then 2) release() it so the
        // decoder/renderers are fully torn down (no lingering codec state).
        try {
            player.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "recoverFromPlaybackError: player.stop() failed for slot $slot", e)
        }
        try {
            player.release()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "recoverFromPlaybackError: player.release() failed for slot $slot", e)
        }

        // 3) build a fresh player for the SAME slot (slot count stays == 2).
        val newPlayer = build(preferSoftware = preferSoftware)
        assignSlot(slot, newPlayer)

        // 4) re-set the MediaItem from the preserved URL; prepare; restore state.
        val mediaItem = MediaItem.fromUri(url)
        newPlayer.setMediaItem(mediaItem)
        newPlayer.repeatMode = Player.REPEAT_MODE_ALL
        newPlayer.volume = volume
        newPlayer.playWhenReady = false
        try {
            newPlayer.prepare()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Fresh player prepare failed in recoverFromPlaybackError for slot $slot", e)
        }
        if (savedPosition > 0L) {
            newPlayer.seekTo(savedPosition)
        }
        if (activeSlot == slot) {
            newPlayer.playWhenReady = savedPlayWhenReady
        }

        val rendererMode = if (preferSoftware) "software-preferred" else "hardware-fallback"
        return RecoveryResult.Recovered(newPlayer, attempt, rendererMode)
    }

    /** True when [error] represents a decoder/codec failure rather than an HTTP/IO error. */
    fun isCodecInitializationError(error: PlaybackException): Boolean {
        // An HTTP/IO error is NEVER a codec failure, even when its cause chain
        // drags decoder remnants. A signed-URL 401/403/5xx must fall through
        // to the network/401 retry path, not to player recreation.
        var h: Throwable? = error.cause
        while (h != null) {
            if (h is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) return false
            h = h.cause
        }
        if (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED) return true
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is MediaCodec.CodecException) return true
            val name = cause.javaClass.name
            if (name.endsWith("DecoderInitializationException") ||
                name.endsWith("CodecException")) return true
            cause = cause.cause
        }
        return false
    }

    /** Binds a player instance to its slot. */
    private fun assignSlot(slot: Slot, player: ExoPlayer) {
        if (slot == Slot.A) slotAPlayer = player else slotBPlayer = player
    }

    /**
     * Immediately updates the MediaItem URI of the slot currently assigned to [id]
     * with [newUrl], preserving playback position and playWhenReady state.
     *
     * Used for 401 recovery when a VCDN signed URL has expired mid-playback:
     * the player already has the expired URL baked into its MediaItem, so a
     * plain prepare() won't help. This swaps the URI and re-prepares while
     * keeping the user at the same position.
     *
     * Unlike acquireOrReuse, this does NOT defer to pendingUrlUpdates — a 401
     * means playback is already broken, so the refresh must be immediate.
     *
     * Returns true if the URL was actually updated, false if no change was needed
     * or the slot/player is unavailable (e.g. already released).
     */
    fun refreshActiveUrl(id: String, newUrl: String): Boolean {
        val slot = slotFor(id) ?: return false
        val currentUrl = slotUrls[slot]
        if (currentUrl == newUrl) return false

        // Clear any deferred update — we're applying a fresh URL now
        pendingUrlUpdates.remove(slot)

        val player = playerFor(slot) ?: return false
        val savedPosition = player.currentPosition
        val savedPlayWhenReady = player.playWhenReady
        try {
            player.setMediaItem(MediaItem.fromUri(newUrl))
            player.prepare()
            player.seekTo(savedPosition)
            player.playWhenReady = savedPlayWhenReady
            slotUrls[slot] = newUrl
            Log.d(TAG, "Refreshed URL for slot $slot (position preserved: $savedPosition, playing: $savedPlayWhenReady)")
            return true
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Player already released during URL refresh for slot $slot", e)
            return false
        }
    }

    /** The slot's currently assigned reel id,. */
    fun assignedId(slot: Slot): String? = if (slot == Slot.A) slotAAssignedId else slotBAssignedId

    /** Player for [slot], or null if never acquired. */
    fun playerFor(slot: Slot): ExoPlayer? = if (slot == Slot.A) slotAPlayer else slotBPlayer

    /** True when [slot] has already been prepped with this id. */
    fun isPreppedFor(slot: Slot, id: String): Boolean = assignedId(slot) == id

    /** The slot currently assigned to [id], or null. */
    fun slotFor(id: String): Slot? {
        return when {
            assignedId(Slot.A) == id -> Slot.A
            assignedId(Slot.B) == id -> Slot.B
            else -> null
        }
    }


    /** The first slot con no assigned reel, o null si ambos busy. */
    fun freeSlot(): Slot? = when {
        slotAAssignedId == null -> Slot.A
        slotBAssignedId == null -> Slot.B
        else -> null
    }

    /**
     * Acquires [id]'s media on the appropriate slot (reusing the slot where it
     * already lives).
     *
     * Active pages play with real volume. Preload pages enter "preload-muted":
     * the player is set to playWhenReady=true with volume=0 so it DECODES and
     * renders its first frame into its PlayerView while advancing silently in
     * the background. This is the mechanism that makes the next reel appear
     * instantly with its frame already visible (TikTok-style), and it never
     * leaks audio because the preload volume is zero.
     *
     * When there is no free slot for a preload, null is returned and the caller
     * (the feed page) retries until a slot frees up or the page leaves the
     * window. There is NO hidden pending queue: a page only ever sees a player
     * once it actually owns a slot, so no "prepared but never attached" state.
     */
    fun acquireOrReuse(id: String, url: String, active: Boolean, volume: Float): Slot? {
        val existing = slotFor(id)
        if (existing != null) {
            val currentUrl = slotUrls[existing]
            if (currentUrl != url) {
                val player = playerFor(existing)!!
                // H2 fix: do not re-prepare the active slot while playback is in
                // progress (playWhenReady=true). Re-prepping the visible player
                // causes black-screen flashes (setMediaItem+prepare discards
                // buffered ranges). Instead, queue the URL update to be applied
                // when this slot transitions to preload/inactive via pause().
                if (existing == activeSlot && player.playWhenReady) {
                    pendingUrlUpdates[existing] = url
                    Log.d(TAG, "Deferred URL update for active slot $existing") // redacted: signed HLS URL
                } else {
                    val savedPosition = player.currentPosition
                    val savedPlayWhenReady = player.playWhenReady
                    try {
                        player.setMediaItem(MediaItem.fromUri(url))
                        player.prepare()
                        player.seekTo(savedPosition)
                        player.playWhenReady = savedPlayWhenReady
                        slotUrls[existing] = url
                    } catch (e: IllegalStateException) {
                        Log.w(TAG, "Player stale during URL update in acquireOrReuse for slot $existing", e)
                    }
                }
            }
            if (active) activate(existing, volume) else promotePreload(existing)
            return existing
        }

        // Active pages always win a slot: take the free one, or steal the slot
        // that is currently in preload (never the actively-playing slot).
        if (active) {
            val free = freeSlot()
            val slot = free ?: if (activeSlot == Slot.A) Slot.B else Slot.A
            if (free == null && assignedId(slot) != null) {
                clearSlot(slot, requeue = false)
            }
            acquire(slot, id, url, volume)
            activate(slot, volume)
            return slot
        }

        // Preload: use a free slot if available, otherwise let the caller retry.
        val free = freeSlot()
        if (free != null) {
            acquire(free, id, url, volume)
            promotePreload(free)
            return free
        }
        return null
    }

    /**
     * Preloads [slot] silently: starts playback at volume 0 so the first frame
     * is decoded and the video advances in the background without making sound.
     * When the page turns active, [activate] just unmutes it.
     */
    fun promotePreload(slot: Slot) {
        val player = if (slot == Slot.A) slotAPlayer else slotBPlayer
        if (player == null) return
        player.volume = 0f
        player.playWhenReady = true
        activeSlot = null
    }

    /** Releases the slot owning [id] (real ExoPlayer release when page left the window). */
    fun releaseIfOwned(id: String) {
        when {
            assignedId(Slot.A) == id -> clearSlot(Slot.A)
            assignedId(Slot.B) == id -> clearSlot(Slot.B)
        }
    }


    /** Reclaims [slot] — stops, releases the underlying ExoPlayer and forgets its assignment. */
    private fun clearSlot(slot: Slot, requeue: Boolean = false) {
        val player = if (slot == Slot.A) slotAPlayer else slotBPlayer
        player?.stop()
        player?.release()
        if (slot == Slot.A) { slotAPlayer = null; slotAAssignedId = null; slotUrls.remove(Slot.A) } else { slotBPlayer = null; slotBAssignedId = null; slotUrls.remove(Slot.B) }
        recoveryAttempts.remove(slot)
    }

    private fun build(preferSoftware: Boolean = false): ExoPlayer {
        // Arranque veloz tipo TikTok: buffer mínimo bajo (7.5s) → el primer
        // frame sale en cuanto hay ~1s listo; los posters grandes de 30s
        // hacían esperar demasiado antes del STATE_READY. Sin SimpleCache el
        // player lee directo del socket y con 7.5s de buffer el rebuffer es
        // imperceptible en redes sanas.
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                7500,  // minBufferMs: 7.5s (rápido a READY)
                20000, // maxBufferMs: cap de 20s
                1000,  // bufferForPlaybackMs: arranca con ~1s
                2000   // bufferForPlaybackAfterRebufferMs: tras rebuffer 2s
            )
            .setBackBuffer(5000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val trackSelector = DefaultTrackSelector(context, androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection.Factory()).apply {
            setParameters(buildUponParameters().clearVideoSizeConstraints())
        }
        // CONSUMO DIRECTO VCDN: sin SimpleCache ni intermediarios. Los reels se leen
        // directo de la URL firmada (HLS VCDN) → nada se escribe a disco, no hay
        // invalidation de caché por path-key ni 401 stale entre firmas distintas.
        // El preload del slot B da el primer frame; el byte-prefetch no aplica.
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 Panalink/1.0"
            )
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(
            context,
            httpFactory
        )
        return ExoPlayer.Builder(context, PanaRenderersFactory.create(context, preferSoftware = preferSoftware))
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
            )
            .setLoadControl(loadControl)
            .build()
            .also { player ->
                player.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        val isCodec = isCodecInitializationError(error)
                        Log.e(TAG, "ReelPlayerError: position=${player.currentPosition}, buffered=${player.bufferedPosition}, state=${player.playbackState}, isLoading=${player.isLoading}, playWhenReady=${player.playWhenReady}, errorCode=${error.errorCode}, cause=${error.cause?.javaClass?.simpleName}, isCodecError=$isCodec")
                    }
                })
            }
    }

    fun releaseAll() {
        clearSlot(Slot.A, requeue = false)
        clearSlot(Slot.B, requeue = false)
    }
}