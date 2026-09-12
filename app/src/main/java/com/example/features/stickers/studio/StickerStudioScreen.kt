package com.example.features.stickers.studio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.request.ImageRequest
import com.example.util.rememberCameraPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class StudioMode { IMAGE, TEXT, VIDEO }

private data class StudioColor(val name: String, val color: Color)

private val PANA_GREEN = Color(0xFF00A884)
private val DARK_BG = Color(0xFF0B141A)
private val PANEL_BG = Color(0xFF111B21)
private val CARD_BG = Color(0xFF1F2C34)

private val TEXT_COLORS = listOf(
    StudioColor("Blanco", Color.White),
    StudioColor("Pana", Color(0xFF00A884)),
    StudioColor("Sol", Color(0xFFFFD54F)),
    StudioColor("Coral", Color(0xFFFF5252)),
    StudioColor("Cielo", Color(0xFF64B5F6)),
    StudioColor("Uva", Color(0xFFCE93D8)),
    StudioColor("Negro", Color(0xFF111B21))
)

private val BG_COLORS = listOf(
    StudioColor("Ninguno", Color.Transparent),
    StudioColor("Pana", Color(0xFF00A884)),
    StudioColor("Oscuro", Color(0xFF111B21)),
    StudioColor("Blanco", Color.White),
    StudioColor("Atardecer", Color(0xFF4A1A6B)),
    StudioColor("Caribe", Color(0xFF005D67))
)

private data class FontStyle(val name: String, val typeface: Typeface)

private val FONT_STYLES = listOf(
    FontStyle("Negrita", Typeface.DEFAULT_BOLD),
    FontStyle("Clásica", Typeface.SERIF),
    FontStyle("Mono", Typeface.MONOSPACE),
    FontStyle("Normal", Typeface.DEFAULT)
)

