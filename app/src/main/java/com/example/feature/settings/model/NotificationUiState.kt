package com.example.feature.settings.model

data class NotificationUiState(
    val globalEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val soundTone: String = "default",
    val vibrationPattern: String = "default",
    val chatSoundEnabled: Boolean = true,
    val chatSoundTone: String = "water_drop",
    val outgoingSoundEnabled: Boolean = true,
    val isLoading: Boolean = false
)
