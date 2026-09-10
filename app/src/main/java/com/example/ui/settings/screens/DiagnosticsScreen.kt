package com.example.ui.settings.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.ui.settings.viewmodel.DiagnosticsViewModel

private val DiagnosticBackground = Color(0xFF121B22)
private val DiagnosticCard = Color(0xFF1E2B33)
private val DiagnosticMuted = Color(0xFF90A4AE)
private val DiagnosticGreen = Color(0xFF25D366)

@OptIn(ExperimentalMaterial3Api::class)
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
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Panalink - Diagnóstico del sistema")
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Compartir diagnóstico"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnóstico del sistema", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DiagnosticBackground)
            )
        },
        containerColor = DiagnosticBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DiagnosticCard),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (captureState == DiagnosticCaptureState.CAPTURING) Icons.Default.RadioButtonChecked else Icons.Default.MonitorHeart,
                                contentDescription = null,
                                tint = if (captureState == DiagnosticCaptureState.CAPTURING) DiagnosticGreen else DiagnosticMuted,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Monitor de procesos", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    if (captureState == DiagnosticCaptureState.CAPTURING) "Capturando eventos en tiempo real"
                                    else "Captura detenida; los errores siguen registrándose",
                                    color = DiagnosticMuted,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = captureState == DiagnosticCaptureState.CAPTURING,
                                onCheckedChange = viewModel::setCapture,
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DiagnosticGreen)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Usa la captura para reproducir un problema y obtener una línea de tiempo de VCDN, ExoPlayer, caché y red. No sustituye Logcat.",
                            color = DiagnosticMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::clear, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Limpiar")
                    }
                    Button(onClick = ::shareDiagnostics, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Exportar")
                    }
                }
            }

            item {
                Text("Filtros", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DiagnosticCategory.entries.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category.label) }
                        )
                    }
                }
            }

            item {
                Text(
                    "Línea de tiempo · ${visibleEvents.size} eventos",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (visibleEvents.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = DiagnosticCard), shape = RoundedCornerShape(16.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Timeline, contentDescription = null, tint = DiagnosticMuted, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Sin eventos todavía", color = Color.White, fontWeight = FontWeight.Medium)
                            Text("Activa la captura y reproduce el problema que quieres investigar.", color = DiagnosticMuted, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                items(visibleEvents, key = { "${it.timestampMs}-${it.event}-${it.correlationId}" }) { event ->
                    DiagnosticEventCard(event)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticEventCard(event: DiagnosticEvent) {
    val severityIcon = when (event.severity) {
        DiagnosticSeverity.SUCCESS -> Icons.Default.CheckCircle
        DiagnosticSeverity.WARNING -> Icons.Default.Warning
        DiagnosticSeverity.ERROR -> Icons.Default.Error
        DiagnosticSeverity.INFO -> Icons.Default.Info
    }
    val severityTint = when (event.severity) {
        DiagnosticSeverity.SUCCESS -> Color(0xFF4CAF50)
        DiagnosticSeverity.WARNING -> Color(0xFFFFB300)
        DiagnosticSeverity.ERROR -> Color(0xFFFF5252)
        DiagnosticSeverity.INFO -> Color(0xFF03A9F4)
    }

    Card(colors = CardDefaults.cardColors(containerColor = DiagnosticCard), shape = RoundedCornerShape(14.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(severityIcon, contentDescription = null, tint = severityTint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(event.displayTime(), color = DiagnosticMuted, fontSize = 11.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(event.category.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(event.event, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                event.durationMs?.let {
                    Text("Duración: ${it} ms", color = DiagnosticMuted, fontSize = 12.sp)
                }
                event.correlationId?.let {
                    Text("ID: $it", color = DiagnosticMuted, fontSize = 11.sp)
                }
                event.details?.let {
                    Text(it, color = DiagnosticMuted, fontSize = 11.sp)
                }
            }
        }
    }
}
