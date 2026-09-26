package com.example.media.player.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.TimeUnit
import com.example.ui.settings.ios.IosSettingsColors

/**
 * P6.7.3 - Player Progress Bar
 * Seekable slider with current position and total duration.
 */
@Composable
fun PlayerProgressBar(
    currentPositionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderValue by remember(currentPositionMs) { mutableStateOf(currentPositionMs.toFloat()) }
    val duration = durationMs.coerceAtLeast(1L)

    Column(modifier = modifier.padding(horizontal = 24.dp)) {
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onSeek(sliderValue.toLong()) },
            valueRange = 0f..duration.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = IosSettingsColors.label,
                activeTrackColor = IosSettingsColors.blue
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(currentPositionMs),
                color = IosSettingsColors.secondaryLabel,
                fontSize = 11.sp
            )
            Text(
                text = formatDuration(durationMs),
                color = IosSettingsColors.secondaryLabel,
                fontSize = 12.sp
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
