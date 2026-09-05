package com.example.util

import android.content.Context
import androidx.startup.Initializer

/**
 * Runs once whenever the application process starts. This is intentionally
 * independent from UI/ViewModel lifecycle so durable Room queues are recovered
 * after process death or reboot as soon as the app process is created.
 */
class OfflineQueueRecoveryInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        OfflineQueueRecovery.reconcile(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
