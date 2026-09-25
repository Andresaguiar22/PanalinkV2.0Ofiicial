package com.example.premium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.premium.domain.model.LevelTierInfo
import com.example.premium.domain.model.WalletTransaction
import com.example.ui.theme.PanalinkSkin
import com.example.ui.settings.ios.IosSettingsColors

/**
 * Wallet Premium 2.0 — economía del usuario.
 *
 * Muestra:
 *  - Saldo completo (🪙 💎 🎟️ ⭐ nivel)
 *  - Progreso de nivel y próximas recompensas
 *  - Historial: entradas/salidas con origen y balance
 *  - Accesos: tienda y canje de diamantes
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumWalletScreen(
    onBack: () -> Unit,
    onOpenShop: () -> Unit,
    onOpenCosmetics: () -> Unit = {},
    viewModel: PremiumViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadWallet() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("👛 Mi Wallet", fontWeight = FontWeight.Bold, color = PanalinkSkin.TitleCream) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { WalletBalanceCard(state.wallet) }
            item { LevelProgressCard(levelInfo = state.levelInfo) }

            // Acciones rápidas
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionChip("🛍️ Tienda", enabled = true, onClick = onOpenShop, modifier = Modifier.weight(1f))
                    ActionChip("👑 Colección", enabled = true, onClick = onOpenCosmetics, modifier = Modifier.weight(1f))
                }
            }

            item { SectionTitle("📜 Historial") }
            if (state.walletHistory.transactions.isEmpty()) {
                item {
                    Text(
                        "Aún no hay movimientos. Comprá funciones o ganá recompensas para ver tu historial.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(state.walletHistory.transactions) { tx ->
                    TransactionRow(tx)
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun WalletBalanceCard(wallet: com.example.premium.domain.model.WalletBalance) {
    val gold = PanalinkSkin.Gold
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(Color(0xFF2A2320), Color(0xFF171A24))),
                RoundedCornerShape(24.dp)
            )
            .border(1.dp, gold.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Column {
            Text("SALDO", color = gold.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BalanceItem("🪙", wallet.coins)
                BalanceItem("💎", wallet.diamonds)
                BalanceItem("🎟️", wallet.tickets)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⭐ Nivel ${wallet.level}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text("💰 ${wallet.xp} XP", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun BalanceItem(emoji: String, amount: Int) {
    Column {
        Text(emoji, fontSize = 22.sp)
        Text("$amount", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    }
}

/** Barra de progreso hacia el próximo tier + próximas recompensas. */
@Composable
private fun LevelProgressCard(levelInfo: com.example.premium.domain.model.LevelInfo) {
    val next = levelInfo.next
    val current = levelInfo.current
    val denominator = (levelInfo.xpForNext - levelInfo.xpPrev).coerceAtLeast(1)
    val progress = ((levelInfo.xp - levelInfo.xpPrev).toFloat() / denominator.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1B2530), RoundedCornerShape(16.dp))
            .border(1.dp, PanalinkSkin.Gold.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${current?.emoji ?: "⭐"} ${current?.title ?: "Pana"}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.weight(1f))
            Text("Nv ${levelInfo.level}", color = PanalinkSkin.GoldBright, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Barra de progreso
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(8.dp)
                    .background(PanalinkSkin.GoldBright, RoundedCornerShape(4.dp))
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (next != null) {
                "Faltan ${levelInfo.xpForNext - levelInfo.xp} XP para ${next.emoji} ${next.title} (Nv ${next.level})"
            } else {
                "¡Nivel máximo alcanzado!"
            },
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp
        )

        // Próximas recompensas (tiers futuros)
        val future = levelInfo.levelTiers.filter { it.level > levelInfo.level }.take(3)
        if (future.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text("🎁 Recompensas por nivel", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            future.forEach { tier ->
                Text(
                    "Nv ${tier.level} · ${tier.emoji} ${tier.title}" +
                        (if (tier.rewardAmount > 0) " · +${tier.rewardAmount} ${tier.rewardCurrency.orEmpty()}" else ""),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                if (enabled) Color(0xFF1B2432) else Color(0xFF16202C),
                RoundedCornerShape(14.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
}

/** Fila del historial (entrada/salida del ledger). */
@Composable
private fun TransactionRow(tx: WalletTransaction) {
    val isPositive = tx.amount > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF16202C), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    if (isPositive) Color(0xFF1E4D2B) else Color(0xFF4D1E1E),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(if (isPositive) "▲" else "▼", color = if (isPositive) Color(0xFF5CE57E) else Color(0xFFE57E7E), fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tx.description ?: kindLabel(tx.kind),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                kindLabel(tx.kind),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${if (isPositive) "+" else ""}$tx.amount ${currencyEmoji(tx.currency)}",
                color = if (isPositive) Color(0xFF5CE57E) else Color(0xFFE57979),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text("Saldo ${tx.balanceAfter}", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp)
        }
    }
}

private fun kindLabel(kind: String): String = when (kind) {
    "premium_buy" -> "Compra de beneficio Premium"
    "premium_renew" -> "Renovación Premium"
    "gift_sent" -> "Regalo enviado"
    "gift_received" -> "Regalo recibido"
    "diamond_exchange" -> "Canje de diamantes"
    "event_reward" -> "Recompensa de evento"
    "reward_claim" -> "Recompensa"
    "daily_reward" -> "Recompensa diaria"
    "mission_reward" -> "Misión completada"
    "streak_reward" -> "Racha"
    "ticket_redeem" -> "Canje de ticket"
    "admin_grant" -> "Ajuste de soporte"
    "promotion_reward" -> "Bono promocional"
    "mystery_box" -> "Caja misteriosa"
    "refund" -> "Reembolso"
    "adjustment" -> "Ajuste"
    else -> kind
}

private fun currencyEmoji(c: String): String = when (c) {
    "diamonds" -> "💎"
    "tickets" -> "🎟️"
    "xp" -> "⭐"
    else -> "🪙"
}