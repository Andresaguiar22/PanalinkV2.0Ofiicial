package com.example.service

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe deduplicator for system notifications across FCM and Supabase Realtime.
 * Ensures that a message identified by [clientMessageUuid] or [messageId]
 * is only notified once to the user's notification tray.
 */
object NotificationDeduplicator {
    private const val TAG = "NotificationDedupe"
    private const val EXPIRATION_MS = 5 * 60 * 1000L // 5 minutes TTL
    private const val MAX_ENTRIES = 500

    private val notifiedEntries = ConcurrentHashMap<String, Long>()

    /**
     * Checks if a notification should be displayed for the given message identifiers.
     * If neither identifier has been seen recently, both are recorded and returns true.
     * If either has already been notified within the expiration window, returns false.
     */
    @Synchronized
    fun shouldNotifyMessage(clientMessageUuid: String?, messageId: String?): Boolean {
        cleanupExpired()

        val cleanUuid = clientMessageUuid?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("temp_") }
        val cleanMsgId = messageId?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("temp_") }

        val now = System.currentTimeMillis()

        // Check if clientMessageUuid already notified
        if (cleanUuid != null) {
            val lastSeen = notifiedEntries[cleanUuid]
            if (lastSeen != null && (now - lastSeen) < EXPIRATION_MS) {
                Log.d(TAG, "Suppressed duplicate notification by clientMessageUuid: $cleanUuid")
                return false
            }
        }

        // Check if messageId already notified
        if (cleanMsgId != null) {
            val lastSeen = notifiedEntries[cleanMsgId]
            if (lastSeen != null && (now - lastSeen) < EXPIRATION_MS) {
                Log.d(TAG, "Suppressed duplicate notification by messageId: $cleanMsgId")
                return false
            }
        }

        // Record both identifiers as notified
        cleanUuid?.let { notifiedEntries[it] = now }
        cleanMsgId?.let { notifiedEntries[it] = now }

        return true
    }

    /**
     * Checks if a generic notification (e.g. friend request, social like) should be notified.
     */
    @Synchronized
    fun shouldNotifyGeneric(notificationKey: String?): Boolean {
        if (notificationKey.isNullOrBlank()) return true
        cleanupExpired()

        val now = System.currentTimeMillis()
        val lastSeen = notifiedEntries[notificationKey]
        if (lastSeen != null && (now - lastSeen) < EXPIRATION_MS) {
            Log.d(TAG, "Suppressed duplicate generic notification for key: $notificationKey")
            return false
        }

        notifiedEntries[notificationKey] = now
        return true
    }

    /**
     * Explicitly marks identifiers as notified (e.g., when handled in active foreground chat).
     */
    @Synchronized
    fun markAsNotified(clientMessageUuid: String?, messageId: String?) {
        val now = System.currentTimeMillis()
        clientMessageUuid?.trim()?.takeIf { it.isNotEmpty() }?.let { notifiedEntries[it] = now }
        messageId?.trim()?.takeIf { it.isNotEmpty() }?.let { notifiedEntries[it] = now }
    }

    private fun cleanupExpired() {
        if (notifiedEntries.size < MAX_ENTRIES) return
        val now = System.currentTimeMillis()
        val iterator = notifiedEntries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > EXPIRATION_MS) {
                iterator.remove()
            }
        }
    }

    @Synchronized
    fun shouldNotify(clientMessageUuid: String?, messageId: String?): Boolean =
        shouldNotifyMessage(clientMessageUuid, messageId)

    fun clear() {
        notifiedEntries.clear()
    }

    fun clearForTest() {
        notifiedEntries.clear()
    }
}
