package com.example.ui.settings.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pentagon
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.feature.settings.model.CustomizationAction
import com.example.ui.settings.viewmodel.CustomizationViewModel
import com.example.ui.theme.ThemeManager
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizationCenterScreen(
    onBack: () -> Unit,
    viewModel: CustomizationViewModel = viewModel()
) {
    val haptic = LocalHapticFeedback.current
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pal = rememberPalette()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Personalización", color = pal.on, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = pal.on)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(pal.bg, pal.bg)))
                .padding(padding)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ─── HERO: modo de apariencia (toma el control real de la app)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionHeader(Icons.Filled.Palette, "Modo de Apariencia", "Se aplica al instante en toda la app", pal)
                    ModeSelector(uiState.themeMode, pal) { viewModel.dispatch(CustomizationAction.SetThemeMode(it)) }
                }
            }

            // ─── Identidad visual
            item{
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionHeader(Icons.Filled.AutoAwesome, "Identidad Visual", "La personalidad cromática de PanaLink", pal)
                    ThemeIdentityGrid(uiState.profileThemeChoice, pal) { viewModel.dispatch(CustomizationAction.SetProfileTheme(it)) }
                }
            }

            // ─── Barra de navegación (preview en vivo)
            item{
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionHeader(Icons.Filled.Waves, "Barra de Navegación", "Preview real de colores y geometría", pal)
                    val colorPresets = listOf(
                        "tropical" to "Menta",
                        "neon_cyber" to "Neón",
                        "monochrome" to "Mono",
                        "sunset" to "Sunset",
                        "aurora" to "Aurora"
                    )
                    BottomBarLivePreview(uiState.bottomBarColorChoice, uiState.bottomBarShapeChoice, pal)

                    val shapePresets = listOf(
                        "pill" to "Píldora",
                        "rounded_rect" to "Suave",
                        "cut_corners" to "Futurista",
                        "wave" to "Onda",
                        "sharp" to "Recto"
                    )
                    val shapeIcons = listOf(
                        Icons.Filled.Circle,
                        Icons.Filled.Hexagon,
                        Icons.Filled.Pentagon,
                        Icons.Filled.Waves,
                        Icons.Filled.CropSquare
                    )

                    Text("Paleta", color = pal.onSub, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {

                        colorPresets.forEach { (key, label) ->
                            val selected = uiState.bottomBarColorChoice == key
ScaleChip(
    modifier = Modifier.weight(1f).height(66.dp),
    shape = RoundedCornerShape(18.dp),
    selected = selected,
    container = if (selected) pal.accent.copy(alpha =0.13f) else pal.inputBg,
    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.dispatch(CustomizationAction.SetBottomBarColor(key)) }
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(if (selected) pal.accent.copy(alpha =0.14f) else pal.inputBg).border(1.dp, pal.border, CircleShape))
        Text(label, color = if (selected) pal.accent else pal.on, fontSize =10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}
                    }
                }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Geometría", color = pal.onSub, fontSize =11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start =4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()){

                        shapePresets.forEach { (key, label) ->
                            val selected = uiState.bottomBarShapeChoice == key
ScaleChip(
    modifier = Modifier.weight(1f).height(66.dp),
    shape = RoundedCornerShape(18.dp),
    selected = selected,
    container = if (selected) pal.accent.copy(alpha =0.13f) else pal.inputBg,
    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.dispatch(CustomizationAction.SetBottomBarShape(key)) }
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(shapeIcons.getOrElse(shapePresets.indexOfFirst { it.first == key }) { Icons.Filled.Circle }, contentDescription = null, tint = if (selected) pal.accent else pal.onSub, modifier = Modifier.size(18.dp))
        Text(label, color = if (selected) pal.accent else pal.on, fontSize =10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
                }
                }
            }

            // ─── Modo esencial (minimalist bar)
            item{
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (pal.isDark) Color(0xFF0C131F) else Color(0xFFF5F9F7))
                        .border(1.dp, pal.border, RoundedCornerShape(22.dp))
                        .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.dispatch(CustomizationAction.SetMinimalistMode(!uiState.isMinimalistMode)) }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (uiState.isMinimalistMode) pal.accent.copy(alpha =0.2f) else pal.inputBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Contrast, contentDescription = null, tint = if (uiState.isMinimalistMode) pal.accent else pal.onSub)
                        }
                        Column {
                            Text("Modo Esencial", color = pal.on, fontWeight = FontWeight.Bold, fontSize =14.sp)
                            Text("Solo íconos en la barra inferior", color = pal.onSub, fontSize =11.sp)
                        }
                        Switch(
                            checked = uiState.isMinimalistMode,
                            onCheckedChange = { viewModel.dispatch(CustomizationAction.SetMinimalistMode(it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = pal.accent,
                                uncheckedThumbColor = pal.onSub,
                                uncheckedTrackColor = pal.border
                            )
                        )
                    }
            }
            }

            // ─── Studio de Color (solo con identidad custom)
            item {
                if (uiState.profileThemeChoice == "custom") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(Icons.Filled.Palette, "Studio de Color", "Mezcla primario secundario y acento", pal)
                        CustomColorSlider("Primario · Rojo", uiState.customR, Color(0xFFFF5252)) { viewModel.dispatch(CustomizationAction.UpdateCustomPrimary(it, uiState.customG, uiState.customB)) }
                        CustomColorSlider("Primario · Verde", uiState.customG, Color(0xFF69F0AE)) { viewModel.dispatch(CustomizationAction.UpdateCustomPrimary(uiState.customR, it, uiState.customB)) }
                        CustomColorSlider("Primario · Azul", uiState.customB, Color(0xFF4FC3F7)) { viewModel.dispatch(CustomizationAction.UpdateCustomPrimary(uiState.customR, uiState.customG, it)) }
                        Spacer(modifier = Modifier.height(2.dp))
                        CustomColorSlider("Secundario · Rojo", uiState.customSecR, Color(0xFFFF5252)) { viewModel.dispatch(CustomizationAction.UpdateCustomSecondary(it, uiState.customSecG, uiState.customSecB)) }
                        CustomColorSlider("Secundario · Verde", uiState.customSecG, Color(0xFF69F0AE)) { viewModel.dispatch(CustomizationAction.UpdateCustomSecondary(uiState.customSecR, it, uiState.customSecB)) }
                        CustomColorSlider("Secundario · Azul", uiState.customSecB, Color(0xFF4FC3F7)) { viewModel.dispatch(CustomizationAction.UpdateCustomSecondary(uiState.customSecR, uiState.customSecG, it)) }
                    }
                }
            }
        }
    }
}

