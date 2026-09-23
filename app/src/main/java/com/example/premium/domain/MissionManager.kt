package com.example.premium.domain

import android.util.Log
import com.example.premium.data.repository.PremiumRepositoryImpl
import com.example.premium.domain.repository.PremiumRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Actividades del usuario que alimentan misiones Premium 2.0.
 */
object MissionActivities {
    const val MESSAGE_SENT = "message_sent"
    const val STORY_CREATED = "story_created"
    const val STORY_VIEWED = "story_viewed"
    const val LIVE_JOINED = "live_joined"
    const val LIVE_STARTED = "live_started"
    const val VOICE_ROOM_JOINED = "voice_room_joined"
    const val POST_CREATED = "post_created"
    const val REACTION_SENT = "reaction_sent"
}

/**
 * Traduce eventos de actividad de la app a progreso de misiones en Supabase,
 * sin duplicar llamadas ni saturar el backend.
 *
 * Estrategias:
 *  - Antirrebote por (misión, periodo): una actividad repetida en ráfaga (p.ej.
 *    muchas reacciones seguidas) no dispara N llamadas; se acumula en memoria y
 *    se envían a intervalos mínimos.
 *  - Estado en memoria de progreso (StateFlow) para que la UI reaccione sin esperar
 *    a la red.
 *  - En el arranque sincroniza el progreso real del backend.
 */
object MissionManager {
    private const val TAG = "PanalinkMissions"
    private const val MIN_DEDUP_INTERVAL_MS = 800L
    private const val FLUSH_DELAY_MS = 900L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository: PremiumRepository by lazy { PremiumRepositoryImpl() }

    private val _missions = MutableStateFlow<List<com.example.premium.domain.model.Mission>>(emptyList())
    val missions: StateFlow<List<com.example.premium.domain.model.Mission>> = _missions.asStateFlow()

    /** Progreso en memoria por code de misión (evita re-enviar lo ya acumulado). */
    private val localProgress = ConcurrentHashMap<String, Int>()
    private val lastSentAt = ConcurrentHashMap<String, Long>()
    private val pendingIncrement = ConcurrentHashMap<String, Int>()
    private val mutex = Mutex()
    private var flushJob: Job? = null

    init {
        // Suscríbete al bus de eventos de actividad y propaga a misiones.
        scope.launch {
            PremiumEventBus.events.collect { event ->
                if (event is PremiumEvent.Activity) {
                    recordActivity(event.activity)
                }
            }
        }
        refresh()
    }

    /** Carga las misiones del servidor (progreso real). */
    fun refresh() {
        scope.launch {
            repository.getMissions().onSuccess {
                _missions.value = it
                it.forEach { m -> localProgress[m.code] = m.progress }
            }
        }
    }

    /** Progreso actual de todas las misiones (StateFlow). */
    fun missionFlow(): StateFlow<List<com.example.premium.domain.model.Mission>> = _missions

    /**
     * Registra una actividad de la app y la traduce a las misiones que le
     * corresponden (una actividad puede incrementar varias misiones).
     */
    fun recordActivity(activity: String) {
        val codes = missionCodesFor(activity)
        if (codes.isEmpty()) return
        val now = System.currentTimeMillis()
        codes.forEach { code ->
            val lastSent = lastSentAt[code] ?: 0L
            val pending = pendingIncrement[code] ?: 0
            pendingIncrement[code] = pending + 1
            // Antirrebote: si hace poco se envió esta misión, no spammear.
            if (now - lastSent >= MIN_DEDUP_INTERVAL_MS) {
                scheduleFlush()
            }
        }
    }

    /** Mapa actividad -> códigos de misión. */
    fun missionCodesFor(activity: String): List<String> = when (activity) {
        MissionActivities.MESSAGE_SENT -> listOf("send_5_messages")
        MissionActivities.STORY_CREATED -> listOf("publish_story")
        MissionActivities.STORY_VIEWED -> listOf("view_3_stories")
        MissionActivities.LIVE_JOINED -> listOf("watch_live")
        MissionActivities.LIVE_STARTED -> emptyList()                 // transmitir no equivale a ver
        MissionActivities.VOICE_ROOM_JOINED -> listOf("join_voice_room")
        MissionActivities.POST_CREATED -> emptyList()                  // no existe misión específica de post/reel
        MissionActivities.REACTION_SENT -> emptyList()                // sin misión semilla; reservado
        else -> emptyList()
    }

    private fun scheduleFlush() {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            delay(FLUSH_DELAY_MS)
            flush()
        }
    }

    private fun flush() {
        scope.launch {
            mutex.withLock {
                val batch = pendingIncrement.toMap()
                pendingIncrement.clear()
                batch.forEach { (code, inc) ->
                    // No re-enviar si el progreso local ya está en target (misión cumplida).
                    val progress = localProgress[code] ?: 0
                    val target = _missions.value.firstOrNull { it.code == code }?.target ?: Int.MAX_VALUE
                    if (progress < target) {
                        repository.reportMissionProgress(code, inc).onSuccess { result ->
                            if (result.ok) {
                                lastSentAt[code] = System.currentTimeMillis()
                                localProgress[code] = result.progress ?: 0
                                updateLocalMission(code, result)
                                if (result.completed == true) {
                                    PremiumEventBus.publish(
                                        PremiumEvent.MissionCompleted(
                                            missionCode = code,
                                            title = _missions.value.firstOrNull { it.code == code }?.title ?: code,
                                            reward = _missions.value.firstOrNull { it.code == code }?.rewardAmount ?: 0
                                        )
                                    )
                                }
                            }
                        }.onFailure { e ->
                            Log.e(TAG, "Fallo al reportar misión $code", e)
                        }
                    }
                }
            }
        }
    }

    private fun updateLocalMission(code: String, result: com.example.premium.domain.model.MissionResult) {
        val list = _missions.value.map { m ->
            if (m.code == code) {
                m.copy(progress = result.progress ?: m.progress, completed = result.completed ?: m.completed)
            } else m
        }
        _missions.value = list
    }
}