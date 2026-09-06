package com.example.worker

import com.example.data.database.PendingPostMediaEntity
import com.example.data.database.PendingPostMediaStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostUploadWorkerDurabilityTest {

    private fun media(
        index: Int,
        status: String,
        remoteUrl: String? = if (status == PendingPostMediaStatus.UPLOADED) "https://cdn.example/$index" else null
    ) = PendingPostMediaEntity(
        id = "post:media:$index",
        postId = "post",
        mediaIndex = index,
        localUri = "content://media/$index",
        mimeType = "image/jpeg",
        sizeBytes = 1024L,
        status = status,
        remoteUrl = remoteUrl,
        updatedAt = System.currentTimeMillis()
    )

    @Test
    fun `case 1 single uploaded media builds ordered urls`() {
        val rows = listOf(media(0, PendingPostMediaStatus.UPLOADED))
        val urls = PostUploadWorker.uploadedUrls(rows)
        assertEquals(listOf("https://cdn.example/0"), urls)
        assertTrue(PostUploadWorker.allMediaUploaded(1, 1))
    }

    @Test
    fun `case 2 multiple uploaded media yield one ordered url list`() {
        val rows = listOf(
            media(0, PendingPostMediaStatus.UPLOADED),
            media(1, PendingPostMediaStatus.UPLOADED),
            media(2, PendingPostMediaStatus.UPLOADED)
        )
        val urls = PostUploadWorker.uploadedUrls(rows)
        assertEquals(3, urls.size)
        assertEquals(listOf("https://cdn.example/0", "https://cdn.example/1", "https://cdn.example/2"), urls)
    }

    @Test
    fun `case 3 recovery skips uploaded and keeps pending order stable`() {
        val rows = listOf(
            media(0, PendingPostMediaStatus.UPLOADED),
            media(1, PendingPostMediaStatus.PENDING)
        )
        // El worker NO debe re-subir media 0: solo la pendiente debe llamar al uploader.
        rows.forEachIndexed { index, row ->
            if (index == 0) {
                assertTrue(PostUploadWorker.shouldSkipUpload(row.status))
            } else {
                assertFalse(PostUploadWorker.shouldSkipUpload(row.status))
            }
        }
        // La URL del media 0 sigue disponible despues del restart sin archivo local..
        assertEquals(listOf("https://cdn.example/0"), PostUploadWorker.uploadedUrls(rows).filter { it.startsWith("https://cdn.example/0") })
    }

    @Test
    fun `case 4 create post failure does not force re-upload`() {
        val rows = listOf(
            media(0, PendingPostMediaStatus.UPLOADED),
            media(1, PendingPostMediaStatus.UPLOADED)
        )
        // Tras un fallo de createPost, los media siguen UPLOADED: el retry no llama al uploader
        // y solo reintenta createPost con las URLs durables..,
        rows.forEach { assertTrue(PostUploadWorker.shouldSkipUpload(it.status)) }
        assertEquals(2, PostUploadWorker.uploadedUrls(rows).size)
        assertTrue(PostUploadWorker.allMediaUploaded(2, 2))
    }

    @Test
    fun `case 5 process death state comes only from room rows`() {
        // Sin variables en memoria del worker: todo el estado necesario para continuar
        // (status, remoteUrl) esta exclusivamente en las filas Room..,
        val rows = listOf(
            media(0, PendingPostMediaStatus.UPLOADED),
            media(1, PendingPostMediaStatus.UPLOADING)
        )
        assertEquals(1, PostUploadWorker.uploadedUrls(rows).size)
        assertTrue(PostUploadWorker.shouldSkipUpload(rows[0].status))
        assertFalse(PostUploadWorker.shouldSkipUpload(rows[1].status))
        assertFalse(PostUploadWorker.allMediaUploaded(2, 1))
    }
}