package com.example.effects

import androidx.compose.ui.graphics.Color

/**
 * Catálogo de efectos premium estilo StarMaker/BIGO para salas de voz y live.
 *
 * Estrategia de renderizado por capas de alta eficiencia:
 *  1. Formato vectorial procedural -> [PremiumEffectSpec] (sin assets Lottie).
 *  2. Z-index independiente -> el consumidor dibuja el effecto en su propio
 *     Box/overlay, por encima del avatar.
 *  3. GPU -> [Canvas] + `graphicsLayer` (Skia -> HWUI) que acelera partículas,
 *     rayos y anillos a 60fps aunque haya varios avatares.
 *  4. Sincronización -> el servidor solo indica `code` (como hoy con
 *     voice_room_decor / gifts Realtime) y la app activa la animación local.
 *
 * El catálogo mantiene compatibilidad total con los `code` del backend.
 */

object PremiumEffectsCatalog {

    // ==== GIFT CODES (public.live_gifts) ====
    val FULL_SCREEN: Set<String> = setOf("galaxy", "lion", "tiger", "airplane", "submarine", "rocket")

    fun giftSpec(code: String?): PremiumEffectSpec = when (code) {
        "galaxy" -> PremiumEffectSpec(
            kind = PremiumKind.GIFT_FULLSCREEN,
            primary = Color(0xFF7B5CFF),
            secondary = Color(0xFFA03BFA),
            accent = Color(0xFFFF6EC7),
            burst = PremiumBurst(44, 40f, 340f, 8f, 30f, 90f),
            rays = PremiumRays(22, 180f, 320f, 0.7f, 40f),
            rings = PremiumRings(3, 260f, 0.8f),
            emoji = "🌌",
            title = "GALAXY"
        )
        "lion" -> PremiumEffectSpec(
            kind = PremiumKind.GIFT_FULLSCREEN,
            primary = Color(0xFFFFD54F),
            secondary = Color(0xFFFFB300),
            accent = Color(0xFFFF8F00),
            burst = PremiumBurst(38, 50f, 300f, 10f, 28f, 70f),
            rays = PremiumRays(18, 160f, 280f, 0.75f, 30f),
            rings = PremiumRings(2, 240f, 0.9f),
            emoji = "🦁",
            title = "LEÓN DORADO"
        )
        "tiger" -> PremiumEffectSpec(
            kind = PremiumKind.GIFT_FULLSCREEN,
            primary = Color(0xFFFFA726),
            secondary = Color(0xFFE65100),
            accent = Color(0xFFFFD54F),
            burst = PremiumBurst(36, 50f, 280f, 10f, 30f, 80f),
            rays = PremiumRays(20, 150f, 260f, 0.7f, 50f),
            rings = PremiumRings(2, 220f, 0.9f),
            emoji = "🐯",
            title = "TIGRE"
        )
        "airplane" -> PremiumEffectSpec(
            kind = PremiumKind.GIFT_FULLSCREEN,
            primary = Color(0xFF4FC3F7),
            secondary = Color(0xFFB3E5FC),
            accent = Color(0xFFFFFFFF),
            burst = PremiumBurst(28, 40f, 240f, 6f, 20f, 60f),
            rays = PremiumRays(16, 140f, 220f, 0.6f, 25f),
            rings = PremiumRings(2, 200f, 0.8f),
            emoji = "✈️",
            title = "AVIÓN"
        )
        "submarine" -> PremiumEffectSpec(
            kind = PremiumKind.GIFT_FULLSCREEN,
            primary = Color(0xFF26C6DA),
            secondary = Color(0xFF0097A7),
            accent = Color(0xFF80DEEA),
            burst = PremiumBurst(28, 40f, 240f, 6f, 20f, 60f),
            rays = PremiumRays(14, 140f, 210f, 0.6f, 20f),
            rings = PremiumRings(2, 200f, 0.8f),
            emoji = "🚢",
            title = "SUBMARINO"
        )
        "rocket" -> PremiumEffectSpec(
            kind = PremiumKind.GIFT_FULLSCREEN,
            primary = Color(0xFFFF5A5F),
            secondary = Color(0xFFFFB300),
            accent = Color(0xFFFF7043),
            burst = PremiumBurst(30, 50f, 260f, 8f, 26f, 90f),
            rays = PremiumRays(18, 150f, 260f, 0.7f, 45f),
            rings = PremiumRings(2, 230f, 0.9f),
            emoji = "🚀",
            title = "COHETE"
        )
        else -> PremiumEffectSpec(
            kind = PremiumKind.GIFT_BANNER,
            primary = Color(0xFFFFD54F),
            secondary = Color(0xFFFF6B9D),
            accent = Color(0xFFA78BFA),
            burst = PremiumBurst(20, 30f, 160f, 5f, 16f, 50f),
            rays = PremiumRays(10, 90f, 150f, 0.5f, 20f),
            rings = PremiumRings(1, 140f, 0.7f),
            emoji = "🎁",
            title = null
        )
    }

