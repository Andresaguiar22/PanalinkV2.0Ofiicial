package com.example.ui.screen.onboarding

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.settings.ios.IosSettingsColors
import com.example.ui.settings.ios.IosFont
import com.example.ui.settings.ios.IosPrimaryButton

@Composable
fun PermissionsScreen(
    onNext: () -> Unit
) {
    // Launcher for Notification Permission on Android 13+ (API 33)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // No matter the response, we continue the onboarding flow
        onNext()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IosSettingsColors.groupBackground)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon Placeholder
            Text(
                text = "🔔",
                fontSize = 72.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Text(
                text = "¡Mantente Conectado! 📡",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = IosFont,
                color = IosSettingsColors.label,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Para recibir los mensajes de tus panas al instante y no perderte de ninguna llamada, Panalink necesita enviarte notificaciones.",
                fontSize = 15.sp,
                color = IosSettingsColors.secondaryLabel,
                fontFamily = IosFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            IosPrimaryButton(
                text = "Activar Notificaciones 🔔",
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onNext()
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Skip / Omit button
            TextButton(
                onClick = onNext,
                colors = ButtonDefaults.textButtonColors(contentColor = IosSettingsColors.blue)
            ) {
                Text(
                    text = "Omitir por ahora, configurar luego",
                    fontSize = 14.sp,
                    fontFamily = IosFont,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
