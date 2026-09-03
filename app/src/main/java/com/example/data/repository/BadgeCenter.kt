package com.example.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Centro de señalización de la bottom bar: cuenta lo nuevo por sección
 * (Momentos, Clips, Llamadas perdidas) y lo expone como flows para que la UI
 * reaccione en vivo. Los contadores de "visto" se persisten en SharedPrefs.
 *
 * Chats y Gente no viven aquí: su conteo sale directo de Room (unreadCount
 * por chat) y de las solicitudes pendientes del ChatsViewModel.
 */
object BadgeCenter {
    private const val PREFS = "panalink_badges"
    private const val KEY_LAST_SEEN_MOMENTOS_MS = "last_seen_momentos_ms"
    private const val KEY_LAST_SEEN_CLIPS_MS = "last_seen_clips_ms"
    private const val KEY_MISSED_CALLS = "missed_calls_count"

    private val _lastSeenMomentosMs = MutableStateFlow(0L)
    val lastSeenMomentosMs: StateFlow<Long> = _lastSeenMomentosMs.asStateFlow()

    private val _lastSeenClipsMs = MutableStateFlow(0L)
    val lastSeenClipsMs: StateFlow<Long> = _lastSeenClipsMs.asStateFlow()

    private val _missedCalls = MutableStateFlow(0)
    val missedCalls: StateFlow<Int> = _missedCalls.asStateFlow()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _lastSeenMomentosMs.value = prefs.getLong(KEY_LAST_SEEN_MOMENTOS_MS, 0L)
        _lastSeenClipsMs.value = prefs.getLong(KEY_LAST_SEEN_CLIPS_MS, 0L)
        _missedCalls.value = prefs.getInt(KEY_MISSED_CALLS, 0)
    }

    fun markMomentosSeen(context: Context) {
        _lastSeenMomentosMs.value = System.currentTimeMillis()
        persist(context, KEY_LAST_SEEN_MOMENTOS_MS, _lastSeenMomentosMs.value)
    }

    fun markClipsSeen(context: Context) {
        _lastSeenClipsMs.value = System.currentTimeMillis()
        persist(context, KEY_LAST_SEEN_CLIPS_MS, _lastSeenClipsMs.value)
    }

    fun recordMissedCall(context: Context) {
        init(context)
        _missedCalls.value += 1
        persist(context, KEY_MISSED_CALLS, _missedCalls.value)
    }

    fun clearMissedCalls(context: Context) {
        _missedCalls.value = 0
        persist(context, KEY_MISSED_CALLS, 0)
    }

    private fun persist(context: Context, key: String, value: Long) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(key, value).apply()
    }

    private fun persist(context: Context, key: String, value: Int) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(key, value).apply()
    }

    /** Cuenta estados (historias o reels) de otros usuarios creados después de `sinceMs`. */
    fun countNewSince(createdAts: List<String?>, sinceMs: Long): Int {
        if (createdAts.isEmpty()) return 0
        return createdAts.count { parseServerTimestampMs(it) > sinceMs }
    }

    /**
     * Parsea timestamps del backend ("2026-08-25T12:00:00.123456+00:00", con Z,
     * con espacio...) tomando la parte civil como UTC.
     */
    fun parseServerTimestampMs(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return try {
            val core = raw.trim().replace(' ', 'T').take(19)
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(core)?.time ?: 0L
        } catch (_: Throwable) {
            0L
        }
    }
}
