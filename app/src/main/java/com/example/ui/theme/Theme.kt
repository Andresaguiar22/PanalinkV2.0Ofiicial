package com.example.ui.theme

import androidx.compose.runtime.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.animation.core.*
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

@Stable
class AppColors(
    primary: Color,
    secondary: Color,
    background: Color,
    surface: Color,
    bubbleMe: Color,
    bubbleOther: Color,
    topBar: Color,
    bottomBar: Color,
    accent: Color,
    isDark: Boolean,
    onPrimary: Color,
    onSecondary: Color,
    onBackground: Color,
    onSurface: Color
) {
    var primary by mutableStateOf(primary)
    var secondary by mutableStateOf(secondary)
    var background by mutableStateOf(background)
    var surface by mutableStateOf(surface)
    var bubbleMe by mutableStateOf(bubbleMe)
    var bubbleOther by mutableStateOf(bubbleOther)
    var topBar by mutableStateOf(topBar)
    var bottomBar by mutableStateOf(bottomBar)
    var accent by mutableStateOf(accent)
    var isDark by mutableStateOf(isDark)
    var onPrimary by mutableStateOf(onPrimary)
    var onSecondary by mutableStateOf(onSecondary)
    var onBackground by mutableStateOf(onBackground)
    var onSurface by mutableStateOf(onSurface)

    fun updateColorsFrom(other: AppColors) {
        primary = other.primary
        secondary = other.secondary
        background = other.background
        surface = other.surface
        bubbleMe = other.bubbleMe
        bubbleOther = other.bubbleOther
        topBar = other.topBar
        bottomBar = other.bottomBar
        accent = other.accent
        isDark = other.isDark
        onPrimary = other.onPrimary
        onSecondary = other.onSecondary
        onBackground = other.onBackground
        onSurface = other.onSurface
    }
}

val WhatsAppDarkColors = AppColors(
    primary = Color(0xFF00A884),      // WhatsApp Green
    secondary = Color(0xFF1F2C34),    // Dark Gray surface
    background = Color(0xFF0B141A),   // Dark background
    surface = Color(0xFF1F2C34),      // Dark card surface
    bubbleMe = Color(0xFF005C4B),     // Me bubble (dark green)
    bubbleOther = Color(0xFF202C33),  // Other bubble (dark gray)
    topBar = Color(0xFF202C33),
    bottomBar = Color(0xFF202C33),
    accent = Color(0xFF00A884),       // WhatsApp Green
    isDark = true,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFE9EDEF),
    onSurface = Color(0xFFE9EDEF)
)

