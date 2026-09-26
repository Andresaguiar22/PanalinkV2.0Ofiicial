package com.example.core.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.data.video.CacheDataSourceFactory

/**
 * Single source of truth for how every video player in the app is built.
 *
 * There used to be two incompatible stacks: the reels engine forced hardware
 * decoders and capped the decode resolution, while the Muro/feed/viewer players
 * went through `PanaRenderersFactory.create(preferSoftware = true)` with no track
 * selector at all. That second stack decoded 1080p/4K on the CPU at full source
 * resolution, which is exactly why high-bitrate posts stuttered and froze while
 * reels played fine.
 *
 * Every player now comes from here, differing only by [Profile].
 */
@OptIn(UnstableApi::class)
object VideoPlaybackEngine {

    /**
     * Playback contexts. The engine behaviour differs only in buffering policy,
     * decoder preference and how aggressively the decoded resolution is capped.
     */
    enum class Profile {
        /** Short-form full-screen reels: start almost instantly, small buffer. */
        REELS,

        /** Vertical full-screen viewer (Muro video). Long videos, wider buffer. */
        VIEWER,

        /** Inline preview inside a feed card: silent, cheap, aggressively capped. */
        PREVIEW,

        /** Story / short local preview where color accuracy matters more than CPU. */
        STORY,
    }

    /** Decoder order for a profile. */
    private fun renderers(context: Context, profile: Profile) =
        when (profile) {
            // Hardware first everywhere: only the story/preview paths opt into the
            // FFmpeg extension first, because 10-bit/HDR sources decoded by some
            // platform adapters come out green and software fixes the conversion.
            Profile.STORY -> PanaRenderersFactory.create(context, preferSoftware = true)
            else -> PanaRenderersFactory.create(context, preferSoftware = false)
        }

    /**
     * Decode cap. The panel cannot show more pixels than it has, so decoding a 4K
     * source on a 1080p screen only burns CPU and causes dropped frames.
     * [Preview] is capped harder because it renders inside a scrolling list.
     */
    private fun trackSelector(context: Context, profile: Profile): DefaultTrackSelector {
        val metrics = context.resources.displayMetrics
        val longEdge = maxOf(metrics.widthPixels, metrics.heightPixels)
        val cap = when (profile) {
            Profile.PREVIEW -> (longEdge * 0.75f).toInt().coerceAtLeast(960)
            else -> (longEdge * 1.2f).toInt().coerceAtLeast(1280)
        }
        val maxBitrate = when (profile) {
            Profile.PREVIEW -> 8_000_000
            else -> 20_000_000
        }
        return DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(cap, cap)
                    .setMaxVideoBitrate(maxBitrate)
            )
        }
    }

    private fun loadControl(profile: Profile): DefaultLoadControl = when (profile) {
        // Start at ~300ms of buffer so a swipe feels instant, then allow a wide
        // rebuffer margin so a long video survives a bandwidth dip.
        Profile.REELS -> DefaultLoadControl.Builder()
            .setBufferDurationsMs(10_000, 60_000, 300, 6_000)
            .setBackBuffer(4_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Long videos: start quickly but keep a big forward buffer so a 10-minute
        // 1080p video does not stall halfway through.
        Profile.VIEWER -> DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 90_000, 500, 8_000)
            .setBackBuffer(10_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Inline previews scroll in and out constantly; a small buffer avoids
        // holding megabytes per off-screen card.
        Profile.PREVIEW -> DefaultLoadControl.Builder()
            .setBufferDurationsMs(5_000, 20_000, 500, 3_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        Profile.STORY -> DefaultLoadControl.Builder()
            .setBufferDurationsMs(8_000, 40_000, 300, 4_000)
            .setBackBuffer(2_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    /**
     * Builds a player for [profile]. The cache/HTTP data source is shared with the
     * rest of the app so a video downloaded for the feed is served from disk by the
     * viewer instead of being fetched again.
     */
    fun build(context: Context, profile: Profile): ExoPlayer {
        val dataSourceFactory = DefaultDataSource.Factory(
            context,
            CacheDataSourceFactory.getCacheDataSourceFactory(context)
        )
        return ExoPlayer.Builder(context, renderers(context, profile))
            .setTrackSelector(trackSelector(context, profile))
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
            )
            .setLoadControl(loadControl(profile))
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                playWhenReady = false
                repeatMode = if (profile == Profile.REELS) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            }
    }
}
