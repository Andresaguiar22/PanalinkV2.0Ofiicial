package com.example.ui.settings.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.settings.ios.IosSettingsColors

@Composable
fun ProfileField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    testTag: String? = null
) {
    val fieldModifier = if (testTag != null) modifier.testTag(testTag) else modifier

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = IosSettingsColors.secondaryLabel) },
        placeholder = if (placeholder != null) { { Text(placeholder, color = Color.Gray) } } else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = IosSettingsColors.green,
            unfocusedBorderColor = Color(0xFF37474F),
            focusedLabelColor = IosSettingsColors.green,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedPlaceholderColor = Color.Gray,
            unfocusedPlaceholderColor = Color.Gray
        ),
        singleLine = singleLine,
        leadingIcon = if (leadingIcon != null) {
            { Icon(leadingIcon, contentDescription = null, tint = IosSettingsColors.secondaryLabel) }
        } else null,
        modifier = fieldModifier,
        shape = RoundedCornerShape(12.dp)
    )
}