/**
 * Panalink Sticker Studio: create stickers from images, text or short videos.
 * Everything renders on a transparent 512px canvas and is stored in the app
 * sticker memory, then uploaded to the CDN so any contact can receive it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerStudioScreen(
    onBack: () -> Unit,
    onStickerCreated: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(StudioMode.IMAGE) }

    // Image mode state
    var baseBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imgScale by remember { mutableStateOf(1f) }
    var imgRotation by remember { mutableStateOf(0f) }
    var imgOffset by remember { mutableStateOf(Offset.Zero) }
    var outlineEnabled by remember { mutableStateOf(true) }
    var bgIndex by remember { mutableStateOf(0) }
    var overlayText by remember { mutableStateOf("") }
    var overlayColorIndex by remember { mutableStateOf(0) }

    // Text mode state
    var textInput by remember { mutableStateOf("") }
    var textColorIndex by remember { mutableStateOf(0) }
    var fontIndex by remember { mutableStateOf(0) }
    var textBgIndex by remember { mutableStateOf(0) }

    // Video mode state
    var gifFile by remember { mutableStateOf<File?>(null) }
    var showTrimmer by remember { mutableStateOf(false) }
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var trimStartMs by remember { mutableStateOf(0L) }
    var trimEndMs by remember { mutableStateOf(5000L) }

    var emojiTag by remember { mutableStateOf("🟢") }
    var isProcessing by remember { mutableStateOf(false) }
    var processingLabel by remember { mutableStateOf("") }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            isProcessing = true
            processingLabel = "Cargando imagen…"
            baseBitmap = StickerStudioRenderer.loadBaseBitmap(context, uri)
            imgScale = 1f; imgRotation = 0f; imgOffset = Offset.Zero
            isProcessing = false
        }
    }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        showTrimmer = true
        selectedVideoUri = uri
    }

    var pendingPhotoFile by remember { mutableStateOf<File?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (!success) return@rememberLauncherForActivityResult
        val photoFile = pendingPhotoFile ?: return@rememberLauncherForActivityResult
        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
        if (bitmap != null) {
            baseBitmap = bitmap
            imgScale = 1f; imgRotation = 0f; imgOffset = Offset.Zero
        }
        photoFile.delete()
        pendingPhotoFile = null
    }

    val cameraPermissionState = rememberCameraPermissionState(
        onPermissionsGranted = {
            val tempDir = File(context.cacheDir, "sticker_studio_camera").apply { mkdirs() }
            val photoFile = File(tempDir, "photo_${System.currentTimeMillis()}.jpg")
            pendingPhotoFile = photoFile
            val photoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                photoFile
            )
            takePictureLauncher.launch(photoUri)
        },
        onPermissionDenied = {
            Toast.makeText(context, "Se requiere permiso de cámara", Toast.LENGTH_SHORT).show()
        }
    )

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val stream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(stream)
        stream?.close()
        bitmap?.let {
            baseBitmap = it
            imgScale = 1f; imgRotation = 0f; imgOffset = Offset.Zero
        }
    }

    fun canSave(): Boolean = when (mode) {
        StudioMode.IMAGE -> baseBitmap != null
        StudioMode.TEXT -> textInput.isNotBlank()
        StudioMode.VIDEO -> gifFile != null || selectedVideoUri != null
    }

    fun save() {
        if (!canSave() || isProcessing) return
        scope.launch {
            isProcessing = true
            processingLabel = "Creando tu sticker…"
            try {
                val localFile: File? = when (mode) {
                    StudioMode.IMAGE -> {
                        val bitmap = withContext(Dispatchers.Default) {
                            StickerStudioRenderer.renderImageSticker(
                                base = baseBitmap,
                                backgroundColor = BG_COLORS[bgIndex].color.takeIf { it != Color.Transparent }?.let {
                                    android.graphics.Color.argb(
                                        (it.alpha * 255).toInt(),
                                        (it.red * 255).toInt(),
                                        (it.green * 255).toInt(),
                                        (it.blue * 255).toInt()
                                    )
                                },
                                outline = outlineEnabled,
                                overlays = if (overlayText.isNotBlank()) listOf(
                                    StickerStudioRenderer.TextOverlay(
                                        text = overlayText,
                                        color = TEXT_COLORS[overlayColorIndex].color.toArgbInt(),
                                        strokeColor = android.graphics.Color.BLACK,
                                        typeface = Typeface.DEFAULT_BOLD
                                    )
                                ) else emptyList(),
                                scale = imgScale,
                                rotation = imgRotation,
                                offsetX = imgOffset.x,
                                offsetY = imgOffset.y
                            )
                        }
                        val file = StickerStudioRenderer.saveAsWebp(context, bitmap)
                        bitmap.recycle()
                        file
                    }
                    StudioMode.TEXT -> {
                        val chosen = TEXT_COLORS[textColorIndex].color
                        val stroke = if (chosen == Color(0xFF111B21)) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                        val bitmap = withContext(Dispatchers.Default) {
                            StickerStudioRenderer.renderTextSticker(
                                text = textInput.trim(),
                                textColor = chosen.toArgbInt(),
                                strokeColor = stroke,
                                typeface = FONT_STYLES[fontIndex].typeface,
                                backgroundColor = BG_COLORS[textBgIndex].color.takeIf { it != Color.Transparent }?.toArgbInt()
                            )
                        }
                        val file = StickerStudioRenderer.saveAsWebp(context, bitmap)
                        bitmap.recycle()
                        file
                    }
                    StudioMode.VIDEO -> {
                        // Use the trimmed and transcoded animated WebP
                        gifFile
                    }
                }

                if (localFile == null) {
                    isProcessing = false
                    Toast.makeText(context, "No se pudo crear el sticker", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                processingLabel = "Guardando sticker…"
                // Guardar localmente primero (offline-first)
                com.example.data.repository.StickerRepository.saveSticker(
                    context,
                    com.example.data.model.StickerResult(url = localFile.absolutePath, preview = localFile.absolutePath)
                )
                // Registrar como reciente
                com.example.data.repository.StickerRepository.addRecentSticker(
                    context,
                    com.example.data.model.StickerResult(url = localFile.absolutePath, preview = localFile.absolutePath)
                )
                // Encolar subida durable con WorkManager (offline-first, reanuda al volver la señal)
                val uploadData = StickerUploadWorker.createInputData(
                    localPath = localFile.absolutePath,
                    previewPath = localFile.absolutePath,
                    name = "Panalink Sticker",
                    emoji = emojiTag
                )
                val constraints = androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
                val workRequest = androidx.work.OneTimeWorkRequestBuilder<StickerUploadWorker>()
                    .setConstraints(constraints)
                    .setInputData(uploadData)
                    .addTag("sticker_upload")
                    .addTag("sticker_upload_${localFile.name}")
                    .setBackoffCriteria(
                        androidx.work.BackoffPolicy.EXPONENTIAL,
                        androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                        java.util.concurrent.TimeUnit.MILLISECONDS
                    )
                    .build()
                androidx.work.WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        "sticker_upload_${localFile.name}",
                        androidx.work.ExistingWorkPolicy.KEEP,
                        workRequest
                    )
                val finalUrl = localFile.absolutePath
                isProcessing = false
                Toast.makeText(context, "Sticker creado 🎉", Toast.LENGTH_SHORT).show()
                onStickerCreated(finalUrl)
            } catch (e: Exception) {
                isProcessing = false
                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Video trimmer overlay
    if (showTrimmer && selectedVideoUri != null) {
        VideoTrimmerScreen(
            videoUri = selectedVideoUri!!,
            onConfirm = { startMs, endMs ->
                trimStartMs = startMs
                trimEndMs = endMs
                showTrimmer = false
                // Pre-generate the animated sticker in background
                scope.launch {
                    isProcessing = true
                    processingLabel = "Procesando video…"
                    gifFile = StickerStudioRenderer.videoToAnimatedWebpSticker(
                        context = context,
                        uri = selectedVideoUri!!,
                        startTimeMs = startMs,
                        endTimeMs = endMs
                    )
                    isProcessing = false
                }
            },
            onDismiss = { showTrimmer = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sticker Studio", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("by Panalink", fontSize = 11.sp, color = PANA_GREEN)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
                    }
                },
                actions = {
                    if (isProcessing) {
                        CircularProgressIndicator(color = PANA_GREEN, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PANEL_BG,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = DARK_BG
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Mode selector
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StudioModeChip("Imagen", Icons.Filled.Image, mode == StudioMode.IMAGE, Modifier.weight(1f)) { mode = StudioMode.IMAGE }
                StudioModeChip("Texto", Icons.Filled.TextFields, mode == StudioMode.TEXT, Modifier.weight(1f)) { mode = StudioMode.TEXT }
                StudioModeChip("Video", Icons.Filled.Videocam, mode == StudioMode.VIDEO, Modifier.weight(1f)) { mode = StudioMode.VIDEO }
            }

            // Canvas preview
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CARD_BG),
                contentAlignment = Alignment.Center
            ) {
                TransparencyGrid(Modifier.fillMaxSize())

                when (mode) {
                    StudioMode.IMAGE -> {
                        if (baseBitmap != null) {
                            ImageStickerPreview(
                                base = baseBitmap!!,
                                backgroundColor = BG_COLORS[bgIndex].color,
                                outline = outlineEnabled,
                                overlayText = overlayText,
                                overlayColor = TEXT_COLORS[overlayColorIndex].color,
                                scale = imgScale,
                                rotation = imgRotation,
                                offset = imgOffset,
                                onTransform = { pan, zoom, rot ->
                                    imgScale = (imgScale * zoom).coerceIn(0.4f, 4f)
                                    imgRotation += rot
                                    imgOffset += pan
                                }
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Convierte tu imagen en sticker", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text("Galería o cámara · pellizca para ajustar", color = Color(0xFF8596A0), fontSize = 12.sp)
                                Spacer(Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                        colors = ButtonDefaults.buttonColors(containerColor = PANA_GREEN)
                                    ) {
                                        Icon(Icons.Filled.Image, null, tint = Color.Black)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Galería", color = Color.Black)
                                    }
                                    Button(
                                        onClick = { cameraPermissionState.requestPermissions() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A3942))
                                    ) {
                                        Icon(Icons.Filled.CameraAlt, null, tint = Color.White)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Cámara", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                    StudioMode.TEXT -> {
                        TextStickerPreview(
                            text = textInput,
                            textColor = TEXT_COLORS[textColorIndex].color,
                            typeface = FONT_STYLES[fontIndex].typeface,
                            backgroundColor = BG_COLORS[textBgIndex].color
                        )
                    }
                    StudioMode.VIDEO -> {
                        if (gifFile != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(gifFile)
                                    .decoderFactory(GifDecoder.Factory())
                                    .build(),
                                contentDescription = "Sticker animado",
                                modifier = Modifier.size(220.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Convierte tu video en sticker animado", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text("Máximo 5 segundos · recorta y convierte a WebP animado", color = Color(0xFF8596A0), fontSize = 12.sp)
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                                    colors = ButtonDefaults.buttonColors(containerColor = PANA_GREEN)
                                ) {
                                    Icon(Icons.Filled.Videocam, null, tint = Color.Black)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Elegir video", color = Color.Black)
                                }
                            }
                        }
                    }
                }

                if (isProcessing) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = PANA_GREEN)
                            Spacer(Modifier.height(10.dp))
                            Text(processingLabel, color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Tools + save
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                when (mode) {
                    StudioMode.IMAGE -> if (baseBitmap != null) {
                        ToolLabel("Borde blanco estilo WhatsApp")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = outlineEnabled,
                                onCheckedChange = { outlineEnabled = it },
                                colors = SwitchDefaults.colors(checkedTrackColor = PANA_GREEN)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (outlineEnabled) "Activado" else "Desactivado", color = Color(0xFF8596A0), fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        ToolLabel("Fondo")
                        ColorRow(BG_COLORS, bgIndex) { bgIndex = it }
                        Spacer(Modifier.height(6.dp))
                        ToolLabel("Texto sobre el sticker (opcional)")
                        OutlinedTextField(
                            value = overlayText,
                            onValueChange = { if (it.length <= 30) overlayText = it },
                            placeholder = { Text("Ej: EPALE", color = Color(0xFF8596A0)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = studioTextFieldColors()
                        )
                        if (overlayText.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            ColorRow(TEXT_COLORS, overlayColorIndex) { overlayColorIndex = it }
                        }
                    }
                    StudioMode.TEXT -> {
                        ToolLabel("Escribe tu sticker")
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { if (it.length <= 40) textInput = it },
                            placeholder = { Text("Ej: CHAMO 😎", color = Color(0xFF8596A0)) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2,
                            colors = studioTextFieldColors()
                        )
                        Spacer(Modifier.height(6.dp))
                        ToolLabel("Estilo de letra")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(FONT_STYLES.size) { i ->
                                val selected = fontIndex == i
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) PANA_GREEN else Color(0xFF2A3942))
                                        .clickable { fontIndex = i }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(FONT_STYLES[i].name, color = if (selected) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        ToolLabel("Color")
                        ColorRow(TEXT_COLORS, textColorIndex) { textColorIndex = it }
                        Spacer(Modifier.height(6.dp))
                        ToolLabel("Fondo")
                        ColorRow(BG_COLORS, textBgIndex) { textBgIndex = it }
                    }
                    StudioMode.VIDEO -> if (gifFile != null) {
                        TextButton(onClick = { gifFile = null }) {
                            Text("Elegir otro video", color = PANA_GREEN)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                ToolLabel("Emoji asociado")
                OutlinedTextField(
                    value = emojiTag,
                    onValueChange = { emojiTag = it.take(4) },
                    singleLine = true,
                    modifier = Modifier.width(110.dp),
                    colors = studioTextFieldColors()
                )

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { save() },
                    enabled = canSave() && !isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PANA_GREEN)
                ) {
                    Icon(Icons.Filled.Check, null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("Guardar sticker", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun Color.toArgbInt(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
)

@Composable
private fun studioTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = PANA_GREEN,
    unfocusedBorderColor = Color(0xFF2A3942)
)

@Composable
private fun ToolLabel(text: String) {
    Text(text, color = Color(0xFF8596A0), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ColorRow(colors: List<StudioColor>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(colors.size) { i ->
            val c = colors[i]
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (c.color == Color.Transparent) Color(0xFF2A3942) else c.color)
                    .border(
                        width = if (selected == i) 3.dp else 1.dp,
                        color = if (selected == i) PANA_GREEN else Color(0xFF8596A0),
                        shape = CircleShape
                    )
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                if (c.color == Color.Transparent) {
                    Text("∅", color = Color(0xFF8596A0), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun StudioModeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(if (selected) PANA_GREEN else CARD_BG)
            .padding(vertical = 10.dp)
    ) {
        Icon(icon, null, tint = if (selected) Color.Black else Color.White, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = if (selected) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Checkerboard that communicates canvas transparency, like pro editors. */
