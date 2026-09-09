package com.example.live.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LiveViewerBottomBar(
    liveId: String,
    onSendComment: (String) -> Unit,
    onGift: () -> Unit,
    modifier: Modifier = Modifier
) {
    var commentText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = commentText,
            onValueChange = { commentText = it },
            placeholder = { Text("Escribe algo...", color = Color.LightGray) },
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .focusRequester(focusRequester),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00A884),
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = CircleShape,
            trailingIcon = {
                if (commentText.isNotBlank()) {
                    IconButton(onClick = {
                        onSendComment(commentText.trim())
                        commentText = ""
                    }) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Enviar",
                            tint = Color(0xFF00A884)
                        )
                    }
                }
            }
        )

        Spacer(modifier = Modifier.width(10.dp))

        IconButton(onClick = onGift) {
            Icon(
                imageVector = Icons.Default.CardGiftcard,
                contentDescription = "Regalar",
                tint = Color(0xFFFE2C55),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