val DarkTealColors = AppColors(
    primary = Color(0xFF00E5FF),      // Electric Cyan
    secondary = Color(0xFF161618),    // Dark Gray surface
    background = Color(0xFF000000),   // Deep Black
    surface = Color(0xFF121212),      // Dark Gray Surface
    bubbleMe = Color(0xFF1C1C1E),     // Graphite Gray Card
    bubbleOther = Color(0xFF262629),  // Light Graphite Gray Card
    topBar = Color(0xFF000000),
    bottomBar = Color(0xFF000000),
    accent = Color(0xFF8B5CF6),       // Electric Violet
    isDark = true,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

val RoyalPurpleColors = AppColors(
    primary = Color(0xFFBB86FC),
    secondary = Color(0xFF4A148C),
    background = Color(0xFF120024),
    surface = Color(0xFF2E1C4B),
    bubbleMe = Color(0xFF6200EE),
    bubbleOther = Color(0xFF3B2E5C),
    topBar = Color(0xFF4A148C),
    bottomBar = Color(0xFF4A148C),
    accent = Color(0xFFBB86FC),
    isDark = true,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

val WhatsAppLightColors = AppColors(
    primary = WhatsAppPrimary,
    secondary = WhatsAppSurface,
    background = WhatsAppBackground,
    surface = WhatsAppSurface,
    bubbleMe = WhatsAppPrimary.copy(alpha = 0.2f),
    bubbleOther = WhatsAppSurface,
    topBar = WhatsAppSurface,
    bottomBar = WhatsAppSurface,
    accent = WhatsAppPrimary,
    isDark = false,
    onPrimary = Color.White,
    onSecondary = WhatsAppTextDark,
    onBackground = WhatsAppTextDark,
    onSurface = WhatsAppTextDark
)

val ClassicDarkColors = AppColors(
    primary = Color(0xFFBB86FC),
    secondary = Color(0xFF1E1E1E),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    bubbleMe = Color(0xFFBB86FC),
    bubbleOther = Color(0xFF333333),
    topBar = Color(0xFF121212),
    bottomBar = Color(0xFF121212),
    accent = Color(0xFFBB86FC),
    isDark = true,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

val ElegantGreyColors = AppColors(
    primary = ElegantPrimary,
    secondary = ElegantSurface,
    background = ElegantBackground,
    surface = ElegantSurface,
    bubbleMe = ElegantPrimary.copy(alpha = 0.2f),
    bubbleOther = ElegantSurface,
    topBar = ElegantSurface,
    bottomBar = ElegantSurface,
    accent = ElegantPrimary,
    isDark = true,
    onPrimary = Color.Black,
    onSecondary = ElegantTextLight,
    onBackground = ElegantTextLight,
    onSurface = ElegantTextLight
)

val VividOceanColors = AppColors(
    primary = OceanPrimary,
    secondary = OceanSurface,
    background = OceanBackground,
    surface = OceanSurface,
    bubbleMe = OceanPrimary.copy(alpha = 0.2f),
    bubbleOther = OceanSurface,
    topBar = OceanSurface,
    bottomBar = OceanSurface,
    accent = OceanPrimary,
    isDark = true,
    onPrimary = Color.Black,
    onSecondary = OceanTextLight,
    onBackground = OceanTextLight,
    onSurface = OceanTextLight
)

val NordicIceColors = AppColors(
    primary = Color(0xFF80DEEA),
    secondary = Color(0xFF006064),
    background = Color(0xFF0E1A20),
    surface = Color(0xFF21323C),
    bubbleMe = Color(0xFF00838F),
    bubbleOther = Color(0xFF263C46),
    topBar = Color(0xFF006064),
    bottomBar = Color(0xFF006064),
    accent = Color(0xFF80DEEA),
    isDark = true,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

val CyberpunkColors = AppColors(
    primary = Color(0xFF00F0FF),
    secondary = Color(0xFFBC00DD),
    background = Color(0xFF05050A),
    surface = Color(0xFF10101F),
    bubbleMe = Color(0xFFBC00DD),
    bubbleOther = Color(0xFF151525),
    topBar = Color(0xFF10101F),
    bottomBar = Color(0xFF10101F),
    accent = Color(0xFFFFE600),
    isDark = true,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

val NeonVibeColors = AppColors(
    primary = Color(0xFF39FF14),
    secondary = Color(0xFF1F1F1F),
    background = Color(0xFF000000),
    surface = Color(0xFF0D0D0D),
    bubbleMe = Color(0xFF1F1F1F),
    bubbleOther = Color(0xFF121212),
    topBar = Color(0xFF000000),
    bottomBar = Color(0xFF000000),
    accent = Color(0xFFFF007F),
    isDark = true,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

val MinimalWhiteColors = AppColors(
    primary = Color(0xFF111111),
    secondary = Color(0xFFEEEEEE),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF7F7F7),
    bubbleMe = Color(0xFFE2E2E2),
    bubbleOther = Color(0xFFF0F0F0),
    topBar = Color(0xFFFFFFFF),
    bottomBar = Color(0xFFFFFFFF),
    accent = Color(0xFF666666),
    isDark = false,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black
)

val HaloLightColors = AppColors(
    // Tema claro estilo Instagram/Facebook: blanco limpio, letras casi negras.
    primary = Color(0xFF1DA060),      // verde de marca
    secondary = Color(0xFFF1F3F7),    // superficie secundaria / inputs
    background = Color(0xFFFFFFFF),   // blanco limpio (fondo global)
    surface = Color(0xFFFFFFFF),
    bubbleMe = Color(0xFF1DA060),     // burbuja propia verde con texto blanco
    bubbleOther = Color(0xFFF1F3F7),
    topBar = Color(0xFFFFFFFF),
    bottomBar = Color(0xFFFFFFFF),
    accent = Color(0xFF8A6F3E),       // dorado bronce (legible sobre blanco)
    isDark = false,
    onPrimary = Color.White,
    onSecondary = Color(0xFF0B0F14),
    onBackground = Color(0xFF0B0F14), // letras negras sobre blanco (pedido del mantenedor)
    onSurface = Color(0xFF0B0F14)
)

val CyberpunkGlobalColors = AppColors(
    primary = Color(0xFF18E7F5),
    secondary = Color(0xB8131A22),
    background = Color(0xFF0D0F12),
    surface = Color(0xB8131A22),
    bubbleMe = Color(0xFF7B3FF2),
    bubbleOther = Color(0xFF18202A),
    topBar = Color(0xB8131A22),
    bottomBar = Color(0xB8131A22),
    accent = Color(0xFFFF28C8),
    isDark = true,
    onPrimary = Color(0xFF071014),
    onSecondary = Color(0xFFF5E6C8),
    onBackground = Color(0xFFF5E6C8),
    onSurface = Color(0xFFF5E6C8)
)

val HaloDarkColors = AppColors(
    // Identidad de marca Panalink: mint del mockup + dorado, sobre navy.
    primary = Color(0xFF3FCF8E),
    secondary = Color(0xFF212936), // superficie de tarjetas/barras
    background = Color(0xFF171D29),
    surface = Color(0xFF212936),
    bubbleMe = Color(0xFF1D5C4E),  // burbuja propia: teal profundo (texto claro legible)
    bubbleOther = Color(0xFF212936),
    topBar = Color(0xFF171D29),
    bottomBar = Color(0xFF171D29),
    accent = Color(0xFFC9A96A),    // dorado de acentos
    isDark = true,
    onPrimary = Color(0xFF06231A),
    onSecondary = Color(0xFFF4F7FB),
    onBackground = Color(0xFFF4F7FB), // letras blancas sobre oscuro (pedido del mantenedor)
    onSurface = Color(0xFFF4F7FB)
)

object ThemeManager {
    val themeKey = kotlinx.coroutines.flow.MutableStateFlow("cyberpunk_global")

    // Global light/dark/system mode. Real: MainActivity resolves the effective
    // theme key from this every recomposition, so "Claro/Oscuro/Sistema"
    // instantly restyles the whole app (no longer just persisted smoke).
    val themeMode = kotlinx.coroutines.flow.MutableStateFlow("oscuro")

    val customPrimary = kotlinx.coroutines.flow.MutableStateFlow(Color(0xFF76CE9F))
    val customBackground = kotlinx.coroutines.flow.MutableStateFlow(Color(0xFF0F1412))
    val customAccent = kotlinx.coroutines.flow.MutableStateFlow(Color(0xFF2E483E))
    val customSurface = kotlinx.coroutines.flow.MutableStateFlow(Color(0xFF1B2420))
    val customSecondary = kotlinx.coroutines.flow.MutableStateFlow(Color(0xFF1B2420))

    val isMinimalistMode = kotlinx.coroutines.flow.MutableStateFlow(false)

    val bottomBarColorPreset = kotlinx.coroutines.flow.MutableStateFlow("tropical")
    val bottomBarShapePreset = kotlinx.coroutines.flow.MutableStateFlow("pill")

    fun getBottomBarColors(preset: String): List<Color> {
        return when (preset) {
            "neon_cyber" -> listOf(
                Color(0xFF00F0FF), // Cyan
                Color(0xFFBC00DD), // Magenta
                Color(0xFFFF007F), // Neon Pink
                Color(0xFF39FF14), // Lime Green
                Color(0xFF00F0FF)
            )
            "monochrome" -> listOf(
                Color(0xFFFFFFFF),
                Color(0xFFB0B0B0),
                Color(0xFF606060),
                Color(0xFF202020),
                Color(0xFFFFFFFF)
            )
            "sunset" -> listOf(
                Color(0xFFFF3D00), // Deep Orange
                Color(0xFFFF9100), // Orange
                Color(0xFFFFEA00), // Yellow
                Color(0xFFFF007F), // Pink
                Color(0xFFFF3D00)
            )
            "aurora" -> listOf(
                Color(0xFF00E5FF), // Cyan
                Color(0xFF1DE9B6), // Teal
                Color(0xFF00E676), // Lime Accent
                Color(0xFF651FFF), // Purple Accent
                Color(0xFF00E5FF)
            )
            else -> listOf( // "tropical" / default elegant mint
                Color(0xFF76CE9F), // Soft Mint Green
                Color(0xFF4FA37D), // Medium Mint Green
                Color(0xFF86D2B1), // Light Mint Green
                Color(0xFF59AC88), // Forest Mint
                Color(0xFF2E483E), // Deep Forest Mint
                Color(0xFF76CE9F)  // Soft Mint Green
            )
        }
    }

    fun getBottomBarShape(preset: String): Shape {
        return when (preset) {
            "rounded_rect" -> RoundedCornerShape(16.dp)
            "cut_corners" -> CutCornerShape(12.dp)
            "wave" -> RoundedCornerShape(
                topStart = 24.dp,
                topEnd = 6.dp,
                bottomStart = 6.dp,
                bottomEnd = 24.dp
            )
            "sharp" -> RectangleShape
            else -> CircleShape // "pill"
        }
    }
}

/** Effective theme key for "claro" mode: uses the light variant when one exists,
 *  otherwise falls back to the same (dark) palette. */
fun resolveLightTheme(themeKey: String): String = when (themeKey) {
    "halo_dark" -> "halo_light"
    "whatsapp_dark" -> "whatsapp_light"
    "minimal_white" -> "minimal_white" // already light
    else -> themeKey
}

/** Applies the global light/dark/system preference on top of the chosen identity. */
fun resolveThemeForMode(themeKey: String, mode: String, systemDark: Boolean): String = when (mode) {
    "claro" -> resolveLightTheme(themeKey)
    "oscuro" -> themeKey // dark identity stays identity (Claro identity has no dark twin)
    else -> if (systemDark) themeKey else resolveLightTheme(themeKey)
}

fun getColorsForTheme(themeKey: String?, customColors: AppColors? = null): AppColors {
    return when (themeKey) {
        "whatsapp_light" -> WhatsAppLightColors
        "classic_dark" -> ClassicDarkColors
        "elegant_grey" -> ElegantGreyColors
        "whatsapp_dark" -> WhatsAppDarkColors

        "vivid_ocean" -> VividOceanColors
        "halo_light" -> HaloLightColors
        "halo_dark" -> HaloDarkColors
        "cyberpunk_global" -> CyberpunkGlobalColors
        "royal_purple" -> RoyalPurpleColors
        "nordic_ice" -> NordicIceColors
        "cyberpunk" -> CyberpunkColors
        "neon" -> NeonVibeColors
        "minimal_white" -> MinimalWhiteColors
        "custom" -> customColors ?: HaloDarkColors
        else -> HaloDarkColors
    }
}

val LocalAppColors = staticCompositionLocalOf { HaloDarkColors }

@Composable
fun MyApplicationTheme(
    themeKey: String = "cyberpunk_global",
    customColors: AppColors? = null,
    content: @Composable () -> Unit,
) {
    // El tema activo ya viene resuelto por resolveThemeForMode() segun el modo
    // elegido (claro / oscuro / sistema). Aqui no se re-interpreta: si el
    // resultado es claro, la app se pinta clara con tinta oscura; si es oscuro,
    // usa la identidad de marca (navy + constelacion + letras blancas).
    val activeColors = remember(themeKey, customColors) {
        getColorsForTheme(themeKey, customColors)
    }

    val isDark = activeColors.isDark
    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = activeColors.primary,
            secondary = activeColors.secondary,
            background = activeColors.background,
            surface = activeColors.surface,
            onPrimary = activeColors.onPrimary,
            onSecondary = activeColors.onSecondary,
            onBackground = activeColors.onBackground,
            onSurface = activeColors.onSurface
        )
    } else {
        lightColorScheme(
            primary = activeColors.primary,
            secondary = activeColors.secondary,
            background = activeColors.background,
            surface = activeColors.surface,
            onPrimary = activeColors.onPrimary,
            onSecondary = activeColors.onSecondary,
            onBackground = activeColors.onBackground,
            onSurface = activeColors.onSurface
        )
    }

    // Publica el modo a la paleta viva: asi el codigo no-composable (constantes,
    // valores por defecto) tambien lee el color correcto del tema vigente.
    PanalinkPalette.isDark = isDark

    CompositionLocalProvider(
        LocalAppColors provides activeColors,
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = FontFamily.Serif),
        LocalContentColor provides if (isDark) TitleColorDark else TitleColorLight
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
        ) {
            // Fondo de marca global: la constelacion (oscuro) o el blanco limpio
            // (claro) viven detras de TODO el contenido. Cualquier pantalla que no
            // pinte su propio fondo opaco lo muestra.
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxSize()
                    .background(PanalinkPalette.background)
            ) {
                if (themeKey == "cyberpunk_global" && isDark) CyberpunkGlobalBackground() else if (isDark) ConstellationBackground() else LightBrandBackground()
                content()
            }
        }
    }
}

