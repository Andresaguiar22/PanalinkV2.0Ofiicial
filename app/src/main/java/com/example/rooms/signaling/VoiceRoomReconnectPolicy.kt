package com.example.rooms.signaling

/**
 * Politica pura de reconexion del signaling de salas de voz.
 * Extraida de [SupabaseVoiceRoomSignaling] para testear la decision y el
 * backoff en JVM sin abrir WebSockets.
 *
 * Reglas:
 *  - NUNCA reconectar si el cierre fue intencional (leaveRoom) o no hay sala.
 *  - Backoff exponencial con tope: base, 2*base, 4*base... hasta maxDelayMs.
 *  - reset() al abrir el socket con exito.
 */
class VoiceRoomReconnectPolicy(
    private val baseDelayMs: Long = 3000,
    private val maxDelayMs: Long = 30000
) {
    private var attempts = 0

    fun shouldReconnect(intentionallyClosed: Boolean, hasActiveRoom: Boolean): Boolean =
        !intentionallyClosed && hasActiveRoom

    /** Delay para el proximo intento (crece exponencialmente hasta el tope). */
    @Synchronized
    fun nextDelayMs(): Long {
        attempts++
        val factor = 1L shl (attempts - 1).coerceAtMost(10)
        return (baseDelayMs * factor).coerceAtMost(maxDelayMs)
    }

    @Synchronized
    fun reset() {
        attempts = 0
    }
}
