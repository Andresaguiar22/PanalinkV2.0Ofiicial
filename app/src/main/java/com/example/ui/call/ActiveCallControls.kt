package com.example.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.settings.ios.IosSettingsColors

/**
 * ActiveCallControls displays the primary interactive bottom bar controls during an active call.
 * Uses a vertical dark gradient overlay for optimal visibility over video renderers.
 */
@Composable
fun ActiveCallControls(
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier,
    isVideoCall: Boolean = false,
    isCameraOn: Boolean = true,
    onCameraToggle: () -> Unit = {},
    onCameraSwitch: () -> Unit = {},
    onMoreOptionSelected: (String) -> Unit = {}
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, IosSettingsColors.mediaScrim)
                )
            )
            .padding(horizontal = 16.dp, vertical = 28.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Microphone Toggle
            CallActionButton(
                onClick = onMuteToggle,
                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = "Toggle Mute",
                containerColor = if (isMuted) IosSettingsColors.label else IosSettingsColors.separator,
                contentColor = if (isMuted) IosSettingsColors.groupBackground else IosSettingsColors.label,
                label = if (isMuted) "Silenciado" else "Silenciar",
                testTag = "mute_button"
            )

            // 2. Speaker Output Toggle
            CallActionButton(
                onClick = onSpeakerToggle,
                icon = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeMute,
                contentDescription = "Toggle Speaker",
                containerColor = if (isSpeakerOn) IosSettingsColors.label else IosSettingsColors.separator,
                contentColor = if (isSpeakerOn) IosSettingsColors.groupBackground else IosSettingsColors.label,
                label = if (isSpeakerOn) "Altavoz" else "Auricular",
                testTag = "speaker_button"
            )

            if (isVideoCall) {
                // 3. Camera Toggle (Video only)
                CallActionButton(
                    onClick = onCameraToggle,
                    icon = if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    contentDescription = "Toggle Camera",
                    containerColor = if (isCameraOn) IosSettingsColors.separator else IosSettingsColors.label,
                    contentColor = if (isCameraOn) IosSettingsColors.label else IosSettingsColors.groupBackground,
                    label = if (isCameraOn) "Cámara" else "Sin Cámara",
                    testTag = "camera_button"
                )

                // 4. Switch Front/Rear Camera (Video only)
                CallActionButton(
                    onClick = onCameraSwitch,
                    icon = Icons.Default.FlipCameraAndroid,
                    contentDescription = "Switch Camera",
                    containerColor = IosSettingsColors.separator,
                    contentColor = IosSettingsColors.label,
                    label = "Girar",
                    testTag = "switch_camera_button"
                )
            } else {
                // 3. More Menu (Audio only)
                Box {
                    CallActionButton(
                        onClick = { showMoreMenu = true },
                        icon = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        containerColor = IosSettingsColors.separator,
                        contentColor = IosSettingsColors.label,
                        label = "Más",
                        testTag = "more_options_button"
                    )

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        modifier = Modifier.background(IosSettingsColors.cellElevated) // Slate 800
                    ) {
                        DropdownMenuItem(
                            text = { Text("Cambiar a video", color = IosSettingsColors.label) },
                            onClick = {
                                showMoreMenu = false
                                onMoreOptionSelected("change_to_video")
                            },
                            leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null, tint = IosSettingsColors.label) }
                        )
                        DropdownMenuItem(
                            text = { Text("Dispositivo Bluetooth", color = IosSettingsColors.label) },
                            onClick = {
                                showMoreMenu = false
                                onMoreOptionSelected("bluetooth")
                            },
                            leadingIcon = { Icon(Icons.Default.Bluetooth, contentDescription = null, tint = IosSettingsColors.label) }
                        )
                        DropdownMenuItem(
                            text = { Text("Enviar mensaje", color = IosSettingsColors.label) },
                            onClick = {
                                showMoreMenu = false
                                onMoreOptionSelected("send_message")
                            },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, tint = IosSettingsColors.label) }
                        )
                    }
                }
            }

            // 5. Large Red End Call Button
            CallActionButton(
                onClick = onEndCall,
                icon = Icons.Default.CallEnd,
                contentDescription = "End Call",
                containerColor = IosSettingsColors.red, // Red 500
                contentColor = IosSettingsColors.label,
                size = 64.dp,
                iconSize = 30.dp,
                label = "Colgar",
                testTag = "end_call_button"
            )
        }
    }
}
