package com.example.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.random.Random

/**
 * Paleta de marca viva (observable). Existe porque en este proyecto hay del orden
 * de mil usos de `Color.White`/`Color.Black` repartidos por las pantallas: exponer
 * los colores como `getter` plano permite que cualquier sitio (composable o no,
 * incluidos valores por defecto de parametros) lea el color vigente del tema.
 *
 * Al ser `mutableStateOf`, cualquier composable que lea estos colores se
 * suscribe y se recompone solo cuando el usuario cambia entre claro y oscuro.
 */
object PanalinkPalette {
    var isDark by mutableStateOf(true)
        internal set

    /** Fondo base: navy del mockup / blanco estilo Instagram. */
    val background: Color get() = if (isDark) Color(0xFF17212B) else Color(0xFFFFFFFF)

    /** Superficie de tarjetas y barras. */
    val surface: Color get() = if (isDark) Color(0xFF202B36) else Color(0xFFFFFFFF)

    /** Texto principal: blanco sobre oscuro, casi negro sobre claro. */
    val textPrimary: Color get() = if (isDark) Color(0xFFFFFFFF) else Color(0xFF0B0F14)

    /** Texto secundario (horas, vista previa, subtitulos). */
    val textSecondary: Color get() = if (isDark) Color(0xFFB6C2CF) else Color(0xFF5C6675)

    /** Titulos de marca: champan sobre oscuro, bronce (legible) sobre claro. */
    val accent: Color get() = if (isDark) Color(0xFF35D07F) else Color(0xFF1DA060)

    /** Dorado de acentos e iconos. */
    val gold: Color get() = if (isDark) Color(0xFF35D07F) else Color(0xFF1DA060)

    val divider: Color get() = if (isDark) Color(0x3328E8F5) else Color(0x247A6234)

    val glass: Color get() = if (isDark) Color(0xE6202B36) else Color(0xF2FFFFFF)
    val glassBorder: Color get() = if (isDark) Color(0x6635D07F) else Color(0x268A6F3E)

    /** Verde de marca (online, acentos vivos). */
    val online: Color get() = if (isDark) Color(0xFF3FCF8E) else Color(0xFF1DA060)
}

/**
 * Paleta "Panalink Prestige": navy profundo + crema/dorado.
 * Valores muestreados del mockup aprobado por el mantenedor.
 */
object PanalinkSkin {
    val NavyBase = Color(0xFF17212B)
    val NavyDeep = Color(0xFF0E1621)

    // Cristales y textos: dependen del tema para que el modo claro sea legible
    // (tinta oscura sobre superficies claras) sin tocar cada pantalla.
    val Glass: Color get() = if (PanalinkPalette.isDark) Color(0xE6202B36) else Color(0xF2F1F3F7)
    val GlassStrong: Color get() = if (PanalinkPalette.isDark) Color(0xF025303B) else Color(0xF2FFFFFF)
    val GlassSoft: Color get() = if (PanalinkPalette.isDark) Color(0x66202B36) else Color(0x99FFFFFF)

    val Cream: Color get() = if (PanalinkPalette.isDark) Color(0xFFFFFFFF) else Color(0xFF1B2330)
    val TitleCream: Color get() = if (PanalinkPalette.isDark) Color(0xFFF5E6C8) else Color(0xFF6F5A2F)
    val CreamDim: Color get() = if (PanalinkPalette.isDark) Color(0xFFB6C2CF) else Color(0xFF5C6675)
    val Sub: Color get() = if (PanalinkPalette.isDark) Color(0xFFB8C4D6) else Color(0xFF5C6675)

    val Gold: Color get() = if (PanalinkPalette.isDark) Color(0xFF35D07F) else Color(0xFF8A6F3E)
    val GoldBright: Color get() = if (PanalinkPalette.isDark) Color(0xFF35D07F) else Color(0xFF8A6F3E)
    val GoldDeep = Color(0xFF8A6F3E)

    val ReadTick: Color get() = if (PanalinkPalette.isDark) Color(0xFF9DA0A7) else Color(0xFF8A9099)
    val Divider: Color get() = if (PanalinkPalette.isDark) Color(0x33C6B294) else Color(0x1F8A6F3E)

    val CardShape: Shape = RoundedCornerShape(20.dp)

    /** Borde cálido muy sutil, como el del mockup. */
    val borderGradient: List<Color>
        get() = if (PanalinkPalette.isDark) {
            listOf(Color(0x6635D07F), Color(0x3335D07F), Color(0x6635D07F))
        } else {
            listOf(Color(0x3D8A6F3E), Color(0x1F8A6F3E), Color(0x3D8A6F3E))
        }

    /** Gradiente de la barra flotante: verde-teal -> indigo (mockup). */
    val barGradient: List<Color> = listOf(
        Color(0xFF1D5C4E),
        Color(0xFF17293F),
        Color(0xFF241E46)
    )

    /** Borde luminoso menta de la barra flotante. */
    val barGlow = Color(0xFF35D07F)
}

/** Posición de una fila dentro del panel continuo de chats. */
enum class ChatCardPosition { SINGLE, TOP, MIDDLE, BOTTOM }

