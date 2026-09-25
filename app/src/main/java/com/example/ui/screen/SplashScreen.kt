package com.example.ui.screen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.settings.ios.IosSettingsColors
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IosSettingsColors.groupBackground),
        contentAlignment = Alignment.Center
    ) {
        // Logo Panalink pequeno y centrado, estilo WhatsApp
        AnimatedPanaWelcomeLogo(logoSize = 44.dp)
    }
}
