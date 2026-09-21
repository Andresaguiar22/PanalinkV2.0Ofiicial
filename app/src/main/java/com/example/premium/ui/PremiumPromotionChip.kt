package com.example.premium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.premium.domain.model.PremiumPromotion
import com.example.ui.theme.PanalinkSkin

/**
 * Chip de promoción activa (oferta con descuento). Se muestra en la tienda y
 * en el listado de ofertas. Aditivo: no altera la lógica de compra.
 */
@Composable
fun PremiumPromotionChip(
    promotion: PremiumPromotion,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1C1428), RoundedCornerShape(14.dp))
            .border(1.dp, PanalinkSkin.Gold.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(promotion.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            promotion.subtitle?.let {
                Text(it, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (promotion.discountPercent > 0) {
                Text(
                    "-${promotion.discountPercent}%",
                    color = PanalinkSkin.GoldBright,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }
            promotion.priceCoins?.let {
                Spacer(modifier = Modifier.width(8.dp))
                Text("$it 🪙", color = PanalinkSkin.GoldBright, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
        }
    }
}