package com.example.premium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.premium.domain.PremiumManager
import com.example.ui.settings.ios.IosSettingsColors
import com.example.ui.theme.PanalinkSkin

/**
 * Chip con el saldo de monedas. Visible en perfil y otros lugares.
 * Al tocarlo abre el centro Premium (monedas).
 */
@Composable
fun CoinChip(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val wallet by PremiumManager.wallet.collectAsState()

    Row(
        modifier = modifier
            .background(PanalinkSkin.GoldDeep.copy(alpha = 0.18f), RoundedCornerShape(50))
            .border(1.dp, PanalinkSkin.Gold.copy(alpha = 0.4f), RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "🪙", fontSize = 14.sp)
        Text(
            text = " ${wallet.coins}",
            color = PanalinkSkin.TitleCream,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp
        )
        Text(
            text = "  ·  💎 ${wallet.diamonds}",
            color = IosSettingsColors.secondaryLabel,
            fontSize = 12.sp
        )
    }
}

/** Chip compacto solo monedas (para barras). */
@Composable
fun MiniCoinChip(modifier: Modifier = Modifier) {
    val wallet by PremiumManager.wallet.collectAsState()
    Row(
        modifier = modifier
            .background(Color(0xFFFFFFFF).copy(alpha = 0.08f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🪙", fontSize = 12.sp)
        Text(
            " ${wallet.coins}",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}