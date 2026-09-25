package com.example.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.bounceClick
import com.example.ui.settings.ios.IosSettingsColors

/**
 * AudioCallScreen hosts active voice-only communication sessions
 * styled with the polished PanaLink V2.0 aesthetic.
 */
@Composable
fun AudioCallScreen(
    opponentId: String? = null,
    opponentName: String,
    formattedDuration: String,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    isConnected: Boolean = true,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IosSettingsColors.groupBackground) // Slate 900
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            // Header: Opponent Info and Status using CallStatusText
            CallStatusText(
                statusText = if (isConnected) "Llamada de voz activa" else "Reconectando... 📡",
                opponentName = opponentName,
                statusColor = if (isConnected) Color.White.copy(alpha = 0.6f) else IosSettingsColors.yellow,
                durationText = formattedDuration,
                isSignalWarning = !isConnected,
                modifier = Modifier.padding(top = 64.dp)
            )

            // Centered visual active audio session avatar
            CallAvatarPulse(
                name = opponentName,
                userId = opponentId,
                pulseColor = if (isConnected) IosSettingsColors.blue else IosSettingsColors.orange,
                avatarSize = 130.dp,
                isAnimating = isConnected
            )

            // Bottom call controls using ActiveCallControls
            ActiveCallControls(
                isMuted = isMuted,
                isSpeakerOn = isSpeakerOn,
                onMuteToggle = onMuteToggle,
                onSpeakerToggle = onSpeakerToggle,
                onEndCall = onEndCall,
                isVideoCall = false,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }
}
