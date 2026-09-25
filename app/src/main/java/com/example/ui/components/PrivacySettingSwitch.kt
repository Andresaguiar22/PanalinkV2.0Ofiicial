package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.settings.ios.IosSettingsColors

@Composable
fun PrivacySettingSwitch(
    title: String,
    description: String,
    isPremium: Boolean,
    hasEntitlement: Boolean,
    isEnabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = IosSettingsColors.label,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                if (isPremium) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "★",
                        color = Color(0xFFFFD700),
                        fontSize = 14.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = IosSettingsColors.secondaryLabel,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
            if (isPremium && !hasEntitlement) {
                Text(
                    text = "Requiere Panalink Premium",
                    color = IosSettingsColors.red,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = { if (hasEntitlement || !isPremium) onCheckedChange(it) },
            enabled = hasEntitlement || !isPremium,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = IosSettingsColors.blue,
                uncheckedThumbColor = IosSettingsColors.secondaryLabel,
                uncheckedTrackColor = IosSettingsColors.cellElevated
            )
        )
    }
}
