package com.example.ui.settings.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PanalinkPalette

/**
 * Kit visual "Ajustes de iOS" para toda la seccion de Centro de Control.
 *
 * Antes cada pantalla traia su propio fondo (#121B22, #161618, #1E2B33, #000000),
 * su acento (verde #25D366 vs #34C759, cian #00E5FF) y sus filas propias, asi que
 * el conjunto se veia como pantallas distintas pegadas. Este kit concentra los
 * tokens y los componentes en un solo sitio para que las 12 pantallas se vean
 * como una sola app.
 *
 * Es sensible al tema (oscuro/claro) leyendo [PanalinkPalette.isDark], asi que
 * funciona igual en modo claro sin tocar cada pantalla.
 */

/** iOS usa SF Pro; el serif de marca no encaja con este lenguaje. */
val IosFont: FontFamily = FontFamily.SansSerif

object IosSettingsColors {
    private val dark: Boolean get() = PanalinkPalette.isDark

    /** Fondo agrupado (detras de las tarjetas). */
    val groupBackground: Color get() = if (dark) Color(0xFF000000) else Color(0xFFF2F2F7)

    /** Fondo de celda / tarjeta. */
    val cell: Color get() = if (dark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)

    /** Celda elevada (controles agrupados dentro de una tarjeta). */
    val cellElevated: Color get() = if (dark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)

    val separator: Color get() = if (dark) Color(0xFF38383A) else Color(0xFFC6C6C8)

    val label: Color get() = if (dark) Color(0xFFFFFFFF) else Color(0xFF000000)
    val secondaryLabel: Color get() = if (dark) Color(0xFF8E8E93) else Color(0x993C3C43)
    val tertiaryLabel: Color get() = if (dark) Color(0xFF636366) else Color(0x4D3C3C43)
    val chevron: Color get() = if (dark) Color(0xFF5A5A5E) else Color(0xFFC4C4C6)

    val blue: Color get() = if (dark) Color(0xFF0A84FF) else Color(0xFF007AFF)
    val green: Color get() = if (dark) Color(0xFF30D158) else Color(0xFF34C759)
    val red: Color get() = if (dark) Color(0xFFFF453A) else Color(0xFFFF3B30)
    val orange: Color get() = if (dark) Color(0xFFFF9F0A) else Color(0xFFFF9500)
    val yellow: Color get() = if (dark) Color(0xFFFFD60A) else Color(0xFFFFCC00)
    val purple: Color get() = if (dark) Color(0xFFBF5AF2) else Color(0xFFAF52DE)
    val pink: Color get() = if (dark) Color(0xFFFF375F) else Color(0xFFFF2D55)
    val teal: Color get() = if (dark) Color(0xFF64D2FF) else Color(0xFF5AC8FA)
    val indigo: Color get() = if (dark) Color(0xFF5E5CE6) else Color(0xFF5856D6)
    val gray: Color get() = Color(0xFF8E8E93)
    val mint: Color get() = if (dark) Color(0xFF66D4CF) else Color(0xFF00C7BE)

    /** Texto/icono sobre un control relleno con color de acento (blue, green, red...). */
    val onAccent: Color get() = Color.White

    /** Velo sobre video/foto: se mantiene oscuro en ambos temas para no lavar la imagen. */
    val mediaScrim: Color get() = Color(0xE61C1C1E)
    val mediaScrimSoft: Color get() = Color(0x661C1C1E)
}

/** Medidas compartidas del kit. */
val IosIconBadgeSize: Dp = 29.dp
val IosGroupShape = RoundedCornerShape(12.dp)

/** Padding de contenido de las listas (deja aire a los lados y abajo). */
val IosListPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 36.dp)

/** Separacion vertical entre grupos. */
val IosSectionSpacing: Dp = 24.dp

/**
 * Scaffold de una pantalla de ajustes: fondo agrupado + barra superior con
 * titulo grande que se colapsa al hacer scroll (igual que Ajustes de iOS).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = IosSettingsColors.groupBackground,
        floatingActionButton = floatingActionButton,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = title,
                        color = IosSettingsColors.label,
                        fontFamily = IosFont,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar",
                            tint = IosSettingsColors.blue
                        )
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = IosSettingsColors.groupBackground,
                    scrolledContainerColor = IosSettingsColors.groupBackground,
                    titleContentColor = IosSettingsColors.label,
                    navigationIconContentColor = IosSettingsColors.blue,
                    actionIconContentColor = IosSettingsColors.blue
                ),
                scrollBehavior = scrollBehavior
            )
        },
        content = content
    )
}

/** Encabezado de seccion (mayusculas grises), como en Ajustes de iOS. */
@Composable
fun IosSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = IosSettingsColors.secondaryLabel,
        fontFamily = IosFont,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 7.dp)
    )
}

