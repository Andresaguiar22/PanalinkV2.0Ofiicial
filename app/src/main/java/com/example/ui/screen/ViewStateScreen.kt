@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui.screen

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import com.example.identity.model.toIdentityUiState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import com.example.ui.components.PanaAvatar
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.compose.AsyncImagePainter
import com.example.identity.repository.IdentityRepository
import com.example.data.model.*
import com.example.ui.viewmodel.StatesUiState
import com.example.ui.viewmodel.StatesViewModel
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import com.example.ui.settings.ios.IosSettingsColors


// Acción 4: Floating reaction data
data class FloatingReactionLog(
    val id: String,
    val avatarUrl: String,
    val emoji: String,
    val userId: String
)

data class StateMetadata(
    val baseCaption: String,
    val filter: String?,
    val musicName: String?,
    val musicTrim: Pair<Float, Float>?,
    val carouselCount: Int,
    val videoTrim: Pair<Float, Float>? = null, val overlaysBase64: String? = null
)

fun parseStateMetadata(caption: String?): StateMetadata {
    if (caption == null) return StateMetadata("", null, null, null, 1)
    
    var filter: String? = null
    var musicName: String? = null
    var musicTrim: Pair<Float, Float>? = null
    var carouselCount = 1
    var videoTrim: Pair<Float, Float>? = null
    
    // Parse Filter
    val filterRegex = "\\[Filtro:\\s*([^\\]]+)\\]".toRegex()
    val filterMatch = filterRegex.find(caption)
    if (filterMatch != null) {
        filter = filterMatch.groupValues[1].trim()
    }
    
    // Parse Music
    val musicRegex = "\\[Música:\\s*([^\\]]+)\\]".toRegex()
    val musicMatch = musicRegex.find(caption)
    if (musicMatch != null) {
        val fullMusicStr = musicMatch.groupValues[1].trim()
        if (fullMusicStr.contains("✂️")) {
            val parts = fullMusicStr.split("✂️")
            musicName = parts[0].trim().removeSuffix("(").trim()
            val timeStr = parts.getOrNull(1)?.removeSuffix(")")?.trim() ?: ""
            val timeParts = timeStr.split("-")
            val startStr = timeParts.getOrNull(0)?.trim() ?: ""
            val endStr = timeParts.getOrNull(1)?.trim() ?: ""
            
            fun parseToSeconds(s: String): Float {
                val t = s.split(":")
                if (t.size == 2) {
                    return (t[0].toIntOrNull() ?: 0) * 60f + (t[1].toIntOrNull() ?: 0)
                }
                return s.toFloatOrNull() ?: 0f
            }
            val startSec = parseToSeconds(startStr)
            val endSec = parseToSeconds(endStr)
            musicTrim = Pair(startSec, endSec)
        } else {
            musicName = fullMusicStr
        }
    }
    
    // Parse Video Trim
    val videoTrimRegex = "\\[VideoTrim:\\s*([0-9.]+)\\s*-\\s*([0-9.]+)\\]".toRegex()
    val videoTrimMatch = videoTrimRegex.find(caption)
    if (videoTrimMatch != null) {
        val startSec = videoTrimMatch.groupValues[1].toFloatOrNull() ?: 0f
        val endSec = videoTrimMatch.groupValues[2].toFloatOrNull() ?: 60f
        videoTrim = Pair(startSec, endSec)
    }
    
    // Parse Carousel
    val carouselRegex = "\\[Carrusel:\\s*([0-9]+)\\]".toRegex()
    val carouselMatch = carouselRegex.find(caption)
    if (carouselMatch != null) {
        carouselCount = carouselMatch.groupValues[1].toIntOrNull() ?: 1
    }
    
    var overlaysBase64: String? = null
    val overlaysRegex = "\\[Overlays:\\s*([^\\]]+)\\]".toRegex()
    val overlaysMatch = overlaysRegex.find(caption)
    if (overlaysMatch != null) {
        overlaysBase64 = overlaysMatch.groupValues[1].trim()
    }

    // Reel editor technical tags: kept in storage as metadata, never shown to viewers
    val transitionRegex = "\\[Transition:\\s*([^\\]]+)\\]".toRegex()
    val coverFrameRegex = "\\[CoverFrame:\\s*([^\\]]+)\\]".toRegex()
    val musicTagRegex = "\\[Music:\\s*([^\\]]+)\\]".toRegex()
    val scheduledRegex = "\\[Scheduled:\\s*([^\\]]+)\\]".toRegex()

    // Clean Caption
    var cleaned = caption
    cleaned = filterRegex.replace(cleaned, "")
    cleaned = musicRegex.replace(cleaned, "")
    cleaned = videoTrimRegex.replace(cleaned, "")
    cleaned = carouselRegex.replace(cleaned, ""); cleaned = overlaysRegex.replace(cleaned, "")
    cleaned = transitionRegex.replace(cleaned, "")
    cleaned = coverFrameRegex.replace(cleaned, "")
    cleaned = musicTagRegex.replace(cleaned, "")
    cleaned = scheduledRegex.replace(cleaned, "")
    cleaned = cleaned.trim()
    
    return StateMetadata(
        baseCaption = cleaned,
        filter = filter,
        musicName = musicName,
        musicTrim = musicTrim,
        carouselCount = carouselCount,
        videoTrim = videoTrim,
        overlaysBase64 = overlaysBase64
    )
}

