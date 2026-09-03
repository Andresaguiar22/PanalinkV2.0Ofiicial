package com.example.rooms.webrtc

/**
 * Libro contable PURO del ciclo de vida de los peers de la sala de voz:
 * que PeerConnection existe para cada userId y que candidatos ICE llegaron
 * antes de tener remoteDescription.
 *
 * Extraido de [VoiceRoomWebRtcEngine] para que la logica anti-zombies sea
 * testeable en JVM sin cargar las librerias nativas de WebRTC. Generico sobre
 * el tipo de peer (P) y de candidato (I): el engine lo instancia con
 * PeerConnection/IceCandidate, los tests con String/String.
 *
 * Invariantes:
 *  - un userId tiene a lo sumo un peer (getOrPut nunca duplica);
 *  - remove() devuelve el peer y descarta sus ICE pendientes (anti-zombie);
 *  - tras remove(), el mismo userId puede volver a entrar con un peer NUEVO;
 *  - removeAll() no deja ni peers ni ICE pendientes (cleanup completo).
 */
class VoiceRoomPeerRegistry<P : Any, I : Any> {

    private val peers = LinkedHashMap<String, P>()
    private val pendingIce = LinkedHashMap<String, MutableList<I>>()

    @Synchronized fun get(userId: String): P? = peers[userId]

    @Synchronized fun contains(userId: String): Boolean = peers.containsKey(userId)

    @Synchronized fun userIds(): List<String> = peers.keys.toList()

    @get:Synchronized val size: Int get() = peers.size

    /** Devuelve el peer existente o crea uno nuevo con [factory]. */
    @Synchronized
    fun getOrPut(userId: String, factory: () -> P): P =
        peers[userId] ?: factory().also { peers[userId] = it }

    /**
     * Quita el peer y sus ICE pendientes.
     * @return el peer removido (para que el llamador lo disponga) o null.
     */
    @Synchronized
    fun remove(userId: String): P? {
        pendingIce.remove(userId)
        return peers.remove(userId)
    }

    /** Quita TODO. @return los peers removidos para disponerlos fuera del lock. */
    @Synchronized
    fun removeAll(): List<P> {
        val all = peers.values.toList()
        peers.clear()
        pendingIce.clear()
        return all
    }

    /** Buffer de ICE que llego antes de tener remoteDescription. */
    @Synchronized
    fun bufferIce(userId: String, candidate: I) {
        pendingIce.getOrPut(userId) { mutableListOf() }.add(candidate)
    }

    /** Devuelve y descarta los ICE pendientes de un peer. */
    @Synchronized
    fun drainIce(userId: String): List<I> =
        pendingIce.remove(userId)?.toList() ?: emptyList()
}
