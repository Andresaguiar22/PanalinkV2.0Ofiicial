package com.example.feature.chat.ui.background

import androidx.compose.ui.graphics.Color

/**
 * Paletas de colores para la burbuja de mensajes enviados (salientes).
 * Cada paleta expone los colores extremos del gradiente diagonal.
 */
enum class ChatBubblePalette(
    val id: String,
    val displayName: String,
    val startColor: Long,
    val endColor: Long
) {
    PANALINK_BLUE("panalink_blue", "Panalink Blue", 0xFF53C8DD, 0xFF27548F),
    NEON_PURPLE("neon_purple", "Neon Purple", 0xFF8B5CF6, 0xFF4C1D95),
    EMERALD_GREEN("emerald_green", "Emerald Green", 0xFF34D399, 0xFF065F46),
    SUNSET_ORANGE("sunset_orange", "Sunset Orange", 0xFFFB923C, 0xFF9A3412),
    DARK_MONOCHROME("dark_monochrome", "Dark Monochrome", 0xFF475569, 0xFF0F172A);

    val colors: List<Color> get() = listOf(Color(startColor), Color(endColor))

    companion object {
        const val DEFAULT_ID = "panalink_blue"
        private val BY_ID = entries.associateBy { it.id }
        fun fromId(id: String?): ChatBubblePalette {
            return BY_ID[id] ?: PANALINK_BLUE
        }
    }
}