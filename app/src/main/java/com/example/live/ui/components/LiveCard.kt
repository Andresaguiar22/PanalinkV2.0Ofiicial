package com.example.live.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.live.domain.model.LiveStream
import com.example.ui.components.PanaAvatar
import com.example.ui.components.rememberAsyncMediaUrl

private val LiveCardShape = RoundedCornerShape(20.dp)
private val LiveRed = Color(0xFFFF3B30)

/**
 * Tarjeta de transmisión con estética glassmorphism: sin bloque de color, borde
 * ultra fino y el thumbnail real (con fallback difuminado) bajo una capa negra
 * translúcida.
 */
@Composable
fun LiveCard(
    live: LiveStream,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resolvedThumbnailUrl = rememberAsyncMediaUrl(live.thumbnailUrl)
    val hostName = rememberLiveIdentity(live.hostId).displayNameOr(live.hostId)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clickable(onClick = onClick),
        shape = LiveCardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (resolvedThumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = resolvedThumbnailUrl,
                    contentDescription = "Miniatura de ${live.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Sin thumbnail real: gradiente de marca difuminado para insinuar
                // el video sin inventar una imagen.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF1E3A3A), Color(0xFF12262E))
                            )
                        )
                        .blur(28.dp)
                )
            }

            // Capa negra translucida sobre el thumbnail: contraste del texto.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.55f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiveBadge()
                ViewerCountBadge(viewerCount = live.viewerCount)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Text(
                    text = live.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PanaAvatar(
                        userId = live.hostId,
                        size = 26.dp,
                        borderWidth = 1.dp,
                        borderColor = Color.White.copy(alpha = 0.35f),
                        contentDescription = "Avatar de $hostName"
                    )
                    Text(
                        text = hostName,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Badge "EN VIVO" glassmorphism: píldora de gris oscuro translúcido con borde
 * sutil, punto rojo dibujado con [Canvas] (halo/glow pulsante) y texto pequeño.
 */
@Composable
private fun LiveBadge() {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color(0xFF111113).copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LiveGlowDot()
        Text(
            text = "EN VIVO",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
    }
}

@Composable
private fun LiveGlowDot() {
    val transition = rememberInfiniteTransition(label = "liveGlow")
    val glowAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "liveGlowAlpha"
    )

    Canvas(modifier = Modifier.size(14.dp)) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    LiveRed.copy(alpha = 0.9f * glowAlpha),
                    LiveRed.copy(alpha = 0f)
                ),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
        drawCircle(color = LiveRed, radius = radius * 0.40f, center = center)
    }
}

@Composable
private fun ViewerCountBadge(viewerCount: Int) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color(0xFF111113).copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = "$viewerCount",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}