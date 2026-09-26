package com.example.ui.settings.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.feature.settings.model.NotificationAction
import com.example.ui.settings.ios.IosActionRow
import com.example.ui.settings.ios.IosBottomSpacer
import com.example.ui.settings.ios.IosDivider
import com.example.ui.settings.ios.IosFont
import com.example.ui.settings.ios.IosGroup
import com.example.ui.settings.ios.IosListPadding
import com.example.ui.settings.ios.IosRadioRow
import com.example.ui.settings.ios.IosSectionFooter
import com.example.ui.settings.ios.IosSectionHeader
import com.example.ui.settings.ios.IosSettingsColors
import com.example.ui.settings.ios.IosSettingsScaffold
import com.example.ui.settings.ios.IosToggleRow
import com.example.ui.settings.viewmodel.NotificationSettingsViewModel

@Composable
fun NotificationCenterScreen(
    onBack: () -> Unit,
    viewModel: NotificationSettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val postNotificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Permiso de notificaciones concedido", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "El permiso es necesario para recibir alertas de nuevos mensajes.", Toast.LENGTH_LONG).show()
        }
    }

    val hasPostNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotificationPermission

    IosSettingsScaffold(title = "Notificaciones", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = IosListPadding
        ) {
            if (needsPermission) {
                item {
                    IosGroup {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = IosSettingsColors.orange,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Notificaciones desactivadas",
                                    color = IosSettingsColors.label,
                                    fontFamily = IosFont,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Android necesita tu permiso para mostrarte alertas de nuevos mensajes.",
                                    color = IosSettingsColors.secondaryLabel,
                                    fontFamily = IosFont,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        IosDivider(startIndent = 16.dp)
                        IosActionRow(
                            title = "Activar notificaciones",
                            onClick = { postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            color = IosSettingsColors.blue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            item {
                IosGroup {
                    IosToggleRow(
                        title = "Notificaciones generales",
                        subtitle = "Activa o desactiva todas las alertas de la app.",
                        checked = uiState.globalEnabled,
                        onCheckedChange = { viewModel.dispatch(NotificationAction.SetGlobalEnabled(it)) },
                        icon = Icons.Rounded.Notifications,
                        iconTint = IosSettingsColors.red
                    )
                }
            }

            if (uiState.globalEnabled) {
                item { IosSectionHeader("Alertas") }
                item {
                    IosGroup {
                        IosToggleRow(
                            title = "Sonido",
                            subtitle = "Reproducir alertas sonoras al recibir mensajes.",
                            checked = uiState.soundEnabled,
                            onCheckedChange = { viewModel.dispatch(NotificationAction.SetSoundEnabled(it)) },
                            icon = Icons.Rounded.MusicNote,
                            iconTint = IosSettingsColors.pink
                        )
                        IosDivider()
                        IosToggleRow(
                            title = "Vibración",
                            subtitle = "Hacer vibrar el dispositivo al recibir mensajes.",
                            checked = uiState.vibrationEnabled,
                            onCheckedChange = { viewModel.dispatch(NotificationAction.SetVibrationEnabled(it)) },
                            icon = Icons.Rounded.Vibration,
                            iconTint = IosSettingsColors.purple
                        )
                    }
                }

                if (uiState.soundEnabled) {
                    item { IosSectionHeader("Tono de alerta") }
                    item {
                        IosGroup {
                            listOf(
                                "default" to "Tono del sistema",
                                "pana_beep" to "Pana bip",
                                "pana_double" to "Pana doble bip",
                                "pana_pip" to "Pana pip rápido",
                                "pana_high" to "Pana alerta aguda",
                                "silent" to "Silencio"
                            ).forEachIndexed { index, (toneKey, toneLabel) ->
                                if (index > 0) IosDivider(startIndent = 16.dp)
                                IosRadioRow(
                                    title = toneLabel,
                                    selected = uiState.soundTone == toneKey,
                                    onClick = {
                                        viewModel.dispatch(NotificationAction.SetSoundTone(toneKey))
                                        com.example.service.NotificationHelper.playNotificationSound(context)
                                    }
                                )
                            }
                        }
                        IosSectionFooter("Toca un tono para escucharlo.")
                    }
                }

                if (uiState.vibrationEnabled) {
                    item { IosSectionHeader("Patrón de vibración") }
                    item {
                        IosGroup {
                            listOf(
                                "default" to "Predeterminado",
                                "short" to "Corto",
                                "long" to "Largo",
                                "double" to "Doble",
                                "triple" to "Triple"
                            ).forEachIndexed { index, (patternKey, patternLabel) ->
                                if (index > 0) IosDivider(startIndent = 16.dp)
                                IosRadioRow(
                                    title = patternLabel,
                                    selected = uiState.vibrationPattern == patternKey,
                                    onClick = {
                                        viewModel.dispatch(NotificationAction.SetVibrationPattern(patternKey))
                                        com.example.service.NotificationHelper.triggerVibration(context)
                                    }
                                )
                            }
                        }
                    }
                }

                item { IosSectionHeader("Dentro del chat") }
                item {
                    IosGroup {
                        IosToggleRow(
                            title = "Sonidos en el chat",
                            subtitle = "Sonidos suaves tipo gota para mensajes nuevos mientras estás en un chat.",
                            checked = uiState.chatSoundEnabled,
                            onCheckedChange = { viewModel.dispatch(NotificationAction.SetChatSoundEnabled(it)) },
                            icon = Icons.AutoMirrored.Filled.Chat,
                            iconTint = IosSettingsColors.green
                        )
                        IosDivider()
                        IosToggleRow(
                            title = "Sonido de envío",
                            subtitle = "Un 'swoosh' suave al enviar tus propios mensajes.",
                            checked = uiState.outgoingSoundEnabled,
                            onCheckedChange = {
                                viewModel.dispatch(NotificationAction.SetOutgoingSoundEnabled(it))
                                if (it) com.example.service.NotificationHelper.playOutgoingSound(context)
                            },
                            icon = Icons.AutoMirrored.Filled.Send,
                            iconTint = IosSettingsColors.teal
                        )
                    }
                }

                if (uiState.chatSoundEnabled) {
                    item { IosSectionHeader("Tono de chat activo") }
                    item {
                        IosGroup {
                            listOf(
                                "water_drop" to "Gota de agua",
                                "soft_pop" to "Pop suave"
                            ).forEachIndexed { index, (toneKey, toneLabel) ->
                                if (index > 0) IosDivider(startIndent = 16.dp)
                                IosRadioRow(
                                    title = toneLabel,
                                    selected = uiState.chatSoundTone == toneKey,
                                    onClick = {
                                        viewModel.dispatch(NotificationAction.SetChatSoundTone(toneKey))
                                        com.example.service.NotificationHelper.playActiveChatSound(context)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item { IosBottomSpacer() }
        }
    }
}
