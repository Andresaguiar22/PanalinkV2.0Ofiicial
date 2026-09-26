package com.example.ui.settings.screens

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.feature.diagnostics.model.DiagnosticCaptureState
import com.example.feature.diagnostics.model.DiagnosticCategory
import com.example.feature.diagnostics.model.DiagnosticEvent
import com.example.feature.diagnostics.model.DiagnosticSeverity
import com.example.feature.diagnostics.model.matches
import com.example.ui.settings.ios.IosBottomSpacer
import com.example.ui.settings.ios.IosFont
import com.example.ui.settings.ios.IosGroup
import com.example.ui.settings.ios.IosListPadding
import com.example.ui.settings.ios.IosSectionHeader
import com.example.ui.settings.ios.IosSettingsColors
import com.example.ui.settings.ios.IosSettingsScaffold
import com.example.ui.settings.viewmodel.DiagnosticsViewModel

@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = viewModel()
) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val captureState by viewModel.captureState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf(DiagnosticCategory.ALL) }

    val visibleEvents = remember(events, selectedCategory) {
        events.filter { selectedCategory.matches(it) }.reversed()
    }

    fun shareDiagnostics() {
        val text = viewModel.exportText()
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Panalink - Diagnóstico del sistema")
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "Compartir diagnóstico"
            )
        )
    }

    IosSettingsScaffold(title = "Diagnóstico", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = IosListPadding
        ) {
            item { IosSectionHeader("Monitor de procesos") }
            item {
                IosGroup {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (captureState == DiagnosticCaptureState.CAPTURING) Icons.Rounded.RadioButtonChecked else Icons.Rounded.MonitorHeart,
                            contentDescription = null,
                            tint = if (captureState == DiagnosticCaptureState.CAPTURING) IosSettingsColors.green else IosSettingsColors.secondaryLabel,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Capturar eventos", color = IosSettingsColors.label, fontFamily = IosFont, fontSize = 16.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (captureState == DiagnosticCaptureState.CAPTURING)
                                    "Capturando en tiempo real"
                                else
                                    "Detenida; los errores siguen registrándose",
                                color = IosSettingsColors.secondaryLabel,
                                fontFamily = IosFont,
                                fontSize = 13.sp
                            )
                        }
                        Switch(
                            checked = captureState == DiagnosticCaptureState.CAPTURING,
                            onCheckedChange = viewModel::setCapture,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = IosSettingsColors.label,
                                checkedTrackColor = IosSettingsColors.green,
                                uncheckedThumbColor = IosSettingsColors.secondaryLabel,
                                uncheckedTrackColor = IosSettingsColors.cellElevated
                            )
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = viewModel::clear, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Limpiar", fontFamily = IosFont)
                    }
                    Button(
                        onClick = ::shareDiagnostics,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IosSettingsColors.blue,
                            contentColor = IosSettingsColors.label
                        )
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Exportar", fontFamily = IosFont)
                    }
                }
            }

            item { IosSectionHeader("Filtros") }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DiagnosticCategory.entries.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category.label, fontFamily = IosFont) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = IosSettingsColors.blue,
                                selectedLabelColor = IosSettingsColors.label,
                                containerColor = IosSettingsColors.cell,
                                labelColor = IosSettingsColors.label
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedCategory == category,
                                borderColor = IosSettingsColors.separator,
                                selectedBorderColor = IosSettingsColors.blue
                            )
                        )
                    }
                }
            }

            item { IosSectionHeader("Línea de tiempo · ${visibleEvents.size} eventos") }

            if (visibleEvents.isEmpty()) {
                item {
                    IosGroup {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Rounded.Timeline, contentDescription = null, tint = IosSettingsColors.secondaryLabel, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Sin eventos todavía", color = IosSettingsColors.label, fontFamily = IosFont, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Activa la captura y reproduce el problema que quieres investigar.",
                                color = IosSettingsColors.secondaryLabel,
                                fontFamily = IosFont,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                items(visibleEvents, key = { "${it.timestampMs}-${it.event}-${it.correlationId}" }) { event ->
                    DiagnosticEventCard(event)
                }
            }

            item { IosBottomSpacer() }
        }
    }
}

@Composable
private fun DiagnosticEventCard(event: DiagnosticEvent) {
    val severityIcon = when (event.severity) {
        DiagnosticSeverity.SUCCESS -> Icons.Rounded.CheckCircle
        DiagnosticSeverity.WARNING -> Icons.Rounded.Warning
        DiagnosticSeverity.ERROR -> Icons.Rounded.Error
        DiagnosticSeverity.INFO -> Icons.Rounded.Info
    }
    val severityTint = when (event.severity) {
        DiagnosticSeverity.SUCCESS -> IosSettingsColors.green
        DiagnosticSeverity.WARNING -> IosSettingsColors.orange
        DiagnosticSeverity.ERROR -> IosSettingsColors.red
        DiagnosticSeverity.INFO -> IosSettingsColors.blue
    }

    IosGroup(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(severityIcon, contentDescription = null, tint = severityTint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(event.displayTime(), color = IosSettingsColors.secondaryLabel, fontFamily = IosFont, fontSize = 11.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(event.category.label, color = IosSettingsColors.label, fontFamily = IosFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(event.event, color = IosSettingsColors.label, fontFamily = IosFont, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                event.durationMs?.let {
                    Text("Duración: ${it} ms", color = IosSettingsColors.secondaryLabel, fontFamily = IosFont, fontSize = 12.sp)
                }
                event.correlationId?.let {
                    Text("ID: $it", color = IosSettingsColors.secondaryLabel, fontFamily = IosFont, fontSize = 11.sp)
                }
                event.details?.let {
                    Text(it, color = IosSettingsColors.secondaryLabel, fontFamily = IosFont, fontSize = 11.sp)
                }
            }
        }
    }
}
