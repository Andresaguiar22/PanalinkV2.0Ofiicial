package com.example.media.player.ui

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.example.media.audio.AudioTrackEntity
import com.example.media.audio.RepeatMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Poweramp-style full-screen music player.
 * Dynamic gradient extracted from the album art, glassy controls, sleep timer and EQ presets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen(
    viewModel: PlayerViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.playerState.collectAsState()
    val sleepTimerMs by viewModel.sleepTimerRemainingMs.collectAsState()
    val eqPreset by viewModel.eqPreset.collectAsState()
    val track = state.currentTrack
    var showQueue by remember { mutableStateOf(false) }
    var showEqSheet by remember { mutableStateOf(false) }
    var showSleepSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Dominant color extracted from the album art — drives the whole theme
    var dominantColor by remember(track?.id) { mutableStateOf(Color(0xFF1E293B)) }
    LaunchedEffect(track?.coverUri) {
        val cover = track?.coverUri ?: return@LaunchedEffect
        val color = withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context).data(cover).allowHardware(false).build()
                val drawable = context.imageLoader.execute(request).drawable
                val bitmap = (drawable as? BitmapDrawable)?.bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                bitmap?.let { bmp ->
                    val palette = Palette.from(bmp).generate()
                    val rgb = palette.getDominantColor(palette.getVibrantColor(0xFF1E293B.toInt()))
                    Color(rgb)
                }
            } catch (_: Exception) { null }
        }
        if (color != null) dominantColor = color
    }

    val animatedTop by androidx.compose.animation.animateColorAsState(
        targetValue = dominantColor.copy(alpha = 0.85f),
        animationSpec = tween(900),
        label = "topColor"
    )
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(animatedTop, Color(0xFF0B0F19))
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "REPRODUCIENDO",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        letterSpacing = 3.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Cerrar", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                },
                actions = {
                    IconButton(onClick = { showSleepSheet = true }) {
                        Icon(
                            Icons.Rounded.Bedtime,
                            contentDescription = "Temporizador",
                            tint = if (sleepTimerMs != null) Color(0xFF38BDF8) else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
                .padding(padding)
        ) {
            // Blurred backdrop art for depth
            AsyncImage(
                model = track?.coverUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(60.dp)
                    .scale(1.4f),
                alpha = 0.35f
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xFF0B0F19).copy(alpha = 0.9f))
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { track?.let { viewModel.toggleFavorite(it) } }
                        )
                    }
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            if (delta > 50) viewModel.previousTrack()
                            else if (delta < -50) viewModel.nextTrack()
                        }
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Album art with subtle pulse while playing
                val artScale by animateFloatAsState(
                    targetValue = if (state.isPlaying) 1f else 0.92f,
                    animationSpec = tween(500),
                    label = "artScale"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .aspectRatio(1f)
                            .scale(artScale)
                            .shadow(32.dp, RoundedCornerShape(24.dp))
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF1E293B))
                    ) {
                        if (track?.coverUri != null) {
                            AsyncImage(
                                model = track.coverUri,
                                contentDescription = "Carátula",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF334155), Color(0xFF1E293B))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(96.dp)
                                )
                            }
                        }
                    }
                }

                // Track info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        track?.title ?: "Sin título",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${track?.artist ?: "Artista desconocido"}  •  ${track?.album ?: "Sencillo"}",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Progress
                PlayerProgressBar(
                    currentPositionMs = state.currentPositionMs,
                    durationMs = state.durationMs,
                    onSeek = { viewModel.seekTo(it) }
                )

                // Sleep timer chip
                sleepTimerMs?.let { remaining ->
                    Text(
                        "⏱ Apagado en ${remaining / 60000}:${"%02d".format((remaining / 1000) % 60)}",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF38BDF8).copy(alpha = 0.12f))
                            .clickable { showSleepSheet = true }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                // Main controls
                PlayerControls(
                    isPlaying = state.isPlaying,
                    isShuffle = state.isShuffle,
                    repeatMode = state.repeatMode,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onNext = { viewModel.nextTrack() },
                    onPrevious = { viewModel.previousTrack() },
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onToggleRepeat = { viewModel.toggleRepeat() }
                )

                // Bottom action row: favorite / EQ / speed / queue
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { track?.let { viewModel.toggleFavorite(it) } }) {
                        Icon(
                            if (track?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorito",
                            tint = if (track?.isFavorite == true) Color(0xFFF43F5E) else Color.White.copy(alpha = 0.8f)
                        )
                    }

                    IconButton(onClick = { showEqSheet = true }) {
                        Icon(
                            Icons.Rounded.Equalizer,
                            contentDescription = "Ecualizador",
                            tint = if (eqPreset != "Normal") Color(0xFF38BDF8) else Color.White.copy(alpha = 0.8f)
                        )
                    }

                    TextButton(onClick = {
                        val nextSpeed = if (state.playbackSpeed >= 2f) 0.5f else state.playbackSpeed + 0.5f
                        viewModel.setPlaybackSpeed(nextSpeed)
                    }) {
                        Text(
                            "${state.playbackSpeed}x",
                            color = if (state.playbackSpeed != 1f) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = { showQueue = true }) {
                        Icon(Icons.Rounded.QueueMusic, contentDescription = "Cola", tint = Color.White.copy(alpha = 0.8f))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            containerColor = Color(0xFF111827),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
        ) {
            PlayerQueueSheet(
                queue = state.queue,
                currentIndex = state.currentIndex,
                onTrackClick = { viewModel.playFromQueue(it) },
                onRemoveTrack = { index ->
                    val trackToRemove = state.queue.getOrNull(index)
                    trackToRemove?.let { viewModel.removeFromQueue(it.id) }
                },
                onReorderTrack = { _, _ -> },
                onClearQueue = { viewModel.clearQueue() }
            )
        }
    }

    if (showEqSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEqSheet = false },
            containerColor = Color(0xFF111827),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
        ) {
            EqPresetSheet(
                currentPreset = eqPreset,
                onSelect = {
                    viewModel.setEqPreset(it)
                    showEqSheet = false
                }
            )
        }
    }

    if (showSleepSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSleepSheet = false },
            containerColor = Color(0xFF111827),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
        ) {
            SleepTimerSheet(
                isActive = sleepTimerMs != null,
                onSelect = { minutes ->
                    viewModel.setSleepTimer(minutes)
                    showSleepSheet = false
                }
            )
        }
    }
}

@Composable
private fun EqPresetSheet(currentPreset: String, onSelect: (String) -> Unit) {
    val presets = listOf("Normal", "Rock", "Pop", "Jazz", "Clásica", "Bass Boost", "Vocal", "Electrónica")
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
        Text("Ecualizador", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        presets.chunked(4).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { preset ->
                    val selected = preset == currentPreset
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Color(0xFF38BDF8) else Color(0xFF1E293B))
                            .clickable { onSelect(preset) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            preset,
                            color = if (selected) Color(0xFF0B0F19) else Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun SleepTimerSheet(isActive: Boolean, onSelect: (Int?) -> Unit) {
    val options = listOf(5, 10, 15, 30, 45, 60)
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
        Text("Temporizador de apagado", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        options.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { minutes ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E293B))
                            .clickable { onSelect(minutes) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$minutes min", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (isActive) {
            OutlinedButton(
                onClick = { onSelect(null) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancelar temporizador", color = Color(0xFFF43F5E))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
