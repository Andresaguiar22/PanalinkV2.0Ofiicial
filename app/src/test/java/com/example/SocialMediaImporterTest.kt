package com.example

import com.example.data.repository.SocialMediaImporter
import org.junit.Assert.*
import org.junit.Test

class SocialMediaImporterTest {

    @Test
    fun supportedPlatformUrlsAreValid() {
        assertTrue(SocialMediaImporter.isValidPlatformUrl("https://www.tiktok.com/@user/video/12345"))
        assertTrue(SocialMediaImporter.isValidPlatformUrl("https://vm.tiktok.com/abc123/"))
        assertTrue(SocialMediaImporter.isValidPlatformUrl("https://www.instagram.com/reel/ABC123/"))
        assertTrue(SocialMediaImporter.isValidPlatformUrl("https://youtube.com/shorts/dQw4w9WgXcQ"))
        assertTrue(SocialMediaImporter.isValidPlatformUrl("https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(SocialMediaImporter.isValidPlatformUrl("https://x.com/user/status/123"))
        assertTrue(SocialMediaImporter.isValidPlatformUrl("https://www.reddit.com/r/videos/comments/abc/"))
    }

    @Test
    fun invalidOrUnsupportedUrlsAreRejected() {
        assertFalse(SocialMediaImporter.isValidPlatformUrl(null))
        assertFalse(SocialMediaImporter.isValidPlatformUrl(""))
        assertFalse(SocialMediaImporter.isValidPlatformUrl("ftp://tiktok.com/video/123"))
        assertFalse(SocialMediaImporter.isValidPlatformUrl("tiktok.com/video/123"))
        assertFalse(SocialMediaImporter.isValidPlatformUrl("https://example.com/video/123"))
        assertFalse(SocialMediaImporter.isValidPlatformUrl("https://vimeo.com/12345"))
        assertFalse(SocialMediaImporter.isValidPlatformUrl("https://tiktok.com.evil.com/video/123"))
    }

    @Test
    fun httpSchemeIsAccepted() {
        assertTrue(SocialMediaImporter.isValidPlatformUrl("http://www.tiktok.com/@user/video/123"))
    }
}