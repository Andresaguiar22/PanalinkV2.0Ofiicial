package com.example.panatv

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.MediaCodec
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
import androidx.media3.datasource.HttpDataSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay

private val PanaTvBackground = Color(0xFF0B1017)
private val PanaTvSurface = Color(0xFF151D26)
private val PanaTvText = Color(0xFFF4F7FA)
private val PanaTvMuted = Color(0xFF9CA8B3)
private val PanaTvAccent = Color(0xFFFF6B00)
private val PanaTvBlue = Color(0xFF2F6BFF)

// Automatic source retries per channel: a live server that drops the connection
// is re-attached silently (2 tries), then the error UI ("Reintentar") appears.
private const val MAX_SOURCE_RETRIES = 2

/**
 * True when [error] is a decoder/codec failure (e.g. MediaCodecVideoRenderer
 * "MediaCodecVideoRenderer error ... video/mp2t, video/avc") rather than an
 * HTTP/IO problem. A network error is never treated as a codec failure even
 * when its cause chain drags decoder remnants, so a 401/403/timeout still
 * follows the normal error path.
 */
private fun isCodecError(error: PlaybackException): Boolean {
    var h: Throwable? = error.cause
    while (h != null) {
        if (h is HttpDataSource.InvalidResponseCodeException) return false
        h = h.cause
    }
    // 4001-4006 are the real decoder/renderer codes (init, query, renderer init,
    // format exceeds capabilities, format unsupported, decoding failed). A decoder
    // that dies mid-stream surfaces in this range, not only as an init failure.
    if (error.errorCode in 4001..4006) return true
    if (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
        error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ||
        error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
    ) return true
    var cause: Throwable? = error.cause
    while (cause != null) {
        if (cause is MediaCodec.CodecException) return true
        val name = cause.javaClass.name
        if (name.endsWith("DecoderInitializationException") ||
            name.endsWith("CodecException") ||
            name.endsWith("MediaCodecDecoderException") ||
            name.endsWith("DecoderException")
        ) return true
        cause = cause.cause
    }
    return false
}

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
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val availableCountries by viewModel.availableCountries.collectAsState()
    val availableCategories by viewModel.availableCategories.collectAsState()
    val availableLanguages by viewModel.availableLanguages.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val showOnlyFavorites by viewModel.showOnlyFavorites.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val debugMessage by viewModel.debugMessage.collectAsState()
    val crashTrace by viewModel.crashTrace.collectAsState()

    var isMuted by remember { mutableStateOf(false) }
    var subtitlesEnabled by remember { mutableStateOf(false) }
    var hasSubtitleTracks by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var hasRenderedFrame by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var showCountries by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var drawerOpen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var lockTapVisible by remember { mutableStateOf(false) }
    var brightness by remember { mutableStateOf(1f) }
    var volumeLevel by remember { mutableStateOf(1f) }
    // Recuperación: un fallo de MediaCodec envenena el decoder del ExoPlayer
    // compartido y una fuente viva puede cortar la conexión; en ambos casos se
    // libra el player y se re-adquiere. El primer fallo de códec pasa a
    // decodificadores FFmpeg (software). Los contadores se reinician al cambiar
    // de canal para que los reintentos siempre queden disponibles.
    var recoveryAttempts by remember(currentChannel?.id) { mutableStateOf(0) }
    var useSoftwareDecoders by remember(currentChannel?.id) { mutableStateOf(false) }
    var playerGeneration by remember { mutableStateOf(0) }
    // Canal cuyo surface está conectado al PlayerView compartido: permite detectar
    // el cambio de canal y forzar el reattach surface (fix imagen congelada).
    var currentPlayerChannelId by remember { mutableStateOf<String?>(null) }

    val player = remember(currentChannel, playerGeneration) {
        currentChannel?.let { channel ->
            com.example.util.AppFloatingPlayerManager.acquirePlayer(
                context = context,
                id = channel.id,
                url = channel.streamUrl,
                title = channel.name,
                type = "panatv",
                userAgent = channel.userAgent,
                referrer = channel.referrer,
                preferSoftware = useSoftwareDecoders,
                // Live IPTV reads the network directly — the disk cache would grow
                // unboundedly on an infinite stream and evict in-flight spans.
                useCache = false
            )
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val recoveringFromCodec = isCodecError(error)
                if (recoveringFromCodec && !useSoftwareDecoders) {
                    // El hardware no acepta este stream (perfil/interlazado/High):
                    // pasamos a los decodificadores FFmpeg (software) SIN gastar el
                    // presupuesto de reintentos — es un upgrade de modo, no un fallo.
                    // Así un canal que solo falla en HW tiene derecho a probar el SW
                    // y luego, si este también falla, recién ahí cuenta el intento.
                    playerError = null
                    isBuffering = true
                    isPlaying = false
                    hasRenderedFrame = false
                    useSoftwareDecoders = true
                    com.example.util.AppFloatingPlayerManager.releasePlayer()
                    currentPlayerChannelId = null
                    playerGeneration += 1
                    return
                }
                if (recoveryAttempts >= MAX_SOURCE_RETRIES) {
                    playerError = error.message ?: "No se pudo cargar el canal"
                    isBuffering = false
                    isPlaying = false
                    return
                }
                recoveryAttempts += 1
                playerError = null
                isBuffering = true
                isPlaying = false
                hasRenderedFrame = false
                // Un decoder envenenado o una fuente viva que cortó la conexión no se
                // recuperan con prepare() sobre el mismo player: se libra y se vuelve
                // a adquirir, que además re-aplica cabeceras y modo (caché/códec).
                com.example.util.AppFloatingPlayerManager.releasePlayer()
                currentPlayerChannelId = null
                playerGeneration += 1
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    // Playback achieved: the error budget resets so a later transient
                    // drop can still auto-recover instead of falling to the error UI.
                    playerError = null
                    recoveryAttempts = 0
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

            override fun onTracksChanged(trackGroups: androidx.media3.common.Tracks) {
                // Compose player reads the track groups of the ACTIVE media item.
                val hasText = player?.takeIf { it.mediaItemCount > 0 }?.currentTracks
                    ?.groups
                    ?.any { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }
                    ?: false
                hasSubtitleTracks = hasText
                if (!hasText) subtitlesEnabled = false
            }
        }
        player?.addListener(listener)
        player?.volume = if (isMuted) 0f else 1f
        isPlaying = player?.isPlaying == true
        onDispose { player?.removeListener(listener) }
    }

    // A fresh channel always starts with a clean retry budget AND fresh media mode:
    // re-acquired from the network (no stale cached bytes from the previous
    // channel, no stale software-decoder flag).
    LaunchedEffect(currentChannel?.id) {
        recoveryAttempts = 0
        useSoftwareDecoders = false
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
        if (isLandscape) {
            enterFullscreen()
        } else {
            // Leaving landscape always drops the transient overlays so returning to
            // portrait never starts with a stale drawer or locked controls.
            drawerOpen = false
            locked = false
            lockTapVisible = false
            exitFullscreen()
        }
    }

    // Auto-hide the on-screen controls so the video stays clean; a tap brings them
    // back. Never hides while locked or with the channel drawer open.
    LaunchedEffect(controlsVisible, locked, drawerOpen, isLandscape) {
        if (isLandscape && controlsVisible && !locked && !drawerOpen) {
            delay(4000)
            controlsVisible = false
        }
    }

    // While locked, tapping reveals the unlock button only for a few seconds.
    LaunchedEffect(locked, lockTapVisible) {
        if (locked && lockTapVisible) {
            delay(3000)
            lockTapVisible = false
        }
    }

    BackHandler(enabled = isLandscape) {
        when {
            drawerOpen -> drawerOpen = false
            locked -> lockTapVisible = true
            controlsVisible -> controlsVisible = false
            else -> exitFullscreen()
        }
    }

    fun startChannel(channel: PanaTVChannelEntity) {
        // The previous channel is CLOSED and the new one starts clean: a released
        // player + a rebuilt surface avoids the stale frame of the old channel and
        // the never-ending spinner (audio of the new channel, picture of the old).
        playerError = null
        hasRenderedFrame = false
        isBuffering = true
        isPlaying = false
        // Cada canal arranca en hardware: si el anterior escaló a software por un
        // códec que no soportaba, el nuevo intenta HW limpio (y si falla, la mejora
        // automática lo vuelve a llevar a SW sin gastar el presupuesto del canal).
        val wasSoftware = useSoftwareDecoders
        useSoftwareDecoders = false
        if (wasSoftware) {
            // El player actual está en modo software; para probar HW hace falta
            // reconstruirlo (el mode de renderer no se puede cambiar en caliente).
            com.example.util.AppFloatingPlayerManager.releasePlayer()
        }
        currentPlayerChannelId = null
        playerGeneration += 1
        viewModel.selectChannel(channel)
    }

    fun selectChannel(channel: PanaTVChannelEntity) {
        // Choosing a channel KEEPS the drawer open so the user can keep browsing;
        // it is only dismissed by tapping outside (or back).
        if (currentChannel?.id == channel.id) {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
            return
        }
        startChannel(channel)
    }

    fun playPrevious() {
        if (channels.isEmpty()) return
        val idx = channels.indexOfFirst { it.id == currentChannel?.id }
        val target = if (idx > 0) channels[idx - 1] else channels.last()
        selectChannel(target)
    }

    fun playNext() {
        if (channels.isEmpty()) return
        val idx = channels.indexOfFirst { it.id == currentChannel?.id }
        val target = if (idx >= 0 && idx < channels.lastIndex) channels[idx + 1] else channels.first()
        selectChannel(target)
    }

    fun toggleSubtitles() {
        val p = player ?: return
        if (!hasSubtitleTracks) return
        subtitlesEnabled = !subtitlesEnabled
        try {
            val params = p.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, !subtitlesEnabled)
                .build()
            p.trackSelectionParameters = params
        } catch (_: Throwable) {}
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
                key(playerGeneration) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
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
                        // Cada canal fuerza un SurfaceView NUEVO (key = playerGeneration).
                        // Con el player REUTILIZADO, media3 solo re-renderiza si además
                        // el surface cambia; con el mismo SurfaceView el renderer de video
                        // se queda negro/congelado con el audio nuevo sonando.
                        val channelChanged = currentPlayerChannelId != currentChannel?.id
                        view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        if (channelChanged) {
                            // El view recién creado ya trae el player conectado (factory);
                            // NO tocar el surface de un canal anterior porque sería un
                            // detach innecesario de un frame que ya no le corresponde.
                            currentPlayerChannelId = currentChannel?.id
                            hasRenderedFrame = false
                        }
                    },
                    onRelease = { view ->
                        // Al destruir este SurfaceView (ya sea por cambio de canal o por
                        // rotación), desvinculamos el player para que el renderer de video
                        // no siga escribiendo en un surface muerto.
                        if (view.player === player) {
                            view.player = null
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                }

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
                                    // prepare() on a poisoned decoder never recovers:
                                    // tear the shared player down and rebuild it.
                                    // Se reinicia a hardware: el manual lo intenta limpio
                                    // (la mejora códec del canal anterior no se arrastra).
                                    playerError = null
                                    isBuffering = true
                                    isPlaying = false
                                    hasRenderedFrame = false
                                    recoveryAttempts = 0
                                    useSoftwareDecoders = false
                                    com.example.util.AppFloatingPlayerManager.releasePlayer()
                                    currentPlayerChannelId = null
                                    playerGeneration += 1
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
                        IconButton(
                            onClick = {
                                currentChannel?.let { viewModel.toggleFavorite(it.id) }
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            val fav = currentChannel?.let { favorites.contains(it.id) } == true
                            Icon(
                                if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                "Favorito",
                                tint = if (fav) PanaTvAccent else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
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

            // Tap anywhere: dismisses the drawer first, then reveals the unlock
            // button while locked, otherwise toggles the control overlays.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(locked, drawerOpen) {
                        detectTapGestures {
                            when {
                                drawerOpen -> drawerOpen = false
                                locked -> lockTapVisible = !lockTapVisible
                                else -> controlsVisible = !controlsVisible
                            }
                        }
                    }
            )

            if (controlsVisible && !locked) {
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
                    LandscapeAction(Icons.Default.SkipNext, "Siguiente") { playNext() }
                    if (hasSubtitleTracks) {
                        LandscapeAction(
                            if (subtitlesEnabled) Icons.Default.CheckCircle else Icons.Default.Subtitles,
                            if (subtitlesEnabled) "Subtítulos: ON" else "Subtítulos: OFF"
                        ) { toggleSubtitles() }
                    }
                    LandscapeAction(Icons.Default.Lock, "Bloquear") {
                        // Lock clears every control so the video plays perfectly clean.
                        // Tapping the screen briefly reveals the unlock button again.
                        drawerOpen = false
                        controlsVisible = false
                        locked = true
                    }
                }
            }

            // Locked: a single unlock affordance, shown only after a tap/back press.
            if (locked && lockTapVisible) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures { } },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier.size(56.dp).clickable {
                            locked = false
                            lockTapVisible = false
                            controlsVisible = true
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.LockOpen, "Desbloquear", tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }

            // Slide-in drawer: categories (left) + channel list (right of it), video stays visible
            AnimatedVisibility(
                visible = drawerOpen && !locked,
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
                        // Swallow taps on empty drawer space so they don't fall through
                        // to the video and toggle the controls behind the list.
                        .pointerInput(Unit) { detectTapGestures { } }
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
                        // Idiomas con etiqueta amigable: Todos / Español / Portugués / Inglés...
                        item {
                            DrawerCategory(
                                "Todos los idiomas",
                                selectedLanguage.isEmpty() && !showOnlyFavorites
                            ) {
                                viewModel.updateSelectedLanguage("")
                                viewModel.setShowOnlyFavorites(false)
                            }
                        }
                        items(availableLanguages) { langCode ->
                            val langLabel = languageLabel(langCode)
                            DrawerCategory(langLabel, selectedLanguage == langCode && !showOnlyFavorites) {
                                viewModel.updateSelectedLanguage(langCode)
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
                    // Selector de idioma (chips): el primero "Todos" global.
                    item {
                        CategoryChip(
                            "Idioma: Todos",
                            selectedLanguage.isEmpty() && !showOnlyFavorites,
                            accent = PanaTvAccent
                        ) {
                            viewModel.updateSelectedLanguage("")
                            viewModel.setShowOnlyFavorites(false)
                        }
                    }
                    items(availableLanguages) { langCode ->
                        val langLabel = languageLabel(langCode)
                        CategoryChip(
                            langLabel,
                            selectedLanguage == langCode && !showOnlyFavorites,
                            accent = PanaTvAccent
                        ) {
                            viewModel.updateSelectedLanguage(langCode)
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
                                onClick = { selectChannel(channel) },
                                onWatchNow = {
                                    // Siempre inicia el canal (aunque ya esté seleccionado,
                                    // sin toggle play/pause) y abre pantalla completa.
                                    startChannel(channel)
                                    enterFullscreen()
                                }
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
private fun CategoryChip(label: String, selected: Boolean, accent: Color = PanaTvBlue, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) accent else PanaTvSurface)
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
    onClick: () -> Unit,
    onWatchNow: () -> Unit
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
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                .clickable(onClick = onWatchNow),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.ArrowForward, "Ver en pantalla completa", tint = Color.White, modifier = Modifier.size(18.dp))
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
