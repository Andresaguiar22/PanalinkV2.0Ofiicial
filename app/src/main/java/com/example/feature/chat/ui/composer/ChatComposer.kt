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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
    onShowTrashAnimation: () -> Unit,
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
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
    
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B141A))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
        if (recordState == RecordState.IDLE) {
            // ChatInputBar Container: Pill-shaped Surface
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .then(
                        if (isGhostMode) Modifier.border(
                            2.dp,
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(Color(0xFFBB86FC), Color(0xFF03DAC6))
                            ),
                            RoundedCornerShape(28.dp)
                        ) else Modifier
                    ),
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                border = if (!isGhostMode) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A3942)) else null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Icon: Alternator
                    IconButton(onClick = {
                        onToggleStickerPanel()
                    }) {
                        Icon(
                            imageVector = if (isStickerPanelOpen) Icons.Default.Keyboard else Icons.Default.SentimentSatisfied,
                            contentDescription = "Emojis, GIFs y Stickers",
                            tint = Color(0xFF8696A0),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // AI suggestion via OpenRouter (Mistral) - powered enhancement
                    val hasKey = !System.getenv("OPENROUTER_API_KEY").isNullOrBlank()
                    // Campo de Texto: BasicTextField con placeholder "Mensaje", soporte para múltiples líneas y expansión vertical suave.
                    androidx.compose.foundation.text.BasicTextField(
                        value = inputMessage,
                        onValueChange = { viewModel.onInputMessageChange(it) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = if (enterSendsMessage) androidx.compose.ui.text.input.ImeAction.Send else androidx.compose.ui.text.input.ImeAction.Default
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
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
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 16.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF00A884)),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (inputMessage.isEmpty()) {
                                    Text(
                                        text = "Mensaje",
                                        color = Color(0xFF8696A0),
                                        fontSize = 16.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    IconButton(onClick = {
                        onToggleAttachmentMenu()
                    }) {
                        Icon(
                            imageVector = if (isAttachmentMenuOpen) Icons.Default.Close else Icons.Default.AttachFile,
                            contentDescription = "Menú Adjuntos",
                            tint = if (isAttachmentMenuOpen) Color(0xFFFF2D55) else Color(0xFF54656F),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(onClick = {
                        cameraPermissionState.requestPermissions()
                    }) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Cámara",
                            tint = Color(0xFF8696A0),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        } else if (recordState == RecordState.RECORDING) {
            // Holding & Sliding mode recording panel on the left (Takes up the rest of the bar)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .background(Color.White, RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Red flashing recording dot
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
                        .background(Color.Red.copy(alpha = redDotAlpha.value), CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = String.format("%02d:%02d", recordDurationSeconds / 60, recordDurationSeconds % 60),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(16.dp))

                // Animated speech waves
                AnimatedAudioWaves(amplitudes = voiceAmplitudes)

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "⬅️ Desliza para cancelar",
                    color = Color(0xFF8696A0),
                    fontSize = 12.sp,
                    modifier = Modifier.graphicsLayer {
                        translationX = (micDragOffsetX * 0.4f).coerceAtMost(0f)
                    }
                )
            }
        } else if (recordState == RecordState.LOCKED_RECORDING) {
            // LOCKED state panel (Flujo de grabación fija WhatsApp style)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .background(Color.White, RoundedCornerShape(24.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Trash button (left, red icon in pink circle)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFCE8E6))
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
                        tint = Color.Red,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Timer + Audio Waveform
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format("%02d:%02d", recordDurationSeconds / 60, recordDurationSeconds % 60),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // Animated waveform when recording
                    AnimatedAudioWaves(amplitudes = voiceAmplitudes, isPaused = isRecordingPaused)
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Central "Pausar" / "Reanudar" pill button
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
                    color = Color.White,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isRecordingPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isRecordingPaused) "Reanudar" else "Pausar",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Red Stop button (Detener grabación e ir a Preescucha)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935))
                        .clickable {
                            isRecordingPaused = false
                            onVoiceGestureEvent(VoiceGestureEvent.StopAndPreviewRecording, context, null, recordDurationSeconds)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Preescuchar",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else if (recordState == RecordState.PREVIEWING) {
            // PREVIEWING mode panel (Preescucha antes de enviar)
            val previewFile = viewModel.previewFile
            val previewDuration = viewModel.previewDurationSeconds

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .background(Color.White, RoundedCornerShape(24.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Trash button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFCE8E6))
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
                        tint = Color.Red,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Play/Pause button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00A884))
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
                        tint = Color.White,
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
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Speed button (1x -> 1.5x -> 2x)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2A3942))
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
                                color = Color(0xFF00A884),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = String.format("%02d:%02d", totalSecs / 60, totalSecs % 60),
                            color = Color(0xFF8696A0),
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

        val isInputEmpty = inputMessage.trim().isEmpty()

        if (recordState == RecordState.LOCKED_RECORDING) {
            // Circular green send button for audio (Direct Send)
            FloatingActionButton(
                onClick = {
                    isRecordingPaused = false
                    onVoiceGestureEvent(VoiceGestureEvent.SendLockedRecording, context, replyingToMessage?.id, recordDurationSeconds)
                },
                modifier = Modifier.size(48.dp),
                containerColor = Color(0xFF00A884),
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Enviar",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        } else if (recordState == RecordState.PREVIEWING || recordState == RecordState.SENDING) {
            // Circular green send button for previewed audio
            val isSending = recordState == RecordState.SENDING || isPreviewSending
            FloatingActionButton(
                onClick = {
                    if (!isSending) {
                        triggerLightVibration(context)
                        onSendPreviewRecording(context, replyingToMessage?.id)
                    }
                },
                modifier = Modifier.size(48.dp),
                containerColor = if (isSending) Color(0xFF00A884).copy(alpha = 0.6f) else Color(0xFF00A884),
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar Nota",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        } else if (recordState == RecordState.IDLE && !isInputEmpty) {
            // Regular send or edit check button (Circular Green FAB)
            FloatingActionButton(
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
                    .size(48.dp)
                    .testTag("chat_send_button"),
                containerColor = Color(0xFF00A884),
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
            ) {
                Icon(
                    imageVector = if (editingMessage != null) Icons.Default.Check else Icons.AutoMirrored.Filled.Send,
                    contentDescription = if (editingMessage != null) "Guardar" else "Enviar",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        } else {
            // Microphone button with professional hold-and-drag gesture (Circular Green FAB)
            // En modo manos libres (LOCKED) este boton se oculta para no confundir
            // con el boton de enviar: la accion pasa al panel dedicado.
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
                modifier = Modifier
                    .graphicsLayer {
                        translationX = micDragOffsetX
                        translationY = micDragOffsetY
                        alpha = if (recordState == RecordState.LOCKED_RECORDING) 0f else 1f
                    }
                    .size(if (recordState == RecordState.LOCKED_RECORDING) 0.dp else 48.dp)
                    .scale(recordingPulseScale.value)
                    .clip(CircleShape)
                    .background(Color(0xFF00A884))
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
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }

    // Candado de bloqueo (estilo WhatsApp): aparece justo ENCIMA del botón del
    // micrófono mientras se arrastra hacia arriba. Se ancla de forma estable
    // (sin offsets negativos ni alturas ilimitadas) para que nunca quede
    // atravesado sobre los mensajes. Desaparece al bloquear.
    if (recordState == RecordState.RECORDING) {
        val lockHighlight = (micDragOffsetY / -70f).coerceIn(0f, 1f)
        // Rebote: cuando el drag alcanza el umbral de lock, el candado hace un
        // spring overshoot (1f -> 1.35f -> 1f) para que se sienta el "enganche"
        // en vez de una escala lineal.
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
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 64.dp)
                .height(120.dp)
                .width(48.dp)
                .background(Color(0xFF1F2C34), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Lock,
                    contentDescription = "Fijar grabación",
                    tint = if (lockHighlight > 0.8f) Color(0xFF00A884) else Color(0xFF8596A0),
                    modifier = Modifier.size(20.dp).graphicsLayer {
                        // Base growth (drag progress) + overshoot bounce on lock-in
                        val base = 1f + (lockHighlight * 0.2f)
                        val over = 1f + (bounce.value * 0.35f)
                        scaleX = base * over
                        scaleY = base * over
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = Color(0xFF8596A0),
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
