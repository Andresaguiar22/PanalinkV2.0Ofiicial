package com.example.rooms.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.rooms.model.VoiceRoomEntranceEvent
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Overlay de ENTRADA a pantalla completa, tipo StarMaker.
 * Aparece cuando un miembro entra a la sala (con el entrance code configurado).
 * - Llamarada cónica con el gradiente de la entrada
 * - Círculo expansivo con avatar + nombre del que entró
 * - Partículas radiales en la paleta del efecto
 * - Etiqueta "ENTRÓ A LA SALA"
 */
@Composable
fun VoiceRoomEntranceOverlay(
    event: VoiceRoomEntranceEvent?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spec = VoiceRoomToolboxCatalog.entranceByCode(event?.entranceCode)
        ?: VoiceRoomToolboxCatalog.entrances.first()

    if (event == null) return

    val progress = remember { Animatable(0f) }
    val contentScale = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }

    LaunchedEffect(event.userId, event.entranceCode) {
        fade.snapTo(0f)
        progress.snapTo(0f)
        contentScale.snapTo(0.4f)
        fade.animateTo(1f, tween(durationMillis = 240, easing = LinearEasing))
        progress.animateTo(1f, tween(durationMillis = 1400, easing = LinearEasing))
        contentScale.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
        delay(900)
        fade.animateTo(0f, tween(durationMillis = 300, easing = LinearEasing))
        onDone()
    }

    val currentProgress = progress.value
    val g1 = Color(spec.gradient.first)
    val g2 = Color(spec.gradient.second)

    // Partículas de la paleta
    val particles = remember(event.userId) {
        val n = 24
        List(n) { i ->
            val angle = (i.toFloat() / n) * 2f * PI.toFloat()
            ParticlePendantSpec(
                angle = angle,
                dist = 60f + Random.nextFloat() * 200f,
                size = 10f + Random.nextFloat() * 22f,
                color = if (i % 2 == 0) g1 else g2
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(fade.value),
        contentAlignment = Alignment.Center
    ) {
        // Fondo oscuro suave
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha((currentProgress * 0.5f).coerceIn(0f, 1f))
                .background(Color(0xAA000000))
        )

        // Llamarada cónica (cono de luz girando)
        Box(
            modifier = Modifier
                .size(640.dp)
                .graphicsLayer {
                    val s = (currentProgress * 1.4f).coerceIn(0f, 1f)
                    scaleX = s
                    scaleY = s
                    rotationZ = 360f * currentProgress
                }
                .background(
                    Brush.radialGradient(
                        colors = listOf(g1.copy(alpha = 0.5f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                        radius = 0.6f
                    )
                )
        )

        // Rayos / picos que giran
        Box(
            modifier = Modifier
                .size(560.dp)
                .graphicsLayer {
                    rotationZ = -180f * currentProgress
                }
        ) {
            particles.take(12).forEachIndexed { i, p ->
                val radial = (currentProgress).coerceIn(0f, 1f)
                val x = cos(p.angle) * p.dist * radial
                val y = sin(p.angle) * p.dist * radial
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = x.dp, y = y.dp)
                        .graphicsLayer {
                            alpha = (1f - radial).coerceIn(0f, 1f)
                            scaleX = 1f + radial * 2f
                            scaleY = 1f + radial * 2f
                        }
                        .size(p.size.dp)
                        .background(p.color.copy(alpha = 0.6f), RoundedCornerShape(50))
                )
            }
        }

        // Contenido central: avatar + nombre
        val scalePop = contentScale.value
        Column(
            modifier = Modifier
                .scale(scalePop)
                .alpha((1f - (currentProgress - 0.6f).coerceIn(0f, 1f) * 2f).coerceIn(0f, 1f))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .padding(6.dp)
                    .background(
                        Brush.linearGradient(listOf(g1, g2)),
                        CircleShape
                    )
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xDD0B1220)),
                contentAlignment = Alignment.Center
            ) {
                if (!event.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = event.avatarUrl,
                        contentDescription = event.displayName ?: "Avatar",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Text(
                        text = event.displayName?.take(1)?.uppercase() ?: "👤",
                        color = Color.White,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "✨ ${event.displayName ?: "Alguien"}",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "ENTRÓ A LA SALA",
                color = g1,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Colgante (pendant) que rodea el avatar de un sillón.
 * Se dibuja alrededor del círculo del asiento según la spec.
 */
@Composable
fun VoiceRoomPendant(
    code: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val spec = VoiceRoomToolboxCatalog.pendantByCode(code)
    if (spec == null || code == "none") return

    val ring = Color(spec.ringColor)
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            pulse.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            pulse.animateTo(0f, tween(900, easing = FastOutSlowInEasing))
        }
    }

    Box(modifier = modifier) {
        // Anillo ligeramente más grande que el avatar (llama la atención)
        Box(
            modifier = Modifier
                .size(size + 8.dp)
                .graphicsLayer {
                    val s = 1f + pulse.value * 0.06f
                    scaleX = s
                    scaleY = s
                }
                .border(2.dp, ring.copy(alpha = 0.8f), CircleShape)
        )
        // Símbolo decorativo en la parte superior
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-4).dp)
                .background(Color(0xCC0B1220), CircleShape)
                .padding(3.dp)
        ) {
            Text(
                text = when {
                    spec.showCrown -> "👑"
                    spec.showHalo -> "😇"
                    spec.showHearts -> "💖"
                    spec.showMusic -> "🎵"
                    spec.showLightning -> "⚡"
                    else -> spec.symbol.ifBlank { "◉" }
                },
                fontSize = if (spec.symbol.length <= 1) 13.sp else 10.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

private data class ParticlePendantSpec(
    val angle: Float,
    val dist: Float,
    val size: Float,
    val color: Color
)