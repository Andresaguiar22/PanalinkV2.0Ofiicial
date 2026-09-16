package com.example.live.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SendGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFFF2E77), Color(0xFFA73BFA), Color(0xFF2EA8FF))
)

@Composable
fun LiveViewerBottomBar(
    unreadCount: Int,
    onSendComment: (String) -> Unit,
    onOpenStudio: () -> Unit,
    onOpenGifts: () -> Unit,
    onOpenRequests: () -> Unit,
    onToggleChat: () -> Unit,
    onOpenMore: () -> Unit,
    requestPending: Boolean = false,
    modifier: Modifier = Modifier
) {
    var commentText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    fun submit() {
        val text = commentText.trim()
        if (text.isEmpty()) return
        onSendComment(text)
        commentText = ""
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BarAction(
            icon = Icons.Default.AutoAwesome,
            label = "Studio",
            iconTint = Color.Unspecified,
            gradientIcon = true,
            onClick = onOpenStudio
        )

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.14f))
                .padding(start = 14.dp, end = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (commentText.isEmpty()) {
                    Text(
                        text = "Escribe algo...",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.5.sp
                    )
                }
                BasicTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(SendGradient)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { submit() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Enviar",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        BarAction(
            icon = Icons.Default.ChatBubble,
            label = "Chat",
            onClick = onToggleChat,
            badgeCount = unreadCount
        )
        BarAction(
            icon = Icons.Default.CardGiftcard,
            label = "Regalos",
            iconTint = Color(0xFFFF7BAC),
            onClick = onOpenGifts
        )
        BarAction(
            icon = Icons.Default.PersonAdd,
            label = "Pedidos",
            iconTint = if (requestPending) Color(0xFFFFC107) else Color.White,
            onClick = onOpenRequests
        )
        BarAction(
            icon = Icons.Default.MoreHoriz,
            label = "Más",
            onClick = onOpenMore
        )
    }
}

@Composable
private fun BarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    iconTint: Color = Color.White,
    gradientIcon: Boolean = false,
    badgeCount: Int = 0
) {
    Column(
        modifier = Modifier
            .width(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (gradientIcon) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color(0xFFB388FF),
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(21.dp)
                )
            }
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-4).dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF3B30))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 8.sp,
            maxLines = 1
        )
    }
}
