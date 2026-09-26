package com.example.ui.components.chat.bubble

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Refresh
import com.example.util.DeliveryState
import com.example.ui.settings.ios.IosSettingsColors

@Composable
fun MessageStatusIndicator(
    formattedTime: String,
    deliveryState: DeliveryState,
    isMe: Boolean,
    isEdited: Boolean = false,
    isFavorited: Boolean = false,
    isPinned: Boolean = false,
    textColor: Color = IosSettingsColors.secondaryLabel,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isEdited) {
            Text(
                text = "(editado) ",
                color = textColor,
                fontSize = 9.sp
            )
        }
        if (isFavorited) {
            Text(
                text = "⭐ ",
                fontSize = 9.sp
            )
        }
        if (isPinned) {
            Text(
                text = "📌 ",
                fontSize = 9.sp
            )
        }
        Text(
            text = formattedTime,
            color = textColor.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal
        )

        if (isMe) {
            Spacer(modifier = Modifier.width(4.dp))
            AnimatedContent(
                targetState = deliveryState,
                transitionSpec = {
                    (scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn()) togetherWith fadeOut()
                },
                label = "statusTickAnimation"
            ) { targetStatus ->
                when (targetStatus) {
                    DeliveryState.SENDING -> {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Rounded.AccessTime,
                            contentDescription = "Enviando",
                            tint = textColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    DeliveryState.OFFLINE_PENDING -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = "Pendiente (Sin conexión)",
                                tint = IosSettingsColors.yellow,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    DeliveryState.FAILED -> {
                        if (onRetry != null) {
                            androidx.compose.material3.IconButton(
                                onClick = onRetry,
                                modifier = Modifier.size(22.dp)
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Reintentar envío",
                                    tint = IosSettingsColors.red,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Rounded.Error,
                                contentDescription = "Error",
                                tint = IosSettingsColors.red,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    DeliveryState.SENT -> {
                        // Un círculo pintado (gris) = enviado al servidor.
                        StatusCircles(
                            count = 1,
                            color = Color(0xFF9CA3AF),
                            offsetStagger = false
                        )
                    }
                    DeliveryState.DELIVERED -> {
                        // Dos círculos pintados (naranja) = entregado al otro dispositivo.
                        StatusCircles(
                            count = 2,
                            color = Color(0xFFF59E0B),
                            offsetStagger = true
                        )
                    }
                    DeliveryState.READ -> {
                        // Tres círculos pintados (verde) = leído por el destinatario.
                        StatusCircles(
                            count = 3,
                            color = Color(0xFF22C55E),
                            offsetStagger = true
                        )
                    }
                    else -> {
                        // UNKNOWN -> Do not show tick
                    }
                }
            }
        }
    }
}

/**
 * Círculos de estado del mensaje (estilo WhatsApp/iOS):
 *  - count 1 → enviado (gris)
 *  - count 2 → entregado (naranja), con el segundo círculo ligeramente
 *    desplazado hacia la derecha para leerlo como "dos"
 *  - count 3 → leído (verde), tres círculos en abanico.
 *
 * El offset (stagger) hace que los círculos no queden apilados exactamente iguales.
 */
@Composable
private fun StatusCircles(count: Int, color: Color, offsetStagger: Boolean) {
    val diameter = 12.dp
    val gap = 3.dp
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.height(16.dp).width(gap * (count - 1) + diameter)
    ) {
        for (i in 0 until count) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .size(diameter)
                    .align(Alignment.CenterStart)
                    .offset(x = gap * i)
            ) {
                drawCircle(color = color, radius = size.minDimension / 2f)
            }
        }
    }
}
