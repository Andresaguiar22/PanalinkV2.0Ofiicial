package com.example.ui.settings.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.ui.settings.ios.IosBottomSpacer
import com.example.ui.settings.ios.IosDivider
import com.example.ui.settings.ios.IosFont
import com.example.ui.settings.ios.IosGroup
import com.example.ui.settings.ios.IosIconBadge
import com.example.ui.settings.ios.IosListPadding
import com.example.ui.settings.ios.IosSectionHeader
import com.example.ui.settings.ios.IosSettingsColors
import com.example.ui.settings.ios.IosSettingsScaffold
import com.example.update.UpdateStatus
import com.example.update.UpdateViewModel

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val updateViewModel: UpdateViewModel = viewModel()
    val installedVersion = updateViewModel.getInstalledVersionName()
    val updateStatus by updateViewModel.updateStatus.collectAsState()
    val remoteVersion by updateViewModel.remoteVersionName.collectAsState()

    var showDialog by remember { mutableStateOf(true) }
    if (showDialog) {
        com.example.update.UpdateDialog(viewModel = updateViewModel, onDismiss = { showDialog = false })
    }
    LaunchedEffect(updateStatus) {
        if (updateStatus == UpdateStatus.UPDATE_AVAILABLE || updateStatus == UpdateStatus.MANDATORY_UPDATE) {
            showDialog = true
        }
    }

    IosSettingsScaffold(title = "Información", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = IosListPadding,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(IosSettingsColors.cell),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = "PanaLink Logo",
                            modifier = Modifier.size(76.dp)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("PanaLink", color = IosSettingsColors.label, fontFamily = IosFont, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("v$installedVersion", color = IosSettingsColors.secondaryLabel, fontFamily = IosFont, fontSize = 14.sp)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "PanaLink es una plataforma de comunicación creada para mantener cerca a familiares, amigos y comunidades. Nuestra misión es ofrecer una experiencia rápida, segura y confiable para conversar, compartir momentos y mantenerse siempre conectado.",
                        color = IosSettingsColors.secondaryLabel,
                        fontFamily = IosFont,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }

            item { IosSectionHeader("Actualizaciones de software") }
            item {
                IosGroup {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = when (updateStatus) {
                                UpdateStatus.CHECKING -> "Buscando actualizaciones..."
                                UpdateStatus.UPDATE_AVAILABLE -> "Nueva versión v$remoteVersion disponible."
                                UpdateStatus.MANDATORY_UPDATE -> "Actualización obligatoria v$remoteVersion disponible."
                                UpdateStatus.UP_TO_DATE -> "Tu PanaLink está actualizado (v$installedVersion)."
                                UpdateStatus.ERROR -> "Error al buscar actualizaciones. Verifica tu conexión."
                                else -> "Verifica si hay una nueva versión disponible."
                            },
                            color = when (updateStatus) {
                                UpdateStatus.UPDATE_AVAILABLE, UpdateStatus.MANDATORY_UPDATE -> IosSettingsColors.green
                                UpdateStatus.ERROR -> IosSettingsColors.red
                                else -> IosSettingsColors.secondaryLabel
                            },
                            fontFamily = IosFont,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { updateViewModel.checkForUpdates(force = true) },
                            enabled = updateStatus != UpdateStatus.CHECKING,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IosSettingsColors.blue,
                                contentColor = androidx.compose.ui.graphics.Color.White
                            ),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Text(
                                text = if (updateStatus == UpdateStatus.CHECKING) "Verificando..." else "Buscar actualizaciones",
                                fontFamily = IosFont,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            item { IosSectionHeader("Acerca de la plataforma") }
            item {
                IosGroup {
                    AboutFeatureRow(Icons.Default.Security, "Plataforma y compatibilidad", "Mensajería en tiempo real optimizada para distintos tipos de conexión.")
                    IosDivider()
                    AboutFeatureRow(Icons.Default.Sync, "Sincronización en la nube", "Rápida, segura y confiable.")
                    IosDivider()
                    AboutFeatureRow(Icons.Default.Description, "Multimedia integrada", "Fotos, vídeos, documentos, notas de voz y llamadas.")
                    IosDivider()
                    AboutFeatureRow(Icons.AutoMirrored.Filled.HelpOutline, "Soporte técnico", "Comunícate con el equipo desde la sección de ayuda.")
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Hecho con el ❤️ para la comunidad",
                    color = IosSettingsColors.secondaryLabel,
                    fontFamily = IosFont,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Trabajamos continuamente para mejorar el rendimiento y ofrecer una experiencia estable, segura y fácil de usar para todos.",
                    color = IosSettingsColors.tertiaryLabel,
                    fontFamily = IosFont,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.92f)
                )
            }

            item { IosBottomSpacer() }
        }
    }
}

@Composable
private fun AboutFeatureRow(icon: ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IosIconBadge(icon, IosSettingsColors.gray)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = IosSettingsColors.label, fontFamily = IosFont, fontSize = 16.sp)
            Spacer(Modifier.height(2.dp))
            Text(description, color = IosSettingsColors.secondaryLabel, fontFamily = IosFont, fontSize = 13.sp)
        }
    }
}
