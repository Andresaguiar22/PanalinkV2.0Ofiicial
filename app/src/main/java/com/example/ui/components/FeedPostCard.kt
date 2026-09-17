package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screen.parseStateMetadata
import com.example.identity.model.toIdentityUiState
import com.example.ui.screen.RenderOverlays
import com.example.data.model.PostDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs

@Composable
internal fun rememberResolvedMediaUrl(rawUrl: String?): String {
    val raw = rawUrl?.trim().orEmpty()
    if (raw.isEmpty()) return ""
    if (com.example.data.repository.VcdnUrlResolver.isVcdnUrl(raw)) {
        var resolvedUrl by remember(raw) { mutableStateOf("") }
        LaunchedEffect(raw) {
            resolvedUrl = com.example.data.repository.VcdnUrlResolver.resolve(raw) ?: ""
        }
        return resolvedUrl
    }
    return com.example.data.repository.CdnManager.resolveMediaUrlSync(raw)
}

/**
 * Al igual que [rememberResolvedMediaUrl] pero fuerza un re-resolve VCDN cuando la
 * URL firmada esta por expirar. En los reels/feed largos, la URL firmada de VCDN
 * caduca a los ~60 s; si el player sigue montado con la URL vieja, la reproduccion
 * se atasca a esa marca (http 401 silencioso). Devolver la misma instancia de URL
 * mientras no caduque hace que [SimpleVideoPreviewPlayer] reutilice su player sin
 * reconstruirlo; al caducar, el key cambia y el player se reconstruye con una URL
 * fresca (equivalente a reintentar).
 */
@Composable
internal fun rememberFreshMediaUrl(rawUrl: String?): String {
    val raw = rawUrl?.trim().orEmpty()
    if (raw.isEmpty()) return ""
    if (com.example.data.repository.VcdnUrlResolver.isVcdnUrl(raw)) {
        var fresh by remember(raw) { mutableStateOf("") }
        // ResoluciOn normal (usa la cache si hay URL vigente; si no, BFF).
        LaunchedEffect(raw) {
            fresh = com.example.data.repository.VcdnUrlResolver.resolve(raw) ?: ""
        }
        // Pre-refresh: ~6s antes de que caduque la URL actual, la re-resolvemos
        // forzado. Cuando fresh cambia (primera carga o refresh), se reprograma.
        // Nunca bloquea el hilo de UI: no hacemos I/O aqui, solo delay.
        LaunchedEffect(raw, fresh) {
            if (fresh.isBlank()) return@LaunchedEffect
            val expiresAt = com.example.data.repository.VcdnUrlResolver.expiresAtMillisOf(fresh)
            val remaining = expiresAt - System.currentTimeMillis() - 6_000L
            if (remaining > 0L) {
                kotlinx.coroutines.delay(remaining)
                val next = com.example.data.repository.VcdnUrlResolver.resolve(raw, forceRefresh = true) ?: ""
                if (next != fresh) fresh = next
            }
        }
        return fresh
    }
    return com.example.data.repository.CdnManager.resolveMediaUrlSync(raw)
}

private fun urlPathOf(url: String): String =
    url.substringBefore('?').substringBefore('#').lowercase()

