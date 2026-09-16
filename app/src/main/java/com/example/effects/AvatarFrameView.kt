package com.example.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Dibuja un marco de avatar ([AvatarFrameCatalog]) alrededor de la foto del asiento.
 *
 * El lienzo es un cuadrado de `avatarSize * spec.overflowScale` centrado sobre el
 * avatar. Todo se dibuja con primitivas vectoriales derivadas del lado del lienzo,
 * asi que no hay reescalado de bitmaps ni deformacion (sin stretch) en ninguna
 * densidad ni tamano de pantalla.
 *
 * El circulo central (radio `rFrame / overflowScale`) se deja vacio para que la foto
 * del asiento se vea completa: el marco solo anade material hacia afuera.
 *
 * @param code codigo del marco (`gold`, `crown`, ...). `none`/desconocido no dibuja nada.
 * @param avatarSize diametro del circulo del avatar tal como lo pinta el asiento.
 * @param animated desactiva la animacion (listas, previews, ahorro de bateria).
 */
@Composable
fun AvatarFrameView(
    code: String?,
    avatarSize: Dp,
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    val spec = AvatarFrameCatalog.byCode(code) ?: return
    val frameSize = avatarSize * spec.overflowScale
    val phase = remember { Animatable(0f) }

    // Marcos raster: el arte ya viene con el hueco del avatar recortado y el diseno
    // completo (aro + banner), asi que se dibuja tal cual, sin rotar ni petalos.
    val bitmap = if (spec.bitmapRes != 0) {
        ImageBitmap.imageResource(spec.bitmapRes)
    } else {
        null
    }
    val cycling = animated && spec.animated && bitmap == null

    LaunchedEffect(spec.code, cycling) {
        if (cycling) {
            phase.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = SPIN_CYCLE_MS, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            phase.snapTo(0.35f)
        }
    }

    Canvas(modifier = modifier.size(frameSize)) {
        if (bitmap != null) {
            val side = size.minDimension.roundToInt().coerceAtLeast(1)
            drawImage(
                image = bitmap,
                dstOffset = IntOffset(
                    ((size.width - side) / 2f).roundToInt(),
                    ((size.height - side) / 2f).roundToInt()
                ),
                dstSize = IntSize(side, side),
                filterQuality = FilterQuality.High
            )
        } else {
            drawAvatarFrame(spec, phase.value)
        }
    }
}

private const val SPIN_CYCLE_MS = 14000

private fun DrawScope.drawAvatarFrame(spec: AvatarFrameSpec, phase: Float) {
    val d = size.minDimension
    if (d <= 0f) return

    val cx = size.width / 2f
    val cy = size.height / 2f
    val rFrame = d / 2f
    val rAvatar = rFrame / spec.overflowScale
    val bandWidth = rFrame * spec.bandWidth
    val rBand = rAvatar + bandWidth * 0.5f
    val bandOuter = rAvatar + bandWidth
    val petalOuter = rFrame * spec.petalOuterRatio
    val center = Offset(cx, cy)
    val pulse = 0.80f + 0.20f * sin(phase * TWO_PI)

    drawAura(spec, center, rFrame, rAvatar, pulse)

    rotate(degrees = phase * 360f, pivot = center) {
        if (spec.frill > 0.01f) {
            drawPetals(spec, cx, cy, bandOuter - rFrame * 0.02f, petalOuter, rFrame, spec.frill)
        }
        drawBand(spec, center, rBand, bandWidth)
        drawGems(spec, cx, cy, rBand, rFrame)
    }

    drawRims(spec, center, rAvatar, bandOuter, rFrame)
    drawOrnaments(spec, cx, cy, rAvatar, bandOuter, rFrame, phase)
    drawSparkles(spec, cx, cy, rAvatar, rFrame, phase)
}

// === Aura / halo de fondo ===

private fun DrawScope.drawAura(
    spec: AvatarFrameSpec,
    center: Offset,
    rFrame: Float,
    rAvatar: Float,
    pulse: Float
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                spec.glow.copy(alpha = 0.20f * pulse),
                spec.glow.copy(alpha = 0.06f * pulse),
                Color.Transparent
            ),
            center = center,
            radius = rFrame
        ),
        radius = rFrame,
        center = center
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(spec.glow.copy(alpha = 0.14f * pulse), Color.Transparent),
            center = center,
            radius = rFrame * 0.9f
        ),
        radius = rFrame * 0.9f,
        center = center,
        style = Stroke(width = maxOf(rFrame * 0.02f, 0.5f))
    )
    if (rAvatar > 0f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent),
                center = center,
                radius = rAvatar * 1.25f
            ),
            radius = rAvatar * 1.25f,
            center = center,
            style = Stroke(width = maxOf(rAvatar * 0.12f, 0.5f))
        )
    }
}