@Composable
private fun CyberpunkGlobalBackground() {
    val cyan = Color(0xFF18E7F5)
    val magenta = Color(0xFFFF28C8)
    val purple = Color(0xFF9B5CFF)
    androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier.fillMaxSize().background(Color(0xFF0D0F12))
        )
        androidx.compose.foundation.Canvas(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
            val step = 22.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(cyan.copy(alpha = 0.035f), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height))
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(cyan.copy(alpha = 0.025f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y))
                y += step
            }
            val wide = 55.dp.toPx()
            var mx = 0f
            while (mx < size.width) {
                drawLine(magenta.copy(alpha = 0.028f), androidx.compose.ui.geometry.Offset(mx, 0f), androidx.compose.ui.geometry.Offset(mx, size.height))
                mx += wide
            }
        }
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier.size(260.dp).offset(x = (-70).dp, y = (-45).dp)
                .blur(65.dp)
                .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(magenta.copy(alpha = 0.16f), Color.Transparent)))
        )
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier.size(300.dp).align(androidx.compose.ui.Alignment.CenterEnd)
                .offset(x = 80.dp, y = (-20).dp).blur(70.dp)
                .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(purple.copy(alpha = 0.13f), Color.Transparent)))
        )
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier.size(260.dp).align(androidx.compose.ui.Alignment.BottomCenter)
                .offset(y = 100.dp).blur(75.dp)
                .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(cyan.copy(alpha = 0.10f), Color.Transparent)))
        )
    }
}

