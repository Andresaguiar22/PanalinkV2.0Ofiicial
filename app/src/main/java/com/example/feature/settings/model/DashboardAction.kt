package com.example.feature.settings.model

sealed interface DashboardAction {
    object RefreshDashboard : DashboardAction
    object ClearError : DashboardAction
}
