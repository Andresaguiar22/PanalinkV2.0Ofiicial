package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.settings.ios.IosSettingsColors

// Colores del tema Premium Dark (estilo iOS)
internal val PaniOSIosBlack = Color(0xFF000000)
internal val PaniOSCardBackground: Color get() = IosSettingsColors.cell
internal val PaniOSCardBorder = Color(0x0DFFFFFF) // Blanco al 5%
internal val PaniOSTextGray = Color(0xFF8E8E93)

// Colores Neón Vibrantes
internal val PaniOSCyanAccent: Color get() = IosSettingsColors.blue
internal val PaniOSPinkAccent = Color(0xFFFF007F)
internal val PaniOSPurpleAccent = Color(0xFF9D4EDD)

/**
 * Pantalla "Estudio de Reels" estilo iOS: fondo negro, título serif grande,
 * tarjetas glass (gris oscuro + borde blanco 5%) y acentos neón por acción.
 * Es el paso inicial del editor de reels (picker → cámara / galería / URL).
 */
@Composable
fun PaniOSReelsStudioPicker(
    onRecordVideo: () -> Unit,
    onPickMedia: () -> Unit,
    onImportUrl: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PaniOSIosBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Espaciador superior
            Spacer(modifier = Modifier.height(60.dp))

            // Título Principal
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Estudio de Reels",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = "🎬🇻🇪", fontSize = 24.sp)
            }

            // Subtítulo
            Text(
                text = "Produce videos impactantes para toda la comunidad de Panalink",
                color = PaniOSTextGray,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Tarjeta 1: Grabar
            PaniOSStudioActionCard(
                icon = Icons.Outlined.Videocam,
                iconTint = PaniOSCyanAccent,
                title = "Grabar en Estudio",
                emoji = "🎙️",
                description = "Abre la cámara de producción con conteo de tiempo",
                onClick = onRecordVideo,
                testTag = "reel_studio_camera_button"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Tarjeta 2: Subir
            PaniOSStudioActionCard(
                icon = Icons.Outlined.Image,
                iconTint = PaniOSPinkAccent,
                title = "Subir Video o Imagen",
                emoji = "🎞️",
                description = "Las imágenes se animan automáticamente con efecto Ken Burns",
                onClick = onPickMedia,
                testTag = "reel_studio_gallery_button"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Tarjeta 3: Importar URL
            PaniOSStudioActionCard(
                icon = Icons.Outlined.Public,
                iconTint = PaniOSPurpleAccent,
                title = "Pegar URL de Video",
                emoji = "🌐",
                description = "Importa TikTok, Instagram, YouTube y más sin marca de agua",
                onClick = onImportUrl,
                testTag = "reel_studio_import_url_button"
            )

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(40.dp))

            // Botón de Regreso
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Regresar",
                    tint = PaniOSTextGray,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Regresar al Feed",
                    color = PaniOSTextGray,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PaniOSStudioActionCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    emoji: String,
    description: String,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PaniOSCardBackground, RoundedCornerShape(24.dp))
            .border(1.dp, PaniOSCardBorder, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .testTag(testTag)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icono Principal
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier
                .size(42.dp)
                .padding(bottom = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Título + Emoji
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = emoji, fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Descripción
        Text(
            text = description,
            color = PaniOSTextGray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}