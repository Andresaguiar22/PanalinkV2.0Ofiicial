package com.example.ui.settings.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.feature.settings.model.ControlCenterUiState
import com.example.feature.settings.model.DashboardAction
import com.example.ui.settings.ios.IosActionRow
import com.example.ui.settings.ios.IosBadge
import com.example.ui.settings.ios.IosBottomSpacer
import com.example.ui.settings.ios.IosDivider
import com.example.ui.settings.ios.IosFont
import com.example.ui.settings.ios.IosGroup
import com.example.ui.settings.ios.IosListPadding
import com.example.ui.settings.ios.IosRow
import com.example.ui.settings.ios.IosSectionHeader
import com.example.ui.settings.ios.IosSettingsColors
import com.example.ui.settings.ios.IosSettingsScaffold
import com.example.ui.settings.ios.IosValueRow
import com.example.ui.settings.viewmodel.DashboardViewModel

@Composable
fun ControlCenterScreen(
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToPresence: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToChats: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToCustomization: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToActivity: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onLogout: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    viewModel: DashboardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { err ->
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            viewModel.dispatch(DashboardAction.ClearError)
        }
    }

    IosSettingsScaffold(
        title = "Centro de Control",
        onBack = onBack,
        actions = {
            IconButton(onClick = { viewModel.dispatch(DashboardAction.RefreshDashboard) }) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
            }
        }
    ) { padding ->
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
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300))
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = IosListPadding
                ) {
                    item { ProfileHeaderGroup(uiState, onNavigateToProfile) }

                    item {
                        IosGroup {
                            IosValueRow(
                                title = "Dispositivos activos",
                                value = uiState.activeDevicesCount.toString(),
                                icon = Icons.Default.Person,
                                iconTint = IosSettingsColors.teal
                            )
                            IosDivider()
                            IosValueRow(
                                title = "Almacenamiento",
                                value = uiState.storageUsedSummary,
                                icon = Icons.Default.Storage,
                                iconTint = IosSettingsColors.orange
                            )
                            IosDivider()
                            IosValueRow(
                                title = "Última sincronización",
                                value = uiState.lastSynchronization,
                                icon = Icons.Default.Refresh,
                                iconTint = IosSettingsColors.indigo
                            )
                        }
                    }

                    item { IosSectionHeader("Módulos inteligentes") }
                    item {
                        IosGroup {
                            IosRow(
                                title = "Seguridad y acceso",
                                subtitle = uiState.securitySummary,
                                icon = Icons.Default.Security,
                                iconTint = IosSettingsColors.red,
                                trailingText = if (uiState.hasPin && uiState.is2FaEnabled) "Máxima" else if (uiState.hasPin) "PIN" else "Básica",
                                onClick = onNavigateToSecurity
                            )
                            IosDivider()
                            IosRow(
                                title = "Actividad y sistema",
                                subtitle = uiState.activitySummary,
                                icon = Icons.Default.Restore,
                                iconTint = IosSettingsColors.blue,
                                trailingText = "${uiState.messagesCount} msj",
                                onClick = onNavigateToActivity
                            )
                            IosDivider()
                            IosRow(
                                title = "Presencia",
                                subtitle = uiState.presenceSummary,
                                icon = Icons.Default.AccountCircle,
                                iconTint = IosSettingsColors.green,
                                onClick = onNavigateToPresence
                            )
                        }
                    }

                    item { IosSectionHeader("Ajustes") }
                    item {
                        IosGroup {
                            IosRow("Perfil", onNavigateToProfile, Icons.Default.Person, IosSettingsColors.blue, uiState.profileSummary)
                            IosDivider()
                            IosRow("Presencia", onNavigateToPresence, Icons.Default.AccountCircle, IosSettingsColors.green, uiState.presenceSummary)
                            IosDivider()
                            IosRow("Privacidad", onNavigateToPrivacy, Icons.Default.Lock, IosSettingsColors.purple, uiState.privacySummary)
                            IosDivider()
                            IosRow("Seguridad", onNavigateToSecurity, Icons.Default.Security, IosSettingsColors.red, uiState.securitySummary)
                            IosDivider()
                            IosRow("Chats", onNavigateToChats, Icons.AutoMirrored.Filled.Chat, IosSettingsColors.teal, uiState.chatsSummary)
                            IosDivider()
                            IosRow("Notificaciones", onNavigateToNotifications, Icons.Default.Notifications, IosSettingsColors.orange, uiState.notificationsSummary)
                            IosDivider()
                            IosRow("Personalización", onNavigateToCustomization, Icons.Default.ColorLens, IosSettingsColors.pink, uiState.customizationSummary)
                            IosDivider()
                            IosRow("Almacenamiento", onNavigateToStorage, Icons.Default.Storage, IosSettingsColors.orange, uiState.storageSummary)
                            IosDivider()
                            IosRow("Actividad", onNavigateToActivity, Icons.Default.Restore, IosSettingsColors.indigo, uiState.activitySummary)
                            IosDivider()
                            IosRow(
                                title = "Información",
                                onClick = onNavigateToAbout,
                                icon = Icons.Default.Info,
                                iconTint = IosSettingsColors.gray,
                                subtitle = "Versión ${uiState.appVersion} • Ayuda y soporte"
                            )
                        }
                    }

                    item { IosSectionHeader("Cuenta") }
                    item {
                        IosGroup {
                            IosActionRow(
                                title = "Cerrar sesión",
                                onClick = { showLogoutDialog = true },
                                color = IosSettingsColors.red
                            )
                            IosDivider(startIndent = 16.dp)
                            IosActionRow(
                                title = "Eliminar cuenta",
                                onClick = { showDeleteAccountDialog = true },
                                color = IosSettingsColors.red
                            )
                        }
                    }

                    item { IosBottomSpacer() }
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = IosSettingsColors.cell,
            title = { Text("Cerrar sesión", color = IosSettingsColors.label, fontFamily = IosFont) },
            text = { Text("¿Estás seguro de que quieres cerrar la sesión?", color = IosSettingsColors.label, fontFamily = IosFont) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout(onComplete = onLogout)
                    }
                ) {
                    Text("Cerrar sesión", color = IosSettingsColors.red, fontFamily = IosFont, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancelar", color = IosSettingsColors.blue, fontFamily = IosFont)
                }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            containerColor = IosSettingsColors.cell,
            title = { Text("Eliminar cuenta", color = IosSettingsColors.label, fontFamily = IosFont) },
            text = { Text("Esta acción es irreversible y borrará todos tus datos. ¿Estás seguro?", color = IosSettingsColors.label, fontFamily = IosFont) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAccountDialog = false
                        viewModel.deleteAccount(onComplete = onDeleteAccount)
                    }
                ) {
                    Text("Eliminar", color = IosSettingsColors.red, fontFamily = IosFont, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancelar", color = IosSettingsColors.blue, fontFamily = IosFont)
                }
            }
        )
    }
}

