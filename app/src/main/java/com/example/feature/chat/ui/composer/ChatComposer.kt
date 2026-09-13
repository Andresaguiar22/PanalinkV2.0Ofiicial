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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
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
    onShowTrashAnimation: () -> Unit,
    inputFocusRequester: androidx.compose.ui.focus.FocusRequester = androidx.compose.ui.focus.FocusRequester()
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
    var isRecordingPaused by remember { mutableStateOf(false) }

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
            isRecordingPaused = false
        }
    }
    var micDragOffsetX by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var micDragOffsetY by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val isInputEmpty = inputMessage.trim().isEmpty()
    val primaryColor = androidx.compose.ui.graphics.Color(0xFF38BDF8)
    val bubbleColor = androidx.compose.ui.graphics.Color(0xFF1E3A5F).copy(alpha = 0.75f)
    // Distancia en PX que debe recorrer el dedo (con el micrófono) para que el
    // candado atrape el mic. Geometría real: el candado vive en el top-end del
    // composer con offset(y=-96.dp) y alto 88dp (su centro queda ~52dp por encima
    // del borde superior del composer); el mic parte de la fila con su centro a
    // ~26dp de ese mismo borde. Distancia dedo->candado = 26 + 52 = 78dp.
    // 90dp da una pisada ligera SEGURA sobre el candado (sin pasarse de largo).
    val lockThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        val micToLockDp = 90.dp
        micToLockDp.toPx()
    }

    // Distancia horizontal (PX) para ELIMINAR al deslizar el mic hacia la izquierda.
    // Se desliza hasta "mitad de la píldora" (tramo prudencial) y ahí se suelta la
    // nota (no hay que llegar al bote ni al borde de pantalla).
    val cancelThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
        val pillWidthDp = (screenWidthDp - 32).coerceAtLeast(120) / 2
        pillWidthDp.dp.toPx()
    }

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

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Acción 7: Slide-to-delete DENTRO de la píldora. Al deslizar el mic
        // horizontalmente hacia la izquierda aparece la píldora de borrado con el
        // bote en el extremo opuesto (a la izquierda, dentro de la píldora). La
        // tapa del bote se abre con el recorrido y la nota se elimina al alcanzar
        // la MITAD de la píldora (cancelThresholdPx) — sin llegar al bote ni al
        // borde de pantalla. El mic se desliza recto (viene ya con eje dominante).
        if (recordState == RecordState.RECORDING && micDragOffsetX < -6f) {
            val deleteProgress = (micDragOffsetX / -cancelThresholdPx).coerceIn(0f, 1f)
            val trashOpen = remember { Animatable(0f) }
            LaunchedEffect(deleteProgress) {
                trashOpen.animateTo(
                    targetValue = deleteProgress,
                    animationSpec = tween(90)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .graphicsLayer { alpha = 0.85f },
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(start = 0.dp, end = 16.dp)
                        .background(bubbleColor, RoundedCornerShape(28.dp))
                        .border(1.dp, androidx.compose.ui.graphics.Color(0xFFE53935).copy(alpha = 0.5f + 0.5f * deleteProgress), RoundedCornerShape(28.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bote en el extremo izquierdo DENTRO de la píldora.
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(44.dp)
                            .graphicsLayer {
                                val bounce2 = if (deleteProgress >= 1f) {
                                    // leve pulso al punto exacto de borrado
                                    1f + (0.15f * (1f - ((deleteProgress - 1f).coerceAtLeast(0f) * 0f)))
                                } else 1f
                                scaleX = 1f + (0.1f * deleteProgress) * bounce2
                                scaleY = 1f + (0.1f * deleteProgress) * bounce2
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar",
                            tint = androidx.compose.ui.graphics.Color(0xFFE53935),
                            modifier = Modifier.size(30.dp)
                        )
                        // Tapa que se abre con el recorrido
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .graphicsLayer {
                                    val rot = -deleteProgress * 45f
                                    rotationZ = rot
                                    val pivotY = 20f
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, pivotY)
                                }
                                .padding(top = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Pequeña "tapa": cuadrado pequeño encima del bote
                            Box(
                                modifier = Modifier
                                    .size(width = 20.dp, height = 6.dp)
                                    .background(androidx.compose.ui.graphics.Color(0xFFE53935), RoundedCornerShape(3.dp))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Text(
                        text = if (deleteProgress < 0.55f) "Desliza para eliminar" else "Suelta para eliminar",
                        color = androidx.compose.ui.graphics.Color(0xFFFFDADA),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = String.format("%02d:%02d", recordDurationSeconds / 60, recordDurationSeconds % 60),
                        color = androidx.compose.ui.graphics.Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // =====================================================================
        // MODO IDLE: píldora azul con borde, emoji+texto+clip dentro,
        // micrófono FUERA (a la derecha). El gesto del micrófono vive SIEMPRE en
        // este estado: if (IDLE) no reemplaza el botón, solo cambia su contenido.
        // Soltar el dedo → FinishRecording → envío inmediato.
        // =====================================================================
        if (recordState == RecordState.IDLE || recordState == RecordState.RECORDING) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp)
                    .imePadding()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pastilla única: campo + emoji + clip
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .background(bubbleColor, CircleShape)
                        .border(1.dp, primaryColor.copy(alpha = 0.7f), CircleShape)
                        .padding(start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botón Emoji
                        IconButton(onClick = { onToggleStickerPanel() }) {
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
                                .focusRequester(inputFocusRequester)
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

                        // Botón Adjuntar (Clip) dentro de la píldora
                        IconButton(onClick = { onToggleAttachmentMenu() }) {
                            Icon(
                                imageVector = if (isAttachmentMenuOpen) Icons.Default.Close else Icons.Default.AttachFile,
                                contentDescription = "Menú Adjuntos",
                                tint = if (isAttachmentMenuOpen) androidx.compose.ui.graphics.Color(0xFFFF2D55) else androidx.compose.ui.graphics.Color(0xFF94A3B8),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Botón DERECHO con doble función:
                //  - Si hay texto (o emoji) escrito: botón ENVIAR (verde/cian) que manda
                //    el mensaje de una. Se desactiva solo si el texto es solo espacios.
                //  - Si el campo está vacío: botón MICRÓFONO (grabación al mantener).
                // El gesto del micrófono vive SIEMPRE aquí: al mantener y soltar,
                // FinishRecording llega porque el botón se recompone con el mismo detentor.
                if (inputMessage.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        androidx.compose.ui.graphics.Color(0xFF38BDF8),
                                        androidx.compose.ui.graphics.Color(0xFF2563EB)
                                    )
                                )
                            )
                            .clickable {
                                viewModel.sendMessage(inputMessage.trim(), replyToId = replyingToMessage?.id, context = context)
                                viewModel.clearReplyAndEdit()
                                viewModel.onInputMessageChange("")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Enviar mensaje",
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                // Botón del micrófono FUERA de la píldora. SELECCIONABLE en
                // recoding (= mantiene el tamaño, muestra ondas, gesto activo).
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = micDragOffsetX
                            translationY = micDragOffsetY
                        }
                        .size(52.dp)
                        .scale(recordingPulseScale.value)
                        .clip(CircleShape)
                        .background(
                            when {
                                recordState == RecordState.RECORDING || recordState == RecordState.LOCKED_RECORDING ->
                                    Brush.radialGradient(
                                        colors = listOf(
                                            androidx.compose.ui.graphics.Color(0xFF2A3546).copy(alpha = 0.95f),
                                            androidx.compose.ui.graphics.Color(0xFF131A26)
                                        )
                                    )
                                else -> Brush.radialGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = 0.45f),
                                        androidx.compose.ui.graphics.Color.Transparent
                                    )
                                )
                            }
                        )
                        .border(
                            width = if (recordState == RecordState.RECORDING || recordState == RecordState.LOCKED_RECORDING) 2.dp else 0.dp,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                        .voiceGestureDetector(
                            enabled = true,
                            isLocked = recordState == RecordState.LOCKED_RECORDING,
                            lockThresholdY = -lockThresholdPx,
                            cancelThresholdX = -cancelThresholdPx,
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
                                        isRecordingPaused = false
                                        triggerLightVibration(context)
                                    }
                                    is VoiceGestureEvent.LockRecording -> {
                                        triggerLightVibration(context)
                                    }
                                    is VoiceGestureEvent.CancelRecording -> {
                                        isRecordingPaused = false
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
                    when {
                        recordState == RecordState.SENDING ->
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = primaryColor,
                                strokeWidth = 2.dp
                            )
                        recordState == RecordState.RECORDING ->
                            AnimatedAudioWaves(amplitudes = voiceAmplitudes, isPaused = isRecordingPaused)
                        else ->
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Grabar nota de voz",
                                tint = primaryColor,
                                modifier = Modifier.size(24.dp)
                            )
                    }
                }
                }
            }
        }
        // MODO LOCKED_RECORDING (manos libres): píldora azul con borde que
        // muestra la grabación en curso encerrada entre dos acciones claras:
        //    [■ STOP]  ...  00:12  ^^^^^  ...  [➤ ENVIAR]
        // Stop detiene la nota y pasa a la píldora de pre-escucha (donde aparecen
        // eliminar + play/pausa para escuchar). Enviar manda la nota directamente.
        else if (recordState == RecordState.LOCKED_RECORDING) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp)
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .background(bubbleColor, RoundedCornerShape(28.dp))
                        .border(1.dp, primaryColor.copy(alpha = 0.7f), RoundedCornerShape(28.dp))
                        .padding(start = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // STOP (rojo): detiene la grabación y abre la pre-escucha
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Color(0xFFFCE8E6))
                            .clickable {
                                triggerLightVibration(context)
                                onVoiceGestureEvent(VoiceGestureEvent.StopAndPreviewRecording, context, null, null)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Detener grabación",
                            tint = androidx.compose.ui.graphics.Color(0xFFE11D48),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = String.format("%02d:%02d", recordDurationSeconds / 60, recordDurationSeconds % 60),
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    AnimatedAudioWaves(amplitudes = voiceAmplitudes, isPaused = isRecordingPaused)

                    Spacer(modifier = Modifier.weight(1f))

                    // Enviar grabación
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        androidx.compose.ui.graphics.Color(0xFF38BDF8),
                                        androidx.compose.ui.graphics.Color(0xFF2563EB)
                                    )
                                )
                            )
                            .clickable {
                                triggerLightVibration(context)
                                onVoiceGestureEvent(VoiceGestureEvent.SendLockedRecording, context, replyingToMessage?.id, recordDurationSeconds)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Enviar grabación",
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        // Previewing mode: píldora de revisión con borrar, reproducir,
        // forma de onda y Enviar. Solo llega aquí cuando el usuario usó la
        // pre-escucha (botón Escuchar del candado = StopAndPreviewRecording).
        else if (recordState == RecordState.PREVIEWING) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp)
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val previewDuration = viewModel.previewDurationSeconds

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .background(bubbleColor, RoundedCornerShape(28.dp))
                        .border(1.dp, primaryColor.copy(alpha = 0.7f), RoundedCornerShape(28.dp))
                        .padding(start = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Trash (cancelar)
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

                    // Play/Pause botón
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

                    // Forma de onda + tiempo
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

                            // Speed
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(primaryColor.copy(alpha = 0.15f))
                                    .clickable { viewModel.togglePreviewSpeed() }
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
                            onSeek = { posMs -> viewModel.seekPreviewAudio(posMs) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Enviar nota (fuera del pill)
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
        // SENDING (envío directo al soltar el dedo): barra slim de estado, sin
        // pre-escucha. Muestra solo el spinner + "Enviando nota..." + opción de
        // cancelar el guardado si el envío tarda (vuelve al estado IDLE y borra).
        else if (recordState == RecordState.SENDING) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp)
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .background(bubbleColor, RoundedCornerShape(28.dp))
                        .border(1.dp, primaryColor.copy(alpha = 0.7f), RoundedCornerShape(28.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = primaryColor,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Enviando nota...",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = String.format("%02d:%02d", recordDurationSeconds / 60, recordDurationSeconds % 60),
                        color = androidx.compose.ui.graphics.Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }
        }
        // Acción 6: Lock overlay for mic drag gesture
        // Se dibuja DENTRO del Box raíz del composer, anclado con offset para
        // flotar sobre el chat. NO usar fillMaxSize() aquí: un hijo fillMaxSize()
        // obliga al composer a tomar toda la altura disponible y el panel de
        // grabación termina renderizándose arriba, dejando un hueco negro gigante.
        if (recordState == RecordState.RECORDING) {
            val lockHighlight = (micDragOffsetY / -lockThresholdPx).coerceIn(0f, 1f)
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
                    .align(Alignment.TopEnd)
                    .offset(y = (-96).dp)
                    .padding(end = 10.dp)
                    .height(88.dp)
                    .width(44.dp)
                    .background(androidx.compose.ui.graphics.Color(0xFF1E293B).copy(alpha = 0.92f), RoundedCornerShape(22.dp)),
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
