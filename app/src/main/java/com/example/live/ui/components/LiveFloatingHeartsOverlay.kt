package com.example.live.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

data class HeartItem(val id: Long = System.currentTimeMillis() + Random.nextLong(1000), val xOffset: Int = Random.nextInt(-60, 60))

@Composable
fun LiveFloatingHeartsOverlay(
    trigger: Unit?,
    modifier: Modifier = Modifier
) {
    val hearts = remember { mutableStateListOf<HeartItem>() }

    LaunchedEffect(trigger) {
        if (trigger != null) {
            hearts.add(HeartItem())
            if (hearts.size > 25) hearts.removeAt(0)
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        hearts.forEach { heart ->
            key(heart.id) {
                var offsetY by remember { mutableStateOf(0f) }
                var alpha by remember { mutableStateOf(1f) }

                LaunchedEffect(heart.id) {
                    val anim = Animatable(0f)
                    anim.animateTo(
                        targetValue = -450f,
                        animationSpec = tween(durationMillis = 2000, easing = LinearEasing)
                    ) {
                        offsetY = value
                        alpha = (1f - (value / -450f)).coerceIn(0f, 1f)
                    }
                    hearts.remove(heart)
                }

                Box(
                    modifier = Modifier
                        .padding(end = 28.dp, bottom = 90.dp)
                        .offset(x = heart.xOffset.dp, y = offsetY.dp)
                        .alpha(alpha)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = listOf(Color(0xFFEF5350), Color(0xFFFF4081), Color(0xFFFFEB3B), Color(0xFF00A884)).random(),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
