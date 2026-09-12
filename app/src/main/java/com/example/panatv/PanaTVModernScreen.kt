package com.example.panatv

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
private val PanaTvSurface2 = Color(0xFF1C2732)
private val PanaTvAccent = Color(0xFFFF6B00)
private val PanaTvAccentSoft = Color(0x29FF6B00)
private val PanaTvText = Color(0xFFF4F7FA)
private val PanaTvMuted = Color(0xFF9CA8B3)

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

    var isFullscreen by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var hasRenderedFrame by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var showCountries by remember { mutableStateOf(false) }
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
        player?.volume = if (isMuted) 0f else 1f
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
            com.example.util.AppFloatingPlayerManager.releasePlayer()
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

    fun exitFullscreen() {
        isFullscreen = false
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (activity != null) {
            WindowInsetsControllerCompat(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.systemBars())
        }
    }
    BackHandler(enabled = isFullscreen) {
        exitFullscreen()
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

    val playerContent: @Composable (Modifier) -> Unit = { modifier ->
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(14.dp))
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
                            // SurfaceView es el default de media3; no tocar el setter (privado en esta version)
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
                            // Descarta la textura residual que persiste en la SurfaceView vieja.

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
                    IconButton(onClick = {
                        enterFullscreen()
                        isFullscreen = true
                    }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Fullscreen, "Pantalla completa", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }

    Scaffold(containerColor = PanaTvBackground) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(PanaTvAccent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.LiveTv, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(9.dp))
                Text("Pana", color = PanaTvText, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Text("TV", color = PanaTvAccent, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                Box {
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(PanaTvSurface).clickable { showCountries = true }.padding(horizontal = 10.dp, vertical = 6.dp),
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
            }

            Box(
                modifier = Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(21.dp)).background(PanaTvSurface).padding(horizontal = 13.dp),
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

            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(horizontal = 1.dp)) {
                item {
                    TvFilterChip("Todos", selectedCategory.isBlank() && !showOnlyFavorites) {
                        viewModel.updateSelectedCategory("")
                        viewModel.setShowOnlyFavorites(false)
                    }
                }
                item {
                    TvFilterChip("Favoritos", showOnlyFavorites) {
                        viewModel.updateSelectedCategory("")
                        viewModel.setShowOnlyFavorites(true)
                    }
                }
                items(availableCategories) { category ->
                    TvFilterChip(tvCategoryLabel(category), selectedCategory == category && !showOnlyFavorites) {
                        viewModel.updateSelectedCategory(category)
                        viewModel.setShowOnlyFavorites(false)
                    }
                }
            }

            playerContent(Modifier.fillMaxWidth().aspectRatio(16f / 9f))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
                Text("Canales", color = PanaTvText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(7.dp))
                Text("${channels.size}", color = PanaTvMuted, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                if (currentChannel != null) Text("Ahora: ${currentChannel!!.name}", color = PanaTvMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            // IMPORTANT: this grid owns only the remaining height. It cannot measure
            // itself at the full screen height, so channel cards never cover the player.
            if (channels.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 108.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 1.dp, end = 1.dp, bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(channels, key = { it.id }) { channel ->
                        PanaTvChannelCard(
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

    // Solo automatiza la ENTRADA (rotar a landscape + ocultar bars) en respuesta al
    // estado; la salida la gestionan el BackHandler y onDismissRequest para no forzar
    // la orientación al montar la pantalla en portrait.
    LaunchedEffect(isFullscreen) {
        if (isFullscreen) enterFullscreen()
    }

    if (isFullscreen) {
        Dialog(
            onDismissRequest = {
                exitFullscreen()
            },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Column(Modifier.fillMaxSize()) {
                    // Player principal
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        playerContent(Modifier.fillMaxSize())
                    }
                    // Mini catalogo de canales debajo, sin salir del fullscreen
                    LazyRow(
                        contentPadding = PaddingValues(horizontal =  10.dp, vertical =  8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha =  0.85f))
                    ) {
                        items(channels, key = { it.id }) { channel ->
                            PanaTvChannelCard(
                                channel = channel,
                                selected = currentChannel?.id == channel.id,
                                favorite = favorites.contains(channel.id),
                                onFavorite = { viewModel.toggleFavorite(channel.id) },
                                onClick = { selectChannel(channel) }
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha =  0.45f)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { exitFullscreen() }) {
                        Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White)
                    }
                    Text(currentChannel?.name.orEmpty(), color = Color.White, fontSize =  15.sp, fontWeight = FontWeight.Bold, maxLines =  1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = { player?.let { if (it.isPlaying) it.pause() else it.play() } }, modifier = Modifier.size(36.dp)) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Reproducir/Pausar", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TvFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) PanaTvAccent else PanaTvSurface,
        contentColor = if (selected) Color.White else PanaTvMuted,
        modifier = Modifier.height(34.dp)
    ) {
        Box(Modifier.padding(horizontal = 13.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun PanaTvChannelCard(
    channel: PanaTVChannelEntity,
    selected: Boolean,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) PanaTvSurface2 else PanaTvSurface)
            .then(if (selected) Modifier.border(1.5.dp, PanaTvAccent, RoundedCornerShape(11.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(7.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1.42f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0D141B)),
            contentAlignment = Alignment.Center
        ) {
            if (channel.logoUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(channel.logoUrl).crossfade(true).build(),
                    contentDescription = channel.name,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentScale = ContentScale.Fit,
                    error = rememberVectorPainter(image = Icons.Default.Tv),
                    placeholder = rememberVectorPainter(image = Icons.Default.Tv)
                )
            } else {
                Icon(Icons.Default.Tv, null, tint = PanaTvMuted, modifier = Modifier.size(30.dp))
            }
            if (channel.name.contains("HD", ignoreCase = true)) {
                Surface(shape = RoundedCornerShape(4.dp), color = PanaTvAccent, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp)) {
                    Text("HD", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
            }
            IconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopStart).size(28.dp)) {
                Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorito", tint = if (favorite) PanaTvAccent else Color.White.copy(alpha = 0.85f), modifier = Modifier.size(15.dp))
            }
            if (!channel.currentProgram.isNullOrBlank()) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.15f))) {
                    Box(Modifier.fillMaxWidth(channel.programProgress.coerceIn(0f, 1f)).fillMaxHeight().background(PanaTvAccent))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(channel.name, color = if (selected) Color.White else PanaTvText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (!channel.currentProgram.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(channel.currentProgram.orEmpty(), color = PanaTvMuted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}
