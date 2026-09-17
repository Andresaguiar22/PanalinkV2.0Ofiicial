package com.example.live.ui.components

import android.util.Log
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

private const val TAG = "LiveVideoSurface"

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
                    // Si el track ya existe al crear el renderer, se engancha aqui y se
                    // deja constancia en attachedTrack para que el LaunchedEffect no lo
                    // vuelva a enganchar (doble addRenderer = frames duplicados).
                    videoTrack?.let { track ->
                        track.addRenderer(this)
                        attachedTrack = track
                    }
                    rendererRef = this
                    Log.d(TAG, "renderer creado (track=${videoTrack != null})")
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Cuando el track (re)aparece, adjuntarlo al renderer existente.
        LaunchedEffect(videoTrack, rendererRef) {
            val rendered = rendererRef ?: return@LaunchedEffect
            val attached = attachedTrack
            if (videoTrack != null && attached !== videoTrack) {
                attached?.removeRenderer(rendered)
                videoTrack.addRenderer(rendered)
                attachedTrack = videoTrack
                Log.d(TAG, "track enganchado al renderer")
            }
            if (videoTrack == null) {
                attached?.removeRenderer(rendered)
                attachedTrack = null
                Log.d(TAG, "track desenganchado del renderer")
            }
        }

        // OJO: la clave debe ser Unit, NUNCA rendererRef. Con `DisposableEffect(rendererRef)`
        // el paso de null -> renderer despide el efecto anterior y su onDispose lee el ref
        // YA con valor, por lo que libera el renderer recien creado y deja rendererRef en
        // null: el track que llega despues no se engancha nunca y el preview queda NEGRO.
        DisposableEffect(Unit) {
            onDispose {
                val renderer = rendererRef
                rendererRef = null
                if (renderer != null) {
                    attachedTrack?.removeRenderer(renderer)
                    attachedTrack = null
                    try { renderer.release() } catch (_: Exception) {}
                    Log.d(TAG, "renderer liberado")
                }
            }
        }
    }
}

