package com.example.core.media

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl

/**
 * LoadControl exclusivo para el visor de Stories.
 *
 * EXPERIMENTAL (hipotesis de mitigacion, NO causa raiz definitiva): el HLS de
 * VCDN arranca con segmentos de ~6.4s / ~4.4 MB. El buffer anterior
 * (minBufferMs=10000, bufferForPlaybackMs=200) arrancaba casi sin colchon y
 * podia re-buffer a los ~2-3s mientras descargaba el segundo segmento. Estos
 * valores dan un colchon inicial real sin esperar a llenar 10s completos.
 *
 * La instrumentacion de Fase 3 (TAG StoryVideoPlayer) confirmara si el
 * corte es realmente READY->BUFFERING o algo mas (error/codec/surface).
 */
@OptIn(UnstableApi::class)
object StoryVideoLoadControl {
    fun create(): DefaultLoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            3000,   // minBufferMs: 3s (experimental, antes 10000)
            10000,  // maxBufferMs: 10s
            1000,   // bufferForPlaybackMs: 1s colchon inicial real (antes 200ms)
            1500     // bufferForPlaybackAfterRebufferMs: 1.5s (antes 500ms)
        )
        .setBackBuffer(0, false) // Stories lineales: sin seek-back, menos uso de memoria
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
}
