package com.example.feature.chat.ui.composer

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Message
import com.example.feature.chat.presentation.ChatViewModel
import com.example.feature.chat.presentation.RecordState
import com.example.feature.chat.ui.message.AnimatedAudioWaves
import com.example.feature.chat.ui.message.PreviewAudioWaveform
import com.example.ui.components.chat.voice.VoiceGestureEvent
import com.example.ui.components.chat.voice.voiceGestureDetector
import com.example.ui.screen.triggerLightVibration
import com.example.util.CameraPermissionState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatComposer(
    viewModel: ChatViewModel,
    inputMessage: String,
    replyingToMessage: Message?,
    editingMessage: Message?,
    isGhostMode: Boolean,
    enterSendsMessage: Boolean,
    hasMicPermission: Boolean,
    isStickerPanelOpen: Boolean,
    isAttachmentMenuOpen: Boolean,
    cameraPermissionState: CameraPermissionState,
    micPermissionLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, Boolean>,
    onToggleStickerPanel: () -> Unit,
    onToggleAttachmentMenu: () -> Unit,
    onVoiceGestureEvent: (VoiceGestureEvent, android.content.Context, String?, Int?) -> Unit,
    onSendPreviewRecording: (android.content.Context, String?) -> Unit,
    onShowTrashAnimation: () -> Unit
) {
    val context = LocalContext.current
    val recordState by viewModel.recordState.collectAsStateWithLifecycle()
    val voiceAmplitudes by viewModel.voiceAmplitudes.collectAsStateWithLifecycle()
    val previewPlayerState by viewModel.previewPlayerState.collectAsStateWithLifecycle()
    val previewWaveform by viewModel.previewWaveform.collectAsStateWithLifecycle()
    val isPreviewSending by viewModel.isPreviewSending.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var recordDurationSeconds by remember { androidx.compose.runtime.mutableIntStateOf(0) }


    LaunchedEffect(recordState) {
        if (recordState == RecordState.RECORDING || recordState == RecordState.LOCKED_RECORDING) {
            focusManager.clearFocus()
            keyboardController?.hide()
            while (true) {
                recordDurationSeconds = viewModel.getRecordingElapsedSeconds()
                delay(500)
            }
        } else {
            recordDurationSeconds = 0
        }
    }
    var isRecordingPaused by remember { mutableStateOf(false) }
    var micDragOffsetX by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var micDragOffsetY by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val isInputEmpty = inputMessage.trim().isEmpty()
    val primaryColor = androidx.compose.ui.graphics.Color(0xFF38BDF8)
    val bubbleColor = androidx.compose.ui.graphics.Color(0xFF1E293B).copy(alpha = 0.8f)

    // Recording pulse animation
    val recordingPulseScale = remember { Animatable(1f) }
    LaunchedEffect(recordState) {
        if (recordState == RecordState.RECORDING) {
            recordingPulseScale.animateTo(
                targetValue = 1.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            recordingPulseScale.snapTo(1f)
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Idle / text input mode: Floating Pill container
        if (recordState == RecordState.IDLE) {
            // Acción 5: La Píldora flotante con padding horizontal 16 + ime + 16
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Floating pill: Row background Color(0xFF1E293B).copy(alpha=0.8f), CircleShape
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(bubbleColor, CircleShape)
                        .heightIn(min = 52.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botón Emoji
                        IconButton(onClick = {
                            onToggleStickerPanel()
                        }) {
                            Icon(
                                imageVector = if (isStickerPanelOpen) Icons.Default.Keyboard else Icons.Default.SentimentSatisfied,
                                contentDescription = "Emojis, GIFs y Stickers",
                                tint = if (isStickerPanelOpen) primaryColor else androidx.compose.ui.graphics.Color(0xFF94A3B8),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Campo de texto transparente con hint
                        BasicTextField(
                            value = inputMessage,
                            onValueChange = { viewModel.onInputMessageChange(it) },
                            keyboardOptions = KeyboardOptions(
                                imeAction = if (enterSendsMessage) ImeAction.Send else ImeAction.Default
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (inputMessage.isNotBlank()) {
                                        if (editingMessage != null) {
                                            viewModel.editMessage(editingMessage!!.id, inputMessage)
                                            viewModel.clearReplyAndEdit()
                                        } else {
                                            viewModel.sendMessage(inputMessage, replyToId = replyingToMessage?.id, context = context)
                                            viewModel.clearReplyAndEdit()
                                        }
                                        viewModel.onInputMessageChange("")
                                    }
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_field")
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            textStyle = TextStyle(color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp),
                            cursorBrush = SolidColor(primaryColor),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (inputMessage.isEmpty()) {
                                        Text(
                                            text = "Escribe tu mensaje...",
                                            color = androidx.compose.ui.graphics.Color(0xFF94A3B8),
                                            fontSize = 16.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        // Botón Adjuntar (Clip) dentro de la píldora - abre modal compacto
                        IconButton(onClick = {
                            onToggleAttachmentMenu()
                        }) {
                            Icon(
                                imageVector = if (isAttachmentMenuOpen) Icons.Default.Close else Icons.Default.AttachFile,
                                contentDescription = "Menú Adjuntos",
                                tint = if (isAttachmentMenuOpen) androidx.compose.ui.graphics.Color(0xFFFF2D55) else androidx.compose.ui.graphics.Color(0xFF94A3B8),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Botón Enviar (Flecha azul) dentro de la píldora, solo cuando hay texto
                        if (!isInputEmpty) {
                            IconButton(
                                onClick = {
                                    if (editingMessage != null) {
                                        viewModel.editMessage(editingMessage!!.id, inputMessage)
                                        viewModel.clearReplyAndEdit()
                                    } else {
                                        viewModel.sendMessage(inputMessage, replyToId = replyingToMessage?.id, context = context)
                                        viewModel.clearReplyAndEdit()
                                    }
                                    viewModel.onInputMessageChange("")
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(primaryColor, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Enviar",
                                    tint = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Botón Micrófono FUERA de la píldora: solo cuando el texto está vacío
                if (isInputEmpty) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                translationX = micDragOffsetX
                                translationY = micDragOffsetY
                                alpha = if (recordState == RecordState.LOCKED_RECORDING) 0f else 1f
                            }
                            .size(if (recordState == RecordState.LOCKED_RECORDING) 0.dp else 48.dp)
                            .scale(recordingPulseScale.value)
                            .clip(CircleShape)
                            .background(if (recordState == RecordState.RECORDING) androidx.compose.ui.graphics.Color(0xFF00E5FF) else primaryColor)
                            .voiceGestureDetector(
                                enabled = true,
                                isLocked = recordState == RecordState.LOCKED_RECORDING,
                                onPermissionRequired = if (!hasMicPermission) {
                                    { micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO) }
                                } else null,
                                onDrag = { x, y ->
                                    micDragOffsetX = x
                                    micDragOffsetY = y
                                },
                                onEvent = { event ->
                                    micDragOffsetX = 0f
                                    micDragOffsetY = 0f

                                    when (event) {
                                        is VoiceGestureEvent.StartRecording -> {
                                            triggerLightVibration(context)
                                        }
                                        is VoiceGestureEvent.LockRecording -> {
                                            triggerLightVibration(context)
                                        }
                                        is VoiceGestureEvent.CancelRecording -> {
                                            triggerLightVibration(context)
                                            onShowTrashAnimation()
                                            Toast.makeText(context, "Grabación cancelada", Toast.LENGTH_SHORT).show()
                                        }
                                        else -> {}
                                    }
                                    onVoiceGestureEvent(event, context, replyingToMessage?.id, recordDurationSeconds)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Grabar nota de voz",
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
        // Recording mode: panel with recording UI
        else if (recordState == RecordState.RECORDING) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Panel de grabación premium
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(androidx.compose.ui.graphics.Color(0xFF1E293B).copy(alpha = 0.9f), RoundedCornerShape(28.dp))
                        .height(56.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val redDotAlpha = remember { Animatable(1f) }
                        LaunchedEffect(Unit) {
                            redDotAlpha.animateTo(
                                targetValue = 0.2f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(600),
                                    repeatMode = RepeatMode.Reverse
                                )
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(androidx.compose.ui.graphics.Color.Red.copy(alpha = redDotAlpha.value), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = String.format("%02d:%02d", recordDurationSeconds / 60, recordDurationSeconds % 60),
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        AnimatedAudioWaves(amplitudes = voiceAmplitudes)
                    }
                }

                // Cancel button outside the pill (red)
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color(0xFFFF2D55))
                        .clickable {
                            triggerLightVibration(context)
                            isRecordingPaused = false
                            Toast.makeText(context, "Grabación cancelada", Toast.LENGTH_SHORT).show()
                            onVoiceGestureEvent(VoiceGestureEvent.CancelRecording, context, null, null)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancelar",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        // Locked recording mode
        else if (recordState == RecordState.LOCKED_RECORDING) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(androidx.compose.ui.graphics.Color(0xFF1E293B).copy(alpha = 0.9f), RoundedCornerShape(28.dp))
                        .height(56.dp)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Trash button (left, red)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(androidx.compose.ui.graphics.Color(0xFFFCE8E6))
                                .clickable {
                                    triggerLightVibration(context)
                                    isRecordingPaused = false
                                    Toast.makeText(context, "Grabación descartada", Toast.LENGTH_SHORT).show()
                                    onVoiceGestureEvent(VoiceGestureEvent.CancelRecording, context, null, null)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Eliminar",
                                tint = androidx.compose.ui.graphics.Color.Red,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = String.format("%02d:%02d", recordDurationSeconds / 60, recordDurationSeconds % 60),
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        AnimatedAudioWaves(amplitudes = voiceAmplitudes, isPaused = isRecordingPaused)
                    }
                }

                // Pause/Resume pill
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            if (isRecordingPaused) {
                                isRecordingPaused = false
                                onVoiceGestureEvent(VoiceGestureEvent.ResumeRecording, context, null, null)
                            } else {
                                isRecordingPaused = true
                                onVoiceGestureEvent(VoiceGestureEvent.PauseRecording, context, null, null)
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    color = androidx.compose.ui.graphics.Color.White,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isRecordingPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isRecordingPaused) "Reanudar" else "Pausar",
                            color = androidx.compose.ui.graphics.Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Send button for locked recording
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(primaryColor)
                        .clickable {
                            isRecordingPaused = false
                            onVoiceGestureEvent(VoiceGestureEvent.SendLockedRecording, context, replyingToMessage?.id, recordDurationSeconds)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Enviar grabación",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        // Previewing / Sending mode
        else if (recordState == RecordState.PREVIEWING || recordState == RecordState.SENDING) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val previewDuration = viewModel.previewDurationSeconds

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(androidx.compose.ui.graphics.Color(0xFF1E293B).copy(alpha = 0.9f), RoundedCornerShape(28.dp))
                        .height(56.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Trash button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(androidx.compose.ui.graphics.Color(0xFFFCE8E6))
                                .clickable {
                                    triggerLightVibration(context)
                                    viewModel.cancelPreviewRecording(context)
                                    Toast.makeText(context, "Nota de voz descartada", Toast.LENGTH_SHORT).show()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Eliminar",
                                tint = androidx.compose.ui.graphics.Color.Red,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))

                        // Play/Pause button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(primaryColor)
                                .clickable {
                                    if (previewPlayerState.isPlaying) {
                                        viewModel.pausePreviewAudio()
                                    } else {
                                        viewModel.playPreviewAudio()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (previewPlayerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Reproducir / Pausar",
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Waveform & Time display
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val currentSecs = (previewPlayerState.currentPositionMs / 1000).toInt()
                                val totalSecs = if (previewPlayerState.durationMs > 0) {
                                    (previewPlayerState.durationMs / 1000).toInt()
                                } else {
                                    previewDuration
                                }
                                Text(
                                    text = String.format("%02d:%02d", currentSecs / 60, currentSecs % 60),
                                    color = androidx.compose.ui.graphics.Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                // Speed button
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(primaryColor.copy(alpha = 0.15f))
                                        .clickable {
                                            viewModel.togglePreviewSpeed()
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val speedLabel = when {
                                        previewPlayerState.playbackSpeed >= 1.9f -> "2x"
                                        previewPlayerState.playbackSpeed >= 1.4f -> "1.5x"
                                        else -> "1x"
                                    }
                                    Text(
                                        text = speedLabel,
                                        color = primaryColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = String.format("%02d:%02d", totalSecs / 60, totalSecs % 60),
                                    color = androidx.compose.ui.graphics.Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            val totalDurationMs = if (previewPlayerState.durationMs > 0) {
                                previewPlayerState.durationMs
                            } else {
                                (previewDuration * 1000L).coerceAtLeast(1000L)
                            }

                            PreviewAudioWaveform(
                                waveform = previewWaveform,
                                currentPositionMs = previewPlayerState.currentPositionMs,
                                durationMs = totalDurationMs,
                                onSeek = { posMs ->
                                    viewModel.seekPreviewAudio(posMs)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Enviar nota de voz preview
                val isSending = recordState == RecordState.SENDING || isPreviewSending
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isSending) primaryColor.copy(alpha = 0.6f) else primaryColor)
                        .clickable(enabled = !isSending) {
                            if (!isSending) {
                                triggerLightVibration(context)
                                onSendPreviewRecording(context, replyingToMessage?.id)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = androidx.compose.ui.graphics.Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Enviar Nota",
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }

    // Acción 6: Lock overlay for mic drag gesture
    if (recordState == RecordState.RECORDING) {
        val lockHighlight = (micDragOffsetY / -70f).coerceIn(0f, 1f)
        val bounce = remember { Animatable(0f) }
        LaunchedEffect(lockHighlight) {
            if (lockHighlight >= 1f && bounce.value == 0f) {
                bounce.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp)
                    .imePadding()
                    .height(120.dp)
                    .width(48.dp)
                    .background(androidx.compose.ui.graphics.Color(0xFF1F2C34), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Fijar grabación",
                    tint = if (lockHighlight > 0.8f) primaryColor else androidx.compose.ui.graphics.Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp).graphicsLayer {
                        val base = 1f + (lockHighlight * 0.2f)
                        val over = 1f + (bounce.value * 0.35f)
                        scaleX = base * over
                        scaleY = base * over
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp).graphicsLayer {
                        translationY = -10f * lockHighlight
                        alpha = 1f - lockHighlight
                    }
                )
            }
        }
    }
}

}
