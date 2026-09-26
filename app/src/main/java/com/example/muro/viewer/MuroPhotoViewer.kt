package com.example.muro.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.data.model.PostDto
import com.example.ui.components.isVideoUrl
import com.example.ui.components.rememberResolvedMediaUrl
import com.example.ui.settings.ios.IosSettingsColors

/** One photo page: the publication it belongs to plus the photo itself. */
private data class MuroPhotoPage(val post: PostDto, val url: String)

/**
 * Full-screen photo viewer for the Muro.
 *
 * The Muro stays the navigation source: it hands over the photo publications in
 * order plus the one the user tapped, and this viewer continues from that exact
 * photo. Swiping moves vertically between photographs and never drops a video into
 * the carousel; a video opens the vertical video viewer instead.
 *
 * Pinch to zoom, drag to pan while zoomed, double tap to reset. Page changes are
 * arbitrated against the zoom: while the photo is zoomed the drag pans it, and the
 * pager only takes the gesture once the photo is back at fit scale.
 */
@Composable
fun MuroPhotoViewer(
    posts: List<PostDto>,
    initialPostId: String?,
    initialPhotoIndex: Int,
    onBack: () -> Unit,
    onToggleLike: (PostDto) -> Unit,
    onOpenComments: (PostDto) -> Unit,
    onShare: (PostDto) -> Unit,
) {
    // Every photo publication flattened into one vertical list of pages.
    val pages = remember(posts) {
        posts.flatMap { post ->
            (post.mediaUrls ?: emptyList())
                .filterNot { isVideoUrl(it) }
                .map { url -> MuroPhotoPage(post, url) }
        }
    }

    if (pages.isEmpty()) {
        onBack()
        return
    }

    val startPage = remember(pages, initialPostId, initialPhotoIndex) {
        val firstOfPost = pages.indexOfFirst { it.post.id == initialPostId }
        if (firstOfPost < 0) {
            0
        } else {
            (firstOfPost + initialPhotoIndex).coerceIn(0, pages.size - 1)
        }
    }

    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { pages.size })

    // Zoom lives per page so the pager can tell whether the visible photo is zoomed
    // and must receive the drag as pan instead of a page change.
    val scales = remember { mutableStateMapOf<Int, Float>() }
    val offsets = remember { mutableStateMapOf<Int, Offset>() }
    val isZoomed = (scales[pagerState.currentPage] ?: 1f) > 1f
    val haptic = LocalHapticFeedback.current

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isZoomed
        ) { page ->
            val item = pages.getOrNull(page) ?: return@VerticalPager
            MuroPhotoPageContent(
                page = page,
                url = item.url,
                scales = scales,
                offsets = offsets
            )
        }

        val current = pages.getOrNull(pagerState.currentPage)
        if (current != null) {
            // Readability scrim: identity and caption must stay legible over any photo.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f),
                            0.32f to Color.Transparent,
                            0.60f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.75f)
                        )
                    )
            )

            // Same actions as the video viewer so both read as one experience.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                MuroActionButton(
                    icon = if (current.post.isLikedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    count = compactCount(current.post.likesCount),
                    tint = if (current.post.isLikedByMe) IosSettingsColors.red else Color.White,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleLike(current.post)
                    }
                )
                MuroActionButton(
                    icon = Icons.AutoMirrored.Filled.Comment,
                    count = compactCount(current.post.commentsCount),
                    onClick = { onOpenComments(current.post) }
                )
                MuroActionButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    count = compactCount(current.post.sharesCount),
                    onClick = { onShare(current.post) }
                )
            }

            MuroPostHeader(
                post = current.post,
                onProfileClick = {},
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, end = 84.dp, bottom = 28.dp)
            )
        }

        // Page counter: only meaningful with more than one photo.
        if (pages.size > 1) {
            Text(
                text = "${pagerState.currentPage + 1}/${pages.size}",
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 10.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

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

@Composable
private fun MuroPhotoPageContent(
    page: Int,
    url: String,
    scales: MutableMap<Int, Float>,
    offsets: MutableMap<Int, Offset>,
) {
    val resolved = rememberResolvedMediaUrl(url)
    val scale = scales[page] ?: 1f
    val offset = offsets[page] ?: Offset.Zero

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (resolved.isBlank()) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.85f),
                strokeWidth = 2.dp,
                modifier = Modifier.size(30.dp)
            )
        } else {
            AsyncImage(
                model = resolved,
                contentDescription = "Fotografía",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(resolved) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var handling = (scales[page] ?: 1f) > 1f
                            do {
                                val event = awaitPointerEvent()
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                // Only take the gesture over when actually zooming or
                                // already zoomed; otherwise the pager keeps the drag.
                                if (handling || zoomChange != 1f) {
                                    val next = ((scales[page] ?: 1f) * zoomChange).coerceIn(1f, 5f)
                                    scales[page] = next
                                    offsets[page] = if (next > 1f) {
                                        (offsets[page] ?: Offset.Zero) + panChange
                                    } else {
                                        Offset.Zero
                                    }
                                    handling = next > 1f
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(resolved) {
                        detectTapGestures(
                            onDoubleTap = {
                                if ((scales[page] ?: 1f) > 1f) {
                                    scales[page] = 1f
                                    offsets[page] = Offset.Zero
                                } else {
                                    scales[page] = 2.5f
                                }
                            }
                        )
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
        }
    }
}

/** True when the post carries at least one photo (the only kind the photo viewer accepts). */
fun isPhotoPost(post: PostDto): Boolean =
    post.mediaUrls?.any { !isVideoUrl(it) } == true