@Composable
fun RenderOverlays(base64Str: String?) {
    if (base64Str.isNullOrEmpty()) return
    
    val overlaysData = remember(base64Str) {
        try {
            val jsonStr = String(android.util.Base64.decode(base64Str, android.util.Base64.NO_WRAP))
            val jsonObj = org.json.JSONObject(jsonStr)
            val textArr = jsonObj.optJSONArray("textOverlays")
            val stickerArr = jsonObj.optJSONArray("stickerOverlays")
            
            val stickers = mutableListOf<org.json.JSONObject>()
            if (stickerArr != null) {
                for (i in 0 until stickerArr.length()) {
                    stickers.add(stickerArr.getJSONObject(i))
                }
            }
            
            val texts = mutableListOf<org.json.JSONObject>()
            if (textArr != null) {
                for (i in 0 until textArr.length()) {
                    texts.add(textArr.getJSONObject(i))
                }
            }
            Pair(stickers, texts)
        } catch (e: Exception) {
            android.util.Log.e("RenderOverlays", "Failed to parse overlays", e)
            null
        }
    } ?: return

    val stickerList = overlaysData.first
    val textList = overlaysData.second

    Box(modifier = Modifier.fillMaxSize()) {
        stickerList.forEach { s ->
            AsyncImage(
                model = s.optString("url"),
                contentDescription = "Sticker",
                modifier = Modifier
                    .size(100.dp)
                    .offset { androidx.compose.ui.unit.IntOffset(s.optDouble("x", 0.0).toInt(), s.optDouble("y", 0.0).toInt()) }
                    .graphicsLayer {
                        scaleX = s.optDouble("scale", 1.0).toFloat()
                        scaleY = s.optDouble("scale", 1.0).toFloat()
                        rotationZ = s.optDouble("rotation", 0.0).toFloat()
                    }
            )
        }
        textList.forEach { t ->
            val fontName = t.optString("fontName", "Default")
            val hasBackground = t.optBoolean("hasBackground", false)
            val hasShadow = t.optBoolean("hasShadow", true)
            val isGradient = t.optBoolean("isGradient", false)
            val colorLong = t.optString("color").toULongOrNull() ?: androidx.compose.ui.graphics.Color.White.value.toULong()
            val color = androidx.compose.ui.graphics.Color(colorLong)
            val brush = if (isGradient) androidx.compose.ui.graphics.Brush.linearGradient(listOf(color, androidx.compose.ui.graphics.Color.White)) else null
            
            var textMod: Modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset(t.optDouble("x", 0.0).toInt(), t.optDouble("y", 0.0).toInt()) }
                .graphicsLayer {
                    scaleX = t.optDouble("scale", 1.0).toFloat()
                    scaleY = t.optDouble("scale", 1.0).toFloat()
                    rotationZ = t.optDouble("rotation", 0.0).toFloat()
                }
            
            if (hasBackground) {
                textMod = textMod.background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(8.dp)
            }

            androidx.compose.material3.Text(
                text = t.optString("text"),
                color = if (brush == null) color else androidx.compose.ui.graphics.Color.Unspecified,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = when (fontName) {
                    "Serif" -> androidx.compose.ui.text.font.FontFamily.Serif
                    "Monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
                    "Cursive" -> androidx.compose.ui.text.font.FontFamily.Cursive
                    else -> androidx.compose.ui.text.font.FontFamily.Default
                },
                style = TextStyle(
                    brush = brush,
                    shadow = if (hasShadow) androidx.compose.ui.graphics.Shadow(color = androidx.compose.ui.graphics.Color.Black, offset = androidx.compose.ui.geometry.Offset(4f, 4f), blurRadius = 8f) else null
                ),
                modifier = textMod
            )
        }
    }
}
fun formatCreatedTime(isoString: String?): String {
    if (isoString == null) return "Ahora"
    try {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val cleanIso = isoString.substringBefore(".")
        val date = format.parse(cleanIso) ?: return "Ahora"
        val diffMs = System.currentTimeMillis() - date.time
        val diffSec = diffMs / 1000
        val diffMin = diffSec / 60
        val diffHour = diffMin / 60
        
        return when {
            diffSec < 45 -> "Ahora"
            diffMin < 60 -> "${diffMin} min"
            diffHour < 24 -> "${diffHour} h"
            else -> "${diffHour / 24} d"
        }
    } catch (e: Exception) {
        return "Ahora"
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ViewStateScreen(
    viewModel: StatesViewModel,
    stateId: String,
    onClose: () -> Unit,
    onNavigateToUserProfile: ((String) -> Unit)? = null
) {
    com.example.util.KeepScreenOn()
    val statesState by viewModel.statesState.collectAsStateWithLifecycle()

    if (statesState is StatesUiState.Loading) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = IosSettingsColors.label)
        }
        return
    }

    if (statesState is StatesUiState.Error) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("Error al cargar estados. Reintentando...", color = IosSettingsColors.label)
            LaunchedEffect(Unit) {
                delay(2000)
                onClose()
            }
        }
        return
    }

    val allStates = (statesState as? StatesUiState.Success)?.states?.filter { !it.state.isReel } ?: emptyList()
    val initialIndex = allStates.indexOfFirst { it.state.id == stateId }

    if (allStates.isEmpty() || initialIndex == -1) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("Estado expirado o no encontrado.", color = IosSettingsColors.label)
            LaunchedEffect(Unit) {
                delay(1500)
                onClose()
            }
        }
        return
    }

    val statesGroupedByUser = remember(allStates) {
        allStates.groupBy { it.state.userId }
    }

    val uniqueUsers = remember(allStates) {
        allStates.distinctBy { it.state.userId }.map { it.state.userId }
    }

    val initialUserId = allStates[initialIndex].state.userId
    val initialPage = uniqueUsers.indexOf(initialUserId).coerceAtLeast(0)

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { uniqueUsers.size }
    )
    val coroutineScope = rememberCoroutineScope()

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) { page ->
        val userId = uniqueUsers.getOrNull(page) ?: return@HorizontalPager
        val userStates = statesGroupedByUser[userId] ?: emptyList()

        if (userStates.isNotEmpty()) {
            val initialStatusIndex = remember(page) {
                if (page == initialPage) {
                    val clickedIndex = userStates.indexOfFirst { it.state.id == stateId }
                    if (clickedIndex != -1) clickedIndex else 0
                } else {
                    0
                }
            }

            UserStoryViewer(
                viewModel = viewModel,
                userStates = userStates,
                initialStatusIndex = initialStatusIndex,
                page = page,
                isPageActive = pagerState.currentPage == page,
                uniqueUsersSize = uniqueUsers.size,
                onClose = onClose,
                onNavigateToUserProfile = onNavigateToUserProfile,
                onNextUser = {
                    if (page < uniqueUsers.size - 1) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(page + 1)
                        }
                    } else {
                        onClose()
                    }
                },
                onPreviousUser = {
                    if (page > 0) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(page - 1)
                        }
                    } else {
                        onClose()
                    }
                }
            )
        }
    }
}

