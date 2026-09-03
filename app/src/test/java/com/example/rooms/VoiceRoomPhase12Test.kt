package com.example.rooms

import com.example.rooms.model.VoiceRoom
import com.example.rooms.model.VoiceRoomMessage
import com.example.rooms.model.VoiceRoomMessagesReducer
import com.example.rooms.model.VoiceRoomSeat
import com.example.rooms.model.VoiceRoomSeatReducer
import com.example.rooms.model.VoiceRoomUiState
import com.example.rooms.signaling.SupabaseVoiceRoomSignaling
import com.example.rooms.signaling.VoiceRoomReconnectPolicy
import com.example.rooms.webrtc.VoiceRoomPeerRegistry
import org.junit.Assert.*
import org.junit.Test

/**
 * Fase 1.2 — casos de aceptacion del modulo de salas de voz.
 * Todo es JVM puro: reducer de sillones, reducer de mensajes, politica de
 * reconexion y registro de peers (la parte nativa de WebRTC queda fuera).
 */
class VoiceRoomPhase12Test {

    private fun empty() = VoiceRoomUiState.emptySeats()

    // 1) Usuario ocupa sillon --------------------------------------------

    @Test
    fun `1 - usuario ocupa sillon libre`() {
        val seats = VoiceRoomSeatReducer.occupy(empty(), 4, "user_a", "Ana", null)
        assertTrue(seats[4].isOccupied)
        assertEquals("user_a", seats[4].userId)
        assertEquals(1, seats.count { it.isOccupied })
    }

    // 2) Usuario abandona sillon -----------------------------------------

    @Test
    fun `2 - usuario abandona su sillon y queda libre`() {
        var seats = VoiceRoomSeatReducer.occupy(empty(), 2, "user_a", null, null)
        seats = VoiceRoomSeatReducer.release(seats, "user_a")
        assertFalse(seats[2].isOccupied)
        assertNull(seats[2].userId)
        assertFalse(seats[2].isMuted)
        assertFalse(seats[2].isSpeaking)
    }

    @Test
    fun `2b - sillon liberado puede ocuparse inmediatamente por otro usuario`() {
        var seats = VoiceRoomSeatReducer.occupy(empty(), 2, "user_a", null, null)
        seats = VoiceRoomSeatReducer.release(seats, "user_a")
        assertNull(seats[2].userId)
        assertFalse(seats[2].isOccupied)

        seats = VoiceRoomSeatReducer.occupy(seats, 2, "user_b", null, null)
        assertEquals("user_b", seats[2].userId)
        assertTrue(seats[2].isOccupied)
    }

    // 3) DELETE remoto libera sillon --------------------------------------
    // Con REPLICA IDENTITY FULL el old_record trae user_id; el cliente libera
    // el sillon y (via engine.onPeerLeft) destruye la PeerConnection.

    @Test
    fun `3 - DELETE remoto con user_id libera el sillon correcto`() {
        var seats = VoiceRoomSeatReducer.occupy(empty(), 1, "user_a", null, null)
        seats = VoiceRoomSeatReducer.occupy(seats, 5, "user_b", null, null)
        // Simula old_record del DELETE realtime: { "user_id": "user_a", ... }
        val remoteDeleteUserId = "user_a"
        seats = VoiceRoomSeatReducer.release(seats, remoteDeleteUserId)
        assertFalse(seats[1].isOccupied)
        assertTrue("el otro ocupante no se toca", seats[5].isOccupied)
    }

    // 4) Usuario abandona y vuelve ----------------------------------------

    @Test
    fun `4 - usuario abandona y vuelve a ocupar sillon (incluso otro)`() {
        var seats = VoiceRoomSeatReducer.occupy(empty(), 0, "user_a", null, null)
        seats = VoiceRoomSeatReducer.release(seats, "user_a")
        seats = VoiceRoomSeatReducer.occupy(seats, 3, "user_a", null, null)
        assertEquals(1, seats.count { it.userId == "user_a" })
        assertTrue(seats[3].isOccupied)
        assertFalse(seats[0].isOccupied)
    }

    @Test
    fun `4b - registry permite peer NUEVO tras remove (sin zombies)`() {
        val registry = VoiceRoomPeerRegistry<String, String>()
        val first = registry.getOrPut("user_a") { "conn_1" }
        assertSame(first, registry.getOrPut("user_a") { "conn_X" }) // no duplica
        registry.remove("user_a")
        val second = registry.getOrPut("user_a") { "conn_2" }
        assertEquals("conn_2", second) // conexion nueva, no la vieja
        assertEquals(1, registry.size)
    }

    // 5) Dos usuarios, mismo sillon ---------------------------------------

