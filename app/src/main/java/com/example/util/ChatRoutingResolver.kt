package com.example.util

import com.example.data.database.ChatEntity

/**
 * Utilidad centralizada para resolución y normalización de la identidad canónica
 * de conversaciones (DMs vs Hilos vs Chats físicos de Supabase) y enrutamiento de notificaciones.
 */
object ChatRoutingResolver {

    /**
     * Extrae el identificador de chat de un mapa de datos según la prioridad estricta:
     * 1. thread_id / threadId / p_thread_id
     * 2. chat_id / chatId / p_chat_id
     */
    fun extractChatIdFromPayload(data: Map<String, String>): String {
        return data["thread_id"]
            ?: data["threadId"]
            ?: data["p_thread_id"]
            ?: data["chat_id"]
            ?: data["chatId"]
            ?: data["p_chat_id"]
            ?: ""
    }

    /**
     * Extrae el sender_id del payload con todos sus alias soportados.
     */
    fun extractSenderIdFromPayload(data: Map<String, String>): String {
        return data["sender_id"]
            ?: data["senderId"]
            ?: data["p_sender_id"]
            ?: data["otherUserId"]
            ?: ""
    }

    /**
     * Normaliza la identidad canónica del chat combinando el payload con la entidad local de Room si existe.
     * Si el payload trajo un chat_id físico pero Room conoce el threadId canónico, resuelve al threadId.
     */
    fun resolveCanonicalChatId(
        rawThreadId: String?,
        rawChatId: String?,
        localChatEntity: ChatEntity?
    ): String {
        val payloadThread = rawThreadId?.takeIf { it.isNotBlank() }
        if (payloadThread != null) {
            return payloadThread
        }
        if (localChatEntity?.threadId?.isNotBlank() == true) {
            return localChatEntity.threadId!!
        }
        val payloadChat = rawChatId?.takeIf { it.isNotBlank() }
        if (payloadChat != null) {
            return localChatEntity?.threadId ?: payloadChat
        }
        return localChatEntity?.threadId ?: localChatEntity?.id ?: ""
    }

    /**
     * Valida la consistencia entre el sender_id recibido y el otherUserId de la entidad Room/Thread.
     * Retorna true si son consistentes (o si uno de ellos no está definido), o false si hay contradicción directa.
     */
    fun isSenderConsistent(payloadSenderId: String?, entityOtherUserId: String?): Boolean {
        if (payloadSenderId.isNullOrBlank() || payloadSenderId == "unknown") return true
        if (entityOtherUserId.isNullOrBlank() || entityOtherUserId == "unknown") return true
        return payloadSenderId == entityOtherUserId
    }

    /**
     * Determina si una notificación debe suprimirse porque el chat correspondiente ya está abierto y activo.
     */
    fun isChatActiveAndMatching(
        isChatScreenActive: Boolean,
        activeChatId: String?,
        canonicalChatId: String,
        physicalChatId: String? = null
    ): Boolean {
        if (!isChatScreenActive || activeChatId.isNullOrBlank()) return false
        return activeChatId == canonicalChatId || (physicalChatId != null && activeChatId == physicalChatId)
    }
}
