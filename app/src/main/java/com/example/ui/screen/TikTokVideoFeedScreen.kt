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
import androidx.compose.ui.focus.focusRequester
import android.app.DownloadManager
import android.os.Environment
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.awaitCancellation
import com.example.data.video.CacheDataSourceFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.data.repository.ProfilesRepository
import com.example.data.repository.CdnManager
import com.example.data.supabase.SupabaseClient

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.util.NetworkMonitor
import com.example.ui.components.PanaAvatar
import com.example.ui.components.OfflineEmptyView

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
                                    model = com.example.data.repository.CdnManager.resolveMediaUrlSync(item.state.mediaUrl),
                                    contentDescription = null,
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
                
                // TikTok-style byte prefetch: only the next video's first bytes into the
                // shared SimpleCache — the dual-manager ya pre-prepara su media (primer frame);
                // este byte-warm adicional acelera el seek sin saturar la conexión. Dispara
                // SOLO cuando el pager se ha detenido (settledPage), nunca en páginas
                // intermedias de un fling (así las precargas intermedias se cancelan solas.
                LaunchedEffect(pagerState.settledPage, videoStates, isActive) {
                    if (!isActive) return@LaunchedEffect
                    kotlinx.coroutines.delay(250) // Debounce 250ms extra tras asentarse
                    val currentIndex = pagerState.settledPage
                    val nextIndex = currentIndex + 1
                    if (nextIndex >= 0 && nextIndex < videoStates.size) {
                        val nextState = videoStates[nextIndex].state
                        // Si el siguiente video ya está preparado en un slot del dual-manager
                        // (window de preload), NO competir I/O con bytes extra: prioriza
                        // siempre el vídeo actualmente visible.
                        if (dualManager.slotFor(nextState.id) != null) return@LaunchedEffect
                        val raw = nextState.mediaUrl
                        val url =withContext(Dispatchers.IO) { com.example.data.repository.CdnManager.resolveMediaUrl(raw) }
                        if (!url.isNullOrEmpty() && url.startsWith("http")) {
                            com.example.data.video.CacheDataSourceFactory.prefetchVideo(context, url, maxBytes = 2L * 1024L * 1024L)
                        }
                    }
                }

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
                // ReelPreloader's full-video parallel download used to run here; it
                // duplicated the SimpleCache prefetch, burned bandwidth competing with
                // the playing reel, and its downloaded file was never used by the
                // player (which streams through CacheDataSource). TikTok only preloads
                // the next videos' first bytes — that happens above.

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
                            isPreload = kotlin.math.abs(pagerState.currentPage - page) <= 1,
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
                .size(46.dp)
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
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var hasError by remember(state.id) { mutableStateOf(false) }

    var exoPlayerRef by remember { mutableStateOf<ExoPlayer?>(null) }
    var isBuffering by remember { mutableStateOf(true) }
    var forceRotationDegrees by remember(state.id) { mutableStateOf(0f) }

    LaunchedEffect(state.id, state.mediaUrl) {
        // La resolución vcdn:// puede requerir red (timeouts largos); nunca bloquear
        // el hilo principal buscando metadata de rotación de un reel..
        val url = withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.example.data.repository.CdnManager.resolveMediaUrl(state.mediaUrl)
        } ?: return@LaunchedEffect
        if (url.isEmpty() || !url.startsWith("http")) return@LaunchedEffect

        // Cached rotation lookup avoids a network metadata fetch on every page
        // re-composition during fast swipes.
        val cachedRotation = com.example.core.media.VideoMetadataCache.getRotation(url)
        if (cachedRotation != null) {
            if (cachedRotation != 0f) forceRotationDegrees = cachedRotation
            return@LaunchedEffect
        }

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
                com.example.core.media.VideoMetadataCache.putRotation(url, neededRotation)
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


    var resolvedUrl by remember(state.id, state.mediaUrl) { mutableStateOf<String?>(null) }
    var resolveFailed by remember(state.id, state.mediaUrl) { mutableStateOf(false) }
    var retryCount by remember(state.id, state.mediaUrl) { mutableStateOf(0) }
    var activeSlot by remember(state.id, state.mediaUrl) { mutableStateOf<ReelDualPlayerManager.Slot?>(null) }

    LaunchedEffect(state.id, state.mediaUrl, isActivePage, isPreload,retryCount,dualManager) {
        if (isActivePage) {
            resolvedUrl = null
            retryCount = 0
            activeSlot = null
        }
        if (state.localVideoPath.isNullOrEmpty() && state.mediaUrl.isNullOrBlank()) return@LaunchedEffect
        resolvedUrl = if (state.localVideoPath.isNullOrEmpty()) {
            val resolved = withContext(Dispatchers.IO) { com.example.data.repository.CdnManager.resolveMediaUrl(state.mediaUrl) }
            resolved.takeIf { !it.startsWith("vcdn://") }
        } else {
            state.localVideoPath
        }
        if (resolvedUrl == null) resolveFailed = true
    }

    LaunchedEffect(isActivePage,isPreload,resolvedUrl,activeSlot,retryCount,dualManager) {


        // BUGFIX: el preload jamás debe caer en el slot activo: re-prepararlo
        // (setMediaItem+prepare) mataría la reproducción visible (black screen tras scroll).

        val url = resolvedUrl ?: return@LaunchedEffect
        // BUGFIX: al perder red el sub-guard (isOnline) reinicia esté LaunchedEffect
        // con resolvedUrl ya resuelto; si el slot ya está en A no re-resolvemos world
        val prev = activeSlot
        if (prev != null && prev != ReelDualPlayerManager.Slot.B) return@LaunchedEffect
        val slot = dualManager.acquireOrReuse(
            state.id,
            url,
            isActivePage,
            if (isMuted) 0f else  1f
        ) ?: return@LaunchedEffect
        activeSlot = slot
        exoPlayerRef = dualManager.playerFor(slot)
    }

    LaunchedEffect(exoPlayerRef, state.id, retryCount) {
        val player = exoPlayerRef ?: return@LaunchedEffect
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE) && !hasError
                if (playbackState == Player.STATE_READY) { hasError = false; resolveFailed = false }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("TikTokVideoFeedScreen", "player error id=${state.id} code=${error.errorCode}", error)
                isBuffering = false
                // Offline: el reintento automático solo con red; sin red marcar error
                // directamente para que la UI muestre Reintentar sin spinner congelado..
                if (retryCount < 2 && com.example.util.NetworkMonitor.isOnline.value) {
                    retryCount += 1
                    resolvedUrl = null
                    dualManager.releaseIfOwned(state.id)
                } else {
                    hasError = true
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

    LaunchedEffect(isMuted) {
        exoPlayerRef?.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(isPaused, isActivePage, exoPlayerRef) {
        exoPlayerRef?.playWhenReady = isActivePage && !isPaused
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
                            val job = coroutineScope.launch {
                                kotlinx.coroutines.delay(350L)
                                if (!isReleased) {
                                    isFocusMode = true
                                }
                            }
                            try {
                                awaitRelease()
                            } finally {
                                isReleased = true
                                job.cancel()
                                isFocusMode = false
                            }
                        }
                    )
                }
        ) {
            if (exoPlayerRef != null && (isActivePage || isPreload)) {
                val hasLocalCopy = !state.localVideoPath.isNullOrBlank() && java.io.File(state.localVideoPath!!).exists()
                if (!com.example.util.NetworkMonitor.isOnline.value && (isActivePage || isPreload) && !hasError && !resolveFailed && !hasLocalCopy) {
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
                } else if (isActivePage && (hasError || resolveFailed)) {
                    ReelsErrorView(
                        avatarUrl = safeAvatarUrl,
                        displayName = safeDisplayName ?: "",
                        onRetry = {
                            hasError = false
                            isBuffering = true
                            retryCount = 0
                            resolvedUrl = null
                            dualManager.releaseIfOwned(state.id)
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(state.id) {
                                detectTransformGesturesCustom(
                                    onGesture = { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(1f, 5f)
                                        offset = if (scale > 1f) {
                                            Offset(offset.x + pan.x, offset.y + pan.y)
                                        } else {
                                            Offset.Zero
                                        }
                                    },
                                    currentScale = { scale }
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
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                    if (forceRotationDegrees != 0f) {
                                        rotationZ = forceRotationDegrees
                                    }
                                }
                        )

                        if (isBuffering && isActivePage) {
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
                onClick = { onLikeClick() }
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
                    val shareText = "Mira este reel de pana en Panalink: ${metadata.baseCaption} - ${com.example.data.repository.CdnManager.resolveMediaUrlSync(state.mediaUrl) ?: ""}"
                    val sendIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }
                    val shareIntent = android.content.Intent.createChooser(sendIntent, "Compartir Reel de pana 🇻🇪")
                    context.startActivity(shareIntent)
                    onShareClick()
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
                            downloadVideo(context, com.example.data.repository.CdnManager.resolveMediaUrlSync(state.mediaUrl) ?: "", state.caption ?: "Vídeo de Panalink")
                            showActionMoreMenu = false
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
                        exoPlayerRef?.seekTo(currentPosition)
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

        // Right side skeleton
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(bottom = 48.dp, end = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(5) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                        .graphicsLayer(alpha = alpha)
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
    onGesture: (centroid: Offset, pan: Offset, zoom: Float, rotation: Float) -> Unit,
    currentScale: () -> Float
) {
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
                            onGesture(centroid, panChange, zoomChange, effectiveRotation)
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
    }
}
