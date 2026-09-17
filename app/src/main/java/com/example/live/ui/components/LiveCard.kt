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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Visibility
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.live.domain.model.LiveStream
import com.example.live.ui.LiveBadgeFill
import com.example.live.ui.LiveCardShape
import com.example.live.ui.LiveGlassBorder
import com.example.live.ui.LiveLiveGlow
import com.example.live.ui.LiveLiveRed
import com.example.live.ui.LiveNeon
import com.example.live.ui.formatLiveCount
import com.example.ui.components.PanaAvatar
import com.example.ui.components.rememberAsyncMediaUrl

private val CardHeight = 178.dp
private val CardPadding = 14.dp

/**
 * Tarjeta de una transmisión en vivo dentro del listado.
 *
 * Sin bloque de color: la miniatura del video llena la tarjeta (desenfocada + oscurecida)
 * y los datos flotan encima, sobre un borde fino translúcido y esquinas muy redondeadas.
 */
@Composable
fun LiveCard(
    live: LiveStream,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resolvedThumbnailUrl = rememberAsyncMediaUrl(live.thumbnailUrl)
    val hostIdentity = rememberLiveIdentity(live.hostId)
    val hostName = "@" + hostIdentity.displayNameOr(live.hostId)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(CardHeight)
            .clickable(onClick = onClick),
        shape = LiveCardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, LiveGlassBorder),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LiveCardBackdrop(resolvedThumbnailUrl, live.title)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(CardPadding)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiveNowBadge()
                    LiveViewerBadge(viewerCount = live.viewerCount)
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = live.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                live.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = description,
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    PanaAvatar(
                        userId = live.hostId,
                        size = 34.dp,
                        borderWidth = 2.dp,
                        borderColor = LiveNeon,
                        contentDescription = "Avatar de $hostName",
                        placeholderName = hostIdentity?.displayName,
                    )
                    Spacer(modifier = Modifier.width(9.dp))
                    Text(
                        text = hostName,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Miniatura real del directo (o un degradado si aún no hay) + capa oscura encima. */
@Composable
private fun LiveCardBackdrop(thumbnailUrl: String, title: String) {
    if (thumbnailUrl.isNotBlank()) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = "Miniatura de $title",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(16.dp)
                // El blur deja los bordes suaves: el scale los saca del recorte.
                .scale(1.12f)
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF16232A), Color(0xFF0C1117))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.LiveTv,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.18f),
                modifier = Modifier.size(56.dp)
            )
        }
    }

    // Capa negra translúcida: simula el video atenuado y garantiza contraste del texto.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.30f),
                        Color.Black.copy(alpha = 0.18f),
                        Color.Black.copy(alpha = 0.72f),
                    )
                )
            )
    )
}

/** Badge "EN VIVO": píldora oscura translúcida, borde rojo y punto con halo dibujado a mano. */
@Composable
private fun LiveNowBadge() {
    val transition = rememberInfiniteTransition(label = "live-pulse")
    val glowAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "live-pulse-alpha"
    )

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(LiveBadgeFill)
            .border(1.dp, LiveLiveRed.copy(alpha = 0.55f), CircleShape)
            .padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val radius = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(LiveLiveGlow.copy(alpha = glowAlpha), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
            drawCircle(
                color = LiveLiveRed,
                radius = radius * 0.42f,
                center = center,
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "EN VIVO",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Badge de espectadores: misma píldora translúcida con ojo + contador compacto. */
@Composable
private fun LiveViewerBadge(viewerCount: Int) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(LiveBadgeFill)
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Visibility,
            contentDescription = "Espectadores",
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = formatLiveCount(viewerCount),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
