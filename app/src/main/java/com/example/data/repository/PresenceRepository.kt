package com.example.data.repository

import android.util.Log
import com.example.data.supabase.SupabaseClient
import com.example.util.PresenceHistoryTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "PresenceRepository"

enum class UserPresenceStatus(val rawValue: String, val label: String) {
    ONLINE("online", "En línea"),
    AWAY("away", "Ausente"),
    BUSY("busy", "En llamada"),
    OFFLINE("offline", "Desconectado")
}

enum class SecondaryPresenceStatus(val rawValue: String, val label: String) {
    NONE("none", ""),
    TYPING("typing", "Escribiendo..."),
    RECORDING_AUDIO("recording_audio", "Grabando audio..."),
    UPLOADING_FILE("uploading_file", "Subiendo archivo..."),
    VOICE_CALL("voice_call", "En llamada de voz"),
    VIDEO_CALL("video_call", "En videollamada"),
    MESSAGES_ONLY("messages_only", "Solo mensajes"),
    DND("dnd", "No molestar")
}

enum class CallAvailability(val label: String) {
    AVAILABLE("Disponible para llamadas"),
    MESSAGES_ONLY("Prefiere mensajes")
}

data class UserPresenceInfo(
    val userId: String,
    val status: UserPresenceStatus,
    val secondaryStatus: SecondaryPresenceStatus = SecondaryPresenceStatus.NONE,
    val callAvailability: CallAvailability = CallAvailability.AVAILABLE,
    val lastSeen: Long = System.currentTimeMillis()
)

object PresenceRepository {
    private val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    // Deduplication cache to prevent duplicate native Presence frames.
    // Key: userId_status_windowTimestamp
    private val deduplicationCache = ConcurrentHashMap<String, Long>()
    private const val DEDUPLICATION_WINDOW_MS = 5000L

    private val _currentUserStatus = MutableStateFlow(UserPresenceStatus.ONLINE)
    val currentUserStatus: StateFlow<UserPresenceStatus> = _currentUserStatus.asStateFlow()

    private val _currentUserSecondaryStatus = MutableStateFlow(SecondaryPresenceStatus.NONE)
    val currentUserSecondaryStatus: StateFlow<SecondaryPresenceStatus> = _currentUserSecondaryStatus.asStateFlow()

    private val _currentUserCallAvailability = MutableStateFlow(CallAvailability.AVAILABLE)
    val currentUserCallAvailability: StateFlow<CallAvailability> = _currentUserCallAvailability.asStateFlow()

    private val _presenceMap = MutableStateFlow<Map<String, UserPresenceInfo>>(emptyMap())
    val presenceMap: StateFlow<Map<String, UserPresenceInfo>> = _presenceMap.asStateFlow()

    private val gracePeriodJobs = ConcurrentHashMap<String, Job>()
    var gracePeriodDurationMs: Long = 12_000L

    // Online entries older than this are decayed to OFFLINE. Heartbeats every
    // 25s keep lastSeen fresh; 3 missed heartbeats (~75s) => not online anymore.
    private val ONLINE_STALE_TTL_MS = 75_000L

    init {
        // Periodically decay stale ONLINE/AWAY rows so the green dot disappears
        // on its own when the remote user stops heartbeating (left, killed app,
        // lost network) without waiting for a server event.
        scope.launch {
            while (isActive) {
                delay(15_000L)
                decayStalePresence()
            }
        }
    }

    private fun decayStalePresence() {
        try {
            val now = System.currentTimeMillis()
            val stale = _presenceMap.value.filterValues { info ->
                (info.status == UserPresenceStatus.ONLINE || info.status == UserPresenceStatus.AWAY) &&
                    (now - info.lastSeen) > ONLINE_STALE_TTL_MS
            }
            if (stale.isEmpty()) return
            val updated = _presenceMap.value.toMutableMap()
            stale.forEach { (userId, info) ->
                updated[userId] = info.copy(
                    status = UserPresenceStatus.OFFLINE,
                    lastSeen = info.lastSeen
                )
                PresenceHistoryTracker.recordEvent(userId, UserPresenceStatus.OFFLINE, info.lastSeen)
            }
            _presenceMap.value = updated
        } catch (e: Exception) {
            Log.w(TAG, "decayStalePresence failed: ${e.localizedMessage}")
        }
    }

