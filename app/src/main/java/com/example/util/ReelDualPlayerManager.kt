package com.example.util

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.core.media.PanaRenderersFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.data.video.CacheDataSourceFactory
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

    /** Acquires the player for [slot] and prepares [url]. If the slot already held this
     *  media, it is returned untouched (first frame already rendered. */
    fun acquire(slot: Slot, id: String, url: String, volume: Float): ExoPlayer {
        val player = when (slot) {
            Slot.A -> slotAPlayer ?: build().also { slotAPlayer = it }
            Slot.B -> slotBPlayer ?: build().also { slotBPlayer = it }
        }
        val assigned = if (slot == Slot.A) slotAAssignedId else slotBAssignedId
        if (assigned != id) {
            val mediaItem = MediaItem.fromUri(url)
            player.setMediaItem(mediaItem)
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
        player.playWhenReady = false
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
     * Acquires [id] s media on the appropriate slot (reusing the slot where it
     * already lives). Active pages play; preload pages stay prepped but
     * paused with their first frame already rendered into their PlayerView.
     */
    private data class ReelPreloadEntry(val id: String, val url: String, val volume: Float)
    private val pendingPreloads = java.util.ArrayDeque<ReelPreloadEntry>()

    fun acquireOrReuse(id: String, url: String, active: Boolean, volume: Float): Slot? {
        val existing = slotFor(id)
        if (existing != null) {
            val currentUrl = slotUrls[existing]
            if (currentUrl != url) {
                val player = playerFor(existing)!!
                val savedPosition = player.currentPosition
                val savedPlayWhenReady = player.playWhenReady
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.seekTo(savedPosition)
                player.playWhenReady = savedPlayWhenReady
                slotUrls[existing] = url
            }
            if (active) activate(existing, volume)
            return existing
        }
        val freeSlot = freeSlot()
        if (!active && freeSlot == null) {
            if (pendingPreloads.none { it.id == id }) {
                pendingPreloads.addLast(ReelPreloadEntry(id, url, volume))
            }
            return null
        }
        val slot = if (active) {
            val candidate = when (activeSlot) {
                Slot.A -> Slot.B
                Slot.B -> Slot.A
                null -> freeSlot ?: Slot.A
            }
            candidate
        } else {
            freeSlot ?: (activeSlot ?: Slot.B)
        }
        val player = acquire(slot, id, url, volume)
        if (active) activate(slot, volume) else pause(slot)
        return slot
    }

    /** Releases the slot owning [id] (real ExoPlayer release when page left the window). */
    fun releaseIfOwned(id: String) {
        when {
            assignedId(Slot.A) == id -> clearSlot(Slot.A)
            assignedId(Slot.B) == id -> clearSlot(Slot.B)
        }
    }


    private fun clearSlot(slot: Slot, requeue: Boolean = true) {
        val player = if (slot == Slot.A) slotAPlayer else slotBPlayer
        player?.stop()
        player?.release()
        if (slot == Slot.A) { slotAPlayer = null; slotAAssignedId = null; slotUrls.remove(Slot.A) } else { slotBPlayer = null; slotBAssignedId = null; slotUrls.remove(Slot.B) }
        if (!requeue) return
        val pending = pendingPreloads.pollFirst() ?: return
        acquire(slot, pending.id, pending.url, pending.volume)
        pause(slot)
    }

    private fun build(): ExoPlayer {
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                8000, // minBufferMs
                20000, // maxBufferMs
                200,   // bufferForPlaybackMs: instant start
                400    // bufferForPlaybackAfterRebufferMs
            )
            .setBackBuffer(3000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val trackSelector = DefaultTrackSelector(context, androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection.Factory()).apply {
            setParameters(buildUponParameters().clearVideoSizeConstraints())
        }
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(
            context,
            CacheDataSourceFactory.getCacheDataSourceFactory(context)
        )
        return ExoPlayer.Builder(context, PanaRenderersFactory.create(context, preferSoftware = true))
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
            )
            .setLoadControl(loadControl)
            .build()
            .also { player ->
                player.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        val uri = player.currentMediaItem?.localConfiguration?.uri?.toString()
                        Log.e(TAG, "ReelPlayerError: position=${player.currentPosition}, buffered=${player.bufferedPosition}, state=${player.playbackState}, isLoading=${player.isLoading}, playWhenReady=${player.playWhenReady}, errorCode=${error.errorCode}, cause=${error.cause?.message}, uri=$uri")
                    }
                })
            }
    }

    fun releaseAll() {
        pendingPreloads.clear()
        clearSlot(Slot.A, requeue = false)
        clearSlot(Slot.B, requeue = false)
    }
}