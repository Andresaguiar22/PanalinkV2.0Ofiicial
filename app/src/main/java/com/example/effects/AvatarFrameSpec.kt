package com.example.effects

import androidx.compose.ui.graphics.Color
import com.example.R

/**
 * Marcos (colgantes) de avatar para salas de voz, estilo StarMaker/BIGO.
 *
 * El marco es 100% vectorial y procedural: se dibuja con [androidx.compose.foundation.Canvas]
 * derivando toda la geometria del lado del lienzo (cuadrado), por lo que escala a
 * cualquier tamano sin deformarse ni pixelarse.
 *
 * Anatomia del marco (radios relativos a `rFrame = lado/2`):
 *  - `rAvatar`   = rFrame / overflowScale  -> el hueco circular donde vive la foto.
 *  - banda      = [rAvatar, rAvatar + rFrame*bandWidth] -> aro con gradiente.
 *  - petalos    = [banda, rFrame*petalOuterRatio] -> fruncido/flecos ornamentales.
 *  - ornamentos = coronas, alas, llamas, plumas, etc. dibujados por encima.
 *
 * El hueco del avatar se deja transparente: el avatar se dibuja debajo (lo pinta el
 * asiento) y el marco solo anade material alrededor, sin tapar la cara.
 */
enum class FrameRarity(val label: String) {
    COMMON("Comun"),
    RARE("Raro"),
    EPIC("Epico"),
    LEGENDARY("Legendario"),
    MYTHIC("Mitico")
}

/** Familia estetica del marco: define como se combinan los ornamentos. */
enum class FrameStyle {
    ROYAL,
    CELESTIAL,
    INFERNO,
    FROST,
    NEON,
    FLORAL,
    NATURE,
    SAVAGE,
    GALAXY,
    OCEAN,
    // Estilos de los colgantes Panalink (arte raster, [AvatarFrameSpec.bitmapRes]).
    PANAMA,
    CAFE,
    CANAL,
    FIESTA,
    HERENCIA,
    TESORO
}

/** Ornamentos concretos que se dibujan sobre el aro. */
enum class FrameOrnament {
    CROWN,
    WINGS,
    FLAMES,
    HALO,
    BOLTS,
    LEAVES,
    FEATHERS,
    STARS,
    SPIKES,
    BUBBLES,
    HEARTS
}

/**
 * Especificacion completa de un marco de avatar.
 *
 * @param overflowScale diametro del marco / diametro del avatar. 1.6 => 60% mas grande.
 * @param bandWidth ancho del aro principal, en fraccion de rFrame.
 * @param petalOuterRatio hasta donde llega el fruncido de petalos (fraccion de rFrame).
 * @param animated si rota/pulsa; los estaticos no consumen animacion.
 */
data class AvatarFrameSpec(
    val code: String,
    val label: String,
    val symbol: String,
    val rarity: FrameRarity,
    val style: FrameStyle,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val glow: Color,
    val ornaments: Set<FrameOrnament> = emptySet(),
    val petalCount: Int = 18,
    val gemCount: Int = 12,
    val animated: Boolean = true,
    // Intensidad del fruncido de petalos (0 = sin fruncido). Los marcos cuyo
    // ornamento principal YA es un anillo completo (llamas, picos, plumas) bajan
    // este valor para no competir con el: dos anillos superpuestos se ven sucios.
    val frill: Float = 1f,
    // Ojo: todo lo que se dibuja debe caber en rFrame (el lienzo es cuadrado y el
    // excedente se recorta). Con overflowScale 1.72 el aro cierra en ~0.70*rFrame y
    // quedan ~0.30*rFrame de radio libre para coronas, alas, llamas y plumas.
    val overflowScale: Float = 1.72f,
    val bandWidth: Float = 0.115f,
    val petalOuterRatio: Float = 0.96f,
    // Marco raster: PNG en drawable-nodpi con el arte del diseno y el hueco del
    // avatar ya recortado. Si es != 0 se dibuja el bitmap en vez de las primitivas.
    val bitmapRes: Int = 0
)

