package com.example.feature.diagnostics

import com.example.PanaApplication
import com.example.feature.diagnostics.data.DiagnosticsRepository
import com.example.feature.diagnostics.model.DiagnosticCategory
import com.example.feature.diagnostics.model.DiagnosticSeverity

/**
 * Small, safe facade for Stories instrumentation.
 *
 * Exposes only metadata (stateId / vcdn id) and never message content, tokens,
 * credentials or raw URLs with query strings. Mirrors [ChatDiagnostics] so the
 * existing "Diagnóstico del sistema" screen shows a real timeline for stories.
 */
object StoryDiagnostics {
    private fun repository(): DiagnosticsRepository =
        DiagnosticsRepository.getInstance(PanaApplication.instance.applicationContext)

    fun event(
        name: String,
        severity: DiagnosticSeverity = DiagnosticSeverity.INFO,
        durationMs: Long? = null,
        correlationId: String? = null,
        details: String? = null
    ) {
        try {
            repository().record(
                category = DiagnosticCategory.STORIES,
                event = name,
                severity = severity,
                durationMs = durationMs,
                correlationId = correlationId,
                details = details?.take(220)
            )
        } catch (_: Throwable) {
            // Diagnostics must never affect story playback.
        }
    }

    fun started(name: String, correlationId: String? = null, details: String? = null) =
        event(name, DiagnosticSeverity.INFO, correlationId = correlationId, details = details)

    fun completed(
        name: String,
        startedAtMs: Long,
        correlationId: String? = null,
        details: String? = null
    ) = event(
        name = name,
        severity = DiagnosticSeverity.SUCCESS,
        durationMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L),
        correlationId = correlationId,
        details = details
    )

    fun failed(
        name: String,
        startedAtMs: Long? = null,
        correlationId: String? = null,
        throwable: Throwable? = null,
        details: String? = null
    ) {
        val safeType = throwable?.javaClass?.simpleName
        val safeDetails = buildString {
            if (!details.isNullOrBlank()) append(details.take(220))
            if (!safeType.isNullOrBlank()) {
                if (isNotEmpty()) append(", ")
                append("throwableType=").append(safeType)
            }
        }.ifBlank { null }

        event(
            name = name,
            severity = DiagnosticSeverity.ERROR,
            durationMs = startedAtMs?.let { (System.currentTimeMillis() - it).coerceAtLeast(0L) },
            correlationId = correlationId,
            details = safeDetails
        )
    }

    fun correlationId(): String = java.util.UUID.randomUUID().toString().take(36)
}