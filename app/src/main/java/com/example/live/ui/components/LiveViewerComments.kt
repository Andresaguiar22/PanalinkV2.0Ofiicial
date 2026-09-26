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
import androidx.compose.ui.graphics.Brush
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
import com.example.ui.settings.ios.IosSettingsColors

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
    modifier: Modifier = Modifier,
    newOnTop: Boolean = false,
    fadeOutBottom: Boolean = false
) {
    LiveViewerComments(
        comments = comments,
        onDeleteComment = onDeleteComment,
        onBlockUser = onBlockUser,
        isBroadcaster = isBroadcaster,
        hostId = hostId,
        modifier = modifier,
        newOnTop = newOnTop,
        fadeOutBottom = fadeOutBottom
    )
}

@Composable
fun LiveViewerComments(
    comments: List<LiveComment>,
    onDeleteComment: (String) -> Unit,
    onBlockUser: (String) -> Unit,
    isBroadcaster: Boolean,
    hostId: String? = null,
    modifier: Modifier = Modifier,
    /** true = los comentarios NUEVOS aparecen arriba y los antiguos van quedando abajo. */
    newOnTop: Boolean = false,
    /** true = desvanece el contenido hacia abajo (los antiguos se difuminan). */
    fadeOutBottom: Boolean = false
) {
    val listState = rememberLazyListState()
    var menuForCommentId by remember { mutableStateOf<String?>(null) }
    val myId = SupabaseClient.currentUser?.id

    LaunchedEffect(comments.size, newOnTop) {
        if (comments.isNotEmpty()) {
            if (newOnTop) {
                listState.animateScrollToItem(0)
            } else {
                listState.animateScrollToItem(comments.size - 1)
            }
        }
    }

    val textShadow = TextStyle(
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.85f),
            offset = Offset(1.5f, 1.5f),
            blurRadius = 4f
        )
    )

    val listContent: @Composable () -> Unit = {
        LazyColumn(
            state = listState,
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(if (newOnTop) comments.asReversed() else comments, key = { it.id }) { comment ->
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

    if (fadeOutBottom) {
        Box(modifier = modifier) {
            listContent()
            // Desvanecido inferior: los comentarios antiguos se difuminan hacia el
            // borde inferior del contenedor, dejando los nuevos nítidos arriba.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.62f to Color.Transparent,
                            1f to Color(0xFF0E0E10)
                        )
                    )
            )
        }
    } else {
        listContent()
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
                        color = if (isMine) IosSettingsColors.yellow else Color.White.copy(alpha = 0.92f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        style = shadow,
                        maxLines = 1
                    )
                    if (isHost) {
                        Spacer(modifier = Modifier.width(5.dp))
                        RoleBadge(text = "Anfitrión", color = IosSettingsColors.red)
                    }
                    if (isMine) {
                        Spacer(modifier = Modifier.width(5.dp))
                        RoleBadge(text = "Tú", color = IosSettingsColors.blue)
}
                }
                if (com.example.ui.components.parseCommentGif(comment.text) != null) {
                    com.example.ui.components.CommentMediaText(
                        text = comment.text,
                        fallbackColor = IosSettingsColors.label,
                        compact = true
                    )
                } else {
                    Text(
                        text = highlightMentions(comment.text),
                        color = IosSettingsColors.label,
                        fontSize = 13.5.sp,
                        style = shadow
                    )
                }
            }

            if (canModerate) {
                Text(
                    text = "⋮",
                    color = IosSettingsColors.secondaryLabel,
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
            containerColor = IosSettingsColors.cell
        ) {
            DropdownMenuItem(
                text = { Text("Eliminar comentario", color = IosSettingsColors.label, fontSize = 14.sp) },
                onClick = onDelete
            )
            DropdownMenuItem(
                text = { Text("Bloquear usuario", color = IosSettingsColors.red, fontSize = 14.sp) },
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
                .background(IosSettingsColors.separator),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "👋", fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.width(7.dp))

        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = IosSettingsColors.green)) {
                    append(displayName)
                }
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = IosSettingsColors.label)) {
                    append(" se unió")
                }
            },
            fontSize = 13.sp,
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
            color = IosSettingsColors.label,
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
                    withStyle(SpanStyle(color = IosSettingsColors.blue, fontWeight = FontWeight.SemiBold)) {
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