fun chatCardShape(position: ChatCardPosition): Shape = when (position) {
    ChatCardPosition.SINGLE -> PanalinkSkin.CardShape
    ChatCardPosition.TOP -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ChatCardPosition.MIDDLE -> RoundedCornerShape(0.dp)
    ChatCardPosition.BOTTOM -> RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
}

/**
 * Iconos de la barra inferior en estilo Rounded relleno (el lenguaje visual de
 * las apps modernas: trazo grueso, esquinas suaves, silueta solida).
 */
object PanalinkIcons {
    val Chats: ImageVector = Icons.Rounded.Forum
    val Momentos: ImageVector = Icons.Rounded.Star
    val Clips: ImageVector = Icons.Rounded.SmartDisplay
    val Llamadas: ImageVector = Icons.Rounded.Phone
    val Gente: ImageVector = Icons.Rounded.Contacts
}

/** Estrella normalizada (0..1) del fondo de constelacion. */
private data class StarPoint(val x: Float, val y: Float, val radius: Float)

/**
 * Construye la constelacion: unos pocos nucleos con racimos alrededor (como el
 * mockup, que no reparte los puntos de forma uniforme) mas algunas estrellas
 * sueltas. Coordenadas normalizadas para que no dependa del tamano.
 */
private fun buildConstellation(seed: Int, count: Int): List<StarPoint> {
    val rnd = Random(seed)
    val clusters = 10
    val cores = List(clusters) {
        Triple(
            0.08f + rnd.nextFloat() * 0.84f,
            0.08f + rnd.nextFloat() * 0.84f,
            0.24f + rnd.nextFloat() * 0.18f
        )
    }

    val points = ArrayList<StarPoint>(count)
    var i = 0
    while (points.size < count) {
        // El factor de radio se precalcula aqui (no en el Canvas): evita un pow
        // por punto en cada frame.
        val f = 0.55f + rnd.nextFloat().pow(1.7f) * (3.8f - 0.55f)
        if (i % 2 == 0) {
            points += StarPoint(rnd.nextFloat(), rnd.nextFloat(), f)
        } else {
            val c = cores[i % clusters]
            val angle = rnd.nextFloat() * 6.2831853f
            val dist = rnd.nextFloat().pow(0.7f) * c.third
            points += StarPoint(
                x = (c.first + dist * kotlin.math.cos(angle)).coerceIn(0.02f, 0.98f),
                y = (c.second + dist * kotlin.math.sin(angle)).coerceIn(0.02f, 0.98f),
                radius = f
            )
        }
        i++
    }
    return points
}

/**
 * Fondo de marca: navy con constelacion. La densidad, el tamano de los puntos y
 * la opacidad de las lineas estan calibrados contra la foto del mockup midiendo
 * luminancia y area de los puntos sobre la captura (puntos planos, linea fina,
 * luminancia ~1.5x el fondo).
 */
@Composable
fun ConstellationBackground(
    modifier: Modifier = Modifier,
    seed: Int = 20260920,
    pointCount: Int = 95
) {
    val stars = remember(seed, pointCount) { buildConstellation(seed, pointCount) }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(listOf(PanalinkSkin.NavyBase, PanalinkSkin.NavyDeep))
        )

        // Lavados de color del mockup: verde-teal abajo-izquierda, indigo arriba-derecha.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x1200C896), Color.Transparent),
                center = Offset(size.width * 0.12f, size.height * 0.88f),
                radius = size.maxDimension * 0.60f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x125A48D8), Color.Transparent),
                center = Offset(size.width * 0.92f, size.height * 0.72f),
                radius = size.maxDimension * 0.55f
            )
        )

        val pts = stars.map { Offset(it.x * size.width, it.y * size.height) }
        val linkDistance = size.minDimension * 0.24f
        val baseRadius = size.minDimension * 0.0042f

        for (a in pts.indices) {
            for (b in a + 1 until pts.size) {
                val dx = pts[a].x - pts[b].x
                val dy = pts[a].y - pts[b].y
                if (hypot(dx, dy) <= linkDistance) {
                    drawLine(
                        color = Color(0x1294A3B8),
                        start = pts[a],
                        end = pts[b],
                        strokeWidth = size.minDimension * 0.0019f
                    )
                }
            }
        }

        pts.forEachIndexed { index, p ->
            val r = baseRadius * stars[index].radius
            // Halo muy tenue: en la foto los puntos son planos, no "dianas".
            drawCircle(color = Color(0x088FA0BF), radius = r * 1.7f, center = p)
            drawCircle(color = Color(0x527E8A99), radius = r, center = p)
        }
    }
}

/**
 * Fondo del tema claro: limpio, estilo Instagram/Facebook, con un degradado
 * superior muy suave que da profundidad sin ensuciar.
 */
@Composable
fun LightBrandBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = Color(0xFFFFFFFF))
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFF1F3F7), Color(0x00FFFFFF)),
                startY = 0f,
                endY = size.height * 0.42f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x0F8A6F3E), Color.Transparent),
                center = Offset(size.width * 0.85f, size.height * 0.06f),
                radius = size.maxDimension * 0.45f
            )
        )
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
