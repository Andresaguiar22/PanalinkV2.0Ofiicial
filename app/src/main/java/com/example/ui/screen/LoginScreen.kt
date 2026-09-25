package com.example.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
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
import com.example.ui.components.AuroraBackground
import com.example.ui.components.AuroraButton
import com.example.ui.components.GlassCard
import com.example.util.SecurityManager
import com.example.ui.theme.PanalinkPalette

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

    AuroraBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (audit.score >= 70) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Shield",
                        tint = if (audit.score >= 70) Color(0xFF18E7F5) else Color(0xFFF0D9A6),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Blindaje Shield: ${audit.status}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PanalinkPalette.textPrimary.copy(alpha = 0.9f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                // PanaLink Logo and Branding on Auth Screen
                com.example.ui.components.PanaLinkLogo(logoSize = 100.dp)
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "PanaLink 🇻🇪",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PanalinkPalette.textPrimary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Inicia Sesión",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PanalinkPalette.textPrimary.copy(alpha = 0.8f)
                )
                
                Spacer(modifier = Modifier.height(32.dp))

                // Form Card
                GlassCard {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Email Input
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email", color = Color(0xFFB8C4D6)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF18E7F5),
                                unfocusedBorderColor = Color(0xFF131A22).copy(alpha = 0.5f),
                                focusedLabelColor = Color(0xFF18E7F5),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email", tint = Color(0xFFB8C4D6)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_email_input"),
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )

                        // Password Input
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Contraseña", color = Color(0xFFB8C4D6)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF18E7F5),
                                unfocusedBorderColor = Color(0xFF131A22).copy(alpha = 0.5f),
                                focusedLabelColor = Color(0xFF18E7F5),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Contraseña", tint = Color(0xFFB8C4D6)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_password_input"),
                            shape = RoundedCornerShape(16.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Error Box if any
                        if (uiState is AuthUiState.Error) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFEF4444).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Alerta",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = (uiState as AuthUiState.Error).message,
                                    color = PanalinkPalette.textPrimary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        AuroraButton(
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
                    Text(text = "¿No tienes una cuenta? ", color = PanalinkPalette.textPrimary.copy(alpha = 0.6f), fontSize = 15.sp)
                    Text(
                        text = "Regístrate aquí",
                        color = Color(0xFF18E7F5),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .clickable { onNavigateToRegister() }
                            .testTag("register_link")
                    )
                }
            }
        }
    }

    if (otherDevices.isNotEmpty()) {
        val names = otherDevices.mapNotNull { it.deviceName?.takeIf { n -> n.isNotBlank() } }
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeviceAlert() },
            title = { Text("Tu cuenta ya está activa en otro dispositivo", color = PanalinkPalette.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = if (names.isEmpty()) {
                        "Esta sesión cerrará la anterior en tu otro equipo."
                    } else {
                        "Dispositivo(s): " + names.joinToString(", ") + ". Al continuar, esa sesión se cerrará."
                    },
                    color = PanalinkPalette.textPrimary.copy(alpha = 0.8f),
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissDeviceAlert() }) {
                    Text("Entendido", color = Color(0xFF18E7F5), fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
