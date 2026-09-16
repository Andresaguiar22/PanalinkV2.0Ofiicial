package com.example.rooms.ui

import androidx.compose.ui.graphics.toArgb
import com.example.effects.AvatarFrameCatalog

/**
 * Catálogos del Toolbox de la sala de voz (estilo StarMaker).
 *
 * - ENTRANCES: animaciones a pantalla completa al entrar un usuario a la sala.
 * - PENDANTS:  marcos vectoriales que rodean el avatar en el sillón
 *              ([AvatarFrameCatalog], sin assets ni bitmaps).
 *
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
    val rarityLabel: String = ""
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

    /**
     * Los colgantes son los marcos vectoriales de [AvatarFrameCatalog]: la lista se
     * deriva de ahi para que el selector muestre exactamente el mismo diseno que se
     * pinta alrededor del avatar.
     */
    val pendants: List<VoiceRoomPendantSpec> = buildList {
        add(VoiceRoomPendantSpec("none", "Sin colgante", ""))
        AvatarFrameCatalog.frames.forEach { frame ->
            add(
                VoiceRoomPendantSpec(
                    code = frame.code,
                    label = frame.label,
                    symbol = frame.symbol,
                    ringColor = frame.secondary.toArgb().toLong() and 0xFFFFFFFFL,
                    rarityLabel = frame.rarity.label
                )
            )
        }
    }

    fun entranceByCode(code: String?): VoiceRoomEntranceSpec? = entrances.firstOrNull { it.code == code }
    fun pendantByCode(code: String?): VoiceRoomPendantSpec? = pendants.firstOrNull { it.code == code }
}