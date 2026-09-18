package com.example.live.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onGloballyPositioned
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
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Black
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        var rendererRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
        var attachedTrack by remember { mutableStateOf<VideoTrack?>(null) }
        var surfaceReady by remember { mutableStateOf(false) }

        // El renderer se crea SIEMPRE (aunque el track todavía no exista). LiveKit
        // exige que el SurfaceViewRenderer exista e inicializado con el EglBase del
        // Room (Room.initVideoRenderer) ANTES de que lleguen frames; si el track se
        // engancha a un renderer sin inicializar, webrtc descarta cada frame
        // ("Received frame when not initialized!") y el preview queda NEGRO para
        // siempre aunque la camara este publicando.
        //
        // La inicializacion NO depende de que la surface exista (EglRenderer crea el
        // EGLSurface en su propio SurfaceHolder.Callback): lo que exige webrtc es el
        // HILO PRINCIPAL. Por eso initRenderer se invoca aqui (onGloballyPositioned,
        // main thread) y LiveKitManager.initVideoRenderer marshalea a Main y reintenta
        // hasta que la Room exista. Ver el comentario de ese metodo.
        AndroidView(
            factory = { viewContext ->
                val rv = SurfaceViewRenderer(viewContext)
                rv.apply {
                    setMirror(false)
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    if (videoTrack != null) {
                        videoTrack.addRenderer(rv)
                        attachedTrack = videoTrack
                    }
                    Log.d(TAG, "renderer creado (track=${videoTrack != null})")
                }
                rendererRef = rv
                rv
            },
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    // La vista ya esta medida: momento seguro para inicializar (y
                    // estamos en el hilo principal, que es lo que webrtc comprueba).
                    if (rendererRef != null && !surfaceReady) {
                        surfaceReady = true
                        try {
                            initRenderer?.invoke(rendererRef!!)
                            Log.d(TAG, "initRenderer invocado (thread=${Thread.currentThread().name})")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error init renderer", e)
                        }
                    }
                },
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