private class PaletteState(
    val isDark: Boolean,
    val bg: Color,
    val card: Color,
    val border: Color,
    val on: Color,
    val onSub: Color,
    val inputBg: Color,
    val accent: Color = Color(0xFF5CF8B0)
)

@Composable
private fun rememberPalette(): PaletteState {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    return remember(isDark) { PaletteState(isDark, scheme.background, scheme.surface, scheme.outlineVariant.copy(alpha =0.6f), scheme.onBackground, scheme.surfaceContainerHighest, scheme.surfaceContainerHighest) }
}

private fun Color.luminance(): Float =
    0.299f * this.red + 0.587f * this.green + 0.114f * this.blue

@Composable
private fun ScaleChip(
    modifier: Modifier = Modifier,
    shape: Shape,
    selected: Boolean ,
    container: Color ,
    onClick: () -> Unit ,
    content: @Composable BoxScope.() -> Unit ,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = tween(120),
        label = "chipScale"
    )
    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(container)
            .shadow(
                elevation = if (selected) 10.dp else 2.dp,
                shape = shape,
                ambientColor = if (selected) Color(0xFF5CF8B0).copy(alpha =0.45f) else Color(0xFF000000).copy(alpha =0.20f),
                spotColor = if (selected) Color(0xFF5CF8B0).copy(alpha =0.30f) else Color(0xFF000000).copy(alpha =0.15f)
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) Color(0xFF5CF8B0).copy(alpha =0.9f) else Color(0xFFFFFFFF).copy(alpha =0.14f),
                shape = shape

            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    pal: PaletteState,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal =4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(pal.accent.copy(alpha =0.22f), pal.accent.copy(alpha =0.06f))))
                .border(1.dp, pal.accent.copy(alpha =0.25f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = pal.accent, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = pal.on, fontSize =16.sp, fontWeight = FontWeight.Bold, letterSpacing =0.2.sp)
            Text(subtitle, color = pal.onSub, fontSize =11.sp)
        }
    }
}

