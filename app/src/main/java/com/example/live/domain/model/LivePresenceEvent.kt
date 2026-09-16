package com.example.live.domain.model

/** Eventos de presencia reales del directo (entradas y salidas de espectadores). */
sealed interface LivePresenceEvent {
    data class Joined(val userId: String) : LivePresenceEvent
    data class Left(val userId: String) : LivePresenceEvent
}
