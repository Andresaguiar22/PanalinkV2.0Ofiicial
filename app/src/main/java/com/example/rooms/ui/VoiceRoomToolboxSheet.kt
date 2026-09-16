package com.example.rooms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.effects.AvatarFrameCatalog
import com.example.effects.AvatarFrameView

/**
 * Toolbox del dueño de la sala de voz (estilo StarMaker).
 * Deja elegir la ENTRADA (efecto a pantalla completa cuando alguien entra)
 * y el COLGANTE (adorno que rodea el avatar del sillón).
 * La selección se persiste en Supabase vía RPCs y llega a todos los miembros
 * por Realtime.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRoomToolboxSheet(
    currentEntrance: String,
    currentPendant: String,
    onSelectEntrance: (String) -> Unit,
    onSelectPendant: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableStateOf(0) } // 0 = Entradas, 1 = Colgantes

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Cabecera
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎛️ Caja de herramientas",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { onDismiss() }
                )
            }
            Spacer(modifier = Modifier.height(14.dp))

            // Pestañas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1E293B))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("Entradas" to 0, "Colgantes" to 1).forEach { (label, index) ->
                    val selected = tab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) Color(0xFF334155) else Color.Transparent)
                            .clickable { tab = index }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (index == 0) "✨ Entradas" else "💍 Colgantes",
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (tab == 0) {
                // ── Entradas ──
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(330.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(VoiceRoomToolboxCatalog.entrances) { spec ->
                        val selected = spec.code == currentEntrance
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (selected) Color(0xFF3B4758)
                                    else Color(0xFF1E293B),
                                    RoundedCornerShape(16.dp)
                                )
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) Color(0xFF4FE7EA) else Color(0x22FFFFFF),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { onSelectEntrance(spec.code) }
                                .padding(vertical = 12.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Color(spec.gradient.first),
                                                Color(spec.gradient.second)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = spec.emoji, fontSize = 26.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = spec.label,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Seleccionada",
                                    tint = Color(0xFF4FE7EA),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // ── Colgantes ──
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(330.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(VoiceRoomToolboxCatalog.pendants) { spec ->
                        val selected = spec.code == currentPendant
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (selected) Color(0xFF3B4758)
                                    else Color(0xFF1E293B),
                                    RoundedCornerShape(16.dp)
                                )
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) Color(0xFF4FE7EA) else Color(0x22FFFFFF),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { onSelectPendant(spec.code) }
                                .padding(vertical = 12.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Vista previa real: el mismo marco vectorial que se pinta
                            // alrededor del avatar en el sillón, con un avatar simulado.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (spec.code == "none") {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF0B1220), CircleShape)
                                            .border(1.dp, Color(0x33FFFFFF), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "—",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 14.sp
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(pendantPreviewAvatar(spec.code))
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(Color(0xFF475569), Color(0xFF0F172A))
                                                ),
                                                CircleShape
                                            )
                                    )
                                    AvatarFrameView(
                                        code = spec.code,
                                        avatarSize = pendantPreviewAvatar(spec.code),
                                        animated = false
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = spec.label,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 10.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                            if (spec.rarityLabel.isNotBlank()) {
                                Text(
                                    text = spec.rarityLabel,
                                    color = Color(spec.ringColor).copy(alpha = 0.95f),
                                    fontSize = 8.sp,
                                    maxLines = 1
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Seleccionado",
                                    tint = Color(0xFF4FE7EA),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Selector del colgante PERSONAL del usuario (no de la sala). Se guarda en su
 * perfil ([VoiceRoomViewModel.setMyPendant]) y se replica en todas las salas a
 * las que entre: el marco viaja con la persona, no con el dueño de la sala.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRoomMyPendantSheet(
    myPendantCode: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Cabecera
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💍 Mi colgante",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { onDismiss() }
                )
            }
            Text(
                text = "El colgante viaja contigo: se verá sobre tu avatar en cualquier sala.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(VoiceRoomToolboxCatalog.pendants) { spec ->
                    val selected = spec.code == myPendantCode
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (selected) Color(0xFF3B4758)
                                else Color(0xFF1E293B),
                                RoundedCornerShape(16.dp)
                            )
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) Color(0xFF4FE7EA) else Color(0x22FFFFFF),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onSelect(spec.code) }
                            .padding(vertical = 12.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (spec.code == "none") {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0B1220), CircleShape)
                                        .border(1.dp, Color(0x33FFFFFF), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "—",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(pendantPreviewAvatar(spec.code))
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF475569), Color(0xFF0F172A))
                                            ),
                                            CircleShape
                                        )
                                )
                                AvatarFrameView(
                                    code = spec.code,
                                    avatarSize = pendantPreviewAvatar(spec.code),
                                    animated = false
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = spec.label,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                        if (spec.rarityLabel.isNotBlank()) {
                            Text(
                                text = spec.rarityLabel,
                                color = Color(spec.ringColor).copy(alpha = 0.95f),
                                fontSize = 8.sp,
                                maxLines = 1
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Seleccionado",
                                tint = Color(0xFF4FE7EA),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Diametro del avatar simulado en la celda del selector de colgantes.
 *
 * Los colgantes vectoriales se dibujan a 36dp de avatar (el marco ocupa ~62dp).
 * Los raster traen el diseno completo (aro + banner) ya embebido, asi que se
 * escalan para que el marco entero quepa en la celda en vez de desbordarla.
 */
private fun pendantPreviewAvatar(code: String): Dp {
    val spec = AvatarFrameCatalog.byCode(code)
    return if (spec != null && spec.bitmapRes != 0) {
        58.dp / spec.overflowScale
    } else {
        36.dp
    }
}
