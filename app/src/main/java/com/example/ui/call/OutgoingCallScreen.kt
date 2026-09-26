package com.example.ui.call

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.bounceClick
import com.example.ui.settings.ios.IosSettingsColors

/**
 * OutgoingCallScreen displays outgoing call feedback to the initiator,
 * showing the opponent's details and active connection state with glowing halos.
 */
@Composable
fun OutgoingCallScreen(
    opponentId: String? = null,
    opponentName: String,
    isVideo: Boolean,
    onCancel: () -> Unit,
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
            // Header Info using CallStatusText
            CallStatusText(
                statusText = if (isVideo) "Llamando por video..." else "Llamando...",
                opponentName = opponentName,
                statusColor = IosSettingsColors.blue,
                modifier = Modifier.padding(top = 64.dp)
            )

            // Centered Pulsing Avatar (glowing wave)
            CallAvatarPulse(
                name = opponentName,
                userId = opponentId,
                pulseColor = IosSettingsColors.blue,
                avatarSize = 130.dp,
                isAnimating = true
            )

            // Cancel Call Button (Bottom Center)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 64.dp)
            ) {
                CallActionButton(
                    onClick = onCancel,
                    icon = Icons.Default.CallEnd,
                    contentDescription = "Cancelar Llamada",
                    containerColor = IosSettingsColors.red, // Red 500
                    contentColor = IosSettingsColors.onAccent,
                    size = 68.dp,
                    iconSize = 32.dp,
                    label = "Cancelar",
                    testTag = "cancel_button"
                )
            }
        }
    }
}
