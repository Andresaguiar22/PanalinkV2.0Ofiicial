package com.example.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel
import com.example.util.SecurityManager
import com.example.ui.settings.ios.IosSettingsColors
import com.example.ui.settings.ios.IosFont
import com.example.ui.settings.ios.IosTextField
import com.example.ui.settings.ios.IosPrimaryButton

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onNavigateToRegister: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val otherDevices by viewModel.otherDevices.collectAsState()
    val context = LocalContext.current
    val audit = remember { SecurityManager.getSecurityAudit(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IosSettingsColors.groupBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Shield Audit Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(IosSettingsColors.cell, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = if (audit.score >= 70) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                    contentDescription = "Shield",
                    tint = if (audit.score >= 70) IosSettingsColors.green else IosSettingsColors.orange,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Blindaje Shield: ${audit.status}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = IosFont,
                    color = IosSettingsColors.secondaryLabel
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // PanaLink Logo and Branding on Auth Screen
            com.example.ui.components.PanaLinkLogo(logoSize = 100.dp)
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "PanaLink 🇻🇪",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = IosFont,
                color = IosSettingsColors.label,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Inicia Sesión",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = IosFont,
                color = IosSettingsColors.secondaryLabel
            )

            Spacer(modifier = Modifier.height(32.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = IosSettingsColors.cell,
                border = BorderStroke(1.dp, IosSettingsColors.separator)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IosTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email",
                        leadingIcon = Icons.Rounded.Email,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.testTag("login_email_input")
                    )

                    IosTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Contraseña",
                        leadingIcon = Icons.Rounded.Lock,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.testTag("login_password_input")
                    )

                    // Error Box if any
                    if (uiState is AuthUiState.Error) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(IosSettingsColors.red.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = "Alerta",
                                tint = IosSettingsColors.red,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = (uiState as AuthUiState.Error).message,
                                color = IosSettingsColors.red,
                                fontFamily = IosFont,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    IosPrimaryButton(
                        text = "Entrar de Pana",
                        onClick = { viewModel.login(email, password, com.example.util.DeviceInfo.getDeviceId(context), com.example.util.DeviceInfo.getDeviceName(context)) },
                        isLoading = uiState is AuthUiState.Loading,
                        modifier = Modifier.testTag("login_submit_button")
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(text = "¿No tienes una cuenta? ", color = IosSettingsColors.secondaryLabel, fontFamily = IosFont, fontSize = 15.sp)
                Text(
                    text = "Regístrate aquí",
                    color = IosSettingsColors.blue,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = IosFont,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clickable { onNavigateToRegister() }
                        .testTag("register_link")
                )
            }
        }
    }


    if (otherDevices.isNotEmpty()) {
        val names = otherDevices.mapNotNull { it.deviceName?.takeIf { n -> n.isNotBlank() } }
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeviceAlert() },
            title = { Text("Tu cuenta ya está activa en otro dispositivo", color = IosSettingsColors.label, fontFamily = IosFont, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    text = if (names.isEmpty()) {
                        "Por seguridad, se cerró la sesión que tenías abierta en tu otro dispositivo."
                    } else {
                        "Por seguridad, se cerró la sesión en: " + names.joinToString(", ") + "."
                    },
                    color = IosSettingsColors.secondaryLabel,
                    fontFamily = IosFont,
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissDeviceAlert() }) {
                    Text("Entendido", color = IosSettingsColors.blue, fontFamily = IosFont, fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = IosSettingsColors.cellElevated
        )
    }
}
