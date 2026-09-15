package com.example.panatv

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.core.*

// ── Xuper TV style palette ──────────────────────────────────────────────
private val TvBg = Color(0xFF121212)
private val TvCard = Color(0xFF1E1E1E)
private val TvCardAlt = Color(0xFF232323)
private val TvAccent = Color(0xFFFF6F00)
private val TvAccentSoft = Color(0x33FF6F00)
private val TvTextSecondary = Color(0xFFB3B3B3)

internal fun tvCategoryLabel(category: String): String = when (category) {
    "movies" -> "Películas"
    "entertainment" -> "Entretenimiento"
    "series" -> "Series"
    "sports" -> "Deportes"
    "news" -> "Noticias"
    "kids" -> "Infantil"
    "music" -> "Música"
    "documentary" -> "Documentales"
    "general" -> "General"
    "comedy" -> "Comedia"
    "lifestyle" -> "Estilo de vida"
    "culture" -> "Cultura"
    "family" -> "Familia"
    "animation" -> "Animación"
    "education" -> "Educación"
    "travel" -> "Viajes"
    "cooking" -> "Cocina"
    "science" -> "Ciencia"
    "religious" -> "Religiosos"
    "shop" -> "Tienda"
    "business" -> "Negocios"
    "classic" -> "Clásicos"
    "outdoor" -> "Aire libre"
    "relax" -> "Relax"
    "weather" -> "Clima"
    "legislative" -> "Legislativo"
    else -> category.replaceFirstChar { it.uppercase() }
}

