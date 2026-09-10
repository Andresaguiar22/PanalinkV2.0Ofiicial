package com.example.ui.settings.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.feature.diagnostics.data.DiagnosticsRepository
import com.example.feature.diagnostics.model.DiagnosticCaptureState
import com.example.feature.diagnostics.model.DiagnosticCategory
import com.example.feature.diagnostics.model.DiagnosticEvent
import com.example.feature.diagnostics.model.matches
import kotlinx.coroutines.flow.StateFlow

class DiagnosticsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DiagnosticsRepository.getInstance(application.applicationContext)

    val events: StateFlow<List<DiagnosticEvent>> = repository.events
    val captureState: StateFlow<DiagnosticCaptureState> = repository.captureState

    fun setCapture(enabled: Boolean) {
        if (enabled) repository.startCapture() else repository.stopCapture()
    }

    fun clear() = repository.clear()

    fun exportText(): String = repository.exportText()

    fun filteredEvents(category: DiagnosticCategory): List<DiagnosticEvent> =
        events.value.filter { category.matches(it) }
}
