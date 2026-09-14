package com.example.reels.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UserStateWithUser
import com.example.reels.engine.ReelPlayerPool
import com.example.reels.engine.ReelPreloadController
import com.example.ui.components.PanaAvatar
import com.example.ui.viewmodel.StatesUiState
import com.example.ui.viewmodel.StatesViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * TikTok-style Reels feed, rebuilt from scratch.
 *
 * - VerticalPager over the full reels list.
 * - Exactly [ReelPlayerPool.POOL_SIZE] ExoPlayers reused; each page binds to the
 *   pool via [ReelPlayerSurface].
 * - Adaptive preload (ReelPreloadController) prefetches the next reel bytes and
 *   resolves fresh VCDN URLs.
 * - Full TikTok overlay: author, caption, right rail (like/favorite/comment/share),
 *   double-tap to like, and back button.
 */
@Composable
fun ReelsFeedScreen(
    viewModel: StatesViewModel,
    initialStateId: String? = null,
    onBack: () -> Unit,
    onNavigateToUserProfile: ((String) -> Unit)? = null,
    onNavigateToHashtag: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current.applicationContext
    val reelsState by viewModel.reelsState.collectAsStateWithLifecycle()
    val reels: List<UserStateWithUser> = when (reelsState) {
        is StatesUiState.Success -> (reelsState as StatesUiState.Success).states
        else -> emptyList()
    }

    val pool = remember { ReelPlayerPool(context) }

    // Local scope for one-off UI actions (refresh spinner, etc.).
    val scope = rememberCoroutineScope()

    // TikTok overlay state (shared across pages).
    var muted by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(ReelFilterV2.EXPLORE) }
    var refreshing by remember { mutableStateOf(false) }
    var commentsReelId by remember { mutableStateOf<String?>(null) }
    var notInterestedReelId by remember { mutableStateOf<String?>(null) }

    val filteredReels = remember(reels, filter) {
        when (filter) {
            ReelFilterV2.EXPLORE -> reels
            ReelFilterV2.NEW -> reels.sortedByDescending { it.state.createdAt ?: "" }
            ReelFilterV2.MOST_VIEWED -> reels.sortedByDescending { it.state.viewsCount ?: 0 }
            ReelFilterV2.TRENDING -> reels.sortedByDescending {
                (it.state.likesCount ?: 0).toLong() * 3 +
                    (it.state.commentsCount ?: 0).toLong() * 4 +
                    (it.state.favoritesCount ?: 0).toLong() * 5 +
                    (it.state.sharesCount ?: 0).toLong() * 6 +
                    (it.state.viewsCount ?: 0).toLong()
            }
        }
    }

    val initialIndex = remember(filteredReels, initialStateId) {
        val idx = filteredReels.indexOfFirst { it.state.id == initialStateId }
        if (idx != -1) idx else 0
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { filteredReels.size }
    )

    // Periodic progress/play-state tick for the active page.
    LaunchedEffect(pool) { pool.startTimingUpdates() }

    var currentIndex by remember { mutableIntStateOf(initialIndex) }

    // Follow the pager's settled page. This is the SOURCE of truth for
    // play/pause: previously currentIndex was never updated, so swiping changed
    // the pager but no LaunchedEffect re-ran (all reels stayed frozen).
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { currentIndex = it }
    }

    // Register a view whenever the active page changes.
    LaunchedEffect(currentIndex) {
        if (currentIndex in filteredReels.indices) {
            viewModel.registerView(filteredReels[currentIndex].state.id)
        }
    }

    // Adaptive preload on page change.
    LaunchedEffect(currentIndex, filteredReels.size) {
        if (currentIndex in filteredReels.indices) {
            ReelPreloadController.adaptAndPrefetch(
                context = context,
                reels = filteredReels,
                currentIndex = currentIndex,
                swipeVelocity = 0.7f,
                avgDurationMs = null,
            )
        }
    }

    // When the page changes, play the page's reel and pause others. Protect the
    // current page + the ones likely to be shown next from eviction so fast
    // swipes never land on a page whose player was just discarded (black frame).
    LaunchedEffect(currentIndex) {
        if (currentIndex in filteredReels.indices) {
            val protect = buildSet {
                add(currentIndex)
                add(currentIndex - 1)
                add(currentIndex + 1)
            }.mapNotNull { filteredReels.getOrNull(it)?.state?.id }.toSet()
            pool.setProtectedReels(protect)

            for (i in filteredReels.indices) {
                if (i == currentIndex) pool.play(filteredReels[i].state.id, 1f) else pool.pause(filteredReels[i].state.id)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { pool.releaseAll() }
    }

    if (filteredReels.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Sin reels todavía", color = Color.White)
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val reel = filteredReels.getOrNull(page) ?: return@VerticalPager

            // Ensure this page's player is acquired (URL resolved off the main
            // thread) as soon as it is composed. `player` in the key re-runs the
            // effect if the player is later evicted, so the page re-acquires
            // instead of staying on a black frame.
            LaunchedEffect(reel.state.id, pool, pool.playerFor(reel.state.id)) {
                ensureAcquired(pool, context, reel)
            }

            // Observe the pool: the livePlayers snapshot map is Compose-state, so this
            // recomposes as soon as the pool assigns a player for this reel.
            val player = pool.playerFor(reel.state.id)

            ReelPlayerSurface(
                player = player,
                modifier = Modifier.fillMaxSize(),
            )

            ReelOverlayV2(
                reel = reel,
                pool = pool,
                player = player,
                muted = muted,
                commentsCount = reel.state.commentsCount ?: 0,
                onMute = { muted = !muted },
                onLike = { viewModel.toggleLike(reel.state.id, reel.state.likedByMe ?: false) },
                onFavorite = { viewModel.toggleFavorite(reel.state.id, reel.state.favoritedByMe ?: false) },
                onShare = {
                    viewModel.incrementShare(reel.state.id)
                    shareReelV2(context, reel)
                },
                onComments = { commentsReelId = reel.state.id },
                onProfile = { onNavigateToUserProfile?.invoke(reel.state.userId) },
                onHashtag = { onNavigateToHashtag?.invoke(it) },
                onNotInterested = { notInterestedReelId = reel.state.id },
                onCopyLink = { copyReelLinkV2(context, reel) },
            )
        }

        // Top "Explorar" pill header (back + Reels + filter chips + refresh).
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 10.dp),
            color = Color.Black.copy(alpha = 0.42f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        ) {
            Row(
                Modifier.height(46.dp).padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White)
                }
                Text("Reels", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(3.dp))
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReelFilterV2.values().forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text(option.label, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = Color.White,
                                selectedLabelColor = Color.White,
                                containerColor = Color.Transparent,
                                selectedContainerColor = Color.White.copy(alpha = 0.22f),
                            ),
                            border = null,
                        )
                    }
                }
                IconButton(
                    enabled = !refreshing,
                    onClick = {
                        refreshing = true
                        viewModel.loadActiveStates()
                        scope.launch { delay(700); refreshing = false }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Refresh, "Actualizar Reels", tint = Color.White)
                }
            }
        }
        if (refreshing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 50.dp)
                    .fillMaxWidth(0.86f)
            )
        }
    }

    // Comments sheet (custom Box, mirrors the old ReelsCommentsSheet).
    commentsReelId?.let { reelId ->
        val currentComments by viewModel.currentComments.collectAsStateWithLifecycle()
        ReelsCommentsSheetV2(
            viewModel = viewModel,
            reelId = reelId,
            comments = currentComments,
            onDismiss = { commentsReelId = null }
        )
    }

    // "No me interesa" → hide the reel from the local feed.
    notInterestedReelId?.let { reelId ->
        LaunchedEffect(reelId) {
            viewModel.deleteStateForMe(reelId) { notInterestedReelId = null }
        }
    }
}