// === Petalos / fruncido ===

private fun DrawScope.drawPetals(
    spec: AvatarFrameSpec,
    cx: Float,
    cy: Float,
    inner: Float,
    outer: Float,
    rFrame: Float,
    intensity: Float
) {
    if (outer <= inner) return
    val n = spec.petalCount.coerceAtLeast(3)
    val step = TWO_PI / n
    val halfWidth = step * 0.44f
    val lift = outer - inner

    for (i in 0 until n) {
        val angle = i * step
        val long = i % 2 == 0
        val rOut = if (long) outer else outer - lift * 0.32f
        val path = petalPath(cx, cy, angle, inner, rOut, halfWidth)
        val grad = Brush.linearGradient(
            colors = listOf(
                spec.primary.copy(alpha = 0.92f * intensity),
                spec.secondary.copy(alpha = 0.96f * intensity),
                spec.accent.copy(alpha = 0.82f * intensity)
            ),
            start = polar(cx, cy, angle, inner),
            end = polar(cx, cy, angle, rOut)
        )
        drawPath(path, grad)
        drawPath(path, spec.accent.copy(alpha = 0.50f * intensity), style = stroke(rFrame * 0.006f))
    }
}

private fun petalPath(
    cx: Float,
    cy: Float,
    angle: Float,
    rIn: Float,
    rOut: Float,
    halfWidth: Float
): Path {
    val baseLeft = polar(cx, cy, angle - halfWidth, rIn)
    val baseRight = polar(cx, cy, angle + halfWidth, rIn)
    val tip = polar(cx, cy, angle, rOut)
    val mid = (rIn + rOut) * 0.5f
    val c1 = polar(cx, cy, angle - halfWidth * 0.85f, mid)
    val c2 = polar(cx, cy, angle - halfWidth * 0.35f, rOut * 0.985f)
    val c3 = polar(cx, cy, angle + halfWidth * 0.35f, rOut * 0.985f)
    val c4 = polar(cx, cy, angle + halfWidth * 0.85f, mid)
    return Path().apply {
        moveTo(baseLeft.x, baseLeft.y)
        cubicTo(c1.x, c1.y, c2.x, c2.y, tip.x, tip.y)
        cubicTo(c3.x, c3.y, c4.x, c4.y, baseRight.x, baseRight.y)
        close()
    }
}

// === Aro principal ===

private fun DrawScope.drawBand(spec: AvatarFrameSpec, center: Offset, rBand: Float, bandWidth: Float) {
    drawCircle(
        brush = Brush.sweepGradient(
            colors = listOf(spec.primary, spec.secondary, spec.accent, spec.secondary, spec.primary),
            center = center
        ),
        radius = rBand,
        center = center,
        style = stroke(bandWidth)
    )
    drawCircle(
        color = spec.accent.copy(alpha = 0.35f),
        radius = rBand - bandWidth * 0.34f,
        center = center,
        style = stroke(maxOf(bandWidth * 0.08f, 0.5f))
    )
}

private fun DrawScope.drawRims(
    spec: AvatarFrameSpec,
    center: Offset,
    rAvatar: Float,
    bandOuter: Float,
    rFrame: Float
) {
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(spec.accent, spec.secondary, spec.accent),
            start = Offset(center.x - rAvatar, center.y - rAvatar),
            end = Offset(center.x + rAvatar, center.y + rAvatar)
        ),
        radius = rAvatar + rFrame * 0.014f,
        center = center,
        style = stroke(rFrame * 0.016f)
    )
    drawCircle(
        color = spec.secondary.copy(alpha = 0.85f),
        radius = bandOuter,
        center = center,
        style = stroke(rFrame * 0.012f)
    )
}

