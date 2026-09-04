package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.CdnManager
import com.example.util.NetworkMonitor
import com.example.worker.MediaUploadWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatVideoRoutingTest {

    @Test
    fun testRoutingDecision_simpleVideoType() {
        assertTrue(MediaUploadWorker.shouldRouteVideoToVcdn("video", "video/mp4"))
        assertTrue(MediaUploadWorker.shouldRouteVideoToVcdn("video", null))
        assertTrue(MediaUploadWorker.shouldRouteVideoToVcdn(null, "video/mp4"))
        assertTrue(MediaUploadWorker.shouldRouteVideoToVcdn("VIDEO", "video/quicktime"))
        assertTrue(MediaUploadWorker.shouldRouteVideoToVcdn("video/mp4", "video/mp4"))
    }

    @Test
    fun testRoutingDecision_nonVideoStaysOnB2() {
        assertFalse(MediaUploadWorker.shouldRouteVideoToVcdn("image", "image/jpeg"))
        assertFalse(MediaUploadWorker.shouldRouteVideoToVcdn("audio", "audio/m4a"))
        assertFalse(MediaUploadWorker.shouldRouteVideoToVcdn("document", "application/pdf"))
        assertFalse(MediaUploadWorker.shouldRouteVideoToVcdn("text", "text/plain"))
        assertFalse(MediaUploadWorker.shouldRouteVideoToVcdn(null, null))
    }

    @Test
    fun testRoutingDecision_edgeCases() {
        assertFalse(MediaUploadWorker.shouldRouteVideoToVcdn("", ""))
        // " video " se normaliza (trim+lowercase) a "video": es un vídeo válido que debe ir a vCDN
        assertTrue(MediaUploadWorker.shouldRouteVideoToVcdn(" video ", null))
        assertTrue(MediaUploadWorker.shouldRouteVideoToVcdn(" video ", "video/mp4"))
    }

    @Test
    fun testViewerResolution_b2HttpUrlIsPreserved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CdnManager.init(context)
        assertEquals(
            "https://f002.backblazeb2.com/file/panalink/chat_video.mp4",
            CdnManager.resolveMediaUrlSync("https://f002.backblazeb2.com/file/panalink/chat_video.mp4")
        )
        assertEquals(
            "https://cdn.example.com/videos/v1.mp4",
            CdnManager.resolveMediaUrlSync("https://cdn.example.com/videos/v1.mp4")
        )
    }

    @Test
    fun testViewerResolution_vcdnPointerWithoutNetworkResolvesEmpty() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CdnManager.init(context)
        // En tests no hay callback de red registrado: NetworkMonitor.isOnline es false por defecto
        // y VcdnUrlResolver falla offline de forma determinista sin quemar red real.
        assertFalse(NetworkMonitor.isOnline.value)
        assertEquals("", CdnManager.resolveMediaUrlSync("vcdn://video-abc-123"))
    }

    @Test
    fun testViewerResolution_blankAndLocalUrlsKeepWorking() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CdnManager.init(context)
        assertEquals("", CdnManager.resolveMediaUrlSync(""))
        assertEquals("content://media/external/images/1", CdnManager.resolveMediaUrlSync("content://media/external/images/1"))
        assertEquals("/storage/emulated/0/Panalink/video.mp4", CdnManager.resolveMediaUrlSync("/storage/emulated/0/Panalink/video.mp4"))
    }
}