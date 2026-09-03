package com.example.ui.call

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.livekit.android.renderer.SurfaceViewRenderer
import livekit.org.webrtc.RendererCommon

/**
 * Local camera preview for LiveKit-routed video calls. Creates the LiveKit SDK
 * renderer (a [SurfaceViewRenderer] subclass initialized with LiveKit's own
 * EglBase by the engine via [io.livekit.android.room.Room.initVideoRenderer]).
 *
 * Unlike [LocalVideoView], this Composable does NOT call `init(eglContext)` —
 * the LiveKitCallEngine owns renderer initialization. Passing the renderer to
 * the engine happens through [onViewReady].
 */
@Composable
fun LocalLiveKitVideoView(
    onViewReady: (SurfaceViewRenderer) -> Unit,
    modifier: Modifier = Modifier
) {
    var rendererRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                setEnableHardwareScaler(true)
                setMirror(true)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                rendererRef = this
                onViewReady(this)
            }
        },
        modifier = modifier
    )

    DisposableEffect(rendererRef) {
        onDispose {
            rendererRef?.let {
                runCatching { it.release() }
            }
        }
    }
}

/**
 * Remote video view for LiveKit-routed video calls. Mirrors [LocalLiveKitVideoView]
 * but with no mirror and receives the remote participant's published track.
 */
@Composable
fun RemoteLiveKitVideoView(
    onViewReady: (SurfaceViewRenderer) -> Unit,
    modifier: Modifier = Modifier
) {
    var rendererRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                setEnableHardwareScaler(true)
                setMirror(false)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                rendererRef = this
                onViewReady(this)
            }
        },
        modifier = modifier
    )

    DisposableEffect(rendererRef) {
        onDispose {
            rendererRef?.let {
                runCatching { it.release() }
            }
        }
    }
}