@Composable
fun UserStoryViewer(
    viewModel: StatesViewModel,
    userStates: List<UserStateWithUser>,
    initialStatusIndex: Int,
    page: Int,
    isPageActive: Boolean,
    uniqueUsersSize: Int,
    onClose: () -> Unit,
    onNavigateToUserProfile: ((String) -> Unit)? = null,
    onNextUser: () -> Unit,
    onPreviousUser: () -> Unit
) {
    var currentStatusIndex by remember(page) { mutableStateOf(initialStatusIndex) }
    val stateWithUser = userStates.getOrNull(currentStatusIndex) ?: return

    val state = stateWithUser.state
    val profile = stateWithUser.profile
    val context = LocalContext.current
    val identityRepository = remember { com.example.identity.bridge.LegacyIdentityBridge(context).identityRepository }
    val initialCached = remember(state.userId) { com.example.identity.memory.IdentityMemoryCache.profiles.get(state.userId) }
    val identityState by identityRepository.observeIdentity(state.userId).collectAsStateWithLifecycle(initialValue = initialCached?.toIdentityUiState())

    val focusManager = LocalFocusManager.current

    var currentDurationMs by remember { mutableLongStateOf(6000L) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var isMuted by remember { mutableStateOf(false) }

    // Whether the current story's media has finished loading. The progress bar
    // must NOT advance until the content is actually on screen; otherwise a slow
    // network lets the bar reach the end and auto-advance before anything shows.
    // Text stories are "loaded" immediately.
    var isContentLoaded by remember(currentStatusIndex, state.id) {
        mutableStateOf(state.mediaType == "text")
    }

    // Los videos avanzan SOLO cuando ExoPlayer reporta STATE_ENDED (onMediaEnded(;
    // el timer no corta videos antes de tiempo (el "negro" a los ~3s era el
    // auto-advance prematuro: la barra usaba una duracion incorrecta y pasaba
    // a la siguiente story, cuya carga arranca con pantalla negra).
    var videoEnded by remember(currentStatusIndex, state.id) { mutableStateOf(false) }
    
    // Bottom Sheet Control (Pauses advance when open)
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showSpectatorsSheet by remember { mutableStateOf(false) }
    
    // Owner menu control
    var showOwnerMenu by remember { mutableStateOf(false) }

    val currentUid = SupabaseClient.currentUser?.id ?: ""
    val isOwner = state.userId == currentUid
    val isMyStory = isOwner // alias per Acción 2 spec

    var isUserPressing by remember { mutableStateOf(false) }
    var isInputFocused by remember { mutableStateOf(false) }
    val isPaused = showCommentsSheet || showSpectatorsSheet || showOwnerMenu || isUserPressing || !isPageActive || isInputFocused

    val spectatorsList by viewModel.currentSpectators.collectAsStateWithLifecycle()
    val commentsList by viewModel.currentComments.collectAsStateWithLifecycle()
    val filteredComments = remember(commentsList) {
        val childrenGrouped = commentsList.filter { it.parentCommentId != null }.groupBy { it.parentCommentId }
        commentsList.filter { comment ->
            if (comment.deletedAt == null) {
                true
            } else {
                comment.parentCommentId == null && childrenGrouped[comment.id]?.isNotEmpty() == true
            }
        }
    }

    // Likes scale animation
    var likeScale by remember { mutableStateOf(1f) }
    val animLikeScale by animateFloatAsState(
        targetValue = likeScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        finishedListener = { likeScale = 1f }
    )

    // Double tap like heart animation
    var showDoubleTapHeart by remember { mutableStateOf(false) }
    
    // Reply & Reactions State
    var replyText by remember { mutableStateOf("") }
var showGifPicker by remember { mutableStateOf(false) }
    var reactionMessage by remember { mutableStateOf<String?>(null) }

    // Acción 4: Floating reactions (live-style)
    var floatingReactions by remember { mutableStateOf(listOf<FloatingReactionLog>()) }
    val reactionScope = rememberCoroutineScope()

    // Acción 4: Resolve the reactor's fresh avatar off the main thread and publish
    // the floating bubble on Main (Compose state must not be mutated from IO).
    fun pushFloatingReaction(reactingUserId: String, emoji: String) {
        reactionScope.launch(Dispatchers.IO) {
            val avatarUrl = if (reactingUserId.isNotBlank()) {
                try {
                    identityRepository.resolveFreshAvatar(reactingUserId) ?: ""
                } catch (_: Exception) {
                    ""
                }
            } else ""
            withContext(Dispatchers.Main) {
                floatingReactions = floatingReactions + FloatingReactionLog(
                    id = "${System.currentTimeMillis()}_${reactingUserId}_${emoji.hashCode()}",
                    avatarUrl = avatarUrl,
                    emoji = emoji,
                    userId = reactingUserId
                )
            }
        }
    }

    // Acción 4: Listen for realtime story reactions via Supabase Realtime (social schema).
    // "Me gusta" arrive on the likes table; emoji reactions arrive as story_comments
    // inserts (the quick-reaction bar publishes emojis as comments).
    LaunchedEffect(state.id, isMyStory) {
        if (!isMyStory) return@LaunchedEffect

        launch {
            SupabaseClient.realtimeLikes.collect { update ->
                if (update.statusId == state.id && update.eventType == "INSERT") {
                    val reactingUserId = try {
                        update.record.optString("user_id", update.record.optString("author_id", ""))
                    } catch (_: Exception) {
                        ""
                    }
                    if (reactingUserId != currentUid) {
                        pushFloatingReaction(reactingUserId, "❤️")
                    }
                }
            }
        }

        launch {
            SupabaseClient.realtimeComments.collect { update ->
                if (update.statusId != state.id || update.eventType != "INSERT") return@collect
                val reactingUserId = try {
                    update.record.optString("author_id", update.record.optString("user_id", ""))
                } catch (_: Exception) {
                    ""
                }
                val rawText = try {
                    update.record.optString(
                        "body",
                        update.record.optString(
                            "content",
                            update.record.optString("comment_text", update.record.optString("text", ""))
                        )
                    )
                } catch (_: Exception) {
                    ""
                }
                val clean = rawText.trim()
                // Only bubble emoji-only reactions; real text replies stay in the comments sheet.
                val isEmojiReaction = clean.isNotEmpty() && clean.length <= 8 && clean.none { it.isLetterOrDigit() }
                if (isEmojiReaction && reactingUserId != currentUid) {
                    pushFloatingReaction(reactingUserId, clean)
                }
            }
        }
    }

    // Parse Metadata
    val metadata = remember(state.caption) { parseStateMetadata(state.caption) }
    val cleanCaption = metadata.baseCaption

    val haptic = LocalHapticFeedback.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    // Load comments and views on status change
    LaunchedEffect(state.id) {
        viewModel.registerView(state.id)
        viewModel.loadComments(state.id)
        viewModel.loadSpectators(state.id)
    }

    // Reset progress when status changes. For video, currentDurationMs will be
    // updated by the player's onDurationReady callback (which respects trim);
    // we keep the previous value as a safety default if the callback never fires.
    LaunchedEffect(currentStatusIndex) {
        elapsedMs = 0L
        videoEnded = false
        val currentMediaType = userStates.getOrNull(currentStatusIndex)?.state?.mediaType ?: "text"
        if (currentMediaType != "video") {
            currentDurationMs = 6000L
        }
        // For video, do NOT overwrite currentDurationMs here — the player's
        // onDurationReady callback supplies the real (trim-adjusted) duration.
    }

    LaunchedEffect(currentStatusIndex, userStates) {
        // Aggressive Preloading: 1 back and 3 ahead within the same user's stories.
        // El prefetch parte SIEMPRE del puntero estable (vcdn_video_id o mediaUrl)
        // y lo resuelve antes de tocar la red: prefetchVideo exige una URL HTTP y
        // un `vcdn://` a secas se descartaría (mismo patrón que ReelPreloadController).
        val preloadIndices = listOf(currentStatusIndex - 1, currentStatusIndex + 1, currentStatusIndex + 2, currentStatusIndex + 3)
        
        preloadIndices.forEach { index ->
            if (index >= 0 && index < userStates.size) {
                val nextState = userStates[index].state
                if (nextState.mediaType == "video") {
                    val stable = nextState.vcdnVideoId?.takeIf { it.isNotBlank() }?.let { "vcdn://$it" }
                        ?: nextState.mediaUrl
                    if (!stable.isNullOrBlank() && !nextState.localVideoPath.isNullOrBlank() && java.io.File(nextState.localVideoPath).exists()) {
                        return@forEach
                    }
                    if (!stable.isNullOrBlank()) {
                        kotlinx.coroutines.MainScope().launch(Dispatchers.IO) {
                            val prefetchStart = System.currentTimeMillis()
                            com.example.feature.diagnostics.StoryDiagnostics.started(
                                "Prefetch",
                                correlationId = nextState.id.take(36),
                                details = "isVcdn=${com.example.data.repository.VcdnUrlResolver.isVcdnUrl(stable)}"
                            )
                            val resolved = com.example.data.repository.CdnManager.resolveMediaUrl(stable)
                            if (!resolved.isNullOrBlank() && resolved.startsWith("http")) {
                                com.example.data.video.CacheDataSourceFactory.prefetchVideo(context, resolved)
                                com.example.feature.diagnostics.StoryDiagnostics.completed(
                                    "Prefetch",
                                    prefetchStart,
                                    correlationId = nextState.id.take(36)
                                )
                            } else {
                                com.example.feature.diagnostics.StoryDiagnostics.failed(
                                    "Prefetch",
                                    prefetchStart,
                                    correlationId = nextState.id.take(36),
                                    details = "noHttpResolve=${resolved?.take(30)}"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Background Audio Player Loop
    val musicUrl = remember(metadata.musicName, state.audioUrl) {
        // Prefer real uploaded audio; fallback to catalog lookup by name.
        val uploadedUrl = state.audioUrl?.takeIf { it.isNotBlank() }
        if (uploadedUrl != null) {
            uploadedUrl
        } else {
            val name = metadata.musicName ?: ""
            when {
                name.contains("Lofi Joropo", ignoreCase = true) -> "https://assets.mixkit.co/music/preview/mixkit-lofi-band-925.mp3"
                name.contains("Tambor Remix", ignoreCase = true) -> "https://assets.mixkit.co/music/preview/mixkit-tribal-drums-958.mp3"
                name.contains("Gaita Pop", ignoreCase = true) -> "https://assets.mixkit.co/music/preview/mixkit-pop-05-1522.mp3"
                name.isNotBlank() -> "https://assets.mixkit.co/music/preview/mixkit-dreaming-big-31.mp3"
                else -> ""
            }
        }
    }

    val bgMediaPlayer = remember { android.media.MediaPlayer() }

    DisposableEffect(musicUrl, state.id) {
        if (state.mediaType != "video" && (metadata.musicName != null || !state.audioUrl.isNullOrBlank()) && musicUrl.isNotEmpty()) {
            try {
                bgMediaPlayer.reset()
                bgMediaPlayer.setDataSource(context, android.net.Uri.parse(musicUrl))
                bgMediaPlayer.isLooping = true
                bgMediaPlayer.prepareAsync()
                bgMediaPlayer.setOnPreparedListener { mp ->
                    if (!isPaused) {
                        mp.start()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("UserStoryViewer", "Error preparing music player", e)
            }
        }
        onDispose {
            try {
                if (bgMediaPlayer.isPlaying) {
                    bgMediaPlayer.stop()
                }
                bgMediaPlayer.reset()
            } catch (e: Exception) {}
        }
    }
    
    LaunchedEffect(isPaused) {
        try {
            if (state.mediaType != "video" && (metadata.musicName != null || !state.audioUrl.isNullOrBlank()) && musicUrl.isNotEmpty()) {
                if (isPaused) {
                    if (bgMediaPlayer.isPlaying) bgMediaPlayer.pause()
                } else {
                    bgMediaPlayer.start()
                }
            }
        } catch (e: Exception) {}
    }
    
    DisposableEffect(Unit) {
        onDispose {
            try {
                bgMediaPlayer.release()
            } catch (e: Exception) {}
        }
    }

    // Story player estable (Fase 1): un único ExoPlayer durante la vida del visor.
    // Se libera SOLO al descomponer este composable (cada cambio de story reusa el
    // MISMO player via StoryVideoPlayerSession.play, evitando teardown/recreación).
    val storyPlayer = remember { com.example.core.media.StoryVideoPlayerSession(context.applicationContext) }

    DisposableEffect(Unit) {
        onDispose {
            storyPlayer.release()
        }
    }

    // Dynamic progress calculation
    val progress = if (currentDurationMs > 0) elapsedMs.toFloat() / currentDurationMs else 0f

    // Main autoplay and timing progress loop
    LaunchedEffect(currentStatusIndex, isPaused, isContentLoaded) {
        if (!isContentLoaded) return@LaunchedEffect
        val isVideo = userStates.getOrNull(currentStatusIndex)?.state?.mediaType?.contains("video", ignoreCase = true) == true
        
        if (isVideo) {
            storyPlayer.onPositionChanged = { position ->
                if (!isPaused) {
                    elapsedMs = position
                }
            }
        } else {
            val interval = 50L
            while (elapsedMs < currentDurationMs) {
                delay(interval)
                if (!isPaused && isContentLoaded) {
                    elapsedMs = (elapsedMs + interval).coerceAtMost(currentDurationMs)
                }
            }
            if (currentStatusIndex < userStates.lastIndex) {
                currentStatusIndex++
            } else {
                onNextUser()
            }
        }
    }



    // Safety: si un video jamas emite STATE_ENDED (red/decoder atascado), no dejar
    // la story colgada: se avanza tras la duración real + margen como fallback.
    // El fallback NUNCA debe ser menor que la duración del video: un clip largo
    // (hasta 2 min permitidos) se cortaría prematuramente con un tiempo fijo.
    LaunchedEffect(currentStatusIndex, isPaused, isContentLoaded, videoEnded, currentDurationMs) {
        val isVideo = userStates.getOrNull(currentStatusIndex)?.state?.mediaType?.contains("video", ignoreCase = true) == true
        if (!isVideo) return@LaunchedEffect
        // Espera la duración real (si ya se conoce) más un margen de 5s; nunca menos de 30s
        // (red/decode lento) pero tampoco un corte anticipado para clips largos.
        val knownDurationMs = currentDurationMs.takeIf { it > 0L } ?: 0L
        val fallbackMs = maxOf(knownDurationMs + 5_000L, 30_000L)
        kotlinx.coroutines.delay(fallbackMs)
        if (!isPaused && isContentLoaded && !videoEnded) {
            videoEnded = true
            if (currentStatusIndex < userStates.lastIndex) {
                currentStatusIndex++
            } else {
                onNextUser()
            }
        }
    }



    val backgroundColors = listOf(
        Color(0xFF1E1B24), Color(0xFF122421), Color(0xFF2B121C),
        Color(0xFF122329), Color(0xFF2C1E1B), Color(0xFF1D1715)
    )
    val chosenBg = backgroundColors[kotlin.math.abs(state.id.hashCode()) % backgroundColors.size]

    val formattedTime = formatCreatedTime(state.createdAt)

    val carouselImages = remember(state.mediaUrl, state.mediaUrls, state.id) {
        val list = mutableListOf<String>()
        val base = state.mediaUrl ?: ""
        if (state.mediaUrls != null && state.mediaUrls.isNotEmpty()) {
            list.addAll(state.mediaUrls)
        } else if (base.isNotEmpty()) {
            if (base.contains(",")) {
                list.addAll(base.split(",").map { it.trim() }.filter { it.isNotEmpty() })
            } else {
                list.add(base)
            }
        }
        list
    }

    // Carousel Image Selection
    val timePerImage = currentDurationMs
    val innerCarouselIndex = if (carouselImages.size > 1) {
        ((elapsedMs.toFloat() / currentDurationMs) * carouselImages.size).toInt().coerceIn(0, carouselImages.lastIndex)
    } else {
        0
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("state_view_player")
    ) {
        // Render Media Content with gestures
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(userStates, currentStatusIndex) {
                    detectTapGestures(
                        onDoubleTap = {
                            showDoubleTapHeart = true
                            likeScale = 1.4f
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (state.likedByMe != true) {
                                viewModel.toggleLike(state.id, false, onError = { err ->
                                    android.widget.Toast.makeText(context, "Error: $err", android.widget.Toast.LENGTH_LONG).show()
                                })
                            }
                        },
                        onLongPress = {
                            // Handled to prevent onTap from firing on release
                        },
                        onPress = {
                            isUserPressing = true
                            try {
                                awaitRelease()
                            } catch (e: Exception) {
                                // Ignored
                            } finally {
                                isUserPressing = false
                            }
                        },
                        onTap = { offset ->
                            focusManager.clearFocus()
                            val screenWidth = size.width
                            if (offset.x < screenWidth * 0.3f) {
                                // Left 30% -> Previous
                                if (currentStatusIndex > 0) {
                                    currentStatusIndex--
                                } else {
                                    onPreviousUser()
                                }
                            } else {
                                // Right 70% -> Next
                                if (currentStatusIndex < userStates.lastIndex) {
                                    currentStatusIndex++
                                } else {
                                    onNextUser()
                                }
                            }
                        }
                    )
                }
        ) {
            if (state.mediaType == "text") {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(chosenBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = cleanCaption,
                        color = IosSettingsColors.label,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            } else if (state.mediaType.equals("video", ignoreCase = true) || state.mediaType.contains("video", ignoreCase = true) || state.isReel) {
                // Puntero estable a la media: el vcdn_video_id nunca rota y es la
                // base para re-resolver URLs firmadas caducadas (igual que reels).
                val stableStoryUrl = remember(state.id, state.vcdnVideoId, state.mediaUrl, state.localVideoPath) {
                    when {
                        !state.vcdnVideoId.isNullOrBlank() -> "vcdn://${state.vcdnVideoId}"
                        !state.mediaUrl.isNullOrBlank() -> state.mediaUrl
                        else -> state.localVideoPath ?: ""
                    }
                }
                var resolvedVideoUrl by remember(state.id, stableStoryUrl) { mutableStateOf<String?>(null) }
                var resolveRetry by remember(state.id, stableStoryUrl) { mutableStateOf(0) }
                LaunchedEffect(state.id, stableStoryUrl, resolveRetry) {
                    val resolveStart = System.currentTimeMillis()
                    com.example.feature.diagnostics.StoryDiagnostics.started(
                        "Resolve URL",
                        correlationId = state.id.take(36),
                        details = "isVcdn=${com.example.data.repository.VcdnUrlResolver.isVcdnUrl(stableStoryUrl)}, local=${!state.localVideoPath.isNullOrBlank()}"
                    )
                    val resolvedNow = if (!state.localVideoPath.isNullOrBlank() && java.io.File(state.localVideoPath).exists()) {
                        state.localVideoPath
                    } else {
                        val resolved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            com.example.data.repository.CdnManager.resolveMediaUrl(stableStoryUrl)
                        }
                        if (resolved.isNullOrBlank() || resolved.startsWith("vcdn://")) "" else resolved
                    }
                    resolvedVideoUrl = resolvedNow
                    if (resolvedNow.isBlank()) {
                        com.example.feature.diagnostics.StoryDiagnostics.failed(
                            "Resolve URL",
                            resolveStart,
                            correlationId = state.id.take(36),
                            details = "retry=$resolveRetry, blank=true"
                        )
                    } else {
                        val host = try { java.net.URI(resolvedNow).host } catch (_: Exception) { "" }
                        com.example.feature.diagnostics.StoryDiagnostics.completed(
                            "Resolve URL",
                            resolveStart,
                            correlationId = state.id.take(36),
                            details = "host=$host"
                        )
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (resolvedVideoUrl.isNullOrBlank()) {
                        CircularProgressIndicator(color = IosSettingsColors.label)
                        LaunchedEffect(state.id, resolveRetry) {
                            kotlinx.coroutines.delay(6000L)
                            if (resolvedVideoUrl.isNullOrBlank()) {
                                videoEnded = true
                                elapsedMs = currentDurationMs
                                if (currentStatusIndex < userStates.lastIndex) {
                                    currentStatusIndex++
                                } else {
                                    onNextUser()
                                }
                            }
                        }
                        if (resolveRetry > 0) {
                            Button(
                                onClick = { resolveRetry++ },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF85)),
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
                            ) {
                                Text("Reintentar", color = Color.Black)
                            }
                        }
                    } else {
                        VideoPlayer(
                            session = storyPlayer,
                            stateId = state.id,
                            videoUrl = resolvedVideoUrl ?: "",
                            stableVideoUrl = stableStoryUrl,
                            isMuted = isMuted,
                            isPaused = isPaused,
                            onDurationReady = { durationMs ->
                                currentDurationMs = durationMs.toLong()
                            },
                            onReady = { isContentLoaded = true },
                            onMediaEnded = {
                                videoEnded = true
                                elapsedMs = currentDurationMs
                                if (currentStatusIndex < userStates.lastIndex) {
                                    currentStatusIndex++
                                } else {
                                    onNextUser()
                                }
                            },
                            onUnavailable = {
                                // Fase 2B: video VCDN no disponible — saltar a la siguiente story
                                videoEnded = true
                                elapsedMs = currentDurationMs
                                if (currentStatusIndex < userStates.lastIndex) {
                                    currentStatusIndex++
                                } else {
                                    onNextUser()
                                }
                            },
                            videoTrim = metadata.videoTrim,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                if (carouselImages.size > 1) {
                    val photoPagerState = rememberPagerState(pageCount = { carouselImages.size })
                    // Observe the painter of the currently visible carousel page so the
                    // progress bar only starts once the visible photo has decoded.
                    val visiblePainter = rememberAsyncImagePainter(model = carouselImages.getOrNull(photoPagerState.currentPage) ?: carouselImages.first())
                    LaunchedEffect(visiblePainter.state) {
                        if (visiblePainter.state is AsyncImagePainter.State.Success || visiblePainter.state is AsyncImagePainter.State.Error) {
                            isContentLoaded = true
                        }
                    }
                    // Safety timeout: release the bar if the painter never reports state.
                    LaunchedEffect(carouselImages) {
                        kotlinx.coroutines.delay(3500L)
                        isContentLoaded = true
                    }
                    HorizontalPager(
                        state = photoPagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { pIndex ->
                        AsyncImage(
                            model = carouselImages[pIndex],
                            contentDescription = "Foto ${pIndex + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                } else {


                    val imageUrl = carouselImages.firstOrNull() ?: state.mediaUrl
                    if (!imageUrl.isNullOrEmpty()) {
                        val resolvedStoryResource = com.example.media.social.StoryMediaResolver.rememberResolvedStoryMediaResource(state)
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val scope = rememberCoroutineScope()
                        LaunchedEffect(userStates, currentStatusIndex) {
                            com.example.media.social.StoryPreloader.preloadStories(context, userStates, currentStatusIndex, 0, scope)
                        }
                        // Drive isContentLoaded from the Coil painter that ALSO renders,
                        // so the state we observe is the state of the visible image.
                        val imageModel = when (resolvedStoryResource) {
                            is com.example.media.model.MediaResource.Local -> java.io.File(resolvedStoryResource.path)
                            is com.example.media.model.MediaResource.Remote -> {
                                val u = resolvedStoryResource.url
                                if (u.contains(".m3u8", ignoreCase = true)) null else u
                            }
                            else -> null
                        }
                        if (imageModel != null) {
                            val painter = rememberAsyncImagePainter(model = imageModel)
                            LaunchedEffect(painter.state) {
                                if (painter.state is AsyncImagePainter.State.Success || painter.state is AsyncImagePainter.State.Error) {
                                    isContentLoaded = true
                                }
                            }
                            // Safety timeout: if the painter never reports Success/Error
                            // (e.g. Coil request hangs), release the bar so the story can
                            // advance instead of showing a stuck spinner over a real image.
                            LaunchedEffect(imageModel) {
                                kotlinx.coroutines.delay(3500L)
                                isContentLoaded = true
                            }
                            androidx.compose.foundation.Image(
                                painter = painter,
                                contentDescription = "Estado Media",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            // Missing / Loading — release the bar so the story can advance.
                            LaunchedEffect(Unit) { isContentLoaded = true }
                            com.example.media.ui.MediaRenderer(
                                resource = resolvedStoryResource,
                                contentDescription = "Estado Media",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }

        RenderOverlays(metadata.overlaysBase64)

        // Loading overlay: shown while the story media is being fetched/decoded.
        // Keeps the top progress bar frozen (the timing loop waits on isContentLoaded)
        // and gives the user a clear "loading" signal instead of a black screen.
        if (!isContentLoaded && state.mediaType != "text") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = IosSettingsColors.label, strokeWidth = 3.dp)
            }
        }

        // Cinematic Filter Overlay
        metadata.filter?.let { activeFilter ->
            when (activeFilter) {
                "Atardecer Criollo 🌅" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFFFF5722).copy(alpha = 0.40f), Color(0xFFFFC107).copy(alpha = 0.25f))
                                )
                            )
                    )
                }
                "Neon Petare 🌌" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0xFFEC407A).copy(alpha = 0.35f), IosSettingsColors.blue.copy(alpha = 0.20f), Color.Transparent)
                                )
                            )
                    )
                }
                "Retro VHS 📺" -> {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val h = size.height
                        val w = size.width
                        var y = 0f
                        while (y < h) {
                            drawLine(
                                color = IosSettingsColors.separator,
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = 1.5f
                            )
                            y += 10f
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 140.dp, start = 20.dp)
                    ) {
                        Text(
                            text = "REC 🔴  PLAY ▶\n00:${String.format("%02d", (elapsedMs / 1000 % 60).toInt())}",
                            color = Color(0xFF00FF85),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.TopStart)
                        )
                    }
                }
            }
        }

        // Inner Carousel Dots Indicator
        if (carouselImages.size > 1 && state.mediaType == "image") {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(carouselImages.size) { idx ->
                    Box(
                        modifier = Modifier
                            .size(if (idx == innerCarouselIndex) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (idx == innerCarouselIndex) Color(0xFF00FF85) else Color.White.copy(alpha = 0.5f))
                    )
                }
            }
        }

        // Top UI HUD (Progress Indicator + Profile Details)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Linear Progress indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                userStates.forEachIndexed { index, _ ->
                    val segmentProgress = when {
                        index < currentStatusIndex -> 1f
                        index == currentStatusIndex -> progress
                        else -> 0f
                    }
                    LinearProgressIndicator(
                        progress = { segmentProgress },
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = IosSettingsColors.label,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
            }

            // Profile info
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            val targetUserId = state.userId
                            if (targetUserId.isNotBlank()) {
                                onNavigateToUserProfile?.invoke(targetUserId)
                            }
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, Color(0xFF00FF85), CircleShape)
                                .padding(2.dp)
                        ) {
                            PanaAvatar(
                                avatarUrl = identityState?.avatarUrl ?: profile.avatarUrl,
                                userId = state.userId,
                                placeholderName = identityState?.displayName ?: (identityState?.displayName ?: profile.displayName),
                                size = 38.dp,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = (identityState?.displayName ?: profile.displayName),
                                color = IosSettingsColors.label,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = formattedTime,
                                color = IosSettingsColors.secondaryLabel,
                                fontSize = 11.sp
                            )
                        }
                    }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.mediaType == "video") {
                        IconButton(
                            onClick = { isMuted = !isMuted },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Text(
                                text = if (isMuted) "🔇" else "🔊",
                                fontSize = 16.sp
                            )
                        }
                    }

                    // Options menu for Owner / Viewers
                    Box {
                        IconButton(
                            onClick = { showOwnerMenu = true },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Opciones",
                                tint = IosSettingsColors.label
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showOwnerMenu,
                            onDismissRequest = { showOwnerMenu = false },
                            modifier = Modifier.background(Color(0xFF1E222B))
                        ) {
                            if (isOwner) {
                                DropdownMenuItem(
                                    text = { Text("Eliminar estado para todos", color = Color(0xFFFF4D4D)) },
                                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFFF4D4D)) },
                                    onClick = {
                                        showOwnerMenu = false
                                        viewModel.deleteState(state.id) {
                                            onClose()
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Ver espectadores", color = IosSettingsColors.label) },
                                    leadingIcon = { Text("👁", fontSize = 16.sp) },
                                    onClick = {
                                        showOwnerMenu = false
                                        showSpectatorsSheet = true
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Borrar historia para mí", color = Color(0xFFFF4D4D)) },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFFF4D4D)) },
                                    onClick = {
                                        showOwnerMenu = false
                                        viewModel.deleteStateForMe(state.id) {
                                            onClose()
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Silenciar historias", color = IosSettingsColors.label) },
                                    leadingIcon = { Icon(Icons.Default.MoreVert, contentDescription = null, tint = IosSettingsColors.label) },
                                    onClick = {
                                        showOwnerMenu = false
                                        Toast.makeText(context, "Historias de ${(identityState?.displayName ?: profile.displayName)} silenciadas", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Compartir enlace", color = IosSettingsColors.label) },
                                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null, tint = IosSettingsColors.label) },
                                onClick = {
                                    showOwnerMenu = false
                                    viewModel.incrementShare(state.id)
                                    val shareUrl = "https://panalink.app/status/${state.id}"
                                    copyToClipboard(context, shareUrl)
                                    shareText(context, "¡Mira el estado de ${(identityState?.displayName ?: profile.displayName)} en PanaLink! 👉 $shareUrl")
                                    reactionMessage = "¡Enlace copiado al portapapeles!"
                                }
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = IosSettingsColors.label)
                    }
                }
                } // closes outer Row (inside Box)
            }

            // Acción 5: Viewers marquee (owner-only) — BELOW the author name, never
            // above it. Auto-scrolling carousel: avatars slide in from the right and
            // slide out on the left.
            if (isMyStory) {
                ViewersMarquee(
                    spectators = spectatorsList,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                )
            }
        }

        // Large animated heart popped on double tap
        AnimatedVisibility(
            visible = showDoubleTapHeart,
            enter = scaleIn(initialScale = 0.4f) + fadeIn(),
            exit = scaleOut(targetScale = 1.6f) + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color(0xFFFF2D55),
                modifier = Modifier.size(110.dp)
            )
        }
        LaunchedEffect(showDoubleTapHeart) {
            if (showDoubleTapHeart) {
                delay(700)
                showDoubleTapHeart = false
            }
        }

        // Unified Bottom Interactivity Panel
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .pointerInput(state.id) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount < -15f) {
                            viewModel.loadSpectators(state.id)
                            showSpectatorsSheet = true
                        }
                    }
                }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                    )
                )
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Caption overlay — the caption is rendered CENTERED in the row below,
            // so it is not repeated here (avoiding the duplicated text).

            // Acción 2: Owner-only eye icon + views count (left) and the CENTERED
            // caption the author wrote for the publication (no hardcoded "Pana Vídeo").
            Box(modifier = Modifier.fillMaxWidth()) {
                val realViewsCount = spectatorsList.size

                if (isMyStory) {
                    Surface(
                        onClick = {
                            viewModel.loadSpectators(state.id)
                            showSpectatorsSheet = true
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = IosSettingsColors.separator,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .testTag("views_counter_pill")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Ver espectadores",
                                tint = IosSettingsColors.label,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "$realViewsCount",
                                color = IosSettingsColors.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Centered publication caption (the comment the author wrote).
                if (state.mediaType != "text" && cleanCaption.isNotBlank()) {
                    Text(
                        text = cleanCaption,
                        color = IosSettingsColors.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 64.dp)
                    )
                }

                if (metadata.musicName != null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .background(Color(0xFF00FF85).copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF00FF85).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("🎵", fontSize = 10.sp)
                        Text(
                            text = metadata.musicName,
                            color = Color(0xFF00FF85),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.widthIn(max = 120.dp)
                        )
                    }
                }
            }
            
            // Quick reactions bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val reactionEmojis = listOf("🔥", "👏", "😂", "🇻🇪", "❤️")
                reactionEmojis.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .clickable {
                                if (!isOwner) {
                                    viewModel.sendQuickReplyToAuthor(
                                        authorId = state.userId,
                                        messageText = emoji,
                                        onSuccess = {
                                            reactionMessage = "📩 DM enviado a ${(identityState?.displayName ?: profile.displayName)} ($emoji)"
                                        },
                                        onError = { err ->
                                            android.widget.Toast.makeText(context, "Error DM: $err", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        storyId = state.id,
                                        storyThumbnailUrl = state.thumbnailUrl ?: state.mediaUrl
                                    )
                                } else {
                                    reactionMessage = "¡Reaccionaste con $emoji!"
                                }
                                viewModel.addComment(state.id, emoji, onError = { err ->
                                    android.widget.Toast.makeText(context, "Error: $err", android.widget.Toast.LENGTH_LONG).show()
                                })
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 20.sp)
                    }
                }
            }
            
            // Interactive Social Floating Bar - Unified
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(28.dp))
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Public comments trigger (Left icon)
                IconButton(
                    onClick = { showCommentsSheet = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    BadgedBox(
                        badge = {
                            if (commentsList.isNotEmpty()) {
                                Badge(containerColor = Color(0xFF00FF85)) {
                                    Text("${commentsList.size}", color = Color.Black, fontSize = 9.sp)
                                }
                            }
                        }
                    ) {
                        Text("💬", fontSize = 18.sp)
                    }
                }

                // Main input area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isInputFocused = it.isFocused },
                        textStyle = TextStyle(color = IosSettingsColors.label, fontSize = 14.sp),
                        singleLine = true,
                        cursorBrush = SolidColor(Color(0xFF00FF85)),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (replyText.isNotBlank()) {
                                val textToSend = replyText
                                replyText = ""
                                focusManager.clearFocus()
                                if (!isOwner) {
                                    viewModel.sendQuickReplyToAuthor(
                                        authorId = state.userId,
                                        messageText = textToSend,
                                        onSuccess = {
                                            reactionMessage = "📩 DM enviado a ${(identityState?.displayName ?: profile.displayName)}"
                                        },
                                        storyId = state.id,
                                        storyThumbnailUrl = state.thumbnailUrl ?: state.mediaUrl
                                    )
                                } else {
                                    viewModel.addComment(state.id, textToSend)
                                    reactionMessage = "¡Comentario publicado!"
                                }
                            }
                        }),
                        decorationBox = { innerTextField ->
                            if (replyText.isEmpty()) {
                                Text(
                                    text = if (isOwner) "Añade un comentario..." else "Responde a ${(identityState?.displayName ?: profile.displayName)}...",
                                    color = IosSettingsColors.secondaryLabel,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    )
                }

                if (replyText.isNotBlank()) {
                    IconButton(
                        onClick = {
                            val textToSend = replyText
                            replyText = ""
                            focusManager.clearFocus()
                            if (!isOwner) {
                                viewModel.sendQuickReplyToAuthor(
                                    authorId = state.userId,
                                    messageText = textToSend,
                                    onSuccess = {
                                        reactionMessage = "📩 DM enviado a ${(identityState?.displayName ?: profile.displayName)}"
                                    },
                                    storyId = state.id,
                                    storyThumbnailUrl = state.thumbnailUrl ?: state.mediaUrl
                                )
                            } else {
                                viewModel.addComment(state.id, textToSend)
                                reactionMessage = "¡Comentario publicado!"
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Enviar",
                            tint = Color(0xFF00FF85),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Divider
                Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = 0.1f)))

                // Action: Like
                IconButton(
                    onClick = {
                        likeScale = 1.4f
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleLike(state.id, state.likedByMe ?: false)
                    },
                    modifier = Modifier.size(36.dp).scale(animLikeScale)
                ) {
                    Icon(
                        imageVector = if (state.likedByMe == true) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (state.likedByMe == true) Color(0xFFFF2D55) else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Action: Favorite
                IconButton(
                    onClick = {
                        viewModel.toggleFavorite(state.id, state.favoritedByMe ?: false)
                        reactionMessage = if (state.favoritedByMe == true) "Eliminado de favoritos" else "⭐ Guardado en favoritos"
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (state.favoritedByMe == true) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "Favorito",
                        tint = if (state.favoritedByMe == true) Color(0xFFFFCC00) else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Action: Share
                IconButton(
                    onClick = {
                        viewModel.incrementShare(state.id)
                        val shareUrl = "https://panalink.app/status/${state.id}"
                        copyToClipboard(context, shareUrl)
                        shareText(context, "Mira esta historia en PanaLink: $shareUrl")
                        reactionMessage = "¡Enlace copiado!"
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Compartir",
                        tint = IosSettingsColors.label,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Temp floating feedback badge
        reactionMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 160.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF00FF85), RoundedCornerShape(20.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(msg, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            LaunchedEffect(msg) {
                delay(2000)
                reactionMessage = null
            }
        }

        // --- Custom BottomSheet: Comments ---
        AnimatedVisibility(
            visible = showCommentsSheet,
            enter = fadeIn() + expandIn(expandFrom = Alignment.BottomCenter),
            exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { showCommentsSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f) // Full standard bottom sheet height
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(Color(0xFF151821))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .clickable(enabled = false) {}
                        .imePadding()
                ) {
                    // Grabber handle
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 12.dp)
                            .width(42.dp)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                    )

                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Comentarios (${filteredComments.size})",
                            color = IosSettingsColors.label,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { showCommentsSheet = false }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = IosSettingsColors.label)
                        }
                    }

                    Divider(color = IosSettingsColors.separator)

                    // List of comments
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (filteredComments.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Aún no hay comentarios.\n¡Sé el primero en comentar! 💬",
                                        color = IosSettingsColors.secondaryLabel,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            items(filteredComments) { comment ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        PanaAvatar(
                                            avatarUrl = comment.avatarUrl,
                                            userId = comment.userId,
                                            placeholderName = comment.authorName,
                                            size = 34.dp,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = if (comment.deletedAt != null) "Eliminado" else comment.authorName,
                                                color = IosSettingsColors.label,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = formatCreatedTime(comment.createdAt),
                                                color = IosSettingsColors.label.copy(alpha = 0.45f),
                                                fontSize = 11.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                            if (comment.deletedAt != null) {
                                                Text(
                                                    text = "Este comentario ha sido eliminado",
                                                    color = Color.White.copy(alpha =   0.4f),
                                                    fontSize = 13.sp,
                                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                                )
                                            } else {
                                                com.example.ui.components.CommentMediaText(
                                                    text = comment.text,
                                                    fallbackColor = Color.White.copy(alpha =   0.9f)
                                                )
                                            }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        // Reply handler
                                        if (comment.deletedAt == null) {
                                            Text(
                                                text = "Responder",
                                                color = Color(0xFF00FF85),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clickable {
                                                        replyText = "@${comment.authorName} "
                                                     }
                                                    .padding(vertical = 2.dp, horizontal = 4.dp)
                                            )
                                        }
                                    }

                                    // Delete comment own button
                                    if (comment.userId == currentUid && comment.deletedAt == null) {
                                        IconButton(
                                            onClick = {
                                                viewModel.deleteComment(state.id, comment.id)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = "Borrar comentario",
                                                tint = IosSettingsColors.secondaryLabel,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Divider(color = IosSettingsColors.separator)

                    // Text write comments input bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .background(Color(0xFF1F2C34))
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { showGifPicker = true },
                        contentAlignment = Alignment.Center
                    ) {
                    Text("GIF", color = Color(0xFF4FC3F7), fontSize =   11.sp, fontWeight = FontWeight.Bold)
                }
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = { Text("Escribe un comentario...", color = IosSettingsColors.label.copy(alpha = 0.4f), fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(color = IosSettingsColors.label, fontSize = 14.sp),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1E222B),
                                unfocusedContainerColor = Color(0xFF1E222B),
                                focusedBorderColor = Color(0xFF00FF85),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                            ),
                            singleLine = true,
                            trailingIcon = {
                                if (replyText.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            keyboardController?.hide()
                                            val textToSend = replyText
                                            replyText = ""
                                            viewModel.addComment(state.id, textToSend, onError = { err ->
                                                android.widget.Toast.makeText(context, "Error: $err", android.widget.Toast.LENGTH_LONG).show()
                                            })
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Send,
                                            contentDescription = "Enviar",
                                            tint = Color(0xFF00FF85),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
if (showGifPicker) {
            com.example.ui.components.KlipyGifStickerPicker(
                onSelected = { sticker ->
                    val textToSend = com.example.ui.components.buildCommentGifText(sticker)

                    viewModel.addComment(state.id, textToSend, onError = { err ->
                        android.widget.Toast.makeText(context, "Error: $err", android.widget.Toast.LENGTH_LONG).show()
                    })
                    showGifPicker = false
    },
                onDismiss = { showGifPicker = false }
            )
        }

        // --- Custom BottomSheet: Spectators (Who viewed) ---
        AnimatedVisibility(
            visible = showSpectatorsSheet,
            enter = fadeIn() + expandIn(expandFrom = Alignment.BottomCenter),
            exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { showSpectatorsSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.60f)
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(Color(0xFF151821))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .clickable(enabled = false) {}
                        .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 12.dp)
                            .width(42.dp)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Personas que vieron tu estado (${spectatorsList.size})",
                            color = IosSettingsColors.label,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { showSpectatorsSheet = false }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = IosSettingsColors.label)
                        }
                    }

                    Divider(color = IosSettingsColors.separator)

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (spectatorsList.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Nadie ha visto tu estado todavía.\n¡Comparte el enlace para tener más vistas! 👁",
                                        color = IosSettingsColors.secondaryLabel,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            items(spectatorsList) { spectator ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        PanaAvatar(
                                            avatarUrl = spectator.avatarUrl,
                                            userId = spectator.viewerId,
                                            placeholderName = spectator.name,
                                            size = 38.dp,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = spectator.name,
                                            color = IosSettingsColors.label,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Visto hace ${formatCreatedTime(spectator.viewedAt)}",
                                            color = IosSettingsColors.secondaryLabel,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Acción 4: Floating reactions overlay (live-style) — owner only sees incoming reactions
        if (isMyStory && floatingReactions.isNotEmpty()) {
            FloatingReactionsContainer(
                reactions = floatingReactions,
                onDismiss = { id ->
                    floatingReactions = floatingReactions.filterNot { it.id == id }
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

// Acción 4: Floating reactions container (live-style)
@Composable
fun FloatingReactionsContainer(
    reactions: List<FloatingReactionLog>,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (reactions.isEmpty()) return
    Box(modifier = modifier.fillMaxSize()) {
        reactions.forEachIndexed { _, reaction ->
            val lane = remember(reaction.id) { (reaction.id.hashCode() % 4) }
            val isLeftLane = remember(reaction.id) { lane % 2 == 0 }
            val offsetY = remember(reaction.id) { Animatable(0f) }
            val offsetX = remember(reaction.id) { Animatable(0f) }
            val alpha = remember(reaction.id) { Animatable(0f) }
            val scale = remember(reaction.id) { Animatable(0.5f) }
            LaunchedEffect(reaction.id) {
                // Pop-in + float UP along a NARROW lateral lane. Each reaction stays
                // close to its edge of the screen and never drifts across the media
                // center, so the publication's content isn't blocked.
                alpha.animateTo(1f, tween(180, easing = LinearOutSlowInEasing))
                val riseDistance = (-160 - (reaction.id.hashCode() % 3) * 36).toFloat()
                scale.animateTo(1f, tween(220, easing = LinearOutSlowInEasing))
                offsetX.animateTo(34f * if (isLeftLane) -1f else 1f, tween(2600, easing = LinearOutSlowInEasing))
                offsetY.animateTo(riseDistance, tween(2600, easing = LinearOutSlowInEasing))
                alpha.animateTo(0f, tween(500, easing = FastOutLinearInEasing))
                onDismiss(reaction.id)
            }
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        this.alpha = alpha.value
                        this.translationY = offsetY.value
                        this.translationX = offsetX.value
                        this.scaleX = scale.value
                        this.scaleY = scale.value
                    }
                    .align(if (isLeftLane) Alignment.BottomStart else Alignment.BottomEnd)
                    .padding(
                        bottom = 110.dp,
                        start = if (isLeftLane) 24.dp else 0.dp,
                        end = if (!isLeftLane) 24.dp else 0.dp
                    )
                    .size(40.dp)
            ) {
                PanaAvatar(
                    avatarUrl = reaction.avatarUrl,
                    userId = reaction.userId,
                    placeholderName = reaction.userId,
                    size = 32.dp,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color.White, CircleShape)
                )
                Text(
                    text = reaction.emoji,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset { IntOffset(8, 8) }
                )
            }
        }
    }
}

// Acción 5: Viewers marquee — auto-scrolling carousel BELOW the author name.
// Avatars slide in from the right edge and slide out on the left edge
// (the owner sees who has watched as a living, continuously moving ticker).
@Composable
fun ViewersMarquee(
    spectators: List<StatusViewer>,
    modifier: Modifier = Modifier
) {
    if (spectators.isEmpty()) return

    val scroller = rememberLazyListState()
    val avatarSize = 26.dp
    val gap = 6.dp

    // Auto-scroll: when the last avatar reaches the left edge, jump back to the
    // start (seamless, since the same pack repeats and the "+N" pill is appended).
    LaunchedEffect(Unit) {
        val total = (spectators.size + 1).toLong()
        var forward = true
        while (total > 1L) {
            if (forward) {
                scroller.animateScrollToItem(spectators.size) // scroll to the "+N" pill
                forward = false
            } else {
                scroller.scrollToItem(0)
                forward = true
            }
            delay(4500)
        }
    }

    LazyRow(
        state = scroller,
        modifier = modifier
            .fillMaxWidth()
            .height(avatarSize + 4.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
        userScrollEnabled = false
    ) {
        items(spectators.take(20)) { spectator ->
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
            ) {
                PanaAvatar(
                    avatarUrl = spectator.avatarUrl,
                    userId = spectator.viewerId,
                    placeholderName = spectator.name,
                    size = avatarSize - 4.dp,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (spectators.size > 20) {
            item {
                Box(
                    modifier = Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+${spectators.size - 20}",
                        color = IosSettingsColors.label,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

fun shareText(context: android.content.Context, text: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Compartir estado"))
}

fun copyToClipboard(context: android.content.Context, text: String) {
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = android.content.ClipData.newPlainText("Estado Link", text)
    clipboard.setPrimaryClip(clip)
}

@Composable
fun VideoPlayer(
    session: com.example.core.media.StoryVideoPlayerSession,
    stateId: String,
    videoUrl: String,
    isMuted: Boolean,
    isPaused: Boolean,
    onDurationReady: (Int) -> Unit,
    onReady: (() -> Unit)? = null,
    onMediaEnded: (() -> Unit)? = null,
    onUnavailable: (() -> Unit)? = null,
    videoTrim: Pair<Float, Float>? = null,
    stableVideoUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var hasError by remember(stateId) { mutableStateOf(false) }
    var isBuffering by remember(stateId) { mutableStateOf(true) }

    // Fase 3 instrumentacion: el session loguea con TAG StoryVideoPlayer.
    // Estos callbacks derivan el estado visual local sin listeners duplicados.
    LaunchedEffect(stateId, videoUrl, videoTrim, stableVideoUrl) {
        hasError = false
        isBuffering = true
        if (videoUrl.isBlank()) {
            android.util.Log.w("StoryVideoPlayer", "stateId=$stateId URL vacia (VCDN no disponible); skip")
            com.example.feature.diagnostics.StoryDiagnostics.failed(
                "URL vacía",
                correlationId = stateId.take(36),
                details = "stable=${stableVideoUrl?.take(40)}"
            )
            isBuffering = false
            onUnavailable?.invoke()
            return@LaunchedEffect
        }
        session.onReady = { onReady?.invoke() }
        // Acción 1 (fix): sin estas dos conexiones la barra usaba la duración por
        // defecto (6 s) y terminaba antes que el vídeo, además de no avanzar la
        // historia al acabar el clip. La sesión ya emite la duración real
        // (ajustada al trim) y el fin de media; aquí solo se propagan a la UI.
        session.onDurationReady = { durationMs -> onDurationReady(durationMs) }
        session.onMediaEnded = { onMediaEnded?.invoke() }
        session.onStateChanged = { stateName, pos, buffered, pct, duration ->
            isBuffering = (stateName == "BUFFERING" || stateName == "IDLE")
        }
        session.onError = { errStateId, codeName, code ->
            if (errStateId == stateId) {
                hasError = true
                com.example.feature.diagnostics.StoryDiagnostics.failed(
                    "onError (UI)",
                    correlationId = stateId.take(36),
                    details = "codeName=$codeName, code=$code"
                )
            }
        }
        session.play(stateId, videoUrl, isMuted, videoTrim, stableVideoUrl)
    }

    LaunchedEffect(isMuted) { session.setMuted(isMuted) }
    LaunchedEffect(isPaused) { session.setPaused(isPaused) }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                // Misma superficie que el reproductor de reels: TextureView dentro de
                // un PlayerView inflado de layout (view_story_player.xml). El visor de
                // stories dibuja overlays Compose encima del video; un SurfaceView
                // rompería ese orden y causaría "punch-through"/negros al animar.
                (android.view.LayoutInflater.from(ctx)
                    .inflate(com.example.R.layout.view_story_player, null) as androidx.media3.ui.PlayerView).apply {
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    // Player estable: se setea UNA vez durante la vida del visor.
                    player = session.player
                }
            },
            update = { playerView ->
                // Media3 setPlayer es no-op si el player es el mismo (verificado).
                if (playerView.player !== session.player) playerView.player = session.player
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering && !hasError) {
            CircularProgressIndicator(
                color = IosSettingsColors.label,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (hasError) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha=0.6f), RoundedCornerShape(8.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = IosSettingsColors.label, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Error al reproducir video", color = IosSettingsColors.label, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    val ctx = context
                    hasError = false
                    isBuffering = true
                    session.onStateChanged = null
                    
                    if (com.example.util.NetworkMonitor.isOnline.value) {
                        // Resolve on IO thread to avoid blocking Main; resolveMediaUrlFresh
                        // may perform network I/O (VCDN BFF call). El retry re-resuelve
                        // desde el puntero ESTABLE (vcdn://), nunca desde la URL firmada
                        // ya caducada (eso devolvería el mismo token expirado).
                        kotlinx.coroutines.MainScope().launch(Dispatchers.IO) {
                            val retryStart = System.currentTimeMillis()
                            val stableForRetry = stableVideoUrl ?: videoUrl
                            com.example.feature.diagnostics.StoryDiagnostics.started(
                                "Reintentar (UI)",
                                correlationId = stateId.take(36),
                                details = "stable=${stableForRetry.take(40)}"
                            )
                            val newUrl = com.example.data.repository.CdnManager.resolveMediaUrlFresh(stableForRetry)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                if (newUrl.isBlank() || newUrl.startsWith("vcdn://")) {
                                    com.example.feature.diagnostics.StoryDiagnostics.failed(
                                        "Reintentar (UI)",
                                        retryStart,
                                        correlationId = stateId.take(36),
                                        details = "noUrl=<blank>"
                                    )
                                    onUnavailable?.invoke()
                                } else {
                                    com.example.feature.diagnostics.StoryDiagnostics.completed(
                                        "Reintentar (UI)",
                                        retryStart,
                                        correlationId = stateId.take(36)
                                    )
                                    session.onStateChanged = { s, p, b, pct, d ->
                                        isBuffering = (s == "BUFFERING" || s == "IDLE")
                                    }
                                    session.play(stateId, newUrl.ifBlank { videoUrl }, isMuted, videoTrim, stableVideoUrl)
                                }
                            }
                        }
                    } else {
                        onUnavailable?.invoke()
                        Toast.makeText(ctx, "No hay conexión a Internet", Toast.LENGTH_SHORT).show()
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF85))) {
                    Text("Reintentar", color = Color.Black)
                }
            }
        }
    }
}
