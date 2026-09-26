package com.example.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.viewmodel.StatesViewModel
import com.example.ui.settings.ios.IosSettingsColors

private enum class StoryMode { IMAGE, VIDEO, TEXT }

private data class StoryPalette(val name: String, val top: Color, val bottom: Color)

private val PALETTES = listOf(
    StoryPalette("Pana Link", Color(0xFF0F1C26), Color(0xFF1A3B2A)),
    StoryPalette("Venezuela", Color(0xFF001A33), Color(0xFF003366)),
    StoryPalette("Atardecer", Color(0xFF2B0A3D), Color(0xFF4A1A6B)),
    StoryPalette("Caribe", Color(0xFF002D2D), Color(0xFF005D67)),
    StoryPalette("Noticias", IosSettingsColors.cell, Color(0xFF3D3D3D)),
    StoryPalette("Rojo Pana", Color(0xFF330A0A), Color(0xFF5D1B1B)),
)

private data class FreeMusicOption(val name: String, val url: String)

private val FREE_MUSIC = listOf(
    FreeMusicOption("Lofi Joropo", "https://assets.mixkit.co/music/preview/mixkit-lofi-band-925.mp3"),
    FreeMusicOption("Tambor Remix", "https://assets.mixkit.co/music/preview/mixkit-tribal-drums-958.mp3"),
    FreeMusicOption("Gaita Pop", "https://assets.mixkit.co/music/preview/mixkit-pop-05-1522.mp3"),
    FreeMusicOption("Atardecer", "https://assets.mixkit.co/music/preview/mixkit-dreaming-big-31.mp3"),
)

