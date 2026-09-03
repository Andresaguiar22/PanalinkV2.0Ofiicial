package com.example.live.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.live.domain.model.LiveComment
import kotlinx.coroutines.launch

@Composable
fun LiveViewerComments(
    comments: List<LiveComment>,
    onSendComment: (String) -> Unit,
    isBroadcaster: Boolean,
    onDeleteComment: (String) -> Unit,
    onBlockUser: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Comentario anclado (Placeholder para lógica real)
    // PinnedComment(username = "Host", text = "Bienvenidos al directo!")

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        reverseLayout = false
    ) {
        items(comments, key = { it.id }) { comment ->
            // Burbuja de comentario normal
            Row(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(20.dp).background(Color.Gray, CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${comment.userId.take(6)}: ${comment.text}",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}
