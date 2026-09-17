package com.example.live.ui.components

import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview as CameraXPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val TAG = "LiveCameraPreview"

/**
 * Vista previa real de CameraX a pantalla completa para la pantalla de configuración
 * previa al directo.
 *
 * Importante para no romper el arranque del Live: LiveKit abre la cámara al activar el
 * track local, y Android sólo admite un cliente por cámara. Por eso [active] debe pasar a
 * `false` antes de conectar con LiveKit; al hacerlo esta vista desvincula el caso de uso y
 * libera la cámara.
 *
 * Se usa [PreviewView.ImplementationMode.COMPATIBLE] (TextureView en vez de SurfaceView):
 * es lo que permite aplicar un efecto de blur sobre la preview desde Compose.
 */
@Composable
fun LiveCameraPreviewBackground(
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val previewUseCase = remember { CameraXPreview.Builder().build() }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(active, lifecycleOwner) {
        val provider = runCatching { ProcessCameraProvider.getInstance(context).get() }
            .getOrElse {
                Log.e(TAG, "No se pudo obtener el ProcessCameraProvider", it)
                return@LaunchedEffect
            }
        cameraProvider = provider

        if (!active) {
            runCatching { provider.unbindAll() }
            Log.i(TAG, "CameraX desvinculado: la cámara queda libre para LiveKit")
            return@LaunchedEffect
        }

        val selector = if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        runCatching {
            provider.unbindAll()
            previewUseCase.setSurfaceProvider(previewView.surfaceProvider)
            provider.bindToLifecycle(lifecycleOwner, selector, previewUseCase)
        }.onFailure {
            // Nunca debe tumbar la pantalla: sin preview el fondo queda oscuro, pero el
            // usuario todavía puede iniciar el directo.
            Log.e(TAG, "No se pudo enlazar la preview de CameraX", it)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraProvider?.unbindAll() }
        }
    }

    // La vista se mantiene siempre en el árbol (oculta cuando la cámara está liberada):
    // reinsertarla tras cada unbind reventaría por "child already has a parent".
    AndroidView(
        factory = { previewView },
        modifier = modifier.alpha(if (active) 1f else 0f),
    )
}
