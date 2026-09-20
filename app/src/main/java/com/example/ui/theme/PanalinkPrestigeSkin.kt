package com.example.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Paleta "Panalink Prestige": navy profundo + crema/dorado.
 * Valores muestreados del mockup aprobado por el mantenedor.
 */
object PanalinkSkin {
    val NavyBase = Color(0xFF171D29)
    val NavyDeep = Color(0xFF0F141D)

    val Glass = Color(0xF02F3640)        // relleno de la barra de búsqueda
    val GlassStrong = Color(0xF0222A37)  // relleno de las tarjetas de chat
    val GlassSoft = Color(0x992A3242)

    val Cream = Color(0xFFE8D8BA)        // nombres de chat
    val TitleCream = Color(0xFFEBD9B6)   // wordmark "PanaLink" + iconos del header
    val CreamDim = Color(0xFFA2A6AD)     // texto de vista previa
    val Sub = Color(0xFFABAEB7)          // hora / textos secundarios

    val Gold = Color(0xFFC9A96A)
    val GoldBright = Color(0xFFE7D2A0)
    val GoldDeep = Color(0xFF8A6F3E)

    val ReadTick = Color(0xFF9DA0A7)     // tildes de estado (gris claro)
    val Divider = Color(0x33C6B294)

    val CardShape: Shape = RoundedCornerShape(20.dp)

    /** Borde cálido muy sutil, como el del mockup. */
    val borderGradient: List<Color> = listOf(
        Color(0x4DE7D2A0),
        Color(0x338A6F3E),
        Color(0x4DE7D2A0)
    )
}

/** Fondo de "constelación": navy con puntos y líneas conectando vecinos. */
@Composable
fun ConstellationBackground(
    modifier: Modifier = Modifier,
    seed: Int = 20260920,
    pointCount: Int = 38
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(PanalinkSkin.NavyBase, PanalinkSkin.NavyDeep)
            )
        )

        val rnd = Random(seed)
        val points = List(pointCount) {
            Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)
        }
        val linkDistance = size.minDimension * 0.30f

        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                val dx = points[i].x - points[j].x
                val dy = points[i].y - points[j].y
                if (hypot(dx, dy) <= linkDistance) {
                    drawLine(
                        color = Color(0x276B7EA8),
                        start = points[i],
                        end = points[j],
                        strokeWidth = 1.2f
                    )
                }
            }
        }

        points.forEach { p ->
            drawCircle(color = Color(0x3D5A7099), radius = 6f, center = p)
            drawCircle(color = Color(0x80486A9E), radius = 2.4f, center = p)
        }
    }
}

/**
 * Tarjeta translúcida con borde degradado dorado. Las esquinas se controlan con
 * [shape] para que varias tarjetas contiguas formen un solo panel (como el mockup).
 */
@Composable
fun GoldGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = PanalinkSkin.CardShape,
    container: Color = PanalinkSkin.GlassStrong,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .border(1.dp, Brush.linearGradient(PanalinkSkin.borderGradient), shape),
        content = content
    )
}

/** Posición de una fila dentro del panel de chats (controla las esquinas). */
enum class ChatCardPosition { SINGLE, TOP, MIDDLE, BOTTOM }

fun chatCardShape(position: ChatCardPosition): Shape = when (position) {
    ChatCardPosition.SINGLE -> PanalinkSkin.CardShape
    ChatCardPosition.TOP -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ChatCardPosition.MIDDLE -> RoundedCornerShape(0.dp)
    ChatCardPosition.BOTTOM -> RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
}
