package com.example.live.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.track.VideoTrack
import livekit.org.webrtc.RendererCommon

@Composable
fun LiveVideoSurface(
    videoTrack: VideoTrack?,
    initRenderer: ((SurfaceViewRenderer) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        var rendererRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
        var attachedTrack by remember { mutableStateOf<VideoTrack?>(null) }

        // El renderer se crea SIEMPRE (aunque el track todavía no exista). LiveKit
        // requiere que el SurfaceViewRenderer exista e inicializado con el EglBase
        // del Room (Room.initVideoRenderer) ANTES de que el VideoTrack llegue;
        // si se crea solo cuando videoTrack != null, el preview puede quedar negro
        // ("la cámara no se activa") aunque el track ya esté publicado.
        AndroidView(
            factory = { viewContext ->
                SurfaceViewRenderer(viewContext).apply {
                    initRenderer?.invoke(this)
                    setEnableHardwareScaler(true)
                    setMirror(false)
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    videoTrack?.addRenderer(this)
                    rendererRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Cuando el track (re)aparece, adjuntarlo al renderer existente.
        LaunchedEffect(videoTrack, rendererRef) {
            val rendered = rendererRef ?: return@LaunchedEffect
            val attached = attachedTrack
            if (videoTrack != null && attached != videoTrack) {
                attached?.removeRenderer(rendered)
                videoTrack.addRenderer(rendered)
                attachedTrack = videoTrack
            }
            if (videoTrack == null) {
                attached?.removeRenderer(rendered)
                attachedTrack = null
            }
        }

        DisposableEffect(rendererRef) {
            onDispose {
                val renderer = rendererRef
                rendererRef = null
                if (renderer != null) {
                    attachedTrack?.removeRenderer(renderer)
                    attachedTrack = null
                    try { renderer.release() } catch (_: Exception) {}
                }
            }
        }
    }
}

