package com.example.ui.settings.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.feature.settings.model.PresenceAction
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
import com.example.ui.settings.viewmodel.PresenceViewModel

@Composable
fun PresenceCenterScreen(
    onBack: () -> Unit,
    viewModel: PresenceViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.successMessage, uiState.errorMessage) {
        uiState.successMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.dispatch(PresenceAction.ClearMessages)
        }
        uiState.errorMessage?.let { err ->
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            viewModel.dispatch(PresenceAction.ClearMessages)
        }
    }

    IosSettingsScaffold(title = "Presencia", onBack = onBack) { padding ->
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
                item { StatusHeaderGroup(uiState.status) }

                item { IosSectionHeader("Estado de presencia") }
                item {
                    IosGroup {
                        listOf(
                            Triple("online", "Disponible", "Muestra cuando estás activo en la app"),
                            Triple("busy", "Ocupado", "Informa que estás ocupado o trabajando"),
                            Triple("invisible", "Invisible", "Oculta totalmente tu presencia")
                        ).forEachIndexed { index, (key, label, description) ->
                            if (index > 0) IosDivider(startIndent = 16.dp)
                            IosRadioRow(
                                title = label,
                                subtitle = description,
                                selected = uiState.status.equals(key, ignoreCase = true),
                                onClick = { viewModel.dispatch(PresenceAction.ChangePresenceStatus(key)) }
                            )
                        }
                    }
                    IosSectionFooter("Elige cómo quieres aparecer ante los demás usuarios.")
                }

                item { IosSectionHeader("Privacidad de última conexión") }
                item {
                    IosGroup {
                        listOf("Todos", "Mis Contactos", "Nadie").forEachIndexed { index, option ->
                            if (index > 0) IosDivider(startIndent = 16.dp)
                            IosRadioRow(
                                title = option,
                                selected = uiState.lastSeenVisibility.equals(option, ignoreCase = true),
                                onClick = { viewModel.dispatch(PresenceAction.UpdateLastSeenVisibility(option)) }
                            )
                        }
                    }
                    IosSectionFooter("Controla quién puede ver tu última hora de conexión.")
                }

                item { IosSectionHeader("Modo invisible") }
                item {
                    IosGroup {
                        IosToggleRow(
                            title = "Modo invisible automático",
                            subtitle = "Oculta tu actividad mientras usas PanaLink.",
                            checked = uiState.isInvisibleMode,
                            onCheckedChange = { viewModel.dispatch(PresenceAction.ToggleInvisibleMode(it)) },
                            icon = if (uiState.isInvisibleMode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            iconTint = IosSettingsColors.indigo
                        )
                    }
                    IosSectionFooter("Nadie sabrá cuándo abres los chats o navegas por la app.")
                }

                item { IosBottomSpacer() }
            }
        }
    }
}

/** Cabecera con el estado de presencia actual. */
@Composable
private fun StatusHeaderGroup(status: String) {
    val (color, title, subtitle) = when (status) {
        "busy" -> Triple(IosSettingsColors.orange, "Ocupado", "Notificaciones sutiles activas para tus panas")
        "invisible" -> Triple(IosSettingsColors.indigo, "Invisible", "Navegas en modo fantasma sin mostrar tu conexión")
        else -> Triple(IosSettingsColors.green, "Disponible", "Visible para todos tus contactos en PanaLink")
    }
    IosGroup {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Circle, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = IosSettingsColors.label, fontFamily = IosFont, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = IosSettingsColors.secondaryLabel, fontFamily = IosFont, fontSize = 13.sp)
            }
        }
    }
}
