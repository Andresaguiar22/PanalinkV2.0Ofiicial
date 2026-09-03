package com.example.live.domain.model

sealed interface LiveConnectionState {
    data object Disconnected : LiveConnectionState
    data object Connecting : LiveConnectionState
    data object Connected : LiveConnectionState
    data object Reconnecting : LiveConnectionState
    data class Error(val message: String) : LiveConnectionState
}
