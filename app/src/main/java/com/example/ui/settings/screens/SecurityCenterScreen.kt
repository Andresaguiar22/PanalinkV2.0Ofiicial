package com.example.ui.settings.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pattern
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.feature.settings.model.SecurityAction
import com.example.ui.components.QrCodeView
import com.example.ui.settings.ios.IosActionRow
import com.example.ui.settings.ios.IosBadge
import com.example.ui.settings.ios.IosBottomSpacer
import com.example.ui.settings.ios.IosDivider
import com.example.ui.settings.ios.IosFont
import com.example.ui.settings.ios.IosGroup
import com.example.ui.settings.ios.IosListPadding
import com.example.ui.settings.ios.IosSectionFooter
import com.example.ui.settings.ios.IosSectionHeader
import com.example.ui.settings.ios.IosSettingsColors
import com.example.ui.settings.ios.IosSettingsScaffold
import com.example.ui.settings.ios.IosToggleRow
import com.example.ui.settings.ios.IosValueRow
import com.example.ui.settings.viewmodel.SecurityViewModel

@Composable
fun SecurityCenterScreen(
    onBack: () -> Unit,
    viewModel: SecurityViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var newPinInput by remember { mutableStateOf("") }
    var scanInputText by remember { mutableStateOf("") }

    LaunchedEffect(uiState.successMessage, uiState.errorMessage) {
        uiState.successMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.dispatch(SecurityAction.ClearMessages)
        }
        uiState.errorMessage?.let { err ->
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            viewModel.dispatch(SecurityAction.ClearMessages)
        }
    }

    IosSettingsScaffold(title = "Seguridad", onBack = onBack) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = IosSettingsColors.blue)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = IosListPadding
            ) {
                item { ProtectionHeaderGroup(uiState) }

                item { IosSectionHeader("Bloqueo de la app") }
                item {
                    IosGroup {
                        IosValueRow(
                            title = "PIN de seguridad",
                            value = if (uiState.hasPin) "Configurado" else "Sin configurar",
                            icon = Icons.Default.Lock,
                            iconTint = IosSettingsColors.green
                        )
                        IosDivider()
                        IosActionRow(
                            title = if (uiState.hasPin) "Cambiar PIN" else "Configurar PIN",
                            onClick = { viewModel.dispatch(SecurityAction.ShowPinDialog(true)) },
                            color = IosSettingsColors.blue
                        )
                        if (uiState.hasPin) {
                            IosDivider(startIndent = 16.dp)
                            IosActionRow(
                                title = "Eliminar PIN",
                                onClick = { viewModel.dispatch(SecurityAction.RemovePin) },
                                color = IosSettingsColors.red
                            )
                        }
                    }
                }

                item {
                    IosGroup {
                        IosValueRow(
                            title = "Patrón de desbloqueo",
                            value = if (uiState.hasPattern) "Activo" else "Inactivo",
                            icon = Icons.Default.Pattern,
                            iconTint = IosSettingsColors.blue
                        )
                        IosDivider()
                        IosActionRow(
                            title = if (uiState.hasPattern) "Cambiar patrón" else "Configurar patrón",
                            onClick = { viewModel.dispatch(SecurityAction.ShowPatternDialog(true)) },
                            color = IosSettingsColors.blue
                        )
                        if (uiState.hasPattern) {
                            IosDivider(startIndent = 16.dp)
                            IosActionRow(
                                title = "Eliminar patrón",
                                onClick = { viewModel.dispatch(SecurityAction.RemovePattern) },
                                color = IosSettingsColors.red
                            )
                        }
                    }
                    IosSectionFooter("Si ambos están activos, el patrón tiene prioridad al desbloquear.")
                }

                item { IosSectionHeader("Bloqueo automático") }
                item {
                    AutoLockGroup(uiState, viewModel)
                }

                item { IosSectionHeader("Identidad y acceso") }
                item {
                    IosGroup {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("PIN de identidad", color = IosSettingsColors.secondaryLabel, fontFamily = IosFont, fontSize = 12.sp)
                                Text(
                                    text = uiState.userPinCode.ifEmpty { "—" },
                                    color = IosSettingsColors.blue,
                                    fontFamily = IosFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    letterSpacing = 2.sp
                                )
                            }
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Pana PIN", uiState.userPinCode))
                                Toast.makeText(context, "PIN copiado", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copiar PIN", tint = IosSettingsColors.blue)
                            }
                        }
                        IosDivider(startIndent = 16.dp)
                        IosActionRow(
                            title = "Ver mi QR Pana",
                            onClick = { viewModel.dispatch(SecurityAction.ShowQrDialog(true)) },
                            color = IosSettingsColors.blue
                        )
                        IosDivider(startIndent = 16.dp)
                        IosActionRow(
                            title = "Escanear QR",
                            onClick = { viewModel.dispatch(SecurityAction.ShowScanner(true)) },
                            color = IosSettingsColors.green
                        )
                    }
                    IosSectionFooter("Tu PIN y código QR te identifican de forma segura para que otros Panas te agreguen.")
                }

                item { IosSectionHeader("Protección adicional") }
                item {
                    IosGroup {
                        IosToggleRow(
                            title = "Desbloqueo biométrico",
                            subtitle = if (uiState.biometricsAvailable)
                                "Usa tu huella o rostro para desbloquear PanaLink."
                            else
                                "No disponible: el dispositivo no tiene biometría configurada.",
                            checked = uiState.isBiometricsEnabled,
                            onCheckedChange = { viewModel.dispatch(SecurityAction.ToggleBiometrics(it)) },
                            icon = Icons.Default.Fingerprint,
                            iconTint = IosSettingsColors.green
                        )
                        IosDivider()
                        IosActionRow(
                            title = "Bloquear ahora",
                            onClick = { com.example.security.AppLockManager.lockNow(context) },
                            color = if (uiState.hasPin || uiState.hasPattern) IosSettingsColors.orange else IosSettingsColors.gray,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IosSectionFooter("Bloquear ahora cierra la sesión al instante y pide tu código para volver a entrar.")
                }

                item { IosBottomSpacer() }
            }
        }
    }

    if (uiState.isPinDialogVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.dispatch(SecurityAction.ShowPinDialog(false)) },
            containerColor = IosSettingsColors.cell,
            title = {
                Text(
                    text = if (uiState.hasPin) "Cambiar PIN de seguridad" else "Configurar PIN de seguridad",
                    color = IosSettingsColors.label,
                    fontFamily = IosFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Ingresa un PIN numérico de 4 a 6 dígitos para proteger la aplicación.",
                        color = IosSettingsColors.secondaryLabel,
                        fontFamily = IosFont,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) newPinInput = it },
                        label = { Text("PIN de seguridad", color = IosSettingsColors.secondaryLabel) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IosSettingsColors.blue,
                            unfocusedBorderColor = IosSettingsColors.separator,
                            focusedTextColor = IosSettingsColors.label,
                            unfocusedTextColor = IosSettingsColors.label
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dispatch(SecurityAction.SetPin(newPinInput))
                        newPinInput = ""
                    }
                ) {
                    Text("Guardar", color = IosSettingsColors.blue, fontFamily = IosFont, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dispatch(SecurityAction.ShowPinDialog(false)) }) {
                    Text("Cancelar", color = IosSettingsColors.blue, fontFamily = IosFont)
                }
            }
        )
    }

    if (uiState.isQrDialogVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.dispatch(SecurityAction.ShowQrDialog(false)) },
            containerColor = IosSettingsColors.cell,
            title = {
                Text(
                    text = "Tu código QR de identidad",
                    color = IosSettingsColors.label,
                    fontFamily = IosFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(IosSettingsColors.label)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        QrCodeView(
                            pin = uiState.userPinCode,
                            payload = "panalink:pin:${uiState.userPinCode}",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Text(
                        text = "PIN: ${uiState.userPinCode}",
                        color = IosSettingsColors.blue,
                        fontFamily = IosFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Muestra este código a otro Pana para que te agregue al instante.",
                        color = IosSettingsColors.secondaryLabel,
                        fontFamily = IosFont,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dispatch(SecurityAction.ShowQrDialog(false)) }) {
                    Text("Cerrar", color = IosSettingsColors.blue, fontFamily = IosFont, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    if (uiState.isPatternDialogVisible) {
        var firstPattern by remember { mutableStateOf<List<Int>?>(null) }
        var patternError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { viewModel.dispatch(SecurityAction.ShowPatternDialog(false)) },
            containerColor = IosSettingsColors.cell,
            title = {
                Text(
                    text = if (firstPattern == null) "Dibuja tu nuevo patrón" else "Confirma tu patrón",
                    color = IosSettingsColors.label,
                    fontFamily = IosFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (firstPattern == null)
                            "Conecta al menos 4 puntos. Lo usarás para desbloquear PanaLink."
                        else
                            "Dibuja el mismo patrón otra vez para confirmarlo.",
                        color = IosSettingsColors.secondaryLabel,
                        fontFamily = IosFont,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    com.example.ui.security.PatternPad(
                        onPatternComplete = { drawn ->
                            val first = firstPattern
                            when {
                                first == null -> {
                                    if (drawn.size < 4) {
                                        patternError = "Conecta al menos 4 puntos"
                                    } else {
                                        patternError = null
                                        firstPattern = drawn
                                    }
                                }
                                first == drawn -> {
                                    patternError = null
                                    viewModel.dispatch(SecurityAction.SetPattern(drawn))
                                }
                                else -> {
                                    patternError = "Los patrones no coinciden, inténtalo de nuevo"
                                    firstPattern = null
                                }
                            }
                        },
                        modifier = Modifier.size(240.dp)
                    )
                    patternError?.let {
                        Text(it, color = IosSettingsColors.red, fontFamily = IosFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.dispatch(SecurityAction.ShowPatternDialog(false)) }) {
                    Text("Cancelar", color = IosSettingsColors.blue, fontFamily = IosFont)
                }
            }
        )
    }

    if (uiState.isScannerVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.dispatch(SecurityAction.ShowScanner(false)) },
            containerColor = IosSettingsColors.cell,
            title = {
                Text(
                    text = "Escanear QR de Pana",
                    color = IosSettingsColors.label,
                    fontFamily = IosFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Ingresa o escanea el payload recibido (ej: panalink:pin:123456).",
                        color = IosSettingsColors.secondaryLabel,
                        fontFamily = IosFont,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = scanInputText,
                        onValueChange = { scanInputText = it },
                        label = { Text("Payload de QR", color = IosSettingsColors.secondaryLabel) },
                        placeholder = { Text("panalink:pin:123456", color = IosSettingsColors.tertiaryLabel) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IosSettingsColors.blue,
                            unfocusedBorderColor = IosSettingsColors.separator,
                            focusedTextColor = IosSettingsColors.label,
                            unfocusedTextColor = IosSettingsColors.label
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dispatch(SecurityAction.ProcessScannedQr(scanInputText))
                        scanInputText = ""
                    }
                ) {
                    Text("Validar QR", color = IosSettingsColors.blue, fontFamily = IosFont, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dispatch(SecurityAction.ShowScanner(false)) }) {
                    Text("Cancelar", color = IosSettingsColors.blue, fontFamily = IosFont)
                }
            }
        )
    }
}

/** Cabecera del centro de seguridad: escudo + nivel de proteccion. */
@Composable
private fun ProtectionHeaderGroup(uiState: com.example.feature.settings.model.SecurityUiState) {
    val isProtected = uiState.hasPin || uiState.hasPattern
    IosGroup {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background((if (isProtected) IosSettingsColors.green else IosSettingsColors.orange).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isProtected) Icons.Default.Shield else Icons.Default.Security,
                    contentDescription = null,
                    tint = if (isProtected) IosSettingsColors.green else IosSettingsColors.orange,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isProtected) "Protección alta" else "Protección básica",
                    color = IosSettingsColors.label,
                    fontFamily = IosFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (isProtected)
                        "PanaLink pedirá tu código al abrirse."
                    else
                        "Configura un PIN o patrón para que la app se bloquee de verdad.",
                    color = IosSettingsColors.secondaryLabel,
                    fontFamily = IosFont,
                    fontSize = 13.sp
                )
            }
        }
    }
}

/** Selector de tiempo de bloqueo automatico (segmentos tipo iOS). */
@Composable
private fun AutoLockGroup(
    uiState: com.example.feature.settings.model.SecurityUiState,
    viewModel: SecurityViewModel
) {
    val enabled = uiState.hasPin || uiState.hasPattern
    IosGroup {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = IosSettingsColors.orange, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Pedir código tras", color = IosSettingsColors.label, fontFamily = IosFont, fontSize = 16.sp)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    0L to "Inmediato",
                    30_000L to "30 s",
                    60_000L to "1 min",
                    300_000L to "5 min"
                ).forEach { (delayMs, label) ->
                    val selected = uiState.autoLockMs == delayMs
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) IosSettingsColors.blue else IosSettingsColors.cellElevated)
                            .clickable(enabled = enabled) {
                                viewModel.dispatch(SecurityAction.SetAutoLock(delayMs))
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = when {
                                selected -> IosSettingsColors.onAccent
                                enabled -> IosSettingsColors.label
                                else -> IosSettingsColors.tertiaryLabel
                            },
                            fontFamily = IosFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            if (!enabled) {
                IosBadge("Configura un PIN o patrón primero", IosSettingsColors.orange)
            }
        }
    }
}
