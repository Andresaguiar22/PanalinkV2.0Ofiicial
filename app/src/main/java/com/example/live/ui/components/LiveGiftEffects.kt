package com.example.live.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.live.ui.viewmodel.LiveGiftPulse
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Regalos que merecen efecto de pantalla completa estilo TikTok.
 * El resto (rose, heart, applause, star...) usan el banner ligero.
 */
private val FULL_SCREEN_GIFTS = setOf("galaxy", "lion", "tiger", "airplane", "submarine", "rocket")

/**
 * Motor de efectos de regalo. Se superpone al reproductor de video (capa GPU vía
 * graphicsLayer) y reproduce una animación al recibir un [pulse] (local o de otro
 * viewer vía Realtime).
 *
 * - Efectos de pantalla completa: ráfaga de partículas + shockwave + emoji gigante.
 * - Los demás: emoji flotante + banner inferior (igual que antes).
 */
@Composable
fun LiveGiftEffectsOverlay(
    pulse: LiveGiftPulse?,
    modifier: Modifier = Modifier
) {
    var current by remember { mutableStateOf<LiveGiftPulse?>(null) }
    // Progreso de la animación de partículas (0..1)
    val particleProgress = remember { Animatable(0f) }

    LaunchedEffect(pulse?.id) {
        val value = pulse ?: return@LaunchedEffect
        if (value.emoji.isBlank() && value.name.isBlank()) return@LaunchedEffect
        current = value
        // Reinicia la animación de partículas (asegura que si viene un segundo
        // evento mientras el anterior corre, el forward se reinicie).
        particleProgress.snapTo(0f)
        particleProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 3200, easing = LinearEasing)
        )
        kotlinx.coroutines.delay(400) // fade out del banner
        current = null
    }

    val active = current
    val isFull = active?.code != null && active.code in FULL_SCREEN_GIFTS

    Box(modifier = modifier.fillMaxSize()) {
        if (active != null && isFull) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            )
            // Partículas radiales
            val particles = remember(active.id) {
                val count = 26
                List(count) { i ->
                    val angle = (i.toFloat() / count) * 2f * PI.toFloat()
                    val distTo = 80f + Random.nextFloat() * 160f
                    ParticleSpec(
                        angle = angle,
                        distanceTarget = distTo,
                        size = 14f + Random.nextFloat() * 26f,
                        color = particleColor(i)
                    )
                }
            }
            // Velo oscuro sutil para destacar las partículas sobre video claro
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .alpha(0.08f)
                    .background(Color.Black, RoundedCornerShape(24.dp))
                    .padding(24.dp)
            )
            particles.take(8).forEachIndexed { i, spec ->
                val progress = particleProgress.value
                val dist = spec.distanceTarget * progress
                val x = cos(spec.angle) * dist
                val y = sin(spec.angle) * dist
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(
                            x = (x).dp,
                            y = (y).dp
                        )
                        .graphicsLayer {
                            this.alpha = (1f - progress).coerceIn(0f, 1f)
                            this.scaleX = ((1f - progress) + 1f)
                            this.scaleY = ((1f - progress) + 1f)
                        }
                        .size(spec.size.dp)
                        .background(spec.color, RoundedCornerShape(50))
                )
            }
            // Emoji gigante central con pulse
            GiantGiftEmoji(pulse = active)
        }

        // Banner inferior (compartido para todos los regalos)
        if (active != null) {
            FullScreenGiftBanner(
                pulse = active,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = 98.dp)
            )
        }
    }
}

@Composable
private fun GiantGiftEmoji(pulse: LiveGiftPulse) {
    var scale by remember { mutableStateOf(0.2f) }
    var alpha by remember { mutableStateOf(0f) }

    LaunchedEffect(pulse.id) {
        scale = 0.2f
        alpha = 1f
        androidx.compose.animation.core.animate(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = tween(durationMillis = 260, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        ) { value, _ ->
            scale = value
        }
        delay(1100)
        alpha = 0f
    }
    Box(
        modifier = Modifier
            .graphicsLayer {
                this.scaleX = scale
                this.scaleY = scale
                this.alpha = alpha
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = pulse.emoji,
            fontSize = 96.sp,
            textAlign = TextAlign.Center
        )
    }
}

private data class ParticleSpec(
    val angle: Float,
    val distanceTarget: Float,
    val size: Float,
    val color: Color
)

private fun particleColor(index: Int): Color {
    val palette = listOf(
        Color(0xFFFFD54F),
        Color(0xFFFF6B9D),
        Color(0xFFA78BFA),
        Color(0xFFFFB300),
        Color(0xFFFFF3BF)
    )
    return palette[index % palette.size]
}

@Composable
private fun FullScreenGiftBanner(
    pulse: LiveGiftPulse,
    modifier: Modifier = Modifier
) {
    var alpha by remember { mutableStateOf(0f) }
    var popScale by remember { mutableStateOf(0.6f) }

    LaunchedEffect(pulse.id) {
        alpha = 1f
        popScale = 0.6f
        androidx.compose.animation.core.animate(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = tween(durationMillis = 220, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        ) { value, _ ->
            popScale = value
        }
        popScale = 1f
        kotlinx.coroutines.delay(2000)
        alpha = 0f
    }
    Box(
        modifier = modifier
            .alpha(alpha)
            .scale(popScale)
            .background(Color(0x66222222), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = pulse.emoji,
                fontSize = 30.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = pulse.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (pulse.quantity > 1) {
                    Text(
                        text = "x${pulse.quantity}",
                        color = Color(0xFFFFD54F),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}