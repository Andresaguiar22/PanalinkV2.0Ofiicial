package com.example.panatv

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest

private val PanaTvBackground = Color(0xFF0B1017)
private val PanaTvSurface = Color(0xFF151D26)
private val PanaTvText = Color(0xFFF4F7FA)
private val PanaTvMuted = Color(0xFF9CA8B3)
private val PanaTvAccent = Color(0xFFFF6B00)
private val PanaTvBlue = Color(0xFF2F6BFF)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PanaTVModernScreen(viewModel: PanaTVViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val channels by viewModel.channels.collectAsState()
    val currentChannel by viewModel.currentChannel.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCountry by viewModel.selectedCountry.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val availableCountries by viewModel.availableCountries.collectAsState()
    val availableCategories by viewModel.availableCategories.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val showOnlyFavorites by viewModel.showOnlyFavorites.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val debugMessage by viewModel.debugMessage.collectAsState()
    val crashTrace by viewModel.crashTrace.collectAsState()

    var isMuted by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var hasRenderedFrame by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var showCountries by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var drawerOpen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var brightness by remember { mutableStateOf(1f) }
    var volumeLevel by remember { mutableStateOf(1f) }
    // Canal cuyo surface está conectado al PlayerView compartido: permite detectar
    // el cambio de canal y forzar el reattach surface (fix imagen congelada).
    var currentPlayerChannelId by remember { mutableStateOf<String?>(null) }

    val player = remember(currentChannel) {
        currentChannel?.let { channel ->
            com.example.util.AppFloatingPlayerManager.acquirePlayer(
                context = context,
                id = channel.id,
                url = channel.streamUrl,
                title = channel.name,
                type = "panatv",
                userAgent = channel.userAgent,
                referrer = channel.referrer
            )
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playerError = error.message ?: "No se pudo cargar el canal"
                isBuffering = false
                isPlaying = false
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    playerError = null
                }
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFrame = true
                playerError = null
            }

            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0 && !hasRenderedFrame) {
                    hasRenderedFrame = true
                    playerError = null
                }
            }
        }
        player?.addListener(listener)
        player?.volume = if (isMuted) 0f else 1f
        isPlaying = player?.isPlaying == true
        onDispose { player?.removeListener(listener) }
    }

    LaunchedEffect(player, currentChannel?.id) {
        if (player != null) {
            playerError = null
            isBuffering = true
            hasRenderedFrame = false
            player.playWhenReady = true
            player.play()
        }
    }

    LaunchedEffect(isMuted, player) {
        player?.volume = if (isMuted) 0f else volumeLevel
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> player?.pause()
                Lifecycle.Event.ON_RESUME -> {
                    // Returning to PanaTV does not force playback. The user can press Play.
                    isPlaying = player?.isPlaying == true
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // NO releasePlayer() here — the shared ExoPlayer must survive config changes
            // (rotation). It is only released when the Activity is truly destroyed.
        }
    }

    LaunchedEffect(crashTrace) {
        // Kept as state so the existing ViewModel diagnostics remain available.
    }

    fun enterFullscreen() {
        val activity = context as? Activity
        if (activity != null) {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            val ctrl = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
            if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
    }

    // Releases the orientation lock so the sensor decides again. Setting PORTRAIT here
    // would pin the Activity to portrait and permanently prevent rotating back into
    // landscape, so UNSPECIFIED is required for the rotation-driven flow to work.
    fun exitFullscreen() {
        val activity = context as? Activity
        if (activity != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowInsetsControllerCompat(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(isLandscape) {
        if (isLandscape) enterFullscreen() else exitFullscreen()
    }

    BackHandler(enabled = isLandscape) {
        if (drawerOpen) {
            drawerOpen = false
        } else if (controlsVisible) {
            controlsVisible = false
        } else {
            exitFullscreen()
        }
    }

    fun selectChannel(channel: PanaTVChannelEntity) {
        if (currentChannel?.id != channel.id) {
            playerError = null
            hasRenderedFrame = false
            isBuffering = true
            viewModel.selectChannel(channel)
        } else {
            player?.let {
                if (it.isPlaying) it.pause() else it.play()
            }
        }
    }

    fun playPrevious() {
        if (channels.isEmpty()) return
        val idx = channels.indexOfFirst { it.id == currentChannel?.id }
        val target = if (idx > 0) channels[idx - 1] else channels.last()
        selectChannel(target)
    }

    fun applyBrightness(value: Float) {
        brightness = value.coerceIn(0.05f, 1f)
        val activity = context as? Activity ?: return
        val lp = activity.window.attributes
        lp.screenBrightness = brightness
        activity.window.attributes = lp
    }

    fun applyVolume(value: Float) {
        volumeLevel = value.coerceIn(0f, 1f)
        isMuted = volumeLevel == 0f
        player?.volume = volumeLevel
    }

    val shareChannel = {
        val channel = currentChannel
        if (channel != null) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, channel.name + "\n" + channel.streamUrl)
            }
            context.startActivity(Intent.createChooser(send, "Compartir canal"))
        }
    }

    val showHelp = {
        Toast.makeText(context, "Toca el video para mostrar u ocultar los controles. Usa los deslizadores laterales para brillo y volumen.", Toast.LENGTH_LONG).show()
    }

    val playerContent: @Composable (Modifier, Boolean) -> Unit = { modifier, withOverlay ->
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(if (withOverlay) 14.dp else 0.dp))
                .background(Color.Black)
        ) {
            if (currentChannel == null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.LiveTv, null, tint = PanaTvAccent.copy(alpha = 0.7f), modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Selecciona un canal para comenzar", color = PanaTvMuted, fontSize = 13.sp)
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            // View NUEVO: su surface nunca ha estado conectado a este player con video
                            // activo. Invalidar el canal actual para que el update que sigue fuerce el
                            // reattach surface (si no, media3 deja la textura vieja: negro o tarjetas).
                            currentPlayerChannelId = null
                            this.player = player
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { view ->
                        val channelChanged = currentPlayerChannelId != currentChannel?.id
                        if (channelChanged && view.player != null) {
                            // Detach el surface del canal anterior: media3 NO re-conecta
                            // el mismo Player sola con `view.player = player`, dejando la
                            // textura congelada del canal previo (audio nuevo + imagen vieja).
                            view.player = null
                        }
                        view.player = player
                        view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        if (channelChanged) {
                            currentPlayerChannelId = currentChannel?.id
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if ((!hasRenderedFrame || isBuffering) && playerError == null) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PanaTvAccent, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                    }
                }

                if (playerError != null) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.WifiOff, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(34.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("No se pudo cargar el canal", color = PanaTvText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                playerError!!.take(90),
                                color = PanaTvMuted,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 28.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    playerError = null
                                    isBuffering = true
                                    hasRenderedFrame = false
                                    player?.prepare()
                                    player?.playWhenReady = true
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PanaTvAccent),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Reintentar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (withOverlay) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.38f))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = RoundedCornerShape(5.dp), color = Color(0xFFE53935)) {
                            Text("EN VIVO", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(currentChannel?.name.orEmpty(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { player?.let { if (it.isPlaying) it.pause() else it.play() } }, modifier = Modifier.size(38.dp)) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Reproducir", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = { isMuted = !isMuted }, modifier = Modifier.size(38.dp)) {
                            Icon(if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, "Volumen", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (currentChannel?.currentProgram.isNullOrBlank()) "EN DIRECTO" else currentChannel?.currentProgram.orEmpty(),
                            color = PanaTvMuted,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp).weight(1f)
                        )
                        IconButton(onClick = { enterFullscreen() }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Default.Fullscreen, "Pantalla completa", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    if (isLandscape) {
        // === LANDSCAPE: immersive full-screen player with translucent controls ===
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            playerContent(Modifier.fillMaxSize(), false)

            // Tap anywhere to toggle the control overlays.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { controlsVisible = !controlsVisible } }
            )

            if (controlsVisible) {
                // Top bar: back + title (left) / share, help, favorite (right)
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { exitFullscreen() }) {
                        Icon(Icons.Default.ArrowBack, "Atrás", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Text(
                        currentChannel?.name.orEmpty(),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = shareChannel) {
                        Icon(Icons.Default.Share, "Compartir", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = showHelp) {
                        Icon(Icons.Default.HelpOutline, "Ayuda", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = { currentChannel?.let { viewModel.toggleFavorite(it.id) } }) {
                        val fav = currentChannel?.let { favorites.contains(it.id) } == true
                        Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorito", tint = if (fav) PanaTvAccent else Color.White, modifier = Modifier.size(22.dp))
                    }
                }

                // Left brightness slider
                VerticalSlider(
                    value = brightness,
                    onValueChange = { applyBrightness(it) },
                    icon = Icons.Default.BrightnessHigh,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp)
                )

                // Right volume slider
                VerticalSlider(
                    value = volumeLevel,
                    onValueChange = { applyVolume(it) },
                    icon = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)
                )

                // Bottom center actions: Categoría / Anterior / Fijar
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(46.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LandscapeAction(Icons.Default.List, "Categoría") { drawerOpen = true }
                    LandscapeAction(Icons.Default.SkipPrevious, "Anterior") { playPrevious() }
                    val fav = currentChannel?.let { favorites.contains(it.id) } == true
                    LandscapeAction(if (fav) Icons.Default.Lock else Icons.Default.LockOpen, "Fijar") {
                        currentChannel?.let { viewModel.toggleFavorite(it.id) }
                    }
                }
            }

            // Slide-in drawer: categories (left) + channel list (right of it), video stays visible
            AnimatedVisibility(
                visible = drawerOpen,
                enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(0.64f)
            ) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.9f))
                ) {
                    LazyColumn(
                        Modifier
                            .width(150.dp)
                            .fillMaxHeight()
                            .padding(vertical = 6.dp)
                    ) {
                        item {
                            DrawerCategory("ChannelList", selectedCategory.isBlank() && !showOnlyFavorites) {
                                viewModel.updateSelectedCategory("")
                                viewModel.setShowOnlyFavorites(false)
                            }
                        }
                        item {
                            DrawerCategory("Favoritos", showOnlyFavorites) {
                                viewModel.setShowOnlyFavorites(true)
                            }
                        }
                        items(availableCategories) { category ->
                            DrawerCategory(tvCategoryLabel(category), selectedCategory == category && !showOnlyFavorites) {
                                viewModel.updateSelectedCategory(category)
                                viewModel.setShowOnlyFavorites(false)
                            }
                        }
                        items(availableCountries) { country ->
                            DrawerCategory(country, selectedCountry == country) {
                                viewModel.updateSelectedCountry(country)
                            }
                        }
                    }

                    Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.12f)))

                    LazyColumn(
                        Modifier
                            .fillMaxHeight()
                            .padding(vertical = 6.dp)
                    ) {
                        itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                            LandscapeChannelRow(
                                number = index + 1,
                                channel = channel,
                                selected = currentChannel?.id == channel.id,
                                onClick = { selectChannel(channel) }
                            )
                        }
                    }
                }
            }
        }
    } else {
        // === PORTRAIT: header + video + tabs + category chips + channel list ===
        Scaffold(containerColor = PanaTvBackground) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pana", color = PanaTvText, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                    Text("TV", color = PanaTvAccent, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.weight(1f))
                    Box {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(PanaTvSurface)
                                .clickable { showCountries = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Public, null, tint = PanaTvAccent, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(selectedCountry.ifBlank { "Todos" }, color = PanaTvMuted, fontSize = 11.sp, maxLines = 1)
                        }
                        DropdownMenu(expanded = showCountries, onDismissRequest = { showCountries = false }, modifier = Modifier.background(PanaTvSurface)) {
                            DropdownMenuItem(text = { Text("Todos los países", color = Color.White) }, onClick = { viewModel.updateSelectedCountry(""); showCountries = false })
                            availableCountries.forEach { country ->
                                DropdownMenuItem(text = { Text(country, color = Color.White) }, onClick = { viewModel.updateSelectedCountry(country); showCountries = false })
                            }
                        }
                    }
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(if (showSearch) Icons.Default.Close else Icons.Default.Search, "Buscar", tint = PanaTvText, modifier = Modifier.size(22.dp))
                    }
                }

                if (showSearch) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .height(42.dp)
                            .clip(RoundedCornerShape(21.dp))
                            .background(PanaTvSurface)
                            .padding(horizontal = 13.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, null, tint = PanaTvMuted, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(9.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = viewModel::updateSearchQuery,
                                modifier = Modifier.weight(1f),
                                textStyle = TextStyle(color = PanaTvText, fontSize = 13.sp),
                                singleLine = true,
                                cursorBrush = SolidColor(PanaTvAccent),
                                decorationBox = { inner ->
                                    if (searchQuery.isBlank()) Text("Buscar canales...", color = PanaTvMuted, fontSize = 13.sp)
                                    inner()
                                }
                            )
                        }
                    }
                }

                playerContent(Modifier.fillMaxWidth().aspectRatio(16f / 9f), true)

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PortraitTab("Categoría", selected = !showOnlyFavorites, modifier = Modifier.weight(1f)) {
                        viewModel.setShowOnlyFavorites(false)
                    }
                    PortraitTab("Favoritos", selected = showOnlyFavorites, modifier = Modifier.weight(1f)) {
                        viewModel.setShowOnlyFavorites(true)
                    }
                }

                LazyRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    item {
                        CategoryChip("ChannelList", selectedCategory.isBlank() && !showOnlyFavorites) {
                            viewModel.updateSelectedCategory("")
                            viewModel.setShowOnlyFavorites(false)
                        }
                    }
                    items(availableCategories) { category ->
                        CategoryChip(tvCategoryLabel(category), selectedCategory == category && !showOnlyFavorites) {
                            viewModel.updateSelectedCategory(category)
                            viewModel.setShowOnlyFavorites(false)
                        }
                    }
                }

                if (channels.isNotEmpty()) {
                    LazyColumn(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 18.dp)
                    ) {
                        itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                            ChannelListRow(
                                number = index + 1,
                                channel = channel,
                                selected = currentChannel?.id == channel.id,
                                favorite = favorites.contains(channel.id),
                                onFavorite = { viewModel.toggleFavorite(channel.id) },
                                onClick = { selectChannel(channel) }
                            )
                        }
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (isLoading) {
                            CircularProgressIndicator(color = PanaTvAccent, modifier = Modifier.size(30.dp))
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.TvOff, null, tint = PanaTvMuted, modifier = Modifier.size(34.dp))
                                Spacer(Modifier.height(7.dp))
                                Text(debugMessage.ifBlank { "No se encontraron canales" }, color = PanaTvMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PortraitTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            color = if (selected) PanaTvBlue else PanaTvMuted,
            fontSize = 17.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(vertical = 10.dp)
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) PanaTvBlue else Color.Transparent)
        )
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) PanaTvBlue else PanaTvSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            color = if (selected) Color.White else PanaTvMuted,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun ChannelLogo(channel: PanaTVChannelEntity, size: Int, padding: Int) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF10161D)),
        contentAlignment = Alignment.Center
    ) {
        if (channel.logoUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(channel.logoUrl).crossfade(true).build(),
                contentDescription = channel.name,
                modifier = Modifier.fillMaxSize().padding(padding.dp),
                contentScale = ContentScale.Fit,
                error = rememberVectorPainter(image = Icons.Default.Tv),
                placeholder = rememberVectorPainter(image = Icons.Default.Tv)
            )
        } else {
            Icon(Icons.Default.Tv, null, tint = PanaTvMuted, modifier = Modifier.size((size - 20).dp))
        }
    }
}

