package com.example.premium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.effects.AvatarFrameCatalog
import com.example.effects.AvatarFrameView
import com.example.premium.domain.model.LevelTierInfo
import com.example.ui.theme.PanalinkSkin
import com.example.ui.settings.ios.IosSettingsColors

/**
 * Cosméticos Premium 2.0 — marcos de avatar desbloqueables por nivel.
 *
 * Cada marco del catálogo se muestra con su preview real (AvatarFrameView).
 * Un marco está:
 *  - poseído (owned en my_cosmetics) -> se puede equipar.
 *  - equipado (equipped == code)      -> marcado en amarillo.
 *  - bloqueado                         -> no se alcanzó el nivel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumCosmeticsScreen(
    onBack: () -> Unit,
    viewModel: PremiumViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadAll()
        viewModel.loadCosmetics()
    }
    val state by viewModel.state.collectAsState()
    val cosmetics by viewModel.cosmetics.collectAsState()
    val equipping by viewModel.equippingCode.collectAsState()

    val equipped = cosmetics?.equipped
    val ownedCodes = remember(cosmetics) {
        cosmetics?.owned?.mapTo(mutableSetOf()) { it.cosmeticCode } ?: emptySet()
    }

    // Tier de nivel -> código cosmético visible en la galería.
    val gallery: List<CosmeticEntry> = remember(state.levelInfo) {
        val levels: List<LevelTierInfo> = state.levelInfo.levelTiers
        buildList {
            add(CosmeticEntry(code = "none", label = "Sin marco", levelRequired = 0))
            levels.forEach { tier ->
                val code = tier.cosmeticCode ?: return@forEach
                if (AvatarFrameCatalog.byCode(code) != null) {
                    add(CosmeticEntry(code = code, label = tier.title, levelRequired = tier.level))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("👑 Mi Colección", fontWeight = FontWeight.Bold, color = PanalinkSkin.TitleCream) },
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(span = { GridItemSpan(3) }) {
                CollectionBanner(level = state.levelInfo.level, nextLevel = state.levelInfo.next?.level)
            }
            items(gallery, key = { it.code }) { entry ->
                CosmeticTile(
                    entry = entry,
                    equipped = equipped == entry.code,
                    owned = entry.code == "none" || ownedCodes.contains(entry.code),
                    equipping = equipping == entry.code,
                    onEquip = { viewModel.equipCosmetic(entry.code) }
                )
            }
        }
    }
}

private data class CosmeticEntry(
    val code: String,
    val label: String,
    val levelRequired: Int
)

@Composable
private fun CollectionBanner(level: Int, nextLevel: Int?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(IosSettingsColors.cell, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text("⭐ Tu nivel: $level", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            nextLevel?.let { "Sube al nivel $it para desbloquear el próximo marco." }
                ?: "¡Alcanzaste todos los marcos!",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun CosmeticTile(
    entry: CosmeticEntry,
    equipped: Boolean,
    owned: Boolean,
    equipping: Boolean,
    onEquip: () -> Unit
) {
    val spec = AvatarFrameCatalog.byCode(entry.code)
    val borderColor = when {
        equipped -> PanalinkSkin.Gold
        owned -> Color.White.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.06f)
    }

    Column(
        modifier = Modifier
            .aspectRatio(1.1f)
            .background(if (owned) IosSettingsColors.cell else Color(0xFF131C26), RoundedCornerShape(14.dp))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(enabled = owned && !equipping) { onEquip() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            // Avatar neutro debajo + marco encima (patrón de las salas de voz).
            Box(
                modifier = Modifier
                    .size(if (spec != null) 34.dp else 44.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(Color(0xFF475569), IosSettingsColors.groupBackground)
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) { Text("🙂", fontSize = 12.sp) }
            if (spec != null) {
                AvatarFrameView(code = entry.code, avatarSize = 34.dp, animated = false)
            }
            if (!owned) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0x880F161F), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("🔒", fontSize = 18.sp) }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            entry.label,
            color = if (owned) Color.White else Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            when {
                !owned -> "Nv ${entry.levelRequired}"
                equipped -> "✓ Equipado"
                equipping -> "Equipando..."
                else -> "Tocar para equipar"
            },
            color = if (equipped) PanalinkSkin.Gold else Color.White.copy(alpha = 0.5f),
            fontSize = 9.sp
        )
    }
}