package com.example.premium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.premium.domain.PremiumFeatures
import com.example.premium.domain.PremiumManager
import com.example.ui.settings.ios.IosSettingsColors
import com.example.ui.theme.PanalinkSkin

/**
 * Compuerta reutilizable de funciones premium.
 *
 * Si el usuario tiene la feature activa, muestra [content]. Si no, muestra
 * [lockedContent] (por defecto un panel "Función Premium" con botón para abrir
 * el shop). El resto de la app NO necesita saber nada de la economía.
 *
 * Uso:
 *   PremiumGate(feature = PremiumFeatures.CHAT) { ChatContent() }
 */
@Composable
fun PremiumGate(
    feature: String,
    modifier: Modifier = Modifier,
    onOpenPremium: (() -> Unit)? = null,
    locked: @Composable () -> Unit = {
        PremiumLockedPanel(
            feature = feature,
            onOpenPremium = onOpenPremium
        )
    },
    content: @Composable () -> Unit
) {
    val entitlements by PremiumManager.entitlements.collectAsState()
    val active = entitlements.any { it.featureKey == feature && it.isActive }

    if (active) {
        content()
    } else {
        locked()
    }
}

/** Panel por defecto cuando la feature está bloqueada. */
@Composable
fun PremiumLockedPanel(
    feature: String,
    onOpenPremium: (() -> Unit)? = null
) {
    val featureName = when (feature) {
        PremiumFeatures.CHAT -> "Chat Gold"
        PremiumFeatures.STORY -> "Story Gold"
        PremiumFeatures.LIVE -> "Live Gold"
        PremiumFeatures.WALL -> "Wall Gold"
        PremiumFeatures.VOICE -> "Voice Gold"
        PremiumFeatures.PANATV -> "Pana TV Gold"
        else -> "Premium"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .background(Color(0xFF1A1410), RoundedCornerShape(20.dp))
            .border(1.dp, PanalinkSkin.Gold.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = "💎", fontSize = 36.sp)
        Text(
            text = "$featureName",
            color = PanalinkSkin.TitleCream,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Text(
            text = "Esta es una función Premium. Actívala con monedas por días para desbloquear todos sus beneficios.",
            color = IosSettingsColors.secondaryLabel,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        if (onOpenPremium != null) {
            Button(
                onClick = onOpenPremium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PanalinkSkin.GoldDeep,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("🪙 Desbloquear $featureName", fontWeight = FontWeight.Bold)
            }
        }
    }
}