/** Resolves the stable URL for [reel] off the main thread and acquires it in the pool. */
private fun ensureAcquired(
    pool: ReelPlayerPool,
    context: android.content.Context,
    reel: UserStateWithUser,
) {
    if (pool.playerFor(reel.state.id) != null) return
    val stable = reel.state.vcdnVideoId?.let { "vcdn://$it" } ?: reel.state.mediaUrl
    if (stable.isNullOrBlank()) return
    pool.acquireAsync(reel.state.id, stable)
}

private enum class ReelFilterV2(val label: String) {
    EXPLORE("Explorar"), NEW("Nuevos"), TRENDING("Tendencias"), MOST_VIEWED("Más vistos")
}

@Composable
private fun ReelOverlayV2(
    reel: UserStateWithUser,
    pool: ReelPlayerPool,
    player: androidx.media3.common.Player?,
    muted: Boolean,
    commentsCount: Int,
    onMute: () -> Unit,
    onLike: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onComments: () -> Unit,
    onProfile: () -> Unit,
    onHashtag: (String) -> Unit,
    onNotInterested: () -> Unit,
    onCopyLink: () -> Unit,
) {
    val state = reel.state
    val profile = reel.profile
    val overlayScope = rememberCoroutineScope()
    var liked by remember(state.id) { mutableStateOf(state.likedByMe ?: false) }
    var favorited by remember(state.id) { mutableStateOf(state.favoritedByMe ?: false) }
    var localLikes by remember(state.id) { mutableIntStateOf(state.likesCount ?: 0) }
    var localFavorites by remember(state.id) { mutableIntStateOf(state.favoritesCount ?: 0) }
    var localShares by remember(state.id) { mutableIntStateOf(state.sharesCount ?: 0) }
    var menuExpanded by remember { mutableStateOf(false) }
    var paused by remember(state.id) { mutableStateOf(false) }
    var showHeartBurst by remember(state.id) { mutableStateOf(false) }

    val timing = pool.timingFor(state.id)

    // Keep player volume in sync with the global mute toggle.
    LaunchedEffect(player, muted, state.id) {
        player?.volume = if (muted) 0f else 1f
    }

    // Reset paused state when landing on this page again.
    LaunchedEffect(state.id, player) {
        if (player != null) paused = !player.playWhenReady && player.playbackState == androidx.media3.common.Player.STATE_READY
    }

    fun togglePlayPause() {
        if (player == null) return
        val next = !paused
        paused = next
        pool.setUserPaused(state.id, next)
    }

    fun doubleTapLike() {
        showHeartBurst = true
        if (!liked) {
            liked = true
            localLikes += 1
        }
        onLike()
        overlayScope.launch { delay(620); showHeartBurst = false }
    }

    Box(modifier = Modifier.fillMaxSize().zIndex(1f)) {
        // Full-surface tap/double-tap gestures: double-tap like, tap play/pause.
        @OptIn(ExperimentalFoundationApi::class)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.id) {
                    detectTapGestures(
                        onDoubleTap = { if (player != null) doubleTapLike() },
                        onTap = { if (player != null) togglePlayPause() }
                    )
                }
        )

        // Center heart burst on double-tap.
        androidx.compose.animation.AnimatedVisibility(
            visible = showHeartBurst,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 48.dp),
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.35f),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 1.45f),
        ) {
            Icon(
                Icons.Filled.Favorite,
                "Me gusta",
                tint = Color(0xFFFF2D55),
                modifier = Modifier.size(150.dp)
            )
        }
        LaunchedEffect(showHeartBurst) {
            if (showHeartBurst) { delay(620); showHeartBurst = false }
        }

        // Center play/pause pill.
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .size(56.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = if (paused) 0.74f else 0.28f),
        ) {
            IconButton(onClick = { if (player != null) togglePlayPause() }) {
                Icon(
                    if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    "Reproducción",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        // Center-right action rail.
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReelActionButtonV2(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, compactCountV2(localLikes), liked) {
                val next = !liked
                liked = next
                localLikes = (localLikes + if (next) 1 else -1).coerceAtLeast(0)
                onLike()
            }
            ReelActionButtonV2(Icons.Filled.ChatBubbleOutline, compactCountV2(commentsCount), false, onComments)
            ReelActionButtonV2(Icons.Filled.Star, compactCountV2(localFavorites), favorited) {
                val next = !favorited
                favorited = next
                localFavorites = (localFavorites + if (next) 1 else -1).coerceAtLeast(0)
                onFavorite()
            }
            ReelActionButtonV2(Icons.Filled.Share, compactCountV2(localShares), false) {
                localShares += 1
                onShare()
            }
            ReelActionButtonV2(if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, "", false, onMute)
            Box {
                ReelActionButtonV2(Icons.Filled.MoreVert, "", false) { menuExpanded = true }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Compartir") }, onClick = { menuExpanded = false; onShare() })
                    DropdownMenuItem(text = { Text("Copiar enlace") }, onClick = { menuExpanded = false; onCopyLink() })
                    DropdownMenuItem(text = { Text("No me interesa") }, onClick = { menuExpanded = false; onNotInterested() })
                    DropdownMenuItem(text = { Text("Ver perfil") }, onClick = { menuExpanded = false; onProfile() })
                }
            }
        }

        // Bottom-left profile + caption + hashtags.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 90.dp, bottom = 34.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PanaAvatar(
                    avatarUrl = profile.avatarUrl,
                    userId = state.userId,
                    placeholderName = profile.displayName,
                    size = 40.dp,
                    modifier = Modifier.clickable(onClick = onProfile)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    "@${profile.displayName?.ifBlank { "pana" } ?: "pana"}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onProfile)
                )
            }
            state.caption?.takeIf(String::isNotBlank)?.let {
                Spacer(Modifier.height(5.dp))
                Text(it, color = Color.White, maxLines = 3, style = MaterialTheme.typography.bodyMedium)
            }
            val hashtags = Regex("#[A-Za-z0-9_ÁÉÍÓÚáéíóúÑñ]+").findAll(state.caption.orEmpty()).map { it.value }.distinct().toList()
            if (hashtags.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    hashtags.take(4).forEach { tag ->
                        Text(
                            tag,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { onHashtag(tag.removePrefix("#")) }
                        )
                    }
                }
            }
        }

        // Bottom progress bar + time.
        if (timing != null && timing.durationMs > 0L) {
            val fraction = (timing.positionMs.toFloat() / timing.durationMs.toFloat()).coerceIn(0f, 1f)
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTimeV2(timing.positionMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    Text(formatTimeV2(timing.durationMs), color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelSmall)
                }
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f)
                )
            }
        }
    }
}

