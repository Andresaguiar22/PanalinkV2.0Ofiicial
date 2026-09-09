package com.example.live.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
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
        if (videoTrack != null) {
            var rendererRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

            DisposableEffect(videoTrack) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val renderer = SurfaceViewRenderer(context).also { view ->
                    initRenderer?.invoke(view)
                    view.setEnableHardwareScaler(true)
                    view.setMirror(false)
                    view.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    videoTrack.addRenderer(view)
                    rendererRef = view
                }

                onDispose {
                    videoTrack.removeRenderer(renderer)
                    rendererRef = null
                    try { renderer.release() } catch (_: Exception) {}
                }
            }

            AndroidView(
                factory = { rendererRef ?: SurfaceViewRenderer(it) },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "Esperando señal de vídeo...",
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
    }
}
