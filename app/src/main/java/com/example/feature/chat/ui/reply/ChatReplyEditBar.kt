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
import androidx.compose.material.icons.rounded.Close
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
import com.example.ui.settings.ios.IosSettingsColors

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
                .background(IosSettingsColors.cell)
                     .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(36.dp)
                        .background(IosSettingsColors.blue)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Respondiendo a $senderName",
                        color = IosSettingsColors.blue,
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
                        color = IosSettingsColors.secondaryLabel,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { onCancelReply() }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Cancelar respuesta",
                        tint = IosSettingsColors.secondaryLabel
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
                     .background(IosSettingsColors.cell)
                     .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(36.dp)
                        .background(IosSettingsColors.blue)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Editar mensaje ✏️",
                        color = IosSettingsColors.blue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = editingMsg.textContent,
                        color = IosSettingsColors.secondaryLabel,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onCancelEdit) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Cancelar edición",
                        tint = IosSettingsColors.secondaryLabel
                    )
                }
            }
        }
}
