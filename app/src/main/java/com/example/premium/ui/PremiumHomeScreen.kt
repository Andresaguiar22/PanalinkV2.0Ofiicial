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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.settings.ios.IosSettingsColors
import com.example.ui.theme.PanalinkSkin

/**
 * Centro Premium 2.0 — la puerta de entrada a toda la economía.
 *
 * Muestra:
 *  - Saldo completo (🪙💎🎟️⭐ nivel)
 *  - Beneficios activos con días restantes
 *  - Oferta/promoción activa (banner)
 *  - Evento temporal activo (multiplicador)
 *  - Recompensa diaria + racha
 *  - Misiones
 *  - Acceso al Shop (productos)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumHomeScreen(
    onBack: () -> Unit,
    onOpenShop: () -> Unit,
    onOpenWallet: () -> Unit = {},
    viewModel: PremiumViewModel = viewModel()
) {
    LaunchedEffect(Unit) { viewModel.loadAll() }
    val state by viewModel.state.collectAsState()
    val buyingCode by viewModel.buyingCode.collectAsState()
    val claimingReward by viewModel.claimingReward.collectAsState()
    val message by viewModel.message.collectAsState()

    // Snackbar-ish toast para mensajes.
    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2600)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("💎 PanaLink Plus", fontWeight = FontWeight.Bold, color = PanalinkSkin.TitleCream) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = IosSettingsColors.label)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = IosSettingsColors.groupBackground)
            )
        },
        containerColor = IosSettingsColors.groupBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Saldo principal (card dorada) — tocar abre el Wallet.
            WalletHeroCard(wallet = state.wallet, onClick = onOpenWallet)

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { SectionTitle("🔥 Beneficios activos") }
                if (state.entitlements.isEmpty()) {
                    item {
                        EmptyEntitlementsCard(onOpenShop = onOpenShop)
                    }
                } else {
                    items(state.entitlements) { e ->
                        EntitlementRow(e)
                    }
                }

                // Promociones activas
                if (state.promotions.isNotEmpty()) {
                    item { SectionTitle("⚡ Ofertas") }
                    items(state.promotions) { p ->
                        PromotionCard(p)
                    }
                }

                // Eventos activos
                if (state.events.isNotEmpty()) {
                    item { SectionTitle("🎉 Eventos") }
                    items(state.events) { ev ->
                        EventCard(ev)
                    }
                }

                item { SectionTitle("🎁 Recompensa diaria") }
                item {
                    DailyRewardCard(
                        status = state.dailyReward,
                        claiming = claimingReward,
                        onClaim = { viewModel.claimDaily() }
                    )
                }

                item { SectionTitle("🎯 Misiones") }
                items(state.missions.take(5)) { m ->
                    MissionRow(m)
                }
                if (state.missions.any { it.completed && !it.claimed }) {
                    item {
                        Button(
                            onClick = { viewModel.claimMissions() },
                            colors = ButtonDefaults.buttonColors(containerColor = PanalinkSkin.GoldDeep),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🎯 Reclamar recompensas de misiones", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Notificaciones Premium (in-app)
                if (state.notifications.isNotEmpty()) {
                    item { SectionTitle("📬 Centro de mensajes") }
                    items(state.notifications.take(8)) { n ->
                        PremiumNotifRow(
                            notification = n,
                            onRead = { viewModel.markNotificationRead(n.id) }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    // Toast
    androidx.compose.material3.SnackbarHost(hostState = remember { SnackbarHostState() })
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = PanalinkSkin.TitleCream,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp
    )
}

/** Card principal con saldo completo. */
@Composable
fun WalletHeroCard(
    wallet: com.example.premium.domain.model.WalletBalance,
    onClick: () -> Unit = {}
) {
    val gold = PanalinkSkin.Gold
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                Spacer(modifier = Modifier.height(0.dp))
                Spacer(modifier = Modifier.weight(1f))
                Text("🪙 gana con recompensas, misiones y eventos", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
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

/** Fila de entitlement activo. */
@Composable
fun EntitlementRow(e: com.example.premium.domain.model.Entitlement) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1B2530), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(e.emoji, fontSize = 22.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(e.name, color = PanalinkSkin.TitleCream, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Vence en ${e.daysLeft} días", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        LinearProgressIndicator(
            progress = { (e.daysLeft / 30f).coerceIn(0f, 1f) },
            modifier = Modifier.width(80.dp),
            color = PanalinkSkin.Gold,
            trackColor = Color.White.copy(alpha = 0.1f)
        )
    }
}

