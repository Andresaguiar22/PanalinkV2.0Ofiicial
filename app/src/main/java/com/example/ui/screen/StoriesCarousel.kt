package com.example.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.supabase.SupabaseClient
import com.example.ui.components.PanaAvatar
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.media.social.StoryMediaResolver
import com.example.data.repository.VcdnUrlResolver
import com.example.ui.viewmodel.StatesViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.identity.bridge.LegacyIdentityBridge
import com.example.identity.model.toIdentityUiState
import com.example.identity.memory.IdentityMemoryCache
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StoriesCarousel(
    statesState: com.example.ui.viewmodel.StatesUiState,
    onNavigateToCreateState: () -> Unit,
    onNavigateToViewState: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            text = "Historias 🇻🇪✨",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                Card(
                    modifier = Modifier
                        .width(100.dp)
                        .height(150.dp)
                        .clickable { onNavigateToCreateState() },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161618))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val resolvedAvatar = remember(SupabaseClient.currentProfile?.avatarUrl) {
                            com.example.data.repository.CdnManager.resolveAvatarUrl(SupabaseClient.currentProfile?.avatarUrl)
                        }
                        if (resolvedAvatar != null) {
                            AsyncImage(
                                model = resolvedAvatar,
                                contentDescription = "Mi Avatar",
                                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f).background(Color(0xFF161618)))
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.3f)
                                .align(Alignment.BottomCenter)
                                .background(Color(0xFF161618))
                        ) {
                            Text("Tu historia", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = (-20).dp)
                                .size(28.dp)
                                .background(Color(0xFFD500F9), CircleShape)
                                .border(2.dp, Color(0xFF161618), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            if (statesState is com.example.ui.viewmodel.StatesUiState.Success) {
                val uniqueUserStories = statesState.states.distinctBy { it.state.userId }
                items(uniqueUserStories, key = { it.state.userId }) { stateWithUser ->
                    val firstState = statesState.states.firstOrNull { it.state.userId == stateWithUser.state.userId } ?: stateWithUser
                    val profile = stateWithUser.profile
                    val state = firstState.state
                    val userStories = statesState.states.filter { it.state.userId == stateWithUser.state.userId }
                    val hasUnread = userStories.any { it.state.viewedByMe != true }

                    val identityRepository = remember { LegacyIdentityBridge(context).identityRepository }
                    val identityState by identityRepository.observeIdentity(state.userId).collectAsStateWithLifecycle(initialValue = IdentityMemoryCache.profiles.get(state.userId)?.toIdentityUiState())
                    val safeAvatarUrl = identityState?.avatarUrl ?: profile.avatarUrl
                    val safeDisplayName = identityState?.displayName ?: profile.displayName
                    val safeUserId = identityState?.userId ?: profile.id

                    Card(
                        modifier = Modifier
                            .width(100.dp)
                            .height(150.dp)
                            .clickable { onNavigateToViewState(state.id) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val resolvedStoryResource = StoryMediaResolver.rememberResolvedStoryMediaResource(state)
                            var vcdnPoster by remember(state.id, state.mediaUrl, state.vcdnPosterUrl) {
                                mutableStateOf<String?>(state.vcdnPosterUrl ?: state.thumbnailUrl)
                            }
                            LaunchedEffect(state.id, state.mediaUrl, state.vcdnPosterUrl) {
                                val raw = state.mediaUrl ?: ""
                                if (state.vcdnPosterUrl.isNullOrBlank()) {
                                    if (raw.startsWith("vcdn://") && state.localVideoPath.isNullOrBlank()) {
                                        vcdnPoster = VcdnUrlResolver.resolvePoster(raw)
                                    }
                                } else {
                                    vcdnPoster = state.vcdnPosterUrl
                                }
                            }
                            val thumbnailModel = when {
                                vcdnPoster != null -> vcdnPoster
                                resolvedStoryResource is com.example.media.model.MediaResource.Local -> java.io.File(resolvedStoryResource.path)
                                resolvedStoryResource is com.example.media.model.MediaResource.Remote -> {
                                    val u = resolvedStoryResource.url
                                    if (u.contains(".m3u8", ignoreCase = true) || u.startsWith("vcdn://")) {
                                        state.thumbnailUrl?.takeIf { it.isNotBlank() } ?: safeAvatarUrl ?: ""
                                    } else u
                                }
                                else -> {
                                    val u = state.mediaUrl ?: ""
                                    if (u.startsWith("vcdn://")) {
                                        state.thumbnailUrl?.takeIf { it.isNotBlank() } ?: safeAvatarUrl ?: ""
                                    } else u.ifBlank { state.thumbnailUrl ?: safeAvatarUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=150&q=80" }
                                }
                            }
                            AsyncImage(
                                model = thumbnailModel,
                                contentDescription = safeDisplayName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(modifier = Modifier.fillMaxSize().background(brush = Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)), startY = 100f)))
                            PanaAvatar(avatarUrl = safeAvatarUrl, userId = safeUserId, size = 32.dp, borderWidth = 2.dp, borderColor = if (hasUnread) Color(0xFFB026FF) else Color.Gray.copy(alpha = 0.5f), placeholderName = safeDisplayName, modifier = Modifier.padding(8.dp))
                            Text(text = safeDisplayName?.take(15) ?: "", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
                        }
                    }
                }
            }
        }
    }
}