/**
 * Cabecera de perfil del hub: avatar con anillo, nombre, handle y las pastillas
 * de estado (presencia / proteccion), como la tarjeta de cuenta de iOS.
 */
@Composable
private fun ProfileHeaderGroup(
    uiState: ControlCenterUiState,
    onNavigateToProfile: () -> Unit
) {
    val presence = when (uiState.presenceStatus) {
        "busy" -> Triple("Ocupado", IosSettingsColors.red, IosSettingsColors.red)
        "invisible" -> Triple("Invisible", IosSettingsColors.gray, IosSettingsColors.gray)
        else -> Triple("Disponible", IosSettingsColors.green, IosSettingsColors.green)
    }
    val protected = uiState.hasPin || uiState.is2FaEnabled

    IosGroup {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(IosSettingsColors.cellElevated),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = uiState.avatarUrl,
                        contentDescription = "Avatar de ${uiState.userName}",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = IosSettingsColors.secondaryLabel,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiState.userName,
                    color = IosSettingsColors.label,
                    fontFamily = IosFont,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = uiState.userHandle,
                    color = IosSettingsColors.secondaryLabel,
                    fontFamily = IosFont,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IosBadge(presence.first, presence.second)
                    IosBadge(if (protected) "Protegida" else "Sin PIN", if (protected) IosSettingsColors.green else IosSettingsColors.orange)
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = IosSettingsColors.chevron,
                modifier = Modifier.size(18.dp)
            )
        }
        IosDivider(startIndent = 16.dp)
        IosActionRow(
            title = "Editar perfil",
            onClick = onNavigateToProfile,
            color = IosSettingsColors.blue,
            fontWeight = FontWeight.SemiBold
        )
    }
}
