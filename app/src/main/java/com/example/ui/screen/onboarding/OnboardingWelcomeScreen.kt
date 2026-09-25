package com.example.ui.screen.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.settings.ios.IosSettingsColors
import com.example.ui.settings.ios.IosFont
import com.example.ui.settings.ios.IosPrimaryButton

@Composable
fun OnboardingWelcomeScreen(
    onNext: () -> Unit
) {
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
            // Elegant Visual element
            Text(
                text = "💬✨",
                fontSize = 64.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "¡Bienvenido a Panalink! 👋",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = IosSettingsColors.label,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(14.dp))
            
            Text(
                text = "Tu nueva plataforma favorita para chatear, realizar llamadas y conectar de verdad con tus panas con la mayor velocidad.",
                fontSize = 15.sp,
                color = IosSettingsColors.secondaryLabel,
                fontFamily = IosFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            IosPrimaryButton(text = "Comenzar Configuración 🚀", onClick = onNext)
        }
    }
}
