package com.example.premium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PanalinkPalette
import com.example.ui.theme.PanalinkSkin

/**
 * Badge "Premium activo" que se muestra junto a usuarios con entitlements.
 * Cuando una feature expire, el badge desaparece automáticamente.
 */
@Composable
fun PremiumBadge(
    modifier: Modifier = Modifier,
    text: String = "💎 PREMIUM"
) {
    Box(
        modifier = modifier
            .background(PanalinkSkin.Gold.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = PanalinkSkin.GoldBright,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 10.sp
        )
    }
}