private fun DrawScope.drawGems(spec: AvatarFrameSpec, cx: Float, cy: Float, rBand: Float, rFrame: Float) {
    val n = spec.gemCount.coerceIn(4, 24)
    val step = TWO_PI / n
    val s = rFrame * 0.026f
    for (i in 0 until n) {
        val c = polar(cx, cy, i * step, rBand)
        val path = Path().apply {
            moveTo(c.x, c.y - s)
            lineTo(c.x + s, c.y)
            lineTo(c.x, c.y + s)
            lineTo(c.x - s, c.y)
            close()
        }
        drawPath(path, spec.accent)
        drawPath(path, Color.White.copy(alpha = 0.85f), style = stroke(rFrame * 0.007f))
    }
}

// === Ornamentos ===

private fun DrawScope.drawOrnaments(
    spec: AvatarFrameSpec,
    cx: Float,
    cy: Float,
    rAvatar: Float,
    bandOuter: Float,
    rFrame: Float,
    phase: Float
) {
    if (FrameOrnament.FEATHERS in spec.ornaments) drawFeatherCrest(spec, cx, cy, bandOuter, rFrame)
    if (FrameOrnament.WINGS in spec.ornaments) drawWings(spec, cx, cy, bandOuter, rFrame)
    if (FrameOrnament.FLAMES in spec.ornaments) drawFlames(spec, cx, cy, bandOuter, rFrame, phase)
    if (FrameOrnament.SPIKES in spec.ornaments) drawSpikes(spec, cx, cy, bandOuter, rFrame)
    if (FrameOrnament.LEAVES in spec.ornaments) drawLeaves(spec, cx, cy, bandOuter, rFrame)
    if (FrameOrnament.STARS in spec.ornaments) drawStarRing(spec, cx, cy, bandOuter, rFrame, phase)
    if (FrameOrnament.HEARTS in spec.ornaments) drawHeartRing(spec, cx, cy, bandOuter, rFrame, phase)
    if (FrameOrnament.BUBBLES in spec.ornaments) drawBubbles(spec, cx, cy, rFrame, phase)
    if (FrameOrnament.BOLTS in spec.ornaments) drawBolts(spec, cx, cy, bandOuter, rFrame, phase)
    if (FrameOrnament.HALO in spec.ornaments) drawHalo(spec, cx, cy, rFrame)
    if (FrameOrnament.CROWN in spec.ornaments) drawCrown(spec, cx, cy, bandOuter, rFrame)
}

private fun DrawScope.drawCrown(spec: AvatarFrameSpec, cx: Float, cy: Float, bandOuter: Float, rFrame: Float) {
    val baseY = cy - bandOuter + rFrame * 0.02f
    val halfWidth = rFrame * 0.27f
    val height = rFrame * 0.26f
    val x0 = cx - halfWidth
    val w = halfWidth * 2f
    val peaks = floatArrayOf(0.55f, 0.85f, 1f, 0.85f, 0.55f)
    val segment = w / peaks.size

    val path = Path().apply {
        moveTo(x0, baseY)
        for (i in peaks.indices) {
            val xRight = x0 + segment * (i + 1)
            val xTip = xRight - segment * 0.5f
            lineTo(xTip, baseY - height * peaks[i])
            lineTo(xRight, baseY - height * 0.18f)
        }
        lineTo(x0 + w, baseY)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(spec.primary, spec.secondary, spec.accent, spec.secondary),
            start = Offset(x0, baseY - height),
            end = Offset(x0 + w, baseY)
        )
    )
    drawPath(path, spec.accent.copy(alpha = 0.9f), style = stroke(rFrame * 0.012f))
    drawLine(
        color = spec.secondary,
        start = Offset(x0, baseY),
        end = Offset(x0 + w, baseY),
        strokeWidth = maxOf(rFrame * 0.028f, 0.5f),
        cap = StrokeCap.Round
    )
    for (i in peaks.indices) {
        val xTip = x0 + segment * i + segment * 0.5f
        drawCircle(spec.accent, radius = rFrame * 0.020f, center = Offset(xTip, baseY - height * peaks[i]))
    }
}

