package com.example.live.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.live.ui.LiveCardShape
import com.example.live.ui.LiveGlassBorder
import com.example.live.ui.LiveGlassFill

/**
 * Placeholder del listado mientras cargan las transmisiones.
 *
 * Comparte altura, esquinas y borde con [LiveCard] para que el paso de "cargando"
 * a "con datos" no salte de tamaño.
 */
@Composable
fun LiveSkeletonCard() {
    val transition = rememberInfiniteTransition(label = "live-skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "live-skeleton-alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(178.dp),
        shape = LiveCardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, LiveGlassBorder),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LiveGlassFill)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    SkeletonBar(width = 96.dp, height = 26.dp, radius = 13.dp, alpha = pulse)
                    Spacer(modifier = Modifier.weight(1f))
                    SkeletonBar(width = 68.dp, height = 26.dp, radius = 13.dp, alpha = pulse)
                }

                Spacer(modifier = Modifier.weight(1f))

                SkeletonBar(width = 220.dp, height = 22.dp, radius = 6.dp, alpha = pulse)

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = pulse * 0.25f))
                    )
                    Spacer(modifier = Modifier.width(9.dp))
                    SkeletonBar(width = 120.dp, height = 14.dp, radius = 6.dp, alpha = pulse)
                }
            }
        }
    }
}

@Composable
private fun SkeletonBar(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    radius: androidx.compose.ui.unit.Dp,
    alpha: Float,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(radius))
            .alpha(alpha)
            .background(Color.White.copy(alpha = 0.22f))
    )
}
