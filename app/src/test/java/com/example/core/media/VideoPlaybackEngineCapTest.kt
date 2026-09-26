package com.example.core.media

import com.example.core.media.VideoPlaybackEngine.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the stutter fix: no video player in the app may decode at full source
 * resolution, because decoding 1080p/4K on a 1080p panel is what caused dropped
 * frames. Every video player must carry a `setMaxVideoSize` cap.
 *
 * This drives [VideoPlaybackEngine.maxDecodeEdgeFor], the real production formula,
 * with real panel sizes. It is a plain JVM test on purpose: an earlier Robolectric
 * version of it destabilised the whole suite (Robolectric's own font loading threw
 * `FileSystemAlreadyExistsException` during environment setup, before any test body
 * ran), and the formula needs no Android runtime to be verified.
 */
class VideoPlaybackEngineCapTest {

    /** Real long edges: 720p/1080p phones, a 1440p panel and a 4K panel. */
    private val panels = listOf(1280, 1920, 2400, 2560, 3840)

    /**
     * The cap is `max(floor, panelLongEdge * factor)` per profile. The floor keeps a
     * small/low-density panel from capping below a usable resolution; the factor
     * keeps a 4K source from being decoded at full size on a 1080p panel.
     */
    @Test
    fun capMatchesThePerProfileContract() {
        panels.forEach { panel ->
            assertEquals(
                "cap de PREVIEW en panel $panel",
                maxOf((panel * 0.75f).toInt(), 960),
                VideoPlaybackEngine.maxDecodeEdgeFor(panel, Profile.PREVIEW)
            )
            listOf(Profile.VIEWER, Profile.REELS, Profile.STORY).forEach { profile ->
                assertEquals(
                    "cap de $profile en panel $panel",
                    maxOf((panel * 1.2f).toInt(), 1280),
                    VideoPlaybackEngine.maxDecodeEdgeFor(panel, profile)
                )
            }
        }
    }

    /**
     * The whole point of the fix: a 4K source (3840x2160, longest edge 3840) must
     * never be decoded at its source size on a phone panel. The cap is on the
     * longest edge, so 3840 is the number that matters — not the 2160 height.
     */
    @Test
    fun a4kSourceIsNeverDecodedAtFullSize() {
        val sourceLongEdge = 3840
        panels.filter { it < sourceLongEdge }.forEach { panel ->
            Profile.entries.forEach { profile ->
                val cap = VideoPlaybackEngine.maxDecodeEdgeFor(panel, profile)
                assertTrue(
                    "cap=$cap decodifica un 4K a tamano completo en panel $panel ($profile)",
                    cap < sourceLongEdge
                )
            }
        }
    }

    /** Preview renders inside a scrolling list, so it must be capped harder. */
    @Test
    fun previewIsCappedHarderThanFullScreenProfiles() {
        panels.forEach { panel ->
            val preview = VideoPlaybackEngine.maxDecodeEdgeFor(panel, Profile.PREVIEW)
            val viewer = VideoPlaybackEngine.maxDecodeEdgeFor(panel, Profile.VIEWER)
            val reels = VideoPlaybackEngine.maxDecodeEdgeFor(panel, Profile.REELS)

            assertTrue("preview($preview) debe ser menor que viewer($viewer) en panel $panel", preview < viewer)
            assertTrue("preview($preview) debe ser menor que reels($reels) en panel $panel", preview < reels)
        }
    }

    /** The floors hold even on a tiny/low-density panel. */
    @Test
    fun tinyPanelsStillGetAUsableFloor() {
        assertEquals(960, VideoPlaybackEngine.maxDecodeEdgeFor(0, Profile.PREVIEW))
        assertEquals(960, VideoPlaybackEngine.maxDecodeEdgeFor(400, Profile.PREVIEW))
        assertEquals(1280, VideoPlaybackEngine.maxDecodeEdgeFor(0, Profile.VIEWER))
        assertEquals(1280, VideoPlaybackEngine.maxDecodeEdgeFor(400, Profile.VIEWER))
    }

    /** Bitrate ceilings stay in a sane band: enough for 1080p, never unbounded. */
    @Test
    fun bitrateCeilingsAreBounded() {
        Profile.entries.forEach { profile ->
            val bitrate = VideoPlaybackEngine.maxBitrate(profile)
            assertTrue("bitrate=$bitrate fuera de rango para $profile", bitrate in 1_000_000..20_000_000)
        }
    }
}
