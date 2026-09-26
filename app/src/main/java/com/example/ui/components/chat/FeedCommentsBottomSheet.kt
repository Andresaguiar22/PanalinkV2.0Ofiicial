package com.example.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ChannelComment
import java.text.SimpleDateFormat
import java.util.*
import com.example.ui.settings.ios.IosSettingsColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedCommentsBottomSheet(
    onDismissRequest: () -> Unit,
    comments: List<ChannelComment>,
    onSendComment: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var text by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = IosSettingsColors.groupBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Comentarios (${comments.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = IosSettingsColors.label
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "Cerrar", tint = IosSettingsColors.label)
                }
            }
            HorizontalDivider(color = IosSettingsColors.cell)
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                items(comments) { comment ->
                    CommentItem(comment)
                }
            }

            HorizontalDivider(color = IosSettingsColors.cell)
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Añadir un comentario...", color = IosSettingsColors.secondaryLabel) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = IosSettingsColors.cell,
                        unfocusedContainerColor = IosSettingsColors.cell,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = IosSettingsColors.label,
                        unfocusedTextColor = IosSettingsColors.label
                    ),
                    shape = CircleShape
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (text.isNotBlank()) {
                            onSendComment(text)
                            text = ""
                        }
                    },
                    modifier = Modifier
                        .background(IosSettingsColors.blue, CircleShape)
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar",
                        tint = IosSettingsColors.label
                    )
                }
            }
        }
    }
}

@Composable
fun CommentItem(comment: ChannelComment) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        AsyncImage(
            model = comment.author?.avatarUrl,
            contentDescription = "Avatar",
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.DarkGray)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.author?.displayName ?: "",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = IosSettingsColors.label
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatTime(comment.createdAt),
                    fontSize = 12.sp,
                    color = IosSettingsColors.secondaryLabel
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = comment.contentText,
                fontSize = 14.sp,
                color = IosSettingsColors.label
            )
        }
    }
}

private fun formatTime(timeStr: String?): String {
    if (timeStr == null) return ""
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = format.parse(timeStr)
        if (date != null) {
            val outFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            outFormat.format(date)
        } else ""
    } catch (e: Exception) {
        ""
    }
}
