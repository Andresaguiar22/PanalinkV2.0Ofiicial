package com.example.live.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.supabase.SupabaseClient
import com.example.live.domain.model.LiveComment
import com.example.ui.components.PanaAvatar

private val MENTION_REGEX = Regex("@[\\p{L}\\p{N}._]+")

/** Variante compatible para la pantalla de emisión: incluye el campo de texto. */
@Composable
fun LiveViewerComments(
    comments: List<LiveComment>,
    onSendComment: (String) -> Unit,
    isBroadcaster: Boolean,
    onDeleteComment: (String) -> Unit,
    onBlockUser: (String) -> Unit,
    hostId: String? = null,
    modifier: Modifier = Modifier
) {
    LiveViewerComments(
        comments = comments,
        onDeleteComment = onDeleteComment,
        onBlockUser = onBlockUser,
        isBroadcaster = isBroadcaster,
        hostId = hostId,
        modifier = modifier
    )
}

@Composable
fun LiveViewerComments(
    comments: List<LiveComment>,
    onDeleteComment: (String) -> Unit,
    onBlockUser: (String) -> Unit,
    isBroadcaster: Boolean,
    hostId: String? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var menuForCommentId by remember { mutableStateOf<String?>(null) }
    val myId = SupabaseClient.currentUser?.id

    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) {
            listState.animateScrollToItem(comments.size - 1)
        }
    }

    val textShadow = TextStyle(
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.85f),
            offset = Offset(1.5f, 1.5f),
            blurRadius = 4f
        )
    )

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        items(comments, key = { it.id }) { comment ->
            val identity = rememberLiveIdentity(comment.userId)
            val displayName = identity?.displayName?.takeIf { it.isNotBlank() }
                ?: comment.userId.take(8)
            val isHost = !hostId.isNullOrEmpty() && comment.userId == hostId
            val isMine = comment.userId == myId

            if (comment.isJoinEvent) {
                JoinEventRow(displayName = displayName, shadow = textShadow)
            } else {
                CommentRow(
                    comment = comment,
                    displayName = displayName,
                    avatarUrl = identity?.avatarUrl,
                    isHost = isHost,
                    isMine = isMine,
                    shadow = textShadow,
                    menuExpanded = menuForCommentId == comment.id,
                    canModerate = isBroadcaster && !isMine,
                    onLongPress = {
                        if (isBroadcaster && !isMine) menuForCommentId = comment.id
                    },
                    onDismissMenu = { menuForCommentId = null },
                    onDelete = {
                        menuForCommentId = null
                        onDeleteComment(comment.id)
                    },
                    onBlock = {
                        menuForCommentId = null
                        onBlockUser(comment.userId)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentRow(
    comment: LiveComment,
    displayName: String,
    avatarUrl: String?,
    isHost: Boolean,
    isMine: Boolean,
    shadow: TextStyle,
    menuExpanded: Boolean,
    canModerate: Boolean,
    onLongPress: () -> Unit,
    onDismissMenu: () -> Unit,
    onDelete: () -> Unit,
    onBlock: () -> Unit
) {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(
                    onLongClick = onLongPress,
                    onClick = {}
                ),
            verticalAlignment = Alignment.Top
        ) {
            PanaAvatar(
                avatarUrl = avatarUrl,
                userId = comment.userId,
                size = 26.dp,
                borderWidth = 0.5.dp,
                borderColor = Color.White.copy(alpha = 0.5f),
                contentDescription = "Avatar de $displayName",
                placeholderName = displayName
            )

            Spacer(modifier = Modifier.width(7.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName,
                        color = if (isMine) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.92f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        style = shadow,
                        maxLines = 1
                    )
                    if (isHost) {
                        Spacer(modifier = Modifier.width(5.dp))
                        RoleBadge(text = "Anfitrión", color = Color(0xFFFF2B54))
                    }
                    if (isMine) {
                        Spacer(modifier = Modifier.width(5.dp))
                        RoleBadge(text = "Tú", color = Color(0xFF00A884))
                    }
                }
                Text(
                    text = highlightMentions(comment.text),
                    color = Color.White,
                    fontSize = 12.5.sp,
                    style = shadow
                )
            }

            if (canModerate) {
                Text(
                    text = "⋮",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onLongPress
                        )
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = onDismissMenu,
            containerColor = Color(0xFF1F2C34)
        ) {
            DropdownMenuItem(
                text = { Text("Eliminar comentario", color = Color.White, fontSize = 14.sp) },
                onClick = onDelete
            )
            DropdownMenuItem(
                text = { Text("Bloquear usuario", color = Color(0xFFEF5350), fontSize = 14.sp) },
                onClick = onBlock
            )
        }
    }
}

@Composable
private fun JoinEventRow(displayName: String, shadow: TextStyle) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "👋", fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.width(7.dp))

        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFB9F6CA))) {
                    append(displayName)
                }
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Color.White.copy(alpha = 0.9f))) {
                    append(" se unió")
                }
            },
            fontSize = 12.sp,
            style = shadow,
            maxLines = 1
        )
    }
}

@Composable
private fun RoleBadge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(5.dp), color = color) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

/** Resalta @menciones dentro del texto del comentario. */
@Composable
private fun highlightMentions(text: String): AnnotatedString {
    return remember(text) {
        if (!text.contains('@')) {
            AnnotatedString(text)
        } else {
            buildAnnotatedString {
                var lastIndex = 0
                MENTION_REGEX.findAll(text).forEach { match ->
                    if (match.range.first > lastIndex) {
                        append(text.substring(lastIndex, match.range.first))
                    }
                    withStyle(SpanStyle(color = Color(0xFF6FD3FF), fontWeight = FontWeight.SemiBold)) {
                        append(match.value)
                    }
                    lastIndex = match.range.last + 1
                }
                if (lastIndex < text.length) {
                    append(text.substring(lastIndex))
                }
            }
        }
    }
}
