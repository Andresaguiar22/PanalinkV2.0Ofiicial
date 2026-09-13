package com.example.feature.chat.ui.reply

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Message

@Composable
fun ChatReplyEditBar(
    replyingToMessage: Message?,
    editingMessage: Message?,
    currentUid: String,
    otherUserDisplayName: String,
    onCancelReply: () -> Unit,
    onCancelEdit: () -> Unit
) {
        // Replying Mode Bar Preview
        if (replyingToMessage != null) {
            val replyingMsg = replyingToMessage!!
            val isRepliedByMe = replyingMsg.senderId == currentUid
            val senderName = if (isRepliedByMe) "Tú" else otherUserDisplayName
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                .background(Color(0xFF1E293B))
                     .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(36.dp)
                        .background(Color(0xFF38BDF8))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Respondiendo a $senderName",
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    val replyBarText = remember(replyingMsg) {
                        val content = replyingMsg.textContent
                        when {
                            content.startsWith("[Image] ") -> "Foto 🖼️"
                            content.startsWith("[Video] ") -> "Video 🎥"
                            content.startsWith("[Audio] ") -> "Nota de voz 🎤"
                            content.startsWith("[Document] ") -> "Documento 📄"
                            content.startsWith("[Sticker] ") -> "Sticker 🏷️"
                            else -> content
                        }
                    }
                    Text(
                        text = replyBarText,
                        color = Color(0xFF8696A0),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { onCancelReply() }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancelar respuesta",
                        tint = Color(0xFF8696A0)
                    )
                }
            }
        }

        // Editing Mode Bar Preview
        if (editingMessage != null) {
            val editingMsg = editingMessage!!
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                     .background(Color(0xFF1E293B))
                     .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(36.dp)
                        .background(Color(0xFF38BDF8))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Editar mensaje ✏️",
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = editingMsg.textContent,
                        color = Color(0xFF8696A0),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onCancelEdit) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancelar edición",
                        tint = Color(0xFF8696A0)
                    )
                }
            }
        }
}
