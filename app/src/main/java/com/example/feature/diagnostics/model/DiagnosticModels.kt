package com.example.feature.diagnostics.model

import java.util.Locale

/** Structured, sanitized Panalink diagnostic event. Never store tokens, URLs with credentials, or message content. */
data class DiagnosticEvent(
    val timestampMs: Long,
    val category: DiagnosticCategory,
    val event: String,
    val severity: DiagnosticSeverity = DiagnosticSeverity.INFO,
    val durationMs: Long? = null,
    val correlationId: String? = null,
    val details: String? = null
) {
    fun displayTime(): String = java.text.SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        .format(java.util.Date(timestampMs))
}

enum class DiagnosticCategory(val label: String) {
    ALL("Todos"),
    REELS("Reels"),
    NETWORK("Red/VCDN"),
    EXOPLAYER("ExoPlayer"),
    CACHE("Caché"),
    SUPABASE("Supabase"),
    WORK_MANAGER("WorkManager"),
    ROOM("Room"),
    ERRORS("Errores")
}

enum class DiagnosticSeverity { INFO, SUCCESS, WARNING, ERROR }

enum class DiagnosticCaptureState { STOPPED, CAPTURING }

fun DiagnosticCategory.matches(event: DiagnosticEvent): Boolean =
    this == DiagnosticCategory.ALL ||
        (this == DiagnosticCategory.ERRORS && event.severity == DiagnosticSeverity.ERROR) ||
        event.category == this
