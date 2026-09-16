package com.example.live.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.live.domain.model.LiveGift

private val QUANTITIES = listOf(1, 5, 10)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveGiftSheet(
    gifts: List<LiveGift>,
    balance: Int?,
    sending: Boolean,
    onDismiss: () -> Unit,
    onSend: (LiveGift, Int) -> Unit
) {
    var selected by remember { mutableStateOf<LiveGift?>(null) }
    var quantity by remember { mutableStateOf(1) }

    val current = selected
    val cost = (current?.coins ?: 0) * quantity
    val affordable = balance == null || cost <= balance

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1B1B1F),
        contentColor = Color.White
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Regalos", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF2A2A32)
                ) {
                    Text(
                        text = "🪙 ${balance ?: "—"}",
                        color = Color(0xFFFFD54F),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (gifts.isEmpty()) {
                Text(
                    text = "Cargando catálogo de regalos...",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(190.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(gifts, key = { it.code }) { gift ->
                        val isSelected = selected?.code == gift.code
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) Color(0xFF3A2A4D) else Color(0xFF26262E)
                                )
                                .clickable { selected = gift }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = gift.emoji, fontSize = 26.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = gift.name,
                                color = Color.White,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                            Text(
                                text = "🪙 ${gift.coins}",
                                color = Color(0xFFFFD54F),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cantidad", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                Spacer(modifier = Modifier.width(10.dp))
                QUANTITIES.forEach { q ->
                    val isSelected = quantity == q
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) Color(0xFFA73BFA) else Color(0xFF26262E),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clickable { quantity = q }
                    ) {
                        Text(
                            text = "x$q",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { current?.let { onSend(it, quantity) } },
                enabled = current != null && !sending && affordable,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFA73BFA),
                    disabledContainerColor = Color(0xFF3A3A44)
                )
            ) {
                Text(
                    text = when {
                        sending -> "Enviando..."
                        current == null -> "Elige un regalo"
                        !affordable -> "Saldo insuficiente"
                        else -> "Enviar ${current.emoji} x$quantity · 🪙 $cost"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
