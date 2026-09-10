package com.example.feature.diagnostics.data

import android.content.Context
import com.example.feature.diagnostics.model.DiagnosticCaptureState
import com.example.feature.diagnostics.model.DiagnosticCategory
import com.example.feature.diagnostics.model.DiagnosticEvent
import com.example.feature.diagnostics.model.DiagnosticSeverity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Lightweight Panalink process timeline. It is independent from Android Logcat. */
class DiagnosticsRepository private constructor(private val context: Context) {
    companion object {
        private const val MAX_EVENTS = 2000
        private const val PREFS = "panalink_diagnostics"
        private const val KEY_CAPTURE = "capture_enabled"
        private const val KEY_EVENTS = "events"

        @Volatile private var instance: DiagnosticsRepository? = null

        fun getInstance(context: Context): DiagnosticsRepository =
            instance ?: synchronized(this) {
                instance ?: DiagnosticsRepository(context.applicationContext).also { instance = it }
            }
    }

    private val _events = MutableStateFlow<List<DiagnosticEvent>>(emptyList())
    val events: StateFlow<List<DiagnosticEvent>> = _events.asStateFlow()
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _captureState = MutableStateFlow(
        if (prefs.getBoolean(KEY_CAPTURE, false)) DiagnosticCaptureState.CAPTURING
        else DiagnosticCaptureState.STOPPED
    )
    val captureState: StateFlow<DiagnosticCaptureState> = _captureState.asStateFlow()

    init { _events.value = readPersistedEvents() }

    fun startCapture() {
        _captureState.value = DiagnosticCaptureState.CAPTURING
        prefs.edit().putBoolean(KEY_CAPTURE, true).apply()
        record(DiagnosticCategory.ROOM, "Captura de diagnóstico iniciada", DiagnosticSeverity.INFO)
    }

    fun stopCapture() {
        record(DiagnosticCategory.ROOM, "Captura de diagnóstico detenida", DiagnosticSeverity.INFO)
        _captureState.value = DiagnosticCaptureState.STOPPED
        prefs.edit().putBoolean(KEY_CAPTURE, false).apply()
    }

    fun clear() {
        _events.value = emptyList()
        ioScope.launch { prefs.edit().remove(KEY_EVENTS).commit() }
    }

    fun record(
        category: DiagnosticCategory,
        event: String,
        severity: DiagnosticSeverity = DiagnosticSeverity.INFO,
        durationMs: Long? = null,
        correlationId: String? = null,
        details: String? = null
    ) {
        if (_captureState.value != DiagnosticCaptureState.CAPTURING && severity != DiagnosticSeverity.ERROR) return
        val item = DiagnosticEvent(
            timestampMs = System.currentTimeMillis(),
            category = category,
            event = event.take(120),
            severity = severity,
            durationMs = durationMs?.coerceAtLeast(0L),
            correlationId = correlationId?.take(64),
            details = sanitizeDetails(details)
        )
        _events.update { (it + item).takeLast(MAX_EVENTS) }
        val snapshot = _events.value
        ioScope.launch { persist(snapshot) }
    }

    fun exportText(): String {
        val snapshot = _events.value
        return buildString {
            appendLine("PANALINK DIAGNÓSTICO")
            appendLine("Eventos: ${snapshot.size}")
            appendLine("Generado: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
            appendLine("----------------------------------------")
            snapshot.forEach { item ->
                append(item.displayTime()).append(" | ")
                    .append(item.category.label).append(" | ")
                    .append(item.severity.name).append(" | ")
                    .append(item.event)
                item.durationMs?.let { append(" | ${it}ms") }
                item.correlationId?.let { append(" | id=$it") }
                item.details?.let { append(" | $it") }
                appendLine()
            }
        }
    }

    private fun sanitizeDetails(details: String?): String? {
        if (details.isNullOrBlank()) return null
        var value = details.take(500)
        value = value.replace(
            Regex("(?i)(token|authorization|password|secret|cookie|signedUrl|access[_-]?token)\\s*[:=]\\s*[^,;\\s]+"),
            "$1=[REDACTED]"
        )
        value = value.replace(Regex("(?i)https?://\\S+"), "[URL_REDACTED]")
        return value.take(300)
    }

    private fun persist(events: List<DiagnosticEvent>) {
        val array = JSONArray()
        events.takeLast(MAX_EVENTS).forEach { item ->
            array.put(JSONObject().apply {
                put("timestampMs", item.timestampMs)
                put("category", item.category.name)
                put("event", item.event)
                put("severity", item.severity.name)
                item.durationMs?.let { put("durationMs", it) }
                item.correlationId?.let { put("correlationId", it) }
                item.details?.let { put("details", it) }
            })
        }
        prefs.edit().putString(KEY_EVENTS, array.toString()).commit()
    }

    private fun readPersistedEvents(): List<DiagnosticEvent> = try {
        val raw = prefs.getString(KEY_EVENTS, null) ?: return emptyList()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(DiagnosticEvent(
                    timestampMs = item.getLong("timestampMs"),
                    category = DiagnosticCategory.valueOf(item.getString("category")),
                    event = item.getString("event"),
                    severity = DiagnosticSeverity.valueOf(item.getString("severity")),
                    durationMs = if (item.has("durationMs")) item.getLong("durationMs") else null,
                    correlationId = item.optString("correlationId", null),
                    details = item.optString("details", null)
                ))
            }
        }.takeLast(MAX_EVENTS)
    } catch (_: Exception) { emptyList() }
}
