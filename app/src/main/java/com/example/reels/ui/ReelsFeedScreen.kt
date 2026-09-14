package com.example.reels.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.UserStateWithUser
import com.example.reels.engine.ReelPlayerPool
import com.example.reels.engine.ReelPreloadController
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

    val initialIndex = remember(reels, initialStateId) {
        val idx = reels.indexOfFirst { it.state.id == initialStateId }
        if (idx != -1) idx else 0
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { reels.size }
    )

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
        if (currentIndex in reels.indices) {
            viewModel.registerView(reels[currentIndex].state.id)
        }
    }

    // Adaptive preload on page change.
    LaunchedEffect(currentIndex, reels.size) {
        if (currentIndex in reels.indices) {
            ReelPreloadController.adaptAndPrefetch(
                context = context,
                reels = reels,
                currentIndex = currentIndex,
                swipeVelocity = 0.7f,
                avgDurationMs = null,
            )
        }
    }

    // When the page changes, play the page's reel and pause others.
    LaunchedEffect(currentIndex) {
        if (currentIndex in reels.indices) {
            for (i in reels.indices) {
                if (i == currentIndex) pool.play(reels[i].state.id, 1f) else pool.pause(reels[i].state.id)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { pool.releaseAll() }
    }

    if (reels.isEmpty()) {
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
            val reel = reels.getOrNull(page) ?: return@VerticalPager

            // Ensure this page's player is acquired (URL resolved off the main
            // thread) as soon as it is composed.
            LaunchedEffect(reel.state.id, pool) {
                ensureAcquired(pool, context, reel)
            }

            // Observe the pool: the livePlayers snapshot map is Compose-state, so this
            // recomposes as soon as the pool assigns a player for this reel.
            val player = pool.playerFor(reel.state.id)

            ReelPlayerSurface(
                player = player,
                modifier = Modifier.fillMaxSize(),
            )

            ReelOverlay(
                reel = reel,
                onLikeToggle = { viewModel.toggleLike(reel.state.id, reel.state.likedByMe ?: false) },
                onFavoriteToggle = { viewModel.toggleFavorite(reel.state.id, reel.state.favoritedByMe ?: false) },
                onCommentClick = { },
                onShareClick = { viewModel.incrementShare(reel.state.id) },
                onNavigateToAuthor = { onNavigateToUserProfile?.invoke(reel.state.userId) },
            )
        }

        // Back button overlay
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White)
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

@Composable
private fun ReelOverlay(
    reel: UserStateWithUser,
    onLikeToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    onNavigateToAuthor: () -> Unit,
) {
    val state = reel.state
    val profile = reel.profile
    var liked by remember(state.id) { mutableStateOf(state.likedByMe ?: false) }
    var fav by remember(state.id) { mutableStateOf(state.favoritedByMe ?: false) }
    var bigHeartPulse by remember(state.id) { mutableStateOf(false) }
    val bigHeartScale by animateFloatAsState(if (bigHeartPulse) 1f else 0.2f, label = "bigHeart")
    val overlayScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-surface double-tap → like (TikTok behavior).
        @OptIn(ExperimentalFoundationApi::class)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onDoubleClick = {
                        liked = true
                        bigHeartPulse = true
                        onLikeToggle()
                        overlayScope.launch {
                            delay(250)
                            bigHeartPulse = false
                        }
                    }
                )
        )

        // Center heart animation on double-tap.
        if (bigHeartPulse) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .scale(bigHeartScale)
                        .size(96.dp)
                )
            }
        }

        // Bottom-left caption + author
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 88.dp, bottom = 52.dp)
        ) {
            Text(
                text = profile.displayName.ifBlank { "Usuario" },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            if (!state.caption.isNullOrBlank()) {
                Text(
                    text = state.caption,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = profile.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "@${profile.displayName}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }

        // Right rail
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconButton(onClick = { liked = !liked; onLikeToggle() }) {
                Icon(
                    imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Me gusta",
                    tint = if (liked) Color(0xFFFF2D55) else Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text("${(state.likesCount ?: 0) + (if (liked) 1 else 0)}", color = Color.White, fontSize = 12.sp)

            IconButton(onClick = { fav = !fav; onFavoriteToggle() }) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = "Favorito",
                    tint = if (fav) Color(0xFFFFC107) else Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
            Text("${state.favoritesCount ?: 0}", color = Color.White, fontSize = 12.sp)

            IconButton(onClick = onCommentClick) {
                Icon(Icons.Filled.Comment, contentDescription = "Comentarios", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Text("${state.commentsCount ?: 0}", color = Color.White, fontSize = 12.sp)

            IconButton(onClick = onShareClick) {
                Icon(Icons.Filled.Share, contentDescription = "Compartir", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Text("${state.sharesCount ?: 0}", color = Color.White, fontSize = 12.sp)
        }
    }
}