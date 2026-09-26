package com.example.ui.components.chat.list

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ChatWithDetails
import com.example.data.model.formatIsoDateTime
import com.example.data.supabase.SupabaseClient
import com.example.identity.model.toIdentityUiState
import com.example.ui.theme.ChatCardPosition
import com.example.ui.theme.GoldGlassCard
import com.example.ui.theme.PanalinkSkin
import com.example.ui.settings.ios.IosSettingsColors
import com.example.ui.theme.chatCardShape

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatPreviewCard(
    chatDetails: ChatWithDetails,
    isTyping: Boolean,
    isSelected: Boolean = false,
    isPinned: Boolean = false,
    position: ChatCardPosition = ChatCardPosition.SINGLE,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    val initialOtherUser = chatDetails.otherMember

    val context = androidx.compose.ui.platform.LocalContext.current
    val identityRepository = remember { com.example.identity.bridge.LegacyIdentityBridge(context).identityRepository }
    val initialCached = remember(initialOtherUser?.id) { com.example.identity.memory.IdentityMemoryCache.profiles.get(initialOtherUser?.id ?: "") }
    val identityState by identityRepository.observeIdentity(initialOtherUser?.id ?: "").collectAsStateWithLifecycle(initialValue = initialCached?.toIdentityUiState())

    val safeAvatarUrl = identityState?.avatarUrl ?: initialOtherUser?.avatarUrl
    val safeDisplayName = identityState?.displayName ?: initialOtherUser?.displayName ?: ""
    val safeUserId = identityState?.userId ?: initialOtherUser?.id

    val lastMessage = chatDetails.lastMessage
    val formattedTime = formatIsoDateTime(lastMessage?.createdAt ?: chatDetails.chat.createdAt)

    val presenceMap by com.example.data.repository.PresenceRepository.presenceMap.collectAsState()
    val presenceInfo = presenceMap[safeUserId ?: ""]
    val userStatus = presenceInfo?.status?.rawValue ?: "offline"
    val secondaryStatus = if (presenceInfo?.secondaryStatus != com.example.data.repository.SecondaryPresenceStatus.NONE) presenceInfo?.secondaryStatus?.rawValue else null
    val isOnline = userStatus != "offline"

    val selectedContainer = IosSettingsColors.cellElevated
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) selectedContainer else PanalinkSkin.GlassStrong,
        label = "container_color"
    )

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(400)) + slideInVertically(initialOffsetY = { 20 }),
        exit = fadeOut()
    ) {
        GoldGlassCard(
            modifier = Modifier.padding(horizontal = 14.dp),
            shape = chatCardShape(position),
            container = containerColor
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onLongClick = onLongClick,
                        onClick = onClick
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar con anillo dorado + presencia
                Box(modifier = Modifier.size(54.dp)) {
                    com.example.ui.components.PanaAvatar(
                        avatarUrl = safeAvatarUrl,
                        userId = safeUserId,
                        placeholderName = safeDisplayName,
                        size = 54.dp,
                        borderWidth = 1.5.dp,
                        borderColor = IosSettingsColors.green
                    )

                    PresenceIndicator(
                        isOnline = isOnline,
                        status = userStatus,
                        secondaryStatus = secondaryStatus,
                        modifier = Modifier.align(Alignment.BottomEnd),
                        borderColor = containerColor
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = safeDisplayName.ifBlank { "Pana de panalink" },
                            color = PanalinkSkin.Cream,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = formattedTime,
                            color = if (chatDetails.unreadCount > 0) IosSettingsColors.green else PanalinkSkin.Sub,
                            fontSize = 12.sp,
                            fontWeight = if (chatDetails.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AnimatedContent(
                            targetState = isTyping,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                                fadeOut(animationSpec = tween(90))
                            },
                            label = "typing_content",
                            modifier = Modifier.weight(1f)
                        ) { typing ->
                            if (typing) {
                                TypingIndicator()
                            } else {
                                val preview = lastMessage?.previewText() ?: "Inicia la conversación..."
                                Text(
                                    text = preview,
                                    color = PanalinkSkin.CreamDim,
                                    // Un mensaje que es solo emoji se muestra grande, como en el mockup
                                    fontSize = if (isEmojiOnly(preview)) 22.sp else 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Tildes de estado del último mensaje propio (enviado / entregado / leído)
                            val myUserId = SupabaseClient.currentUser?.id
                            if (lastMessage != null && myUserId != null && lastMessage.senderId == myUserId && !isTyping) {
                                val seen = lastMessage.seenAt != null ||
                                    lastMessage.status == "read" || lastMessage.status == "seen"
                                val delivered = lastMessage.deliveredAt != null || lastMessage.status == "delivered"
                                Icon(
                                    imageVector = if (seen || delivered) Icons.Default.DoneAll else Icons.Default.Done,
                                    contentDescription = "Estado del mensaje",
                                    tint = PanalinkSkin.ReadTick,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            if (chatDetails.chat.isMuted) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsOff,
                                    contentDescription = "Silenciado",
                                    tint = PanalinkSkin.Sub,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            if (isPinned) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = null,
                                    tint = PanalinkSkin.Gold,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            AnimatedContent(
                                targetState = chatDetails.unreadCount,
                                transitionSpec = {
                                    scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) togetherWith
                                    scaleOut()
                                },
                                label = "unread_badge"
                            ) { count ->
                                ChatUnreadBadge(count = count)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** true si el texto son solo emojis/símbolos (sin letras ni dígitos). */
private fun isEmojiOnly(text: String): Boolean {
    val t = text.trim()
    if (t.isEmpty() || t.length > 6) return false
    return t.none { it.isLetterOrDigit() }
}
