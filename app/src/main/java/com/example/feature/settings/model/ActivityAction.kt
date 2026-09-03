package com.example.feature.settings.model

sealed interface ActivityAction {
    object RefreshSummary : ActivityAction
    object LoadDiagnostics : ActivityAction
    object RefreshStorage : ActivityAction
    object RefreshDevices : ActivityAction
    object ClearError : ActivityAction
}