    init {
        // Escuchar el flujo global de Realtime Presence de SupabaseClient
        scope.launch {
            SupabaseClient.realtimePresenceState.collect { rawMap ->
                val currentMap = _presenceMap.value.toMutableMap()
                val currentTime = System.currentTimeMillis()

                rawMap.forEach { (userId, presence) ->
                    // System Deduplication Check
                    val timeWindow = presence.lastSeen / DEDUPLICATION_WINDOW_MS
                    val dedupKey = "${userId}_${presence.status}_$timeWindow"
                    val lastProcessed = deduplicationCache[dedupKey]

                    if (lastProcessed == null || currentTime - lastProcessed >= DEDUPLICATION_WINDOW_MS) {
                        deduplicationCache[dedupKey] = currentTime
                        
                        // Clean up old deduplication keys periodically
                        if (deduplicationCache.size > 200) {
                            val cutoff = currentTime - (DEDUPLICATION_WINDOW_MS * 2)
                            deduplicationCache.entries.removeIf { it.value < cutoff }
                        }

                        val statusEnum = mapRealtimeStatus(presence.status)

                        if (statusEnum == UserPresenceStatus.OFFLINE) {
                            val prevPresence = currentMap[userId]
                            if (prevPresence != null && prevPresence.status != UserPresenceStatus.OFFLINE) {
                                // Schedule Grace Period before marking OFFLINE
                                if (gracePeriodJobs[userId] == null) {
                                    gracePeriodJobs[userId] = scope.launch {
                                        delay(gracePeriodDurationMs)
                                        val updatedMap = _presenceMap.value.toMutableMap()
                                        val finalInfo = UserPresenceInfo(
                                            userId = userId,
                                            status = UserPresenceStatus.OFFLINE,
                                            secondaryStatus = SecondaryPresenceStatus.NONE,
                                            callAvailability = prevPresence.callAvailability,
                                            lastSeen = presence.lastSeen
                                        )
                                        updatedMap[userId] = finalInfo
                                        _presenceMap.value = updatedMap
                                        PresenceHistoryTracker.recordEvent(userId, UserPresenceStatus.OFFLINE, presence.lastSeen)
                                        gracePeriodJobs.remove(userId)
                                    }
                                }
                            } else {
                                val info = UserPresenceInfo(
                                    userId = userId,
                                    status = UserPresenceStatus.OFFLINE,
                                    secondaryStatus = SecondaryPresenceStatus.NONE,
                                    lastSeen = presence.lastSeen
                                )
                                currentMap[userId] = info
                                PresenceHistoryTracker.recordEvent(userId, UserPresenceStatus.OFFLINE, presence.lastSeen)
                            }
                        } else {
                            // User is ONLINE, AWAY, or BUSY -> cancel any pending grace period job
                            gracePeriodJobs[userId]?.cancel()
                            gracePeriodJobs.remove(userId)

                            val info = UserPresenceInfo(
                                userId = userId,
                                status = statusEnum,
                                secondaryStatus = currentMap[userId]?.secondaryStatus ?: SecondaryPresenceStatus.NONE,
                                callAvailability = currentMap[userId]?.callAvailability ?: CallAvailability.AVAILABLE,
                                lastSeen = presence.lastSeen
                            )
                            currentMap[userId] = info
                            PresenceHistoryTracker.recordEvent(userId, statusEnum, presence.lastSeen)
                        }
                    }
                }

                _presenceMap.value = currentMap
            }
        }
    }

    /** Effective status honoring the Privacy/Presence center settings. */
    private fun effectiveStatus(): UserPresenceStatus {
        val raw = try {
            val uid = SupabaseClient.currentUser?.id ?: "guest"
            val prefs = com.example.PanaApplication.instance
                .getSharedPreferences("panalink_prefs", android.content.Context.MODE_PRIVATE)
            val invisible = prefs.getBoolean("profile_invisibility_$uid", false)
            val presence = prefs.getString("profile_presence_$uid", "online") ?: "online"
            when {
                invisible || presence == "invisible" -> UserPresenceStatus.OFFLINE
                presence == "busy" -> UserPresenceStatus.BUSY
                else -> _currentUserStatus.value
            }
        } catch (e: Exception) {
            _currentUserStatus.value
        }
        return raw
    }

    fun currentEffectiveStatus(): UserPresenceStatus = effectiveStatus()