private fun DrawScope.drawWings(spec: AvatarFrameSpec, cx: Float, cy: Float, bandOuter: Float, rFrame: Float) {
    val feathers = 7
    for (side in intArrayOf(-1, 1)) {
        for (k in 0 until feathers) {
            val t = k / (feathers - 1f)
            val angle = -HALF_PI + side * (0.30f + t * 0.95f)
            val rOut = rFrame * (0.94f - 0.22f * t)
            val rIn = bandOuter * 0.96f
            if (rOut <= rIn) continue
            val path = featherPath(cx, cy, angle, rIn, rOut, rFrame * 0.052f)
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(
                        spec.accent.copy(alpha = 0.95f),
                        spec.secondary.copy(alpha = 0.90f),
                        spec.primary.copy(alpha = 0.70f)
                    ),
                    start = polar(cx, cy, angle, rIn),
                    end = polar(cx, cy, angle, rOut)
                )
            )
            drawPath(path, spec.accent.copy(alpha = 0.55f), style = stroke(rFrame * 0.008f))
        }
    }
}

private fun featherPath(
    cx: Float,
    cy: Float,
    angle: Float,
    rIn: Float,
    rOut: Float,
    halfWidth: Float
): Path {
    val dirX = cos(angle)
    val dirY = sin(angle)
    val perpX = -dirY
    val perpY = dirX
    val bx = cx + dirX * rIn
    val by = cy + dirY * rIn
    val tx = cx + dirX * rOut
    val ty = cy + dirY * rOut
    val midR = (rIn + rOut) * 0.5f
    val mx = cx + dirX * midR
    val my = cy + dirY * midR
    val bend = halfWidth * 0.55f

    return Path().apply {
        moveTo(bx + perpX * halfWidth, by + perpY * halfWidth)
        cubicTo(
            mx + perpX * halfWidth * 1.5f, my + perpY * halfWidth * 1.5f,
            tx + perpX * bend, ty + perpY * bend,
            tx, ty
        )
        cubicTo(
            mx - perpX * halfWidth * 1.5f, my - perpY * halfWidth * 1.5f,
            bx - perpX * halfWidth, by - perpY * halfWidth,
            bx - perpX * halfWidth, by - perpY * halfWidth
        )
        close()
    }
}

private fun DrawScope.drawFeatherCrest(
    spec: AvatarFrameSpec,
    cx: Float,
    cy: Float,
    bandOuter: Float,
    rFrame: Float
) {
    val n = 11
    for (i in 0 until n) {
        val t = i / (n - 1f)
        val angle = -HALF_PI + (t - 0.5f) * 2.5f
        val rOut = rFrame * (0.90f + 0.05f * sin(t * PI.toFloat()))
        val rIn = bandOuter * 0.94f
        val path = featherPath(cx, cy, angle, rIn, rOut, rFrame * 0.030f)
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(
                    spec.primary.copy(alpha = 0.95f),
                    spec.secondary.copy(alpha = 0.95f),
                    spec.accent.copy(alpha = 0.9f)
                ),
                start = polar(cx, cy, angle, rIn),
                end = polar(cx, cy, angle, rOut)
            )
        )
        drawPath(path, spec.accent.copy(alpha = 0.5f), style = stroke(rFrame * 0.006f))
        val tip = polar(cx, cy, angle, rOut * 0.98f)
        drawCircle(spec.accent, radius = rFrame * 0.014f, center = tip)
    }
}

private fun DrawScope.drawFlames(
    spec: AvatarFrameSpec,
    cx: Float,
    cy: Float,
    bandOuter: Float,
    rFrame: Float,
    phase: Float
) {
    val n = 12
    val step = TWO_PI / n
    for (i in 0 until n) {
        val angle = i * step + 0.26f
        val flicker = 0.82f + 0.18f * sin(phase * TWO_PI * 3f + i * 0.9f)
        val rIn = bandOuter * 0.94f
        val rOut = rFrame * (0.66f + 0.28f * flicker)
        if (rOut <= rIn) continue
        val path = flamePath(cx, cy, angle, rIn, rOut, rFrame * 0.062f, if (i % 2 == 0) 1f else -1f)
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(
                    spec.accent.copy(alpha = 0.98f),
                    spec.secondary.copy(alpha = 0.92f),
                    spec.primary.copy(alpha = 0.85f)
                ),
                start = polar(cx, cy, angle, rIn),
                end = polar(cx, cy, angle, rOut)
            )
        )
        drawPath(path, spec.accent.copy(alpha = 0.45f), style = stroke(rFrame * 0.007f))
    }
}