private val VIDEO_EXTENSIONS = listOf(".mp4", ".mov", ".webm", ".mkv", ".3gp", ".avi", ".m3u8", ".m3u")
private val AUDIO_EXTENSIONS = listOf(".mp3", ".wav", ".ogg", ".m4a", ".aac", ".flac", ".opus")
private val DOC_EXTENSIONS = listOf(".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".zip", ".rar", ".txt", ".csv", ".json")
private val VIDEO_SEGMENTS = setOf("video", "videos", "stream")
private val AUDIO_SEGMENTS = setOf("audio", "audios", "voice")
private val DOC_SEGMENTS = setOf("document", "documents", "docs")

private fun hasSegment(url: String, segments: Set<String>): Boolean =
    urlPathOf(url).split('/').any { it in segments }

fun isVideoUrl(url: String): Boolean {
    if (url.isBlank()) return false
    if (url.startsWith("vcdn://", ignoreCase = true)) return true
    val path = urlPathOf(url)
    if (path.endsWith(".m3u8") || path.endsWith(".m3u")) return true
    return hasSegment(url, VIDEO_SEGMENTS) || VIDEO_EXTENSIONS.any { path.endsWith(it) }
}

fun isAudioUrl(url: String): Boolean {
    if (url.isBlank()) return false
    val path = urlPathOf(url)
    return hasSegment(url, AUDIO_SEGMENTS) || AUDIO_EXTENSIONS.any { path.endsWith(it) }
}

fun isDocumentUrl(url: String): Boolean {
    if (url.isBlank()) return false
    val path = urlPathOf(url)
    return hasSegment(url, DOC_SEGMENTS) || DOC_EXTENSIONS.any { path.endsWith(it) } || path.contains("application/")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun FeedPostCard(
    post: PostDto,
    onLikeClick: () -> Unit = {},
    onCommentClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onEditClick: (String) -> Unit = {},
    onMediaClick: (List<String>, Int, String?, Long) -> Unit = { _, _, _, _ -> },
    onAudioPlaylistClick: (PostDto) -> Unit = {},
    onShareClick: () -> Unit = {},
    onSaveClick: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val currentUserId = remember {
        try { com.example.data.supabase.SupabaseClient.currentUser?.id } catch (e: Throwable) { null }
    }
    val isMyPost = currentUserId != null && post.userId == currentUserId
    var showMenu by remember { mutableStateOf(false) }

    val identityRepository = remember { com.example.identity.bridge.LegacyIdentityBridge(context).identityRepository }
    val identityState by identityRepository.observeIdentity(post.userId ?: "").collectAsStateWithLifecycle(initialValue = com.example.identity.memory.IdentityMemoryCache.profiles.get(post.userId ?: "")?.toIdentityUiState())

    var isSaved by rememberSaveable(post.id) { mutableStateOf(false) }
    var isExpandedText by rememberSaveable(post.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var isFollowingAuthor by rememberSaveable(post.id) { mutableStateOf(false) }
    var followChecked by rememberSaveable(post.id) { mutableStateOf(false) }
    LaunchedEffect(post.userId, currentUserId) {
        val authorId = post.userId
        if (!followChecked && authorId != null && currentUserId != null && com.example.util.NetworkMonitor.isOnline.value && authorId != currentUserId) {
            com.example.data.repository.ProfilesRepository().isFollowing(currentUserId, authorId)
                .onSuccess { isFollowingAuthor = it }
            followChecked = true
        }
    }

    var isLiked by rememberSaveable(post.id) { mutableStateOf(post.isLikedByMe ?: false) }
    LaunchedEffect(post.isLikedByMe) {
        post.isLikedByMe?.let { isLiked = it }
    }

    var likeScale by remember { mutableStateOf(1f) }
    val likeAnimScale by animateFloatAsState(
        targetValue = likeScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "like_scale"
    )

    var showHeartAnimation by remember { mutableStateOf(false) }

    fun performLike() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        likeScale = 1.4f
        showHeartAnimation = true
        likeScale = 1f
        onLikeClick()
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(post.id) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) + slideInVertically(initialOffsetY = { 20 }),
        exit = fadeOut()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161618))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val rawAvatar = identityState?.avatarUrl ?: post.profile?.avatarUrl
                val resolvedAvatar = remember(rawAvatar) {
                    com.example.data.repository.CdnManager.resolveAvatarUrl(rawAvatar)
                }
                
                Box(modifier = Modifier.clickable { onProfileClick() }) {
                    PanaAvatar(
                        avatarUrl = resolvedAvatar,
                        userId = identityState?.userId ?: post.profile?.id,
                        size = 44.dp,
                        borderWidth = 0.dp,
                        borderColor = Color.Transparent,
                        contentDescription = identityState?.displayName ?: post.profile?.displayName,
                        placeholderName = identityState?.displayName ?: post.profile?.displayName ?: ""
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = identityState?.displayName ?: post.profile?.displayName ?: "Pana de la Comunidad",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { onProfileClick() }
                        )
                        if (!isMyPost) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                onClick = {
                                    val uid = post.userId ?: return@Surface
                                    val me = currentUserId ?: return@Surface
                                    val next = !isFollowingAuthor
                                    isFollowingAuthor = next
                                    scope.launch {
                                        val repo = com.example.data.repository.ProfilesRepository()
                                        val result = if (next) repo.followUser(me, uid) else repo.unfollowUser(me, uid)
                                        result.onFailure { isFollowingAuthor = !next }
                                    }
                                },
                                shape = RoundedCornerShape(20.dp),
                                border = if (!isFollowingAuthor) BorderStroke(1.dp, Color(0xFF45B6FF)) else null,
                                color = if (isFollowingAuthor) Color.Transparent else Color(0xFF45B6FF).copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = if (isFollowingAuthor) "Siguiendo" else "Seguir",
                                    color = Color(0xFF45B6FF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    val timeStr = remember(post.createdAt) {
                        try {
                            if (post.createdAt != null) {
                                val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                                parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                val date = parser.parse(post.createdAt)
                                val diff = System.currentTimeMillis() - (date?.time ?: System.currentTimeMillis())
                                val minutes = (diff / 60000).toInt()
                                when {
                                    minutes < 1 -> "hace un momento"
                                    minutes < 60 -> "hace ${minutes}m"
                                    minutes < 1440 -> "hace ${minutes / 60}h"
                                    else -> "hace ${minutes / 1440}d"
                                }
                            } else {
                                "hace poco"
                            }
                        } catch (e: Exception) {
                            "hace poco"
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = timeStr, color = Color.Gray, fontSize = 12.sp)
                        Text(text = "  ·  ", color = Color.Gray, fontSize = 12.sp)
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "Público",
                            tint = Color.Gray,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones", tint = Color.Gray)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color(0xFF2A2A30))
                    ) {
                        if (isMyPost) {
                            DropdownMenuItem(
                                text = { Text("Editar", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White) },
                                onClick = {
                                    showMenu = false
                                    onEditClick(post.content ?: "")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar", color = Color(0xFFFF4D4D)) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF4D4D)) },
                                onClick = {
                                    showMenu = false
                                    onDeleteClick()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Reportar", color = Color.Gray) },
                                leadingIcon = { Icon(Icons.Default.Report, contentDescription = null, tint = Color.Gray) },
                                onClick = {
                                    showMenu = false
                                    Toast.makeText(context, "Publicación reportada", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }

            val youtubeVideoId = remember(post.content) {
                if (!post.content.isNullOrBlank()) {
                    com.example.util.YouTubeUrlParser.extractYouTubeVideoId(post.content)
                } else null
            }
            val metadata = remember(post.content) { parseStateMetadata(post.content) }
            val cleanCaption = metadata.baseCaption

            if (cleanCaption.isNotBlank() && youtubeVideoId.isNullOrBlank()) {
                val isLongText = cleanCaption.length > 150 || cleanCaption.lines().size > 4
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Text(
                        text = cleanCaption,
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        maxLines = if (isExpandedText) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(enabled = isLongText) { isExpandedText = !isExpandedText }
                    )
                    if (isLongText && !isExpandedText) {
                        Row(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable { isExpandedText = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ver más",
                                color = Color(0xFF45B6FF),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color(0xFF45B6FF),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            } else if (!youtubeVideoId.isNullOrBlank()) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    YouTubePostCard(videoId = youtubeVideoId, originalText = cleanCaption)
                }
            }

            val allMediaList = (post.mediaUrls ?: emptyList()).filter { it.isNotBlank() }
            val mediaImagesAndVideos = remember(allMediaList) {
                allMediaList.filter { !isAudioUrl(it) && !isDocumentUrl(it) }
            }
            val mediaDocuments = remember(allMediaList) {
                allMediaList.filter { isDocumentUrl(it) }
            }
            val voiceAudioUrl = remember(post.audioUrl, allMediaList) {
                post.audioUrl ?: allMediaList.firstOrNull { isAudioUrl(it) }
            }

            if (mediaImagesAndVideos.isNotEmpty()) {
                val pagerState = rememberPagerState(pageCount = { mediaImagesAndVideos.size })
                var isMuted by remember { mutableStateOf(true) }
                // Per-media video position tracking keyed by stable raw URL
                val videoPositionMap = remember { mutableStateMapOf<String, Long>() }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(460.dp)
                        .background(Color.Black)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val url = mediaImagesAndVideos[page]
                        val resolvedUrl = rememberFreshMediaUrl(url)

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { onMediaClick(mediaImagesAndVideos, page, voiceAudioUrl, videoPositionMap[mediaImagesAndVideos[page]] ?: 0L) },
                                        onDoubleTap = { performLike() }
                                    )
                                }
                        ) {
                            if (resolvedUrl.isBlank()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Color(0xFF00A884), modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                                }
                            } else if (post.type == "VIDEO" || post.type == "REEL" || isVideoUrl(resolvedUrl)) {
                                val videoUri = remember(resolvedUrl) { Uri.parse(resolvedUrl) }
                                SimpleVideoPreviewPlayer(
                                    videoUri = videoUri,
                                    isMuted = isMuted,
                                    modifier = Modifier.fillMaxSize(),
                                    onPositionUpdate = { pos -> videoPositionMap[url] = pos },
                                    stableUrl = url
                                )
                            } else {
                                val resolvedResources = com.example.media.feed.PostMediaResolver.rememberResolvedMediaResources(
                                    mediaUrls = listOf(resolvedUrl),
                                    ownerId = post.userId
                                )
                                val mediaResource = resolvedResources.firstOrNull() ?: com.example.media.model.MediaResource.Remote(resolvedUrl)

                                com.example.media.ui.MediaRenderer(
                                    resource = mediaResource,
                                    contentDescription = "Media",
                                    modifier = Modifier
                                        .fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            RenderOverlays(metadata.overlaysBase64)

                            if (showHeartAnimation) {
                                LaunchedEffect(showHeartAnimation) {
                                    delay(800)
                                    showHeartAnimation = false
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(80.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = showHeartAnimation,
                                        enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                                        exit = scaleOut() + fadeOut()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = null,
                                            tint = Color(0xFFFF2B54),
                                            modifier = Modifier.size(80.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val currentUrl = mediaImagesAndVideos.getOrNull(pagerState.currentPage) ?: ""
                    if (post.type == "VIDEO" || post.type == "REEL" || isVideoUrl(currentUrl)) {
                        IconButton(
                            onClick = { isMuted = !isMuted },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                contentDescription = "Sonido",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (mediaImagesAndVideos.size > 1) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Expandir",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .padding(6.dp)
                            )
                            Text(
                                text = "${pagerState.currentPage + 1}/${mediaImagesAndVideos.size}",
                                color = Color.White,
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        // Premium sliding indicator (pill-style)
                        val indicatorOffset by animateFloatAsState(
                            targetValue = pagerState.currentPage * 24f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "indicatorOffset"
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width((mediaImagesAndVideos.size * 24).dp + 12.dp)
                                    .height(6.dp)
                            ) {
                                // Track
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.3f))
                                )
                                // Sliding pill indicator
                                Box(
                                    modifier = Modifier
                                        .offset(x = (indicatorOffset + 6).dp)
                                        .width(18.dp)
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    }

                    if (voiceAudioUrl != null && post.type != "VIDEO" && post.type != "REEL") {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🎵 Audio", color = Color(0xFF00FF85), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (voiceAudioUrl != null || post.type == "AUDIO") {
                val resolvedAudio = rememberResolvedMediaUrl(voiceAudioUrl)
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                    if (resolvedAudio.isBlank()) {
                        Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF00A884), modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        PlaylistAudioPlayer(audioUrls = listOf(resolvedAudio))
                    }
                }
            }

            if (mediaDocuments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    mediaDocuments.forEach { docUrl ->
                        val resolvedDocUrl = rememberResolvedMediaUrl(docUrl)
                        com.example.ui.components.chat.media.DocumentPreviewCard(
                            docUrl = resolvedDocUrl,
                            mediaSize = null,
                            bubbleColor = Color(0xFF2A2A30),
                            isSender = false,
                            senderAvatarUrl = null,
                            messageStatus = "sent",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            val postIsLiked = post.isLikedByMe ?: isLiked
            if (post.likesCount > 0 || post.commentsCount > 0 || post.sharesCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (post.likesCount > 0) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = null,
                            tint = Color(0xFF45B6FF),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "${post.likesCount}", color = Color.Gray, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    val tail = buildString {
                        val parts = mutableListOf<String>()
                        if (post.commentsCount > 0) parts.add(if (post.commentsCount == 1) "1 comentario" else "${post.commentsCount} comentarios")
                        if (post.sharesCount > 0) parts.add(if (post.sharesCount == 1) "1 vez compartido" else "${post.sharesCount} veces compartido")
                        append(parts.joinToString("  ·  "))
                    }
                    if (tail.isNotEmpty()) {
                        Text(text = tail, color = Color.Gray, fontSize = 13.sp, modifier = Modifier.clickable { onCommentClick() })
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 12.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { performLike() }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (postIsLiked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                        tint = if (postIsLiked) Color(0xFF45B6FF) else Color.Gray,
                        contentDescription = "Me gusta",
                        modifier = Modifier
                            .size(20.dp)
                            .scale(likeAnimScale)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Me gusta",
                        color = if (postIsLiked) Color(0xFF45B6FF) else Color.Gray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                VerticalDivider(
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.height(24.dp).padding(vertical = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onCommentClick() }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        tint = Color.Gray,
                        contentDescription = "Comentar",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Comentar",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                VerticalDivider(
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.height(24.dp).padding(vertical = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            onShareClick()
                            val shareIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_TEXT, "¡Mira esta publicación en PanaLink!\n${post.content ?: ""}")
                                type = "text/plain"
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Compartir publicación"))
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        tint = Color.Gray,
                        contentDescription = "Compartir",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Compartir",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (post.type == "AUDIO" || voiceAudioUrl != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = { onAudioPlaylistClick(post) }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.PlaylistPlay,
                            tint = Color(0xFF00FF85),
                            contentDescription = "Reproducir lista",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
