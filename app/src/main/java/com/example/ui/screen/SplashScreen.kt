package com.example.ui.screen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.components.AuroraBackground
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    AuroraBackground {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Logo Panalink pequeno y centrado, estilo WhatsApp
            AnimatedPanaWelcomeLogo(logoSize = 44.dp)
        }
    }
}
