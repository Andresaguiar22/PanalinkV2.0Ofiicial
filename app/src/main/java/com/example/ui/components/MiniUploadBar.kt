package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Barra de subida minima, una por seccion. Ocupa lo menos posible: una linea
 * con etiqueta + % + X, y una pista de progreso fina debajo.
 */
@Composable
fun MiniUploadBar(
    label: String,
    percent: Int,
    onCancel: () -> Unit = {},
    onRetry: (() -> Unit)? = null,
    onDiscard: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF00FF85)
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "$label ${percent.coerceIn(0, 100)}%",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
            if (onRetry != null) {
                Text(
                    text = "↻",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 8.dp, vertical =  2.dp)
                )
            }
            Text(
                text = "✕",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier
                    .clickable(onClick = { onDiscard?.invoke() ?: onCancel() })
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
            }
        Spacer(Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { (percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp)),
            color = color,
            trackColor = Color.White.copy(alpha = 0.12f)
        )
    }
}
