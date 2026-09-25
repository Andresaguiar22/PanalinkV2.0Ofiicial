package com.example.ui.screen

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.ui.settings.ios.IosSettingsColors
import com.example.ui.settings.ios.IosFont
import com.example.ui.settings.ios.IosPrimaryButton

@Composable
fun AnimatedPanaWelcomeLogo(
    modifier: Modifier = Modifier,
    logoSize: Dp = 120.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WelcomeLogoAnim")

    // Very soft pulsation of scale (between 97% and 103%)
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LogoPulseScale"
    )

    // Slow rotation of the outer frame (360 degrees every 20 seconds)
    val frameRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "LogoFrameRotation"
    )

    // Gentle blinking of outer lights (between 0.35f and 1f opacity)
    val lightBlink by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LogoLightBlink"
    )

    Box(
        modifier = modifier
            .size(logoSize + 32.dp) // Extra padding for the rotating, glowing frame
            .scale(pulseScale),
        contentAlignment = Alignment.Center
    ) {
        // Rotating background frame with glowing neon border lights
        Canvas(
            modifier = Modifier
                .size(logoSize + 20.dp)
                .graphicsLayer {
                    rotationZ = frameRotation
                }
        ) {
            val w = size.width
            val h = size.height
            val center = androidx.compose.ui.geometry.Offset(w / 2f, h / 2f)
            val outerRadius = (w / 2f) - 6.dp.toPx()

            // Draw extremely soft, blurry neon green shadow glow behind the frame
            drawIntoCanvas { canvas ->
                val greenPaint = Paint().asFrameworkPaint().apply {
                    color = android.graphics.Color.parseColor("#00FF85")
                    alpha = (lightBlink * 110).toInt().coerceIn(0, 255)
                    style = android.graphics.Paint.Style.FILL
                    maskFilter = BlurMaskFilter(14.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                }
                canvas.nativeCanvas.drawCircle(center.x, center.y, outerRadius, greenPaint)
            }

            // Draw a dashed neon glowing ring with light dots (representing the glowing border lights)
            val strokeWidth = 3.dp.toPx()
            val dashLength = 12.dp.toPx()
            val gapLength = 8.dp.toPx()

            drawCircle(
                color = IosSettingsColors.green.copy(alpha = lightBlink * 0.85f),
                radius = outerRadius,
                style = Stroke(
                    width = strokeWidth,
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(dashLength, gapLength),
                        phase = 0f
                    ),
                    cap = StrokeCap.Round
                )
            )

            // Draw a secondary subtle, out-of-phase gold inner rotating border
            drawCircle(
                color = Color(0xFFFFD700).copy(alpha = (1.2f - lightBlink).coerceIn(0.1f, 0.9f) * 0.6f),
                radius = outerRadius - 4.dp.toPx(),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(6.dp.toPx(), 12.dp.toPx()),
                        phase = 20f
                    ),
                    cap = StrokeCap.Round
                )
            )
        }

        // The actual static logo in the center
        com.example.ui.components.PanaLinkLogo(
            logoSize = logoSize,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
fun WelcomeScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IosSettingsColors.groupBackground)
            .padding(24.dp)
            .navigationBarsPadding()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Branding Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedPanaWelcomeLogo(logoSize = 120.dp)

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "¡Bienvenido a\nPanaLink! 🇻🇪",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = IosFont,
                    color = IosSettingsColors.label,
                    textAlign = TextAlign.Center,
                    lineHeight = 40.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tu app de mensajería bien criolla.",
                    fontSize = 17.sp,
                    color = IosSettingsColors.secondaryLabel,
                    fontFamily = IosFont,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }

            // Copy Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Mensajes rápidos, estados que desaparecen y privacidad real.",
                    fontSize = 15.sp,
                    color = IosSettingsColors.secondaryLabel,
                    fontFamily = IosFont,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Conéctate de pana con tus panas en todo el país sin límites.",
                    fontSize = 12.sp,
                    color = IosSettingsColors.tertiaryLabel,
                    fontFamily = IosFont,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IosPrimaryButton(
                    text = "Registrarse",
                    onClick = onNavigateToRegister,
                    modifier = Modifier.testTag("welcome_register_button")
                )

                // Secondary button: outlined, iOS style
                Surface(
                    onClick = onNavigateToLogin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("welcome_login_button"),
                    shape = RoundedCornerShape(12.dp),
                    color = IosSettingsColors.cell,
                    border = androidx.compose.foundation.BorderStroke(1.dp, IosSettingsColors.separator)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Ya tengo cuenta",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = IosFont,
                            color = IosSettingsColors.blue
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}