private fun flamePath(
    cx: Float,
    cy: Float,
    angle: Float,
    rIn: Float,
    rOut: Float,
    halfWidth: Float,
    bendSign: Float
): Path {
    val halfAngle = halfWidth / maxOf(rIn, 1f)
    val baseLeft = polar(cx, cy, angle - halfAngle, rIn)
    val baseRight = polar(cx, cy, angle + halfAngle, rIn)
    val tipAngle = angle + bendSign * 0.16f
    val tip = polar(cx, cy, tipAngle, rOut)
    val midR = rIn + (rOut - rIn) * 0.55f
    val c1 = polar(cx, cy, angle - halfAngle * 0.6f, midR)
    val c2 = polar(cx, cy, tipAngle - bendSign * 0.32f, rOut * 0.90f)
    val c3 = polar(cx, cy, tipAngle + bendSign * 0.32f, rOut * 0.90f)
    val c4 = polar(cx, cy, angle + halfAngle * 0.6f, midR)

    return Path().apply {
        moveTo(baseLeft.x, baseLeft.y)
        cubicTo(c1.x, c1.y, c2.x, c2.y, tip.x, tip.y)
        cubicTo(c3.x, c3.y, c4.x, c4.y, baseRight.x, baseRight.y)
        close()
    }
}

private fun DrawScope.drawSpikes(
    spec: AvatarFrameSpec,
    cx: Float,
    cy: Float,
    bandOuter: Float,
    rFrame: Float
) {
    val n = 18
    val step = TWO_PI / n
    val inner = bandOuter * 0.98f
    val outer = rFrame * 0.97f
    for (i in 0 until n) {
        val angle = i * step
        val long = i % 2 == 0
        val rOut = if (long) outer else outer - rFrame * 0.09f
        val baseLeft = polar(cx, cy, angle - step * 0.34f, inner)
        val baseRight = polar(cx, cy, angle + step * 0.34f, inner)
        val tip = polar(cx, cy, angle, rOut)
        val path = Path().apply {
            moveTo(baseLeft.x, baseLeft.y)
            lineTo(tip.x, tip.y)
            lineTo(baseRight.x, baseRight.y)
            close()
        }
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(spec.accent.copy(alpha = 0.95f), spec.primary.copy(alpha = 0.85f)),
                start = polar(cx, cy, angle, inner),
                end = polar(cx, cy, angle, rOut)
            )
        )
        drawPath(path, spec.accent.copy(alpha = 0.6f), style = stroke(rFrame * 0.006f))
    }
}

private fun DrawScope.drawLeaves(spec: AvatarFrameSpec, cx: Float, cy: Float, bandOuter: Float, rFrame: Float) {
    val n = 10
    val step = TWO_PI / n
    val inner = bandOuter - rFrame * 0.03f
    for (i in 0 until n) {
        val angle = i * step + step * 0.5f
        val rOut = rFrame * if (i % 2 == 0) 0.95f else 0.82f
        val side = if (i % 2 == 0) 1f else -1f
        val path = leafPath(cx, cy, angle + side * 0.18f, inner, rOut, rFrame * 0.085f)
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(spec.secondary.copy(alpha = 0.95f), spec.primary.copy(alpha = 0.9f)),
                start = polar(cx, cy, angle, inner),
                end = polar(cx, cy, angle, rOut)
            )
        )
        val spineStart = polar(cx, cy, angle + side * 0.18f, inner)
        val spineEnd = polar(cx, cy, angle + side * 0.18f, rOut * 0.92f)
        drawLine(
            color = spec.accent.copy(alpha = 0.7f),
            start = spineStart,
            end = spineEnd,
            strokeWidth = maxOf(rFrame * 0.008f, 0.5f),
            cap = StrokeCap.Round
        )
    }
}

private fun leafPath(
    cx: Float,
    cy: Float,
    angle: Float,
    rIn: Float,
    rOut: Float,
    halfWidth: Float
): Path {
    val dirX = cos(angle)
    val dirY = sin(angle)
    val perpX = -dirY
    val perpY = dirX
    val bx = cx + dirX * rIn
    val by = cy + dirY * rIn
    val tx = cx + dirX * rOut
    val ty = cy + dirY * rOut
    val midR = (rIn + rOut) * 0.5f
    val mx = cx + dirX * midR
    val my = cy + dirY * midR

    return Path().apply {
        moveTo(bx, by)
        cubicTo(
            mx + perpX * halfWidth, my + perpY * halfWidth,
            tx + perpX * halfWidth * 0.35f, ty + perpY * halfWidth * 0.35f,
            tx, ty
        )
        cubicTo(
            mx - perpX * halfWidth, my - perpY * halfWidth,
            bx, by,
            bx, by
        )
        close()
    }
}

