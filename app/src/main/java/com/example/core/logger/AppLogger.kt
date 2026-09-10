package com.example.core.logger

import android.util.Log
import com.example.PanaApplication
import com.example.feature.diagnostics.data.DiagnosticsRepository
import com.example.feature.diagnostics.model.DiagnosticCategory
import com.example.feature.diagnostics.model.DiagnosticSeverity

object AppLogger {
    private const val TAG = "PanaLinkCore"
    private var isDebug: Boolean = true

    fun init(debugMode: Boolean) {
        isDebug = debugMode
    }

    fun d(tag: String = TAG, message: String) {
        if (isDebug) {
            Log.d(tag, sanitize(message))
        }
    }

    fun i(tag: String = TAG, message: String) {
        if (isDebug) {
            Log.i(tag, sanitize(message))
        }
    }

    fun w(tag: String = TAG, message: String) {
        val safeMessage = sanitize(message)
        if (isDebug) {
            Log.w(tag, safeMessage)
        }
        recordDiagnostic(tag, safeMessage, DiagnosticSeverity.WARNING)
    }

    fun e(tag: String = TAG, message: String, throwable: Throwable? = null) {
        val safeMessage = sanitize(message)
        Log.e(tag, safeMessage, throwable)
        recordDiagnostic(tag, safeMessage, DiagnosticSeverity.ERROR, throwable)
    }

    private fun recordDiagnostic(
        tag: String,
        message: String,
        severity: DiagnosticSeverity,
        throwable: Throwable? = null
    ) {
        try {
            val repository = DiagnosticsRepository.getInstance(PanaApplication.instance.applicationContext)
            val throwableType = throwable?.javaClass?.simpleName
            val details = buildString {
                append("source=$tag")
                if (!throwableType.isNullOrBlank()) append(", throwableType=$throwableType")
            }
            repository.record(
                category = DiagnosticCategory.ERRORS,
                event = if (severity == DiagnosticSeverity.ERROR) "AppLogger error" else "AppLogger warning",
                severity = severity,
                details = "$message | $details"
            )
        } catch (_: Throwable) {
            // Diagnostics must never break the application's existing logging path.
        }
    }

    private fun sanitize(message: String): String {
        return message
            .replace(Regex("(?i)(bearer|token|password|secret)=[^&\\s]+"), "$1=REDACTED")
            .replace(Regex("(?i)(authorization|cookie|access-token|signedUrl)=[^&\\s]+"), "$1=REDACTED")
            .replace(Regex("https?://[^\\s]+"), "URL_REDACTED")
            .take(1000)
    }
}
