package com.example.premium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.premium.domain.model.Entitlement
import com.example.premium.domain.model.PremiumProduct
import com.example.ui.settings.ios.IosSettingsColors
import com.example.ui.theme.PanalinkSkin

/**
 * Tienda Premium: comprar funciones con monedas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumShopScreen(
    onBack: () -> Unit,
    viewModel: PremiumViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val buyingCode by viewModel.buyingCode.collectAsState()
    val message by viewModel.message.collectAsState()

    // La tienda puede abrirse directamente desde cualquier gate; por eso
    // debe iniciar su propia carga en lugar de depender de PremiumHomeScreen.
    LaunchedEffect(Unit) { viewModel.loadShop() }

    // Canje de diamantes -> monedas (visible si el usuario tiene diamantes).
    val diamonds = state.wallet.diamonds

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2600)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("🛍️ Tienda Premium", fontWeight = FontWeight.Bold, color = PanalinkSkin.TitleCream) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = IosSettingsColors.label)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(0xFF121A24))
            )
        },
        containerColor = Color(0xFF121A24)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Balance bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(PanalinkSkin.GoldDeep.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🪙", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Saldo disponible", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Spacer(modifier = Modifier.weight(1f))
                Text("${state.wallet.coins} 🪙", color = PanalinkSkin.TitleCream, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }

            // Promociones activas (precios descontados)
            if (state.promotions.isNotEmpty()) {
                state.promotions.take(3).forEach { p ->
                    com.example.premium.ui.PremiumPromotionChip(p)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.products) { product ->
                    ProductCard(
                        product = product,
                        buying = buyingCode == product.code,
                        hasActive = state.entitlements.any { it.featureKey == product.featureKey && it.isActive },
                        activeEntitlement = state.entitlements.firstOrNull { it.featureKey == product.featureKey && it.isActive },
                        onBuy = { viewModel.buyProduct(product.code) }
                    )
                }

                // Canje de diamantes por monedas (economía viva: eventos -> 💎 -> 🪙).
                if (diamonds > 0) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF152038), RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0xFF5B7BE8).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                .clickable { viewModel.exchangeDiamonds() }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("💎", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Canjear diamantes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "$diamonds 💎 disponibles → monedas 🪙 (100 🪙 c/u)",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp
                                )
                            }
                            Text("Canjear", color = Color(0xFF5B7BE8), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductCard(
    product: PremiumProduct,
    buying: Boolean,
    hasActive: Boolean,
    activeEntitlement: Entitlement?,
    onBuy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF16202C), RoundedCornerShape(16.dp))
            .border(1.dp, PanalinkSkin.Gold.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(product.emoji, fontSize = 26.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "${product.durationDays} días",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
            if (hasActive && activeEntitlement != null) {
                Text(
                    "✓ Activo · ${activeEntitlement.daysLeft}d",
                    color = PanalinkSkin.GoldBright,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        product.description?.let { desc ->
            Text(
                desc,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onBuy,
            enabled = !buying,
            colors = ButtonDefaults.buttonColors(
                containerColor = PanalinkSkin.GoldDeep,
                contentColor = Color.White,
                disabledContainerColor = PanalinkSkin.GoldDeep.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (buying) {
                Text("Comprando...", fontWeight = FontWeight.Bold)
            } else if (product.promoDiscountPercent > 0 && product.originalPriceCoins != null && product.originalPriceCoins > product.priceCoins) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${product.originalPriceCoins} 🪙",
                        color = Color.White.copy(alpha = 0.5f),
                        textDecoration = TextDecoration.LineThrough,
                        fontSize = 12.sp
                    )
                    Text(
                        "${product.priceCoins} 🪙 · -${product.promoDiscountPercent}%",
                        fontWeight = FontWeight.Bold,
                        color = PanalinkSkin.GoldBright
                    )
                }
            } else {
                Text("Comprar por ${product.priceCoins} 🪙", fontWeight = FontWeight.Bold)
            }
        }
    }
}