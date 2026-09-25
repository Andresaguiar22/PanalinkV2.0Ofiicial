package com.example.ui.settings.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.feature.settings.model.ActivityAction
import com.example.ui.settings.ios.IosBottomSpacer
import com.example.ui.settings.ios.IosDivider
import com.example.ui.settings.ios.IosFont
import com.example.ui.settings.ios.IosGroup
import com.example.ui.settings.ios.IosIconBadge
import com.example.ui.settings.ios.IosListPadding
import com.example.ui.settings.ios.IosRow
import com.example.ui.settings.ios.IosSectionHeader
import com.example.ui.settings.ios.IosSettingsColors
import com.example.ui.settings.ios.IosSettingsScaffold
import com.example.ui.settings.ios.IosValueRow
import com.example.ui.settings.viewmodel.ActivityViewModel

@Composable
fun ActivityCenterScreen(
    onBack: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    viewModel: ActivityViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { err ->
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            viewModel.dispatch(ActivityAction.ClearError)
        }
    }

    IosSettingsScaffold(
        title = "Actividad",
        onBack = onBack,
        actions = {
            IconButton(onClick = { viewModel.dispatch(ActivityAction.RefreshSummary) }) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = IosSettingsColors.blue)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = IosListPadding
            ) {
                item { IosSectionHeader("Resumen de uso") }
                item {
                    IosGroup {
                        IosValueRow("Mensajes", uiState.messagesCount.toString(), Icons.AutoMirrored.Filled.Chat, IosSettingsColors.teal)
                        IosDivider()
                        IosValueRow("Llamadas", uiState.callsCount.toString(), Icons.Default.Call, IosSettingsColors.green)
                        IosDivider()
                        IosValueRow("Almacenamiento", uiState.storageUsed, Icons.Default.Storage, IosSettingsColors.orange)
                        IosDivider()
                        IosValueRow(
                            title = "Red",
                            value = if (uiState.isOnline) "Conectado" else "Sin red",
                            icon = Icons.Default.Wifi,
                            iconTint = if (uiState.isOnline) IosSettingsColors.green else IosSettingsColors.red
                        )
                    }
                }

                item { IosSectionHeader("Diagnóstico") }
                item {
                    IosGroup {
                        IosRow(
                            title = "Diagnóstico del sistema",
                            subtitle = "Monitoriza Reels, VCDN, ExoPlayer, caché, red y errores desde el teléfono.",
                            icon = Icons.Default.MonitorHeart,
                            iconTint = IosSettingsColors.pink,
                            onClick = onNavigateToDiagnostics
                        )
                    }
                }

                item { IosSectionHeader("Almacenamiento local") }
                item {
                    IosGroup {
                        IosValueRow("Base de datos (Room)", uiState.databaseSize, Icons.Default.Storage, IosSettingsColors.blue)
                        IosDivider()
                        IosValueRow("Caché y multimedia", uiState.mediaSize, Icons.Default.Folder, IosSettingsColors.orange)
                    }
                }

                item { IosSectionHeader("Estado del sistema") }
                item {
                    IosGroup {
                        IosValueRow("Sincronización de chats", uiState.lastSynchronization, Icons.Default.CheckCircle, IosSettingsColors.green)
                        IosDivider()
                        IosValueRow(
                            title = "Calidad de conexión",
                            value = uiState.connectionStatus,
                            icon = Icons.Default.Wifi,
                            iconTint = if (uiState.isOnline) IosSettingsColors.green else IosSettingsColors.red
                        )
                        IosDivider()
                        IosValueRow("Caché de datos en disco", uiState.dataUsageToday, Icons.Default.DataUsage, IosSettingsColors.teal)
                    }
                }

                item { IosSectionHeader("Dispositivos activos") }
                item {
                    IosGroup {
                        if (uiState.activeDevices.isEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("No hay dispositivos registrados", color = IosSettingsColors.secondaryLabel, fontFamily = IosFont, fontSize = 14.sp)
                            }
                        } else {
                            uiState.activeDevices.forEachIndexed { index, device ->
                                if (index > 0) IosDivider()
                                DeviceRow(
                                    name = device.name,
                                    time = device.lastActive,
                                    icon = if (device.iconType == "computer") Icons.Default.Computer else Icons.Default.Smartphone,
                                    isCurrent = device.isCurrent
                                )
                            }
                        }
                    }
                }

                item { IosBottomSpacer() }
            }
        }
    }
}

@Composable
private fun DeviceRow(name: String, time: String, icon: ImageVector, isCurrent: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IosIconBadge(icon, IosSettingsColors.gray)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = IosSettingsColors.label, fontFamily = IosFont, fontSize = 16.sp)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrent) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(IosSettingsColors.green, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = if (isCurrent) "$time · Este dispositivo" else time,
                    color = if (isCurrent) IosSettingsColors.green else IosSettingsColors.secondaryLabel,
                    fontFamily = IosFont,
                    fontSize = 13.sp
                )
            }
        }
    }
}
