package com.example.feature.chat.ui

import com.example.ui.components.chat.state.*
import com.example.ui.components.chat.voice.voiceGestureDetector
import com.example.ui.components.chat.voice.VoiceGestureEvent
import com.example.util.ChatScrollPositionManager
import com.example.ui.components.chat.bubble.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.flow.distinctUntilChanged

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavHostController
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.request.ImageRequest
import com.example.data.model.Message
import com.example.data.model.Profile
import com.example.feature.chat.ui.attachment.ChatAttachmentSheet
import com.example.feature.chat.ui.background.ChatBackgroundDialog
import com.example.feature.chat.ui.background.ChatPersonalizationStore
import com.example.feature.chat.ui.background.ChatBubblePaletteDialog
import com.example.feature.chat.ui.background.ChatWallpaperSpec
import com.example.feature.chat.ui.call.ActiveCallOverlay
 
import com.example.feature.chat.ui.contact.ChatContactDetailSheet
import com.example.feature.chat.ui.emoji.EmojiAndMediaSheet
 import com.example.feature.chat.ui.message.MessageBubble
 import com.example.feature.chat.ui.message.StickerMessageBubble
 import com.example.feature.chat.ui.message.TypingDotIndicator
import com.example.feature.chat.ui.reply.ChatReplyEditBar
import com.example.feature.chat.ui.composer.ChatComposer
import com.example.feature.chat.ui.topbar.ChatTopBar
import com.example.data.model.StickerResult
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import com.example.ui.theme.bounceClick
import com.example.ui.theme.getAvatarGradient
import com.example.ui.components.VoiceMessageBubble
import com.example.feature.chat.presentation.ChatUiState
import com.example.feature.chat.presentation.ChatViewModel
import com.example.feature.chat.presentation.RecordState
import com.example.ui.screen.triggerLightVibration
import com.example.util.AudioPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    chatId: String,
    otherUserId: String,
    onBack: () -> Unit,
    onNavigateToChatMedia: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onPlaylistAction: (com.example.media.playlist.PlaylistSharePayload, String) -> Unit = { _, _ -> },
    navController: NavHostController? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isOnline by com.example.util.NetworkMonitor.isOnline.collectAsStateWithLifecycle()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val currentUid = viewModel.currentUserId

    // Message Highlight state
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    
    // Restore and Persist Scroll Position
    val savedPosition = remember(chatId) {
        ConversationStateManager.getScrollPosition(context, chatId)
    }
    val lazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedPosition?.first ?: 0,
        initialFirstVisibleItemScrollOffset = savedPosition?.second ?: 0
    )

    // Message Jump Controller
    val jumpController = remember(lazyListState) { MessageJumpController(lazyListState) }

    // Persistent Scroll Logic
    LaunchedEffect(lazyListState, chatId) {
        snapshotFlow { lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { pos ->
                ConversationStateManager.saveScrollPosition(context, chatId, pos.first, pos.second)
            }
    }

    // Smart Scroll Controller
    val messages = (uiState as? ChatUiState.Success)?.messages ?: emptyList()
    val smartScrollController = rememberSmartScrollController(
        lazyListState = lazyListState,
        messages = messages,
        currentUserId = currentUid
    )

    // Cuando el teclado (IME) está visible, la lista queda MÁS baja (imePadding en el
    // Column) pero el scroll no baja solo: las últims burbujas quedan ocultas debajo del
    // teclado. Al abrir el teclado, scrolleamos al último mensaje para que SIEMPRE se
    // vea por encima del teclado mientras se escribe y al enviar un mensaje..
    var imeVisible by androidx.compose.runtime.remember { mutableStateOf(false) }
    val imeDensity = LocalDensity.current
    val imeInsets = WindowInsets.ime
    LaunchedEffect(Unit) {
        snapshotFlow { imeInsets.getBottom(imeDensity) }
            .distinctUntilChanged()
            .collect { imeBottom -> imeVisible = imeBottom >  0 }
    }
    LaunchedEffect(imeVisible, messages.size) {
        if (imeVisible && messages.isNotEmpty()) {
            lazyListState.animateScrollToItem(messages.size -  1)
        }
    }

    // Identify first unread message for divider
    val firstUnreadMessageId by remember(messages) {
        derivedStateOf {
            messages.firstOrNull { it.senderId != currentUid && it.status != "seen" }?.id
        }
    }

    // Scroll To Bottom Logic
    val unreadMessagesCount by remember(messages) {
        derivedStateOf {
            messages.count { it.senderId != currentUid && it.status != "seen" }
        }
    }

    // Message Read Tracker
    MessageReadTracker(
        lazyListState = lazyListState,
        messages = messages,
        onMessagesVisible = { visibleIds ->
            // Mark visible messages as read in background
            viewModel.markMessagesAsRead(visibleIds)
        }
    )

    val inputMessage by viewModel.inputMessage.collectAsStateWithLifecycle()
    val isGhostMode by viewModel.isGhostMode.collectAsStateWithLifecycle()
    val typingUsers = viewModel.typingUsers.collectAsStateWithLifecycle()
    val userPresence = viewModel.userPresence.collectAsStateWithLifecycle()
    val editedMessages = viewModel.editedMessages.collectAsStateWithLifecycle()

    // Pagination trigger when scrolling up
    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex }
            .collect { firstVisibleIndex ->
                if (firstVisibleIndex == 0 && (uiState as? ChatUiState.Success)?.messages?.isNotEmpty() == true) {
                    viewModel.loadMoreMessages(chatId)
                }
            }
    }

    // Debounced typing status broadcast
    var isTypingSent by remember { mutableStateOf(false) }
    LaunchedEffect(inputMessage) {
        if (inputMessage.isNotEmpty()) {
            if (!isTypingSent) {
                viewModel.sendTypingStatus(true)
                isTypingSent = true
            }
            delay(4000)
            viewModel.sendTypingStatus(false)
            isTypingSent = false
        } else {
            if (isTypingSent) {
                viewModel.sendTypingStatus(false)
                isTypingSent = false
            }
        }
    }

    val prefs = remember { context.getSharedPreferences("panalink_prefs", android.content.Context.MODE_PRIVATE) }
    val chatTextSize = remember { prefs.getFloat("chat_text_size_${currentUid}", 16f) }
    // "Enter para enviar" (Ajustes > Chats): activa el botón IME de enviar.
    val enterSendsMessage = remember { prefs.getBoolean("chat_enter_sends_${currentUid}", false) }
    // Personalización reactiva del chat: fondo (wallpaper) + paleta de burbujas.
    val personalization = remember(currentUid, chatId) {
        ChatPersonalizationStore.observePersonalization(context, currentUid, chatId)
    }.collectAsStateWithLifecycle()
    val chatWallpaperState = personalization.value.wallpaperId
    val chatWallpaperCustomUri = personalization.value.wallpaperCustomUri
    val bubblePaletteState = personalization.value.bubblePalette

    val colors = com.example.ui.theme.LocalAppColors.current
    val coroutineScope = rememberCoroutineScope()

    // Handle Jumps from Search
    val targetMessageId = navController?.currentBackStackEntry?.savedStateHandle?.get<String>("targetMessageId")
    LaunchedEffect(targetMessageId, messages) {
        if (targetMessageId != null && messages.isNotEmpty()) {
            navController.currentBackStackEntry?.savedStateHandle?.remove<String>("targetMessageId")
            if (jumpController.jumpToMessage(targetMessageId, messages)) {
                highlightedMessageId = targetMessageId
                delay(2000)
                highlightedMessageId = null
            }
        }
    }
    val haptic = LocalHapticFeedback.current

    // Replying message state & Editing message state preserved in ViewModel
    val replyingToMessage by viewModel.replyingToMessage.collectAsStateWithLifecycle()
    val editingMessage by viewModel.editingMessage.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val isPinned by viewModel.isPinned.collectAsStateWithLifecycle()
    LaunchedEffect(chatId) {
        if (chatId.isNotEmpty()) {
            viewModel.loadChatMuteStatus(chatId, context)
            viewModel.loadChatPinStatus(chatId, context)
        }
    }
    var isLocalSearching by rememberSaveable { mutableStateOf(false) }
    var localSearchQuery by rememberSaveable { mutableStateOf("") }

    val feedViewModel: com.example.ui.viewmodel.FeedViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    // Media states
    var isAttachmentMenuOpen by rememberSaveable { mutableStateOf(false) }
    var isStickerPanelOpen by rememberSaveable { mutableStateOf(false) }
    val inputFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    var isUploading by remember { mutableStateOf(false) }
    var lightboxImageUrl by remember { mutableStateOf<String?>(null) }

    // Progreso real de subida por messageId (desde MediaUploadWorker via WorkManager)
    val uploadProgress by viewModel.uploadProgress.collectAsStateWithLifecycle()
    val stateMessages = (uiState as? com.example.feature.chat.presentation.ChatUiState.Success)?.messages ?: emptyList()
    LaunchedEffect(stateMessages) {
        val pendingIds = stateMessages.map { it.id }
        viewModel.observeUploadProgress(pendingIds)
    }
    
    // Chat Menu & Background states
    var showBackgroundDialog by remember { mutableStateOf(false) }
    var showBubblePaletteDialog by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    val recordState by viewModel.recordState.collectAsStateWithLifecycle()
    val voiceAmplitudes by viewModel.voiceAmplitudes.collectAsStateWithLifecycle()
    val previewPlayerState by viewModel.previewPlayerState.collectAsStateWithLifecycle()
    val previewWaveform by viewModel.previewWaveform.collectAsStateWithLifecycle()
    val isPreviewSending by viewModel.isPreviewSending.collectAsStateWithLifecycle()

    var recordDurationSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(recordState) {
        if (recordState == RecordState.RECORDING || recordState == RecordState.LOCKED_RECORDING) {
            focusManager.clearFocus()
            keyboardController?.hide()

            while (true) {
                recordDurationSeconds = viewModel.getRecordingElapsedSeconds()
                delay(500)
            }
        } else {
            recordDurationSeconds = 0
        }
    }
    
    var isRecordingPaused by remember { mutableStateOf(false) }
    var hasMicPermission by remember { mutableStateOf(false) }
    var micDragOffsetX by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var micDragOffsetY by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    // Animacion del bote de basura al cancelar una nota de voz (tapa que se abre +
    // waveform que se lanza dentro). Corto (~400ms), bloquea hasta terminar.
    var showTrashAnimation by remember { mutableStateOf(false) }

    // Audio player state (single player instance for screen)
    val audioPlayer = remember { AudioPlayer() }
    val audioPlayerState by audioPlayer.playerState.collectAsStateWithLifecycle()
    val playingAudioUrl = audioPlayerState.currentUrl
    val isAudioPlaying = audioPlayerState.isPlaying
    val audioCurrentPositionMs = audioPlayerState.currentPositionMs.toInt()
    val audioDurationMs = audioPlayerState.durationMs.toInt()
    val audioProgress = if (audioPlayerState.durationMs > 0) {
        (audioPlayerState.currentPositionMs.toFloat() / audioPlayerState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // Simulated Call overlay states
    var activeCallState by remember { mutableStateOf<String?>(null) } // "ringing", "active", null
    var callTimerSeconds by remember { mutableStateOf(0) }
    var showContactDetail by remember { mutableStateOf(false) }

    // Multi-select & Forward messages states
    var activeGhostMessage by remember { mutableStateOf<com.example.data.model.Message?>(null) }
    var selectedMessageIds = remember { mutableStateOf(setOf<String>()) }
    var showForwardDialog by remember { mutableStateOf(false) }
    var contactsList by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var forwardingMessage by remember { mutableStateOf<com.example.data.model.Message?>(null) }

    // Repositories for forwarding
    val profilesRepository = remember { com.example.data.repository.ProfilesRepository() }
    val chatsRepository = remember { com.example.data.repository.ChatsRepository() }
    var isBlockedUser by remember(otherUserId) { mutableStateOf(profilesRepository.isUserBlocked(otherUserId)) }
    var blockedAtEpochMs by remember(otherUserId) {
        mutableStateOf(prefs.getLong("blocked_at_$otherUserId", 0L))
    }

    // Compose Performance Engine: Derived state for scroll-to-bottom FAB
    val showScrollToBottom by remember {
        derivedStateOf {
            val total = lazyListState.layoutInfo.totalItemsCount
            if (total == 0) false
            else {
                val lastVisibleIndex = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisibleIndex < total - 5
            }
        }
    }

    // Stable callback references to prevent item recomposition in LazyColumn
    val onReplyCallback = remember(viewModel) { { msg: Message -> viewModel.setReplyingToMessage(msg) } }
    val onDeleteForMeCallback = remember(viewModel) { { id: String -> viewModel.deleteMessageForMe(id) } }
    val onDeleteForEveryoneCallback = remember(viewModel) { { id: String -> viewModel.deleteMessageForEveryone(id) } }
    val onForwardCallback = remember { { msg: Message -> forwardingMessage = msg; showForwardDialog = true } }
    val onImageClickCallback = remember { { url: String -> lightboxImageUrl = url } }
    val onReactCallback = remember(viewModel) { { msgId: String, emoji: String -> viewModel.addReaction(msgId, emoji) } }
    val onEditCallback = remember(viewModel) { { msg: Message -> viewModel.setEditingMessage(msg); viewModel.onInputMessageChange(msg.textContent) } }

    // Request permissions launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "¡Micrófono listo, mantén presionado para hablar!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permiso denegado para las notas de voz", Toast.LENGTH_SHORT).show()
        }
    }

    // Trigger load on entry
    LaunchedEffect(chatId) {
        viewModel.loadChatHistory(chatId, otherUserId)
        hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    // Clear active chat status on disposal (navigation exit) and release audio resources
    DisposableEffect(chatId) {
        onDispose {
            viewModel.sendTypingStatus(false)
            viewModel.clearActiveChat()
            audioPlayer.release()
            try {
                viewModel.onVoiceGestureEvent(
                    event = VoiceGestureEvent.CancelRecording,
                    context = context
                )
            } catch (e: Exception) {}
            viewModel.cancelPreviewRecording()
        }
    }

    // Intercept back button when in PREVIEWING or LOCKED state
    androidx.activity.compose.BackHandler(enabled = recordState == RecordState.PREVIEWING || recordState == RecordState.LOCKED_RECORDING) {
        if (recordState == RecordState.PREVIEWING) {
            viewModel.cancelPreviewRecording()
        } else if (recordState == RecordState.LOCKED_RECORDING) {
            viewModel.onVoiceGestureEvent(
                event = VoiceGestureEvent.CancelRecording,
                context = context
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.cleanOldCache(context)
    }

    // Call timer
    LaunchedEffect(activeCallState) {
        if (activeCallState == "active") {
            callTimerSeconds = 0
            while (activeCallState == "active") {
                delay(1000)
                callTimerSeconds++
            }
        }
    }

    // Active Sound Notification (subtle in-app active chat sound)
    LaunchedEffect(viewModel) {
        viewModel.playNotificationSound.collectLatest {
            com.example.service.NotificationHelper.playActiveChatSound(context)
        }
    }

    // Outgoing swoosh sound feedback when sending message
    LaunchedEffect(viewModel) {
        viewModel.playOutgoingSound.collectLatest {
            com.example.service.NotificationHelper.playOutgoingSound(context)
        }
    }

    // Lazy load contacts for forwarding
    LaunchedEffect(showForwardDialog) {
        if (showForwardDialog) {
            profilesRepository.getMyContacts()
                .onSuccess { contacts ->
                    contactsList = contacts
                }
        }
    }

    if (activeGhostMessage != null) {
        GhostViewerDialog(
            message = activeGhostMessage!!,
            onClose = {
                viewModel.consumeGhostMessage(activeGhostMessage!!.id)
                activeGhostMessage = null
            }
        )
    }

    // Media attachment launchers
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                try {
                    val mime = context.contentResolver.getType(it) ?: "image/jpeg"
                    viewModel.uploadAndSendMedia(
                        uri = it,
                        mimeType = mime,
                        typeLabel = "Image",
                        replyToId = replyingToMessage?.id,
                        context = context,
                        onProgress = { isUploading = it }
                    )
                } catch (e: Exception) {
                    Toast.makeText(context, "No se pudo leer la imagen", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    // Multi-picker: las fotos elegidas juntas se van como UN album grid (WhatsApp).
    val imageMultiPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.uploadAndSendImageAlbum(
                uris = uris,
                replyToId = replyingToMessage?.id,
                context = context,
                onProgress = { isUploading = it }
            )
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                try {
                    isUploading = true
                    val mime = context.contentResolver.getType(it) ?: "video/mp4"
                    
                    viewModel.uploadAndSendMedia(
                        uri = it,
                        mimeType = mime,
                        typeLabel = "Video",
                        replyToId = replyingToMessage?.id,
                        context = context,
                        onProgress = { isUploading = it }
                    )
                } catch (e: Exception) {
                    isUploading = false
                    android.util.Log.e("ChatScreen", "Fallo al procesar o comprimir video", e)
                    Toast.makeText(context, "No se pudo leer o comprimir el video", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                try {
                    val mime = context.contentResolver.getType(it) ?: "audio/mpeg"
                    viewModel.uploadAndSendMedia(
                        uri = it,
                        mimeType = mime,
                        typeLabel = "Audio",
                        replyToId = replyingToMessage?.id,
                        context = context,
                        onProgress = { isUploading = it }
                    )
                } catch (e: Exception) {
                    Toast.makeText(context, "No se pudo leer el audio", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                try {
                    val mime = context.contentResolver.getType(it) ?: "application/pdf"
                    var docFileName: String? = null
                    try {
                        context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (nameIndex != -1) {
                                    docFileName = cursor.getString(nameIndex)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // ignore query failure fallback
                    }
                    if (docFileName.isNullOrEmpty()) {
                        docFileName = it.lastPathSegment?.substringAfterLast("/")
                    }

                    viewModel.uploadAndSendMedia(
                        uri = it,
                        mimeType = mime,
                        typeLabel = "Document",
                        replyToId = replyingToMessage?.id,
                        context = context,
                        fileName = docFileName,
                        onProgress = { isUploading = it }
                    )
                } catch (e: Exception) {
                    Toast.makeText(context, "No se pudo leer el documento", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            coroutineScope.launch {
                try {
                    val tempFile = java.io.File(context.cacheDir, "camera_temp_${java.util.UUID.randomUUID()}.jpg")
                    java.io.FileOutputStream(tempFile).use { out ->
                        it.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    viewModel.uploadAndSendMedia(
                        file = tempFile,
                        mimeType = "image/jpeg",
                        typeLabel = "Image",
                        replyToId = replyingToMessage?.id,
                        context = context,
                        onProgress = { isUploading = it }
                    )
                } catch (e: Exception) {
                    Toast.makeText(context, "Error procesando foto", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val cameraPermissionState = com.example.util.rememberCameraPermissionState(
        onPermissionsGranted = {
            cameraLauncher.launch(null)
        }
    )

    // Helper functions for selection
    fun toggleSelection(msgId: String) {
        val currentSet = selectedMessageIds.value
        if (currentSet.contains(msgId)) {
            selectedMessageIds.value = currentSet - msgId
        } else {
            selectedMessageIds.value = currentSet + msgId
        }
    }

    com.example.util.CameraPermissionDialog(
        showExplanation = cameraPermissionState.showExplanationDialog,
        isPermanentlyDenied = cameraPermissionState.isPermanentlyDenied,
        onDismiss = cameraPermissionState.dismissDialog,
        onRequestPermission = { cameraPermissionState.requestPermissions() },
        onOpenSettings = cameraPermissionState.openSettings
    )

    val myPlaylists by viewModel.myPlaylists.collectAsStateWithLifecycle()
    LaunchedEffect(showPlaylistPicker) {
        if (showPlaylistPicker) {
            viewModel.loadMyPlaylists()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color(0xFF070B18)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .navigationBarsPadding()
                .imePadding()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0E1730),
                            Color(0xFF070B18)
                        )
                    )
                )
        ) {
            // Topbar DENTRO del content: así el fondo de chat corre TAMBIÉN debajo
            // de la barra del perfil (referencia).
            val stateTop = uiState as? ChatUiState.Success
            val otherUserTop = stateTop?.otherUser
            ChatTopBar(
                otherUser = otherUserTop,
                isMuted = isMuted,
                isPinned = isPinned,
                isLocalSearching = isLocalSearching,
                localSearchQuery = localSearchQuery,
                typingUsers = typingUsers.value,
                userPresence = userPresence.value,
                isBlockedUser = isBlockedUser,
                onBack = onBack,
                onVideoCall = {
                    if (otherUserTop != null) {
                        com.example.call.CallPermissionGate.startCallIfPermitted(
                            activity = null,
                            context = context,
                            targetUserId = otherUserTop.id,
                            targetUserName = otherUserTop.displayName,
                            type = com.example.call.CallType.VIDEO
                        )
                    }
                },
                onAudioCall = {
                    if (otherUserTop != null) {
                        com.example.call.CallPermissionGate.startCallIfPermitted(
                            activity = null,
                            context = context,
                            targetUserId = otherUserTop.id,
                            targetUserName = otherUserTop.displayName,
                            type = com.example.call.CallType.AUDIO
                        )
                    }
                },
                onStartSearch = { isLocalSearching = true },
                onStopSearch = {
                    isLocalSearching = false
                    localSearchQuery = ""
                },
                onSearchQueryChange = { localSearchQuery = it },
                onShowContactDetail = { showContactDetail = true },
                onShowBackgroundDialog = { showBackgroundDialog = true },
                onShowBubblePaletteDialog = { showBubblePaletteDialog = true },
                onToggleMute = {
                    val newMutedState = !isMuted
                    viewModel.muteChat(chatId, newMutedState)
                    Toast.makeText(
                        context,
                        if (newMutedState) "Notificaciones silenciadas 🔇" else "Notificaciones activadas 🔔",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onTogglePin = {
                    val newPinnedState = !isPinned
                    viewModel.pinChat(chatId, newPinnedState)
                    Toast.makeText(
                        context,
                        if (newPinnedState) "Chat anclado 📌" else "Chat desanclado 📌",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onClearChat = {
                    viewModel.clearChat()
                    Toast.makeText(context, "Chat vaciado", Toast.LENGTH_SHORT).show()
                },
                onDeleteChat = {
                    viewModel.deleteChat { onBack() }
                    Toast.makeText(context, "Chat eliminado", Toast.LENGTH_SHORT).show()
                },
                onToggleBlockUser = {
                    if (otherUserId.isNotEmpty()) {
                        val currentlyBlocked = isBlockedUser
                        coroutineScope.launch {
                            if (currentlyBlocked) {
                                profilesRepository.unblockUser(otherUserId)
                                isBlockedUser = false
                                prefs.edit().remove("blocked_at_$otherUserId").apply()
                                blockedAtEpochMs = 0L
                                Toast.makeText(context, "Contacto desbloqueado ✅", Toast.LENGTH_SHORT).show()
                            } else {
                                profilesRepository.blockUser(otherUserId)
                                isBlockedUser = true
                                val nowMs = System.currentTimeMillis()
                                prefs.edit().putLong("blocked_at_$otherUserId", nowMs).apply()
                                blockedAtEpochMs = nowMs
                                Toast.makeText(context, "Contacto bloqueado 🚫", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onNavigateToChatMedia = onNavigateToChatMedia,
                onNavigateToSearch = onNavigateToSearch,
            )
            // Pinned Message Bar
            val state = uiState as? ChatUiState.Success
            

            
             // Message Area
             val wallpaperActiveSpec = ChatWallpaperSpec.fromId(chatWallpaperState, chatWallpaperCustomUri)
             com.example.feature.chat.ui.background.ChatWallpaperBackground(
                 spec = wallpaperActiveSpec,
                 modifier = Modifier.weight(1f).fillMaxWidth()
             ) {
                when (uiState) {
                    is ChatUiState.Loading -> {
                        CircularProgressIndicator(
                            color = colors.primary,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    is ChatUiState.Success -> {
                        val state = uiState as ChatUiState.Success
                        val rawMessages = if (localSearchQuery.isEmpty()) {
                            state.messages
                        } else {
                            state.messages.filter { it.textContent.contains(localSearchQuery, ignoreCase = true) }
                        }
                        val filteredMessages = rawMessages
                            .filter { message ->
                                // Contacto bloqueado: se oculta solo lo llegado DESPUES del bloqueo.
                                // El historial anterior permanece visible (comportamiento tipo WhatsApp).
                                if (isBlockedUser && blockedAtEpochMs > 0L && message.senderId != currentUid) {
                                    val msgTs = com.example.util.TimeUtils.parseToEpochMilli(message.createdAt)
                                    msgTs <= 0L || msgTs > blockedAtEpochMs
                                } else {
                                    true
                                }
                            }
                            .distinctBy { message ->
                                if (!message.clientMessageUuid.isNullOrBlank()) message.clientMessageUuid else message.id
                            }
                        if (filteredMessages.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = if (localSearchQuery.isNotEmpty()) "No se encontraron mensajes de pana 🔍" else "Escribe un mensaje para empezar de pana! 🇻🇪",
                                    color = Color(0xFF90A4AE),
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                
                                itemsIndexed(
                                    filteredMessages,
                                    key = { _, message ->
                                        message.id.takeIf { it.isNotBlank() && !it.startsWith("temp_") }
                                            ?: message.clientMessageUuid.takeIf { it.isNotBlank() && !it.startsWith("temp_") }
                                            ?: "message_${message.createdAt}_${message.senderId}_${message.content.hashCode()}"
                                    },
                                    contentType = { _, message -> message.messageType ?: if (message.textContent.startsWith("[Sticker] ")) "sticker" else "text" }
                                ) { index, message ->
                                    val isMe = message.senderId == currentUid

                                    // Custom Date Separator
                                    val showDateSeparator = if (index == 0) {
                                        true
                                    } else {
                                        val prevMessage = filteredMessages[index - 1]
                                        val currDate = message.createdAt.take(10)
                                        val prevDate = prevMessage.createdAt.take(10)
                                        currDate != prevDate
                                    }

                                    if (showDateSeparator) {
                                        val dateText = try {
                                            val formatInput = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                            val parsedDate = formatInput.parse(message.createdAt.take(10)) ?: java.util.Date()
                                            val today = java.util.Date()
                                            val formatOutput = java.text.SimpleDateFormat("d 'de' MMMM, yyyy", java.util.Locale("es", "VE"))
                                            
                                            val sdfDiff = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                                            val todayStr = sdfDiff.format(today)
                                            val dateStr = sdfDiff.format(parsedDate)
                                            
                                            val calendar = java.util.Calendar.getInstance()
                                            calendar.time = today
                                            calendar.add(java.util.Calendar.DATE, -1)
                                            val yesterdayStr = sdfDiff.format(calendar.time)

                                            when (dateStr) {
                                                todayStr -> "Hoy"
                                                yesterdayStr -> "Ayer"
                                                else -> formatOutput.format(parsedDate)
                                            }
                                        } catch (e: Exception) {
                                            "Chat"
                                        }

                                            Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.7f)),
                                                shape = RoundedCornerShape(10.dp),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                            ) {
                                                Text(
                                                    text = dateText,
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }

                                    // New Messages Divider
                                    if (message.id == firstUnreadMessageId) {
                                        NewMessagesDivider()
                                    }

                                    val prevMsg = filteredMessages.getOrNull(index - 1)
                                    val nextMsg = filteredMessages.getOrNull(index + 1)
                                    val groupPosition = calculateMessageGroupPosition(message, prevMsg, nextMsg)

                                    MessageBubbleEngine(
                                        message = message,
                                        isMe = isMe,
                                        groupPosition = groupPosition,
                                        deliveryState = com.example.util.MessageStatusResolver.resolveMessageDeliveryState(message, isOnline),
                                        myAvatarUrl = viewModel.currentAvatarUrl,
                                        otherAvatarUrl = state.otherUser?.avatarUrl,
                                        otherUserName = state.otherUser?.displayName,
                                        textSizeSp = chatTextSize,
                                        outgoingBubbleColors = bubblePaletteState.colors,
                                        allMessages = state.messages,
                                        onReply = onReplyCallback,
                                        onDeleteForMe = onDeleteForMeCallback,
                                        onDeleteForEveryone = onDeleteForEveryoneCallback,
                                        onForward = onForwardCallback,
                                        isSelected = selectedMessageIds.value.contains(message.id),
                                        onSelect = { toggleSelection(message.id) },
                                        onImageClick = onImageClickCallback,
                                        playingAudioUrl = playingAudioUrl,
                                        isAudioPlaying = isAudioPlaying,
                                        audioProgress = audioProgress,
                                        audioDurationMs = audioDurationMs,
                                        audioCurrentPositionMs = audioCurrentPositionMs,
                                        audioPlayer = audioPlayer,
                                        onAudioPlayStateChange = { _, _ -> },
                                        reactions = message.reactions,
                                        onReact = { emoji -> onReactCallback(message.id, emoji) },
                                        onToggleFavorite = { viewModel.toggleFavorite(it) },
        onSaveSticker = { url -> viewModel.saveSticker(url, context) },
        onToggleStickerFavorite = { url -> viewModel.toggleStickerFavorite(url, context) },
                                        isEdited = message.isEdited || editedMessages.value.containsKey(message.id),
                                        onEdit = onEditCallback,
                                        isHighlighted = message.id == highlightedMessageId,
                                        onGhostOpen = { activeGhostMessage = it },
                                        onPlaylistAction = onPlaylistAction,
                                        onRetry = { msgId ->
                                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                                com.example.data.repository.MessagesRepository.getInstance().retryMessage(msgId)
                                            }
                                        },
                                        uploadProgress = uploadProgress
                                    )
                                }

                                // Typing indicator in-chat list entry
                                val otherUser = state.otherUser
                                val isOtherUserTyping = typingUsers.value.contains(otherUser?.id ?: "other_user_id_demo")
                                if (isOtherUserTyping) {
                                    item(contentType = "typing_indicator") {
                                            Row(
                                            modifier = Modifier
                                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                                .background(Color(0xFF1E293B).copy(alpha = 0.7f), RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "${otherUser?.displayName ?: "Tu pana"} está escribiendo...",
                                                color = Color(0xFF38BDF8),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            TypingDotIndicator()
                                        }
                                    }
                                }
                            }

                            // Scroll To Latest Button (Phase 3.2-B)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(bottom = 12.dp, end = 16.dp)
                            ) {
                                ScrollToLatestButton(
                                    visible = showScrollToBottom,
                                    unreadCount = unreadMessagesCount,
                                    onClick = {
                                        coroutineScope.launch {
                                            smartScrollController.scrollToBottom(messages.size)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    is ChatUiState.Error -> {
                        Text(
                            text = (uiState as ChatUiState.Error).message,
                            color = Color.Red,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp)
                        )
                    }
                }

                // Uploading transparent cover indicator removed per user request
            }

            ChatReplyEditBar(
                replyingToMessage = replyingToMessage,
                editingMessage = editingMessage,
                currentUid = currentUid,
                otherUserDisplayName = (uiState as? ChatUiState.Success)?.otherUser?.displayName ?: "",
                onCancelReply = { viewModel.setReplyingToMessage(null) },
                onCancelEdit = {
                    viewModel.setEditingMessage(null)
                    viewModel.onInputMessageChange("")
                }
            )

            ChatAttachmentSheet(
                visible = isAttachmentMenuOpen,
                isGhostMode = isGhostMode,
                onCamera = {
                    isAttachmentMenuOpen = false
                    cameraPermissionState.requestPermissions()
                },
                onImage = {
                    isAttachmentMenuOpen = false
                    imageMultiPickerLauncher.launch("image/*")
                },
                onVideo = {
                    isAttachmentMenuOpen = false
                    videoPickerLauncher.launch("video/*")
                },
                onDocument = {
                    isAttachmentMenuOpen = false
                    documentPickerLauncher.launch(arrayOf("*/*"))
                },
                onAudio = {
                    isAttachmentMenuOpen = false
                    audioPickerLauncher.launch("audio/*")
                },
                onPlaylist = {
                    isAttachmentMenuOpen = false
                    showPlaylistPicker = true
                },
                onGif = {
                    isAttachmentMenuOpen = false
                    isStickerPanelOpen = true
                },
                onSticker = {
                    isAttachmentMenuOpen = false
                    isStickerPanelOpen = true
                },
                onToggleGhostMode = {
                    isAttachmentMenuOpen = false
                    viewModel.toggleGhostMode()
                }
            )
            // Advanced Message Composer (extraído a ChatComposer.kt)
            ChatComposer(
                viewModel = viewModel,
                inputMessage = inputMessage,
                replyingToMessage = replyingToMessage,
                editingMessage = editingMessage,
                isGhostMode = isGhostMode,
                enterSendsMessage = enterSendsMessage,
                hasMicPermission = hasMicPermission,
                isStickerPanelOpen = isStickerPanelOpen,
                isAttachmentMenuOpen = isAttachmentMenuOpen,
                cameraPermissionState = cameraPermissionState,
                micPermissionLauncher = micPermissionLauncher,
                onToggleStickerPanel = { isStickerPanelOpen = !isStickerPanelOpen; isAttachmentMenuOpen = false },
                onToggleAttachmentMenu = { isAttachmentMenuOpen = !isAttachmentMenuOpen;isStickerPanelOpen = false },
                onVoiceGestureEvent = { event, context, replyToId, fallbackDurationSeconds ->
                    viewModel.onVoiceGestureEvent(
                        event = event,
                        context = context,
                        replyToId = replyToId,
                        fallbackDurationSeconds = fallbackDurationSeconds ?: 0,
                        onProgress = { isUploading = it }
                    )
                },
                onSendPreviewRecording = { context, replyToId ->
                    viewModel.sendPreviewRecording(
                        context = context,
                        replyToId = replyToId,
                        onProgress = { isUploading = it }
                    )
                },
                onShowTrashAnimation = { showTrashAnimation = true },
                inputFocusRequester = inputFocusRequester,
            )

        // ---- Animacion "bote de basura" al cancelar la nota de voz ----
        if (showTrashAnimation) {
            val progress = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                progress.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
                kotlinx.coroutines.delay(80)
                showTrashAnimation = false
            }
            Box(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .size(72.dp),
                contentAlignment = Alignment.Center
            ) {
                // El waveform se "lanza": se achica y cae hacia el bote
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .graphicsLayer {
                            val s = 1f - progress.value
                            scaleX = s
                            scaleY = s
                            translationY = progress.value * 36f
                            alpha = 1f - progress.value * 0.6f
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color(0xFF8696A0),
                        modifier = Modifier.size(30.dp)
                    )
                }
                // Bote con tapa que se abre (rotacion hacia atras)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.BottomCenter),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminada",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isStickerPanelOpen,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            val emojiMedia by viewModel.emojiMedia.collectAsStateWithLifecycle()
            EmojiAndMediaSheet(
                emojiMedia = emojiMedia,
                isGhostMode = isGhostMode,
                onToggleGhostMode = { viewModel.toggleGhostMode() },
                onEmojiSelected = { emoji ->
                    viewModel.onInputMessageChange(inputMessage + emoji)
                },
                onStickerSelected = { sticker ->
                    viewModel.sendSticker(sticker.url, sticker.preview, replyToId = replyingToMessage?.id)
                    isStickerPanelOpen = false
                    viewModel.clearReplyAndEdit()
                },
                onBackspace = { viewModel.deleteLastChar() },
                onTabSelected = { viewModel.onEmojiTabSelected(it) },
                onSearchActiveChange = { viewModel.setEmojiSearchActive(it) },
                onSearchQueryChange = { viewModel.setEmojiSearchQuery(it) },
                onClose = { isStickerPanelOpen = false }
            )
            LaunchedEffect(isStickerPanelOpen) {
                if (!isStickerPanelOpen) {
                    // Al cerrar el panel de emojis, el campo de texto recupera el foco
                    // para seguir escribiendo sin tocar de nuevo el input.
                    runCatching { inputFocusRequester.requestFocus() }
                } else {
                    focusManager.clearFocus()
                    viewModel.loadEmojiStickers(query = null)
                }
            }
        }
    }

    // FullScreen media viewer (Fotos y Videos)
    if (lightboxImageUrl != null) {
        val url = lightboxImageUrl!!
        val isVideoMedia = remember(url) {
            val lower = url.lowercase()
            lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") || lower.contains("/video/") || lower.contains("video_") || lower.contains(".mov")
        }
        com.example.ui.components.chat.media.FullScreenMediaViewer(
            mediaUrl = url,
            isVideo = isVideoMedia,
            onClose = { lightboxImageUrl = null }
        )
    }

    // Forward dialog selector
    val forwardSource = forwardingMessage
    if (showForwardDialog && forwardSource != null) {
        val isMultimediaForward = forwardSource.mediaUrl?.isNotBlank() == true
        AlertDialog(
            onDismissRequest = { showForwardDialog = false },
            title = { Text("Reenviar mensaje de pana 🇻🇪", color = Color.White) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Selecciona un contacto para reenviar:",
                        color = Color(0xFF8596A0),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    if (contactsList.isEmpty()) {
                        Text("No tienes contactos agregados chamo 🥺", color = Color.Gray, fontSize = 13.sp)
                    } else {
                        LazyColumn(
                            modifier = Modifier.height(250.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(contactsList) { contact ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showForwardDialog = false
                                            val source = forwardSource
                                            val forwardUuid = java.util.UUID.randomUUID().toString()
                                            coroutineScope.launch {
                                                chatsRepository.createDirectChat(contact.id)
                                                    .onSuccess { chat ->
                                                        val messagesRepo = com.example.data.repository.MessagesRepository.getInstance()
                                                        if (isMultimediaForward) {
                                                            messagesRepo.forwardMessage(
                                                                chatId = chat.id,
                                                                context = context,
                                                                source = source,
                                                                receiverId = contact.id
                                                            ).onSuccess {
                                                                Toast.makeText(context, "¡Mensaje reenviado a ${contact.displayName}!", Toast.LENGTH_SHORT).show()
                                                            }.onFailure { e ->
                                                                Toast.makeText(context, "Error al reenviar: ${e.localizedMessage ?: "desconocido"}", Toast.LENGTH_LONG).show()
                                                            }
                                                        } else {
                                                            messagesRepo.sendMessage(
                                                                chat.id,
                                                                source.textContent,
                                                                null,
                                                                contact.id,
                                                                clientMessageUuid = forwardUuid
                                                            )
                                                            Toast.makeText(context, "¡Mensaje reenviado a ${contact.displayName}!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                    .onFailure {
                                                        Toast.makeText(context, "Error al reenviar", Toast.LENGTH_SHORT).show()
                                                    }
                                            }
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    com.example.ui.components.PanaAvatar(
                                        avatarUrl = contact.avatarUrl,
                                        userId = contact.id,
                                        placeholderName = contact.displayName,
                                        size = 36.dp,
                                        borderWidth = 0.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(contact.displayName, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showForwardDialog = false }) {
                    Text("Cancelar", color = Color(0xFF38BDF8))
                }
            },
            containerColor = Color(0xFF1F2C34)
        )
    }

    // Simulated Call Overlay (extraído a ActiveCallOverlay.kt)
    ActiveCallOverlay(
        activeCallState = activeCallState,
        callTimerSeconds = callTimerSeconds,
        otherUser = (uiState as? ChatUiState.Success)?.otherUser,
        onReject = { activeCallState = null },
        onAccept = { activeCallState = "active" },
        onHangUp = { activeCallState = null },
    )

    // Simulated Contact Detail Custom Bottom Sheet (extraído a ChatContactDetailSheet.kt)
    ChatContactDetailSheet(
        visible = showContactDetail,
        otherUser = (uiState as? ChatUiState.Success)?.otherUser,
        onDismiss = { showContactDetail = false },
    )

    if (showPlaylistPicker) {
        PlaylistPickerDialog(
            playlists = myPlaylists,
            onPlaylistSelected = { playlist ->
                showPlaylistPicker = false
                viewModel.sharePlaylist(chatId, playlist)
            },
            onDismiss = { showPlaylistPicker = false }
        )
    }

    // Chat Background Selection Dialog (extraído a ChatBackgroundDialog.kt)
    ChatBackgroundDialog(
        visible = showBackgroundDialog,
        chatWallpaperState = chatWallpaperState,
        wallpaperCustomUri = chatWallpaperCustomUri,
        onDismiss = { showBackgroundDialog = false },
        onSelect = { spec ->
            ChatPersonalizationStore.setWallpaper(context, currentUid, chatId, spec)
            showBackgroundDialog = false
        },
    )

    ChatBubblePaletteDialog(
        visible = showBubblePaletteDialog,
        currentPalette = bubblePaletteState,
        onDismiss = { showBubblePaletteDialog = false },
        onSelect = { palette ->
            ChatPersonalizationStore.setBubblePalette(context, currentUid, chatId, palette)
            showBubblePaletteDialog = false
        },
    )
}
}
        

     
 
 
 

@Composable
fun PlaylistPickerDialog(
    playlists: List<com.example.media.playlist.PlaylistEntity>,
    onPlaylistSelected: (com.example.media.playlist.PlaylistEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Selecciona una Playlist", color = Color.White) },
        text = {
            if (playlists.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text("No tienes playlists creadas", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(playlists) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlaylistSelected(playlist) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF202C33)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (playlist.coverPath != null) {
                                    AsyncImage(
                                        model = playlist.coverPath,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.Gray)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(playlist.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Playlist • Actualizada recientemente", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color(0xFF38BDF8))
            }
        },
        containerColor = Color(0xFF1F2C34),
        shape = RoundedCornerShape(28.dp)
    )
}
