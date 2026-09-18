package com.example.live.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.core.Preview as CameraXPreview

private const val TAG = "LiveCameraBackground"

/**
 * Mantiene viva la [ProcessCameraProvider] de la preview para poder liberar el
 * sensor de forma explicita antes de que LiveKit lo reclame al iniciar el
 * directo. Sin esto, CameraX y LiveKit pelean por la camara y el directo arranca
 * con "camara ocupada" o preview negra.
 */
class LiveCameraPreviewController {
    private var provider: ProcessCameraProvider? = null

    internal fun attach(cameraProvider: ProcessCameraProvider) {
        provider = cameraProvider
    }

    /** Suelta la camara ya. Idempotente y seguro de llamar desde el hilo principal. */
    fun releaseCamera() {
        try {
            provider?.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error liberando la camara de preview", e)
        }
    }
}

@Composable
fun rememberLiveCameraPreviewController(): LiveCameraPreviewController =
    remember { LiveCameraPreviewController() }

/**
 * Vista previa de camara a pantalla completa usada como fondo dinamico del
 * pre-live. Real (CameraX), no simulada.
 *
 * El desenfoque se aplica con [RenderEffect] directamente sobre el
 * [PreviewView] (modo COMPATIBLE -> TextureView, que si se compone en la
 * jerarquia y por tanto se puede difuminar). Requiere API 31+; en APIs
 * anteriores el fondo se ve nitido y el scrim oscuro del caller sigue
 * garantizando el contraste del contenido.
 */
@Composable
fun LiveCameraBackgroundPreview(
    controller: LiveCameraPreviewController,
    modifier: Modifier = Modifier,
    lensFacing: Int = CameraSelector.LENS_FACING_FRONT,
    blurRadiusPx: Float = 42f,
    /** Cambiar este valor fuerza a re-vincular la camara (p.ej. tras un fallo). */
    restartKey: Int = 0,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            // COMPATIBLE == TextureView: necesario para que el blur tenga efecto.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.fillMaxSize(),
    )

    LaunchedEffect(lensFacing, previewView, restartKey) {
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()
            controller.attach(cameraProvider)
            cameraProvider.unbindAll()

            val selector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()
            val preview = CameraXPreview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                previewView.setRenderEffect(
                    RenderEffect.createBlurEffect(
                        blurRadiusPx,
                        blurRadiusPx,
                        Shader.TileMode.CLAMP,
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo iniciar la preview de camara", e)
        }
    }

    DisposableEffect(Unit) {
        onDispose { controller.releaseCamera() }
    }
}