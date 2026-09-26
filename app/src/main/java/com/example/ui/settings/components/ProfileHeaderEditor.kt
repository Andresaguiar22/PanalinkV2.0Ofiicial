package com.example.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.settings.ios.IosSettingsColors

@Composable
fun ProfileHeaderEditor(
    avatarUrl: String,
    coverUrl: String,
    displayName: String,
    statusText: String,
    onPickAvatar: () -> Unit,
    onPickCover: () -> Unit,
    isUploadingAvatar: Boolean,
    isUploadingCover: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = IosSettingsColors.cell
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Cover Photo Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(IosSettingsColors.cellElevated)
            ) {
                if (coverUrl.isNotEmpty()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = "Portada de Perfil",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Change Cover Button overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    CoverPhotoPicker(
                        onPickImage = onPickCover,
                        isUploading = isUploadingCover
                    )
                }
            }

            // Avatar & User Details Info Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar with Camera overlay badge
                Box(
                    modifier = Modifier
                        .offset(y = (-30).dp)
                        .size(86.dp)
                        .clickable { onPickAvatar() }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(IosSettingsColors.cellElevated)
                            .border(3.dp, IosSettingsColors.cell, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarUrl.isNotEmpty()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = "Foto de perfil",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = IosSettingsColors.label,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    // Edit camera badge
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(IosSettingsColors.green)
                            .align(Alignment.BottomEnd)
                            .border(2.dp, IosSettingsColors.cell, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = "Cambiar avatar",
                            tint = IosSettingsColors.label,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        text = displayName.ifEmpty { "Tu Apodo de Pana" },
                        color = IosSettingsColors.label,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = statusText.ifEmpty { "Sin estado configurado" },
                        color = IosSettingsColors.secondaryLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}
