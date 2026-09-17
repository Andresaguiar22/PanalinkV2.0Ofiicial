package com.example.live.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Verde neon de la marca Panalink. */
val PanalinkNeonGreen = Color(0xFF00E676)

/** Menta usada para los bordes ultra finos del vidrio. */
val PanalinkMint = Color(0xFF7CFFCB)

private val GlassPanelShape = RoundedCornerShape(24.dp)

/**
 * Barra superior flotante: boton de retroceso + titulo, sin TopAppBar generico.
 * Respeta el inset de la status bar para no quedar bajo el reloj/notch.
 */
@Composable
fun LivePreliveTopBar(
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Regresar",
                tint = Color.White,
            )
        }
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp,
        )
    }
}

/**
 * Panel con efecto Glassmorphism: fondo translucido, esquinas de 24dp y borde
 * ultra fino blanco/menta. El desenfoque real del fondo lo aporta la preview de
 * camara difuminada que queda por debajo.
 */
@Composable
fun LiveGlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glassFill = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.13f),
            Color.White.copy(alpha = 0.05f),
        )
    )
    val glassBorder = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = 0.10f),
            PanalinkMint.copy(alpha = 0.10f),
            Color.White.copy(alpha = 0.04f),
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 24.dp,
                shape = GlassPanelShape,
                ambientColor = Color.Black.copy(alpha = 0.45f),
                spotColor = Color.Black.copy(alpha = 0.55f),
            )
            .clip(GlassPanelShape)
            .background(glassFill, GlassPanelShape)
            .border(0.6.dp, glassBorder, GlassPanelShape)
            .padding(horizontal = 20.dp, vertical = 22.dp),
    ) {
        Column(content = content)
    }
}

/**
 * Campo de texto transparente para el panel de vidrio: sin fondo, sin bordes ni
 * linea inferior; solo texto blanco y un placeholder tenue.
 */
@Composable
fun LiveGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minHeight: Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 15.sp,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = LocalTextStyle.current.merge(
                TextStyle(
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            ),
            cursorBrush = SolidColor(PanalinkNeonGreen),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Separador tenue entre los campos del panel (no es un borde de campo). */
@Composable
fun LiveGlassDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.08f))
    )
}

/**
 * Boton de accion flotante en forma de pildora con el verde neon de la marca y
 * un halo (glow) suave. Flota sobre el borde inferior respetando el inset de la
 * barra de navegacion.
 */
@Composable
fun LiveStartBroadcastButton(
    isStarting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = CircleShape,
                    ambientColor = PanalinkNeonGreen.copy(alpha = 0.85f),
                    spotColor = PanalinkNeonGreen.copy(alpha = 0.95f),
                )
                .clip(CircleShape)
                .background(
                    if (enabled) {
                        Brush.horizontalGradient(
                            listOf(PanalinkNeonGreen, Color(0xFF00B865))
                        )
                    } else {
                        Brush.horizontalGradient(
                            listOf(
                                PanalinkNeonGreen.copy(alpha = 0.35f),
                                Color(0xFF00B865).copy(alpha = 0.35f),
                            )
                        )
                    }
                )
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (isStarting) {
                CircularProgressIndicator(
                    color = Color(0xFF04231A),
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Text(
                    text = "INICIAR TRANSMISIÓN EN VIVO",
                    color = Color(0xFF04231A),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 0.6.sp,
                )
            }
        }
    }
}