    internal fun mapRealtimeStatus(rawStatus: String): UserPresenceStatus = when (rawStatus.lowercase()) {
        "online" -> UserPresenceStatus.ONLINE
        "away" -> UserPresenceStatus.AWAY
        "busy", "in_call", "on_call" -> UserPresenceStatus.BUSY
        else -> UserPresenceStatus.OFFLINE
    }

    /** True when the user hides their last-seen from everyone. */
    private fun isLastSeenHidden(): Boolean {
        return try {
            val uid = SupabaseClient.currentUser?.id ?: "guest"
            val prefs = com.example.PanaApplication.instance
                .getSharedPreferences("panalink_prefs", android.content.Context.MODE_PRIVATE)
            val invisible = prefs.getBoolean("profile_invisibility_$uid", false)
            val lastSeenPref = prefs.getString("privacy_last_seen_$uid", "Mis Contactos") ?: "Mis Contactos"
            invisible || lastSeenPref == "Nadie"
        } catch (e: Exception) {
            false
        }
    }

    /** Called by the Presence/Privacy center to publish the manual state immediately. */
    fun applyManualStatusFromSettings(status: String) {
        _currentUserStatus.value = when (status) {
            "busy" -> UserPresenceStatus.BUSY
            "invisible" -> UserPresenceStatus.OFFLINE
            else -> UserPresenceStatus.ONLINE
        }
        if (SupabaseClient.isConnected) {
            SupabaseClient.trackCurrentUserPresence(effectiveStatus().rawValue)
        }
    }

    /**
     * Application-level presence keepalive. Writes this user's row in
     * public.user_presence every [PRESENCE_HEARTBEAT_MS] while the device is in
     * the foreground with Realtime connected, so other clients see a fresh
     * last_seen_at/online via postgres_changes (the DB is now the real source of
     * truth for online status on this backend).
     */
    private var keepaliveJob: Job? = null
    private val PRESENCE_HEARTBEAT_MS = 25_000L

    /** @return true when the app is in the foreground (Realtime is connected). */
    private fun presenceKeepaliveActive(): Boolean =
        com.example.PanaApplication.instance.isAppInForeground && SupabaseClient.isConnected

    fun startHeartbeat(currentUserId: String) {
        stopHeartbeat()
        keepaliveJob = scope.launch {
            while (isActive) {
                // Immediately refresh when we just transitioned to foreground/connected.
                persistPresenceToDatabase(currentUserStatus.value)
                delay(PRESENCE_HEARTBEAT_MS)
                if (!presenceKeepaliveActive()) break
            }
        }
    }

    fun stopHeartbeat() {
        keepaliveJob?.cancel()
        keepaliveJob = null
    }

    /**
     * Actualiza el estado del usuario local.
     * Si es un cambio manual o eventos críticos (Login, Logout, OFFLINE, Cambio dispositivo),
     * escribe a la tabla public.user_presence en PostgreSQL.
     */
    fun updateMyStatus(status: UserPresenceStatus, isManualOrLifecycle: Boolean = true) {
        _currentUserStatus.value = status
        val currentUid = SupabaseClient.currentUser?.id ?: "me"
        PresenceHistoryTracker.recordEvent(currentUid, status)

        if (SupabaseClient.isConnected) {
            // Honors Privacy center: invisible users are tracked as offline.
            SupabaseClient.trackCurrentUserPresence(effectiveStatus().rawValue)
        }

        // Persistir a PostgreSQL SOLAMENTE en eventos de ciclo de vida/cambio manual
        if (isManualOrLifecycle) {
            persistPresenceToDatabase(status)
        }
    }

    /** Maps a local status to the DB enum presence_status_type. null = skip write. */
    private fun dbStatusFor(status: UserPresenceStatus): String? = when (status) {
        UserPresenceStatus.ONLINE -> "online"
        UserPresenceStatus.AWAY -> "away"
        UserPresenceStatus.OFFLINE -> "offline"
        // En la BD no existe valor "busy": los contactos lo muestran como offline.
        UserPresenceStatus.BUSY -> null
    }

    fun setSecondaryStatus(secondaryStatus: SecondaryPresenceStatus) {
        _currentUserSecondaryStatus.value = secondaryStatus
    }

    fun onLogin(userId: String) {
        updateMyStatus(UserPresenceStatus.ONLINE, isManualOrLifecycle = true)
        startHeartbeat(userId)
    }

    fun onLogout() {
        stopHeartbeat()
        updateMyStatus(UserPresenceStatus.OFFLINE, isManualOrLifecycle = true)
    }

