package com.example.premium.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import com.example.ui.settings.ios.IosSettingsColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.premium.domain.PremiumEvent
import com.example.premium.domain.PremiumEventBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance

/**
 * Overlay transitorio de recompensas Premium 2.0.
 *
 * Reacciona a los eventos del bus:
 *  - [PremiumEvent.RewardClaimed]: muestra recompensa diaria con racha.
 *  - [PremiumEvent.MissionCompleted]: muestra misión completada + premio.
 *  - [PremiumEvent.LevelUp]: muestra subida de nivel.
 *  - [PremiumEvent.CoinsEarned]: muestra monedas recibidas.
 *
 * Es 100% aditivo: no bloquea la interacción y desaparece solo.
 */
@Composable
fun PremiumRewardsOverlay(
    modifier: Modifier = Modifier
) {
    var active by remember { androidx.compose.runtime.mutableStateOf<PremiumEvent?>(null) }
    val scale = remember { Animatable(0.6f) }
    val alpha = remember { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(Unit) {
        PremiumEventBus.events
            .filterIsInstance<PremiumEvent>()
            .collect { event ->
                when (event) {
                    is PremiumEvent.RewardClaimed,
                    is PremiumEvent.MissionCompleted,
                    is PremiumEvent.LevelUp,
                    is PremiumEvent.CoinsEarned -> {
                        active = event
                        scale.snapTo(0.6f)
                        alpha.snapTo(0f)
                        scale.animateTo(1f, animationSpec = tween(350))
                        alpha.animateTo(1f, animationSpec = tween(250))
                        delay(2600)
                        active = null
                    }
                    else -> Unit
                }
            }
    }

    val current = active ?: return
    if (alpha.value <= 0.01f) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .scale(scale.value),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp)
                .background(IosSettingsColors.mediaScrim, RoundedCornerShape(20.dp))
                .border(1.dp, IosSettingsColors.separator, RoundedCornerShape(20.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = when (current) {
                    is PremiumEvent.RewardClaimed -> "🎁 Recompensa diaria"
                    is PremiumEvent.MissionCompleted -> "✅ Misión cumplida"
                    is PremiumEvent.LevelUp -> "🎉 ¡Nivel ${current.newLevel}!"
                    is PremiumEvent.CoinsEarned -> "🪙 +${current.amount} monedas"
                    else -> "⭐"
                },
                color = IosSettingsColors.label,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = when (current) {
                    is PremiumEvent.RewardClaimed ->
                        "${current.amount} ${currencyLabel(current.currency)} · racha de ${current.streak} días"
                    is PremiumEvent.MissionCompleted ->
                        "${current.title}"
                    is PremiumEvent.LevelUp ->
                        "Seguís acumulando beneficios premium"
                    is PremiumEvent.CoinsEarned ->
                        (current.note ?: (current.source))
                    else -> ""
                },
                color = IosSettingsColors.secondaryLabel,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun currencyLabel(currency: String): String = when (currency) {
    "coins" -> "monedas"
    "diamonds" -> "diamantes"
    "tickets" -> "tickets"
    else -> currency
}