private val TitleColorDark = Color(0xFFF4F7FB)
private val TitleColorLight = Color(0xFF0B0F14)

// 🦴 SHIMMER SKELETON LOADERS
fun Modifier.shimmerEffect(): Modifier = composed {
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.toFloat() - 1f,
        targetValue = 2 * size.width.toFloat() + 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffsetX"
    )

    background(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF263238).copy(alpha = 0.7f),
                Color(0xFF37474F).copy(alpha = 0.4f),
                Color(0xFF263238).copy(alpha = 0.7f),
            ),
            start = androidx.compose.ui.geometry.Offset(startOffsetX, 0f),
            end = androidx.compose.ui.geometry.Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())
        )
    ).onGloballyPositioned {
        size = it.size
    }
}

// 🎨 AVATARES CON GRADIENTE DINÁMICO (AvatarRing)
fun getAvatarGradient(name: String): Brush {
    val gradients = listOf(
        listOf(Color(0xFF3F51B5), Color(0xFF9C27B0)),
        listOf(Color(0xFF009688), Color(0xFF4CAF50)),
        listOf(Color(0xFFFF5722), Color(0xFFE91E63)),
        listOf(Color(0xFF2196F3), Color(0xFF00BCD4)),
        listOf(Color(0xFFFFC107), Color(0xFFFF5722)),
        listOf(Color(0xFFE91E63), Color(0xFF673AB7)),
        listOf(Color(0xFFCDDC39), Color(0xFF4CAF50)),
        listOf(Color(0xFF673AB7), Color(0xFFE040FB))
    )
    val charCode = if (name.isNotEmpty()) name[0].code else 0
    val index = Math.abs(charCode) % gradients.size
    val colors = gradients[index]
    return Brush.linearGradient(
        colors = colors,
        start = androidx.compose.ui.geometry.Offset.Zero,
        end = androidx.compose.ui.geometry.Offset.Infinite
    )
}

// 🔄 ANIMACIONES EN CADA ACCIÓN (Bounce scale)
fun Modifier.bounceClick(onClick: () -> Unit = {}): Modifier = composed {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "bounceScale"
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(
        interactionSource = interactionSource,
        indication = androidx.compose.foundation.LocalIndication.current,
        onClick = onClick
    )
}

// 🔮 PREMIUM GRADIENT (Electric Blue, Violet, Cyan)
fun getPremiumGradient(): Brush {
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFF0052FF), // Electric Blue
            Color(0xFF7C3AED), // Violet
            Color(0xFF00E5FF)  // Cyan
        ),
        start = androidx.compose.ui.geometry.Offset.Zero,
        end = androidx.compose.ui.geometry.Offset.Infinite
    )
}

fun getPremiumActiveIconGradient(): Brush {
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFF00E5FF), // Cyan
            Color(0xFF8B5CF6)  // Violet
        )
    )
}

