@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.ui.screen

import com.example.ui.components.*
import com.example.util.*

import androidx.compose.foundation.BorderStroke
import com.example.ui.components.FeedPostCard
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import com.example.ui.viewmodel.StatesViewModel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import coil.compose.AsyncImage
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.asImageBitmap
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.identity.model.toIdentityUiState
import androidx.navigation.NavGraph.Companion.findStartDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.data.model.*
import com.example.data.supabase.SupabaseClient
import com.example.ui.viewmodel.*
import com.example.ui.theme.shimmerEffect
import com.example.ui.theme.getAvatarGradient
import com.example.ui.components.PanalinkPullToRefreshBox
import com.example.ui.theme.bounceClick
import com.example.ui.components.chat.list.ChatPreviewCard
import com.example.util.ChatListScrollManager
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.text.SimpleDateFormat
import java.util.*

import com.example.ui.viewmodel.NotificationsViewModel
import com.example.ui.settings.ios.IosSettingsColors
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Done


@Composable
fun ChatsTabContent(
    chatsState: ChatsUiState,
    typingChats: Map<String, Boolean>,
    contactsState: ContactsUiState,
    statesState: StatesUiState,
    chatsViewModel: ChatsViewModel,
    onNavigateToChat: (String, String) -> Unit,
    onNavigateToViewState: (String) -> Unit,
    onNavigateToCreateState: () -> Unit,
    onRefresh: () -> Unit,
    selectedChatIds: Set<String> = emptySet(),
    onToggleChatSelection: (String) -> Unit = {},
    onStartChatSelection: (String) -> Unit = {},
    deletedChatIds: Set<String> = emptySet(),
    pinnedChatIds: Set<String> = emptySet(),
    mutedChatIds: Set<String> = emptySet(),
    customUnreadCounts: Map<String, Int> = emptyMap(),
    onNavigateToSearch: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(Unit) {
        val pos = ChatListScrollManager.getPosition(context)
        if (pos != null) listState.scrollToItem(pos.first, pos.second)
    }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            ChatListScrollManager.savePosition(context, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
    }

    val visibleChats = remember(chatsState, deletedChatIds) {
        (chatsState as? ChatsUiState.Success)?.chats
            ?.filterNot { deletedChatIds.contains(it.chat.id) || it.chat.isArchived }
            ?.sortedWith(
                compareByDescending<ChatWithDetails> { it.chat.isPinned }
                    .thenByDescending { it.chat.pinnedAt ?: "" }
                    .thenByDescending { it.lastMessage?.createdAt ?: it.chat.createdAt ?: "" }
            )
            ?: emptyList()
    }

    Box(Modifier.fillMaxSize().background(IosSettingsColors.groupBackground)) {
        PanalinkPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    onRefresh()
                    kotlinx.coroutines.delay(700)
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 2.dp, bottom =  12.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item {
                    PaniOSSearchBar(
                        onSearchClick = onNavigateToSearch,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal =  16.dp, vertical =  8.dp)
                    )
                }
                if (statesState is StatesUiState.Success && statesState.states.isNotEmpty()) {
                    item {
                        com.example.ui.components.StoryShortcutRow(
                            stories = statesState.states,
                            currentUserId = SupabaseClient.currentUser?.id,
                            currentUserAvatar = SupabaseClient.currentProfile?.avatarUrl,
                            onNavigateToCreateState = onNavigateToCreateState,
                            onNavigateToViewState = onNavigateToViewState
                        )
                    }
                }
                    when (chatsState) {
                        is ChatsUiState.Loading -> {
                            items(6) { ShimmerChatItemRow() }
                        }
                        is ChatsUiState.Success -> {
                            if (visibleChats.isEmpty()) {
                                item {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(48.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = IosSettingsColors.secondaryLabel, modifier = Modifier.size(64.dp))
                                        Spacer(Modifier.height(14.dp))
                                        Text("No tienes chats activos", color = IosSettingsColors.label, fontWeight = FontWeight.SemiBold, fontSize =  15.sp)
                                        Spacer(Modifier.height(6.dp))
                                        Text("Usa + para comenzar una nueva conversación.", color = IosSettingsColors.secondaryLabel, fontSize = 13.sp, textAlign = TextAlign.Center)
                                    }
                                }
                            } else {
                                itemsIndexed(visibleChats, key = { index, chatDetails -> "${chatDetails.chat.id}_${index}" }) { index, chatDetails ->
                                    ChatItemRow(
                                        chatDetails = if (customUnreadCounts.containsKey(chatDetails.chat.id)) chatDetails.copy(unreadCount = customUnreadCounts[chatDetails.chat.id]!!) else chatDetails,
                                        chatsViewModel = chatsViewModel,
                                        isTyping = typingChats[chatDetails.chat.id] == true,
                                        isSelected = selectedChatIds.contains(chatDetails.chat.id),
                                        isMuted = mutedChatIds.contains(chatDetails.chat.id) || chatDetails.chat.isMuted,
                                        isPinned = pinnedChatIds.contains(chatDetails.chat.id) || chatDetails.chat.isPinned,
                                        onLongClick = { if (selectedChatIds.isEmpty()) onStartChatSelection(chatDetails.chat.id) },
                                        onClick = {
                                            if (selectedChatIds.isNotEmpty()) onToggleChatSelection(chatDetails.chat.id)
                                            else onNavigateToChat(chatDetails.chat.id, chatDetails.otherMember?.id ?: "")
                                        }
                                    )
                                }
                            }
                        }
                        is ChatsUiState.Error -> {
                            item {
                                Text(chatsState.message, color = IosSettingsColors.red, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp))
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
fun ChatItemRow(
    chatDetails: ChatWithDetails,
    chatsViewModel: ChatsViewModel,
    onNavigateToChat: (String, String) -> Unit = { _, _ -> },
    isSelected: Boolean = false,
    isMuted: Boolean = false,
    isPinned: Boolean = false,
    customUnreadCount: Int? = null,
    isTyping: Boolean = false,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    val otherUser = chatDetails.otherMember
    val lastMessage = chatDetails.lastMessage
    val formattedTime = com.example.data.model.formatIsoDateTime(lastMessage?.createdAt)
    val presenceMap by com.example.data.repository.PresenceRepository.presenceMap.collectAsStateWithLifecycle()
    val status = presenceMap[otherUser?.id ?: ""]?.status?.rawValue ?: "offline"
    val secondaryStatus = presenceMap[otherUser?.id ?: ""]?.secondaryStatus
        ?.takeIf { it != com.example.data.repository.SecondaryPresenceStatus.NONE }
        ?.rawValue
    val unread = customUnreadCount ?: chatDetails.unreadCount
    val isMine = lastMessage?.senderId == SupabaseClient.currentUser?.id

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            
            .background(
                if (isSelected) IosSettingsColors.cellElevated
                else Color.Transparent
            )
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(start =    16.dp, top =    10.dp, end =    12.dp, bottom =    10.dp)
    ) {
        ChatAvatar(
            name = otherUser?.displayName ?: "Pana de panalink",
            avatarUrl = otherUser?.avatarUrl,
            status = status,
            secondaryStatus = secondaryStatus,
            hasUnread = unread > 0,
            size = 52.dp,
            isSelected = isSelected
        )

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = otherUser?.displayName ?: "Pana de panalink",
                        color = IosSettingsColors.label,
                        fontWeight = FontWeight.SemiBold,
                        fontSize =  15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isPinned) {
                        Spacer(Modifier.width(5.dp))
                        Icon(Icons.Rounded.PushPin, contentDescription = "Anclado", tint = IosSettingsColors.secondaryLabel, modifier = Modifier.size(12.dp))
                    }
                }

                Text(
                    text = formattedTime,
                    color = if (unread > 0) IosSettingsColors.blue else IosSettingsColors.secondaryLabel,
                    fontSize =  12.sp
                )
            }

            Spacer(Modifier.height(3.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isTyping) "escribiendo…" else (lastMessage?.previewText() ?: "Inicia la conversación chamo..."),
                    color = if (isTyping) IosSettingsColors.green else IosSettingsColors.secondaryLabel,
                    fontSize =  13.sp,
                    fontWeight = if (isTyping) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isMuted) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.NotificationsOff, contentDescription = "Silenciado", tint = IosSettingsColors.secondaryLabel, modifier = Modifier.size(14.dp))
                }

                if (isMine && lastMessage != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = if (lastMessage.seenAt != null) Icons.Rounded.DoneAll else Icons.Rounded.Done,
                        contentDescription = if (lastMessage.seenAt != null) "Visto" else "Enviado",
                        tint = IosSettingsColors.blue,
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (unread > 0) {
                    Spacer(Modifier.width(7.dp))
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 20.dp)
                            .height(20.dp)
                            .background(IosSettingsColors.blue, CircleShape)
                            .padding(horizontal = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (unread > 99) "99+" else unread.toString(),
color = IosSettingsColors.onAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(2.dp))

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = IosSettingsColors.chevron,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun ShimmerChatItemRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Shimmer Avatar
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .shimmerEffect()
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shimmer Name
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerEffect()
                )
                // Shimmer Time
                Box(
                    modifier = Modifier
                        .width(50.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerEffect()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Shimmer Message Snippet
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
        }
    }
}


