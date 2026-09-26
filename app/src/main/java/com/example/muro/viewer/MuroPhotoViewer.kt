package com.example.muro.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.ui.components.rememberResolvedMediaUrl

/**
 * Full-screen photo viewer for the Muro.
 *
 * Photos are viewed on their own, never mixed with video: a swipe here only ever
 * moves between photographs of the same publication, so a video can no longer be
 * dropped into a horizontal photo carousel where it had no playback controls.
 *
 * Pinch to zoom, drag to pan while zoomed, double tap to reset, back to close.
 */
@Composable
fun MuroPhotoViewer(
    mediaUrls: List<String>,
    initialPage: Int,
    onBack: () -> Unit,
) {
    val startPage = initialPage.coerceIn(0, (mediaUrls.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = startPage,
        pageCount = { mediaUrls.size }
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val raw = mediaUrls.getOrNull(page) ?: return@HorizontalPager
            val resolved = rememberResolvedMediaUrl(raw)

            var scale by remember(raw) { mutableFloatStateOf(1f) }
            var offset by remember(raw) { mutableStateOf(Offset.Zero) }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (resolved.isBlank()) {
                    androidx.compose.material3.CircularProgressIndicator(
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
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    offset = if (scale > 1f) {
                                        offset + pan
                                    } else {
                                        Offset.Zero
                                    }
                                }
                            }
                            .pointerInput(resolved) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        if (scale > 1f) {
                                            scale = 1f
                                            offset = Offset.Zero
                                        } else {
                                            scale = 2.5f
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

        // Page counter: only meaningful with more than one photo.
        if (mediaUrls.size > 1) {
            Text(
                text = "${pagerState.currentPage + 1}/${mediaUrls.size}",
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
