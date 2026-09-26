package com.example.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.settings.ios.IosSettingsColors
import com.example.ui.settings.ios.IosFont
import com.example.ui.settings.ios.IosPrimaryButton

@Composable
fun EmailVerificationScreen(
    viewModel: AuthViewModel,
    email: String,
    onBackToLogin: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showResendSuccess by remember { mutableStateOf(false) }

    val isAlreadyAuthenticated = uiState is AuthUiState.Authenticated ||
            uiState is AuthUiState.NeedsProfileSetup ||
            uiState is AuthUiState.AuthenticatedReady ||
            uiState is AuthUiState.AuthenticatedIncomplete

    // ON_RESUME listener when returning from email app / browser
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isAlreadyAuthenticated) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (!isAlreadyAuthenticated) {
                    viewModel.checkEmailVerificationStatus(silent = true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Polling every 4 seconds while screen is active
    LaunchedEffect(isAlreadyAuthenticated) {
        if (!isAlreadyAuthenticated) {
            while (true) {
                kotlinx.coroutines.delay(4000)
                viewModel.checkEmailVerificationStatus(silent = true)
            }
        }
    }

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
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = IosSettingsColors.blue.copy(alpha = 0.12f),
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MarkEmailRead, contentDescription = null, tint = IosSettingsColors.blue, modifier = Modifier.size(48.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Verifica tu correo",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = IosFont,
                color = IosSettingsColors.label,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Hemos enviado un correo mágico a tu bandeja. Verifícalo antes de iniciar sesión en Panalink.",
                fontSize = 15.sp,
                color = IosSettingsColors.secondaryLabel,
                fontFamily = IosFont,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = email,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = IosFont,
                color = IosSettingsColors.blue,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = IosSettingsColors.cell,
                border = androidx.compose.foundation.BorderStroke(1.dp, IosSettingsColors.separator)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IosPrimaryButton(
                        text = "Ya verifiqué",
                        onClick = {
                            if (!isAlreadyAuthenticated) {
                                viewModel.checkVerification()
                            }
                        },
                        icon = Icons.Rounded.Refresh,
                        isLoading = uiState is AuthUiState.Loading,
                        modifier = Modifier.testTag("verify_confirm_button")
                    )

                    Surface(
                        onClick = {
                            viewModel.resendVerificationEmail()
                            showResendSuccess = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("verify_resend_button"),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(1.dp, IosSettingsColors.blue.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Rounded.Email, contentDescription = null, tint = IosSettingsColors.blue, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reenviar correo", color = IosSettingsColors.blue, fontFamily = IosFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        }
                    }

                    if (showResendSuccess) {
                        Text(
                            text = "¡Correo reenviado con éxito! 📨",
                            color = IosSettingsColors.green,
                            fontFamily = IosFont,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (uiState is AuthUiState.Error) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(IosSettingsColors.red.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Icon(Icons.Rounded.Warning, contentDescription = null, tint = IosSettingsColors.red)
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
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            TextButton(
                onClick = onBackToLogin,
                colors = ButtonDefaults.textButtonColors(contentColor = IosSettingsColors.secondaryLabel)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Volver al Inicio", fontSize = 14.sp, fontFamily = IosFont, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}