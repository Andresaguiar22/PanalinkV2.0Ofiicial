package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Scale
import androidx.core.graphics.drawable.toBitmap

/**
 * Carga imágenes para las notificaciones ricas (thumbnail de publicaciones,
 * avatares, etc.) en segundo plano (Coil enqueue, no bloquea el hilo actual)
 * y entrega el bitmap en el hilo principal.
 */
object PanaNotificationImageLoader {

    private const val TAG = "PanaNotificationImageLoader"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun loadBigImage(
        context: Context,
        url: String,
        onLoaded: (Bitmap) -> Unit,
        onError: (() -> Unit)? = null
    ) {
        if (url.isBlank()) {
            onError?.invoke()
            return
        }
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(1024, 1024)
            .scale(Scale.FIT)
            .target(
                onSuccess = { drawable ->
                    val bmp = drawable.toBitmap()
                    mainHandler.post {
                        try {
                            onLoaded(bmp)
                        } catch (e: Exception) {
                            Log.e(TAG, "onLoaded callback error: ${e.message}")
                        }
                    }
                },
                onError = {
                    mainHandler.post { onError?.invoke() }
                }
            )
            .build()
        try {
            context.imageLoader.enqueue(request)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading notification image: ${e.message}")
            mainHandler.post { onError?.invoke() }
        }
    }
}