private fun DrawScope.drawStarRing(
    spec: AvatarFrameSpec,
    cx: Float,
    cy: Float,
    bandOuter: Float,
    rFrame: Float,
    phase: Float
) {
    val n = 8
    val step = TWO_PI / n
    val radius = bandOuter + rFrame * 0.10f
    for (i in 0 until n) {
        val angle = i * step + phase * 0.6f
        val c = polar(cx, cy, angle, radius)
        val scale = 0.75f + 0.25f * sin(phase * TWO_PI * 2f + i)
        val path = starPath(c.x, c.y, rFrame * 0.085f * scale, 0.32f, 4, angle)
        drawPath(path, Brush.radialGradient(listOf(Color.White, spec.accent), center = c, radius = rFrame * 0.09f))
        drawPath(path, spec.secondary.copy(alpha = 0.8f), style = stroke(rFrame * 0.005f))
    }
}

private fun DrawScope.drawHeartRing(
    spec: AvatarFrameSpec,
    cx: Float,
    cy: Float,
    bandOuter: Float,
    rFrame: Float,
    phase: Float
) {
    val n = 7
    val step = TWO_PI / n
    val radius = bandOuter + rFrame * 0.11f
    for (i in 0 until n) {
        val angle = i * step + 0.4f
        val c = polar(cx, cy, angle, radius)
        val scale = 0.8f + 0.2f * sin(phase * TWO_PI * 2f + i * 0.8f)
        val path = heartPath(c.x, c.y, rFrame * 0.062f * scale)
        drawPath(
            path = path,
            brush = Brush.radialGradient(listOf(spec.accent, spec.secondary), center = c, radius = rFrame * 0.075f)
        )
        drawPath(path, spec.accent.copy(alpha = 0.85f), style = stroke(rFrame * 0.005f))
    }
}

private fun DrawScope.drawBubbles(
    spec: AvatarFrameSpec,
    cx: Float,
    cy: Float,
    rFrame: Float,
    phase: Float
) {
    val rnd = Random(spec.code.hashCode() * 17 + 3)
    repeat(10) { i ->
        val baseAngle = rnd.nextFloat() * TWO_PI
        val radius = rFrame * (0.60f + rnd.nextFloat() * 0.34f)
        val rise = (phase + i * 0.13f) % 1f
        val angle = baseAngle + rise * 0.5f
        val c = polar(cx, cy, angle, radius)
        val r = rFrame * (0.02f + rnd.nextFloat() * 0.035f)
        val alpha = (0.55f * (1f - rise) + 0.15f).coerceIn(0.1f, 0.8f)
        drawCircle(
            color = spec.accent.copy(alpha = alpha * 0.55f),
            radius = r,
            center = c
        )
        drawCircle(
            color = spec.accent.copy(alpha = alpha),
            radius = r,
            center = c,
            style = stroke(rFrame * 0.006f)
        )
        drawCircle(
            color = Color.White.copy(alpha = alpha * 0.8f),
            radius = r * 0.28f,
            center = Offset(c.x - r * 0.32f, c.y - r * 0.32f)
        )
    }
}

private fun DrawScope.drawBolts(
    spec: AvatarFrameSpec,
    cx: Float,
    cy: Float,
    bandOuter: Float,
    rFrame: Float,
    phase: Float
) {
    // Los rayos salen del aro hacia afuera: nunca deben invadir el hueco del avatar.
    val angles = floatArrayOf(-2.35f, -0.79f, HALF_PI)
    angles.forEachIndexed { index, angle ->
        val flicker = 0.55f + 0.45f * abs(sin(phase * TWO_PI * 2f + index * 1.7f))
        val rIn = bandOuter * 0.98f
        val length = rFrame * (0.16f + 0.10f * flicker)
        val path = boltPath(cx, cy, angle, rIn, length, rFrame * 0.048f)
        drawPath(path, spec.accent.copy(alpha = flicker))
        drawPath(path, spec.secondary.copy(alpha = flicker * 0.9f), style = stroke(rFrame * 0.008f))
    }
}

