package com.example.ui.settings.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.feature.settings.model.ChatsSettingsAction
import com.example.ui.settings.ios.IosBottomSpacer
import com.example.ui.settings.ios.IosFont
import com.example.ui.settings.ios.IosGroup
import com.example.ui.settings.ios.IosListPadding
import com.example.ui.settings.ios.IosSectionFooter
import com.example.ui.settings.ios.IosSectionHeader
import com.example.ui.settings.ios.IosSettingsColors
import com.example.ui.settings.ios.IosSettingsScaffold
import com.example.ui.settings.ios.IosToggleRow
import com.example.ui.settings.viewmodel.ChatsSettingsViewModel

@Composable
fun ChatsCenterScreen(
    onBack: () -> Unit,
    viewModel: ChatsSettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    IosSettingsScaffold(title = "Chats y apariencia", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = IosListPadding
        ) {
            item { IosSectionHeader("Tamaño del texto") }
            item {
                IosGroup {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Tamaño del texto", color = IosSettingsColors.label, fontFamily = IosFont, fontSize = 16.sp)
                            Text(
                                text = "${uiState.textSize.toInt()} sp",
                                color = IosSettingsColors.secondaryLabel,
                                fontFamily = IosFont,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Slider(
                            value = uiState.textSize,
                            onValueChange = { viewModel.dispatch(ChatsSettingsAction.UpdateTextSize(it)) },
                            valueRange = 12f..24f,
                            steps = 5,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = IosSettingsColors.green,
                                inactiveTrackColor = IosSettingsColors.cellElevated
                            )
                        )
                        Spacer(Modifier.height(12.dp))
                        ChatPreview(uiState.wallpaper, uiState.textSize)
                    }
                }
            }

            item { IosSectionHeader("Fondo de pantalla") }
            item {
                IosGroup {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(
                            "dark_slate" to "Gris oscuro",
                            "classic_teal" to "Azul verdoso",
                            "midnight_blue" to "Azul medianoche"
                        ).forEach { (wpKey, wpLabel) ->
                            val isSelected = uiState.wallpaper == wpKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = when (wpKey) {
                                            "classic_teal" -> IosSettingsColors.groupBackground
                                            "midnight_blue" -> Color(0xFF0A0E17)
                                            else -> IosSettingsColors.groupBackground
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) IosSettingsColors.green else IosSettingsColors.separator,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { viewModel.dispatch(ChatsSettingsAction.SetWallpaper(wpKey)) }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = wpLabel,
                                    color = if (isSelected) IosSettingsColors.green else IosSettingsColors.label,
                                    fontFamily = IosFont,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            item { IosSectionHeader("Comportamiento") }
            item {
                IosGroup {
                    IosToggleRow(
                        title = "Enter para enviar",
                        subtitle = "La tecla Enter envía el mensaje directamente.",
                        checked = uiState.enterSends,
                        onCheckedChange = { viewModel.dispatch(ChatsSettingsAction.SetEnterSends(it)) }
                    )
                }
            }

            item { IosBottomSpacer() }
        }
    }
}

/** Vista previa real de burbujas con el tamaño de letra y fondo elegidos. */
@Composable
private fun ChatPreview(wallpaper: String, textSize: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = when (wallpaper) {
                    "classic_teal" -> IosSettingsColors.groupBackground
                    "midnight_blue" -> Color(0xFF0A0E17)
                    else -> IosSettingsColors.groupBackground
                },
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "VISTA PREVIA",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = IosFont,
                color = IosSettingsColors.secondaryLabel,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Box(
                modifier = Modifier
                    .background(IosSettingsColors.cellElevated, RoundedCornerShape(12.dp, 12.dp, 12.dp, 0.dp))
                    .padding(10.dp)
                    .align(Alignment.Start)
                    .widthIn(max = 220.dp)
            ) {
                Text(
                    text = "¿Qué pasó chamo? ¿Cómo vas?",
                    color = IosSettingsColors.label,
                    fontFamily = IosFont,
                    fontSize = textSize.sp,
                    lineHeight = (textSize + 5).sp
                )
            }
            Box(
                modifier = Modifier
                    .background(IosSettingsColors.green.copy(alpha = 0.35f), RoundedCornerShape(12.dp, 12.dp, 0.dp, 12.dp))
                    .padding(10.dp)
                    .align(Alignment.End)
                    .widthIn(max = 220.dp)
            ) {
                Text(
                    text = "¡Todo fino de pana! Mira el tamaño de letra.",
                    color = IosSettingsColors.label,
                    fontFamily = IosFont,
                    fontSize = textSize.sp,
                    lineHeight = (textSize + 5).sp
                )
            }
        }
    }
}
