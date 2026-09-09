package com.example.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ui.components.rememberResolvedMediaUrl
import com.example.ui.components.isVideoUrl
import com.example.ui.screen.FeedFullscreenVideoPlayer
import com.example.ui.viewmodel.FeedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: String,
    viewModel: FeedViewModel,
    onBackClick: () -> Unit,
    onMediaClick: (List<String>, Int, String?, Long) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val postState by viewModel.selectedPostDetail.collectAsStateWithLifecycle()
    val isLoading by viewModel.selectedPostLoading.collectAsStateWithLifecycle()
    val commentsMap by viewModel.postComments.collectAsStateWithLifecycle()
    val comments = commentsMap[postId] ?: emptyList()
    
    var commentText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    
    // Fullscreen media viewer state
    var fullScreenMediaList by remember { mutableStateOf<List<String>?>(null) }
    var fullScreenInitialPage by remember { mutableIntStateOf(0) }
    var fullScreenStartPosition by remember { mutableLongStateOf(0L) }
    var fullScreenBackgroundAudio by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(postId) {
        viewModel.getPostDetail(postId)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF0E1621), // Telegram Deep Chat Dark
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Publicación",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF17212B)
                )
            )
        },
        bottomBar = {
            if (postState != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding(),
                    color = Color(0xFF17212B)
                ) {
                    Column {
                        // Quick Emoji Selector Bar
                        AnimatedVisibility(visible = showEmojiPicker) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E222B))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val emojis = listOf("❤️", "🔥", "😂", "🥰", "👏", "😮", "🙏", "🇻🇪")
                                emojis.forEach { emoji ->
                                    Text(
                                        text = emoji,
                                        fontSize = 22.sp,
                                        modifier = Modifier
                                            .clickable {
                                                commentText += emoji
                                            }
                                            .padding(4.dp)
                                    )
                                }
                            }
                        }

                        // Bottom Input Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = commentText,
                                onValueChange = { commentText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    Text("Escribe un comentario...", color = Color.Gray, fontSize = 14.sp)
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF1E222B),
                                    unfocusedContainerColor = Color(0xFF1E222B),
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(24.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                                maxLines = 4
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = { showEmojiPicker = !showEmojiPicker },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text("😃", fontSize = 20.sp)
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = {
                                    if (commentText.isNotBlank() && !isSending) {
                                        isSending = true
                                        viewModel.addComment(postId, commentText) {
                                            commentText = ""
                                            isSending = false
                                            showEmojiPicker = false
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (commentText.isNotBlank()) Color(0xFF00E5FF) else Color.Transparent),
                                enabled = commentText.isNotBlank() && !isSending
                            ) {
                                if (isSending) {
                                    CircularProgressIndicator(
                                        color = Color.Black,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "Enviar",
                                        tint = if (commentText.isNotBlank()) Color.Black else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFF2AABEE))
            } else if (postState == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "La publicación no existe o fue eliminada.",
                        color = Color.Gray,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onBackClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2AABEE))
                    ) {
                        Text("Regresar", color = Color.White)
                    }
                }
            } else {
                val post = postState!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        com.example.ui.components.FeedPostCard(
                            post = post,
                            onLikeClick = { viewModel.toggleLike(post) },
                            onShareClick = { viewModel.sharePost(post) },
                            onCommentClick = {
                                // Already on detail screen
                            },
                            onDeleteClick = {
                                viewModel.deletePost(post.id!!)
                                onBackClick()
                            },
                            onEditClick = { content ->
                                viewModel.updatePost(post.id!!, content)
                            },
                             onMediaClick = { list, page, audio, position ->
                                 fullScreenMediaList = list
                                 fullScreenInitialPage = page
                                 fullScreenBackgroundAudio = audio
                                 fullScreenStartPosition = position
                             }
                        )
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "Comentarios (${comments.size})",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        }
                    }

                    if (comments.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Aún no hay comentarios. ¡Sé el primero! 💬",
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        itemsIndexed(
                            comments,
                            key = { index, comment -> "${comment.id ?: comment.hashCode()}_$index" }
                        ) { _, comment ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                TikTokCommentRow(
                                    comment = comment,
                                    onReplyClick = { authorName ->
                                        commentText = "@$authorName "
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Fullscreen media viewer (similar to InicioTabContent)
    if (fullScreenMediaList != null) {
        val mediaList = fullScreenMediaList!!
        val pagerState = rememberPagerState(
            initialPage = fullScreenInitialPage,
            pageCount = { mediaList.size }
        )

        // Background audio player for photos with audio
        var backgroundAudioPlayer by remember { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }
        var backgroundAudioMuted by remember { mutableStateOf(false) }
        
        LaunchedEffect(fullScreenBackgroundAudio, pagerState.currentPage, fullScreenMediaList) {
            val audioUrl = fullScreenBackgroundAudio
            val currentMediaUrl = mediaList.getOrNull(pagerState.currentPage)
            val currentIsVideo = currentMediaUrl?.let { com.example.ui.components.isVideoUrl(it) } ?: false
            
            // Release previous player if exists - independent players should be released
            backgroundAudioPlayer?.let { player ->
                player.release()
                backgroundAudioPlayer = null
            }
            
            // Create new player for background audio if we have audio and current media is not video
            if (audioUrl != null && audioUrl.isNotBlank() && !currentIsVideo) {
                val player = androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                    setMediaItem(androidx.media3.common.MediaItem.fromUri(audioUrl))
                    repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
                    prepare()
                    playWhenReady = true
                    volume = if (backgroundAudioMuted) 0f else 1f
                }
                backgroundAudioPlayer = player
            }
        }

        // Cleanup background audio player - independent players should be released, not returned to pool
        DisposableEffect(fullScreenBackgroundAudio, pagerState.currentPage, fullScreenMediaList) {
            onDispose {
                backgroundAudioPlayer?.let { player ->
                    // Independent players (created with ExoPlayer.Builder) should be released, not returned to pool
                    player.release()
                    backgroundAudioPlayer = null
                }
            }
        }

        // Pause/resume background audio when page changes to/from video
        LaunchedEffect(pagerState.currentPage, mediaList) {
            val currentMediaUrl = mediaList.getOrNull(pagerState.currentPage)
            val currentIsVideo = currentMediaUrl?.let { com.example.ui.components.isVideoUrl(it) } ?: false
            if (currentIsVideo) {
                backgroundAudioPlayer?.playWhenReady = false
            } else if (fullScreenBackgroundAudio != null && fullScreenBackgroundAudio!!.isNotBlank()) {
                backgroundAudioPlayer?.playWhenReady = !backgroundAudioMuted
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val mediaUrl = mediaList[page]
                val resolvedViewerUrl = rememberResolvedMediaUrl(mediaUrl)
                val isVideo = isVideoUrl(resolvedViewerUrl)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isVideo) {
                        val startPos = if (page == fullScreenInitialPage) fullScreenStartPosition else 0L
                        FeedFullscreenVideoPlayer(
                            videoUrl = resolvedViewerUrl,
                            isActivePage = pagerState.currentPage == page,
                            startPosition = startPos
                        )
                    } else {
                        var photoScale by remember { mutableFloatStateOf(1f) }
                        var photoOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                        AsyncImage(
                            model = resolvedViewerUrl,
                            contentDescription = "Pantalla completa",
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(resolvedViewerUrl) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        photoScale = (photoScale * zoom).coerceIn(1f, 5f)
                                        photoOffset = if (photoScale > 1f) {
                                            androidx.compose.ui.geometry.Offset(photoOffset.x + pan.x, photoOffset.y + pan.y)
                                        } else {
                                            androidx.compose.ui.geometry.Offset.Zero
                                        }
                                    }
                                }
                                .graphicsLayer {
                                    scaleX = photoScale
                                    scaleY = photoScale
                                    translationX = photoOffset.x
                                    translationY = photoOffset.y
                                },
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { 
                        fullScreenMediaList = null
                        fullScreenBackgroundAudio = null
                        fullScreenStartPosition = 0L
                        backgroundAudioPlayer?.let { player ->
                            player.release()
                            backgroundAudioPlayer = null
                        }
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                }

                if (mediaList.size > 1) {
                    Text(
                        text = "${pagerState.currentPage + 1}/${mediaList.size}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // Background audio mute button (only show when background audio is playing)
                if (fullScreenBackgroundAudio != null && backgroundAudioPlayer != null) {
                    IconButton(
                        onClick = { 
                            backgroundAudioMuted = !backgroundAudioMuted
                            backgroundAudioPlayer?.volume = if (backgroundAudioMuted) 0f else 1f
                        },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (backgroundAudioMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                            contentDescription = if (backgroundAudioMuted) "Activar audio" else "Silenciar audio",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        val currentUrl = mediaList[pagerState.currentPage]
                        try {
                            val uri = Uri.parse(currentUrl)
                            val request = android.app.DownloadManager.Request(uri).apply {
                                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                val fileName = currentUrl.substringAfterLast("/")
                                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
                                setTitle("Descargando archivo")
                                setDescription(fileName)
                            }
                            val manager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                            manager.enqueue(request)
                            Toast.makeText(context, "Descarga iniciada... 📥", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = "Descargar",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
