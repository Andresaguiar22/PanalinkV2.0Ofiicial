package com.example.ui.screen

import android.widget.VideoView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.example.identity.model.toIdentityUiState
import com.example.util.ReelDualPlayerManager
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import com.example.ui.viewmodel.StatesUiState
import com.example.ui.viewmodel.StatesViewModel
import com.example.ui.viewmodel.SocialViewModel
import com.example.ui.viewmodel.SocialUiState
import com.example.ui.viewmodel.CommentsEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.focus.focusRequester
import android.app.DownloadManager
import android.os.Environment
import android.net.Uri
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import com.example.data.repository.ProfilesRepository
import com.example.data.repository.CdnManager
import com.example.data.supabase.SupabaseClient

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.util.NetworkMonitor
import com.example.ui.components.PanaAvatar
import com.example.ui.components.OfflineEmptyView
import com.example.feature.diagnostics.data.DiagnosticsRepository
import com.example.feature.diagnostics.model.DiagnosticCategory
import com.example.feature.diagnostics.model.DiagnosticSeverity

/** Max automatic codec-recovery attempts per reel before showing the definitive error. */
private const val MAX_CODEC_AUTO_RETRIES = 2
/** How long to wait for a freshly-rebuilt ExoPlayer to reach STATE_READY before escalating. */
private const val RECOVERY_READY_TIMEOUT_MS = 6000L

// Async thumbnail URL resolution: resolveMediaUrlSync can perform VCDN BFF I/O (runBlocking),
// which must never block Main/UI during compose. produceState runs the resolution in a coroutine.
@Composable
private fun resolvedThumbnailUrl(rawUrl: String?): String {
    val state by produceState(initialValue = rawUrl.orEmpty(), rawUrl) {
        value = com.example.data.repository.CdnManager.resolveMediaUrl(rawUrl)
    }
    return state
}

private fun performHaptic(context: Context) {
    val vib = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    vib.vibrate(android.os.VibrationEffect.createOneShot(15, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TikTokVideoFeedScreen(
    viewModel: StatesViewModel,
    initialStateId: String,
    isActive: Boolean = true,
    onBack: () -> Unit,
    onNavigateToUserProfile: ((String) -> Unit)? = null,
    onNavigateToHashtag: ((String) -> Unit)? = null,
    onNavigateToLive: (() -> Unit)? = null
) {
    val reelsState by viewModel.reelsState.collectAsStateWithLifecycle()
    var isMuted by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val context = LocalContext.current
    val dualManager = remember { ReelDualPlayerManager(context) }
    val diagnostics = remember { DiagnosticsRepository.getInstance(context) }
    val activity = context as? android.app.Activity
    val coroutineScope = rememberCoroutineScope()
    DisposableEffect(isActive) {
        val window = activity?.window
        if (window != null && isActive) {
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            }
            // No floating bubble: leaving the feed always stops and releases the player.
            dualManager.releaseAll()
            com.example.util.AppFloatingPlayerManager.releasePlayer()
        }
    }

    androidx.activity.compose.BackHandler {
        com.example.util.AppFloatingPlayerManager.releasePlayer()
        onBack()
    }

    val floatAndNavigate: (String) -> Unit = { userId ->
        com.example.util.AppFloatingPlayerManager.releasePlayer()
        onNavigateToUserProfile?.invoke(userId)
    }

    // Filter video states dynamically by search query (caption or user displayName)
    var selectedFilter by remember { mutableStateOf("Todo") }

    val videoStates = remember(reelsState, searchQuery, selectedFilter) {
        if (reelsState is StatesUiState.Success) {
            var allVideos = (reelsState as StatesUiState.Success).states.filter { 
                it.state.mediaType.equals("video", ignoreCase = true) || 
                it.state.mediaType.contains("video", ignoreCase = true) || 
                it.state.isReel || 
                it.state.type.equals("reel", ignoreCase = true)
            }
            
            // Apply Search
            if (searchQuery.isNotBlank()) {
                allVideos = allVideos.filter {
                    it.state.caption?.contains(searchQuery, ignoreCase = true) == true ||
                    it.profile?.displayName?.contains(searchQuery, ignoreCase = true) == true
                }
            }

            // Apply Filters
            when (selectedFilter) {
                "Tendencias" -> allVideos.sortedByDescending { it.state.likesCount ?: 0 }
                "Más Vistos" -> allVideos.sortedByDescending { it.state.viewsCount ?: 0 }
                "Favoritos" -> allVideos.filter { it.state.favoritedByMe == true }
                else -> allVideos
            }
        } else {
            emptyList()
        }
    }

    // Seek to the specific reel if initialStateId changes (e.g. navigating from search or profile)
    var seekTargetId by remember { mutableStateOf<String?>(null) }
    
    val initialIndex = remember(videoStates, initialStateId, seekTargetId) {
        val targetId = seekTargetId ?: initialStateId
        val index = videoStates.indexOfFirst { it.state.id == targetId }
        if (index != -1) index else 0
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { videoStates.size }
    )

    LaunchedEffect(initialIndex) {
        if (initialIndex >= 0 && initialIndex < videoStates.size && pagerState.currentPage != initialIndex) {
            pagerState.scrollToPage(initialIndex)
        }
    }

    if (reelsState is StatesUiState.Loading && videoStates.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFF00FF85))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Cargando vídeos venezolanos... 🇻🇪", color = Color.White)
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        var showSearchInput by remember { mutableStateOf(false) }
        var isDiscoveryMode by remember { mutableStateOf(false) }

        // Discovery / Search Grid View
        if (isDiscoveryMode || searchQuery.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 70.dp)) {
                // Filters Row
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Todo", "Tendencias", "Más Vistos", "Favoritos").forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.White.copy(alpha = 0.05f),
                                selectedContainerColor = Color(0xFF00FF85),
                                labelColor = Color.White,
                                selectedLabelColor = Color.Black
                            ),
                            border = null,
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }

                if (videoStates.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No se encontraron vídeos 🇻🇪🔍", color = Color.Gray)
                    }
                } else {
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(2.dp)
                    ) {
                        gridItems(videoStates) { item ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(0.56f)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E1E24))
                                    .clickable {
                                        // Play this video in full screen
                                        val targetId = item.state.id
                                        searchQuery = "" 
                                        isDiscoveryMode = false
                                        seekTargetId = targetId
                                    }
                            ) {
                                AsyncImage(
                                    model = resolvedThumbnailUrl(item.state.mediaUrl),
                                    contentDescription = "Miniatura de reel de ${item.profile?.displayName ?: "usuario"}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                
                                // View count overlay
                                Row(
                                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(10.dp))
                                    Text("${item.state.viewsCount ?: 0}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // We use key(searchQuery) so the pager state resets securely to index 0 when the search query changes,
            // preventing IndexOutOfBoundsException and ensuring smooth TikTok-style feed navigation.
            key(searchQuery) {

                // SIN byte-prefetch ni SimpleCache: los reels consumen directo VCDN.
                // El dual-manager ya pre-prepara el primer frame del siguiente video
                // (preload del slot), que es lo que da el cambio instantáneo a lo
                // TikTok sin I/O extra de competencia con la reproducción.

            if (videoStates.isEmpty() && !NetworkMonitor.isOnline.value) {
                // Sin conexión total: un spinner infinito "Cargando..." o "No se
                // encontraron" serían confusos; mostrar estado offline claro y accion recy
                OfflineEmptyView(
                    subsection = "Los vídeos",
                    onRetry = { viewModel.loadActiveStates(showLoading = false) },
                    showRetry = true
                )
            } else if (videoStates.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No se encontraron vídeos 🇻🇪🔍",
                            color = Color.Gray,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                // Los reels consumen directo VCDN (sin cache): el preload del primer
                // frame lo hace el dual-manager en el slot inactivo, no este bloque.

                // Filesystem cleaner runs once per screen entry, deferred 400ms cancellable.
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(400)
                    withContext(Dispatchers.IO) {
                        com.example.media.social.SocialMediaCleaner.cleanExpiredStoriesAndReels(context)
                    }
                }
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(state = pagerState)
                ) { page ->
                    val stateWithUser = videoStates[page]
                    
                    // Ultra-smooth crossfade & subtle scale zoom out transition between pages
                    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                    val scale = 1f - (kotlin.math.abs(pageOffset) * 0.12f).coerceIn(0f, 0.12f)
                    val alpha = 1f - (kotlin.math.abs(pageOffset) * 0.75f).coerceIn(0f, 0.75f)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                            }
                    ) {
                        TikTokPageItem(
                            stateWithUser = stateWithUser,
                            viewModel = viewModel,
                            isActivePage = isActive && (pagerState.currentPage == page),
                            // Ventana de preload = los 2 siguientes reels: el inmediato
                            // (slot en preload-muted, frame ya decodificándose) y el
                            // siguiente-siguiente (encola slot libre cuando se libera y
                            // va cargando en segundo plano mientras el usuario mira).
                            isPreload = (page > pagerState.currentPage && page <= pagerState.currentPage + 2),
                              dualManager = dualManager,
                            isMuted = isMuted,
                            onMuteToggle = { isMuted = !isMuted },
                            onLikeClick = {
                                viewModel.toggleLike(stateWithUser.state.id, stateWithUser.state.likedByMe ?: false, onError = { err ->
                                    android.widget.Toast.makeText(context, "Error: $err", android.widget.Toast.LENGTH_LONG).show()
                                })
                            },
                            onFavoriteClick = {
                                viewModel.toggleFavorite(stateWithUser.state.id, stateWithUser.state.favoritedByMe ?: false, onError = { err ->
                                    android.widget.Toast.makeText(context, "Error: $err", android.widget.Toast.LENGTH_LONG).show()
                                })
                            },
                            onShareClick = {
                                viewModel.incrementShare(stateWithUser.state.id, onError = { err ->
                                    android.widget.Toast.makeText(context, "Error: $err", android.widget.Toast.LENGTH_LONG).show()
                                })
                            },
                            onCommentSubmit = { text: String ->
                                viewModel.addComment(stateWithUser.state.id, text, onError = { err ->
                                    android.widget.Toast.makeText(context, "Error: $err", android.widget.Toast.LENGTH_LONG).show()
                                })
                            },
                            onViewRegistered = {
                                viewModel.registerView(stateWithUser.state.id)
                            },
                            onNavigateToUserProfile = floatAndNavigate,
                            onHashtagClick = { tag ->
                                com.example.util.AppFloatingPlayerManager.releasePlayer()
                                onNavigateToHashtag?.invoke(tag)
                            },
                            onDeleteClick = {
                                viewModel.deleteState(stateWithUser.state.id) {
                                    android.widget.Toast.makeText(context, "Publicación eliminada", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }

        // Fixed Top Header Overlay containing Reels header (Translucent, elegant vertical black gradient blur)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black.copy(alpha =  0.45f),
                            Color.Transparent
                        )
                    )
                )
                .statusBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal =  8.dp, vertical =  10.dp)
            ) {
                // Top-left group: back button + LIVE chip, so the LIVE badge never overlaps the arrow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left side: Back button
                    if (!showSearchInput) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Chip de acceso a Live: separado del botón de retroceso, alineado verticalmente
                    if (!showSearchInput) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            onClick = {
                                if (onNavigateToLive != null) onNavigateToLive?.invoke()
                            },
                            modifier = Modifier
                                .padding(horizontal =  12.dp)
                                .height(36.dp)
                                .clip(RoundedCornerShape(18.dp)),
                            color = Color.Black.copy(alpha =  0.6f),
                            contentColor = Color(0xFFFF3B5C),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .height(IntrinsicSize.Min)
                                    .padding(horizontal =  8.dp, vertical =  6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LiveTv,
                                    contentDescription = "Entrar a Panalink Live",
                                    tint = Color(0xFFFF3B5C),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "LIVE",
                                    color = Color(0xFFFF3B5C),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = TextStyle(
                                        shadow = androidx.compose.ui.graphics.Shadow(
                                            color = Color.Black.copy(alpha =  0.7f),
                                            offset = Offset(1f, 1f),
                                            blurRadius = 3f
                                        )
                                    )
                                )
                            }
                        }
                    }


                    // Center: TikTok-style feed tabs
                    if (!showSearchInput) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            ReelFeedTab(
                                label = "Para ti",
                                selected = !isDiscoveryMode,
                                onClick = { isDiscoveryMode = false }
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            ReelFeedTab(
                                label = "Explorar",
                                selected =isDiscoveryMode,
                                onClick = { isDiscoveryMode = true }
                            )
                        }
                    } else {
                        // Expanding animated premium search bar overlay
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar reels o panas venezolanos...", color = Color.Gray, fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end =  8.dp)
                                .height(48.dp),
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    showSearchInput = false
                                }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00FF85),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedContainerColor = Color.Black.copy(alpha = 0.6f),
                                unfocusedContainerColor = Color.Black.copy(alpha =  0.4f)
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                    }


                    // Right side: Refresh feed + Search trigger
                    if (!showSearchInput) {
                        IconButton(
                            onClick = {
                                viewModel.loadActiveStates(showLoading = false)
                                coroutineScope.launch {
                                    if (videoStates.isNotEmpty()) pagerState.scrollToPage(0)
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refrescar feed",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(
                            onClick = { showSearchInput = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

// Persistent Premium Floating Upload Status Card with dynamic reload on completion
        val workContext = androidx.compose.ui.platform.LocalContext.current
        var uploadWorkInfos by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf<List<androidx.work.WorkInfo>>(emptyList())
        }
        var completedWorkIds by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf<Set<java.util.UUID>>(emptySet())
        }

        androidx.compose.runtime.LaunchedEffect(Unit) {
            val workManager = androidx.work.WorkManager.getInstance(workContext)
            val liveData = workManager.getWorkInfosByTagLiveData("social_upload")
            val observer = androidx.lifecycle.Observer<List<androidx.work.WorkInfo>> { list ->
                uploadWorkInfos = list ?: emptyList()
                val newlyCompleted = list?.filter { it.state == androidx.work.WorkInfo.State.SUCCEEDED && !completedWorkIds.contains(it.id) } ?: emptyList()
                if (newlyCompleted.isNotEmpty()) {
                    completedWorkIds = completedWorkIds + newlyCompleted.map { it.id }
                    // Trigger dynamic feed refresh instantly
                    viewModel.loadActiveStates(showLoading = false)
                }
            }
            liveData.observeForever(observer)
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                liveData.removeObserver(observer)
            }
        }

        val activeUpload = uploadWorkInfos.firstOrNull {
            it.state == androidx.work.WorkInfo.State.RUNNING ||
            it.state == androidx.work.WorkInfo.State.ENQUEUED
        }

        if (activeUpload != null) {
            val uploadProgressVal = activeUpload.progress.getInt("progress", 0)

            // Minimal upload pill: slim bar + percent, tucked under the header
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Subiendo",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$uploadProgressVal%",
                        color = Color(0xFF00FF85),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { uploadProgressVal / 100f },
                    modifier = Modifier
                        .width(96.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp)),
                    color = Color(0xFF00FF85),
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
            }
        }
    }
}
}