/** Nombre amigable para códigos ISO-639 de idioma (iptv-org). */
internal fun languageLabel(code: String): String = when (code.trim().lowercase()) {
    "spa", "es" -> "Español"
    "eng", "en" -> "Inglés"
    "por", "pt" -> "Portugués"
    "fra", "fr" -> "Francés"
    "deu", "de" -> "Alemán"
    "ita", "it" -> "Italiano"
    "rus", "ru" -> "Ruso"
    "jpn", "ja" -> "Japonés"
    "zho", "zh" -> "Chino"
    "kor", "ko" -> "Coreano"
    "ara", "ar" -> "Árabe"
    "hin", "hi" -> "Hindi"
    "tur", "tr" -> "Turco"
    "nld", "nl" -> "Neerlandés"
    "pol", "pl" -> "Polaco"
    "cat", "ca" -> "Catalán"
    "eus", "eu" -> "Euskera"
    "glg", "gl" -> "Gallego"
    else -> code.replaceFirstChar { it.uppercase() }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanaTVScreen(viewModel: PanaTVViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val channels by viewModel.channels.collectAsState()
    val currentChannel by viewModel.currentChannel.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCountry by viewModel.selectedCountry.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val availableCountries by viewModel.availableCountries.collectAsState()
    val availableCategories by viewModel.availableCategories.collectAsState()
    val debugMessage by viewModel.debugMessage.collectAsState()
    val crashTrace by viewModel.crashTrace.collectAsState()
    val showOnlyFavorites by viewModel.showOnlyFavorites.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var isFullscreen by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf("") }
    var showCountryDropdown by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isVideoRendering by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var isPlayingState by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var lockTapVisible by remember { mutableStateOf(false) }
    var showChannelList by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showChannelInput by remember { mutableStateOf(false) }
    var channelNumberInput by remember { mutableStateOf("") }
    var switchingChannel by remember { mutableStateOf(false) }
    var switchingChannelName by remember { mutableStateOf("") }
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Diagnostics: Show crash trace if exists
    if (crashTrace.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.clearCrashTrace() },
            title = { Text("CRASH DIAGNOSTICS", color = Color.Red, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(crashTrace, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.clearCrashTrace() }) {
                    Text("BORRAR Y CONTINUAR")
                }
            },
            containerColor = TvCard,
            textContentColor = Color.White
        )
    }

    // Channel number input dialog
    if (showChannelInput) {
        AlertDialog(
            onDismissRequest = { showChannelInput = false },
            title = { Text("Número de canal", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = channelNumberInput,
                        onValueChange = { if (it.length <= 3 && it.all { c -> c.isDigit() }) channelNumberInput = it },
                        placeholder = { Text("Ej: 101", color = TvTextSecondary) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = TvAccent,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    if (channelNumberInput.isNotBlank()) {
                        val channelNum = channelNumberInput.toIntOrNull()
                        val foundChannel = channels.find { it.id.equals(channelNum.toString(), ignoreCase = true) }
                        Text(
                            text = foundChannel?.let { "Canal: ${it.name}" } ?: "Canal no encontrado",
                            color = if (foundChannel != null) Color.Green else Color.Red,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val channelNum = channelNumberInput.toIntOrNull()
                    val foundChannel = channels.find { it.id.equals(channelNum.toString(), ignoreCase = true) }
                    if (foundChannel != null) {
                        switchingChannelName = foundChannel.name
                        switchingChannel = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.selectChannel(foundChannel)
                    }
                    showChannelInput = false
                    channelNumberInput = ""
                }) {
                    Text("Ir al canal", color = TvAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showChannelInput = false; channelNumberInput = "" }) {
                    Text("Cancelar", color = TvTextSecondary)
                }
            },
            containerColor = TvCard,
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    // Single ExoPlayer source of truth: AppFloatingPlayerManager.
    // PanaTV acquires/reuses the shared player — never creates an orphan.
    // When currentChannel == null, no player is needed (placeholder UI shown instead).
    val exoPlayer = remember(currentChannel) {
        val channel = currentChannel
        if (channel != null) {
            com.example.util.AppFloatingPlayerManager.acquirePlayer(
                context = context,
                id = channel.id,
                url = channel.streamUrl,
                title = channel.name,
                type = "panatv",
                userAgent = channel.userAgent,
                referrer = channel.referrer
            )
        } else {
            null
        }
    }

    // Single listener per player instance. Re-attached only when the player
    // reference changes (channel switch causes acquirePlayer to return the same
    // reused player, so the listener key stays stable and we avoid stacking
    // duplicate listeners that caused stale callbacks after fast channel switches).
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playerError = error.message ?: "Error desconocido"
                isBuffering = false
            }
            override fun onRenderedFirstFrame() {
                isVideoRendering = true
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
                if (isPlaying) {
                    isVideoRendering = true
                    isBuffering = false
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                currentPosition = newPosition.positionMs
            }
        }
        exoPlayer?.addListener(listener)
        exoPlayer?.volume = if (isMuted) 0f else 1f
        isPlayingState = exoPlayer?.isPlaying ?: false
        onDispose { exoPlayer?.removeListener(listener) }
    }

    // Position/duration polling on the SAME player instance. Keyed on exoPlayer
    // so the coroutine is cancelled/restarted only when the player changes.
    LaunchedEffect(exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        while (true) {
            currentPosition = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            delay(1000)
        }
    }

    LaunchedEffect(isMuted, exoPlayer) {
        exoPlayer?.volume = if (isMuted) 0f else 1f
    }

    var activePlayerView by remember { mutableStateOf<PlayerView?>(null) }

    val rebindPlayer: (PlayerView) -> Unit = remember {
        { playerView ->
            val player = exoPlayer ?: return@remember
            playerView.player = null
            playerView.player = player
            val surfaceView = playerView.videoSurfaceView as? android.view.SurfaceView
            if (surfaceView != null) {
                player.setVideoSurfaceView(surfaceView)
            } else {
                val textureView = playerView.videoSurfaceView as? android.view.TextureView
                if (textureView != null) {
                    player.setVideoTextureView(textureView)
                }
            }
            playerView.invalidate()
            playerView.requestLayout()
        }
    }

    LaunchedEffect(isFullscreen) {
        if (!isFullscreen) {
            isLocked = false
            showChannelList = false
        }
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        if (isFullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        delay(100)
        activePlayerView?.let { rebindPlayer(it) }
    }

    // Auto-hide fullscreen controls after 3s (never while locked or browsing channels)
    LaunchedEffect(controlsVisible, isFullscreen, isLocked, showChannelList) {
        if (isFullscreen && controlsVisible && !isLocked && !showChannelList) {
            delay(3000)
            controlsVisible = false
        }
    }

    // Hide switching channel overlay after delay
    LaunchedEffect(switchingChannel) {
        if (switchingChannel) {
            delay(2000)
            switchingChannel = false
        }
    }

    // Lock affordance: tapping the screen while locked reveals the unlock button briefly
    LaunchedEffect(isLocked, lockTapVisible) {
        if (isLocked && lockTapVisible) {
            delay(3000)
            lockTapVisible = false
        }
    }

    // CHANNEL SWITCHING IS HANDLED IN acquirePlayer: single prepare path.
    // The old LaunchedEffect(currentChannel) that did stop() → clearMediaItems()
    // → setMediaSource() → prepare() has been removed to eliminate double preparation.
    // acquirePlayer() already calls setMediaItem + prepare with the correct
    // userAgent/referrer headers via the shared HTTP factory.

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer?.pause()
                Lifecycle.Event.ON_STOP -> {
                    // Backgrounded (HOME, alt-TAB, another Activity over PanaTV): pause
                    // immediately so no audio leaks into the background. The player
                    // instance is kept alive (not released) so returning here is cheap;
                    // it is fully released in onDispose below when the screen is actually
                    // left/destroyed.
                    exoPlayer?.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Do NOT auto-play on resume: prevents ghost playback when the user
                    // returns to PanaTV. Only rebind the surface so an in-flight item
                    // keeps rendering if the player still exists.
                    activePlayerView?.let { rebindPlayer(it) }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // No floating bubble: leaving PanaTV always stops and releases the player.
            com.example.util.AppFloatingPlayerManager.releasePlayer()
        }
    }

    BackHandler(enabled = isFullscreen) {
        when {
            showChannelList -> showChannelList = false
            isLocked -> {}
            else -> isFullscreen = false
        }
    }

    val cycleResizeMode: () -> Unit = {
        resizeMode = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    val resizeModeLabel = when (resizeMode) {
        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "LLENAR"
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "ZOOM"
        else -> "AJUSTAR"
    }

    val toggleOrientation: () -> Unit = {
        val activity = context as? Activity
        activity?.let {
            val isLandscape = it.resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
            it.requestedOrientation = if (isLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
    }

    Scaffold(containerColor = TvBg) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // ── Brand header ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(TvAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.LiveTv,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Pana",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "TV",
                        color = TvAccent,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(TvCard)
                                .clickable { showCountryDropdown = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null, tint = TvAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                selectedCountry.ifEmpty { "Todos" },
                                color = TvTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                    IconButton(onClick = { showChannelInput = true }) {
                        Icon(Icons.Default.Numbers, contentDescription = "Número de canal", tint = TvTextSecondary, modifier = Modifier.size(18.dp))
                    }
                        DropdownMenu(
                            expanded = showCountryDropdown,
                            onDismissRequest = { showCountryDropdown = false },
                            modifier = Modifier.background(TvCard)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Todos los países", color = Color.White) },
                                onClick = {
                                    viewModel.updateSelectedCountry("")
                                    showCountryDropdown = false
                                }
                            )
                            availableCountries.forEach { country ->
                                DropdownMenuItem(
                                    text = { Text(country, color = Color.White) },
                                    onClick = {
                                        viewModel.updateSelectedCountry(country)
                                        showCountryDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // ── Search bar ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(48.dp)
                        .background(TvCard, RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = TvTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                            modifier = Modifier.fillMaxWidth(),
                            cursorBrush = SolidColor(TvAccent),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text("Buscar canales por nombre o país...", color = TvTextSecondary, fontSize = 15.sp)
                                }
                                innerTextField()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── Category chips ──
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        CategoryChip(
                            label = "Todos",
                            selected = selectedCategory.isEmpty() && !showOnlyFavorites,
                            onClick = {
                                viewModel.updateSelectedCategory("")
                                viewModel.setShowOnlyFavorites(false)
                            }
                        )
                    }
                    item {
                        CategoryChip(
                            label = "❤ Favoritos",
                            selected = showOnlyFavorites,
                            onClick = {
                                viewModel.updateSelectedCategory("")
                                viewModel.setShowOnlyFavorites(true)
                            }
                        )
                    }
                    items(availableCategories) { cat ->
                        CategoryChip(
                            label = tvCategoryLabel(cat),
                            selected = selectedCategory == cat && !showOnlyFavorites,
                            onClick = {
                                viewModel.updateSelectedCategory(cat)
                                viewModel.setShowOnlyFavorites(false)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── Hero player (16:9) ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .aspectRatio(16f / 9f)
                        .background(Color.Black, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectTransformGestures(
                                onGesture = { _, pan, _, _ ->
                                    val threshold = 100f
                                    if (pan.x > threshold && !showChannelList) {
                                        val currentIndex = channels.indexOfFirst { it.id == currentChannel?.id }
                                        val prevIndex = (currentIndex - 1).coerceAtLeast(0)
                                        if (prevIndex != currentIndex) {
                                            switchingChannelName = channels[prevIndex].name
                                            switchingChannel = true
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.selectChannel(channels[prevIndex])
                                        }
                                    } else if (pan.x < -threshold && !showChannelList) {
                                        val currentIndex = channels.indexOfFirst { it.id == currentChannel?.id }
                                        val nextIndex = (currentIndex + 1).coerceAtMost(channels.lastIndex)
                                        if (nextIndex != currentIndex) {
                                            switchingChannelName = channels[nextIndex].name
                                            switchingChannel = true
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.selectChannel(channels[nextIndex])
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    if (currentChannel != null) {
                        if (!isFullscreen) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = exoPlayer
                                        useController = false
                                        layoutParams = FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    }
                                },
                                update = { playerView ->
                                    playerView.resizeMode = resizeMode
                                    activePlayerView = playerView
                                    rebindPlayer(playerView)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        if ((!isVideoRendering || isBuffering) && exoPlayer != null) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = TvAccent)
                            }
                        }

                        // Top overlay: channel name + LIVE badge + EPG
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.35f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFE53935))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("EN VIVO", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                currentChannel?.name ?: "",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!currentChannel?.currentProgram.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "· ${currentChannel!!.currentProgram!!.take(20)}",
                                    color = TvAccent,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Bottom mini controls
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.35f))
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    val p = exoPlayer
                                    if (p?.isPlaying == true) p.pause() else p?.play()
                                }) {
                                    Icon(
                                        if (isPlayingState) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                                IconButton(onClick = { isMuted = !isMuted }) {
                                    Icon(
                                        if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!currentChannel?.currentProgram.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = TvAccent.copy(alpha = 0.85f),
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Text(
                                            text = currentChannel!!.currentProgram!!.take(18),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    resizeModeLabel,
                                    color = TvAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { cycleResizeMode() }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                                    IconButton(onClick = { isFullscreen = true }) {
                                        Icon(Icons.Default.Fullscreen, contentDescription = "Pantalla completa", tint = Color.White)
                                    }
                                    // PiP entry removed for PanaTV: leaving the screen must stop
                                    // playback outright (see lifecycle). Reels PiP is unaffected.
                            }
                        }

                        // EPG progress bar under player
                        if (!currentChannel?.currentProgram.isNullOrBlank() && currentChannel!!.programProgress > 0f) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 52.dp)
                                    .height(3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.White.copy(alpha = 0.2f))
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(currentChannel!!.programProgress.coerceIn(0f, 1f))
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(TvAccent)
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.LiveTv,
                                contentDescription = null,
                                tint = TvAccent.copy(alpha = 0.4f),
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Toca un canal para empezar a ver",
                                color = TvTextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Channel switching transition overlay
                    AnimatedVisibility(
                        visible = switchingChannel,
                        enter = fadeIn(animationSpec = tween(300)),
                        exit = fadeOut(animationSpec = tween(300)),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = TvAccent,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Cambiando a $switchingChannelName...",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Channel grid ──
                // Lays out BELOW the hero player: the player keeps its intrinsic 16:9
                // aspect height and the grid fills the remaining (scrollable) space,
                // so it never overlaps the player (Xuper-TV layout).
                if (channels.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (debugMessage.isNotEmpty() && channels.isEmpty()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = TvAccent)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(debugMessage, color = TvTextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                            }
                        } else if (channels.isEmpty() && isLoading) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 130.dp),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(12) {
                                    ChannelCardSkeleton()
                                }
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.TvOff, contentDescription = null, tint = TvTextSecondary, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No se encontraron canales", color = TvTextSecondary, fontSize = 15.sp)
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 130.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(channels, key = { it.id }) { channel ->
                            ChannelCard(
                                channel = channel,
                                isSelected = currentChannel?.id == channel.id,
                                isFavorite = favorites.contains(channel.id),
                                onToggleFavorite = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleFavorite(channel.id)
                                },
                                onClick = {
                                    if (currentChannel?.id != channel.id) {
                                        switchingChannelName = channel.name
                                        switchingChannel = true
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.selectChannel(channel)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // ── Fullscreen player overlay ──
            AnimatedVisibility(
                visible = isFullscreen,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            if (isLocked) {
                                lockTapVisible = true
                            } else {
                                controlsVisible = !controlsVisible
                                if (!controlsVisible) showChannelList = false
                            }
                        }
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { playerView ->
                            playerView.resizeMode = resizeMode
                            activePlayerView = playerView
                            rebindPlayer(playerView)
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                        if ((!isVideoRendering || isBuffering) && currentChannel != null) {
                            if (isBuffering && !playerError.isBlank()) {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.WifiOff,
                                            contentDescription = null,
                                            tint = Color(0xFFEF5350),
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Señal perdida",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Surface(
                                            onClick = {
                                                playerError = ""
                                                isBuffering = true
                                                viewModel.selectChannel(currentChannel!!)
                                            },
                                            shape = RoundedCornerShape(20.dp),
                                            color = TvAccent
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                Text("Reintentar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            } else if (isBuffering) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = TvAccent,
                                        modifier = Modifier.size(36.dp),
                                        strokeWidth = 3.dp
                                    )
                                }
                            }
                        }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = controlsVisible && !isLocked,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Top bar
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { isFullscreen = false }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFE53935))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("EN VIVO", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    currentChannel?.name ?: "",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (!currentChannel?.currentProgram.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = TvAccentSoft,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    ) {
                                        Text(
                                            text = "Ahora: ${currentChannel!!.currentProgram!!.take(20)}",
                                            color = TvAccent,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                // Channel list toggle (stays in fullscreen)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (showChannelList) TvAccentSoft else Color.White.copy(alpha = 0.12f))
                                        .clickable { showChannelList = !showChannelList }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.List,
                                        contentDescription = null,
                                        tint = if (showChannelList) TvAccent else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Canales",
                                        color = if (showChannelList) TvAccent else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Bottom controls
                            Column(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                                // EPG progress bar (if available)
                                if (!currentChannel?.currentProgram.isNullOrBlank() && currentChannel!!.programProgress > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp)
                                            .height(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Color.White.copy(alpha = 0.2f))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(currentChannel!!.programProgress.coerceIn(0f, 1f))
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(TvAccent)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                }

                                // Progress bar scrubber: only for seekable/DVR windows;
                                // suppress the VOD-style slider on non-seekable live streams.
                                if (duration > 0L && exoPlayer?.isCurrentWindowSeekable == true) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp)
                                            .height(32.dp)
                                    ) {
                                        Slider(
                                            value = currentPosition.toFloat(),
                                            onValueChange = { newValue ->
                                                currentPosition = newValue.toLong()
                                                exoPlayer?.seekTo(currentPosition)
                                            },
                                            valueRange = 0f..duration.toFloat(),
                                            colors = SliderDefaults.colors(
                                                thumbColor = TvAccent,
                                                activeTrackColor = TvAccent,
                                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(20.dp)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = formatMmSs(currentPosition),
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = formatMmSs(duration),
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        val p = exoPlayer
                                    if (p?.isPlaying == true) p.pause() else p?.play()
                                    }) {
                                        Icon(
                                            if (isPlayingState) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                    IconButton(onClick = { isMuted = !isMuted }) {
                                        Icon(
                                            if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    }
                                    // Lock screen: hides controls and disables touch until unlocked
                                    IconButton(onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isLocked = true
                                        showChannelList = false
                                        controlsVisible = false
                                        lockTapVisible = false
                                    }) {
                                        Icon(Icons.Default.Lock, contentDescription = "Bloquear pantalla", tint = Color.White)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Zoom / fill modes
                                    Text(
                                        resizeModeLabel,
                                        color = TvAccent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(TvAccentSoft)
                                            .clickable { cycleResizeMode() }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                    // Rotate screen
                                    IconButton(onClick = toggleOrientation) {
                                        Icon(Icons.Default.ScreenRotation, contentDescription = "Voltear pantalla", tint = Color.White)
                                    }
                                    // PiP entry removed for PanaTV: leaving the screen must stop
                                    // playback outright (see lifecycle). Reels PiP is unaffected.
                                    // Exit fullscreen
                                    IconButton(onClick = { isFullscreen = false }) {
                                        Icon(Icons.Default.FullscreenExit, contentDescription = "Salir", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    // ── Side channel list (toggleable, stays in fullscreen) ──
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showChannelList && !isLocked,
                        enter = androidx.compose.animation.slideInHorizontally { it } + fadeIn(),
                        exit = androidx.compose.animation.slideOutHorizontally { it } + fadeOut(),
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(260.dp)
                                .background(Color(0xF2141414))
                        ) {
                            Text(
                                "Canales",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                            )
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 24.dp)
                            ) {
                                items(channels, key = { it.id }) { channel ->
                                    val isCurrent = currentChannel?.id == channel.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (isCurrent) TvAccentSoft else Color.Transparent)
                                            .clickable {
                                                viewModel.selectChannel(channel)
                                                showChannelList = false
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(TvCard),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (channel.logoUrl.isNotBlank()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(channel.logoUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize().padding(4.dp),
                                                    contentScale = ContentScale.Fit,
                                                    error = rememberVectorPainter(image = Icons.Default.Tv),
                                                    placeholder = rememberVectorPainter(image = Icons.Default.Tv)
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.Tv,
                                                    contentDescription = null,
                                                    tint = TvTextSecondary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            channel.name,
                                            color = if (isCurrent) TvAccent else Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isCurrent) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFFE53935))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text("VIENDO", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Lock overlay: only the unlock button, revealed on tap ──
                    if (isLocked && lockTapVisible) {
                        IconButton(
                            onClick = {
                                isLocked = false
                                lockTapVisible = false
                                controlsVisible = true
                            },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 16.dp)
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Desbloquear pantalla",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatMmSs(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
}

@Composable
private fun ChannelCardSkeleton() {
    var targetOffset by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            targetOffset = 1000f
            delay(1200)
            targetOffset = 0f
            delay(1200)
        }
    }

    val shimmerColors = listOf(
        Color(0xFF1E1E1E),
        Color(0xFF2A2A30),
        Color(0xFF1E1E1E)
    )

    val brush = Brush.horizontalGradient(
        colors = shimmerColors,
        startX = targetOffset - 500f,
        endX = targetOffset
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1E1E1E))
            .padding(bottom = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.3f)
                .padding(10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(brush)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .padding(horizontal = 4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) TvAccent else TvCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            color = if (selected) Color.White else TvTextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ChannelCard(
    channel: PanaTVChannelEntity,
    isSelected: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) TvCardAlt else TvCard)
            .then(
                if (isSelected) Modifier.border(2.dp, TvAccent, RoundedCornerShape(10.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(bottom = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.3f)
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            if (channel.logoUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(channel.logoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    error = rememberVectorPainter(image = Icons.Default.Tv),
                    placeholder = rememberVectorPainter(image = Icons.Default.Tv)
                )
            } else {
                Icon(
                    Icons.Default.Tv,
                    contentDescription = null,
                    tint = TvTextSecondary,
                    modifier = Modifier.size(32.dp)
                )
            }

            // HD badge
            if (channel.name.contains("HD", ignoreCase = true)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(4.dp))
                        .background(TvAccent)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text("HD", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Favorite heart
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(onClick = onToggleFavorite),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorito",
                    tint = if (isFavorite) TvAccent else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
            }

            // EPG now indicator (bottom bar)
            if (!channel.currentProgram.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF333333))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(TvAccent)
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Text(
                channel.name,
                color = if (isSelected) Color.White else TvTextSecondary,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (!channel.currentProgram.isNullOrBlank()) {
                Text(
                    text = channel.currentProgram ?: "",
                    color = TvTextSecondary.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
