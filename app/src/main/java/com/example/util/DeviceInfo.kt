package com.example.util

import android.content.Context
import android.os.Build
import java.util.UUID

/**
 * Identidad de instalación para control de sesiones activas.
 * Genera una vez un UUID persistente (identifica este dispositivo en la tabla user_devices).
 * El nombre es legible para mostrarlo en los avisos ("Xiaomi Redmi Note" o "Pixel 7").
 */
object DeviceInfo {
    private const val PREFS_NAME = "panalink_device_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_DEVICE_NAME = "device_name"

    @Volatile
    private var cachedDeviceId: String? = null

    fun getDeviceId(context: Context): String {
        cachedDeviceId?.let { return it }
        synchronized(this) {
            cachedDeviceId?.let { return it }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existing = prefs.getString(KEY_DEVICE_ID, null)
            if (!existing.isNullOrBlank()) {
                cachedDeviceId = existing
                return existing
            }
            val fresh = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, fresh).apply()
            cachedDeviceId = fresh
            return fresh
        }
    }

    fun getDeviceName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_DEVICE_NAME, null)
        if (!saved.isNullOrBlank()) return saved
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val name = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
        prefs.edit().putString(KEY_DEVICE_NAME, name).apply()
        return name
    }
}