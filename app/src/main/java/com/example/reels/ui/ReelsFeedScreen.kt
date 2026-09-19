package com.example.reels.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search

import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UserStateWithUser
import com.example.data.repository.ProfilesRepository
import com.example.data.repository.PublicProfileResolver
import com.example.data.supabase.SupabaseClient
import com.example.reels.engine.ReelPlayerPool
import com.example.reels.engine.ReelPreloadController
import com.example.ui.components.PanaAvatar
import com.example.ui.screen.parseStateMetadata
import com.example.ui.viewmodel.StatesUiState
import com.example.ui.viewmodel.StatesViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val MIN_REEL_SCALE = 1f
private const val MAX_REEL_SCALE = 4f

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
    onSearchReels: () -> Unit = {},
    onNavigateToUserProfile: ((String) -> Unit)? = null,
    onNavigateToHashtag: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current.applicationContext
    val reelsState by viewModel.reelsState.collectAsStateWithLifecycle()
    val reelsTimeline by viewModel.reelsTimeline.collectAsStateWithLifecycle()

    // TikTok overlay state (shared across pages).
    var muted by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(ReelFilterV2.EXPLORE) }
    var refreshing by remember { mutableStateOf(false) }

    val localReels: List<UserStateWithUser> = when (reelsState) {
        is StatesUiState.Success -> (reelsState as StatesUiState.Success).states
        else -> emptyList()
    }
    // The timeline tabs (Tendencias / Más vistos) render the server-ordered
    // result from Supabase (E2E). Fallback tabs / first load use the Room list.
    val reels: List<UserStateWithUser> = if (filter == ReelFilterV2.TRENDING || filter == ReelFilterV2.MOST_VIEWED) {
        reelsTimeline.ifEmpty { localReels }
    } else {
        localReels
    }

    val pool = remember { ReelPlayerPool(context) }

    // System bars stay VISIBLE in the feed: the user always sees the status bar
    // (clock, notifications, signal, battery) and the native navigation buttons.
    // The window is NOT edge-to-edge here: decorFitsSystemWindows stays true, so the
    // system already reserves the bar space. The overlay must therefore NOT apply
    // window insets again — doing so floated the pill and the progress bar a whole
    // status-bar / nav-bar height away from the screen edges.
    val feedActivity = LocalContext.current as? android.app.Activity
    DisposableEffect(feedActivity) {
        val window = feedActivity?.window
        if (window != null) {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            controller.show(androidx.core.view.WindowInsetsCompat.Type.statusBars() or
                androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            // Dark video behind the bars: keep the system icons light (white).
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
        onDispose {
            if (window != null) {
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
            }
        }
    }

    // Local scope for one-off UI actions (refresh spinner, etc.).
    val scope = rememberCoroutineScope()

    var commentsReelId by remember { mutableStateOf<String?>(null) }
    var heartReelId by remember { mutableStateOf<String?>(null) }
    var notInterestedReelId by remember { mutableStateOf<String?>(null) }
    var deleteReelId by remember { mutableStateOf<String?>(null) }
    // When the refresh button is pressed we scroll the feed back to the top.
    var refreshKey by remember { mutableIntStateOf(0) }
    // Per-reel user pause toggle (tap center toggles play/pause).
    val userPausedIds = remember { mutableStateMapOf<String, Boolean>() }

    val filteredReels = remember(reels, filter) {
        when (filter) {
            // E2E tabs: the list already carries the server-returned ordering.
            ReelFilterV2.TRENDING, ReelFilterV2.MOST_VIEWED -> reels
            ReelFilterV2.EXPLORE -> reels
            ReelFilterV2.NEW -> reels.sortedByDescending { it.state.createdAt ?: "" }
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

    // Land on the tapped reel. `initialPage` only applies at first composition, so
    // opening the feed from a card before its list loaded left the pager on page 0
    // (the tapped video was never shown). Once the list arrives, jump to the target.
    // One-shot per target: a later background refresh must not yank the user back.
    var landedForId by remember(initialStateId) { mutableStateOf<String?>(null) }
    LaunchedEffect(filteredReels, initialStateId) {
        val targetId = initialStateId ?: return@LaunchedEffect
        if (landedForId == targetId) return@LaunchedEffect
        val target = filteredReels.indexOfFirst { it.state.id == targetId }
        if (target >= 0) {
            landedForId = targetId
            if (pagerState.currentPage != target) pagerState.scrollToPage(target)
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

    // When the refresh button is pressed, scroll the feed back to the top so the
    // newly fetched reels are immediately visible.
    LaunchedEffect(refreshKey) {
        if (refreshKey > 0 && filteredReels.isNotEmpty()) {
            pagerState.scrollToPage(0)
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

            // Per-page overlay drawn INSIDE the pager item. Now that the video
            // surface is a TextureView (not a SurfaceView), Compose siblings can
            // draw above it without punch-through, which lets the tap/double-tap
            // layer and the content overlay live inside the item вҖ” so the content
            // scrolls with the video and the pager keeps receiving vertical drags.
            Box(modifier = Modifier.fillMaxSize()) {
                // Pinch-to-zoom: the texture keeps its authored size by default;
                // while pinching, scale it up to 4x. Releasing springs it back to
                // 1f (the user asked "zoom and on release it returns to normal").
                val pageScale = remember(reel.state.id) { mutableFloatStateOf(1f) }
                val pageScope = rememberCoroutineScope()
                ReelPlayerSurface(
                    player = player,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = pageScale.floatValue
                            scaleY = pageScale.floatValue
                        },
                )

                // Double-tap → like (big heart). Single tap → play/pause.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(reel.state.id) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (pageScale.floatValue <= 1.05f) {
                                        heartReelId = reel.state.id
                                        viewModel.toggleLike(reel.state.id, reel.state.likedByMe ?: false)
                                    }
                                },
                                onTap = {
                                    if (pageScale.floatValue <= 1.05f) {
                                        val next = !(userPausedIds[reel.state.id] ?: false)
                                        userPausedIds[reel.state.id] = next
                                        pool.setUserPaused(reel.state.id, next)
                                    }
                                }
                            )
                        }
                        .pointerInput(reel.state.id) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var consumed = false
                                var currentScale = pageScale.floatValue
                                do {
                                    val event = awaitPointerEvent()
                                    if (event.changes.size >= 2) {
                                        val newZoom = event.calculateZoom()
                                        if (kotlin.math.abs(newZoom - 1f) > 0.02f) {
                                            consumed = true
                                            event.changes.forEach { it.consume() }
                                        }
                                        currentScale = (currentScale * newZoom).coerceIn(MIN_REEL_SCALE, MAX_REEL_SCALE)
                                        pageScale.floatValue = currentScale
                                    }
                                } while (event.changes.any { it.pressed })
                                if (consumed) {
                                    pageScope.launch {
                                        animate(
                                            initialValue = currentScale,
                                            targetValue = 1f,
                                            animationSpec = spring(dampingRatio = 0.6f, stiffness = 380f),
                                        ) { value, _ ->
                                            pageScale.floatValue = value
                                        }
                                    }
                                }
                            }
                        }
                )

                val isThisPaused = userPausedIds[reel.state.id] ?: false

                // Reel content (rail + caption + progress + play/pause pill),
                // drawn per page so it stays glued to its video.
                ReelFeedOverlay(
                    reel = reel,
                    pool = pool,
                    player = player,
                    paused = isThisPaused,
                    muted = muted,
                    commentsCount = reel.state.commentsCount ?: 0,
                    onLike = {
                        heartReelId = reel.state.id
                        viewModel.toggleLike(reel.state.id, reel.state.likedByMe ?: false)
                    },
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
                    onDelete = { deleteReelId = reel.state.id },
                    onMute = { muted = !muted },
                    onTogglePlayPause = {
                        val next = !(userPausedIds[reel.state.id] ?: false)
                        userPausedIds[reel.state.id] = next
                        pool.setUserPaused(reel.state.id, next)
                    },
                )
            }
        }

        // ------------------------------------------------------------------
        // WHOLE-FEED OVERLAY вҖ” the header pill, heart animation and per-reel
        // content (rail/caption/progress) used to live here ABOVE the pager.
        // That feed-level tap layer blocked the pager's vertical drags, so the
        // feed could never scroll. ReelsFeedOverlay is now rendered inside each
        // pager item; only the shared bits (header, heart, loading) remain here.
        // ------------------------------------------------------------------

        // The system already reserves the status bar area (decorFitsSystemWindows is
        // true below), so the pill only needs a small gap under it.
        val headerTopInset = 8.dp

        // Big heart on double-tap (like). Re-animates on each new reel id set.
        heartReelId?.let { heartId ->
            androidx.compose.animation.AnimatedVisibility(
                visible = true,
                modifier = Modifier.align(Alignment.Center),
                enter = fadeIn() + scaleIn(initialScale = 0.3f),
                exit = fadeOut() + scaleOut(targetScale = 1.5f),
            ) {
                Icon(
                    Icons.Filled.Favorite,
                    "Me gusta",
                    tint = Color(0xFFFF2D55),
                    modifier = Modifier.size(150.dp)
                )
            }
            LaunchedEffect(heartId) {
                delay(600)
                if (heartReelId == heartId) heartReelId = null
            }
        }

        // Floating glassmorphism pill: back + "Reels" + the timeline filter tabs
        // (Explorar / Nuevos / Tendencias / Más vistos). Selecting a tab triggers a
        // REAL remote query (loadReelsTimeline with the tab's PostgREST order), so
        // navigation is E2E against the database, not just a local sort.
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = headerTopInset, start = 10.dp, end = 10.dp),
            shape = RoundedCornerShape(30.dp),
            color = Color.Transparent,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF0B1620).copy(alpha = 0.90f),
                                Color(0xFF0B1620).copy(alpha = 0.74f)
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(30.dp))
                    .height(44.dp)
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White)
                }
                IconButton(
                    onClick = onSearchReels,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Search, "Buscar", tint = Color.White)
                }
                Text(
                    "Reels",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(end = 2.dp)
                )
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReelFilterV2.values().forEach { option ->
                        val selected = filter == option
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (selected) Color.White.copy(alpha = 0.28f) else Color.Transparent
                                )
                                .clickable {
                                    if (!selected) {
                                        filter = option
                                        refreshing = true
                                        viewModel.loadReelsTimeline(option.orderBy) {
                                            refreshing = false
                                            refreshKey += 1
                                            scope.launch { pagerState.scrollToPage(0) }
                                        }
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                option.label,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
                }
                IconButton(
                    enabled = !refreshing,
                    onClick = {
                        refreshing = true
                        viewModel.refreshReels {
                            refreshing = false
                            refreshKey += 1
                            scope.launch { pagerState.scrollToPage(0) }
                        }
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Refresh, "Actualizar reels", tint = Color.White)
                }
            }
        }

        if (refreshing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = headerTopInset + 56.dp)
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

    // Author-only delete: confirm, then remove the video remotely + locally
    // (same wiring as the old player: viewModel.deleteState(id) { toast }).
    deleteReelId?.let { reelId ->
        AlertDialog(
            onDismissRequest = { deleteReelId = null },
            title = { Text("Eliminar vídeo") },
            text = { Text("¿Seguro que quieres eliminar este vídeo? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteReelId = null
                    viewModel.deleteState(reelId) {
                        android.widget.Toast.makeText(context, "Publicación eliminada", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Eliminar", color = Color(0xFFFF5252)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteReelId = null }) { Text("Cancelar") }
            },
        )
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

private enum class ReelFilterV2(
    val label: String,
    val orderBy: String? = null,
) {
    EXPLORE("Explorar", null),
    NEW("Nuevos", null),
    TRENDING("Tendencias", "likes_count.desc.nullslast,shares_count.desc.nullslast,comments_count.desc.nullslast,created_at.desc"),
    MOST_VIEWED("Más vistos", "views_count.desc.nullslast,created_at.desc")
}

@Composable
private fun ReelFeedOverlay(
    reel: UserStateWithUser,
    pool: ReelPlayerPool,
    player: androidx.media3.common.Player?,
    paused: Boolean,
    muted: Boolean,
    commentsCount: Int,
    onLike: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onComments: () -> Unit,
    onProfile: () -> Unit,
    onHashtag: (String) -> Unit,
    onNotInterested: () -> Unit,
    onCopyLink: () -> Unit,
    onDelete: () -> Unit,
    onMute: () -> Unit,
    onTogglePlayPause: () -> Unit,
) {
    val state = reel.state
    val profile = reel.profile
    val overlayScope = rememberCoroutineScope()
    val profilesRepo = remember { ProfilesRepository() }
    val currentUid = SupabaseClient.currentUser?.id
    val isOwner = !currentUid.isNullOrBlank() && state.userId == currentUid
    var isFollowing by remember(state.userId) { mutableStateOf(false) }

    // Track local optimistic values so the toggles feel instant.
    var liked by remember(state.id) { mutableStateOf(state.likedByMe ?: false) }
    var favorited by remember(state.id) { mutableStateOf(state.favoritedByMe ?: false) }
    var localLikes by remember(state.id) { mutableIntStateOf(state.likesCount ?: 0) }
    var localFavorites by remember(state.id) { mutableIntStateOf(state.favoritesCount ?: 0) }
    var localShares by remember(state.id) { mutableIntStateOf(state.sharesCount ?: 0) }
    var menuExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(state.userId) {
        if (!currentUid.isNullOrBlank() && state.userId.isNotBlank()) {
            profilesRepo.isFollowing(currentUid, state.userId).onSuccess { isFollowing = it }
        }
    }

    val timing = pool.timingFor(state.id)

    LaunchedEffect(player, muted, state.id) {
        player?.volume = if (muted) 0f else 1f
    }

    // Center play/pause affordance: it stays visible while paused but only
    // flashes briefly when playback resumes, then fades out (TikTok behaviour).
    var showCenterIcon by remember(state.id) { mutableStateOf(false) }
    LaunchedEffect(paused, state.id) {
        if (paused) {
            showCenterIcon = true
        } else if (showCenterIcon) {
            delay(700)
            showCenterIcon = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = showCenterIcon,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn() + scaleIn(initialScale = 0.7f),
            exit = fadeOut() + scaleOut(targetScale = 0.7f),
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = if (paused) 0.74f else 0.28f),
            ) {
                IconButton(onClick = onTogglePlayPause) {
                    Icon(
                        if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        "ReproducciГіn",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }

        // Bottom-right action rail (like/comment/favorite/share/mute/menu).
        // Rendered inside the pager item so it hugs the bottom edge tightly,
        // right above the progress/time block, buttons tight together.
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = 50.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // TikTok avatar in the rail: circular photo with a follow "+" /
            // following "✓" pill sitting on its bottom edge.
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onProfile),
                contentAlignment = Alignment.Center
            ) {
                PanaAvatar(
                    avatarUrl = profile.avatarUrl,
                    userId = state.userId,
                    placeholderName = profile.displayName,
                    size = 44.dp,
                    borderWidth = 1.5.dp,
                )
                if (!currentUid.isNullOrBlank() && state.userId.isNotBlank() && state.userId != currentUid) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 11.dp)
                            .size(21.dp)
                            .clip(CircleShape)
                            .background(if (isFollowing) Color(0xFF2B2B2B) else Color(0xFFFF2B54))
                            .border(1.5.dp, Color.Black, CircleShape)
                            .clickable {
                                if (currentUid.isNullOrBlank()) return@clickable
                                overlayScope.launch {
                                    if (isFollowing) {
                                        profilesRepo.unfollowUser(currentUid, state.userId).onSuccess { isFollowing = false }
                                    } else {
                                        profilesRepo.followUser(currentUid, state.userId).onSuccess { isFollowing = true }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isFollowing) Icons.Filled.Check else Icons.Filled.Add,
                            contentDescription = if (isFollowing) "Dejar de seguir" else "Seguir",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
            ReelActionButtonV2(
                icon = if (liked) Icons.Filled.Favorite else Icons.Rounded.FavoriteBorder,
                count = compactCountV2(localLikes),
                selected = liked,
                selectedColor = Color(0xFFFF2B54),
                popOnSelect = true,
            ) {
                val next = !liked
                liked = next
                localLikes = (localLikes + if (next) 1 else -1).coerceAtLeast(0)
                onLike()
            }
            ReelActionButtonV2(
                icon = Icons.Rounded.ChatBubbleOutline,
                count = compactCountV2(commentsCount),
            ) { onComments() }
            ReelActionButtonV2(
                icon = if (favorited) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                count = compactCountV2(localFavorites),
                selected = favorited,
                selectedColor = Color(0xFFF9C74F),
                popOnSelect = true,
            ) {
                val next = !favorited
                favorited = next
                localFavorites = (localFavorites + if (next) 1 else -1).coerceAtLeast(0)
                onFavorite()
            }
            ReelActionButtonV2(
                icon = Icons.AutoMirrored.Rounded.Send,
                count = compactCountV2(localShares),
            ) {
                localShares += 1
                onShare()
            }
            ReelActionButtonV2(
                icon = if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
            ) { onMute() }
            Box {
                ReelActionButtonV2(icon = Icons.Rounded.MoreHoriz) { menuExpanded = true }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Compartir") }, onClick = { menuExpanded = false; onShare() })
                    DropdownMenuItem(text = { Text("Copiar enlace") }, onClick = { menuExpanded = false; onCopyLink() })
                    DropdownMenuItem(text = { Text("No me interesa") }, onClick = { menuExpanded = false; onNotInterested() })
                    DropdownMenuItem(text = { Text("Ver perfil") }, onClick = { menuExpanded = false; onProfile() })
                    if (isOwner) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                        DropdownMenuItem(
                            text = { Text("Eliminar vídeo", color = Color(0xFFFF5252)) },
                            onClick = { menuExpanded = false; onDelete() },
                        )
                    }
                }
            }
        }

        // Bottom-left column: author name (semi-bold) → description ("Ver más"
        // expands/collapses with animateContentSize) → one line of compound
        // hashtags (array from Supabase) rendered as a single clickable blue block.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 90.dp, bottom = 50.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Author row: avatar + display name + follow pill.
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
                    profile.displayName?.ifBlank { "pana" } ?: "pana",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).clickable(onClick = onProfile)
                )
                if (!currentUid.isNullOrBlank() && state.userId.isNotBlank() && state.userId != currentUid) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isFollowing) "Siguiendo" else "Seguir",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isFollowing) Color.White.copy(alpha = 0.16f) else Color(0xFFFF2B54))
                            .clickable {
                                if (currentUid.isNullOrBlank()) return@clickable
                                overlayScope.launch {
                                    if (isFollowing) {
                                        profilesRepo.unfollowUser(currentUid, state.userId).onSuccess { isFollowing = false }
                                    } else {
                                        profilesRepo.followUser(currentUid, state.userId).onSuccess { isFollowing = true }
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
            // Technical editor tags ([Transition: ...], [CoverFrame: ...], …) are
            // metadata and are stripped. Hashtags are pulled from the dedicated
            // Supabase array when present, else parsed from the caption, and shown
            // on their own line so the description contains only plain text.
            val cleanCaption = remember(state.caption) {
                parseStateMetadata(state.caption).baseCaption
                    .replace(Regex("#[^\\s]+"), "").trim()
            }
            if (cleanCaption.isNotBlank()) {
                var expanded by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy))
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        cleanCaption,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (cleanCaption.length > 60 || cleanCaption.lines().size > 2) {
                        Text(
                            text = if (expanded) "Ver menos" else "Ver más",
                            color = Color(0xFF7FB8FF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { expanded = !expanded }
                                .padding(top = 2.dp),
                        )
                    }
                }
            }
            // Compound hashtags line: the whole multi-word block is one clickable
            // unit (blue), like a single chip, without breaking at spaces.
            val compoundTags = remember(state.hashtags, state.caption) {
                val stored = state.hashtags?.filter { it.isNotBlank() }
                if (stored.isNullOrEmpty()) {
                    Regex("#[^\\s]+").findAll(parseStateMetadata(state.caption).baseCaption)
                        .map { it.value }
                        .filter { it.length > 1 }
                        .toList()
                } else {
                    stored.map { if (it.startsWith("#")) it else "#$it" }
                }
            }
            if (compoundTags.isNotEmpty()) {
                val tagsAnnotated = remember(compoundTags) {
                    buildAnnotatedString {
                        compoundTags.forEachIndexed { i, tag ->
                            if (i > 0) append("  ")
                            pushStringAnnotation(tag = "HASHTAG", annotation = tag)
                            withStyle(SpanStyle(color = Color(0xFF7FB8FF), fontWeight = FontWeight.Bold)) {
                                append(tag)
                            }
                            pop()
                        }
                    }
                }
                Text(
                    text = tagsAnnotated,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .clickable {
                            val first = compoundTags.firstOrNull() ?: return@clickable
                            onHashtag(first.removePrefix("#"))
                        }
                )
            }
        }

        // Bottom progress bar + time (m:ss). The bar is seekable: drag or tap it
        // to scrub; the target time previews optimistically while dragging and the
        // seek is committed to the ExoPlayer on release.
        if (timing != null && timing.durationMs > 0L) {
            ReelProgressBar(
                positionMs = timing.positionMs,
                durationMs = timing.durationMs,
                onSeek = { target -> pool.playerFor(state.id)?.seekTo(target.coerceAtLeast(0L)) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
            )
        }
    }
}

