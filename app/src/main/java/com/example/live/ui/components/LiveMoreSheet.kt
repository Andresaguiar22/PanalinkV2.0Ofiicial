package com.example.live.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LIVE_REPORT_REASONS = listOf(
    "Spam o publicidad",
    "Contenido inapropiado",
    "Violencia o peligro",
    "Suplantación de identidad",
    "Otro motivo"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMoreSheet(
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onReport: (String) -> Unit
) {
    var showReasons by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1B1B1F),
        contentColor = Color.White
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("Más opciones", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(10.dp))

            MoreAction(text = "Compartir directo", onClick = onShare)
            MoreAction(text = "Copiar enlace", onClick = onCopyLink)
            MoreAction(
                text = if (showReasons) "Reportar directo ▲" else "Reportar directo ▼",
                onClick = { showReasons = !showReasons }
            )

            if (showReasons) {
                LIVE_REPORT_REASONS.forEach { reason ->
                    Text(
                        text = reason,
                        color = Color(0xFFEF9A9A),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onReport(reason) }
                            .padding(vertical = 10.dp, horizontal = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancelar", color = Color.White)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun MoreAction(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, color = Color.White, fontSize = 14.sp)
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
}
