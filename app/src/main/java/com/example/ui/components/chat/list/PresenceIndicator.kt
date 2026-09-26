package com.example.ui.components.chat.list

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.settings.ios.IosSettingsColors

@Composable
fun PresenceIndicator(
    isOnline: Boolean = false,
    status: String = if (isOnline) "online" else "offline",
    secondaryStatus: String? = null,
    size: Dp = 12.dp,
    borderColor: Color = IosSettingsColors.groupBackground,
    showOffline: Boolean = false,
    showText: Boolean = false,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
    modifier: Modifier = Modifier
) {
    val activeStatus = (secondaryStatus ?: status).lowercase()
    val indicatorColor = when (activeStatus) {
        "online" -> IosSettingsColors.green
        "away" -> IosSettingsColors.orange
        "busy", "in_call", "on_call", "voice_call", "video_call" -> IosSettingsColors.red
        "typing", "recording_audio", "recording", "uploading_file", "uploading" -> IosSettingsColors.blue
        "dnd" -> IosSettingsColors.red
        "messages_only" -> IosSettingsColors.purple
        else -> IosSettingsColors.secondaryLabel
    }

    val isVisible = isOnline || activeStatus in listOf(
        "online", "away", "busy", "in_call", "on_call", "typing",
        "recording_audio", "recording", "uploading_file", "uploading",
        "voice_call", "video_call", "dnd", "messages_only"
    ) || showOffline

    if (!isVisible) return

    val animatedColor by animateColorAsState(targetValue = indicatorColor, label = "presenceColor")

    val transition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseScale by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (activeStatus in listOf("online", "busy", "typing", "recording_audio", "uploading_file", "voice_call", "video_call")) 1.25f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val labelText = when (activeStatus) {
        "online" -> "En línea"
        "away" -> "Ausente"
        "busy", "in_call", "on_call" -> "En llamada"
        "typing" -> "Escribiendo..."
        "recording_audio", "recording" -> "Grabando audio..."
        "uploading_file", "uploading" -> "Subiendo archivo..."
        "voice_call" -> "Llamada de voz"
        "video_call" -> "Videollamada"
        "messages_only" -> "Solo mensajes"
        "dnd" -> "No molestar"
        else -> "Desconectado"
    }

    val iconVector = when (activeStatus) {
        "typing" -> Icons.Rounded.Edit
        "recording_audio", "recording" -> Icons.Rounded.Mic
        "uploading_file", "uploading" -> Icons.Rounded.FileUpload
        "voice_call" -> Icons.Rounded.Call
        "video_call" -> Icons.Rounded.Videocam
        "dnd" -> Icons.Rounded.NotificationsOff
        "messages_only" -> Icons.AutoMirrored.Filled.Chat
        else -> null
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .scale(if (activeStatus in listOf("online", "busy", "typing", "recording_audio", "uploading_file", "voice_call", "video_call")) pulseScale else 1.0f)
                .size(if (iconVector != null) size * 1.3f else size)
                .background(animatedColor, CircleShape)
                .border(1.5.dp, borderColor, CircleShape)
        ) {
            if (iconVector != null) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = labelText,
                    tint = IosSettingsColors.label,
                    modifier = Modifier.size(size * 0.7f)
                )
            }
        }

        if (showText) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = labelText,
                style = textStyle,
                color = animatedColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