    fun onDeviceConnected(userId: String) {
        updateMyStatus(UserPresenceStatus.ONLINE, isManualOrLifecycle = true)
        startHeartbeat(userId)
    }

    private fun persistPresenceToDatabase(status: UserPresenceStatus) {
        scope.launch {
            try {
                // Privacy center: never publish last_seen/status when hidden.
                if (isLastSeenHidden() && status != UserPresenceStatus.OFFLINE) {
                    Log.d(TAG, "persistPresenceToDatabase skipped: user hides presence")
                    return@launch
                }
                val currentUid = SupabaseClient.currentUser?.id ?: return@launch
                val service = SupabaseClient.apiService ?: return@launch
                val dbStatus = dbStatusFor(status) ?: return@launch
                val nowIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(java.util.Date())

                val body = mapOf(
                    "user_id" to currentUid,
                    "status" to dbStatus,
                    "last_seen_at" to nowIso,
                    "updated_at" to nowIso
                )

                service.upsertUserPresence(
                    apiKey = SupabaseClient.supabaseAnonKey,
                    authorization = "Bearer ${SupabaseClient.currentToken ?: SupabaseClient.supabaseAnonKey}",
                    presence = body
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist user_presence to DB: ${e.localizedMessage}")
            }
        }
    }

    fun updateMyCallAvailability(availability: CallAvailability) {
        _currentUserCallAvailability.value = availability
    }

    fun getPresenceForUser(userId: String): UserPresenceInfo {
        return _presenceMap.value[userId] ?: UserPresenceInfo(userId, UserPresenceStatus.OFFLINE)
    }

    /**
     * One-shot fetch of a user's persisted presence row, so a freshly opened
     * profile shows the real last_seen/status immediately instead of waiting up to
     * the next 25s heartbeat.
 */
    fun refreshPresenceForUser(userId: String) {
        scope.launch {
            try {
                val service = SupabaseClient.apiService ?: return@launch
                val resp = service.getUserPresence(
                    apiKey = SupabaseClient.supabaseAnonKey,
                    authorization = "Bearer ${SupabaseClient.currentToken ?: SupabaseClient.supabaseAnonKey}",
                    userIdFilter = "eq.$userId"
                )
                if (resp.isSuccessful) {
                    val row = resp.body()?.firstOrNull() ?: return@launch
                    val rawStatus = row["status"]?.toString() ?: "offline"
                    val statusEnum = if (rawStatus == "away") UserPresenceStatus.AWAY else mapRealtimeStatus(rawStatus)

                    val lastSeen = (try {
                        row["last_seen_at"]?.toString()?.let { java.time.Instant.parse(it).toEpochMilli() }
                    } catch (e: Exception) { null })?: System.currentTimeMillis()
                    // A row left "online" by a device that died>75s ago is stale -> offline..

                    val effectiveStatus = if (
                        (statusEnum == UserPresenceStatus.ONLINE || statusEnum == UserPresenceStatus.AWAY) &&
                        System.currentTimeMillis() - lastSeen > ONLINE_STALE_TTL_MS
                    ) UserPresenceStatus.OFFLINE else statusEnum
                    val info = UserPresenceInfo(
                        userId = userId,
                        status = effectiveStatus,
                        lastSeen = lastSeen
                    )
                    // Don't overwrite a fresher realtime event with an older DB snapshot.
                    val existing = _presenceMap.value[userId]
                    if (existing == null || existing.lastSeen <= info.lastSeen) {
                        val updated = _presenceMap.value.toMutableMap()
                        updated[userId] = info
                        _presenceMap.value = updated
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "refreshPresenceForUser failed: ${e.localizedMessage}")
            }
        }
    }

    fun isUserAvailableForCall(userId: String): Pair<Boolean, String> {
        val presence = getPresenceForUser(userId)
        return when (presence.status) {
            UserPresenceStatus.BUSY -> Pair(false, "El usuario está en otra llamada")
            UserPresenceStatus.OFFLINE -> Pair(false, "El usuario está desconectado")
            UserPresenceStatus.ONLINE, UserPresenceStatus.AWAY -> {
                if (presence.callAvailability == CallAvailability.MESSAGES_ONLY) {
                    Pair(false, "Este usuario prefiere mensajes")
                } else {
                    Pair(true, "Disponible")
                }
            }
        }
    }
}

