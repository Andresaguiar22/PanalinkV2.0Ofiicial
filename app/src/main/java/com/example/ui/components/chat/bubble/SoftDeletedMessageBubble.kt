package com.example.ui.components.chat.bubble

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Message

@Composable
fun SoftDeletedMessageBubble(
    message: Message,
    isMe: Boolean,
    modifier: Modifier = Modifier
) {
    val bubbleColor = if (isMe) Color(0xFFD9FDD3) else Color(0xFFFFFFFF)
    val textColor = Color(0xFF78828A)

    Surface(
        modifier = modifier
            .padding(vertical = 2.dp)
            .widthIn(max = 280.dp),
        shape = RoundedCornerShape(16.dp),
        color = bubbleColor,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Mensaje eliminado",
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}