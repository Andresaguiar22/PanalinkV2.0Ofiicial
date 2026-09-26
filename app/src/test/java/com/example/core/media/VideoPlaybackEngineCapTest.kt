package com.example.core.media

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guards the stutter fix: no video player in the app may decode at full source
 * resolution, because decoding 1080p/4K on a 1080p panel is what caused dropped
 * frames. Every video player must carry a `setMaxVideoSize` cap, and the cap must
 * actually reach the player that gets built.
 */
@RunWith(RobolectricTestRunner::class)
class VideoPlaybackEngineCapTest {

    private val context: android.content.Context = ApplicationProvider.getApplicationContext()

    /**
     * The cap is `max(floor, panelLongEdge * factor)` per profile. The floor keeps a
     * small/low-density panel from capping below a usable resolution; the factor
     * keeps a 4K source from being decoded at full size on a 1080p panel.
     * PREVIEW is intentionally the most aggressive because it renders in a list.
     */
    @Test
    fun decodeCapMatchesPerProfileContract() {
        val metrics = context.resources.displayMetrics
        val longEdge = maxOf(metrics.widthPixels, metrics.heightPixels)

        VideoPlaybackEngine.Profile.entries.forEach { profile ->
            val cap = VideoPlaybackEngine.maxDecodeEdge(context, profile)
            val expected = if (profile == VideoPlaybackEngine.Profile.PREVIEW) {
                maxOf((longEdge * 0.75f).toInt(), 960)
            } else {
                maxOf((longEdge * 1.2f).toInt(), 1280)
            }
            assertEquals("cap inesperado para $profile (panel longEdge=$longEdge)", expected, cap)
        }
    }

    /** Preview renders inside a scrolling list, so it must be capped harder. */
    @Test
    fun previewIsCappedHarderThanFullScreenProfiles() {
        val preview = VideoPlaybackEngine.maxDecodeEdge(context, VideoPlaybackEngine.Profile.PREVIEW)
        val viewer = VideoPlaybackEngine.maxDecodeEdge(context, VideoPlaybackEngine.Profile.VIEWER)
        val reels = VideoPlaybackEngine.maxDecodeEdge(context, VideoPlaybackEngine.Profile.REELS)

        assertTrue("preview($preview) debe ser menor que viewer($viewer)", preview < viewer)
        assertTrue("preview($preview) debe ser menor que reels($reels)", preview < reels)
    }

    /** Bitrate ceilings stay in a sane band: enough for 1080p, never unbounded. */
    @Test
    fun bitrateCeilingsAreBounded() {
        VideoPlaybackEngine.Profile.entries.forEach { profile ->
            val bitrate = VideoPlaybackEngine.maxBitrate(profile)
            assertTrue("bitrate=$bitrate fuera de rango para $profile", bitrate in 1_000_000..20_000_000)
        }
    }

    /**
     * The built player must actually carry the cap. If someone swaps the track
     * selector back to `clearVideoSizeConstraints()`, this fails.
     */
    @Test
    fun builtPlayerCarriesTheDecodeCap() {
        VideoPlaybackEngine.Profile.entries.forEach { profile ->
            val player: ExoPlayer = VideoPlaybackEngine.build(context, profile)
            try {
                val selector = player.trackSelector
                assertNotNull("$profile no tiene TrackSelector", selector)
                val params = (selector as DefaultTrackSelector).parameters
                val cap = VideoPlaybackEngine.maxDecodeEdge(context, profile)

                // A null max size means "no constraint" = full source resolution.
                assertNotNull("$profile decodifica sin limite de tamano", params.maxVideoWidth)
                assertEquals("ancho cap de $profile", cap, params.maxVideoWidth)
                assertEquals("alto cap de $profile", cap, params.maxVideoHeight)
            } finally {
                player.release()
            }
        }
    }
}
