package com.example.ui.settings.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.feature.settings.model.PrivacyAction
import com.example.ui.settings.ios.IosBottomSpacer
import com.example.ui.settings.ios.IosDivider
import com.example.ui.settings.ios.IosGroup
import com.example.ui.settings.ios.IosListPadding
import com.example.ui.settings.ios.IosRadioRow
import com.example.ui.settings.ios.IosSectionFooter
import com.example.ui.settings.ios.IosSectionHeader
import com.example.ui.settings.ios.IosSettingsColors
import com.example.ui.settings.ios.IosSettingsScaffold
import com.example.ui.settings.ios.IosToggleRow
import com.example.ui.settings.viewmodel.PrivacyViewModel

@Composable
fun PrivacyCenterScreen(
    onBack: () -> Unit,
    viewModel: PrivacyViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.dispatch(PrivacyAction.ClearMessages)
        }
    }

    IosSettingsScaffold(title = "Privacidad", onBack = onBack) { padding ->
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
                item { IosSectionHeader("Última vez y en línea") }
                item {
                    IosGroup {
                        listOf("Todos", "Mis Contactos", "Nadie").forEachIndexed { index, option ->
                            if (index > 0) IosDivider(startIndent = 16.dp)
                            IosRadioRow(
                                title = option,
                                selected = uiState.lastSeenVisibility.equals(option, ignoreCase = true),
                                onClick = { viewModel.dispatch(PrivacyAction.UpdateLastSeen(option)) }
                            )
                        }
                    }
                    IosSectionFooter("Elige quién puede ver cuándo estuviste en línea por última vez.")
                }

                item { IosSectionHeader("Mensajes") }
                item {
                    IosGroup {
                        IosToggleRow(
                            title = "Confirmaciones de lectura",
                            subtitle = "Al desactivarlo no verás ni enviarás el doble tilde azul.",
                            checked = uiState.readReceiptsEnabled,
                            onCheckedChange = { viewModel.dispatch(PrivacyAction.ToggleReadReceipts(it)) },
                            icon = Icons.Default.Visibility,
                            iconTint = IosSettingsColors.blue
                        )
                        IosDivider()
                        IosToggleRow(
                            title = "Lectura inteligente",
                            subtitle = "Envía la confirmación al abrir la conversación.",
                            checked = uiState.smartReadReceiptsEnabled,
                            onCheckedChange = { viewModel.dispatch(PrivacyAction.ToggleSmartReadReceipts(it)) },
                            icon = Icons.Default.Bolt,
                            iconTint = IosSettingsColors.orange
                        )
                    }
                }

                item { IosSectionHeader("Presencia") }
                item {
                    IosGroup {
                        IosToggleRow(
                            title = "Modo invisible",
                            subtitle = "Navega y lee sin mostrar 'En línea'.",
                            checked = uiState.invisibleModeEnabled,
                            onCheckedChange = { viewModel.dispatch(PrivacyAction.ToggleInvisibleMode(it)) },
                            icon = if (uiState.invisibleModeEnabled) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            iconTint = IosSettingsColors.indigo
                        )
                    }
                    IosSectionFooter("El modo invisible oculta tu estado pero sigues viendo el de los demás.")
                }

                item { IosSectionHeader("Estado de presencia predeterminado") }
                item {
                    IosGroup {
                        listOf(
                            "online" to "En línea",
                            "busy" to "Ocupado",
                            "invisible" to "Invisible"
                        ).forEachIndexed { index, (statusKey, label) ->
                            if (index > 0) IosDivider(startIndent = 16.dp)
                            IosRadioRow(
                                title = label,
                                selected = uiState.profilePresence.equals(statusKey, ignoreCase = true),
                                onClick = { viewModel.dispatch(PrivacyAction.UpdatePresence(statusKey)) },
                                subtitle = null
                            )
                        }
                    }
                    IosSectionFooter("Define cómo te verán tus contactos por defecto al entrar a la app.")
                }

                item { IosBottomSpacer() }
            }
        }
    }
}
