package com.example.feature.chat.ui.background

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun ChatBubblePaletteDialog(
    visible: Boolean,
    currentPalette: ChatBubblePalette,
    onDismiss: () -> Unit,
    onSelect: (ChatBubblePalette) -> Unit,
) {
    if (!visible) return

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2C34)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Color de burbujas",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                ChatBubblePalette.entries.forEach { palette ->
                    val selected = palette == currentPalette
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(palette) }
                            .background(if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent)
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = if (selected) Color(0xFF25D366) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(palette.colors)
                                )
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            palette.displayName,
                            color = Color.White,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Text("●", color = Color(0xFF25D366), fontSize = 18.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
                ) {
                    Text("Cerrar", color = Color(0xFF25D366))
                }
            }
        }
    }
}