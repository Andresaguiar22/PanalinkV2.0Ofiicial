package com.example.live.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.identity.bridge.LegacyIdentityBridge
import com.example.data.supabase.SupabaseClient
import com.example.live.domain.model.LiveComment
import com.example.ui.components.rememberAsyncMediaUrl
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
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
    val listState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val identityBridge = remember(context) { LegacyIdentityBridge(context) }

    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) {
            listState.animateScrollToItem(comments.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        reverseLayout = false
    ) {
        items(comments, key = { it.id }) { comment ->
            val identityState by identityBridge.identityRepository
                .observeIdentity(comment.userId)
                .collectAsStateWithLifecycle(initialValue = null)
            val displayName = identityState?.displayName ?: comment.userId.take(8)
            val avatarUrl = identityState?.avatarUrl
            val resolvedAvatarUrl = rememberAsyncMediaUrl(avatarUrl)

            Row(
                modifier = Modifier
                    .background(
                        if (comment.userId == SupabaseClient.currentUser?.id) {
                            Color(0xFF00A884).copy(alpha = 0.25f)
                        } else {
                            Color.Black.copy(alpha = 0.35f)
                        },
                        RoundedCornerShape(12.dp)
                    )
                    .combinedClickable(
                        onLongClick = {
                            if (isBroadcaster && comment.userId != SupabaseClient.currentUser?.id) {
                                onDeleteComment(comment.id)
                            }
                        },
                        onClick = {}
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                ) {
                    if (resolvedAvatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = resolvedAvatarUrl,
                            contentDescription = "Avatar de $displayName",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Gray.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayName.firstOrNull()?.toString() ?: "?",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (comment.userId == SupabaseClient.currentUser?.id) FontWeight.Bold else FontWeight.SemiBold
                        )
                        if (comment.userId == hostId) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFF2B54),
                                modifier = Modifier.height(14.dp)
                            ) {
                                Text(
                                    text = "HOST",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = comment.text,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp
                    )
                }

                if (isBroadcaster && comment.userId != SupabaseClient.currentUser?.id) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "🗑",
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { onDeleteComment(comment.id) }
                    )
                }
            }
        }
    }
}