    fun giftEmojiFallback(code: String?): String = when (code) {
        "rose" -> "🌹"
        "heart" -> "❤️"
        "applause" -> "👏"
        "star" -> "⭐"
        "crown" -> "👑"
        "diamond" -> "💎"
        "rocket" -> "🚀"
        "galaxy" -> "🌌"
        "lion" -> "🦁"
        "tiger" -> "🐯"
        "airplane" -> "✈️"
        "submarine" -> "🚢"
        else -> "🎁"
    }

    /** Pendientes de sala de voz (voice_room_decor.pendant_code). */
    fun pendantSpec(code: String): PremiumEffectSpec = when (code) {
        "gold" -> pendant(PremiumKind.PENDANT, Color(0xFFD4AF37), Color(0xFFFFE082), "⭕", 10)
            .copy(lottieAsset = "pendant_gold_ring")
        "crown" -> pendant(PremiumKind.PENDANT, Color(0xFFD4AF37), Color(0xFFFFF3B0), "👑", 12)
        "halo" -> pendant(PremiumKind.PENDANT, Color(0xFFFFF176), Color(0xFFFFF9C4), "😇", 8)
        "hearts" -> pendant(PremiumKind.PENDANT, Color(0xFFFF80AB), Color(0xFFF48FB1), "💖", 14)
        "music" -> pendant(PremiumKind.PENDANT, Color(0xFF4DD0E1), Color(0xFF81D4FA), "🎵", 12)
        "fire" -> pendant(PremiumKind.PENDANT, Color(0xFFFF7043), Color(0xFFFFB300), "🔥", 14)
        "diamond" -> pendant(PremiumKind.PENDANT, Color(0xFF4FC3F7), Color(0xFFB3E5FC), "💎", 14)
        "bolt" -> pendant(PremiumKind.PENDANT, Color(0xFFFFEB3B), Color(0xFFFFF9C4), "⚡", 16)
        else -> pendant(PremiumKind.PENDANT, Color(0xFFD4AF37), Color(0xFFFFE082), "◉", 8)
    }

    private fun pendant(
        kind: PremiumKind,
        primary: Color,
        secondary: Color,
        emoji: String,
        burstCount: Int
    ) = PremiumEffectSpec(
        kind = kind,
        primary = primary,
        secondary = secondary,
        accent = Color.White,
        burst = PremiumBurst(burstCount, 10f, 48f, 2f, 6f, 25f),
        rays = PremiumRays(8 + burstCount / 3, 38f, 62f, 0.5f, 12f),
        rings = PremiumRings(1, 30f, 0.4f),
        emoji = emoji,
        durationMs = 2000L
    )

    /** Entradas de sala de voz (voice_room_decor.entrance_code). */
    fun entranceSpec(code: String): PremiumEffectSpec = when (code) {
        "sparkle" -> entrance(PremiumKind.ENTRANCE, Color(0xFFFFD54F), Color(0xFFFF6B9D), "✨", 60, 2800L)
        "fireworks" -> entrance(PremiumKind.ENTRANCE, Color(0xFFFFB300), Color(0xFFFF5A5F), "🎆", 80, 3200L)
        "rose" -> entrance(PremiumKind.ENTRANCE, Color(0xFFD81B60), Color(0xFFFF80AB), "🌹", 52, 2800L)
        "king" -> entrance(PremiumKind.ENTRANCE, Color(0xFFE6B800), Color(0xFFFFD700), "👑", 70, 3000L)
        "party" -> entrance(PremiumKind.ENTRANCE, Color(0xFF7B5CFF), Color(0xFFFF6EC7), "🎉", 70, 3000L)
        "rocket" -> entrance(PremiumKind.ENTRANCE, Color(0xFF4FC3F7), Color(0xFFFF7043), "🚀", 60, 2900L)
        "music" -> entrance(PremiumKind.ENTRANCE, Color(0xFF26C6DA), Color(0xFF4DD0E1), "🎵", 50, 2800L)
        "music2" -> entrance(PremiumKind.ENTRANCE, Color(0xFFFFB300), Color(0xFFFFCA28), "🎶", 50, 2800L)
        "heart" -> entrance(PremiumKind.ENTRANCE, Color(0xFFFF5C8A), Color(0xFFF06292), "💖", 64, 2900L)
        "angel" -> entrance(PremiumKind.ENTRANCE, Color(0xFFFFFDE7), Color(0xFFE0F7FA), "😇", 56, 2800L)
        else -> entrance(PremiumKind.ENTRANCE, Color(0xFFFFD54F), Color(0xFFFF6B9D), "✨", 40, 2600L)
    }

    private fun entrance(
        kind: PremiumKind,
        primary: Color,
        secondary: Color,
        emoji: String,
        burstCount: Int,
        durationMs: Long
    ) = PremiumEffectSpec(
        kind = kind,
        primary = primary,
        secondary = secondary,
        accent = Color.White,
        burst = PremiumBurst(burstCount, 40f, 420f, 6f, 28f, 120f),
        rays = PremiumRays(burstCount / 2, 200f, 440f, 0.7f, 60f),
        rings = PremiumRings(3, 340f, 0.9f),
        emoji = emoji,
        durationMs = durationMs
    )
}