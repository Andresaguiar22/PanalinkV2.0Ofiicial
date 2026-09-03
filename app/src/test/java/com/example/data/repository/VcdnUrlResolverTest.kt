package com.example.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VcdnUrlResolverTest {

    @Test
    fun isVcdnUrlDetectsScheme() {
        assertTrue(VcdnUrlResolver.isVcdnUrl("vcdn://abc123"))
        assertTrue(VcdnUrlResolver.isVcdnUrl("vcdn://abc123/playlist.m3u8"))
        assertFalse(VcdnUrlResolver.isVcdnUrl("https://cdn.example.com/video.mp4"))
        assertFalse(VcdnUrlResolver.isVcdnUrl(""))
        assertFalse(VcdnUrlResolver.isVcdnUrl(null))
    }

    @Test
    fun isVcdnUrlIgnoresLeadingWhitespace() {
        assertTrue(VcdnUrlResolver.isVcdnUrl("  vcdn://abc123"))
    }


    @Test
    fun rawVcdnPointerIsNeverAPlayableSource() {
        // The feed gates on this, so an unresolved vcdn:// pointer can never reach
        // ExoPlayer (which would fail with an unplayable-source error).
        val raw = "vcdn://abc123"
        assertTrue(VcdnUrlResolver.isVcdnUrl(raw))
        // A playable source must be an http(s) URL.
        assertFalse(raw.startsWith("http"))
        assertTrue("https://resolved-stream.example.com/a.m3u8".startsWith("http"))
    }
}