@Composable
private fun ReelActionButtonV2(icon: androidx.compose.ui.graphics.vector.ImageVector, count: String, selected: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) Color.Red else Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
        if (count.isNotBlank()) Text(count, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

fun compactCountV2(value: Int): String = when {
    value >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", value / 1_000_000f).removeSuffix(".0M")
    value >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", value / 1_000f).removeSuffix(".0K")
    else -> value.toString()
}

fun formatTimeV2(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L).toInt()
    return String.format(java.util.Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

private fun reelShareLink(reel: UserStateWithUser): String = "panalink://reel/${reel.state.id}"

fun shareReelV2(context: android.content.Context, reel: UserStateWithUser) {
    val link = reelShareLink(reel)
    val caption = reel.state.caption?.takeIf { it.isNotBlank() }
    val text = if (caption != null) "$caption\n$link" else link
    context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }, "Compartir Reel"))
}

fun copyReelLinkV2(context: android.content.Context, reel: UserStateWithUser) {
    val link = reelShareLink(reel)
    (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)
        ?.setPrimaryClip(android.content.ClipData.newPlainText("Enlace del Reel", link))
    android.widget.Toast.makeText(context, "Enlace copiado", android.widget.Toast.LENGTH_SHORT).show()
}

@Composable
private fun ReelsCommentsSheetV2(
    viewModel: StatesViewModel,
    reelId: String,
    comments: List<com.example.data.model.Comment>,
    onDismiss: () -> Unit,
) {
    var text by remember(reelId) { mutableStateOf("") }

    LaunchedEffect(reelId) { viewModel.loadComments(reelId) }

    Box(Modifier.fillMaxSize()) {
        // Scrim: tap outside dismisses.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
        )
        // Bottom sheet.
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp),
                    color = Color.Gray.copy(alpha = 0.5f),
                    shape = CircleShape
                ) {}
                Text("Comentarios", fontWeight = FontWeight.Bold)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(comments, key = { it.id }) { comment ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(comment.authorName, fontWeight = FontWeight.SemiBold)
                                Text(comment.text)
                            }
                            IconButton(onClick = { viewModel.deleteComment(reelId, comment.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Escribe un comentario…") },
                    singleLine = false,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                    Spacer(Modifier.padding(4.dp))
                    Button(
                        enabled = text.isNotBlank(),
                        onClick = {
                            viewModel.addComment(reelId, text.trim())
                            text = ""
                        },
                    ) { Text("Enviar") }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}