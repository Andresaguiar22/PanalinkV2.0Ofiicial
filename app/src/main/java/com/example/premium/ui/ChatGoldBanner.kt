package com.example.premium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ui.settings.ios.IosSettingsColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.premium.domain.PremiumFeatures
import com.example.premium.domain.PremiumManager

/**
 * Banner delgado de Chat Gold que se muestra DEBAJO de la top bar del chat.
 *
 * Aditivo: la mensajería básica nunca se bloquea. Solo informa del estado
 * premium de la conversación y lleva al centro Premium si el usuario quiere
 * activar/renovar Chat Gold.
 */
@Composable
fun ChatGoldUpgradeBanner(
    onNavigateToPremium: () -> Unit
) {
    val entitlements by PremiumManager.entitlements.collectAsState()
    val active = entitlements.any { it.featureKey == PremiumFeatures.CHAT && it.isActive }
    val daysLeft = entitlements
        .firstOrNull { it.featureKey == PremiumFeatures.CHAT && it.isActive }
        ?.daysLeft

    if (active) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(IosSettingsColors.cellElevated)
                .border(1.dp, IosSettingsColors.separator, RoundedCornerShape(0.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (daysLeft != null)
                    "⭐ Chat Gold activo — $daysLeft día${if (daysLeft == 1) "" else "s"} restante${if (daysLeft == 1) "" else "s"}"
                else "⭐ Chat Gold activo",
                color = IosSettingsColors.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(IosSettingsColors.cellElevated)
                .clickable(onClick = onNavigateToPremium)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "💎 Chat Gold — mensajes premium y ventajas exclusivas. Tocá para activar.",
                color = IosSettingsColors.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}