@Composable
private fun ModeSelector(
    currentRow: String,
    pal: PaletteState,
    onSelect: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val modes = listOf(
        Triple("claro", "Claro", Icons.Filled.LightMode),
        Triple("oscuro", "Oscuro", Icons.Filled.DarkMode),
        Triple("system", "Sistema", Icons.Filled.SettingsBrightness)
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {



        modes.forEach { (mode, label, icon) ->
            val selected = currentRow == mode
            ScaleChip(
                modifier = Modifier.weight(1f)
                    .height(74.dp),
                shape = RoundedCornerShape(20.dp),

                selected = selected,
                container = if (selected) pal.accent.copy(alpha =0.14f) else pal.inputBg,

                onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onSelect(mode) }
            ) {

                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {


                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (selected) pal.accent.copy(alpha =0.22f) else pal.border.copy(alpha =0.5f)),
                        contentAlignment = Alignment.Center
                    ) {



                        Icon(icon, contentDescription = null, tint = if (selected) pal.accent else pal.onSub, modifier = Modifier.size(18.dp))
                    }
                    Text(label, color = if (selected) pal.accent else pal.on, fontSize =11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun ThemeIdentityGrid(
    current: String,
    pal: PaletteState,
    onPick: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val themes = listOf(
        listOf(
            Triple("halo_dark", "Aurora", Icons.Filled.AutoAwesome),
            Triple("royal_purple", "Royal", Icons.Filled.Bolt),
            Triple("nordic_ice", "Glaciar", Icons.Filled.WaterDrop),
            Triple("cyberpunk", "Cyber", Icons.Filled.Hexagon),
            Triple("neon", "Neón", Icons.Filled.Contrast)
        ),
        listOf(
            Triple("minimal_white", "Pure", Icons.Filled.LightMode),
            Triple("elegant_grey", "Grafito", Icons.Filled.Circle),
            Triple("classic_dark", "Clásico", Icons.Filled.CropSquare),
            Triple("whatsapp_dark", "Verde", Icons.Filled.CheckCircle),
            Triple("custom", "Studio", Icons.Filled.Palette)
        )
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {

        themes.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {


                row.forEach { (key, label, icon) ->
                    val selected = current == key

                    val gradient = when (key) {
                        "halo_dark" -> listOf(Color(0xFF00E5FF), Color(0xFF7C3AED))
                        "royal_purple" -> listOf(Color(0xFFBB86FC), Color(0xFF4A148C))
                        "nordic_ice" -> listOf(Color(0xFF4FC3F7), Color(0xFF81D4FA))
                        "cyberpunk" -> listOf(Color(0xFFFF00FF), Color(0xFF00FFFF))
                        "neon" -> listOf(Color(0xFF39FF14), Color(0xFF00F0FF))
                        "minimal_white" -> listOf(Color(0xFFFFFFFF), Color(0xFFE0E7E3))
                        "elegant_grey" -> listOf(Color(0xFF9E9E9E), Color(0xFF616161))
                        "classic_dark" -> listOf(Color(0xFF00A884), Color(0xFF1F2C34))
                        "whatsapp_dark" -> listOf(Color(0xFF00A884), Color(0xFF0B141A))
                        else -> listOf(pal.accent, pal.accent.copy(alpha =0.5f))
                    }
                    ScaleChip(
                        modifier = Modifier.weight(1f).height(88.dp),
                        shape = RoundedCornerShape(22.dp),

                        selected = selected,
                        container = Color.Transparent,

                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onPick(key) }
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(gradient))
                                    .border(1.dp, Color.White.copy(alpha =0.35f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                            }
                            Text(label, color = if (selected) pal.accent else pal.on, fontSize =10.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines =1)
                            if (selected) {
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                    }
                }
            }

    }
}
}

@Composable
private fun BottomBarLivePreview(
    colorPreset: String,
    shapePreset: String,
    pal: PaletteState,
) {
    val colors = ThemeManager.getBottomBarColors(colorPreset)
    val shape = ThemeManager.getBottomBarShape(shapePreset)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (pal.isDark) Color(0xFF0C131F) else Color(0xFFF5F9F7))
            .border(1.dp, pal.border, RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal =24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val items = listOf(
                Icons.Filled.Circle,
                Icons.Filled.Bolt,
                Icons.Filled.Palette,
                Icons.Filled.Hexagon,
                Icons.Filled.WaterDrop
            )
            items.forEachIndexed { index, icon ->
                val selected = index == 2
                Box(
                    modifier = Modifier
                        .size(if (selected) 52.dp else 40.dp)
                        .clip(shape)
                        .background(if (selected) colors[0] else pal.inputBg)
                        .border(1.dp, if (selected) colors[1].copy(alpha =0.7f) else pal.border, shape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = if (selected) Color.White else pal.onSub, modifier = Modifier.size(if (selected) 24.dp else 17.dp))
                }
            }
        }
    }
}

@Composable
private fun CustomColorSlider(
    label: String,
    value: Int,
    accent: Color,
    onChange:(Int) -> Unit,
) {
    val pal = rememberPalette()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {

        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {


            Text(label, color = pal.on, fontSize =12.sp, fontWeight = FontWeight.Medium)
            Text(value.toString(), color = accent.copy(alpha =0.9f), fontSize =12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange =0f..255f,
            colors = SliderDefaults.colors(
                activeTrackColor = accent,
                inactiveTrackColor = pal.border.copy(alpha =0.6f),
                thumbColor = accent,
                activeTickColor = pal.bg
            )
        )
    }
}
