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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
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
 * Regalos premium con efecto de pantalla completa estilo TikTok.
 * El resto (rose, heart, applause, star...) usan el banner ligero.
 */

/** Paleta y director de animacion por codigo de regalo premium. */
private data class GiftEffectStyle(
    val palette: List<Color>,
    val burstCount: Int,
    val vignetteColor: Color
)

private fun giftStyle(code: String?): GiftEffectStyle = when (code) {
    "galaxy" -> GiftEffectStyle(
        palette = listOf(
            Color(0xFF7B5CFF), Color(0xFFA03BFA), Color(0xFFFF6EC7),
            Color(0xFF5ED4FF), Color(0xFFFFFFFF)
        ),
        burstCount = 40,
        vignetteColor = Color(0xFF3D0F66)
    )
    "lion" -> GiftEffectStyle(
        palette = listOf(
            Color(0xFFFFD54F), Color(0xFFFFB300), Color(0xFFFF8F00),
            Color(0xFFFFE082), Color(0xFFFFFF)
        ),
        burstCount = 30,
        vignetteColor = Color(0xFF7A3B00)
    )
    "tiger" -> GiftEffectStyle(
        palette = listOf(
            Color(0xFFFFA726), Color(0xFFE65100), Color(0xFFFFD54F),
            Color(0xFFFF8A65), Color(0xFFFFFF)
        ),
        burstCount = 30,
        vignetteColor = Color(0xFF4A1A0A)
    )
    "airplane" -> GiftEffectStyle(
        palette = listOf(
            Color(0xFF4FC3F7), Color(0xFFB3E5FC), Color(0xFFFFFFFF),
            Color(0xFF81D4FA), Color(0xFFE1F5FE)
        ),
        burstCount = 24,
        vignetteColor = Color(0xFF0D3B66)
    )
    "submarine" -> GiftEffectStyle(
        palette = listOf(
            Color(0xFF26C6DA), Color(0xFF0097A7), Color(0xFF80DEEA),
            Color(0xFFFFFFFF), Color(0xFFB2EBF2)
        ),
        burstCount = 24,
        vignetteColor = Color(0xFF003D40)
    )
    "rocket" -> GiftEffectStyle(
        palette = listOf(
            Color(0xFFFF5A5F), Color(0xFFFFB300), Color(0xFFFF7043),
            Color(0xFFFFCDD2), Color(0xFFFFFF)
        ),
        burstCount = 28,
        vignetteColor = Color(0xFF6B0F1A)
    )
    else -> GiftEffectStyle(
        palette = listOf(
            Color(0xFFFFD54F), Color(0xFFFF6B9D), Color(0xFFA78BFA),
            Color(0xFFFFB300), Color(0xFFFFF3BF)
        ),
        burstCount = 20,
        vignetteColor = Color(0xFF333333)
    )
}

/**
 * Motor de efectos de regalo v2. Superpuesto al reproductor (capa GPU vía
 * graphicsLayer). Al recibir un [pulse] (local o de otro viewer vía Realtime):
 *
 * - Flash de camara inicial
 * - Shockwave (anillo expansivo)
 * - Rayos de luz radiales (conic gradient aprox. usando colores de la paleta)
 * - Ráfaga de partículas con color por código de regalo
 * - Emoji gigante central con pulse
 * - Contador de racha (mismo emisor+mismo regalo en ventana: x2, x3...)
 * - Banner inferior con remitente + cantidad
 */
