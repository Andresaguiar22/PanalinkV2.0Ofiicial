package com.example.feature.chat.ui.background

/**
 * Especificación de fondo de chat. Puede ser un preset (gradiente/sólido) o
 * una imagen elegida desde la galería del dispositivo.
 */
sealed class ChatWallpaperSpec {
    abstract val id: String
    abstract val label: String

    /** Presets premium renderizados con Brush/colores CSS en Compose. */
    data class Gradient(
        override val id: String,
        override val label: String,
        val start: Long,
        val end: Long,
        val depthHue: Long? = null
    ) : ChatWallpaperSpec()

    data class Solid(
        override val id: String,
        override val label: String,
        val color: Long
    ) : ChatWallpaperSpec()

    /** Imagen remota (URL) copiada al preselector. */
    data class Remote(
        override val id: String,
        override val label: String,
        val url: String
    ) : ChatWallpaperSpec()

    /** Imagen local elegida con ActivityResultContracts.GetContent(). */
    data class Custom(
        override val id: String,
        override val label: String,
        val uri: String
    ) : ChatWallpaperSpec()

    companion object {
        /** 14 presets premium: gradientes oscuros estilo mesh y sólidos quirúrgicos. */
        val PRESETS: List<ChatWallpaperSpec> = listOf(
            Gradient("midnight_abyss", "Abismo Medianoche", 0xFF0E1730, 0xFF070B18),
            Gradient("electric_dusk", "Crepúsculo Eléctrico", 0xFF1A1440, 0xFF0A1128),
            Gradient("ocean_void", "Vacío Oceánico", 0xFF06283D, 0xFF0B1B2B),
            Gradient("royal_plum", "Ciruela Imperial", 0xFF2B1B3D, 0xFF0E081C),
            Gradient("deep_jungle", "Selva Profunda", 0xFF0B3D2E, 0xFF04140F),
            Gradient("smokey_steel", "Acero Ahumado", 0xFF1F2733, 0xFF0F1319),
            Gradient("nordic_fog", "Niebla Nórdica", 0xFF263445, 0xFF111A24),
            Gradient("aurora_pulse", "Pulso Aurora", 0xFF16324F, 0xFF0A1826),
            Gradient("amber_ember", "Ámbar Incandescente", 0xFF3D1B0B, 0xFF120600),
            Gradient("lavender_mist", "Niebla Lavanda", 0xFF3D2B5F, 0xFF150D24),
            Solid("graphite", "Grafito", 0xFF14181D),
            Solid("charcoal_matte", "Carbón Mate", 0xFF191E24),
            Solid("deep_teal", "Verde Botella", 0xFF0B2E2E),
            Solid("dark_slate", "Pizarra Oscura", 0xFF1A222B)
        )

        val DEFAULT_ACTIVE = "dark_slate"

        fun fromId(id: String?, customUri: String? = null): ChatWallpaperSpec {
            if (id.isNullOrBlank()) return Solid(DEFAULT_ACTIVE, "Defecto", 0xFF1A222B)
            PRESETS.firstOrNull { it.id == id }?.let { return it }
            if (id.startsWith("http")) return Remote(id, "Imagen remota", id)
            if (id == "custom" && !customUri.isNullOrBlank()) return Custom("custom", "Galería", customUri)
            return Solid(DEFAULT_ACTIVE, "Defecto", 0xFF1A222B)
        }
    }
}