@Composable
private fun ReelFeedTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 17.sp,
            style = TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black.copy(alpha = 0.6f),
                    offset = Offset(1f, 1f),
                    blurRadius = 4f
                )
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(width = 28.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) Color.White else Color.Transparent)
        )
    }
}

private fun formatCountCompact(value: Int): String = when {
    value >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", value / 1_000_000f).removeSuffix(".0M")
    value >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", value / 1_000f).removeSuffix(".0K")
    else -> value.toString()
}

@Composable
private fun ReelRailAction(
    icon: ImageVector,
    count: String,
    tint: Color,
    contentDescription: String,
    iconModifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = iconModifier.size(30.dp)
            )
        }
        if (count.isNotEmpty()) {
            Text(
                text = count,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.7f),
                        offset = Offset(1f, 1f),
                        blurRadius = 3f
                    )
                )
            )
        }
    }
}

@Composable
fun TikTokPageItem(
    stateWithUser: com.example.data.model.UserStateWithUser,
    viewModel: StatesViewModel,
    isActivePage: Boolean,
    isPreload: Boolean = false,
    dualManager: ReelDualPlayerManager,
    isMuted: Boolean,
    onMuteToggle: () -> Unit,
    onLikeClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onShareClick: () -> Unit,
    onCommentSubmit: (String) -> Unit,
    onViewRegistered: () -> Unit,
    onNavigateToUserProfile: ((String) -> Unit)? = null,
    onHashtagClick: ((String) -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null
) {
    val state = stateWithUser.state
    val initialProfile = stateWithUser.profile
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val diagnostics = com.example.feature.diagnostics.data.DiagnosticsRepository.getInstance(ctx)

    val context = androidx.compose.ui.platform.LocalContext.current
    val identityRepository = remember { com.example.identity.bridge.LegacyIdentityBridge(context).identityRepository }
    val initialCached = remember(state.userId) { com.example.identity.memory.IdentityMemoryCache.profiles.get(state.userId) }
    val identityState by identityRepository.observeIdentity(state.userId).collectAsStateWithLifecycle(initialValue = initialCached?.toIdentityUiState())
    
    val safeAvatarUrl = identityState?.avatarUrl ?: initialProfile?.avatarUrl
    val safeDisplayName = identityState?.displayName ?: initialProfile?.displayName ?: ""
    val safeProfileId = identityState?.userId ?: initialProfile?.id ?: state.userId

    val profilesRepo = remember { ProfilesRepository() }
    val currentUid = SupabaseClient.currentUser?.id ?: "me_demo_id"
    val scope = rememberCoroutineScope()
    var isFollowing by remember { mutableStateOf(false) }
    val isOwner = state.userId == SupabaseClient.currentUser?.id

    LaunchedEffect(state.userId) {
        profilesRepo.isFollowing(currentUid, state.userId)
            .onSuccess { isFollowing = it }
    }

    LaunchedEffect(state.id, isActivePage) {
        if (isActivePage) {
            onViewRegistered()
        }
    }

    val currentComments by viewModel.currentComments.collectAsStateWithLifecycle()
    var replyingTo by remember { mutableStateOf<com.example.data.model.Comment?>(null) }

    val localIsLiked = state.likedByMe ?: false
    val localLikesCount = state.likesCount ?: 0
    val localCommentsCount = state.commentsCount ?: 0
    val commentsList = currentComments

    val structuredComments = remember(commentsList) {
        val parents = commentsList.filter { it.parentCommentId == null }
        val childrenGrouped = commentsList.filter { it.parentCommentId != null }.groupBy { it.parentCommentId }
        buildList {
            parents.forEach { parent ->
                val activeChildren = childrenGrouped[parent.id]?.filter { it.deletedAt == null } ?: emptyList()
                val hasChildren = activeChildren.isNotEmpty()
                if (parent.deletedAt == null || hasChildren) {
                    add(parent)
                    activeChildren.forEach { child ->
                        add(child)
                    }
                }
            }
            val allAddedIds = map { it.id }.toSet()
            commentsList.forEach { comment ->
                if (comment.id !in allAddedIds && comment.deletedAt == null) {
                    add(comment)
                }
            }
        }
    }

    val localIsFavorited = state.favoritedByMe ?: false
    val localFavoritesCount = state.favoritesCount ?: 0
    val localSharesCount = state.sharesCount ?: 0

    var isPaused by remember { mutableStateOf(false) }
    
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                isPaused = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showCommentDialog by remember { mutableStateOf(false) }
    LaunchedEffect(showCommentDialog, state.id) {
        if (showCommentDialog) {
            viewModel.loadComments(state.id)
        }
    }
    var commentText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    val hearts = remember { mutableStateListOf<HeartPopState>() }
    var isFocusMode by remember { mutableStateOf(false) }
    // Acción 4: Zoom state backed by Animatable for snap-back animation.
    // scaleAnim/offsetXAnim/offsetYAnim replace the old scale/offset mutableState.
    val scaleAnim = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }
    // Acción 2: Floating reactions for reels (owner-only live reactions)
    var floatingReactions by remember { mutableStateOf(listOf<FloatingReactionLog>()) }
    val reactionScope = rememberCoroutineScope()
    // Acción 3: Smart-press zone state
    var isRewinding by remember(state.id) { mutableStateOf(false) }
    var isFastForwarding by remember(state.id) { mutableStateOf(false) }
    var seekJob by remember(state.id) { mutableStateOf<Job?>(null) }
    var hasError by remember(state.id) { mutableStateOf(false) }
    // True while a codec-recovery cycle (player recreation) is in flight; the error
    // card must NOT be shown during this window, only on definitive failure.
    var isRecovering by remember(state.id) { mutableStateOf(false) }
    // True once codec recovery has exhausted both (HW then software) attempts for
    // this reel. While set, the background auto-retry is suppressed so a
    // permanently-broken codec does not loop forever (manual "Reintentar" resets it).
    var codecIrrecoverable by remember(state.id) { mutableStateOf(false) }

    var exoPlayerRef by remember { mutableStateOf<ExoPlayer?>(null) }
    var isBuffering by remember { mutableStateOf(true) }
    var forceRotationDegrees by remember(state.id) { mutableStateOf(0f) }
    // Bumped by manual/auto retry to force the acquisition LaunchedEffect to re-run.
    var playerRefreshKey by remember(state.id) { mutableStateOf(0) }

    // Acción 2: Listen for realtime likes on this reel to show floating reactions (owner-only)
    LaunchedEffect(state.id, isOwner) {
        if (isOwner) {
            SupabaseClient.realtimeLikes.collect { update ->
                if (update.statusId == state.id && update.isReel && update.eventType == "INSERT") {
                    val reactingUserId = try {
                        update.record.optString("user_id", update.record.optString("author_id", ""))
                    } catch (_: Exception) { "" }
                    val emoji = "❤️"
                    val avatarUrl = if (reactingUserId.isNotBlank()) {
                        try {
                            identityRepository.resolveFreshAvatar(reactingUserId) ?: ""
                        } catch (_: Exception) { "" }
                    } else ""
                    val log = FloatingReactionLog(
                        id = "${System.currentTimeMillis()}_${reactingUserId}",
                        avatarUrl = avatarUrl,
                        emoji = emoji,
                        userId = reactingUserId
                    )
                    reactionScope.launch {
                        floatingReactions = floatingReactions + log
                    }
                }
            }
        }
    }

    // El feed remoto puede re-emitir la fila con mediaUrl distinta (re-anclaje CDN,
    // URL firmada expirada, refresco de red). Resolver/player solo dependen
    // del puntero estable (vcdn_video_id o copia local): así la reproducción en
    // curso jamás se reinicia por un mediaUrl rotatorio.
    val stableMediaUrl = remember(state.id, state.vcdnVideoId, state.localVideoPath, state.mediaUrl) {
        // La copia local solo se usa OFFLINE. En línea SIEMPRE stream remoto:
        // una copia dañada/incompleta (descarga cancelada a medias) reproducida
        // en línea era la causa de CodecException 4006 y "Reintentar" a cada rato..
        if (!com.example.util.NetworkMonitor.isOnline.value) {
            val local = state.localVideoPath?.takeIf { it.isNotBlank() && java.io.File(it).exists() && it.length > 100_000L }
            if (local != null) return@remember local
        }
        if (!state.vcdnVideoId.isNullOrBlank()) "vcdn://${state.vcdnVideoId}"
        else state.mediaUrl
    }

    // Cached rotation lookup avoids a network metadata fetch on every page
    // re-composition during fast swipes. Cache key is stableMediaUrl (vcdn_video_id
    // or local path) so it works even before HTTP resolution completes.
    val cachedRotation = com.example.core.media.VideoMetadataCache.getRotation(stableMediaUrl ?: "")
    LaunchedEffect(cachedRotation, stableMediaUrl) {
        if (cachedRotation != null) {
            if (cachedRotation != 0f) forceRotationDegrees = cachedRotation
        } else if (isActivePage && !isPreload && stableMediaUrl != null) {
            // Metadata remota: secundaria y NO bloquear para preload.
            // Sólo para páginas activas donde el player necesita rotación correcta.
            val url = withContext(kotlinx.coroutines.Dispatchers.IO){
                com.example.data.repository.CdnManager.resolveMediaUrl(stableMediaUrl)
            } ?: return@LaunchedEffect
            if (url.isEmpty() || !url.startsWith("http")) return@LaunchedEffect

            withContext(Dispatchers.IO) {
                var retriever: android.media.MediaMetadataRetriever? = null
                try {
                    retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(url, HashMap<String, String>())
                    val rotationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    val widthStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    val heightStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)

                    val rotation = rotationStr?.toIntOrNull() ?: 0
                    val width = widthStr?.toIntOrNull() ?: 0
                    val height = heightStr?.toIntOrNull() ?: 0

                    val neededRotation = if (width > height && (rotation == 0 || rotation == 180)) 90f else 0f
                    com.example.core.media.VideoMetadataCache.putRotation(stableMediaUrl!!, neededRotation)
                    if (neededRotation != 0f) {
                        withContext(Dispatchers.Main) {
                            forceRotationDegrees = neededRotation
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TikTokVideoFeedScreen", "Error retrieving video metadata for $url", e)
                } finally {
                    try {
                        retriever?.release()
                    } catch (e: Exception) {}
                }
            }
        }
    }

    var resolvedUrl by remember(stableMediaUrl, state.id) { mutableStateOf<String?>(null) }
    var resolveFailed by remember(stableMediaUrl, state.id) { mutableStateOf(false) }
    var retryCount by remember(stableMediaUrl, state.id) { mutableStateOf(0) }
    var activeSlot by remember(stableMediaUrl, state.id) { mutableStateOf<ReelDualPlayerManager.Slot?>(null) }
    // Guard against concurrent retry attempts on the same player
    var isRetrying by remember(state.id) { mutableStateOf(false) }
    // Track first READY to report first-frame latency
    val isFirstReady = remember(state.id) { java.util.concurrent.atomic.AtomicBoolean(false) }
    // Track when buffering state started, for accurate buffering duration
    var bufferingStartMs by remember(state.id) { mutableStateOf<Long?>(null) }

    // Player acquisition depends ONLY on resolved URL, NOT on metadata.
    // Metadata extraction is deferred to a secondary LaunchedEffect above so
    // it never blocks playback preparation.
    LaunchedEffect(stableMediaUrl, state.id,isActivePage,isPreload,dualManager,playerRefreshKey,com.example.util.NetworkMonitor.isOnline.value) {

        // BUGFIX: reiniciar el estado de resolución solo CUANDO el puntero estable
        // cambia (otra fila, copia local nueva, o reel distinto). Un REPLACE de fila
        // con mediaUrl rotatorio deja intacto el player que ya está sonando.
        // retryCount NO está en esta key: el retry no debe re-resolver ni re-acquire,
        // solo re-preparar el player existente (ver LaunchedEffect de listener abajo).
        if (state.localVideoPath.isNullOrEmpty() && stableMediaUrl.isNullOrBlank()) return@LaunchedEffect
        val isVcdn = com.example.data.repository.VcdnUrlResolver.isVcdnUrl(stableMediaUrl)
        val resolveStart = if (isVcdn) System.currentTimeMillis() else 0L
        if (isVcdn) diagnostics.record(
            DiagnosticCategory.NETWORK,
            "VCDN resolve iniciado",
            correlationId = state.id.take(36)
        )
        resolvedUrl = if (!com.example.util.NetworkMonitor.isOnline.value && !state.localVideoPath.isNullOrBlank()){
            val local = state.localVideoPath!!.takeIf { java.io.File(it).exists() && it.length > 100_000L }
            if (local == null) {
                resolveFailed = true
                return@LaunchedEffect
            }
            local
        } else {
            val resolved =withContext(Dispatchers.IO) { com.example.data.repository.CdnManager.resolveMediaUrl(stableMediaUrl)}
            resolved.takeIf { !it.startsWith("vcdn://") }
        }
        if (isVcdn && resolveStart > 0L) {
            val duration = System.currentTimeMillis() - resolveStart
            diagnostics.record(
                DiagnosticCategory.NETWORK,
                "VCDN resolve completado",
                correlationId = state.id.take(36),
                durationMs = duration,
                severity = if (resolvedUrl == null) DiagnosticSeverity.WARNING else DiagnosticSeverity.INFO
            )
        }
        if (resolvedUrl == null) resolveFailed = true

        // BUGFIX: al perder red el sub-guard (isOnline) reinicia esté LaunchedEffect
        // con resolvedUrl ya resuelto; si el slot ya está en A no re-resolvemos world
        val wasReused = activeSlot != null || exoPlayerRef != null
        val prev = activeSlot
        val hasLivePlayer = exoPlayerRef != null
        if (!hasLivePlayer && prev != null && prev != ReelDualPlayerManager.Slot.B) return@LaunchedEffect
        val url = resolvedUrl ?: return@LaunchedEffect
        val acquireStart = System.currentTimeMillis()
        // Con la ventana de preload ampliada a +2, una página preload puede no
        // encontrar slot libre al primer intento (ambos slots ocupados). Reintentar
        // en un loop corto: cuando el slot del reel anterior se libere (al avanzar),
        // esta página lo toma en <200ms y arranca su preload-muted en segundo plano.
        // El effect se cancela solo al cambiar isPreload/isActivePage (la página
        // sale del window) o si la clave del ID cambia.
        var slot = dualManager.acquireOrReuse(
            state.id,
            url,
            isActivePage,
            if (isMuted) 0f else  1f
        )
        var retryTicks = 0
        while (slot == null && !isActivePage) {
            if (retryTicks >= 100) return@LaunchedEffect
            kotlinx.coroutines.delay(120L)
            slot = dualManager.acquireOrReuse(
                state.id,
                url,
                false,
                if (isMuted) 0f else 1f
            )
            retryTicks++
        }
        if (slot == null) return@LaunchedEffect
        activeSlot = slot
        exoPlayerRef = dualManager.playerFor(slot)
        // --- FIX: flags de error/buffering pegajosos al volver a una página ya
        // prepeada. Cuando un reel pasa activo→inactivo→activo (swipe atrás), el
        // player sigue en STATE_READY (solo se pausó con playWhenReady=false), así que
        // onPlaybackStateChanged(STATE_READY) no se re-emite y los flags de error/
        // resolveFailed quedarían pegados mostrando la tarjeta de error sobre un
        // vídeo que sí está sano y sonando debajo..
        if (wasReused && isActivePage && !isPreload) {
            val p = dualManager.playerFor(slot)
            if (p != null) {
                when (p.playbackState) {
                    Player.STATE_READY -> {
                        hasError = false
                        resolveFailed = false
                        isRetrying = false
                    }
                    Player.STATE_IDLE, Player.STATE_ENDED -> {
                        // El player quedó muerto (IDLE tras error fatal, o ENDED). Re-vivirlo
                        // con un prepare() preservando posición — menos destructivo que
                        // release+re-acquire; playWhenReady lo aplica el Effect de abajo.

                        val pos = p.currentPosition
                        try {
                            p.prepare()
                            p.seekTo(pos)
                            hasError = false
                            resolveFailed = false
                            isRetrying = false
                        } catch (e: IllegalStateException) {
                            android.util.Log.w("TikTokVideoFeedScreen", "reactivar prepare skipped: player stale", e)
                        }
                    }
                    else -> {}
                }
            }
        }
        diagnostics.record(
            DiagnosticCategory.EXOPLAYER,
            if (wasReused) "Player reuse" else "Player acquire",
            correlationId = state.id.take(36),
            durationMs = System.currentTimeMillis() - acquireStart
        )
    }

    // NOTE: forced offline copy (ensureLocalCopy) removed 2026-09-11:
    // it re-downloaded the SAME video in parallel while the player was streaming it,
    // saturating the network (stutter/slow start) and left half-downloaded copies
    // that playback preferred, causing CodecException 4006 and "Reintentar".
    // The player SimpleCache still gives natural offline for what you watched..

    // ---------------------------------------------------------------------------
    // Codec/decoder error recovery.
    //
    // A MediaCodec/CodecException leaves the ExoPlayer instance in a state where
    // player.prepare() on the SAME player cannot recover (the decoder is poisoned),
    // which is the root cause of the "black screen + IllegalStateException 1004"
    // loop. Recovery here means: release the bad player, build a FRESH one (the
    // dual manager keeps the slot count at 2), rebind it to the PlayerView, and
    // consider it recovered ONLY once STATE_READY is reached — never after a bare
    // prepare(). Attempt 1 uses the normal hardware+FFmpeg-fallback renderers;
    // attempt 2 escalates to software-preferred (FFmpeg-first) renderers.
    //
    // This is intentionally centralized here (driving ReelDualPlayerManager which
    // does the mechanical recreate) so TikTokPageItem owns the READY-or-escalate
    // loop and the UI states (RECOVERING -> READY / DEFINITIVE_ERROR).
    // ---------------------------------------------------------------------------
    fun handleCodecError(error: androidx.media3.common.PlaybackException) {
        if (isRetrying || isRecovering) return
        isRetrying = true
        isRecovering = true
        hasError = false
        codecIrrecoverable = false
        val volume = if (isMuted) 0f else 1f
        diagnostics.record(
            DiagnosticCategory.ERRORS,
            "CodecException detectado",
            severity = DiagnosticSeverity.WARNING,
            correlationId = state.id.take(36),
            details = "causeType=${error.cause?.javaClass?.simpleName}, errorCode=${error.errorCode}, isCodec=${dualManager.isCodecInitializationError(error)}"
        )

        scope.launch {
            var recovered = false
            var attempt = 0
            // At most 2 recreations per error event: hardware, then software fallback.
            while (attempt < 2 && !recovered) {
                attempt++
                when (val result = dualManager.recoverFromPlaybackError(state.id, error, volume)) {
                    is ReelDualPlayerManager.RecoveryResult.Recovered -> {
                        val newPlayer = result.player
                        diagnostics.record(
                            DiagnosticCategory.EXOPLAYER,
                            "Recovery iniciado",
                            correlationId = state.id.take(36),
                            details = "attempt=$attempt, recoveryType=RECREATE_PLAYER, renderer=${result.rendererMode}"
                        )
                        // Bind the NEW player to the PlayerView. Reassigning
                        // exoPlayerRef also re-runs the listener LaunchedEffect below,
                        // so a fresh listener attaches to the new player — the View
                        // is never left pointing at the released player.
                        exoPlayerRef = newPlayer
                        // Confirm readiness ONLY via STATE_READY (or a fresh error),
                        // never via prepare() completion. A short-lived listener is
                        // attached and removed before continuing so the steady-state
                        // listener LaunchedEffect owns the player going forward.
                        val ready = run {
                            val deferred = CompletableDeferred<Boolean>()
                            val tempListener = object : Player.Listener {
                                override fun onPlaybackStateChanged(playbackState: Int) {
                                    if (!deferred.isCompleted && playbackState == Player.STATE_READY) {
                                        deferred.complete(true)
                                    }
                                }
                                override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
                                    if (!deferred.isCompleted) deferred.complete(false)
                                }
                            }
                            newPlayer.addListener(tempListener)
                            try {
                                withTimeoutOrNull(RECOVERY_READY_TIMEOUT_MS) { deferred.await() } ?: false
                            } finally {
                                newPlayer.removeListener(tempListener)
                            }
                        }
                        if (ready) {
                            recovered = true
                            diagnostics.record(
                                DiagnosticCategory.EXOPLAYER,
                                "Recovery READY",
                                correlationId = state.id.take(36),
                                details = "attempt=$attempt, renderer=${result.rendererMode}"
                            )
                        } else {
                            diagnostics.record(
                                DiagnosticCategory.ERRORS,
                                "Recovery attempt fallido",
                                severity = DiagnosticSeverity.WARNING,
                                correlationId = state.id.take(36),
                                details = "attempt=$attempt, renderer=${result.rendererMode}"
                            )
                            // Loop: the manager escalates the renderer (HW->SW) and/or
                            // returns Exhausted on the next call.
                        }
                    }
                    is ReelDualPlayerManager.RecoveryResult.Exhausted -> {
                        diagnostics.record(
                            DiagnosticCategory.ERRORS,
                            "Recovery agotado",
                            severity = DiagnosticSeverity.ERROR,
                            correlationId = state.id.take(36),
                            details = "attempt=$attempt"
                        )
                        break
                    }
                    is ReelDualPlayerManager.RecoveryResult.NotACodecError -> {
                        // Defensive: we already filtered non-codec errors.
                        break
                    }
                }
            }
            isBuffering = false
            isRecovering = false
            isRetrying = false
            if (recovered) {
                hasError = false
            } else {
                codecIrrecoverable = true
                hasError = true
                diagnostics.record(
                    DiagnosticCategory.ERRORS,
                    "Recovery definitiva fallida",
                    severity = DiagnosticSeverity.ERROR,
                    correlationId = state.id.take(36),
                    details = "attempts=$attempt"
                )
            }
        }
    }

    LaunchedEffect(exoPlayerRef, state.id) {
        val player = exoPlayerRef ?: return@LaunchedEffect
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE) && !hasError
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        // Only record BUFFERING START on real transition into buffering
                        if (bufferingStartMs == null) {
                            bufferingStartMs = System.currentTimeMillis()
                            diagnostics.record(
                                DiagnosticCategory.EXOPLAYER,
                                "Buffering START",
                                correlationId = state.id.take(36)
                            )
                        }
                    }
                    Player.STATE_READY -> {
                        // Record BUFFERING END if we were buffering
                        val start = bufferingStartMs
                        if (start != null) {
                            val duration = System.currentTimeMillis() - start
                            diagnostics.record(
                                DiagnosticCategory.EXOPLAYER,
                                "Buffering END",
                                correlationId = state.id.take(36),
                                durationMs = duration
                            )
                            bufferingStartMs = null
                        }
                        hasError = false
                        resolveFailed = false
                        retryCount = 0
                        isRetrying = false
                        isRecovering = false
                        codecIrrecoverable = false
                        if (isFirstReady.compareAndSet(false, true)) {
                            diagnostics.record(
                                DiagnosticCategory.EXOPLAYER,
                                "First READY",
                                correlationId = state.id.take(36)
                            )
                        }
                    }
                    Player.STATE_ENDED -> diagnostics.record(
                        DiagnosticCategory.EXOPLAYER,
                        "Playback END",
                        correlationId = state.id.take(36)
                    )
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("TikTokVideoFeedScreen", "player error id=${state.id} code=${error.errorCode}", error)
                isBuffering = false
                // Errores en preload: liberar el slot en silencio (sin recovery
                // visible ni cartel) — el swipe lo re-adquirirá limpio.

                if (isPreload) {
                    dualManager.releaseIfOwned(state.id)
                    return
                }
                // Issue #12: capturar HTTP status real del error para saber si el
                // servidor devuelve 403, 404, 416, 5xx, etc. Instrumentación pura:
                // se inspecciona la cadena de causas del PlaybackException en busca
                // de InvalidResponseCodeException (Media3 HttpDataSource).
                var httpStatus: Int? = null
                var causeType: String = "null"
                var currentCause: Throwable? = error.cause
                while (currentCause != null) {
                    val cause = currentCause
                    causeType = cause.javaClass.simpleName
                    if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                        httpStatus = cause.responseCode
                        break
                    }
                    currentCause = cause.cause
                }
                diagnostics.record(
                    DiagnosticCategory.ERRORS,
                    "Player error",
                    severity = DiagnosticSeverity.ERROR,
                    correlationId = state.id.take(36),
                    details = "causeType=$causeType, httpStatus=${httpStatus ?: "N/A"}, errorCode=${error.errorCode}, retryCount=$retryCount"
                )
                // --- Codec/decoder failure (MediaCodec.CodecException / Decoder init) ---
                // This is NOT a network/HTTP error, so the generic prepare()-retry below
                // must NEVER run: calling prepare() on a poisoned ExoPlayer (decoder in a
                // stuck state) yields the CodecException->prepare() -> Buffering ->
                // IllegalStateException 1004 -> definitive-error loop. Instead, release the
                // bad player and build a fresh one (delegated to ReelDualPlayerManager),
                // which rebinds the PlayerView to the new instance and waits for READY.
                if (dualManager.isCodecInitializationError(error)) {
                    // If the brand-new player from a previous cycle also failed with a codec
                    // error, the recovery coroutine is already driving escalation; skip the
                    // re-entrant call so we never nest prepare()/recreate on the same slot.
                    if (isRetrying || isRecovering) return
                    handleCodecError(error)
                    return // Do NOT fall through to 401/network retry for codec errors.
                }
                // Issue #15: 401 recovery for VCDN signed URLs.
                // The BFF may report a long expiry, but the CDN token actually
                // expires ~60s into playback. When the CDN returns 401 mid-stream,
                // force a fresh resolution (bypassing the stale cache) and update
                // the MediaItem URI on the active player, preserving position
                // and playWhenReady. This does NOT touch ReelDualPlayerManager
                // architecture — refreshActiveUrl is a targeted addition that
                // mirrors the existing pause() URL-swap pattern.
                if (httpStatus == 401 && com.example.data.repository.VcdnUrlResolver.isVcdnUrl(stableMediaUrl) &&
                    !isRetrying && com.example.util.NetworkMonitor.isOnline.value && retryCount < 2) {
                    isRetrying = true
                    retryCount += 1
                    val currentPos = player.currentPosition
                    diagnostics.record(
                        DiagnosticCategory.NETWORK,
                        "401 recovery started",
                        correlationId = state.id.take(36),
                        details = "attempt=$retryCount, position=$currentPos"
                    )
                    scope.launch {
                        val refreshStart = System.currentTimeMillis()
                        var recovered = false
                        try {
                            val freshUrl = com.example.data.repository.CdnManager.resolveMediaUrlFresh(stableMediaUrl ?: "")
                            if (!freshUrl.isNullOrBlank() && freshUrl.startsWith("http") && freshUrl != resolvedUrl) {
                                val updated = dualManager.refreshActiveUrl(state.id, freshUrl)
                                if (updated) {
                                    resolvedUrl = freshUrl
                                    recovered = true
                                    diagnostics.record(
                                        DiagnosticCategory.NETWORK,
                                        "401 recovery completed",
                                        correlationId = state.id.take(36),
                                        durationMs = System.currentTimeMillis() - refreshStart,
                                        details = "attempt=$retryCount, position=$currentPos"
                                    )
                                } else {
                                    diagnostics.record(
                                        DiagnosticCategory.ERRORS,
                                        "401 recovery failed",
                                        severity = DiagnosticSeverity.WARNING,
                                        correlationId = state.id.take(36),
                                        details = "attempt=$retryCount, urlNotUpdated=true"
                                    )
                                }
                            } else {
                                diagnostics.record(
                                    DiagnosticCategory.ERRORS,
                                    "401 recovery failed",
                                    severity = DiagnosticSeverity.WARNING,
                                    correlationId = state.id.take(36),
                                    details = "attempt=$retryCount, noFreshUrl=${freshUrl.isNullOrBlank()}"
                                )
                            }
                        } catch (e: Exception) {
                            diagnostics.record(
                                DiagnosticCategory.ERRORS,
                                "401 recovery failed",
                                severity = DiagnosticSeverity.WARNING,
                                correlationId = state.id.take(36),
                                details = "attempt=$retryCount, exception=${e.javaClass.simpleName}"
                            )
                        } finally {
                            isRetrying = false
                            if (!recovered) {
                                hasError = true
                            }
                        }
                    }
                    return  // Don't fall through to generic retry — URL refresh is in flight
                }
                // OFFLINE FIX: solo reintentar con red disponible y no si ya está en curso un retry
                if (!isRetrying && retryCount < 2 && com.example.util.NetworkMonitor.isOnline.value && exoPlayerRef == player) {
                    isRetrying = true
                    retryCount += 1
                    diagnostics.record(
                        DiagnosticCategory.EXOPLAYER,
                        "Retry iniciado",
                        correlationId = state.id.take(36),
                        details = "attempt=$retryCount"
                    )
                    // Retry REAL: re-preparar el player existente preservando posición.
                    // acquireOrReuse no reprepa si el slot+URL son los mismos, así que
                    // forzamos prepare() explícitamente aquí.
                    val currentPos = player.currentPosition
                    val wasPlaying = player.playWhenReady
                    val prepareSkipped = try {
                        player.prepare()
                        player.seekTo(currentPos)
                        player.playWhenReady = isActivePage && !isPaused && wasPlaying
                        false
                    } catch (e: IllegalStateException) {
                        android.util.Log.w("TikTokVideoFeedScreen", "Retry skipped: player stale/released", e)
                        true
                    }
                    if (prepareSkipped) {
                        // FIX: si el player existente no puede re-prepararse (estado interno
                        // medio muerto tras el error), NO mostrar error definitivo a secas:
                        // degradar al MISMO recovery limpio que usa el botón Reintentar —
                        // release del slot + bump de adquisición para re-resolver/
                        // re-acquire un player fresco. Así el reintento automático funciona
                        // donde antes dejaba la tarjeta de error hasta pulsar manualmente.
                        isRetrying = false
                        hasError = false
                        isBuffering = true
                        resolveFailed = false
                        dualManager.releaseIfOwned(state.id)
                        activeSlot = null
                        exoPlayerRef = null
                        playerRefreshKey++
                        diagnostics.record(
                            DiagnosticCategory.EXOPLAYER,
                            "Retry degradado a re-acquire limpio",
                            correlationId = state.id.take(36),
                            details = "retryCount=$retryCount, stalePlayer=true"
                        )
                    } else {
                        diagnostics.record(
                            DiagnosticCategory.EXOPLAYER,
                            "Retry completado (prepare)",
                            correlationId = state.id.take(36),
                            details = "pos=$currentPos, wasPlaying=$wasPlaying"
                        )
                    }
                } else {
                    hasError = true
                    isRetrying = false
                    diagnostics.record(
                        DiagnosticCategory.ERRORS,
                        "Error definitivo después de reintentos",
                        severity = DiagnosticSeverity.ERROR,
                        correlationId = state.id.take(36),
                        details = "retryCount=$retryCount"
                    )
                }
            }
        }
        player.addListener(listener)
        try {
            awaitCancellation()
        } finally {
            player.removeListener(listener)
        }
    }

    // VCDN signed-URL refresh policy (Issue #15 revisited): NO preventive refresh.
    // The BFF mints a signed HLS streamUrl with an [expires] in the player-config. The
    // old preventive mid-playback URL swap tore down the decoder pipeline in flight,
    // causing CodecException 4003/4006, black flashes and stalls on hardware codecs.
    // Now playback runs from the CDN long-lived HLS segments; ONLY when the signed token
    // really expires mid-stream does the reactive 401 handler in onPlayerError resolve
    // a fresh URL and swap it preserving position, recovering with minimal buffering — safe path.

    DisposableEffect(state.id) {
        onDispose {
            // Si esta página sale del window de preload, liberar su slot (el código
            // del dual-manager lo reutiliza para el siguiente Reel).. Nunca liberamos
            // el player del feed salvo al salir del feed (releaseAll en el feed-level).
            dualManager.releaseIfOwned(state.id)
            if (activeSlot == ReelDualPlayerManager.Slot.A || activeSlot == ReelDualPlayerManager.Slot.B)
                exoPlayerRef = null
        }
    }

    LaunchedEffect(isMuted, isActivePage, exoPlayerRef) {
        // El volumen real solo aplica a la página ACTIVA. Los preloads se quedan
        // a volume=0 SIEMPRE (preload-muted del manager) para que nada se oiga en
        // segundo plano mientras el reel actual reproduce.
        if (exoPlayerRef != null) {
            exoPlayerRef?.volume = if (isActivePage && !isMuted) 1f else 0f
        }
    }

    // FIX: al volver a una página activa, resetear el pausado manual (isPaused(
    // que era `remember` sin key y quedaba pegado al salir/vovler, mostrando el
    // ícono de play sobre un vídeo que el usuario no pausó en esta visita.
    // Los preloads NO se tocan: el manager los deja en preload-muted
    // (playWhenReady=true, volume=0) para que su frame ya esté decodificado
    // cuando la página se vuelva activa (cambio instantáneo sin black screen).
    LaunchedEffect(isPaused, isActivePage, isPreload, exoPlayerRef) {
        when {
            isActivePage -> exoPlayerRef?.playWhenReady = !isPaused
            !isPreload -> exoPlayerRef?.playWhenReady = false
            // else: página en preload → no tocar (el manager controla su mute/play)
        }
    }

    LaunchedEffect(isActivePage) {
        // Limpiar la pausa manual SOLO al volver a esta página activa
        // (transición inactiva->activa); NO en cada toggle del usuario, que
        // rompía el botón play/pause (el effect anterior estaba keyed en
        // isPaused y lo reseteaba a false inmediatamente tras pulsarlo).
        if (isActivePage) isPaused = false
    }

    // Update video play position and total duration in real-time
    LaunchedEffect(isActivePage, exoPlayerRef, isPaused, isDraggingSlider) {
        if (isActivePage && exoPlayerRef != null && !isDraggingSlider) {
            while (true) {
                currentPosition = exoPlayerRef?.currentPosition ?: 0L
                duration = exoPlayerRef?.duration ?: 0L
                kotlinx.coroutines.delay(200)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val controlsAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isFocusMode) 0.05f else 1f,
            animationSpec = androidx.compose.animation.core.tween(300),
            label = "controlsAlpha"
        )

        // 1. Full screen video background / skeleton / error states
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.id) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            if (state.likedByMe != true) {
                                onLikeClick()
                            }
                            hearts.add(HeartPopState(id = System.currentTimeMillis(), x = offset.x, y = offset.y))
                        },
                        onTap = {
                            isPaused = !isPaused
                        },
                        onPress = { offset ->
                            var isReleased = false
                            val screenWidth = size.width
                            val zone = when {
                                offset.x < screenWidth * 0.25f -> "rewind"
                                offset.x > screenWidth * 0.75f -> "forward"
                                else -> "center"
                            }
                            val longPressJob = coroutineScope.launch {
                                kotlinx.coroutines.delay(350L)
                                if (!isReleased) {
                                    when (zone) {
                                        "forward" -> {
                                            isFastForwarding = true
                                            exoPlayerRef?.setPlaybackParameters(PlaybackParameters(2f))
                                        }
                                        "center" -> {
                                            isFocusMode = true
                                        }
                                        "rewind" -> {
                                            isRewinding = true
                                            seekJob?.cancel()
                                            seekJob = coroutineScope.launch {
                                                while (isActive && !isReleased) {
                                                    exoPlayerRef?.let { player ->
                                                        player.seekTo(kotlin.math.max(0L, player.currentPosition - 1000))
                                                    }
                                                    kotlinx.coroutines.delay(100)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            try {
                                awaitRelease()
                            } finally {
                                isReleased = true
                                longPressJob.cancel()
                                // Revert all zone effects on release
                                when (zone) {
                                    "forward" -> {
                                        isFastForwarding = false
                                        exoPlayerRef?.setPlaybackParameters(PlaybackParameters(1f))
                                    }
                                    "center" -> {
                                        isFocusMode = false
                                    }
                                    "rewind" -> {
                                        isRewinding = false
                                        seekJob?.cancel()
                                        seekJob = null
                                    }
                                }
                            }
                        }
                    )
                }
        ) {
            if (exoPlayerRef != null && (isActivePage || isPreload)) {
                val hasLocalCopy = !state.localVideoPath.isNullOrBlank() && java.io.File(state.localVideoPath!!).exists()
                if (!com.example.util.NetworkMonitor.isOnline.value && (isActivePage || isPreload) && !hasError && !resolveFailed && !isRecovering && !codecIrrecoverable && !hasLocalCopy) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F10)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "📵",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 36.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Sin conexión",
                            color = Color.White.copy(alpha =  0.7f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Este vídeo requiere internet. Navega o sal cuando quieras.",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else if (isActivePage && isRecovering) {
                    // RECOVERING state: a codec recovery cycle (player recreation) is in
                    // flight. Do NOT show the error card here — only show it once
                    // recovery has definitively failed (hasError stays false during this).
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0F0F10)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF00FF85), strokeWidth = 3.dp)
                        }
                    }
                } else if (isActivePage && (hasError || resolveFailed) && !isRecovering) {
                    ReelsErrorView(
                        avatarUrl = safeAvatarUrl,
                        displayName = safeDisplayName ?: "",
                        onRetry = {
                            // Manual retry must perform a REAL recovery, not a bare
                            // prepare() on a potentially-damaged player: release the
                            // poisoned slot (resets the manager's per-slot attempt budget)
                            // and bump the acquisition key so the feed re-acquires a fresh
                            // ExoPlayer; subsequent codec errors are caught by handleCodecError.
                            hasError = false
                            isRecovering = false
                            codecIrrecoverable = false
                            isBuffering = true
                            resolveFailed = false
                            retryCount = 0
                            resolvedUrl = null
                            dualManager.releaseIfOwned(state.id)
                            activeSlot = null
                            exoPlayerRef = null
                            playerRefreshKey++
                        }
                    )
                    // Auto-retry: attempt reconnection every 5s while error persists.
                    // Mirrors the original network/401 retry semantics (release the
                    // slot + bump the acquisition key to re-resolve and re-acquire a
                    // fresh player). The ONLY additions vs. the original:
                    //  (a) the codec guard `!codecIrrecoverable` so a decoder that is
                    //      definitively unrecoverable (both HW and software recreate
                    //      attempts failed in handleCodecError) does NOT auto-loop —
                    //      that would spin forever recreating players that can't
                    //      initialize a codec. Manual "Reintentar" resets
                    //      codecIrrecoverable and is always available.
                    // Codec errors never reach this branch: handleCodecError owns
                    // that path and sets hasError=false on success (so this Effect
                    // won't fire) or codecIrrecoverable=true on exhaustion (blocked
                    // by the guard above).
                    // FIX: el auto-retry también debe disparar si solo quedó
                    // resolveFailed=true (resolución BFF falló transitoriamente sin
                    // stale cache). Antes solo `hasError` lo disparaba, dejando una
                    // tarjeta de error eterna hasta pulsar Reintentar manual.

                    LaunchedEffect(hasError, resolveFailed) {
                        kotlinx.coroutines.delay(5000)
                        if ((hasError || resolveFailed) && !codecIrrecoverable) {
                            hasError = false
                            isBuffering = true
                            resolveFailed = false
                            retryCount = 0
                            resolvedUrl = null
                            // Force re-acquisition on auto-retry too: releasing the
                            // slot + nulling exoPlayerRef would leave the reel blank
                            // with NO re-acquisition, because the acquisition
                            // LaunchedEffect is keyed on playerRefreshKey. Bump it so
                            // the feed re-resolves and re-acquires a fresh player.
                            // (Codec errors never reach here: handleCodecError owns
                            //  that path and sets codecIrrecoverable=true, which the
                            //  guard above already blocks.)
                            dualManager.releaseIfOwned(state.id)
                            activeSlot = null
                            exoPlayerRef = null
                            playerRefreshKey++
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(state.id) {
                                 detectTransformGesturesCustom(
                                    onGesture = { _, pan, zoom, _ ->
                                        scope.launch {
                                            val targetScale = (scaleAnim.value * zoom).coerceIn(1f, 5f)
                                            scaleAnim.snapTo(targetScale)
                                            if (targetScale > 1f) {
                                                offsetXAnim.snapTo(offsetXAnim.value + pan.x)
                                                offsetYAnim.snapTo(offsetYAnim.value + pan.y)
                                            } else {
                                                offsetXAnim.snapTo(0f)
                                                offsetYAnim.snapTo(0f)
                                            }
                                        }
                                    },
                                    currentScale = { scaleAnim.value },
                                    onEnd = {
                                        // Acción 4: Snap-back to 1f / 0f when all fingers released
                                        if (scaleAnim.value > 1.01f) {
                                            scope.launch {
                                                scaleAnim.animateTo(
                                                    targetValue = 1f,
                                                    animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
                                                )
                                                offsetXAnim.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
                                                )
                                                offsetYAnim.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    player = exoPlayerRef
                                }
                            },
                            update = { playerView ->
                                playerView.player = exoPlayerRef
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .align(Alignment.Center)
                                 .graphicsLayer {
                                     scaleX = scaleAnim.value
                                     scaleY = scaleAnim.value
                                     translationX = offsetXAnim.value
                                     translationY = offsetYAnim.value
                                     if (forceRotationDegrees != 0f) {
                                         rotationZ = forceRotationDegrees
                                     }
                                 }
                        )

                        if (isBuffering && isActivePage && currentPosition == 0L) {
                            ReelsSkeletonLoader(
                                avatarUrl = safeAvatarUrl,
                                displayName = safeDisplayName ?: ""
                            )
                        }

                        // Badge amistoso cuando el reel se está reproduciendo desde la copia
                        // local (ROM) sin conexión: el vídeo sigue visible y el usuario
                        // sabe que está viendo contenido guardado..
                        if (!com.example.util.NetworkMonitor.isOnline.value && hasLocalCopy && !hasError && !isBuffering) {
                            Surface(
                                color = Color.Black.copy(alpha =  0.55f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(50.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom =  14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal =  12.dp, vertical =  6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "📵",
                                        fontSize =  13.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Viendo desde tu copia guardada sin conexión",
                                        color = Color.White.copy(alpha =  0.9f),
                                        fontSize =  12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Placeholder skeleton for non-active or preparing pages
                ReelsSkeletonLoader(
                    avatarUrl = safeAvatarUrl,
                    displayName = safeDisplayName ?: ""
                )
            }
        }

        val metadata = parseStateMetadata(state.caption)
        RenderOverlays(metadata.overlaysBase64)

        // Render any active floating heart animations from double taps
        hearts.forEach { heart ->
            key(heart.id) {
                FloatingHeart(
                    heart = heart,
                    onAnimationEnd = {
                        hearts.remove(heart)
                    }
                )
            }
        }

        // Acción 2: Floating reactions from live likes (owner-only)
        if (floatingReactions.isNotEmpty()) {
            FloatingReactionsContainer(
                reactions = floatingReactions,
                onDismiss = { id ->
                    floatingReactions = floatingReactions.filterNot { it.id == id }
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // Animated play/pause central overlay icon
        if (isPaused && !hasError) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(80.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Pausado",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // Smart-press feedback: manteniendo un lateral se adelanta (⏩ 2x) o
        // retrocede (⏪); el video NUNCA se pausa por este gesto (solo cambia la
        // velocidad/posición, y el volumen/playWhenReady de los reels son ajenos).
        if (isFastForwarding || isRewinding) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(96.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isFastForwarding) {
                        Icon(
                            imageVector = Icons.Rounded.FastForward,
                            contentDescription = "Adelantando",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("2x", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.FastRewind,
                            contentDescription = "Retrocediendo",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("⏪", color = Color.White, fontSize = 16.sp)
                    }
                }
            }
        }

        // Bottom and Right content overlay with a smooth cinematic gradient vignette
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.05f),
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.85f)
                        ),
                        startY = 150f
                    )
                )
        )

        // 2. Right side action rail (TikTok-style: creator avatar + actions, compact)
        var showActionMoreMenu by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(bottom = 48.dp, end = 4.dp)
                .graphicsLayer(alpha = controlsAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Creator Avatar with Follow Plus Badge
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clickable {
                        val targetId = safeProfileId.ifBlank { state.userId }
                        if (targetId.isNotBlank()) {
                            onNavigateToUserProfile?.invoke(targetId)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                PanaAvatar(
                    avatarUrl = safeAvatarUrl,
                    modifier = Modifier
                        .size(48.dp),
                    size = 48.dp,
                    borderWidth = 1.dp,
                    borderColor = Color.White,
                    contentDescription = "Perfil del creador",
                    placeholderName = safeDisplayName
                )
                if (!isOwner && !isFollowing) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .align(Alignment.BottomCenter)
                            .offset(y = 8.dp)
                            .background(Color(0xFFFF2B54), CircleShape)
                            .clickable {
                                performHaptic(context)
                                scope.launch {
                                    profilesRepo.followUser(currentUid, safeProfileId)
                                    isFollowing = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Seguir",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Like with elastic scale pop
            val likeScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (localIsLiked) 1.25f else 1f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                ),
                label = "likeScale"
            )
            ReelRailAction(
                icon = if (localIsLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                count = formatCountCompact(localLikesCount),
                tint = if (localIsLiked) Color(0xFFFF2B54) else Color.White,
                contentDescription = "Me Gusta",
                iconModifier = Modifier.graphicsLayer {
                    scaleX = likeScale
                    scaleY = likeScale
                },
                onClick = {
                    performHaptic(context)
                    onLikeClick()
                }
            )

            // Comments
            ReelRailAction(
                icon = Icons.Rounded.ChatBubble,
                count = formatCountCompact(localCommentsCount),
                tint = Color.White,
                contentDescription = "Comentarios",
                onClick = { showCommentDialog = true }
            )

            // Favorite (bookmark)
            ReelRailAction(
                icon = if (localIsFavorited) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                count = formatCountCompact(localFavoritesCount),
                tint = if (localIsFavorited) Color(0xFFF9C74F) else Color.White,
                contentDescription = "Guardar",
                onClick = { onFavoriteClick() }
            )

            // Share (native Android share sheet)
            ReelRailAction(
                icon = Icons.AutoMirrored.Rounded.Send,
                count = formatCountCompact(localSharesCount),
                tint = Color.White,
                contentDescription = "Compartir",
                onClick = {
                    // Resolve on IO to avoid blocking Main with VCDN BFF I/O
                    scope.launch(Dispatchers.IO) {
                        val resolvedUrl = com.example.data.repository.CdnManager.resolveMediaUrl(state.mediaUrl)
                        val shareText = "Mira este reel de pana en Panalink: ${metadata.baseCaption} - ${resolvedUrl ?: ""}"
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            val sendIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            val shareIntent = android.content.Intent.createChooser(sendIntent, "Compartir Reel de pana 🇻🇪")
                            context.startActivity(shareIntent)
                            onShareClick()
                        }
                    }
                }
            )

            // Sound toggle
            ReelRailAction(
                icon = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                count = "",
                tint = Color.White,
                contentDescription = if (isMuted) "Activar sonido" else "Silenciar",
                onClick = { onMuteToggle() }
            )

            // More options (download / delete)
            Box {
                ReelRailAction(
                    icon = Icons.Rounded.MoreHoriz,
                    count = "",
                    tint = Color.White,
                    contentDescription = "Opciones",
                    onClick = { showActionMoreMenu = true }
                )

                DropdownMenu(
                    expanded = showActionMoreMenu,
                    onDismissRequest = { showActionMoreMenu = false },
                    modifier = Modifier.background(Color(0xFF0F0F10))
                ) {
                    DropdownMenuItem(
                        text = { Text("Descargar vídeo", color = Color.White, fontSize = 14.sp) },
                        onClick = {
                            // Resolve on IO to avoid blocking Main with VCDN BFF I/O
                            scope.launch(Dispatchers.IO) {
                                val resolved = com.example.data.repository.CdnManager.resolveMediaUrl(state.mediaUrl)
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    downloadVideo(context, resolved ?: "", state.caption ?: "Vídeo de Panalink")
                                    showActionMoreMenu = false
                                }
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isMuted) "Activar sonido" else "Silenciar todo", color = Color.White, fontSize = 14.sp) },
                        onClick = {
                            onMuteToggle()
                            showActionMoreMenu = false
                        }
                    )
                    if (isOwner && onDeleteClick != null) {
                        DropdownMenuItem(
                            text = { Text("Eliminar", color = Color.Red, fontSize = 14.sp) },
                            onClick = {
                                onDeleteClick()
                                showActionMoreMenu = false
                            }
                        )
                    }
                }
            }
        }

        // 3. Bottom left details panel (TikTok-style: username + follow, caption)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 80.dp, bottom = 44.dp)
                .graphicsLayer(alpha = controlsAlpha),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Username + Follow pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "@${safeDisplayName}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable {
                        val targetId = safeProfileId.ifBlank { state.userId }
                        if (targetId.isNotBlank()) {
                            onNavigateToUserProfile?.invoke(targetId)
                        }
                    },
                    style = TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.8f),
                            offset = Offset(1f, 1f),
                            blurRadius = 4f
                        )
                    )
                )

                if (safeProfileId != currentUid) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isFollowing) Color.White.copy(alpha = 0.12f)
                                else Color(0xFFFF2B54)
                            )
                            .clickable {
                                scope.launch {
                                    if (isFollowing) {
                                        profilesRepo.unfollowUser(currentUid, safeProfileId)
                                            .onSuccess {
                                                isFollowing = false
                                                Toast.makeText(context, "Dejaste de seguir a @${safeDisplayName} 🇻🇪", Toast.LENGTH_SHORT).show()
                                            }
                                    } else {
                                        profilesRepo.followUser(currentUid, safeProfileId)
                                            .onSuccess {
                                                isFollowing = true
                                                Toast.makeText(context, "Siguiendo a @${safeDisplayName} de pana 🇻🇪", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (isFollowing) "Siguiendo" else "Seguir",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Description / Caption Text
            if (!state.caption.isNullOrBlank()) {
                val metadata = parseStateMetadata(state.caption); val caption = metadata.baseCaption
                com.example.ui.components.TextAnnotator.AnnotatedClickableText(
                    text = caption,
                    style = TextStyle(color = Color.White, fontSize = 14.sp),
                    hashtagColor = Color(0xFF69F0AE),
                    mentionColor = Color(0xFFE040FB),
                    onHashtagClick = { tag ->
                        onHashtagClick?.invoke(tag)
                    },
                    onMentionClick = { mention ->
                        android.util.Log.d("TikTokFeed", "Mention clicked: $mention")
                    }
                )
            }
        }

        // 5. Thin TikTok-style progress bar with seek support; timestamps only while dragging
        if (isActivePage && exoPlayerRef != null && duration > 0 && !hasError) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 8.dp, end = 8.dp, bottom = 14.dp)
            ) {
                if (isDraggingSlider) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatMmSs(currentPosition),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatMmSs(duration),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Slider(
                    value = currentPosition.toFloat(),
                    onValueChange = { newValue ->
                        isDraggingSlider = true
                        currentPosition = newValue.toLong()
                    },
                    onValueChangeFinished = {
                        isDraggingSlider = false
                        exoPlayerRef?.let { player ->
                            player.seekTo(currentPosition)
                            player.playWhenReady = true
                        }
                    },
                    valueRange = 0f..duration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isDraggingSlider) 20.dp else 8.dp)
                        .semantics {
                            this.contentDescription = "Progreso del vídeo: ${formatMmSs(currentPosition)} de ${formatMmSs(duration)}"
                        },
                )
            }
        }

        // Bottom Sheet Comments Panel Style (TikTok-style, semi-transparent overlays on top without stopping video playback)
        AnimatedVisibility(
            visible = showCommentDialog,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .background(
                        color = Color(0xF2101D24), // Semitransparent WhatsApp deep charcoal
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    .clickable(enabled = true, onClick = {}) // consume clicks to avoid pausing video behind
                    .imePadding() // Keyboard avoidance - slides the input overlay above the soft keyboard!
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                ) {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 8.dp, bottom = 12.dp)
                            .size(width = 40.dp, height = 4.dp)
                            .background(Color.Gray.copy(alpha = 0.5f), CircleShape)
                    )

                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp).padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Comentarios (${localCommentsCount})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        IconButton(onClick = { showCommentDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // Scrollable list of comments (using structuredComments to support threaded replies)
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(structuredComments) { comment ->
                            val isReply = comment.parentCommentId != null
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = if (isReply) 48.dp else 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                if (isReply) {
                                    // Visual hierarchy thread connector
                                    Text(
                                        text = "└─ ",
                                        color = Color.White.copy(alpha = 0.3f),
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(end = 4.dp, top = 2.dp)
                                    )
                                }
                                Box(modifier = Modifier.clickable { onNavigateToUserProfile?.invoke(comment.userId) }) {
                                    com.example.ui.components.PanaAvatar(
                                        avatarUrl = comment.avatarUrl,
                                        userId = comment.userId,
                                        size = if (isReply) 28.dp else 36.dp,
                                        borderWidth = 0.dp,
                                        placeholderName = comment.authorName
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = if (comment.deletedAt != null) "Eliminado"
                                                else com.example.data.repository.PublicProfileResolver.formatForUi(comment.authorName, "Pana"),
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = if (isReply) 12.sp else 13.sp,
                                            modifier = Modifier.clickable { onNavigateToUserProfile?.invoke(comment.userId) }
                                        )
                                        // Dynamic relative time formatting helper
                                        val timeStr = remember(comment.createdAt) {
                                            try {
                                                val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                                                parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                                val date = parser.parse(comment.createdAt)
                                                val diff = System.currentTimeMillis() - (date?.time ?: System.currentTimeMillis())
                                                val minutes = (diff / 60000).toInt()
                                                when {
                                                    minutes < 1 -> "ahora"
                                                    minutes < 60 -> "hace ${minutes}m"
                                                    minutes < 1440 -> "hace ${minutes / 60}h"
                                                    else -> "hace ${minutes / 1440}d"
                                                }
                                            } catch (e: Exception) {
                                                "hace poco"
                                            }
                                        }
                                        Text(
                                            text = timeStr,
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 11.sp
                                        )

                                        // Reply action (keeps hierarchy at exactly 1 level depth)
                                        val targetParent = if (isReply) {
                                            commentsList.find { it.id == comment.parentCommentId } ?: comment
                                        } else {
                                            comment
                                        }

                                        if (comment.deletedAt == null) {
                                            Text(
                                                text = "• Responder",
                                                color = Color(0xFF25D366),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier
                                                    .clickable {
                                                        replyingTo = targetParent
                                                        coroutineScope.launch {
                                                            focusRequester.requestFocus()
                                                        }
                                                    }
                                                    .padding(horizontal = 4.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (comment.deletedAt != null) "Este comentario ha sido eliminado" else comment.text,
                                        color = if (comment.deletedAt != null) Color.White.copy(alpha = 0.4f) else Color.White,
                                        fontSize = if (isReply) 13.sp else 14.sp,
                                        fontStyle = if (comment.deletedAt != null) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // Contextual Ribbon for Threaded Reply Mode
                    val currentReplyingTo = replyingTo
                    if (currentReplyingTo != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E2D35))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Respondiendo a @${com.example.data.repository.PublicProfileResolver.formatForUi(currentReplyingTo.authorName, "Pana")}",
                                color = Color(0xFF25D366),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(
                                onClick = { replyingTo = null },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancelar respuesta",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // Fixed Input overlay at bottom
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = commentText,
                            onValueChange = { commentText = it },
                            placeholder = { 
                                val hint = if (replyingTo != null) "Escribe tu respuesta..." else "Escribe tu comentario de pana..."
                                Text(hint, color = Color.Gray) 
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            maxLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF1E2D35),
                                unfocusedContainerColor = Color(0xFF1E2D35),
                                focusedBorderColor = Color(0xFF25D366),
                                unfocusedBorderColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )

                        IconButton(
                            onClick = {
                                if (commentText.isNotBlank()) {
                                    viewModel.addComment(
                                        stateId = state.id,
                                        commentText = commentText,
                                        parentId = replyingTo?.id,
                                        onError = { err ->
                                            android.widget.Toast.makeText(context, "Error: $err", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    commentText = ""
                                    replyingTo = null
                                }
                            },
                            modifier = Modifier
                                .background(Color(0xFF25D366), CircleShape)
                                .size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Enviar",
                                tint = Color(0xFF101D24),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Data class representation for beautiful bottom sheet comments
data class HeartPopState(
    val id: Long,
    val x: Float,
    val y: Float
)

@Composable
fun FloatingHeart(
    heart: HeartPopState,
    onAnimationEnd: () -> Unit
) {
    val scale = remember { androidx.compose.animation.core.Animatable(0f) }
    val alpha = remember { androidx.compose.animation.core.Animatable(1f) }
    val translateY = remember { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(heart.id) {
        kotlinx.coroutines.coroutineScope {
            launch {
                scale.animateTo(
                    targetValue = 2.5f,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 600, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                )
            }
            launch {
                translateY.animateTo(
                    targetValue = -120f,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 600, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                )
            }
            launch {
                alpha.animateTo(
                    targetValue = 0f,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 600, easing = androidx.compose.animation.core.LinearEasing)
                )
            }
        }
        onAnimationEnd()
    }

    Box(
        modifier = Modifier
            .offset(
                x = with(androidx.compose.ui.platform.LocalDensity.current) { heart.x.toDp() - 40.dp },
                y = with(androidx.compose.ui.platform.LocalDensity.current) { heart.y.toDp() - 40.dp + translateY.value.dp }
            )
            .size(80.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Favorite,
            contentDescription = null,
            tint = Color(0xFF00FF85),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale.value,
                    scaleY = scale.value,
                    alpha = alpha.value
                )
        )
    }
}

private fun downloadVideo(context: android.content.Context, videoUrl: String, title: String) {
    if (videoUrl.isBlank()) {
        Toast.makeText(context, "Enlace de descarga vacío ❌", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val request = DownloadManager.Request(Uri.parse(videoUrl)).apply {
            setTitle(title)
            setDescription("Descargando vídeo de Panalink...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "panalink_${System.currentTimeMillis()}.mp4")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val manager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        Toast.makeText(context, "Descarga iniciada de pana... 📥\uD83C\uDDFB\uD83C\uDDEA", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error en la descarga: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun ReelsSkeletonLoader(avatarUrl: String?, displayName: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070708))
    ) {
        // Ambient background gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF1E1E24).copy(alpha = 0.4f), Color.Black),
                        radius = 1200f
                    )
                )
        )

        // Bottom skeleton
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 88.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.graphicsLayer(alpha = alpha)
            ) {
                // Avatar skeleton
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                )
                // Username and Seguir button skeleton
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(100.dp, 16.dp)
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    )
                    Box(
                        modifier = Modifier
                            .size(60.dp, 12.dp)
                            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    )
                }
            }
            // Caption lines
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.graphicsLayer(alpha = alpha)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(14.dp)
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(14.dp)
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                )
            }
        }

        // Center spinner
        CircularProgressIndicator(
            color = Color(0xFF00FF85),
            strokeWidth = 3.dp,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
fun ReelsErrorView(
    avatarUrl: String?,
    displayName: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F10)),
        contentAlignment = Alignment.Center
    ) {
        // Blurred backdrop simulation
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF1E0A0A), Color.Black)
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // Error icon with subtle glow
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFFFF3355).copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Error",
                    tint = Color(0xFFFF3355),
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = "No se pudo cargar el vídeo de pana 🇻🇪",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Text(
                text = "Un problema técnico impidió la reproducción. Inténtalo de nuevo.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00FF85),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text("Reintentar", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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

private suspend fun PointerInputScope.detectTransformGesturesCustom(
    onGesture: PointerInputScope.(centroid: Offset, pan: Offset, zoom: Float, rotation: Float) -> Unit,
    currentScale: () -> Float,
    onEnd: (() -> Unit)? = null
) {
    val ptrScope = this
    awaitEachGesture {
        var rotation = 0f
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val rotationChange = event.calculateRotation()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    rotation += rotationChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = kotlin.math.abs(1 - zoom) * centroidSize
                    val rotationMotion = kotlin.math.abs(rotation * (kotlin.math.PI.toFloat() / 180f)) * centroidSize
                    val panMotion = pan.getDistance()

                    if (zoomMotion > touchSlop ||
                        rotationMotion > touchSlop ||
                        panMotion > touchSlop
                    ) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    val effectiveRotation = rotationChange
                    
                    val isMultiTouch = event.changes.size > 1
                    val isZoomedIn = currentScale() > 1.01f

                    if (isMultiTouch || isZoomedIn) {
                        if (effectiveRotation != 0f ||
                            zoomChange != 1f ||
                            panChange != Offset.Zero
                        ) {
                            ptrScope.onGesture(centroid, panChange, zoomChange, effectiveRotation)
                        }
                        event.changes.forEach {
                            if (it.positionChanged()) {
                                it.consume()
                            }
                        }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })

        // Acción 4: Trigger snap-back when all fingers are lifted
        onEnd?.invoke()
    }
}
