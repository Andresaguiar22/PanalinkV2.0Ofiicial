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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.premium.domain.model.Entitlement
import com.example.premium.domain.model.PremiumProduct
import com.example.ui.theme.PanalinkPalette
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = PanalinkPalette.textPrimary)
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
    val canAfford = buying
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
            Text(
                if (buying) "Comprando..." else "Comprar por ${product.priceCoins} 🪙",
                fontWeight = FontWeight.Bold
            )
        }
    }
}