package com.example.feature.diagnostics

import android.content.Context
import com.example.feature.diagnostics.data.DiagnosticsRepository
import com.example.feature.diagnostics.model.DiagnosticCategory
import com.example.feature.diagnostics.model.DiagnosticSeverity

/**
 * Small domain-neutral facade for application instrumentation.
 * Callers should describe process milestones, not dump Logcat-style text.
 */
class DiagnosticsLogger private constructor(context: Context) {
    private val repository = DiagnosticsRepository.getInstance(context.applicationContext)

    fun event(
        category: DiagnosticCategory,
        event: String,
        severity: DiagnosticSeverity = DiagnosticSeverity.INFO,
        durationMs: Long? = null,
        correlationId: String? = null,
        details: String? = null
    ) = repository.record(category, event, severity, durationMs, correlationId, details)

    fun error(
        category: DiagnosticCategory,
        event: String,
        correlationId: String? = null,
        details: String? = null
    ) = event(category, event, DiagnosticSeverity.ERROR, correlationId = correlationId, details = details)

    fun flowStart(category: DiagnosticCategory, event: String, correlationId: String? = null, details: String? = null) =
        event(category, "$event iniciado", DiagnosticSeverity.INFO, correlationId = correlationId, details = details)

    fun flowEnd(category: DiagnosticCategory, event: String, durationMs: Long, correlationId: String? = null, details: String? = null) =
        event(category, "$event completado", DiagnosticSeverity.SUCCESS, durationMs, correlationId, details)

    companion object {
        @Volatile private var instance: DiagnosticsLogger? = null

        fun getInstance(context: Context): DiagnosticsLogger =
            instance ?: synchronized(this) {
                instance ?: DiagnosticsLogger(context.applicationContext).also { instance = it }
            }
    }
}
