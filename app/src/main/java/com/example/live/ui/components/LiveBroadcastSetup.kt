package com.example.live.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.live.ui.LiveCardShape
import com.example.live.ui.LiveNeon
import com.example.live.ui.LiveOnNeon

private val FieldShape = RoundedCornerShape(14.dp)

/**
 * Pantalla de configuración previa al directo.
 *
 * Reemplaza el `Scaffold` de fondo sólido por una composición edge-to-edge: vista previa
 * real de CameraX de fondo (con blur + oscurecido), barra superior flotante, panel de
 * formularios con efecto glassmorphism y CTA en píldora con glow.
 */
@Composable
fun LiveBroadcastSetup(
    hasPermissions: Boolean,
    titleText: String,
    onTitleChange: (String) -> Unit,
    descriptionText: String,
    onDescriptionChange: (String) -> Unit,
    isStarting: Boolean,
    errorMessage: String?,
    cameraPreviewActive: Boolean,
    onBack: () -> Unit,
    onRequestPermissions: () -> Unit,
    onStart: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05070A)),
    ) {
        LiveCameraPreviewBackground(
            active = hasPermissions && cameraPreviewActive,
            modifier = Modifier
                .fillMaxSize()
                .scale(1.08f),
        )

        // Oscurecido pedido por diseño + degradado para que el texto siempre se lea.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f)),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.10f),
                            Color.Black.copy(alpha = 0.55f),
                        ),
                    ),
                ),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            SetupTopBar(onBack = onBack)

            Spacer(modifier = Modifier.weight(1f))

            val panelModifier = Modifier
                .fillMaxWidth(0.86f)
                .widthIn(max = 380.dp)
                .align(Alignment.CenterHorizontally)

            if (hasPermissions) {
                GlassFormPanel(
                    titleText = titleText,
                    onTitleChange = onTitleChange,
                    descriptionText = descriptionText,
                    onDescriptionChange = onDescriptionChange,
                    modifier = panelModifier,
                )
            } else {
                PermissionGlassPanel(modifier = panelModifier)
            }

            Spacer(modifier = Modifier.weight(1f))

            NeonPillButton(
                label = if (hasPermissions) "INICIAR TRANSMISIÓN" else "CONCEDER PERMISOS",
                isBusy = isStarting,
                enabled = !isStarting && (!hasPermissions || titleText.isNotBlank()),
                onClick = if (hasPermissions) onStart else onRequestPermissions,
            )
        }

        // El error va flotando sobre el borde inferior para no descentrar el panel.
        errorMessage?.let { message ->
            Text(
                text = message,
                color = Color(0xFFFF8A80),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 32.dp, bottom = 96.dp),
            )
        }
    }
}

/**
 * Barra superior flotante: sin `TopAppBar`, sólo un `Row` sobre el fondo de cámara.
 *
 * No se aplican `statusBarsPadding()`/`navigationBarsPadding()` a propósito: en esta app
 * la ventana NO es edge-to-edge (`decorFitsSystemWindows` queda en `true`), así que el
 * sistema ya reserva el espacio de las barras y volver a aplicar los insets duplicaría el
 * desplazamiento (misma conclusión documentada al arreglar el feed de reels).
 */
@Composable
private fun SetupTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Regresar",
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "Transmitir en Vivo",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun GlassFormPanel(
    titleText: String,
    onTitleChange: (String) -> Unit,
    descriptionText: String,
    onDescriptionChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(LiveCardShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.14f),
                        Color.White.copy(alpha = 0.06f),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.32f),
                        LiveNeon.copy(alpha = 0.16f),
                    ),
                ),
                shape = LiveCardShape,
            )
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            GlassFieldGroup(
                label = "Título",
                icon = Icons.Outlined.Edit,
                value = titleText,
                onValueChange = onTitleChange,
                placeholder = "Añade un título a tu directo...",
                fieldHeight = 52.dp,
            )
            GlassFieldGroup(
                label = "Descripción (Opcional)",
                icon = Icons.Outlined.Description,
                value = descriptionText,
                onValueChange = onDescriptionChange,
                placeholder = "Cuéntanos de qué trata tu transmisión...",
                fieldHeight = 140.dp,
            )
        }
    }
}

@Composable
private fun PermissionGlassPanel(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(LiveCardShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.14f),
                        Color.White.copy(alpha = 0.06f),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.32f),
                        LiveNeon.copy(alpha = 0.16f),
                    ),
                ),
                shape = LiveCardShape,
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Videocam,
                contentDescription = null,
                tint = LiveNeon,
                modifier = Modifier.size(40.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Necesitamos tu cámara y micrófono",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Concede los permisos para ver tu vista previa y salir en directo.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GlassFieldGroup(
    label: String,
    icon: ImageVector,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    fieldHeight: Dp,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        GlassTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            fieldHeight = fieldHeight,
        )
    }
}

/** Campo transparente con borde propio: sin `OutlinedTextField` ni líneas inferiores. */
@Composable
private fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    fieldHeight: Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(fieldHeight)
            .clip(FieldShape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.20f), FieldShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = Color.White.copy(alpha = 0.42f),
                fontSize = 15.sp,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
            cursorBrush = SolidColor(LiveNeon),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun NeonPillButton(
    label: String,
    isBusy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Halo desenfocado: el "glow" suave del CTA.
        Box(
            modifier = Modifier
                .width(190.dp)
                .height(46.dp)
                .blur(26.dp)
                .background(
                    LiveNeon.copy(alpha = if (enabled) 0.65f else 0.22f),
                    CircleShape,
                ),
        )
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = LiveNeon,
                contentColor = LiveOnNeon,
                disabledContainerColor = LiveNeon.copy(alpha = 0.35f),
                disabledContentColor = LiveOnNeon.copy(alpha = 0.7f),
            ),
            contentPadding = PaddingValues(horizontal = 30.dp),
            modifier = Modifier
                .height(50.dp)
                .shadow(14.dp, CircleShape, ambientColor = LiveNeon, spotColor = LiveNeon),
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    color = LiveOnNeon,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(
                    text = label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}
