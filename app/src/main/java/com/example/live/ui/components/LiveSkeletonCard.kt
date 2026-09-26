package com.example.live.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import com.example.ui.settings.ios.IosSettingsColors

@Composable
fun LiveSkeletonCard() {
    val shimmerColors = listOf(
        IosSettingsColors.cell,
        IosSettingsColors.cellElevated,
        IosSettingsColors.cell
    )

    var targetOffset by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            targetOffset = 1000f
            delay(1200)
            targetOffset = 0f
            delay(1200)
        }
    }

    val brush = Brush.horizontalGradient(
        colors = shimmerColors,
        startX = targetOffset - 500f,
        endX = targetOffset
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, IosSettingsColors.separator)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(brush)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(IosSettingsColors.separator)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                IosSettingsColors.mediaScrim
                            )
                        )
                    )
                    .padding(12.dp)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(IosSettingsColors.separator)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(IosSettingsColors.separator)
                    )
                }
            }
        }
    }
}
