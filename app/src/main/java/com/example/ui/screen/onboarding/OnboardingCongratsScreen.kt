package com.example.ui.screen.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.viewmodel.onboarding.OnboardingViewModel
import com.example.ui.settings.ios.IosSettingsColors
import com.example.ui.settings.ios.IosFont
import com.example.ui.settings.ios.IosPrimaryButton

data class PresetAvatar(val emoji: String, val brush: Brush)

@Composable
fun OnboardingCongratsScreen(
    viewModel: OnboardingViewModel,
    onNext: () -> Unit
) {
    val displayName by viewModel.displayName.collectAsState()
    val avatarUrl by viewModel.avatarUrl.collectAsState()

    // Grab preset background if avatarUrl is preset
    val presets = remember {
        listOf(
            PresetAvatar("🔥", Brush.linearGradient(listOf(IosSettingsColors.orange, IosSettingsColors.orange))),
            PresetAvatar("⚡", Brush.linearGradient(listOf(IosSettingsColors.blue, IosSettingsColors.blue))),
            PresetAvatar("👾", Brush.linearGradient(listOf(IosSettingsColors.purple, IosSettingsColors.pink))),
            PresetAvatar("🚀", Brush.linearGradient(listOf(IosSettingsColors.green, IosSettingsColors.green)))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IosSettingsColors.groupBackground)
            .padding(24.dp)
    ) {
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Party Popper Celebration Emoji
            Text(
                text = "🎉🥳✨",
                fontSize = 54.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "¡Felicidades, ya eres un Pana! 🇻🇪",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = IosFont,
                color = IosSettingsColors.label,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "¡Gracias por elegirnos! Panalink ha sido creada con mucho cariño para mantenerte conectado con alta fidelidad, rapidez y absoluta confianza.",
                fontSize = 15.sp,
                color = IosSettingsColors.secondaryLabel,
                fontFamily = IosFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Profile Preview Card
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .border(1.dp, IosSettingsColors.separator, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = IosSettingsColors.cell)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar view
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(IosSettingsColors.cellElevated)
                            .border(2.5.dp, IosSettingsColors.blue, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!avatarUrl.isNullOrEmpty()) {
                            if (avatarUrl!!.startsWith("preset:")) {
                                val symbol = avatarUrl!!.removePrefix("preset:")
                                val preset = presets.firstOrNull { it.emoji == symbol }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(preset?.brush ?: presets[0].brush),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = symbol, fontSize = 42.sp)
                                }
                            } else {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = "Avatar de perfil",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(IosSettingsColors.cellElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = displayName.firstOrNull()?.uppercase()?.toString() ?: "P",
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IosSettingsColors.label
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = displayName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IosFont,
                        color = IosSettingsColors.label
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Panalink Oficial Member ⚡",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = IosFont,
                        color = IosSettingsColors.blue
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    val currentProfile = com.example.data.supabase.SupabaseClient.currentProfile
                    val pin = currentProfile?.pin ?: ""
                    if (pin.isNotEmpty()) {
                        Text(
                            text = "Tu Código PIN Único",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = IosSettingsColors.secondaryLabel
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Box(
                            modifier = Modifier
                                .background(IosSettingsColors.cellElevated, RoundedCornerShape(12.dp))
                                .border(1.dp, IosSettingsColors.separator, RoundedCornerShape(12.dp))
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = pin,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = IosFont,
                                color = IosSettingsColors.label,
                                letterSpacing = 8.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = "Tu Código QR de Pana",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = IosSettingsColors.secondaryLabel
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(IosSettingsColors.label)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            com.example.ui.components.QrCodeView(
                                pin = pin,
                                payload = if (pin.isNotEmpty()) "panalink:pin:$pin" else "panalink:contact:${currentProfile?.id}",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Esta será tu identidad en Panalink.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = IosFont,
                            color = IosSettingsColors.tertiaryLabel,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            IosPrimaryButton(
                text = "Continuar y Entrar",
                onClick = onNext,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                modifier = Modifier.testTag("onboarding_continue_button")
            )
        }
    }
}
