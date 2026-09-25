package com.example.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.settings.ios.IosSettingsColors
import com.example.ui.settings.ios.IosFont
import com.example.ui.settings.ios.IosTextField
import com.example.ui.settings.ios.IosPrimaryButton

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onNavigateToLogin: () -> Unit
) {
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()

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
            // PanaLink Logo and Branding on Auth Screen
            com.example.ui.components.PanaLinkLogo(logoSize = 100.dp)
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Únete a PanaLink 🇻🇪",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = IosFont,
                color = IosSettingsColors.label,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Regístrate de pana",
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
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = "Nombre Completo",
                        leadingIcon = Icons.Default.Person,
                        modifier = Modifier.testTag("register_name_input")
                    )

                    IosTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email",
                        leadingIcon = Icons.Default.Email,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.testTag("register_email_input")
                    )

                    IosTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Contraseña",
                        leadingIcon = Icons.Default.Lock,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.testTag("register_password_input")
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
                                imageVector = Icons.Default.Warning,
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
                        text = "Crear Cuenta",
                        onClick = { viewModel.register(displayName, email, password) },
                        isLoading = uiState is AuthUiState.Loading,
                        modifier = Modifier.testTag("register_submit_button")
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(text = "¿Ya eres un pana? ", color = IosSettingsColors.secondaryLabel, fontFamily = IosFont, fontSize = 15.sp)
                Text(
                    text = "Inicia Sesión",
                    color = IosSettingsColors.blue,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = IosFont,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clickable { onNavigateToLogin() }
                        .testTag("login_link")
                )
            }
        }
    }

}