/**
 * TikTok-style seekable progress bar: elapsed/total time plus a draggable track.
 * Dragging (or tapping) previews the target time without touching the player; the
 * seek is applied once on release.
 */
@Composable
private fun ReelProgressBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val baseFraction = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val fraction = dragFraction ?: baseFraction
    val shownPosition = if (dragFraction != null && durationMs > 0L) (fraction * durationMs).toLong() else positionMs

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTimeV2(shownPosition), color = Color.White, style = MaterialTheme.typography.labelSmall)
            Text(formatTimeV2(durationMs), color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelSmall)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .pointerInput(durationMs) {
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var target = (down.position.x / width).coerceIn(0f, 1f)
                        dragFraction = target
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            target = (change.position.x / width).coerceIn(0f, 1f)
                            dragFraction = target
                            change.consume()
                            if (!change.pressed) break
                        }
                        dragFraction = null
                        onSeek((target * durationMs).toLong())
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
            Box(Modifier.fillMaxWidth(fraction).height(3.dp).clip(CircleShape).background(Color.White))
            Box(Modifier.fillMaxWidth(fraction), contentAlignment = Alignment.CenterEnd) {
                Box(
                    Modifier
                        .size(if (dragFraction != null) 14.dp else 8.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

@Composable
private fun ReelActionButtonV2(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: String? = null,
    selected: Boolean = false,
    selectedColor: Color = Color(0xFFF9C74F),
    popOnSelect: Boolean = false,
    onClick: () -> Unit,
) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(selected, popOnSelect) {
        if (popOnSelect && selected) {
            scale.snapTo(1.25f)
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(42.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) selectedColor else Color.White,
                modifier = Modifier
                    .size(29.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    },
            )
        }
        if (!count.isNullOrBlank()) {
            Text(
                count,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.7f),
                        offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                        blurRadius = 3f
                    )
                ),
            )
        }
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
    var commentText by remember(reelId) { mutableStateOf("") }
    var replyingTo by remember(reelId) { mutableStateOf<com.example.data.model.Comment?>(null) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reelId) { viewModel.loadComments(reelId) }

    val structured = remember(comments) {
        val parents = comments.filter { it.parentCommentId == null }
        val children = comments.filter { it.parentCommentId != null }.groupBy { it.parentCommentId }
        buildList {
            parents.forEach { parent ->
                add(parent)
                children[parent.id]?.filter { it.deletedAt == null }?.forEach { add(it) }
            }
        }
    }

    // TikTok-style: semi-transparent dark panel over the video without stopping it.
    Box(Modifier.fillMaxSize()) {
        // Scrim: tap outside dismisses.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
        )
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.72f)
                    .background(color = Color(0xF2101D24), shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .clickable(enabled = true, onClick = {})
                    .imePadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 8.dp, bottom = 12.dp)
                            .size(width = 40.dp, height = 4.dp)
                            .background(Color.Gray.copy(alpha = 0.5f), CircleShape)
                    )

                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp).padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Comentarios (${comments.size})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White)
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // Scrollable comment list (threaded replies inline).
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(structured, key = { it.id }) { comment ->
                            val isReply = comment.parentCommentId != null
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = if (isReply) 48.dp else 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                if (isReply) {
                                    Text(
                                        text = "в””в”Җ ",
                                        color = Color.White.copy(alpha = 0.3f),
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(end = 4.dp, top = 2.dp)
                                    )
                                }
                                Box(modifier = Modifier.clickable { }) {
                                    PanaAvatar(
                                        avatarUrl = comment.avatarUrl,
                                        userId = comment.userId,
                                        size = if (isReply) 28.dp else 36.dp,
                                        borderWidth = 0.dp,
                                        placeholderName = comment.authorName
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = if (comment.deletedAt != null) "Eliminado"
                                                else PublicProfileResolver.formatForUi(comment.authorName, "Pana"),
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = if (isReply) 12.sp else 13.sp,
                                            modifier = Modifier.clickable { }
                                        )
                                        val timeStr = remember(comment.createdAt) {
                                            try {
                                                val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                                                parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                                val date = parser.parse(comment.createdAt)
                                                val diff = System.currentTimeMillis() - (date?.time ?: System.currentTimeMillis())
                                                val minutes = (diff / 60000).toInt()
                                                when {
                                                    minutes < 1 -> "ahora"
                                                    minutes < 60 -> "hace ${minutes}m"
                                                    minutes < 1440 -> "hace ${minutes / 60}h"
                                                    else -> "hace ${minutes / 1440}d"
                                                }
                                            } catch (e: Exception) {
                                                "hace poco"
                                            }
                                        }
                                        Text(
                                            text = timeStr,
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 11.sp
                                        )
                                        if (comment.deletedAt == null) {
                                            Text(
                                                text = "вҖў Responder",
                                                color = Color(0xFF25D366),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier
                                                    .clickable {
                                                        replyingTo = if (isReply) {
                                                            comments.find { it.id == comment.parentCommentId } ?: comment
                                                        } else {
                                                            comment
                                                        }
                                                        scope.launch { focusRequester.requestFocus() }
                                                    }
                                                    .padding(horizontal = 4.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (comment.deletedAt != null) "Este comentario ha sido eliminado" else comment.text,
                                        color = if (comment.deletedAt != null) Color.White.copy(alpha = 0.4f) else Color.White,
                                        fontSize = if (isReply) 13.sp else 14.sp,
                                        fontStyle = if (comment.deletedAt != null) FontStyle.Italic else FontStyle.Normal
                                    )
                                    if (comment.deletedAt != null) {
                                        IconButton(onClick = { viewModel.deleteComment(reelId, comment.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = Color.White.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // Replying banner
                    val currentReplyingTo = replyingTo
                    if (currentReplyingTo != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E2D35))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Respondiendo a @${PublicProfileResolver.formatForUi(currentReplyingTo.authorName, "Pana")}",
                                color = Color(0xFF25D366),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(
                                onClick = { replyingTo = null },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Cancelar respuesta",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // Fixed input (rounded, green send button).
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = commentText,
                            onValueChange = { commentText = it },
                            placeholder = {
                                val hint = if (replyingTo != null) "Escribe tu respuesta..." else "Escribe tu comentario de pana..."
                                Text(hint, color = Color.Gray)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            maxLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF1E2D35),
                                unfocusedContainerColor = Color(0xFF1E2D35),
                                focusedBorderColor = Color(0xFF25D366),
                                unfocusedBorderColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                        IconButton(
                            onClick = {
                                if (commentText.isNotBlank()) {
                                    viewModel.addComment(reelId, commentText, parentId = replyingTo?.id)
                                    commentText = ""
                                    replyingTo = null
                                }
                            },
                            modifier = Modifier
                                .background(Color(0xFF25D366), CircleShape)
                                .size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Enviar",
                                tint = Color(0xFF101D24),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}