private fun boltPath(
    cx: Float,
    cy: Float,
    angle: Float,
    rIn: Float,
    length: Float,
    halfWidth: Float
): Path {
    val dirX = cos(angle)
    val dirY = sin(angle)
    val perpX = -dirY
    val perpY = dirX

    fun point(distance: Float, offset: Float) = Offset(
        cx + dirX * distance + perpX * offset,
        cy + dirY * distance + perpY * offset
    )

    val p0 = point(rIn, halfWidth)
    val p1 = point(rIn + length * 0.40f, halfWidth * 1.45f)
    val p2 = point(rIn + length * 0.42f, halfWidth * 0.15f)
    val p3 = point(rIn + length, 0f)
    val p4 = point(rIn + length * 0.56f, -halfWidth * 0.20f)
    val p5 = point(rIn + length * 0.58f, -halfWidth * 1.45f)
    val p6 = point(rIn, -halfWidth)

    return Path().apply {
        moveTo(p0.x, p0.y)
        lineTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        lineTo(p3.x, p3.y)
        lineTo(p4.x, p4.y)
        lineTo(p5.x, p5.y)
        lineTo(p6.x, p6.y)
        close()
    }
}

private fun DrawScope.drawHalo(spec: AvatarFrameSpec, cx: Float, cy: Float, rFrame: Float) {
    val haloCy = cy - rFrame * 0.88f
    val halfW = rFrame * 0.34f
    val halfH = rFrame * 0.075f
    drawOval(
        color = spec.glow.copy(alpha = 0.30f),
        topLeft = Offset(cx - halfW * 1.25f, haloCy - halfH * 2.2f),
        size = Size(halfW * 2.5f, halfH * 4.4f)
    )
    drawOval(
        brush = Brush.verticalGradient(listOf(spec.accent, spec.secondary)),
        topLeft = Offset(cx - halfW, haloCy - halfH),
        size = Size(halfW * 2f, halfH * 2f),
        style = stroke(rFrame * 0.045f)
    )
}

// === Destellos ===

private fun DrawScope.drawSparkles(
    spec: AvatarFrameSpec,
    cx: Float,
    cy: Float,
    rAvatar: Float,
    rFrame: Float,
    phase: Float
) {
    val rnd = Random(spec.code.hashCode() * 31 + 11)
    repeat(9) { i ->
        val angle = rnd.nextFloat() * TWO_PI
        // Siempre entre el borde del avatar y el limite del lienzo.
        val radius = rAvatar + (rFrame - rAvatar) * (0.15f + rnd.nextFloat() * 0.80f)
        val c = polar(cx, cy, angle, radius)
        val twinkle = 0.35f + 0.65f * abs(sin(phase * TWO_PI * 2f + i * 1.3f))
        val size = rFrame * (0.018f + rnd.nextFloat() * 0.020f) * (0.7f + 0.3f * twinkle)
        val path = starPath(c.x, c.y, size, 0.22f, 4, angle)
        drawPath(path, Color.White.copy(alpha = twinkle))
        drawPath(path, spec.accent.copy(alpha = twinkle * 0.6f), style = stroke(rFrame * 0.004f))
    }
}

// === Helpers ===

private const val TWO_PI = 6.2831855f
private const val HALF_PI = 1.5707964f

private fun stroke(width: Float) = Stroke(width = maxOf(width, 0.5f))

private fun polar(cx: Float, cy: Float, angle: Float, radius: Float): Offset =
    Offset(cx + cos(angle) * radius, cy + sin(angle) * radius)

private fun starPath(
    cx: Float,
    cy: Float,
    radius: Float,
    innerRatio: Float,
    points: Int,
    rotation: Float
): Path {
    val total = points * 2
    val start = rotation - HALF_PI
    return Path().apply {
        for (i in 0 until total) {
            val r = if (i % 2 == 0) radius else radius * innerRatio
            val angle = start + i * PI.toFloat() / points
            val x = cx + cos(angle) * r
            val y = cy + sin(angle) * r
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

private fun heartPath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    moveTo(cx, cy + r * 0.85f)
    cubicTo(
        cx - r * 1.6f, cy - r * 0.35f,
        cx - r * 0.55f, cy - r * 1.35f,
        cx, cy - r * 0.45f
    )
    cubicTo(
        cx + r * 0.55f, cy - r * 1.35f,
        cx + r * 1.6f, cy - r * 0.35f,
        cx, cy + r * 0.85f
    )
    close()
}
