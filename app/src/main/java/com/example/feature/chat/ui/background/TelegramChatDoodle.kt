package com.example.feature.chat.ui.background

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlin.math.cos
import kotlin.math.sin

/** Fondo base: oscuro profundo con una pizca de azul, casi negro, para que el
 *  patron se lea sin competir con las burbujas de mensaje. */
val DoodleBaseColor = Color(0xFF0B0E13)

private const val TILE_DP = 104f
private const val GLYPH_DP = 22f
private const val INK_ALPHA = 22 // ~0.09 sobre el blanco del trazo

/**
 * Fondo de chat estilo Telegram: patron repetido de garabatos en trazo fino
 * muy tenue sobre un oscuro profundo. El mosaico se dibuja UNA vez a un Bitmap
 * y se repite con un BitmapShader, para no repintar cientos de trazos por frame.
 */
@Composable
fun TelegramChatDoodle(modifier: Modifier = Modifier) {
    val brush = rememberDoodleBrush()
    Canvas(modifier.fillMaxSize()) {
        drawRect(DoodleBaseColor)
        drawRect(brush = brush)
    }
}

/**
 * Brush del patron de garabatos para usar como fondo de un contenedor completo
 * (p. ej. `Modifier.background(rememberDoodleBrush())`), sin necesidad de un
 * Canvas extra. El mosaico se genera una sola vez por densidad.
 */
@Composable
fun rememberDoodleBrush(): ShaderBrush {
    val density = LocalDensity.current
    val tile = remember(density.density) { createDoodleTile(density) }
    return remember(tile) {
        ShaderBrush(
            android.graphics.BitmapShader(
                tile,
                android.graphics.Shader.TileMode.REPEAT,
                android.graphics.Shader.TileMode.REPEAT
            )
        )
    }
}

private fun createDoodleTile(density: Density): Bitmap {
    val tilePx = (TILE_DP * density.density).toInt().coerceAtLeast(1)
    val glyphPx = GLYPH_DP * density.density
    val bitmap = Bitmap.createBitmap(tilePx, tilePx, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)

    val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = glyphPx * 0.075f
        color = android.graphics.Color.WHITE
        alpha = INK_ALPHA
    }

    // Garabatos desfasados por el mosaico, como el patron de Telegram.
    // Mas densidad: al reducir el mosaico cada glifo se repite mas veces.
    val slots = listOf(
        GlyphSlot(0.20f, 0.20f, -14f, Glyph.HEART),
        GlyphSlot(0.72f, 0.14f, 10f, Glyph.STAR),
        GlyphSlot(0.44f, 0.42f, 4f, Glyph.BUBBLE),
        GlyphSlot(0.88f, 0.44f, -8f, Glyph.NOTE),
        GlyphSlot(0.14f, 0.62f, 12f, Glyph.PLANE),
        GlyphSlot(0.60f, 0.72f, -6f, Glyph.SMILE),
        GlyphSlot(0.34f, 0.90f, 8f, Glyph.STAR),
        GlyphSlot(0.90f, 0.84f, -12f, Glyph.HEART),
    )
    slots.forEach { slot ->
        val cx = slot.x * tilePx
        val cy = slot.y * tilePx
        canvas.save()
        canvas.rotate(slot.rotation, cx, cy)
        drawGlyph(canvas, basePaint, slot.glyph, cx - glyphPx / 2f, cy - glyphPx / 2f, glyphPx)
        canvas.restore()
    }
    return bitmap
}

private data class GlyphSlot(val x: Float, val y: Float, val rotation: Float, val glyph: Glyph)

private enum class Glyph { HEART, STAR, PLANE, NOTE, BUBBLE, SMILE }

/** Dibuja un glifo normalizado en una caja de 24x24 escalada a [size]. */
private fun drawGlyph(canvas: AndroidCanvas, paint: Paint, glyph: Glyph, left: Float, top: Float, size: Float) {
    val u = size / 24f
    val path = Path()
    fun x(v: Float) = left + v * u
    fun y(v: Float) = top + v * u

    when (glyph) {
        Glyph.HEART -> {
            path.moveTo(x(12f), y(21f))
            path.cubicTo(x(3f), y(14f), x(3f), y(8f), x(3f), y(8f))
            path.cubicTo(x(3f), y(4f), x(8f), y(4f), x(8f), y(4f))
            path.cubicTo(x(10f), y(4f), x(12f), y(7f), x(12f), y(7f))
            path.cubicTo(x(14f), y(4f), x(16f), y(4f), x(16f), y(4f))
            path.cubicTo(x(21f), y(4f), x(21f), y(8f), x(21f), y(8f))
            path.cubicTo(x(21f), y(14f), x(12f), y(21f), x(12f), y(21f))
            canvas.drawPath(path, paint)
        }
        Glyph.STAR -> {
            for (i in 0 until 10) {
                val radius = if (i % 2 == 0) 10f else 4.4f
                val angle = (-90f + i * 36f) * Math.PI.toFloat() / 180f
                val px = x(12f + radius * cos(angle))
                val py = y(12f + radius * sin(angle))
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            canvas.drawPath(path, paint)
        }
        Glyph.PLANE -> {
            path.moveTo(x(21f), y(3f))
            path.lineTo(x(3f), y(11f))
            path.lineTo(x(10f), y(13f))
            path.lineTo(x(12f), y(21f))
            path.close()
            canvas.drawPath(path, paint)
        }
        Glyph.NOTE -> {
            path.moveTo(x(15f), y(3f))
            path.lineTo(x(15f), y(15f))
            canvas.drawPath(path, paint)
            canvas.drawCircle(x(11.5f), y(18f), 3.6f * u, paint)
            val head = Path()
            head.moveTo(x(9f), y(4.5f))
            head.lineTo(x(15f), y(3f))
            canvas.drawPath(head, paint)
        }
        Glyph.BUBBLE -> {
            path.moveTo(x(4f), y(5f))
            path.lineTo(x(20f), y(5f))
            path.lineTo(x(20f), y(15f))
            path.lineTo(x(10f), y(15f))
            path.lineTo(x(6f), y(20f))
            path.lineTo(x(6f), y(15f))
            path.lineTo(x(4f), y(15f))
            path.close()
            canvas.drawPath(path, paint)
        }
        Glyph.SMILE -> {
            canvas.drawCircle(x(12f), y(12f), 9f * u, paint)
            val eyes = Paint(paint).apply { strokeWidth = paint.strokeWidth * 2.4f }
            canvas.drawPoint(x(9f), y(9.5f), eyes)
            canvas.drawPoint(x(15f), y(9.5f), eyes)
            val mouth = Path()
            mouth.moveTo(x(8.5f), y(14f))
            mouth.quadTo(x(15.5f), y(14f), x(12f), y(17f))
            canvas.drawPath(mouth, paint)
        }
    }
}
