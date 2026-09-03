package com.example.ui.settings.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.feature.settings.model.DashboardAction
import com.example.ui.settings.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { err ->
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            viewModel.dispatch(DashboardAction.ClearError)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Centro de Control", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.dispatch(DashboardAction.RefreshDashboard) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color(0xFF121B22),
                    scrolledContainerColor = Color(0xFF1E2B33)
                ),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = Color(0xFF121B22)
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF25D366))
            }
        } else {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(400)) + slideInVertically(animationSpec = tween(400))
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = 32.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Hero Card Superior Dinámico
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2B33)),
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2A3942)),
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
                                            tint = Color.White,
                                            modifier = Modifier.size(44.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = uiState.userName,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = uiState.userHandle,
                                    color = Color(0xFF90A4AE),
                                    fontSize = 13.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Badges Row: Presencia + Seguridad
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val (badgeBg, badgeText, badgeColor) = when (uiState.presenceStatus) {
                                        "busy" -> Triple(Color(0xFFFF3D00).copy(alpha = 0.2f), "🔴 Ocupado", Color(0xFFFF3D00))
                                        "invisible" -> Triple(Color(0xFF00E5FF).copy(alpha = 0.2f), "⚪ Invisible", Color(0xFF00E5FF))
                                        else -> Triple(Color(0xFF25D366).copy(alpha = 0.2f), "🟢 Disponible", Color(0xFF25D366))
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = badgeBg
                                    ) {
                                        Text(
                                            text = badgeText,
                                            color = badgeColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }

                                    val isProtected = uiState.hasPin || uiState.is2FaEnabled
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (isProtected) Color(0xFF4CAF50).copy(alpha = 0.2f) else Color(0xFFFFC107).copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = if (isProtected) "🛡️ Protegida" else "🔒 Básica",
                                            color = if (isProtected) Color(0xFF4CAF50) else Color(0xFFFFC107),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Contextual Quick Info
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    QuickInfoItem(
                                        icon = Icons.Default.Smartphone,
                                        label = "${uiState.activeDevicesCount} Dispositivo(s)",
                                        value = uiState.connectionStatus
                                    )
                                    Box(modifier = Modifier.height(24.dp).width(1.dp).background(Color(0xFF37474F)))
                                    QuickInfoItem(
                                        icon = Icons.Default.Storage,
                                        label = "Almacenamiento",
                                        value = uiState.storageUsedSummary
                                    )
                                    Box(modifier = Modifier.height(24.dp).width(1.dp).background(Color(0xFF37474F)))
                                    QuickInfoItem(
                                        icon = Icons.Default.Sync,
                                        label = "Sincronización",
                                        value = uiState.lastSynchronization
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = onNavigateToProfile,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(0.7f)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF121B22), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Editar Perfil", color = Color(0xFF121B22), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    // 2. Tarjetas Inteligentes (Smart Cards)
                    item {
                        Text(
                            text = "Módulos Inteligentes",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    item {
                        SmartDomainCard(
                            title = "Seguridad & Acceso",
                            subtitle = uiState.securitySummary,
                            statusBadge = if (uiState.hasPin && uiState.is2FaEnabled) "🛡️ Protección Máxima" else if (uiState.hasPin) "🔐 PIN Activo" else "⚠️ Sin PIN",
                            badgeColor = if (uiState.hasPin) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            icon = Icons.Default.Security,
                            iconColor = Color(0xFFF44336),
                            onClick = onNavigateToSecurity
                        )
                    }

                    item {
                        SmartDomainCard(
                            title = "Centro de Actividad & Sistema",
                            subtitle = uiState.activitySummary,
                            statusBadge = "📊 ${uiState.messagesCount} Mensajes",
                            badgeColor = Color(0xFF03A9F4),
                            icon = Icons.Default.Restore,
                            iconColor = Color(0xFF00BCD4),
                            onClick = onNavigateToActivity
                        )
                    }

                    item {
                        SmartDomainCard(
                            title = "Centro de Presencia",
                            subtitle = uiState.presenceSummary,
                            statusBadge = uiState.privacySummary,
                            badgeColor = Color(0xFF25D366),
                            icon = Icons.Default.AccountCircle,
                            iconColor = Color(0xFF4CAF50),
                            onClick = onNavigateToPresence
                        )
                    }

                    // 3. Menú Completo de Configuración (tarjetas individuales premium)
                    item {
                        Text(
                            text = "Ajustes Generales",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    item { IndividualSettingCard("Perfil", uiState.profileSummary, Icons.Default.Person, Color(0xFF2196F3), onNavigateToProfile) }
                    item { IndividualSettingCard("Presencia", uiState.presenceSummary, Icons.Default.AccountCircle, Color(0xFF4CAF50), onNavigateToPresence) }
                    item { IndividualSettingCard("Privacidad", uiState.privacySummary, Icons.Default.Lock, Color(0xFF9C27B0), onNavigateToPrivacy) }
                    item { IndividualSettingCard("Seguridad", uiState.securitySummary, Icons.Default.Security, Color(0xFFF44336), onNavigateToSecurity) }
                    item { IndividualSettingCard("Chats", uiState.chatsSummary, Icons.Default.Chat, Color(0xFF03A9F4), onNavigateToChats) }
                    item { IndividualSettingCard("Notificaciones", uiState.notificationsSummary, Icons.Default.Notifications, Color(0xFFFFC107), onNavigateToNotifications) }
                    item { IndividualSettingCard("Personalización", uiState.customizationSummary, Icons.Default.ColorLens, Color(0xFFE91E63), onNavigateToCustomization) }
                    item { IndividualSettingCard("Almacenamiento", uiState.storageSummary, Icons.Default.Storage, Color(0xFFFF9800), onNavigateToStorage) }
                    item { IndividualSettingCard("Centro de Actividad", uiState.activitySummary, Icons.Default.Restore, Color(0xFF00BCD4), onNavigateToActivity) }
                    item { IndividualSettingCard("Información", "Versión ${uiState.appVersion} • Ayuda y Soporte", Icons.Default.Info, Color(0xFF9E9E9E), onNavigateToAbout) }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { IndividualSettingCard("Cerrar sesión", "Desconectarse de la cuenta actual", Icons.Default.ExitToApp, Color(0xFFFF5722)) { showLogoutDialog = true } }
                    item { IndividualSettingCard("Eliminar cuenta", "Borrar permanentemente todos tus datos", Icons.Default.DeleteForever, Color(0xFFE53935)) { showDeleteAccountDialog = true } }
                }
            }
        }
    }

    if (showLogoutDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Cerrar sesión", color = Color.White) },
            text = { Text("¿Estás seguro de que quieres cerrar la sesión?", color = Color.White) },
            containerColor = Color(0xFF1E2B33),
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout(onComplete = onLogout)
                    }
                ) {
                    Text("Cerrar sesión", color = Color(0xFFFF5722))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancelar", color = Color.White)
                }
            }
        )
    }

    if (showDeleteAccountDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Eliminar cuenta", color = Color.White) },
            text = { Text("Esta acción es irreversible y borrará todos tus datos. ¿Estás seguro?", color = Color.White) },
            containerColor = Color(0xFF1E2B33),
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showDeleteAccountDialog = false
                        viewModel.deleteAccount(onComplete = onDeleteAccount)
                    }
                ) {
                    Text("Eliminar", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancelar", color = Color.White)
                }
            }
        )
    }
}

@Composable
fun QuickInfoItem(icon: ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color(0xFF90A4AE), modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text(label, color = Color(0xFF90A4AE), fontSize = 10.sp)
    }
}

@Composable
fun IndividualSettingCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2B33))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(description, color = Color(0xFF90A4AE), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF546E7A),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SmartDomainCard(
    title: String,
    subtitle: String,
    statusBadge: String,
    badgeColor: Color,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2B33))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Text(subtitle, color = Color(0xFF90A4AE), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (statusBadge.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = badgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = statusBadge,
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF90A4AE),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
