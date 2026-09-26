package com.example.ui.call

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.bounceClick
import com.example.ui.settings.ios.IosSettingsColors
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff

/**
 * CallControls displays call interactions (Mute, Speaker, Video, Flip, End) with Material 3.
 */
@Composable
fun CallControls(
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier,
    isVideoCall: Boolean = false,
    isCameraOn: Boolean = true,
    isConnected: Boolean = true,
    onCameraToggle: () -> Unit = {},
    onCameraSwitch: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isConnected) {
            // Mute Microphone
            IconButton(
                onClick = {},
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isMuted) IosSettingsColors.label else IosSettingsColors.mediaScrimSoft
                ),
                modifier = Modifier
                    .size(56.dp)
                    .bounceClick(onMuteToggle)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    contentDescription = "Mute Microphone",
                    tint = if (isMuted) IosSettingsColors.groupBackground else IosSettingsColors.label
                )
            }
    
            // Toggle Speaker
            IconButton(
                onClick = {},
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isSpeakerOn) IosSettingsColors.label else IosSettingsColors.mediaScrimSoft
                ),
                modifier = Modifier
                    .size(56.dp)
                    .bounceClick(onSpeakerToggle)
            ) {
                Icon(
                    imageVector = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeMute,
                    contentDescription = "Toggle Speaker",
                    tint = if (isSpeakerOn) IosSettingsColors.groupBackground else IosSettingsColors.label
                )
            }
    
            if (isVideoCall) {
                // Toggle Video Camera
                IconButton(
                    onClick = {},
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (!isCameraOn) IosSettingsColors.label else IosSettingsColors.mediaScrimSoft
                    ),
                    modifier = Modifier
                        .size(56.dp)
                        .bounceClick(onCameraToggle)
                ) {
                    Icon(
                        imageVector = if (isCameraOn) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                        contentDescription = "Toggle Video",
                        tint = if (!isCameraOn) IosSettingsColors.groupBackground else IosSettingsColors.label
                    )
                }
    
                // Switch front/back camera
                IconButton(
                    onClick = {},
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = IosSettingsColors.mediaScrimSoft
                    ),
                    modifier = Modifier
                        .size(56.dp)
                        .bounceClick(onCameraSwitch)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FlipCameraAndroid,
                        contentDescription = "Flip Camera",
                        tint = IosSettingsColors.label
                    )
                }
            }
        }

        // End Call (Always Red)
        IconButton(
            onClick = {},
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = IosSettingsColors.red
            ),
            modifier = Modifier
                .size(64.dp)
                .bounceClick(onEndCall)
        ) {
            Icon(
                imageVector = Icons.Rounded.CallEnd,
                contentDescription = "End Call",
                tint = IosSettingsColors.label,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