@Composable
fun ChatAvatar(
    name: String,
    avatarUrl: String?,
    status: String = "offline",
    secondaryStatus: String? = null,
    hasUnread: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 54.dp,
    isSelected: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(size)
            .bounceClick()
    ) {
        val borderModifier = Modifier
            .fillMaxSize()
            .border(
                if (hasUnread) 2.2.dp else 1.4.dp,
if (hasUnread) com.example.ui.theme.getPremiumActiveIconGradient() else Brush.linearGradient(listOf(PanaLinkCyberpunkColors.Gold, PanaLinkCyberpunkColors.Gold)),
                CircleShape
            )
            .padding(if (hasUnread) 3.dp else 1.5.dp)

        Box(
            modifier = borderModifier
                .clip(CircleShape)
                .background(getAvatarGradient(name))
        ) {
            val resolvedUrl = remember(avatarUrl) {
                com.example.data.repository.CdnManager.resolveAvatarUrl(avatarUrl)
            }
            if (resolvedUrl != null) {
                AsyncImage(
                    model = resolvedUrl,
                    contentDescription = "Avatar de $name",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                val initials = if (name.isNotEmpty()) name.take(1).uppercase() else "?"
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        color = IosSettingsColors.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value * 0.38f).sp
                    )
                }
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.BottomEnd)
                    .background(IosSettingsColors.groupBackground, CircleShape)
                    .border(1.dp, IosSettingsColors.groupBackground, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Selected",
                    tint = IosSettingsColors.blue,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            val isOnline = status != "offline"
            if (isOnline || secondaryStatus != null) {
                com.example.ui.components.chat.list.PresenceIndicator(
                    isOnline = isOnline,
                    status = status,
                    secondaryStatus = secondaryStatus,
                    size = 13.dp,
                    showText = false,
                    showOffline = false,
                    borderColor = PanaLinkCyberpunkColors.Background,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        }
    }
}



/** Posición de una fila de chat dentro del panel agrupado (esquinas del panel). */
private fun chatCardPositionFor(index: Int, total: Int): com.example.ui.theme.ChatCardPosition = when {
    total <= 1 -> com.example.ui.theme.ChatCardPosition.SINGLE
    index <= 0 -> com.example.ui.theme.ChatCardPosition.TOP
    index >= total - 1 -> com.example.ui.theme.ChatCardPosition.BOTTOM
    else -> com.example.ui.theme.ChatCardPosition.MIDDLE
}