@Composable
private fun TransparencyGrid(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cell = 20.dp.toPx()
        val paintColor = Color.White.copy(alpha = 0.05f)
        var row = 0
        var y = 0f
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else cell
            while (x < size.width) {
                drawRect(paintColor, topLeft = Offset(x, y), size = androidx.compose.ui.geometry.Size(cell, cell))
                x += cell * 2
            }
            y += cell
            row++
        }
    }
}

@Composable
private fun ImageStickerPreview(
    base: Bitmap,
    backgroundColor: Color,
    outline: Boolean,
    overlayText: String,
    overlayColor: Color,
    scale: Float,
    rotation: Float,
    offset: Offset,
    onTransform: (pan: Offset, zoom: Float, rotation: Float) -> Unit
) {
    // Live preview mirrors the final render in StickerStudioRenderer.
    val preview = remember(base, backgroundColor, outline, overlayText, overlayColor, scale, rotation, offset) {
        StickerStudioRenderer.renderImageSticker(
            base = base,
            backgroundColor = backgroundColor.takeIf { it != Color.Transparent }?.toArgbInt(),
            outline = outline,
            overlays = if (overlayText.isNotBlank()) listOf(
                StickerStudioRenderer.TextOverlay(
                    text = overlayText,
                    color = overlayColor.toArgbInt(),
                    strokeColor = android.graphics.Color.BLACK,
                    typeface = Typeface.DEFAULT_BOLD
                )
            ) else emptyList(),
            scale = scale,
            rotation = rotation,
            offsetX = offset.x,
            offsetY = offset.y
        ).asImageBitmap()
    }
    androidx.compose.foundation.Image(
        bitmap = preview,
        contentDescription = "Vista previa del sticker",
        modifier = Modifier
            .size(240.dp)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rot -> onTransform(pan, zoom, rot) }
            },
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun TextStickerPreview(
    text: String,
    textColor: Color,
    typeface: Typeface,
    backgroundColor: Color
) {
    val preview = remember(text, textColor, typeface, backgroundColor) {
        val stroke = if (textColor == Color(0xFF111B21)) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        StickerStudioRenderer.renderTextSticker(
            text = text.ifBlank { "Aa" },
            textColor = textColor.toArgbInt(),
            strokeColor = stroke,
            typeface = typeface,
            backgroundColor = backgroundColor.takeIf { it != Color.Transparent }?.toArgbInt()
        ).asImageBitmap()
    }
    androidx.compose.foundation.Image(
        bitmap = preview,
        contentDescription = "Vista previa del sticker de texto",
        modifier = Modifier.size(240.dp),
        contentScale = ContentScale.Fit
    )
}
