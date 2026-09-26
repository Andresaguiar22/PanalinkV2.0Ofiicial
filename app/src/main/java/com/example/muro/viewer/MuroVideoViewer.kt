package com.example.muro.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.PostDto
import com.example.reels.ui.ReelPlayerSurface
import com.example.ui.components.PanaAvatar
import com.example.ui.components.isVideoUrl
import com.example.ui.settings.ios.IosSettingsColors
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Immersive vertical viewer for the videos of the Muro.
 *
 * The Muro stays the navigation source: it passes the ordered list of video post
 * ids plus the id the user tapped, and this screen continues from that exact point
 * (swipe up = next video, swipe down = previous, back = return to the Muro).
 *
 * Only the visible page plays. The page is driven by the pager's settled page, not
 * by scroll offset, so a half-swipe never starts two players.
 */
@Composable
fun MuroVideoViewer(
    posts: List<PostDto>,
    initialPostId: String?,
    onBack: () -> Unit,
    onToggleLike: (PostDto) -> Unit,
    onOpenComments: (PostDto) -> Unit,
    onShare: (PostDto) -> Unit,
    onProfileClick: (String) -> Unit,
) {
    val context = LocalContext.current

    val initialIndex = remember(posts, initialPostId) {
        val index = posts.indexOfFirst { it.id == initialPostId }
        if (index >= 0) index else 0
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { posts.size }
    )

    val pool = remember(context) { MuroVideoPlayerPool(context) }
    var isMuted by remember { mutableStateOf(false) }
    var userPaused by remember { mutableStateOf(false) }

    DisposableEffect(pool) {
        onDispose { pool.releaseAll() }
    }

    // Android lifecycle: leaving the app must not keep decoding video in the
    // background. Resuming re-arms the visible page through the pager effect below.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, pool) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> pool.pauseAll()
                Lifecycle.Event.ON_RESUME -> {
                    val current = posts.getOrNull(pagerState.settledPage) ?: return@LifecycleEventObserver
                    val id = current.id ?: return@LifecycleEventObserver
                    pool.setTarget(id)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Land on the requested post even if the list arrived after the first
    // composition (entering straight from a fresh publication).
    LaunchedEffect(posts, initialPostId) {
        val target = posts.indexOfFirst { it.id == initialPostId }
        if (target >= 0 && pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }

    // Settled page decides what plays. `snapshotFlow` + distinctUntilChanged keeps
    // this to one transition per real page change instead of one per scroll tick.
    LaunchedEffect(pagerState, posts, isMuted) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val post = posts.getOrNull(page) ?: return@collect
                val stable = post.mediaUrls?.firstOrNull() ?: return@collect
                userPaused = false
                pool.setTarget(post.id ?: return@collect)
                pool.acquireAsync(post.id ?: return@collect, stable)
                // Preload only the next video: enough to make the swipe instant
                // without holding a third decoder.
                posts.getOrNull(page + 1)?.let { next ->
                    next.id?.let { id ->
                        next.mediaUrls?.firstOrNull()?.let { pool.acquireAsync(id, it) }
                    }
                }
            }
    }

    LaunchedEffect(isMuted) {
        pool.setMuted(isMuted)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            val post = posts.getOrNull(page) ?: return@VerticalPager
            val postId = post.id ?: return@VerticalPager
            val player = pool.playerFor(postId)
            val isActive = pagerState.settledPage == page

            LaunchedEffect(postId) {
                val stable = post.mediaUrls?.firstOrNull() ?: return@LaunchedEffect
                pool.acquireAsync(postId, stable)
            }

            Box(modifier = Modifier.fillMaxSize()) {
                ReelPlayerSurface(
                    player = player,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(postId, isActive) {
                            detectTapGestures(
                                onTap = {
                                    if (!isActive) return@detectTapGestures
                                    userPaused = !userPaused
                                    pool.setUserPaused(postId, userPaused)
                                },
                                onDoubleTap = {
                                    if (!isActive) return@detectTapGestures
                                    onToggleLike(post)
                                }
                            )
                        }
                )

                if (player == null || pool.isBuffering(postId)) {
                    MuroBufferingIndicator()
                }

                // Readability scrim: the caption must stay legible over any frame.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.55f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.75f)
                            )
                        )
                )

                if (isActive && userPaused) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = "Reproducir",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                MuroViewerActions(
                    post = post,
                    isActive = isActive,
                    isMuted = isMuted,
                    onToggleLike = { onToggleLike(post) },
                    onOpenComments = { onOpenComments(post) },
                    onShare = { onShare(post) },
                    onToggleMute = { isMuted = !isMuted },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 28.dp)
                )

                MuroPostHeader(
                    post = post,
                    onProfileClick = { post.userId?.let(onProfileClick) },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, end = 84.dp, bottom = 28.dp)
                )
            }
        }

        // Back control floats above the pager so it never scrolls away.
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .zIndex(2f)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver al muro",
                tint = Color.White
            )
        }
    }
}

/** Small, calm buffering indicator — never a full-screen spinner. */
@Composable
private fun MuroBufferingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            color = Color.White.copy(alpha = 0.85f),
            strokeWidth = 2.dp,
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
private fun MuroViewerActions(
    post: PostDto,
    isActive: Boolean,
    isMuted: Boolean,
    onToggleLike: () -> Unit,
    onOpenComments: () -> Unit,
    onShare: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        MuroActionButton(
            icon = if (post.isLikedByMe) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            count = compactCount(post.likesCount),
            tint = if (post.isLikedByMe) IosSettingsColors.red else Color.White,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggleLike()
            }
        )
        MuroActionButton(
            icon = Icons.AutoMirrored.Filled.Comment,
            count = compactCount(post.commentsCount),
            onClick = onOpenComments
        )
        MuroActionButton(
            icon = Icons.AutoMirrored.Filled.Send,
            count = compactCount(post.sharesCount),
            onClick = onShare
        )
        MuroActionButton(
            icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            onClick = onToggleMute
        )
    }
}

@Composable
internal fun MuroActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: String? = null,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(28.dp)
            )
        }
        if (!count.isNullOrBlank()) {
            Text(
                text = count,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
internal fun MuroPostHeader(
    post: PostDto,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(post.id) { mutableStateOf(false) }
    val caption = post.content.orEmpty()

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.clip(CircleShape)) {
                PanaAvatar(
                    avatarUrl = post.profile?.avatarUrl,
                    userId = post.userId,
                    size = 38.dp,
                    borderWidth = 1.5.dp,
                    borderColor = Color.White.copy(alpha = 0.85f),
                    placeholderName = post.profile?.displayName ?: ""
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = post.profile?.displayName ?: "Pana de la Comunidad",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickableNoRipple(onProfileClick)
            )
        }

        if (caption.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = caption,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .heightIn(max = if (expanded) 260.dp else 48.dp)
                    .clickableNoRipple { expanded = !expanded }
            )
            if (!expanded && caption.length > 90) {
                Text(
                    text = "ver más",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickableNoRipple { expanded = true }
                )
            }
        }
    }
}

/** Tap without the ripple: these sit on top of video and a ripple reads as noise. */
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.pointerInput(onClick) {
        detectTapGestures(onTap = { onClick() })
    }

fun compactCount(value: Int): String = when {
    value >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", value / 1_000_000f)
    value >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", value / 1_000f)
    else -> value.toString()
}

/** True when the post is a video publication (the only kind this viewer accepts). */
fun isVideoPost(post: PostDto): Boolean {
    if (post.type == "VIDEO" || post.type == "REEL") return true
    return post.mediaUrls?.any { isVideoUrl(it) } == true
}