// Paleta de Colores estilo iOS (dark premium)
private val IosBlackStory = Color(0xFF000000)
private val BrandGreenStory = IosSettingsColors.green
private val SegmentedBgStory = IosSettingsColors.cell
private val SegmentedActiveStory = IosSettingsColors.separator
private val PlaceholderBgStory: Color get() = IosSettingsColors.cell
private val IconBoxBgStory = IosSettingsColors.cellElevated
private val TextGrayStory = IosSettingsColors.secondaryLabel
private val BorderWhiteAlphaStory = IosSettingsColors.separator

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CleanStoryEditorScreen(
    viewModel: StatesViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    var mode by remember { mutableStateOf(StoryMode.IMAGE) }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var mediaMime by remember { mutableStateOf<String?>(null) }

    var textContent by remember { mutableStateOf("") }
    var overlayTextEnabled by remember { mutableStateOf(false) }
    var palette by remember { mutableStateOf(PALETTES[0]) }

    var audioEnabled by remember { mutableStateOf(false) }
    var audioName by remember { mutableStateOf<String?>(null) }
    var audioUri by remember { mutableStateOf<Uri?>(null) }
    var audioUrl by remember { mutableStateOf<String?>(null) }
    var isUploadingAudio by remember { mutableStateOf(false) }
    var audioUploadFailed by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }

    // Límite de duración para historias de vídeo: 2 minutos (120 s).
    val MAX_STORY_VIDEO_SECONDS = 120
    // Diálogo de aviso cuando el clip excede el límite (no bloquea, solo informa).
    var showOverlongClipDialog by remember { mutableStateOf(false) }

    var audioPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var audioPlaying by remember { mutableStateOf(false) }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            mediaUri = uri
            mediaMime = context.contentResolver.getType(uri)
        } else {
            Toast.makeText(context, "Sin medio seleccionado", Toast.LENGTH_SHORT).show()
        }
    }

    val pickAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            audioUri = uri
            audioName = uri.lastPathSegment ?: "Audio personalizado"
            audioUrl = null
            audioUploadFailed = false
            isUploadingAudio = true
            // Subida durable: cola persistente + WorkManager (sin red desde Compose).
            viewModel.enqueueStoryAudio(
                context = context,
                uri = uri,
                mimeType = context.contentResolver.getType(uri) ?: "audio/mpeg",
                audioName = audioName
            )
        }
    }

    val storyAudioUploadId by viewModel.storyAudioUploadId.collectAsState()
    val storyAudioUploadError by viewModel.storyAudioUploadError.collectAsState()

    // Observa la subida durable del audio: cuando el worker termina, resolvemos el URL.
    LaunchedEffect(storyAudioUploadId) {
        val uploadId = storyAudioUploadId ?: return@LaunchedEffect
        androidx.work.WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow("social_upload_$uploadId")
            .collect { infos ->
                val info = infos.firstOrNull() ?: return@collect
                when (info.state) {
                    androidx.work.WorkInfo.State.SUCCEEDED -> {
                        val entity = com.example.data.database.PanalinkDatabase.getDatabase(context)
                            .pendingUploadDao().getUploadById(uploadId)
                        if (entity?.remoteUrl?.isNotBlank() == true) {
                            audioUrl = entity.remoteUrl
                            isUploadingAudio = false
                        } else {
                            audioUploadFailed = true
                            isUploadingAudio = false
                        }
                    }
                    androidx.work.WorkInfo.State.FAILED -> {
                        audioUploadFailed = true
                        isUploadingAudio = false
                    }
                    else -> Unit
                }
            }
    }

    LaunchedEffect(storyAudioUploadError) {
        val error = storyAudioUploadError ?: return@LaunchedEffect
        audioUploadFailed = true
        isUploadingAudio = false
        Toast.makeText(context, "No se pudo programar el audio: $error", Toast.LENGTH_SHORT).show()
        viewModel.clearStoryAudioUploadError()
    }

    fun launchMediaPicker() {
        val kind = when (mode) {
            StoryMode.IMAGE -> ActivityResultContracts.PickVisualMedia.ImageOnly
            StoryMode.VIDEO -> ActivityResultContracts.PickVisualMedia.VideoOnly
            StoryMode.TEXT -> ActivityResultContracts.PickVisualMedia.ImageAndVideo
        }
        pickMedia.launch(PickVisualMediaRequest(kind))
    }

    fun togglePreviewAudio() {
        if (audioPlaying) {
            audioPlayer?.stop()
            audioPlayer?.reset()
            audioPlaying = false
            return
        }
        val source = audioUrl ?: audioUri?.toString() ?: return
        try {
            audioPlayer?.stop(); audioPlayer?.reset()
            audioPlayer = android.media.MediaPlayer().apply {
                setDataSource(source)
                prepareAsync()
                setOnPreparedListener { it.start() }
                setOnCompletionListener { audioPlaying = false }
            }
            audioPlaying = true
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo reproducir: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioPlayer?.stop()
            audioPlayer?.release()
        }
    }

    fun canPublish(): Boolean = when (mode) {
        StoryMode.TEXT -> textContent.isNotBlank()
        else -> mediaUri != null
    }

    fun queryVideoDurationMs(uri: android.net.Uri?): Long {
        if (uri == null) return 0L
        return try {
            android.media.MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                val d = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                d?.toLongOrNull() ?: 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    fun performPublish(mediaFile: java.io.File? = null) {
        if (isUploadingAudio) {
            Toast.makeText(context, "Subiendo audio, espera…", Toast.LENGTH_SHORT).show()
            return
        }
        if (audioUploadFailed) {
            Toast.makeText(context, "El audio no se pudo subir; se publicará sin audio", Toast.LENGTH_SHORT).show()
            audioEnabled = false
            audioName = null
        }
        publishing = true
        // Close the editor immediately. The enqueue must run in the activity-scoped
        // StatesViewModel (not the composition scope, which is cancelled by onBack)
        // so the WorkManager upload survives navigation; progress shows in the
        // PendingUploadsBanner on the chat list.
        val isVideo = mode == StoryMode.VIDEO
        val caption = when (mode) {
            StoryMode.TEXT -> textContent.trim()
            else -> textContent.trim().takeIf { overlayTextEnabled && it.isNotBlank() }
                ?: if (isVideo) "Pana Vídeo" else "Pana Foto"
        }
        val resolvedAudioUrl = if (audioEnabled) audioUrl else null
        Toast.makeText(context, "Publicando tu historia…", Toast.LENGTH_SHORT).show()
        if (mode == StoryMode.TEXT) {
            viewModel.publishTextState(caption, audioUrl = resolvedAudioUrl)
        } else {
            viewModel.publishStoryBackground(
                context = context,
                uri = mediaUri!!,
                mimeType = mediaMime ?: if (isVideo) "video/mp4" else "image/jpeg",
                caption = caption,
                audioUrl = resolvedAudioUrl,
                mediaFile = mediaFile
            )
        }
        onBack()
    }

    // El usuario aceptó publicar un video de más de 2 minutos: se trunca el clip
    // a los primeros 120s (stream-copy, sin recodificar) para respetar el límite.
    // Si el truncado falla por cualquier motivo, se publica el original intacto.
    fun confirmOverlongPublish() {
        showOverlongClipDialog = false
        val mUri = mediaUri
        if (mUri == null) { performPublish(); return }
        try {
            val pendingMediaDir = java.io.File(context.filesDir, "pending_media")
            if (!pendingMediaDir.exists()) pendingMediaDir.mkdirs()
            val src = java.io.File.createTempFile("overlong_src_", ".mp4", pendingMediaDir)
            context.contentResolver.openInputStream(mUri)?.use { input ->
                src.outputStream().use { out -> input.copyTo(out) }
            }
            val out = java.io.File(pendingMediaDir, "story_${System.currentTimeMillis()}.mp4")
            val ok = com.example.util.VideoTruncatorHelper.truncateToMs(
                src.absolutePath,
                out.absolutePath,
                MAX_STORY_VIDEO_SECONDS * 1000L
            )
            src.delete()
            if (ok) {
                performPublish(mediaFile = out)
            } else {
                out.delete()
                performPublish()
            }
        } catch (e: Exception) {
            performPublish()
        }
    }

    fun uploadStory() {
        if (publishing) return
        if (!canPublish()) {
            Toast.makeText(
                context,
                if (mode == StoryMode.TEXT) "Escribe un texto para tu historia" else "Elige una imagen o vídeo",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        // Límite de 2 minutos para vídeo en historias: avisar (no bloquear) si se excede.
        if (mode == StoryMode.VIDEO && mediaUri != null) {
            val durationMs = queryVideoDurationMs(mediaUri)
            if (durationMs > 0L && durationMs > MAX_STORY_VIDEO_SECONDS * 1000L) {
                showOverlongClipDialog = true
                return
            }
        }
        performPublish()
    }

Box(
        Modifier
            .fillMaxSize()
            .background(IosBlackStory)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(bottom = 30.dp) // Espacio para el Home Indicator
        ) {
            // Top Navigation Bar (Estilo iOS)
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón Atrás
                Row(
                    Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onBack() }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBackIos,
                        "Atrás",
                        tint = IosSettingsColors.label,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Título Centrado
                Text(
                    "Nueva historia",
                    color = IosSettingsColors.label,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )

                // Botón Acción Primaria
                Row(
                    Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !publishing
                        ) { uploadStory() }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedVisibility(visible = publishing, enter = fadeIn()) {
                        CircularProgressIndicator(
                            color = BrandGreenStory,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Publicar",
                        color = BrandGreenStory,
                        fontSize =  17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Área Scrolleable
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {

                // iOS Segmented Control
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .background(SegmentedBgStory, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SegmentedButtonStory(
                        "Imagen",
                        Icons.Outlined.Image,
                        mode == StoryMode.IMAGE,
                        Modifier.weight(1f)
                    ) { mode = StoryMode.IMAGE; mediaUri = null }
                    SegmentedButtonStory(
                        "Vídeo",
                        Icons.Outlined.Videocam,
                        mode == StoryMode.VIDEO,
                        Modifier.weight(1f)
                    ) { mode = StoryMode.VIDEO; mediaUri = null }
                    SegmentedButtonStory(
                        "Texto",
                        Icons.Outlined.TextFields,
                        mode == StoryMode.TEXT,
                        Modifier.weight(1f)
                    ) { mode = StoryMode.TEXT }
                }

                Spacer(Modifier.height(4.dp))

                if (mode == StoryMode.TEXT) {

                    // Vista previa de historia de texto: ocupa el alto libre, estilo iOS
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(24.dp))
                            .border(1.dp, BorderWhiteAlphaStory, RoundedCornerShape(24.dp))
                            .background(Brush.verticalGradient(listOf(palette.top, palette.bottom))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            textContent.ifBlank { "Tu texto aparecerá aquí" },
                            color = if (textContent.isBlank()) IosSettingsColors.label.copy(alpha = .5f) else IosSettingsColors.label,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Tema de fondo", color = TextGrayStory, fontSize = 11.sp)
                    LazyRow(
                        Modifier.padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(PALETTES) { p ->
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .border(if (palette == p) 2.dp else  0.5.dp, IosSettingsColors.label, CircleShape)
                                    .background(Brush.linearGradient(listOf(p.top, p.bottom)))
                                    .clickable { palette = p }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = textContent,
                        onValueChange = { if (it.length <= 240) textContent = it },
                        placeholder = { Text("Escribe tu historia…", color = TextGrayStory) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 70.dp, max = 110.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = IosSettingsColors.label,
                            unfocusedTextColor = IosSettingsColors.label,
                            focusedBorderColor = BrandGreenStory,
                            unfocusedBorderColor = TextGrayStory.copy(alpha = .4f)
                        ),
                        maxLines = 4
                    )
                } else {

                    // Placeholder Multimedia estilo iOS
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(PlaceholderBgStory)
                            .border(1.dp, BorderWhiteAlphaStory, RoundedCornerShape(24.dp))
                            .clickable { launchMediaPicker() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (mediaUri != null) {
                            AsyncImage(
                                model = mediaUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (mode == StoryMode.VIDEO) {
                                Icon(
                                    Icons.Outlined.Videocam, null,
                                    tint = IosSettingsColors.label,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(10.dp)
                                        .size(22.dp)
                                )
                            }
                            if (overlayTextEnabled && textContent.isNotBlank()) {
                                Text(
                                    textContent,
                                    color = IosSettingsColors.label,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .background(IosSettingsColors.mediaScrimSoft)
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                )
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier
                                        .size(64.dp)
                                        .background(IconBoxBgStory, RoundedCornerShape(16.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val activeIcon = when (mode) {
                                        StoryMode.IMAGE -> Icons.Outlined.Image
                                        StoryMode.VIDEO -> Icons.Outlined.Videocam
                                        else -> Icons.Outlined.TextFields
                                    }
                                    Icon(
                                        activeIcon,
                                        null,
                                        tint = TextGrayStory,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(Modifier.height(16.dp))
                                val activeText = when (mode) {
                                    StoryMode.IMAGE ->"una imagen"
                                    StoryMode.VIDEO ->"un vídeo"
                                    else ->"un fondo"
                                }
                                Text(
                                    "Toca para elegir $activeText",
                                    color = TextGrayStory,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Texto sobre el medio (solo imagen/vídeo)
                if (mode != StoryMode.TEXT) {

                    SettingRowWithSwitchStory(
                        "Texto sobre el medio",
                        "(opcional)",
                        overlayTextEnabled,
                        { overlayTextEnabled = it }
                    )

                    if (overlayTextEnabled) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = textContent,
                            onValueChange = { if (it.length <= 120) textContent = it },
                            placeholder = { Text("Texto que irá sobre el medio…", color = TextGrayStory) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = IosSettingsColors.label,
                                unfocusedTextColor = IosSettingsColors.label,
                                focusedBorderColor = BrandGreenStory,
                                unfocusedBorderColor = TextGrayStory.copy(alpha = .4f)
                            ),
                            maxLines = 2
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                }

                // Audio de fondo (opcional)
                SettingRowWithSwitchStory(
                    "Audio de fondo",
                    "(opcional)",
                    audioEnabled,
                    { audioEnabled = it }
                )

                if (audioEnabled) {
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                        items(FREE_MUSIC) { item ->
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(IconBoxBgStory)
                                    .clickable {
                                        audioUri = null; audioName = item.name; audioUrl = item.url
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(14.dp), tint = IosSettingsColors.label
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(item.name, color = IosSettingsColors.label, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            audioName ?: "Ningún audio seleccionado",
                            color = if (audioName == null) TextGrayStory else IosSettingsColors.label,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {

                            if (audioName != null) {
                                IconButton(onClick = { togglePreviewAudio() }) {
                                    Icon(
                                        if (audioPlaying) Icons.Rounded.Stop else Icons.Rounded.Audiotrack,
                                        contentDescription = if (audioPlaying) "Parar" else "Reproducir",
                                        tint = if (audioPlaying) BrandGreenStory else IosSettingsColors.label
                                    )
                                }
                            }
                            Button(
                                onClick = { pickAudio.launch("audio/*") },
                                modifier = Modifier.height(34.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IconBoxBgStory, contentColor = IosSettingsColors.label)
                            ) {

                                if (isUploadingAudio) {
                                    CircularProgressIndicator(
                                        color = BrandGreenStory,
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                Text(if (audioName == null) "Elegir archivo" else "Cambiar", fontSize =  12.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))


                // Botón inferior de Publicar
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SegmentedBgStory)
                        .border(1.dp, BorderWhiteAlphaStory, RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !publishing && canPublish()
                        ) { uploadStory() }
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.FileUpload,
                        "Publicar",
                        tint = TextGrayStory,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Publicar historia",
                        color = IosSettingsColors.onAccent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }


        // Mock Home Indicator iOS
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .width(134.dp)
                .height(5.dp)
                .background(IosSettingsColors.separator, CircleShape)
        )
    }

    if (showOverlongClipDialog) {
        AlertDialog(
            onDismissRequest = { showOverlongClipDialog = false },
            title = { Text("Tu vídeo dura más de 2 minutos", color = IosSettingsColors.label) },
            text = {
                Text(
                    "Las historias de vídeo tienen un límite de 2 minutos. Tu vídeo se publicará " +
                        "recortado a los primeros 2 minutos; el resto no se verá. ¿Quieres continuar?",
                    color = IosSettingsColors.secondaryLabel
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmOverlongPublish() }) {
                    Text("Recortar y publicar", color = IosSettingsColors.green)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlongClipDialog = false }) {
                    Text("Cancelar", color = IosSettingsColors.secondaryLabel)
                }
            },
            containerColor = IosSettingsColors.cellElevated
        )
    }
}

@Composable
private fun SegmentedButtonStory(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) SegmentedActiveStory else Color.Transparent,
        animationSpec = tween(durationMillis = 200)
    )
    val contentColor = if (selected) IosSettingsColors.label else TextGrayStory
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingRowWithSwitchStory(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(color = IosSettingsColors.label, fontSize = 16.sp)) {
                    append("$title ")
                }
                withStyle(style = SpanStyle(color = TextGrayStory, fontSize = 14.sp)) {
                    append(subtitle)
                }
            }
        )
        IosCustomSwitchStory(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun IosCustomSwitchStory(checked: Boolean, onCheckedChange:(Boolean) -> Unit) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = tween(durationMillis = 300)
    )
    val bgColor by animateColorAsState(
        targetValue = if (checked) BrandGreenStory else IosSettingsColors.separator,
        animationSpec = tween(durationMillis = 300)
    )
    Box(
        modifier = Modifier
            .width(51.dp)
            .height(31.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            )
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(27.dp)
                .shadow(elevation = 2.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(IosSettingsColors.label)
        )
    }
}