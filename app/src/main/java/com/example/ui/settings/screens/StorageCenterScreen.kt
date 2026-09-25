package com.example.ui.settings.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.feature.settings.model.ActivityAction
import com.example.feature.settings.model.SettingsKeys
import com.example.ui.settings.ios.IosBottomSpacer
import com.example.ui.settings.ios.IosDivider
import com.example.ui.settings.ios.IosFont
import com.example.ui.settings.ios.IosGroup
import com.example.ui.settings.ios.IosListPadding
import com.example.ui.settings.ios.IosRadioRow
import com.example.ui.settings.ios.IosRow
import com.example.ui.settings.ios.IosSectionFooter
import com.example.ui.settings.ios.IosSectionHeader
import com.example.ui.settings.ios.IosSettingsColors
import com.example.ui.settings.ios.IosSettingsScaffold
import com.example.ui.settings.ios.IosToggleRow
import com.example.ui.settings.ios.IosValueRow
import com.example.ui.settings.viewmodel.ActivityViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StorageCenterScreen(
    onBack: () -> Unit,
    viewModel: ActivityViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE) }

    var showStorageDialog by remember { mutableStateOf(false) }
    var isCleaning by remember { mutableStateOf(false) }

    var autoMobile by remember { mutableStateOf(prefs.getBoolean(SettingsKeys.STORAGE_AUTO_DOWNLOAD_MOBILE, true)) }
    var autoWifi by remember { mutableStateOf(prefs.getBoolean(SettingsKeys.STORAGE_AUTO_DOWNLOAD_WIFI, true)) }
    var autoRoaming by remember { mutableStateOf(prefs.getBoolean(SettingsKeys.STORAGE_AUTO_DOWNLOAD_ROAMING, false)) }
    var uploadQuality by remember { mutableStateOf(prefs.getString(SettingsKeys.STORAGE_UPLOAD_QUALITY, "auto") ?: "auto") }

    IosSettingsScaffold(title = "Almacenamiento y datos", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = IosListPadding
        ) {
            item {
                IosGroup {
                    IosRow(
                        title = "Administrar almacenamiento",
                        subtitle = "${uiState.storageUsed} • DB ${uiState.databaseSize} • Medios ${uiState.mediaSize}",
                        icon = Icons.Default.Folder,
                        iconTint = IosSettingsColors.blue,
                        onClick = { showStorageDialog = true }
                    )
                    IosDivider()
                    IosValueRow(
                        title = "Uso de datos (esta app)",
                        value = uiState.dataUsageToday,
                        icon = Icons.Default.DataUsage,
                        iconTint = IosSettingsColors.green,
                        subtitle = "Desde el último reinicio"
                    )
                }
            }

            item { IosSectionHeader("Descarga automática de medios") }
            item {
                IosGroup {
                    IosToggleRow(
                        title = "Datos móviles",
                        checked = autoMobile,
                        onCheckedChange = {
                            autoMobile = it
                            prefs.edit().putBoolean(SettingsKeys.STORAGE_AUTO_DOWNLOAD_MOBILE, it).apply()
                        }
                    )
                    IosDivider(startIndent = 16.dp)
                    IosToggleRow(
                        title = "Wi-Fi",
                        checked = autoWifi,
                        onCheckedChange = {
                            autoWifi = it
                            prefs.edit().putBoolean(SettingsKeys.STORAGE_AUTO_DOWNLOAD_WIFI, it).apply()
                        }
                    )
                    IosDivider(startIndent = 16.dp)
                    IosToggleRow(
                        title = "Itinerancia (roaming)",
                        checked = autoRoaming,
                        onCheckedChange = {
                            autoRoaming = it
                            prefs.edit().putBoolean(SettingsKeys.STORAGE_AUTO_DOWNLOAD_ROAMING, it).apply()
                        }
                    )
                }
                IosSectionFooter("Controla si PanaLink pre-descarga fotos y vídeos en segundo plano según tu red.")
            }

            item { IosSectionHeader("Calidad de carga de fotos") }
            item {
                IosGroup {
                    IosRadioRow(
                        title = "Automática (recomendada)",
                        selected = uploadQuality == "auto",
                        onClick = { uploadQuality = "auto"; prefs.edit().putString(SettingsKeys.STORAGE_UPLOAD_QUALITY, "auto").apply() }
                    )
                    IosDivider(startIndent = 16.dp)
                    IosRadioRow(
                        title = "Alta calidad",
                        subtitle = "Más datos",
                        selected = uploadQuality == "high",
                        onClick = { uploadQuality = "high"; prefs.edit().putString(SettingsKeys.STORAGE_UPLOAD_QUALITY, "high").apply() }
                    )
                    IosDivider(startIndent = 16.dp)
                    IosRadioRow(
                        title = "Ahorro de datos",
                        selected = uploadQuality == "data_saver",
                        onClick = { uploadQuality = "data_saver"; prefs.edit().putString(SettingsKeys.STORAGE_UPLOAD_QUALITY, "data_saver").apply() }
                    )
                }
            }

            item { IosBottomSpacer() }
        }
    }

    if (showStorageDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCleaning) showStorageDialog = false },
            containerColor = IosSettingsColors.cell,
            title = {
                Text("Administrar almacenamiento", color = IosSettingsColors.label, fontFamily = IosFont, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Uso local actual", color = IosSettingsColors.secondaryLabel, fontFamily = IosFont, fontSize = 13.sp)
                    Text("• Total: ${uiState.storageUsed}", color = IosSettingsColors.label, fontFamily = IosFont, fontSize = 14.sp)
                    Text("• Base de datos (Room): ${uiState.databaseSize}", color = IosSettingsColors.label, fontFamily = IosFont, fontSize = 14.sp)
                    Text("• Medios y archivos: ${uiState.mediaSize}", color = IosSettingsColors.label, fontFamily = IosFont, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Limpiar caché elimina imágenes, miniaturas y descargas temporales. Tus mensajes y fotos enviadas o recibidas no se borran.",
                        color = IosSettingsColors.secondaryLabel,
                        fontFamily = IosFont,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isCleaning,
                    onClick = {
                        isCleaning = true
                        scope.launch {
                            val freed = withContext(Dispatchers.IO) { clearAppCache(context) }
                            isCleaning = false
                            showStorageDialog = false
                            viewModel.dispatch(ActivityAction.RefreshStorage)
                            Toast.makeText(context, "Caché liberada: $freed", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(if (isCleaning) "Limpiando..." else "Limpiar caché", color = IosSettingsColors.red, fontFamily = IosFont, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(enabled = !isCleaning, onClick = { showStorageDialog = false }) {
                    Text("Cerrar", color = IosSettingsColors.blue, fontFamily = IosFont)
                }
            }
        )
    }
}

private fun clearAppCache(context: Context): String {
    var freed = 0L
    fun sizeOf(file: java.io.File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { sizeOf(it) } ?: 0L
    }
    try {
        val targets = listOfNotNull(context.cacheDir, context.externalCacheDir)
        for (dir in targets) {
            freed += sizeOf(dir)
            dir.deleteRecursively()
            dir.mkdirs()
        }
    } catch (_: Exception) { }
    return when {
        freed >= 1024 * 1024 * 1024 -> "%.1f GB".format(freed / (1024.0 * 1024 * 1024))
        freed >= 1024 * 1024 -> "%.1f MB".format(freed / (1024.0 * 1024))
        freed >= 1024 -> "%.1f KB".format(freed / 1024.0)
        else -> "$freed B"
    }
}
