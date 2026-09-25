package com.example.ui.settings.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight

/** Campo de texto con la apariencia de iOS: relleno gris, foco azul del sistema. */
@Composable
fun IosTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontFamily = IosFont) },
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        isError = isError,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = IosSettingsColors.blue,
            unfocusedBorderColor = IosSettingsColors.separator,
            focusedLabelColor = IosSettingsColors.blue,
            unfocusedLabelColor = IosSettingsColors.secondaryLabel,
            focusedTextColor = IosSettingsColors.label,
            unfocusedTextColor = IosSettingsColors.label,
            cursorColor = IosSettingsColors.blue,
            focusedContainerColor = IosSettingsColors.cell,
            unfocusedContainerColor = IosSettingsColors.cell,
            errorBorderColor = IosSettingsColors.red
        ),
        leadingIcon = leadingIcon?.let {
            { Icon(it, contentDescription = null, tint = IosSettingsColors.secondaryLabel) }
        },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}

/** Boton primario estilo iOS: relleno azul solido, esquinas 12dp, texto blanco. */
@Composable
fun IosPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: ImageVector? = null
) {
    val active = enabled && !isLoading
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) IosSettingsColors.blue else IosSettingsColors.cellElevated)
            .clickable(
                enabled = active,
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    color = Color.White,
                    fontFamily = IosFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

