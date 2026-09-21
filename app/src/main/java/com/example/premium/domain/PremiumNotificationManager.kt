package com.example.premium.domain

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.premium.data.repository.PremiumRepositoryImpl
import com.example.premium.domain.model.PremiumNotification
import com.example.premium.domain.repository.PremiumRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Categorías de notificaciones Premium 2.0 (coinciden con la tabla
 * `in_app_notifications.category`).
 */
object PremiumNotifCategories {
    const val PREMIUM = "PREMIUM"
    const val SOCIAL = "SOCIAL"
    const val LIVE = "LIVE"
    const val REWARD = "REWARD"
    const val COINS = "COINS"
    const val PROMOTION = "PROMOTION"
    const val SYSTEM = "SYSTEM"
    const val SECURITY = "SECURITY"
}

/**
 * Manager de notificaciones in-app Premium 2.0.
 *
 * - Expone [notifications] (StateFlow) con las notificaciones del backend.
 * - Mantiene preferencias por categoría (SharedPreferences locales) en
 *   [enabledCategories] y [pushEnabled].
 * - Escucha el [PremiumEventBus] para optimística/refresh cuando ocurren
 *   monedas, recompensas o misiones.
 */
object PremiumNotificationManager {
    private const val TAG = "PanalinkNotifs"
    private const val PREFS = "panalink_premium_notifs"
    private const val KEY_ENABLED = "enabled_categories"
    private const val KEY_UNREAD = "unread_count"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository: PremiumRepository by lazy { PremiumRepositoryImpl() }

    private val _notifications = MutableStateFlow<List<PremiumNotification>>(emptyList())
    val notifications: StateFlow<List<PremiumNotification>> = _notifications.asStateFlow()

    private val _enabledCategories =
        MutableStateFlow(setOf(PremiumNotifCategories.PREMIUM, PremiumNotifCategories.REWARD, PremiumNotifCategories.COINS))
    val enabledCategories: StateFlow<Set<String>> = _enabledCategories.asStateFlow()

    private val _pushEnabled = MutableStateFlow(true)
    val pushEnabled: StateFlow<Boolean> = _pushEnabled.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private var prefs: SharedPreferences? = null
    private var userId: String? = null

    /** Inicializa con contexto y uid del usuario (llamar tras auth). */
    fun initialize(context: Context, uid: String) {
        if (prefs == null && userId == null) {
            prefs = context.getSharedPreferences(PREFS + "_$uid", Context.MODE_PRIVATE)
            userId = uid
            loadPrefs()
            refresh()
        }
    }

    fun refresh() {
        scope.launch {
            repository.getNotifications().onSuccess { list ->
                _notifications.value = list
                _unreadCount.value = list.count { !it.isRead }
            }
        }
    }

    fun markRead(id: String) {
        scope.launch {
            repository.markNotificationRead(id).onSuccess {
                _notifications.value = _notifications.value.map { n ->
                    if (n.id == id) n.copy(isRead = true) else n
                }
                _unreadCount.value = _notifications.value.count { !it.isRead }
            }
        }
    }

    fun markAllRead() {
        val ids = _notifications.value.filter { !it.isRead }.map { it.id }
        ids.forEach { markRead(it) }
    }

    /** Categorías activadas. */
    fun setCategoryEnabled(category: String, enabled: Boolean) {
        val newSet = _enabledCategories.value.toMutableSet().apply {
            if (enabled) add(category) else remove(category)
        }
        _enabledCategories.value = newSet
        persistPrefs()
    }

    fun setAllEnabled(categories: Set<String>) {
        _enabledCategories.value = categories
        persistPrefs()
    }

    fun setPushEnabled(enabled: Boolean) {
        _pushEnabled.value = enabled
        persistPrefs()
    }

    private fun loadPrefs() {
        val p = prefs ?: return
        val enabled = p.getStringSet(KEY_ENABLED, null)
        if (enabled != null) _enabledCategories.value = enabled
        _pushEnabled.value = p.getBoolean("push_enabled", true)
        _unreadCount.value = p.getInt(KEY_UNREAD, _unreadCount.value)
    }

    private fun persistPrefs() {
        val p = prefs ?: return
        p.edit()
            .putStringSet(KEY_ENABLED, _enabledCategories.value)
            .putBoolean("push_enabled", _pushEnabled.value)
            .putInt(KEY_UNREAD, _unreadCount.value)
            .apply()
    }
}