object AvatarFrameCatalog {

    val frames: List<AvatarFrameSpec> = listOf(
        AvatarFrameSpec(
            code = "gold",
            label = "Aro Real",
            symbol = "👑",
            rarity = FrameRarity.RARE,
            style = FrameStyle.ROYAL,
            primary = Color(0xFFB8860B),
            secondary = Color(0xFFFFD700),
            accent = Color(0xFFFFF3B0),
            glow = Color(0xFFFFC107),
            ornaments = setOf(FrameOrnament.STARS),
            petalCount = 20,
            gemCount = 12
        ),
        AvatarFrameSpec(
            code = "crown",
            label = "Corona Imperial",
            symbol = "👑",
            rarity = FrameRarity.LEGENDARY,
            style = FrameStyle.ROYAL,
            primary = Color(0xFF8B6914),
            secondary = Color(0xFFFFCC33),
            accent = Color(0xFFFFF8DC),
            glow = Color(0xFFFFB300),
            ornaments = setOf(FrameOrnament.CROWN, FrameOrnament.STARS),
            petalCount = 18,
            gemCount = 14,
            bandWidth = 0.135f
        ),
        AvatarFrameSpec(
            code = "halo",
            label = "Halo Celestial",
            symbol = "😇",
            rarity = FrameRarity.EPIC,
            style = FrameStyle.CELESTIAL,
            primary = Color(0xFFFFE082),
            secondary = Color(0xFFFFFDE7),
            accent = Color(0xFFFFFFFF),
            glow = Color(0xFFFFF59D),
            ornaments = setOf(FrameOrnament.HALO, FrameOrnament.WINGS, FrameOrnament.STARS),
            petalCount = 22,
            gemCount = 10
        ),
        AvatarFrameSpec(
            code = "hearts",
            label = "Corazones",
            symbol = "💖",
            rarity = FrameRarity.RARE,
            style = FrameStyle.FLORAL,
            primary = Color(0xFFE91E63),
            secondary = Color(0xFFFF80AB),
            accent = Color(0xFFFFE4EC),
            glow = Color(0xFFFF4081),
            ornaments = setOf(FrameOrnament.HEARTS),
            petalCount = 16,
            gemCount = 10
        ),
        AvatarFrameSpec(
            code = "music",
            label = "Melodia",
            symbol = "🎵",
            rarity = FrameRarity.RARE,
            style = FrameStyle.NEON,
            primary = Color(0xFF00BCD4),
            secondary = Color(0xFF4DD0E1),
            accent = Color(0xFFE0F7FA),
            glow = Color(0xFF26C6DA),
            ornaments = setOf(FrameOrnament.STARS, FrameOrnament.BOLTS),
            petalCount = 20,
            gemCount = 10
        ),
        AvatarFrameSpec(
            code = "fire",
            label = "Infierno",
            symbol = "🔥",
            rarity = FrameRarity.EPIC,
            style = FrameStyle.INFERNO,
            primary = Color(0xFFFF5722),
            secondary = Color(0xFFFFB300),
            accent = Color(0xFFFFF3E0),
            glow = Color(0xFFFF7043),
            ornaments = setOf(FrameOrnament.FLAMES),
            petalCount = 14,
            gemCount = 8,
            frill = 0f,
            bandWidth = 0.125f
        ),
        AvatarFrameSpec(
            code = "diamond",
            label = "Diamante",
            symbol = "💎",
            rarity = FrameRarity.EPIC,
            style = FrameStyle.FROST,
            primary = Color(0xFF2196F3),
            secondary = Color(0xFF81D4FA),
            accent = Color(0xFFFFFFFF),
            glow = Color(0xFF4FC3F7),
            ornaments = setOf(FrameOrnament.SPIKES, FrameOrnament.STARS),
            petalCount = 16,
            gemCount = 16,
            frill = 0f
        ),
        AvatarFrameSpec(
            code = "bolt",
            label = "Rayo",
            symbol = "⚡",
            rarity = FrameRarity.RARE,
            style = FrameStyle.NEON,
            primary = Color(0xFFF9A825),
            secondary = Color(0xFFFFF59D),
            accent = Color(0xFFFFFDE7),
            glow = Color(0xFFFDD835),
            ornaments = setOf(FrameOrnament.BOLTS, FrameOrnament.SPIKES),
            petalCount = 14,
            gemCount = 8
        ),
        AvatarFrameSpec(
            code = "flor",
            label = "Flor de Jade",
            symbol = "🌸",
            rarity = FrameRarity.EPIC,
            style = FrameStyle.NATURE,
            primary = Color(0xFF2E7D32),
            secondary = Color(0xFF9CCC65),
            accent = Color(0xFFF1F8E9),
            glow = Color(0xFF66BB6A),
            ornaments = setOf(FrameOrnament.LEAVES, FrameOrnament.HEARTS),
            petalCount = 16,
            gemCount = 10,
            frill = 0.5f
        ),
        AvatarFrameSpec(
            code = "wings",
            label = "Alas de Angel",
            symbol = "🕊️",
            rarity = FrameRarity.LEGENDARY,
            style = FrameStyle.CELESTIAL,
            primary = Color(0xFF7E57C2),
            secondary = Color(0xFFB39DDB),
            accent = Color(0xFFF3E5F5),
            glow = Color(0xFF9575CD),
            ornaments = setOf(FrameOrnament.WINGS, FrameOrnament.STARS),
            petalCount = 20,
            gemCount = 10,
            bandWidth = 0.105f
        ),
        AvatarFrameSpec(
            code = "jaguar",
            label = "Guerrero Azteca",
            symbol = "🐆",
            rarity = FrameRarity.LEGENDARY,
            style = FrameStyle.SAVAGE,
            primary = Color(0xFF8D6E63),
            secondary = Color(0xFFD7A86E),
            accent = Color(0xFFFFF8E1),
            glow = Color(0xFFBCAAA4),
            ornaments = setOf(FrameOrnament.FEATHERS, FrameOrnament.STARS),
            petalCount = 12,
            gemCount = 14,
            frill = 0.35f,
            bandWidth = 0.135f
        ),
        AvatarFrameSpec(
            code = "galaxy",
            label = "Galaxia",
            symbol = "🌌",
            rarity = FrameRarity.MYTHIC,
            style = FrameStyle.GALAXY,
            primary = Color(0xFF3F51B5),
            secondary = Color(0xFF9C27B0),
            accent = Color(0xFFE1BEE7),
            glow = Color(0xFF673AB7),
            ornaments = setOf(FrameOrnament.STARS, FrameOrnament.BUBBLES),
            petalCount = 24,
            gemCount = 14
        ),
        AvatarFrameSpec(
            code = "ice",
            label = "Escarcha",
            symbol = "❄️",
            rarity = FrameRarity.EPIC,
            style = FrameStyle.FROST,
            primary = Color(0xFF00ACC1),
            secondary = Color(0xFFB2EBF2),
            accent = Color(0xFFFFFFFF),
            glow = Color(0xFF26C6DA),
            ornaments = setOf(FrameOrnament.SPIKES, FrameOrnament.STARS),
            petalCount = 18,
            gemCount = 12,
            frill = 0f
        ),
        AvatarFrameSpec(
            code = "dragon",
            label = "Dragon",
            symbol = "🐉",
            rarity = FrameRarity.MYTHIC,
            style = FrameStyle.SAVAGE,
            primary = Color(0xFFD84315),
            secondary = Color(0xFFFFB74D),
            accent = Color(0xFFFFF3E0),
            glow = Color(0xFFE64A19),
            ornaments = setOf(FrameOrnament.SPIKES, FrameOrnament.FLAMES),
            petalCount = 14,
            gemCount = 10,
            frill = 0f,
            bandWidth = 0.125f
        ),
        AvatarFrameSpec(
            code = "ocean",
            label = "Mar Profundo",
            symbol = "🐚",
            rarity = FrameRarity.RARE,
            style = FrameStyle.OCEAN,
            primary = Color(0xFF00695C),
            secondary = Color(0xFF4DB6AC),
            accent = Color(0xFFE0F2F1),
            glow = Color(0xFF26A69A),
            ornaments = setOf(FrameOrnament.BUBBLES, FrameOrnament.STARS),
            petalCount = 18,
            gemCount = 10
        ),
        // === Colgantes Panalink (arte raster con el hueco del avatar ya recortado) ===
        AvatarFrameSpec(
            code = "panama",
            label = "Panamá",
            symbol = "🇵🇦",
            rarity = FrameRarity.MYTHIC,
            style = FrameStyle.PANAMA,
            primary = Color(0xFF0B3D91),
            secondary = Color(0xFFD21034),
            accent = Color(0xFFFFFFFF),
            glow = Color(0xFF2196F3),
            animated = false,
            overflowScale = 2.26f,
            bitmapRes = R.drawable.frame_panama
        ),
        AvatarFrameSpec(
            code = "cafe",
            label = "Panalink Café",
            symbol = "☕",
            rarity = FrameRarity.LEGENDARY,
            style = FrameStyle.CAFE,
            primary = Color(0xFF8B5A2B),
            secondary = Color(0xFFD4AF37),
            accent = Color(0xFFF5E6C8),
            glow = Color(0xFFB8860B),
            animated = false,
            overflowScale = 2.20f,
            bitmapRes = R.drawable.frame_cafe
        ),
        AvatarFrameSpec(
            code = "canal",
            label = "Panalink Canal",
            symbol = "🚢",
            rarity = FrameRarity.LEGENDARY,
            style = FrameStyle.CANAL,
            primary = Color(0xFF0D3B66),
            secondary = Color(0xFF4FC3F7),
            accent = Color(0xFFE1F5FE),
            glow = Color(0xFF29B6F6),
            animated = false,
            overflowScale = 2.45f,
            bitmapRes = R.drawable.frame_canal
        ),
        AvatarFrameSpec(
            code = "fiesta",
            label = "Panalink Fiesta",
            symbol = "🎉",
            rarity = FrameRarity.EPIC,
            style = FrameStyle.FIESTA,
            primary = Color(0xFFB8860B),
            secondary = Color(0xFFFFD700),
            accent = Color(0xFF2E7D32),
            glow = Color(0xFFFFC107),
            animated = false,
            overflowScale = 2.63f,
            bitmapRes = R.drawable.frame_fiesta
        ),
        AvatarFrameSpec(
            code = "herencia",
            label = "Panalink Herencia",
            symbol = "⛪",
            rarity = FrameRarity.LEGENDARY,
            style = FrameStyle.HERENCIA,
            primary = Color(0xFF6A1B9A),
            secondary = Color(0xFFE040FB),
            accent = Color(0xFFFFD9EC),
            glow = Color(0xFFAB47BC),
            animated = false,
            overflowScale = 2.01f,
            bitmapRes = R.drawable.frame_herencia
        ),
        AvatarFrameSpec(
            code = "tesoro",
            label = "Panalink Tesoro",
            symbol = "🧭",
            rarity = FrameRarity.EPIC,
            style = FrameStyle.TESORO,
            primary = Color(0xFF8D6E63),
            secondary = Color(0xFFD4AF37),
            accent = Color(0xFFF5E6C8),
            glow = Color(0xFFC9A227),
            animated = false,
            overflowScale = 2.08f,
            bitmapRes = R.drawable.frame_tesoro
        )
    )

    /** Codigos que la app entiende. `none` significa sin marco. */
    const val NONE = "none"

    fun byCode(code: String?): AvatarFrameSpec? {
        if (code.isNullOrBlank() || code == NONE) return null
        return frames.firstOrNull { it.code == code }
    }

    /** Etiqueta legible para UI; tolera codigos desconocidos del backend. */
    fun labelOf(code: String?): String = byCode(code)?.label ?: if (code == NONE) "Sin colgante" else "Colgante"
}
