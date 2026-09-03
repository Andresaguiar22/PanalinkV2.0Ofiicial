package com.example.feature.settings.model

sealed interface PrivacyAction {
    data class UpdateLastSeen(val visibility: String) : PrivacyAction
    data class ToggleReadReceipts(val enabled: Boolean) : PrivacyAction
    data class ToggleInvisibleMode(val enabled: Boolean) : PrivacyAction
    data class ToggleSmartReadReceipts(val enabled: Boolean) : PrivacyAction
    data class UpdatePresence(val status: String) : PrivacyAction
    object ClearMessages : PrivacyAction
}