    @Test
    fun `5 - dos usuarios no pueden ocupar el mismo sillon`() {
        var seats = VoiceRoomSeatReducer.occupy(empty(), 0, "user_a", null, null)
        seats = VoiceRoomSeatReducer.occupy(seats, 0, "user_b", null, null)
        assertEquals("user_a", seats[0].userId)
        assertEquals(1, seats.count { it.isOccupied })
    }

    // 6) Maximo 9 participantes (1 anfitrion + 8 invitados) ------------------

    @Test
    fun `6 - la sala tiene exactamente 9 sillones y no admite un decimo`() {
        var seats = empty()
        (0..8).forEach { seats = VoiceRoomSeatReducer.occupy(seats, it, "u$it", null, null) }
        assertEquals(VoiceRoom.MAX_SEATS, seats.count { it.isOccupied })
        assertNull(VoiceRoomSeatReducer.firstFreeSeatIndex(seats))
        // Un decimo intento fuera de rango se rechaza
        val after = VoiceRoomSeatReducer.occupy(seats, 9, "u9", null, null)
        assertEquals(9, after.count { it.isOccupied })
    }

    @Test
    fun `6b - decimo usuario queda como listener y la sala sigue consistente`() {
        var seats: List<VoiceRoomSeat> = empty()
        repeat(9) { i -> seats = VoiceRoomSeatReducer.occupy(seats, i, "user_$i", null, null) }

        // El 10mo no encuentra sillon libre: no se sienta, no rompe nada.
        assertNull(VoiceRoomSeatReducer.firstFreeSeatIndex(seats))
        assertEquals(9, seats.count { it.isOccupied })
        assertEquals(9, seats.distinctBy { it.userId }.size)

        // La sala sigue operativa: mute, speaking y liberacion funcionan.
        seats = VoiceRoomSeatReducer.setMuted(seats, "user_0", true)
        assertTrue(seats[0].isMuted)
        seats = VoiceRoomSeatReducer.release(seats, "user_3")
        assertEquals(3, VoiceRoomSeatReducer.firstFreeSeatIndex(seats))
    }

    // 7) Mensaje enviado/recibido -----------------------------------------

    private fun msg(id: String, content: String, ts: String) =
        VoiceRoomMessage(id, "room", "sender", null, content, ts)

    @Test
    fun `7 - mensaje entrante se agrega con dedupe y orden cronologico`() {
        // Snapshot: la API trae DESC (mas nuevo primero) y el repo invierte.
        val descFromApi = listOf(msg("m3", "c", "2026-08-23T00:00:03Z"),
                                 msg("m2", "b", "2026-08-23T00:00:02Z"),
                                 msg("m1", "a", "2026-08-23T00:00:01Z"))
        var messages = descFromApi.asReversed()
        assertEquals(listOf("m1", "m2", "m3"), messages.map { it.id })

        // INSERT realtime nuevo: se anexa al final
        messages = VoiceRoomMessagesReducer.append(messages, msg("m4", "d", "2026-08-23T00:00:04Z"))
        assertEquals(listOf("m1", "m2", "m3", "m4"), messages.map { it.id })

        // INSERT duplicado (reintento de realtime): no se repite
        messages = VoiceRoomMessagesReducer.append(messages, msg("m4", "d", "2026-08-23T00:00:04Z"))
        assertEquals(4, messages.size)

        // Payload sin id (broadcast parcial): se ignora
        messages = VoiceRoomMessagesReducer.append(messages, msg("", "x", "2026-08-23T00:00:05Z"))
        assertEquals(4, messages.size)
    }

    @Test
    fun `7b - historial acotado al cap`() {
        var messages: List<VoiceRoomMessage> = emptyList()
        repeat(VoiceRoomMessagesReducer.HISTORY_CAP + 10) { i ->
            messages = VoiceRoomMessagesReducer.append(messages, msg("m$i", "x", "t$i"))
        }
        assertEquals(VoiceRoomMessagesReducer.HISTORY_CAP, messages.size)
        assertEquals("m10", messages.first().id) // se descartaron los mas viejos
    }

    // 8) Mute/unmute --------------------------------------------------------

    @Test
    fun `8 - mute silencia y limpia speaking, unmute restaura`() {
        var seats = VoiceRoomSeatReducer.occupy(empty(), 1, "user_a", null, null)
        seats = VoiceRoomSeatReducer.setSpeaking(seats, "user_a", true)
        assertTrue(seats[1].isSpeaking)

        seats = VoiceRoomSeatReducer.setMuted(seats, "user_a", true)
        assertTrue(seats[1].isMuted)
        assertFalse("mute apaga el indicador de hablando", seats[1].isSpeaking)

        // Mientras esta muteado no puede quedar "hablando"
        seats = VoiceRoomSeatReducer.setSpeaking(seats, "user_a", true)
        assertFalse(seats[1].isSpeaking)

        seats = VoiceRoomSeatReducer.setMuted(seats, "user_a", false)
        assertFalse(seats[1].isMuted)
        seats = VoiceRoomSeatReducer.setSpeaking(seats, "user_a", true)
        assertTrue(seats[1].isSpeaking)
    }

