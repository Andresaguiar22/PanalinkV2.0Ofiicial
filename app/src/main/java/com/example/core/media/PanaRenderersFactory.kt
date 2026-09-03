package com.example.core.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory

/**
 * RenderersFactory compartida por todos los ExoPlayer de video de la app.
 *
 * - [DefaultRenderersFactory.setEnableDecoderFallback]: si el codec de hardware
 *   falla con un video (p.ej. HEVC 10-bit/4K), ExoPlayer cae a otro decoder
 *   disponible en lugar de pintar frames corruptos.
 * - [DefaultRenderersFactory.setExtensionRendererMode] en modo ON: registra los
 *   renderers FFmpeg (lib nativa incluida via org.jellyfin.media3) DESPUES de los
 *   de plataforma. El hardware sigue decodificando H.264/HEVC/VP9 normales, pero
 *   formatos sin decoder de hardware (H.264 Hi10P/4:4:4 de capturas de juegos,
 *   HEVC Main 10/HDR10+ mal soportado por el chip, MPEG-2, WMV3, ProRes, etc.)
 *   pasan a software y se ven con los colores correctos en vez de tinte verde.
 */
@OptIn(UnstableApi::class)
object PanaRenderersFactory {
    fun create(context: Context): DefaultRenderersFactory = create(context, preferSoftware = false)

    /**
     * [preferSoftware] engages the FFmpeg extension decoders BEFORE the platform ones.
     * Normal H.264/HEVC keeps hardware decode in [create]; short-form players
     * (stories/previews( pass preferSoftware=true so 10-bit/4K/HDR content that
     * the platform adapter decodes with wrong colors ((tinte verde() falls back to
     * FFmpeg software decode with correct color conversion, at the cost of CPU.
     */
    fun create(context: Context, preferSoftware: Boolean): DefaultRenderersFactory =
        DefaultRenderersFactory(context.applicationContext)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(if (preferSoftware) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
}
