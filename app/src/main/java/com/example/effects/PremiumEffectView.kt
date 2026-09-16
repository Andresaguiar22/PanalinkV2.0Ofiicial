package com.example.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Renderiza un [PremiumEffectSpec] con capas GPU (Skia -> HWUI/compositor).
 *
 * Modos:
 *  - PENDANT: contenedor compacto (anillo + emoji + chispas) que rodea el avatar.
 *  - ENTRANCE / GIFT_FULLSCREEN: capa única que ocupa todo el área (vignette,
 *    anillos expansivos, rayos radiales, ráfaga de partículas).
 *  - GIFT_BANNER: efecto ligero (solo chispas, se combina con banner en la UI).
 *
 * Las partículas se dibujan como primitivas vectoriales en [DrawScope]
 * (sin Bitmaps), por lo que se mantienen 60fps con varios efectos a la vez.
 */
@Composable
fun PremiumEffectView(
    spec: PremiumEffectSpec,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    infinite: Boolean = false,
    contentAlignment: Alignment = Alignment.Center
) {
    val progress = remember { Animatable(0f) }
    val sparklePhase = remember { Animatable(0f) }

    LaunchedEffect(spec.emoji, spec.kind, infinite) {
        if (infinite) {
            sparklePhase.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2600, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = spec.durationMs.toInt(),
                    easing = LinearEasing
                )
            )
            progress.snapTo(1f)
        }
    }

    val burst = spec.burst
    val rays = spec.rays
    val rings = spec.rings

    val particles = remember(spec.emoji) {
        val rnd = Random(spec.emoji.hashCode())
        List(burst.count) { i ->
            val angle = (i.toFloat() / maxOf(burst.count, 1)) * 2f * PI.toFloat()
            ParticleDef(
                angle = angle + rnd.nextFloat() * 0.14f,
                distFrac = (burst.innerRadius + rnd.nextFloat() * (burst.outerRadius - burst.innerRadius)) / 360f,
                sizePx = burst.minSize + rnd.nextFloat() * (burst.maxSize - burst.minSize),
                colorIndex = i % 3,
                spin = (rnd.nextInt(2) * 2 - 1) * burst.spinSpeed,
                twinkleAmp = 0.3f + rnd.nextFloat() * 0.5f,
                twinkleSpeed = 0.8f + rnd.nextFloat() * 1.4f
            )
        }
    }

    val rayDefs = remember(spec.emoji) {
        val rnd = Random(spec.emoji.hashCode() * 31 + 7)
        List(rays.count) { i ->
            val angle = (i.toFloat() / maxOf(rays.count, 1)) * 2f * PI.toFloat() + rnd.nextFloat() * 0.2f
            RayDef(
                angle = angle,
                lengthFrac = (rays.minLength + rnd.nextFloat() * (rays.maxLength - rays.minLength)) / 640f,
                thickness = 2f + rnd.nextFloat() * 4f,
                spin = (rnd.nextInt(2) * 2 - 1) * rays.rotationSpeed
            )
        }
    }

    Box(modifier = modifier, contentAlignment = contentAlignment) {
        // Lottie si está configurado: capa superior para el asset vectorial.
        // En PENDANT se limita al tamaño del anillo; en pantalla completa llena.
        if (spec.lottieUrl != null || spec.lottieAsset != null) {
            val lottieModifier = if (spec.kind == PremiumKind.PENDANT) {
                Modifier.size(size * 1.6f)
            } else {
                Modifier.fillMaxSize()
            }
            PremiumLottieView(
                lottieUrl = spec.lottieUrl,
                lottieRawRes = spec.lottieAsset,
                modifier = lottieModifier
            )
        }
        when (spec.kind) {
            PremiumKind.PENDANT -> PendantEffect(
                spec = spec,
                size = size,
                particles = particles,
                phase = sparklePhase.value
            )
            PremiumKind.ENTRANCE, PremiumKind.GIFT_FULLSCREEN -> FullEffect(
                spec = spec,
                particles = particles,
                rayDefs = rayDefs,
                progress = progress.value
            )
            PremiumKind.GIFT_BANNER -> BannerSparkle(
                spec = spec,
                particles = particles,
                phase = sparklePhase.value
            )
        }
    }
}