    // 9) Reconexion del signaling ------------------------------------------

    @Test
    fun `9 - reconexion solo si el cierre no fue intencional y hay sala activa`() {
        val policy = VoiceRoomReconnectPolicy()
        assertTrue(policy.shouldReconnect(intentionallyClosed = false, hasActiveRoom = true))
        assertFalse(policy.shouldReconnect(intentionallyClosed = true, hasActiveRoom = true))
        assertFalse(policy.shouldReconnect(intentionallyClosed = false, hasActiveRoom = false))
        assertFalse(policy.shouldReconnect(intentionallyClosed = true, hasActiveRoom = false))
    }

    @Test
    fun `9c - el topic de broadcast lleva el prefijo realtime obligatorio`() {
        // Verificado en vivo (2026-08-23): este proyecto rechaza topics sin el
        // prefijo "realtime:" con phx_reply error "unmatched topic".
        val roomId = "00000000-0000-0000-0000-000000000000"
        assertEquals(
            "realtime:voice_room:$roomId",
            SupabaseVoiceRoomSignaling.broadcastTopic(roomId)
        )
    }

    @Test
    fun `9b - backoff exponencial con tope y reset tras conexion exitosa`() {
        val policy = VoiceRoomReconnectPolicy(baseDelayMs = 1000, maxDelayMs = 5000)
        assertEquals(1000, policy.nextDelayMs())
        assertEquals(2000, policy.nextDelayMs())
        assertEquals(4000, policy.nextDelayMs())
        assertEquals(5000, policy.nextDelayMs()) // tope
        assertEquals(5000, policy.nextDelayMs())
        policy.reset()
        assertEquals(1000, policy.nextDelayMs()) // vuelve al inicio
    }

    // 10) Cleanup completo de PeerConnections -------------------------------

    @Test
    fun `10 - removeAll no deja peers ni ICE pendientes`() {
        val registry = VoiceRoomPeerRegistry<String, String>()
        registry.getOrPut("user_a") { "conn_a" }
        registry.getOrPut("user_b") { "conn_b" }
        registry.bufferIce("user_a", "ice_1")
        registry.bufferIce("user_c", "ice_huerfano") // ICE de un peer que nunca llego

        val disposed = registry.removeAll()
        assertEquals(2, disposed.size)

        assertEquals(0, registry.size)
        assertFalse(registry.contains("user_a"))
        assertNull(registry.get("user_b"))
        // ICE pendientes tambien se purgaron: drenar ahora devuelve vacio
        assertTrue(registry.drainIce("user_a").isEmpty())
        assertTrue(registry.drainIce("user_c").isEmpty())
    }

    @Test
    fun `10b - remove individual descarta ICE pendientes de ese peer solamente`() {
        val registry = VoiceRoomPeerRegistry<String, String>()
        registry.getOrPut("user_a") { "conn_a" }
        registry.getOrPut("user_b") { "conn_b" }
        registry.bufferIce("user_a", "ice_a")
        registry.bufferIce("user_b", "ice_b")

        val removed = registry.remove("user_a")
        assertEquals("conn_a", removed)
        assertTrue(registry.drainIce("user_a").isEmpty())
        assertEquals(listOf("ice_b"), registry.drainIce("user_b"))
        assertEquals(1, registry.size)
    }

    @Test
    fun `10c - peer_reset es idempotente y permite reconstruir el peer`() {
        // Simula el flujo de reconexion: onPeerLeft + onPeerJoined sobre el
        // registry (lo que hace el engine al recibir peer_reset).
        val registry = VoiceRoomPeerRegistry<String, String>()
        registry.getOrPut("user_a") { "conn_vieja" }
        registry.bufferIce("user_a", "ice_viejo")

        // peer_reset #1: descarta la PC vieja (y su ICE) y reconstruye
        assertEquals("conn_vieja", registry.remove("user_a"))
        assertTrue(registry.drainIce("user_a").isEmpty())
        registry.getOrPut("user_a") { "conn_nueva" }
        assertEquals("conn_nueva", registry.get("user_a"))

        // peer_reset #2 (duplicado, ambos extremos se reconectan a la vez):
        // no falla y el estado queda consistente
        assertEquals("conn_nueva", registry.remove("user_a"))
        assertNull(registry.remove("user_a")) // ya no existe: idempotente
        registry.getOrPut("user_a") { "conn_final" }
        assertEquals(1, registry.size)
        assertEquals("conn_final", registry.get("user_a"))
    }
}
