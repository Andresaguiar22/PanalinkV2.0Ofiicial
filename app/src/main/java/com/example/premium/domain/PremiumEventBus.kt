package com.example.premium.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Tipos de eventos Premium 2.0.
 *
 * Un único action de negocio (p. ej. comprar premium) puede producir varios
 * eventos y el [PremiumEventBus] los distribuye a los consumidores reactivos:
 * actualizar wallet, actualizar entitlement, disparar animación, crear
 * notificación, actualizar badge, estadísticas/analytics.
 */
sealed class PremiumEvent {
    /** Compra completada de un producto (puede activar un entitlement). */
    data class PurchaseCompleted(
        val productCode: String,
        val featureKey: String,
        val productName: String,
        val emoji: String,
        val balance: Int?
    ) : PremiumEvent()

    /** Entitlement activado (primera compra o renovación). */
    data class EntitlementActivated(
        val featureKey: String,
        val productCode: String,
        val days: Int
    ) : PremiumEvent()

    /** Entitlement vencido detectado. */
    data class EntitlementExpired(
        val featureKey: String,
        val productCode: String
    ) : PremiumEvent()

    /** Monedas recibidas (recompensa, misión, evento, regalo...). */
    data class CoinsEarned(
        val amount: Int,
        val source: String,          // "mission" | "daily_reward" | "event" | "exchange" | "admin"
        val note: String?
    ) : PremiumEvent()

    /** Monedas gastadas (compra de feature...). */
    data class CoinsSpent(
        val amount: Int,
        val source: String,
        val note: String?
    ) : PremiumEvent()

    /** Recompensa diaria reclamada. */
    data class RewardClaimed(
        val amount: Int,
        val day: Int,
        val streak: Int,
        val currency: String
    ) : PremiumEvent()

    /** Misión completada (progreso llegó al target). */
    data class MissionCompleted(
        val missionCode: String,
        val title: String,
        val reward: Int
    ) : PremiumEvent()

    /** Subida de nivel. */
    data class LevelUp(
        val newLevel: Int,
        val xp: Int
    ) : PremiumEvent()

    /** Promoción abierta (desde banner). */
    data class PromotionOpened(
        val promotionId: String,
        val title: String
    ) : PremiumEvent()

    /** Actividad del usuario relevante para misiones/recompensas. */
    data class Activity(
        val activity: String  // message_sent | story_created | story_viewed | live_joined | live_started | voice_room_joined | post_created | reaction_sent
    ) : PremiumEvent()
}

/**
 * Bus de eventos Premium 2.0.
 *
 * Características:
 *  - [events] SharedFlow replay 0: evita recomponer toda la app por eventos viejos;
 *    los consumidores efímeros (animaciones) se suscriben temporalmente.
 *  - [lastEvent] StateFlow: guarda el último evento para que la UI reaccione una
 *    sola vez (registro de analytics, badge, etc.).
 *  - El bus es el ÚNICO punto por el que las acciones de negocio disparan efectos
 *    transversales. Evita que cada función llame a 10 componentes.
 */
object PremiumEventBus {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _events = MutableSharedFlow<PremiumEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<PremiumEvent> = _events.asSharedFlow()

    private val _lastEvent = MutableStateFlow<PremiumEvent?>(null)
    val lastEvent: StateFlow<PremiumEvent?> = _lastEvent.asStateFlow()

    /** Publica un evento. Los consumidores reaccionan sin bloquear. */
    fun publish(event: PremiumEvent) {
        scope.launch {
            _events.emit(event)
            _lastEvent.value = event
        }
    }

    /** Publica un evento de actividad del usuario (traducción a misiones). */
    fun publishActivity(activity: String) = publish(PremiumEvent.Activity(activity))
}