@Composable
fun LiveGiftEffectsOverlay(
    pulse: LiveGiftPulse?,
    modifier: Modifier = Modifier
) {
    var current by remember { mutableStateOf<LiveGiftPulse?>(null) }
    var comboCount by remember { mutableStateOf(1) }
    var comboKey by remember { mutableStateOf("") }
    var isFull by remember { mutableStateOf(false) }

    val particleProgress = remember { Animatable(0f) }
    val shockScale = remember { Animatable(0f) }
    val flashAlpha = remember { Animatable(0f) }

    LaunchedEffect(pulse?.id) {
        val value = pulse ?: return@LaunchedEffect
        if (value.emoji.isBlank() && value.name.isBlank()) return@LaunchedEffect

        val full = value.code != null && value.code in com.example.effects.PremiumEffectsCatalog.FULL_SCREEN
        isFull = full
        current = value

        // Racha: mismo emisor + mismo regalo dentro de un lapso -> combo++
        val key = "${value.senderId}|${value.code}"
        if (key == comboKey && !full) {
            comboCount = (comboCount + 1).coerceAtMost(99)
        } else {
            comboKey = key
            comboCount = 1
        }

        if (full) {
            // Flash inicial
            flashAlpha.snapTo(0.55f)
            // Shockwave
            shockScale.snapTo(0f)
            // Reinicia partículas
            particleProgress.snapTo(0f)

            flashAlpha.animateTo(0f, tween(durationMillis = 300, easing = LinearEasing))
            shockScale.animateTo(1f, tween(durationMillis = 900, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            particleProgress.animateTo(1f, tween(durationMillis = 2600, easing = LinearEasing))
        }
        delay(if (full) 1600L else 1200L)  // tiempo visible
        current = null
    }

    val active = current ?: return
    val style = giftStyle(active.code)
    val premium = com.example.effects.PremiumEffectsCatalog.giftSpec(active.code)
    val isPremiumGift = active.code != null && active.code in com.example.effects.PremiumEffectsCatalog.FULL_SCREEN

    Box(modifier = modifier.fillMaxSize()) {
        if (isFull) {
            if (isPremiumGift) {
                // === Efecto premium GPU (alta resolución) ===
                com.example.effects.PremiumEffectView(
                    spec = premium,
                    modifier = Modifier.fillMaxSize(),
                    infinite = false
                )
                // Emoji gigante central sin las capas procedurales viejas
                GiantGiftEmoji(pulse = active)
            } else {
                // Velo/vignette con el color del regalo
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.25f)
                        .background(style.vignetteColor.copy(alpha = 0.35f))
                )

                // Flash de camara
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(flashAlpha.value)
                        .background(Color.White)
                )

                // Shockwave: anillo expansivo
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            val s = shockScale.value
                            scaleX = s
                            scaleY = s
                            alpha = (1f - s).coerceIn(0f, 1f)
                        }
                        .size(220.dp)
                        .background(Color.White.copy(alpha = 0.6f), CircleShape)
                )

                // Rayos de luz radiales alrededor del centro
                val rays = remember(active.id) {
                    val count = style.burstCount / 2
                    List(count) { i ->
                        val angle = (i.toFloat() / count) * 2f * PI.toFloat() + 0.1f
                        RaySpec(
                            angle = angle,
                            length = 120f + Random.nextFloat() * 180f,
                            thickness = 2f + Random.nextFloat() * 3f,
                            color = style.palette[i % style.palette.size],
                            spin = (Random.nextInt(2) * 2 - 1) * (30f + Random.nextFloat() * 60f)
                        )
                    }
                }

                // Partículas de la ráfaga con la paleta del regalo
                val particles = remember(active.id) {
                    List(style.burstCount) { i ->
                        val angle = (i.toFloat() / style.burstCount) * 2f * PI.toFloat()
                        ParticleSpec(
                            angle = angle + Random.nextFloat() * 0.15f,
                            distanceTarget = 90f + Random.nextFloat() * 220f,
                            size = 12f + Random.nextFloat() * 30f,
                            color = style.palette[i % style.palette.size]
                        )
                    }
                }

                // Rayos
                rays.forEach { ray ->
                    val progress = particleProgress.value
                    val rot = ray.spin * progress
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .rotate(rot)
                            .graphicsLayer {
                                val s = (1f - progress).coerceIn(0f, 1f)
                                alpha = s
                                scaleX = s
                                scaleY = s
                            }
                            .offset(
                                x = 0.dp,
                                y = 0.dp
                            )
                            .size(
                                width = (ray.thickness * 6).dp,
                                height = ray.length.dp
                            )
                            .background(ray.color.copy(alpha = 0.55f), RoundedCornerShape(50))
                    )
                }

                // Partículas radiales
                particles.take(14).forEachIndexed { i, spec ->
                    val progress = particleProgress.value
                    val dist = spec.distanceTarget * progress
                    val x = cos(spec.angle) * dist
                    val y = sin(spec.angle) * dist
                    val baseOpacity = (1f - progress).coerceIn(0f, 1f)
                    val scaleF = 0.3f + (1f - progress).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(
                                x = (x).dp,
                                y = (y).dp
                            )
                            .graphicsLayer {
                                this.alpha = baseOpacity
                                this.scaleX = scaleF
                                this.scaleY = scaleF
                                this.rotationZ = spec.spin
                            }
                            .size(spec.size.dp)
                            .background(spec.color, RoundedCornerShape(50))
                    )
                }

                // Emoji gigante central con pulse
                GiantGiftEmoji(pulse = active)
            }
        }

        // Banner inferior (también para regalos normales, con racha)
        GiftBanner(
            pulse = active,
            combo = comboCount,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 98.dp)
        )
    }
}

private data class RaySpec(
    val angle: Float,
    val length: Float,
    val thickness: Float,
    val color: Color,
    val spin: Float
)

private data class ParticleSpec(
    val angle: Float,
    val distanceTarget: Float,
    val size: Float,
    val color: Color,
    val spin: Float = Random.nextFloat() * 360f
)

@Composable
private fun GiantGiftEmoji(pulse: LiveGiftPulse) {
    var scale by remember { mutableStateOf(0.2f) }
    var alpha by remember { mutableStateOf(0f) }
    var rotation by remember { mutableStateOf(0f) }

    val emoji = if (pulse.code != null && pulse.code in com.example.effects.PremiumEffectsCatalog.FULL_SCREEN) {
        com.example.effects.PremiumEffectsCatalog.giftSpec(pulse.code).emoji
    } else {
        pulse.emoji
    }

    LaunchedEffect(pulse.id) {
        scale = 0.2f
        alpha = 1f
        rotation = 0f
        androidx.compose.animation.core.animate(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = tween(durationMillis = 280, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        ) { value, _ ->
            scale = value
        }
        delay(1000)
        alpha = 0f
    }
    Box(
        modifier = Modifier
            .graphicsLayer {
                this.scaleX = scale
                this.scaleY = scale
                this.alpha = alpha
                this.rotationZ = rotation
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 104.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GiftBanner(
    pulse: LiveGiftPulse,
    combo: Int,
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
        kotlinx.coroutines.delay(2200)
        alpha = 0f
    }
    val identity = rememberLiveIdentity(pulse.senderId)
    val senderName = identity.displayNameOr(pulse.senderId)
    val giftEmoji = pulse.emoji.ifBlank { com.example.effects.PremiumEffectsCatalog.giftEmojiFallback(pulse.code) }

    Row(
        modifier = modifier
            .alpha(alpha)
            .scale(popScale)
            .background(Color(0x66222222), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = giftEmoji, fontSize = 26.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = senderName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "envió",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
            Text(
                text = "${pulse.name}${if (pulse.quantity > 1) " x${pulse.quantity}" else ""}${if (combo > 1) " · $combo combo" else ""}",
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}