package com.example.rooms.ui

/**
 * Catálogos del Toolbox de la sala de voz (estilo StarMaker).
 *
 * - ENTRANCES: animaciones a pantalla completa al entrar un usuario a la sala.
 * - PENDANTS:  adornos que cuelgan/rodean el avatar en el sillón.
 *
 * Los catálogos son locales (emoji nativo + colores), sin assets externos.
 * La persistencia de la selección por sala vive en Supabase
 * (voice_room_decor + RPCs set_voice_room_entrance / set_voice_room_pendant).
 */

data class VoiceRoomEntranceSpec(
    val code: String,
    val label: String,
    val emoji: String,
    val gradient: Pair<Long, Long>,
    val overlayColor: Long = 0x66000000,
    val particleCount: Int = 30
)

data class VoiceRoomPendantSpec(
    val code: String,
    val label: String,
    val symbol: String,
    val ringColor: Long = 0xFFD4AF37,
    val showCrown: Boolean = false,
    val showHalo: Boolean = false,
    val showHearts: Boolean = false,
    val showMusic: Boolean = false,
    val showLightning: Boolean = false
)

object VoiceRoomToolboxCatalog {

    val entrances: List<VoiceRoomEntranceSpec> = listOf(
        VoiceRoomEntranceSpec("sparkle", "Destello", "✨", 0xFFFFD54F to 0xFFFF6B9D),
        VoiceRoomEntranceSpec("fireworks", "Fuegos", "🎆", 0xFFFFB300 to 0xFFFF5A5F),
        VoiceRoomEntranceSpec("rose", "Paso de rosa", "🌹", 0xFFD81B60 to 0xFFFF80AB),
        VoiceRoomEntranceSpec("king", "Entrada real", "👑", 0xFFE6B800 to 0xFFFFD700),
        VoiceRoomEntranceSpec("party", "Fiesta", "🎉", 0xFF7B5CFF to 0xFFFF6EC7),
        VoiceRoomEntranceSpec("rocket", "Cohete", "🚀", 0xFF4FC3F7 to 0xFFFF7043),
        VoiceRoomEntranceSpec("music", "Nota musical", "🎵", 0xFF26C6DA to 0xFF4DD0E1),
        VoiceRoomEntranceSpec("music2", "Nota doble", "🎶", 0xFFFFB300 to 0xFFFFCA28),
        VoiceRoomEntranceSpec("heart", "Lluvia de corazones", "💖", 0xFFFF5C8A to 0xFFF06292),
        VoiceRoomEntranceSpec("angel", "Angelical", "😇", 0xFFFFFDE7 to 0xFFE0F7FA)
    )

    val pendants: List<VoiceRoomPendantSpec> = listOf(
        VoiceRoomPendantSpec("none", "Sin colgante", ""),
        VoiceRoomPendantSpec("gold", "Aro dorado", "⭕", ringColor = 0xFFD4AF37),
        VoiceRoomPendantSpec("crown", "Corona", "👑", ringColor = 0xFFD4AF37, showCrown = true),
        VoiceRoomPendantSpec("halo", "Halo", "😇", ringColor = 0xFFFFF176, showHalo = true),
        VoiceRoomPendantSpec("hearts", "Corazoncitos", "💖", ringColor = 0xFFFF80AB, showHearts = true),
        VoiceRoomPendantSpec("music", "Notas", "🎵", ringColor = 0xFF4DD0E1, showMusic = true),
        VoiceRoomPendantSpec("fire", "Llama", "🔥", ringColor = 0xFFFF7043),
        VoiceRoomPendantSpec("diamond", "Diamante", "💎", ringColor = 0xFF4FC3F7),
        VoiceRoomPendantSpec("bolt", "Rayo", "⚡", ringColor = 0xFFFFEB3B, showLightning = true)
    )

    fun entranceByCode(code: String?): VoiceRoomEntranceSpec? = entrances.firstOrNull { it.code == code }
    fun pendantByCode(code: String?): VoiceRoomPendantSpec? = pendants.firstOrNull { it.code == code }
}