/** Pie de seccion: aclara el efecto de los ajustes de arriba. */
@Composable
fun IosSectionFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = IosSettingsColors.secondaryLabel,
        fontFamily = IosFont,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp)
    )
}

/** Tarjeta contenedora de filas (esquinas 12dp, como las celdas agrupadas). */
@Composable
fun IosGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = IosGroupShape,
        color = IosSettingsColors.cell
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/**
 * Separador de fila. Por defecto arranca alineado con el texto (despues del
 * icono), como el inset de iOS; las filas sin icono pasan [startIndent] = 16.dp.
 */
@Composable
fun IosDivider(startIndent: Dp = 57.dp) {
    HorizontalDivider(
        color = IosSettingsColors.separator,
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = startIndent)
    )
}

/** Icono de fila: cuadro redondeado de color solido con el glifo en blanco. */
@Composable
fun IosIconBadge(
    icon: ImageVector,
    tint: Color,
    size: Dp = IosIconBadgeSize
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(tint),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.58f)
        )
    }
}

/**
 * Fila navegable: icono + titulo (+ subtitulo) + valor opcional + chevron.
 * Es el bloque base de todas las listas de ajustes.
 */
@Composable
fun IosRow(
    title: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    iconTint: Color = IosSettingsColors.blue,
    subtitle: String? = null,
    trailingText: String? = null,
    showChevron: Boolean = true,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            IosIconBadge(icon, iconTint)
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = IosSettingsColors.label,
                fontFamily = IosFont,
                fontSize = 16.sp
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = IosSettingsColors.secondaryLabel,
                    fontFamily = IosFont,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!trailingText.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = trailingText,
                color = IosSettingsColors.secondaryLabel,
                fontFamily = IosFont,
                fontSize = 16.sp,
                maxLines = 1
            )
        }
        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = IosSettingsColors.chevron,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Fila con interruptor (Switch verde de iOS). */
@Composable
fun IosToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    iconTint: Color = IosSettingsColors.blue,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            IosIconBadge(icon, iconTint)
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = IosSettingsColors.label,
                fontFamily = IosFont,
                fontSize = 16.sp
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = IosSettingsColors.secondaryLabel,
                    fontFamily = IosFont,
                    fontSize = 13.sp
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = IosSettingsColors.green,
                checkedBorderColor = IosSettingsColors.green,
                uncheckedThumbColor = if (PanalinkPalette.isDark) Color(0xFF8E8E93) else Color.White,
                uncheckedTrackColor = if (PanalinkPalette.isDark) Color(0xFF39393D) else Color(0xFFE9E9EA),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

/** Fila de seleccion unica: el check va a la DERECHA (patron de iOS). */
@Composable
fun IosRadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = IosSettingsColors.label,
                fontFamily = IosFont,
                fontSize = 16.sp
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = IosSettingsColors.secondaryLabel,
                    fontFamily = IosFont,
                    fontSize = 13.sp
                )
            }
        }
        if (selected) {
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Seleccionado",
                tint = IosSettingsColors.blue,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Fila de accion centrada (ej. "Cerrar sesion"), en color. */
@Composable
fun IosActionRow(
    title: String,
    onClick: () -> Unit,
    color: Color = IosSettingsColors.blue,
    fontWeight: FontWeight = FontWeight.Normal
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = color,
            fontFamily = IosFont,
            fontSize = 16.sp,
            fontWeight = fontWeight
        )
    }
}

/** Fila informativa sin navegacion (icono + titulo + valor a la derecha). */
@Composable
fun IosValueRow(
    title: String,
    value: String,
    icon: ImageVector? = null,
    iconTint: Color = IosSettingsColors.blue,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            IosIconBadge(icon, iconTint)
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = IosSettingsColors.label,
                fontFamily = IosFont,
                fontSize = 16.sp
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = IosSettingsColors.secondaryLabel,
                    fontFamily = IosFont,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            color = IosSettingsColors.secondaryLabel,
            fontFamily = IosFont,
            fontSize = 16.sp,
            maxLines = 1
        )
    }
}

/** Pastilla de estado (badge) con el color del dominio. */
@Composable
fun IosBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = color.copy(alpha = 0.16f)
    ) {
        Text(
            text = text,
            color = color,
            fontFamily = IosFont,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** Espaciador vertical reutilizable para cerrar listas. */
@Composable
fun IosBottomSpacer() {
    Spacer(Modifier.height(IosSectionSpacing))
}

/** Estado vacio coherente con el kit. */
@Composable
fun IosEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = IosSettingsColors.gray
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IosIconBadge(icon, tint, size = 44.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                color = IosSettingsColors.label,
                fontFamily = IosFont,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = IosSettingsColors.secondaryLabel,
                    fontFamily = IosFont,
                    fontSize = 13.sp
                )
            }
        }
    }
}
