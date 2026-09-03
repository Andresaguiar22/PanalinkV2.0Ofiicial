package com.example.feature.chat.ui.message

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp


@Composable
internal fun PreviewAudioWaveform(
    waveform: List<Float>,
    currentPositionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalDuration = if (durationMs > 0) durationMs else 1000L
    val progressFraction = (currentPositionMs.toFloat() / totalDuration).coerceIn(0f, 1f)
    val bars = if (waveform.isNotEmpty()) waveform else List(40) { 0.2f }

    var canvasWidthPx by remember { mutableStateOf(1f) }

    Box(
        modifier = modifier
            .pointerInput(totalDuration) {
                detectTapGestures { offset ->
                    if (canvasWidthPx > 0) {
                        val fraction = (offset.x / canvasWidthPx).coerceIn(0f, 1f)
                        onSeek((fraction * totalDuration).toLong())
                    }
                }
            }
            .pointerInput(totalDuration) {
                detectDragGestures { change, _ ->
                    change.consume()
                    if (canvasWidthPx > 0) {
                        val fraction = (change.position.x / canvasWidthPx).coerceIn(0f, 1f)
                        onSeek((fraction * totalDuration).toLong())
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { layoutCoordinates ->
                    canvasWidthPx = layoutCoordinates.size.width.toFloat().coerceAtLeast(1f)
                }
        ) {
            val count = bars.size
            if (count == 0) return@Canvas

            val spacingPx = 2.dp.toPx()
            val totalSpacingPx = spacingPx * (count - 1)
            val barWidthPx = maxOf(1f, (size.width - totalSpacingPx) / count)
            val centerY = size.height / 2f
            val maxBarHeight = size.height * 0.9f

            val activeBarIndex = (progressFraction * count).toInt().coerceIn(0, count)

            for (i in 0 until count) {
                val amplitude = bars[i].coerceIn(0.1f, 1.0f)
                val barHeight = maxOf(3.dp.toPx(), amplitude * maxBarHeight)
                val startX = i * (barWidthPx + spacingPx)
                val isPlayed = i <= activeBarIndex

                val color = if (isPlayed) Color(0xFF00A884) else Color(0xFF53636E)

                drawRoundRect(
                    color = color,
                    topLeft = Offset(startX, centerY - barHeight / 2f),
                    size = Size(barWidthPx, barHeight),
                    cornerRadius = CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
                )
            }
        }
    }
}

