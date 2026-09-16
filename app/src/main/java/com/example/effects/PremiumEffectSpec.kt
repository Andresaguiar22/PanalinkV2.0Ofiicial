package com.example.effects

import androidx.compose.ui.graphics.Color

/** Tipo de efecto premium según la zona en la que se muestra. */
enum class PremiumKind {
    GIFT_FULLSCREEN,
    GIFT_BANNER,
    PENDANT,
    ENTRANCE
}

/** Ráfaga de partículas (brillos dorados, fuegos artificiales). */
data class PremiumBurst(
    val count: Int,
    val innerRadius: Float,
    val outerRadius: Float,
    val minSize: Float,
    val maxSize: Float,
    val spinSpeed: Float,
    val gravity: Float = 0f
)

/** Rayos de luz que emanan del centro. */
data class PremiumRays(
    val count: Int,
    val minLength: Float,
    val maxLength: Float,
    val alpha: Float,
    val rotationSpeed: Float
)

/** Anillos expansivos (shockwave / onda). */
data class PremiumRings(
    val count: Int,
    val maxRadius: Float,
    val startAlpha: Float
)

/** Especificación de un efecto premium de alta gama. */
data class PremiumEffectSpec(
    val kind: PremiumKind,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val burst: PremiumBurst,
    val rays: PremiumRays,
    val rings: PremiumRings,
    val glowBlurRadius: Float = 30f,
    val emoji: String = "✨",
    val title: String? = null,
    val durationMs: Long = 2600L,
    val lottieAsset: String? = null,
    val lottieUrl: String? = null
)

/** Chispa individual de alta resolución (para brillos dorados). */
data class Sparkle(
    val x: Float,
    val y: Float,
    val size: Float,
    val twinkleSpeed: Float = 1f
)