@Composable
private fun ChannelListRow(
    number: Int,
    channel: PanaTVChannelEntity,
    selected: Boolean,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelLogo(channel, size = 52, padding = 4)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(String.format("%03d", number), color = PanaTvBlue, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                Text(
                    channel.name,
                    color = if (selected) PanaTvBlue else PanaTvText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!channel.currentProgram.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    channel.currentProgram.orEmpty(),
                    color = PanaTvMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onFavorite, modifier = Modifier.size(34.dp)) {
            Icon(
                if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                "Favorito",
                tint = if (favorite) PanaTvAccent else PanaTvMuted,
                modifier = Modifier.size(18.dp)
            )
        }
        Box(
            Modifier.size(34.dp).clip(CircleShape).border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.ArrowForward, "Ver", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun LandscapeChannelRow(
    number: Int,
    channel: PanaTVChannelEntity,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelLogo(channel, size = 38, padding = 3)
        Spacer(Modifier.width(10.dp))
        Text(String.format("%03d", number), color = if (selected) PanaTvBlue else PanaTvMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Text(
            channel.name,
            color = if (selected) PanaTvBlue else PanaTvText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DrawerCategory(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(if (selected) PanaTvBlue else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Text(
            label,
            color = if (selected) Color.White else PanaTvText,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LandscapeAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(5.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val v = value.coerceIn(0f, 1f)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .width(32.dp)
                .height(150.dp)
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        onValueChange((1f - pos.y / size.height).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, _ ->
                        onValueChange((1f - change.position.y / size.height).coerceIn(0f, 1f))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.35f))
            )
            if (v > 0f) {
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight(v)
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White)
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = ((1f - v) * 140f).dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}