/** Estado sin beneficios. */
@Composable
private fun EmptyEntitlementsCard(onOpenShop: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1B2530), RoundedCornerShape(14.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🔒 Aún no tienes beneficios activos", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onOpenShop,
            colors = ButtonDefaults.buttonColors(containerColor = PanalinkSkin.GoldDeep),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("🛍️ Ir a la tienda", color = Color.White)
        }
    }
}

/** Card de promoción. */
@Composable
fun PromotionCard(p: com.example.premium.domain.model.PremiumPromotion) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF131B26), RoundedCornerShape(14.dp))
            .border(1.dp, PanalinkSkin.Gold.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(p.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            p.subtitle?.let { Text(it, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp) }
            if (p.discountPercent > 0) {
                Text("🔥 -${p.discountPercent}% OFF", color = PanalinkSkin.GoldBright, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        if (p.priceCoins != null) {
            Text("${p.priceCoins} 🪙", color = PanalinkSkin.GoldBright, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

/** Card de evento. */
@Composable
fun EventCard(ev: com.example.premium.domain.model.PremiumEvent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1B1428), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(ev.emoji ?: "🎉", fontSize = 22.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(ev.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                "🪙 x${ev.multiplierCoins} · ⭐ x${ev.multiplierXp}",
                color = PanalinkSkin.GoldBright,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

/** Card de recompensa diaria con racha. */
@Composable
fun DailyRewardCard(
    status: com.example.premium.domain.model.DailyRewardStatus,
    claiming: Boolean,
    onClaim: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF16202C), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🎁", fontSize = 26.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Racha: ${status.streak} días 🔥", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                if (status.claimedToday) "Volviste mañana por tu recompensa ✨" else "¡Reclama tu recompensa diaria!",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
        Button(
            onClick = onClaim,
            enabled = !status.claimedToday && !claiming,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (status.claimedToday) Color(0xFF2A3040) else PanalinkSkin.GoldDeep,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF2A3040),
                disabledContentColor = Color.White.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (claiming) "..." else if (status.claimedToday) "✅" else "RECLAMAR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Fila de misión. */
@Composable
fun MissionRow(m: com.example.premium.domain.model.Mission) {
    val done = m.completed && m.claimed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF151E28), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                m.title,
                color = if (done) Color.White.copy(alpha = 0.5f) else Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
            LinearProgressIndicator(
                progress = { (m.progress.toFloat() / m.target).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(0.8f).padding(top = 4.dp).height(4.dp),
                color = if (done) IosSettingsColors.green else PanalinkSkin.Gold,
                trackColor = Color.White.copy(alpha = 0.1f)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "${m.progress}/${m.target}",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("+${m.rewardAmount} ${currencyEmoji(m.rewardCurrency)}", color = PanalinkSkin.GoldBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

private fun currencyEmoji(c: String): String = when (c) {
    "diamonds" -> "💎"
    "tickets" -> "🎟️"
    "xp" -> "⭐"
    else -> "🪙"
}

/** Fila de notificación Premium en el centro de mensajes. */
@Composable
private fun PremiumNotifRow(
    notification: com.example.premium.domain.model.PremiumNotification,
    onRead: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (notification.isRead) IosSettingsColors.groupBackground else Color(0xFF1B2432),
                RoundedCornerShape(12.dp)
            )
            .clickable(enabled = !notification.isRead, onClick = onRead)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(categoryEmoji(notification.category), fontSize = 18.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                notification.title,
                color = if (notification.isRead) Color.White.copy(alpha = 0.6f) else Color.White,
                fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Bold,
                fontSize = 13.sp
            )
            notification.body?.let {
                Text(
                    it,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        if (!notification.isRead) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(Color(0xFFE8B23A), CircleShape)
            )
        }
    }
}

private fun categoryEmoji(category: String): String = when (category) {
    "PREMIUM" -> "💎"
    "SOCIAL" -> "👥"
    "LIVE" -> "🔴"
    "REWARD" -> "🎁"
    "COINS" -> "🪙"
    "PROMOTION" -> "🔥"
    "SYSTEM" -> "⚙️"
    "SECURITY" -> "🔒"
    else -> "📬"
}