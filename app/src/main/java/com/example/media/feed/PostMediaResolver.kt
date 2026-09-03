package com.example.media.feed

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.media.model.MediaResource
import com.example.media.repository.MediaRepository
import com.example.media.storage.MediaStorageManager
import java.io.File

object PostMediaResolver {

    @Composable
    fun rememberResolvedMediaResources(
        mediaUrls: List<String>?,
        ownerId: String? = null
    ): List<MediaResource> {
        if (mediaUrls.isNullOrEmpty()) return emptyList()

        val context = LocalContext.current
        val repository = remember {
            val storage = MediaStorageManager(context.applicationContext)
            MediaRepository(context.applicationContext, storage)
        }

        return mediaUrls.map { remoteUrl ->
            if (remoteUrl.startsWith("file://") || remoteUrl.startsWith("/")) {
                val file = File(remoteUrl.replace("file://", ""))
                if (file.exists()) {
                    MediaResource.Local(file.absolutePath)
                } else {
                    MediaResource.Missing
                }
            } else {
                val mediaId = remember(remoteUrl) {
                    "media_${kotlin.math.abs(remoteUrl.hashCode())}"
                }

                // Observe Room una sola vez por media id: re-suscribirse en cada
                // recomposición de scroll (sube/baja rápido) apilaba observers de Room por
                // item y disparaba syncs duplicados —el origen del "se queda como pegado".
                val mediaState by remember(mediaId) {
                    repository.observeMedia(mediaId)
                }.collectAsStateWithLifecycle(initialValue = null)

                // Sync one-shot por media (aunque el item salga y vuelva del viewport no
                // se re-descarga; el archivo local ya existe y Coil/ExoPlayer lo usan directo..
                var syncStarted by remember(mediaId) { mutableStateOf(false) }
                LaunchedEffect(mediaId, mediaState?.localPath) {
                    val local = mediaState?.localPath?.takeIf { !it.isNullOrBlank() && File(it).exists() }
                    if (!syncStarted && local == null) {
                        syncStarted = true
                        val type = if (remoteUrl.endsWith(".mp4") || remoteUrl.contains("video")) "video" else "image"
                        repository.syncManager.syncMedia(mediaId, remoteUrl, type, ownerId)
                    }
                }

                remember(mediaState, remoteUrl) {
                    val localPath = mediaState?.localPath
                    if (!localPath.isNullOrBlank()) {
                        val file = File(localPath)
                        if (file.exists() && file.length() > 0) {
                            MediaResource.Local(file.absolutePath)
                        } else {
                            MediaResource.Remote(remoteUrl)
                        }
                    } else if (mediaState?.syncState == "FAILED") {
                        MediaResource.Remote(remoteUrl)
                    } else {
                        MediaResource.Remote(remoteUrl)
                    }
                }
            }
        }
    }
}
