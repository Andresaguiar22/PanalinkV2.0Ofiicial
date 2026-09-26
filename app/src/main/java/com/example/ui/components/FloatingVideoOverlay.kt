package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.util.ReelsPlayerManager
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.material3.MaterialTheme
import coil.compose.AsyncImage // As a placeholder for the video if we don't have ExoPlayer here, or we can use the same video component as TikTok Video
import com.example.ui.settings.ios.IosSettingsColors

@Composable
fun FloatingVideoOverlay(
    onNavigateBackToReels: () -> Unit
) {
    val isFloatingActive by ReelsPlayerManager.isFloatingActive.collectAsState()
    val activeVideoUrl by ReelsPlayerManager.activeVideoUrl.collectAsState()
    
    if (!isFloatingActive || activeVideoUrl == null) return

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

    val overlayWidth = with(density) { 120.dp.toPx() }
    val overlayHeight = with(density) { 200.dp.toPx() }

    var offsetX by remember { mutableFloatStateOf(screenWidth - overlayWidth - 40f) }
    var offsetY by remember { mutableFloatStateOf(screenHeight - overlayHeight - 300f) }

    var isDragging by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Trash area logic
    val trashAreaHeight = with(density) { 100.dp.toPx() }
    val isOverTrash = isDragging && offsetY > screenHeight - trashAreaHeight - overlayHeight / 2

    Box(modifier = Modifier.fillMaxSize().zIndex(1000f)) {
        // Trash Icon
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        if (isOverTrash) Color.Red.copy(alpha = 0.8f) else IosSettingsColors.mediaScrimSoft,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Trash",
                    tint = IosSettingsColors.label,
                    modifier = Modifier.size(if (isOverTrash) 40.dp else 32.dp)
                )
            }
        }

        // Floating Video Window
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(120.dp, 200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(IosSettingsColors.groupBackground)
                .border(2.dp, if (isOverTrash) Color.Red else IosSettingsColors.label, RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            if (isOverTrash) {
                                ReelsPlayerManager.closeAndClear()
                            } else {
                                // Snap to nearest edge
                                coroutineScope.launch {
                                    val snapX = if (offsetX + overlayWidth / 2 < screenWidth / 2) {
                                        40f
                                    } else {
                                        screenWidth - overlayWidth - 40f
                                    }
                                    // Simple manual animation could be implemented here, but for now we just snap
                                    offsetX = snapX
                                }
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount.x).coerceIn(0f, screenWidth - overlayWidth)
                            offsetY = (offsetY + dragAmount.y).coerceIn(0f, screenHeight - overlayHeight)
                        }
                    )
                }
                .clickable {
                    ReelsPlayerManager.hideFloatingPlayer()
                    onNavigateBackToReels()
                }
        ) {
            // Simplified: showing thumbnail or video.
            // Ideally, you'd use ExoPlayer here, sharing the instance from TikTokVideoFeedScreen if possible.
            // For now, we simulate with an AsyncImage or just a Box since TikTok video might be complex to extract.
            AsyncImage(
                model = activeVideoUrl,
                contentDescription = "Mini Player",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Close button
            IconButton(
                onClick = { ReelsPlayerManager.closeAndClear() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .padding(4.dp)
                    .background(IosSettingsColors.mediaScrimSoft, CircleShape)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = IosSettingsColors.label, modifier = Modifier.size(16.dp))
            }
        }
    }
}
