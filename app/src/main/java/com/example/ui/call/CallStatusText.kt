package com.example.ui.call

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.settings.ios.IosSettingsColors

/**
 * CallStatusText displays elegant typography indicating the status of the call,
 * with optional colors and support for badges (e.g. duration or reconnections).
 */
@Composable
fun CallStatusText(
    statusText: String,
    opponentName: String,
    modifier: Modifier = Modifier,
    statusColor: Color = IosSettingsColors.label.copy(alpha = 0.6f),
    durationText: String? = null,
    isSignalWarning: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // Status indicator (Animated)
        AnimatedContent(
            targetState = statusText,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "statusTextAnim"
        ) { text ->
            Text(
                text = text,
                color = if (isSignalWarning) IosSettingsColors.yellow else statusColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Contact Name
        Text(
            text = opponentName,
            color = IosSettingsColors.label,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        // Duration Badge
        if (!durationText.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .background(IosSettingsColors.separator, shape = CircleShape)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = durationText,
                    color = IosSettingsColors.blue, // Cyan 400
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
