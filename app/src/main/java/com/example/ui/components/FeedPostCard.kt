package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screen.parseStateMetadata
import com.example.ui.screen.RenderOverlays
import com.example.data.model.PostDto
import com.example.identity.model.toIdentityUiState


private fun urlPathOf(url: String): String =
    url.substringBefore('?').substringBefore('#').lowercase()

private val VIDEO_EXTENSIONS = listOf(".mp4", ".mov", ".webm", ".mkv", ".3gp", ".avi", ".m3u8", ".m3u")
private val AUDIO_EXTENSIONS = listOf(".mp3", ".wav", ".ogg", ".m4a", ".aac", ".flac", ".opus")
private val DOC_EXTENSIONS = listOf(".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".zip", ".rar", ".txt", ".csv", ".json")
private val VIDEO_SEGMENTS = setOf("video", "videos", "stream")
private val AUDIO_SEGMENTS = setOf("audio", "audios", "voice")
private val DOC_SEGMENTS = setOf("document", "documents", "docs")

// Match on whole path segments (not substrings): the legacy Sufy bucket name was
// "panalink-audio", so contains("audio") wrongly flagged every Sufy URL.
private fun hasSegment(url: String, segments: Set<String>): Boolean =
    urlPathOf(url).split('/').any { it in segments }

fun isVideoUrl(url: String): Boolean {
    if (url.isBlank()) return false
    // VCDN pointers and HLS manifests are always video.
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

@Composable
fun FeedPostCard(
    post: PostDto,
    onLikeClick: () -> Unit = {},
    onCommentClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onEditClick: (String) -> Unit = {},
    onMediaClick: (List<String>, Int, String?) -> Unit = { _, _, _ -> },
    onAudioPlaylistClick: (PostDto) -> Unit = {},
    onShareClick: () -> Unit = {}
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

    // Real follow state (Facebook-style "Seguir" pill in header)
    var isFollowingAuthor by rememberSaveable(post.id) { mutableStateOf(false) }
    var followChecked by rememberSaveable(post.id) { mutableStateOf(false) }
    LaunchedEffect(post.userId, currentUserId) {
        val authorId = post.userId
        if (!followChecked && authorId != null && currentUserId != null && authorId != currentUserId) {
            com.example.data.repository.ProfilesRepository().isFollowing(currentUserId, authorId)
                .onSuccess { isFollowingAuthor = it }
            followChecked = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161618))
    ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Resolve Avatar
                val rawAvatar = identityState?.avatarUrl ?: post.profile?.avatarUrl
                val resolvedAvatar = remember(rawAvatar) {
                    com.example.data.repository.CdnManager.resolveAvatarUrl(rawAvatar)
                }
                
                Box(
                    modifier = Modifier.clickable { onProfileClick() }
                ) {
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
                            Text(
                                text = if (isFollowingAuthor) "  ·  Siguiendo" else "  ·  Seguir",
                                color = if (isFollowingAuthor) Color.Gray else Color(0xFF45B6FF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.clickable {
                                    val uid = post.userId ?: return@clickable
                                    val me = currentUserId ?: return@clickable
                                    val next = !isFollowingAuthor
                                    isFollowingAuthor = next
                                    scope.launch {
                                        val repo = com.example.data.repository.ProfilesRepository()
                                        val result = if (next) repo.followUser(me, uid) else repo.unfollowUser(me, uid)
                                        result.onFailure { isFollowingAuthor = !next }
                                    }
                                }
                            )
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

            // Extract content and metadata
            val youtubeVideoId = remember(post.content) {
                if (!post.content.isNullOrBlank()) {
                    com.example.util.YouTubeUrlParser.extractYouTubeVideoId(post.content)
                } else null
            }
            val metadata = remember(post.content) { parseStateMetadata(post.content) }
            val cleanCaption = metadata.baseCaption

            // 1. Text Content (Before Media)
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
                        Text(
                            text = "Ver más",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable { isExpandedText = true }
                        )
                    }
                }
            } else if (!youtubeVideoId.isNullOrBlank()) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    YouTubePostCard(
                        videoId = youtubeVideoId,
                        originalText = cleanCaption
                    )
                }
            }

            // 2. Media Content
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
                        val resolvedUrl = remember(url) {
                            com.example.data.repository.CdnManager.resolveMediaUrlSync(url)
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { onMediaClick(mediaImagesAndVideos, page, voiceAudioUrl) }
                        ) {
                            if (post.type == "VIDEO" || post.type == "REEL" || isVideoUrl(resolvedUrl)) {
                                val videoUri = remember(resolvedUrl) { Uri.parse(resolvedUrl) }
                                SimpleVideoPreviewPlayer(
                                    videoUri = videoUri,
                                    isMuted = isMuted,
                                    modifier = Modifier.fillMaxSize()
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
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            RenderOverlays(metadata.overlaysBase64)
                        }
                    }

                    // Video Controls
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

                    // Pager Indicators
                    if (mediaImagesAndVideos.size > 1) {
                        Text(
                            text = "${pagerState.currentPage + 1}/${mediaImagesAndVideos.size}",
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(mediaImagesAndVideos.size) { iteration ->
                                val color = if (pagerState.currentPage == iteration) Color.White else Color.White.copy(alpha = 0.4f)
                                val width = if (pagerState.currentPage == iteration) 18.dp else 6.dp
                                Box(
                                    modifier = Modifier
                                        .padding(3.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .height(6.dp)
                                        .width(width)
                                )
                            }
                        }
                    }

                    // Audio Badge
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
                val resolvedAudio = remember(voiceAudioUrl) {
                    com.example.data.repository.CdnManager.resolveMediaUrlSync(voiceAudioUrl ?: "")
                }
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                    PlaylistAudioPlayer(audioUrls = listOf(resolvedAudio))
                }
            } else if (post.type == "TEXT" && mediaImagesAndVideos.isEmpty() && youtubeVideoId.isNullOrBlank()) {
                // If it's just short text and was not expanded, we might want to make it look like a quote card
                if (cleanCaption.isNotBlank() && cleanCaption.length < 150) {
                     // The text is already shown in the Header section (1. Text Content).
                     // We don't need to do anything here unless we want to remove it from there and show it here instead.
                     // Since the prompt says "NO obligar a generar una imagen artificial. crear una presentación visual tipo quote/card."
                     // actually, the standard text display above is enough for Twitter-like text.
                     // Just add some bottom padding.
                     Spacer(modifier = Modifier.height(4.dp))
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
                        val resolvedDocUrl = remember(docUrl) {
                            com.example.data.repository.CdnManager.resolveMediaUrlSync(docUrl)
                        }
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

            // Counters row (Facebook-style)
            val isLiked = post.isLikedByMe
            if (post.likesCount > 0 || post.commentsCount > 0 || post.sharesCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (post.likesCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(Color(0xFF45B6FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ThumbUp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "${post.likesCount}",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    val tail = buildString {
                        val parts = mutableListOf<String>()
                        if (post.commentsCount > 0) parts.add(if (post.commentsCount == 1) "1 comentario" else "${post.commentsCount} comentarios")
                        if (post.sharesCount > 0) parts.add(if (post.sharesCount == 1) "1 vez compartido" else "${post.sharesCount} veces compartido")
                        append(parts.joinToString("  ·  "))
                    }
                    if (tail.isNotEmpty()) {
                        Text(
                            text = tail,
                            color = Color.Gray,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { onCommentClick() }
                        )
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 12.dp))
            }

            // Action Bar (Facebook-style labeled buttons)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onLikeClick() }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                        tint = if (isLiked) Color(0xFF45B6FF) else Color.Gray,
                        contentDescription = "Me gusta",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Me gusta",
                        color = if (isLiked) Color(0xFF45B6FF) else Color.Gray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

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

                if (post.type == "AUDIO" || voiceAudioUrl != null) {
                    IconButton(onClick = { onAudioPlaylistClick(post) }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.PlaylistPlay,
                            tint = Color(0xFF00FF85),
                            contentDescription = "Reproducir lista",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        isSaved = !isSaved
                        val msg = if (isSaved) "Guardado" else "Eliminado de guardados"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.BookmarkBorder else Icons.Outlined.BookmarkBorder,
                        tint = if (isSaved) Color(0xFF45B6FF) else Color.Gray,
                        contentDescription = "Guardar",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
    }
}
