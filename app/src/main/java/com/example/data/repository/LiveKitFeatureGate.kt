package com.example.data.repository

/**
 * Central feature gate for the LiveKit SFU media transport. When ON, voice
 * rooms (and calls) route media through LiveKit instead of the P2P WebRTC
 * mesh / Socket.IO signaling. Default ON for the beta.
 *
 * Flip to false to fall back to the legacy engines without code changes.
 */
object LiveKitFeatureGate {
    @Volatile var enabled: Boolean = true
    fun isEnabled(): Boolean = enabled
}