@Composable
private fun PendantEffect(
    spec: PremiumEffectSpec,
    size: Dp,
    particles: List<ParticleDef>,
    phase: Float
) {
    Box(
        modifier = Modifier
            .size(size * 1.6f)
            .graphicsLayer {
                val s = 0.85f + 0.1f * sin(phase * PI.toFloat() * 2f)
                scaleX = s
                scaleY = s
                alpha = 0.9f + 0.1f * sin(phase * PI.toFloat())
            }
    ) {
        // Anillo dorado con gradiente radial
        Canvas(modifier = Modifier.size(size * 1.6f)) {
            val r = this.size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(spec.secondary, spec.primary),
                    radius = r
                ),
                radius = r,
                center = this.center,
                style = Stroke(width = 2.6f)
            )
        }
        // Símbolo central
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(size)
        ) {
            Text(
                text = spec.emoji,
                fontSize = (size.value * 0.62f).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        // Chispas que orbitan
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            repeat(minOf(particles.size, 16)) { i ->
                val p = particles[i]
                val ang = p.angle + phase * p.spin * 0.04f
                val dist = (p.distFrac * 1.7f) * this.size.minDimension
                val x = center.x + cos(ang) * dist
                val y = center.y + sin(ang) * dist
                val alpha = (0.4f + p.twinkleAmp * sin(phase * p.twinkleSpeed * PI.toFloat()))
                    .coerceIn(0.15f, 1f)
                drawCircle(
                    color = listOf(spec.primary, spec.secondary, spec.accent)[p.colorIndex]
                        .copy(alpha = alpha),
                    radius = p.sizePx * 0.55f,
                    center = Offset(x, y)
                )
            }
        }
    }
}

@Composable
private fun FullEffect(
    spec: PremiumEffectSpec,
    particles: List<ParticleDef>,
    rayDefs: List<RayDef>,
    progress: Float
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Fondo vignette sutil
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(spec.primary.copy(alpha = 0.18f), Color.Transparent),
                    radius = this.size.minDimension * 0.8f
                ),
                radius = this.size.maxDimension * 0.7f,
                center = this.center
            )
        }
        // Anillos expansivos
        Canvas(modifier = Modifier.fillMaxSize()) {
            repeat(spec.rings.count) { r ->
                val rr = spec.rings.maxRadius * (progress + r.toFloat() / spec.rings.count)
                val alpha = spec.rings.startAlpha * (1f - progress)
                if (rr > 0f) {
                    drawCircle(
                        color = listOf(spec.primary, spec.secondary, spec.accent)[r % 3]
                            .copy(alpha = alpha.coerceIn(0f, 1f)),
                        radius = rr * this.size.minDimension / 360f,
                        center = this.center,
                        style = Stroke(width = 3f * (1f - progress * 0.5f))
                    )
                }
            }
        }
        // Rayos radiales
        Canvas(modifier = Modifier.fillMaxSize()) {
            rayDefs.forEach { ray ->
                val ang = ray.angle + progress * ray.spin * 0.01f
                val len = (ray.lengthFrac * 640f) * (0.6f + progress * 0.6f)
                val start = Offset(
                    x = this.center.x + cos(ang) * (this.size.minDimension * 0.08f),
                    y = this.center.y + sin(ang) * (this.size.minDimension * 0.08f)
                )
                val end = Offset(
                    x = this.center.x + cos(ang) * len,
                    y = this.center.y + sin(ang) * len
                )
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            spec.accent.copy(alpha = 0f),
                            spec.secondary.copy(alpha = spec.rays.alpha)
                        ),
                        start = start,
                        end = end
                    ),
                    start = start,
                    end = end,
                    strokeWidth = ray.thickness,
                    cap = StrokeCap.Round
                )
            }
        }
        // Ráfaga de partículas
        Canvas(modifier = Modifier.fillMaxSize()) {
            particles.take(spec.burst.count.coerceAtMost(80)).forEach { part ->
                val ang = part.angle + progress * part.spin * 0.01f
                val dist = (part.distFrac * 2.2f) * progress * this.size.minDimension
                val x = this.center.x + cos(ang) * dist
                val y = this.center.y + sin(ang) * dist
                val alpha = (1f - progress).coerceIn(0f, 1f) * 0.85f
                drawCircle(
                    color = listOf(spec.primary, spec.secondary, spec.accent)[part.colorIndex]
                        .copy(alpha = alpha),
                    radius = part.sizePx * (1f - progress * 0.5f),
                    center = Offset(x, y)
                )
            }
        }
    }
}

@Composable
private fun BannerSparkle(
    spec: PremiumEffectSpec,
    particles: List<ParticleDef>,
    phase: Float
) {
    Canvas(modifier = Modifier.size(120.dp)) {
        val center = this.center
        particles.take(minOf(particles.size, 12)).forEach { p ->
            val ang = p.angle + phase * p.spin * 0.03f
            val dist = (p.distFrac * 1.2f) * this.size.minDimension
            val x = center.x + cos(ang) * dist
            val y = center.y + sin(ang) * dist
            val alpha = 0.3f + 0.5f * sin(phase * p.twinkleSpeed * PI.toFloat())
            drawCircle(
                color = spec.secondary.copy(alpha = alpha.coerceIn(0.1f, 1f)),
                radius = p.sizePx * 0.5f,
                center = Offset(x, y)
            )
        }
    }
}

private data class ParticleDef(
    val angle: Float,
    val distFrac: Float,
    val sizePx: Float,
    val colorIndex: Int,
    val spin: Float,
    val twinkleAmp: Float,
    val twinkleSpeed: Float
)

private data class RayDef(
    val angle: Float,
    val lengthFrac: Float,
    val thickness: Float,
    val spin: Float
)