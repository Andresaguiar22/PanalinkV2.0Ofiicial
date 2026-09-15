package com.example.ui.navigation

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.call.CallManager
import com.example.media.player.ui.PlayerViewModel
import com.example.ui.screen.ChatMediaGalleryScreen
import com.example.feature.chat.ui.ChatScreen
import com.example.ui.screen.ChatSearchScreen
import com.example.ui.screen.ChatsListScreen
import com.example.ui.screen.CleanStoryEditorScreen
import com.example.ui.screen.FavoritesScreen
import com.example.ui.screen.NotificationsScreen
import com.example.ui.screen.PostDetailScreen
import com.example.ui.screen.ProfileScreen
import com.example.ui.screen.ReelEditorScreen
import com.example.ui.screen.SearchUsersScreen
import com.example.ui.screen.SplashScreen
import com.example.reels.ui.ReelsFeedScreen
import com.example.ui.screen.UserProfileScreen
import com.example.ui.screen.ViewStateScreen
import com.example.ui.viewmodel.AuthViewModel
import com.example.feature.chat.presentation.ChatViewModel
import com.example.ui.viewmodel.ChatsViewModel
import com.example.ui.viewmodel.NotificationsViewModel
import com.example.ui.viewmodel.ProfileViewModel
import com.example.ui.viewmodel.StatesViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavHost(
    chatsViewModel: ChatsViewModel,
    chatViewModel: ChatViewModel,
    statesViewModel: StatesViewModel,
    profileViewModel: ProfileViewModel,
    notificationsViewModel: NotificationsViewModel,
    playerViewModel: PlayerViewModel,
    authViewModel: AuthViewModel,
    currentIntentState: State<Intent?>,
    callManager: CallManager,
    isPlayerFullVisible: Boolean,
    onPlayerFullClose: () -> Unit,
) {
            val mainNavController: NavHostController = rememberNavController()

            val context = androidx.compose.ui.platform.LocalContext.current
            val intentToProcess = currentIntentState.value
            LaunchedEffect(intentToProcess) {
                intentToProcess?.let { intent ->
                    val chatId = intent.getStringExtra("thread_id")
                        ?: intent.getStringExtra("threadId")
                        ?: intent.getStringExtra("p_thread_id")
                        ?: intent.getStringExtra("chat_id")
                        ?: intent.getStringExtra("chatId")
                        ?: intent.getStringExtra("p_chat_id")
                    val stateId = intent.getStringExtra("state_id") ?: intent.getStringExtra("stateId")
                    val type = intent.getStringExtra("notification_type") ?: intent.getStringExtra("notificationType")

                    android.util.Log.d("MainActivity", "Deep Link Intent received - chatId: $chatId, stateId: $stateId, type: $type")

                    if (type == "llamada_entrante") {
                        val callerId = intent.getStringExtra("callerId") ?: ""
                        val callerName = intent.getStringExtra("callerName") ?: ""
                        val callTypeStr = intent.getStringExtra("callType") ?: "audio"
                        val sdpStr = intent.getStringExtra("sdp") ?: "" // If available in FCM
                        intent.removeExtra("notification_type")
                        intent.removeExtra("notificationType")

                        // Handle incoming call if CallManager is IDLE
                        if (callManager.callState.value == com.example.call.CallState.IDLE) {
                            callManager.handleFCMIncomingCall(callerId, callerName, callTypeStr)
                        }
                    } else if (!chatId.isNullOrEmpty()) {
                        intent.removeExtra("thread_id")
                        intent.removeExtra("threadId")
                        intent.removeExtra("p_thread_id")
                        intent.removeExtra("chat_id")
                        intent.removeExtra("chatId")
                        intent.removeExtra("p_chat_id")
                        val rawOtherUserId = intent.getStringExtra("otherUserId")
                            ?: intent.getStringExtra("sender_id")
                            ?: intent.getStringExtra("senderId")
                            ?: intent.getStringExtra("p_sender_id")
                        intent.removeExtra("otherUserId")
                        intent.removeExtra("sender_id")
                        intent.removeExtra("senderId")
                        intent.removeExtra("p_sender_id")

                        // Resolver el id CANÓNICO local del hilo para navegación:
                        // 1. Buscar primero chatDao.getChatByThreadId(id) -> usar entity.threadId
                        // 2. Si no, chatDao.getChatById(id) -> usar entity.threadId ?: entity.id
                        // 3. Validar sender_id contra otherUserId para evitar contradicciones
                        // 4. Si no está en Room, resolver con Supabase (one_to_one_threads)
                        val db2 = com.example.data.database.PanalinkDatabase.getDatabase(context)
                        val resolvedPair = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            var canonicalThreadId: String? = null
                            var resolvedOtherUserId = rawOtherUserId?.takeIf { it.isNotBlank() && it != "unknown" }
                            val currentUid = com.example.data.supabase.SupabaseClient.currentUser?.id

                            val byThread = db2.chatDao().getChatByThreadId(chatId)
                            if (byThread != null) {
                                canonicalThreadId = byThread.threadId ?: byThread.id
                                if (byThread.type == "dm") {
                                    val roomOther = byThread.otherUserId
                                    if (!roomOther.isNullOrBlank()) {
                                        if (resolvedOtherUserId != null && resolvedOtherUserId != roomOther) {
                                            android.util.Log.e("MainActivity", "Contradicción de sender: notif sender=$resolvedOtherUserId != room otherUser=$roomOther. Abortando.")
                                            return@withContext null
                                        }
                                        resolvedOtherUserId = roomOther
                                    }
                                }
                            } else {
                                val byId = db2.chatDao().getChatById(chatId)
                                if (byId != null) {
                                    canonicalThreadId = byId.threadId ?: byId.id
                                    if (byId.type == "dm") {
                                        val roomOther = byId.otherUserId
                                        if (!roomOther.isNullOrBlank()) {
                                            if (resolvedOtherUserId != null && resolvedOtherUserId != roomOther) {
                                                android.util.Log.e("MainActivity", "Contradicción de sender: notif sender=$resolvedOtherUserId != room otherUser=$roomOther. Abortando.")
                                                return@withContext null
                                            }
                                            resolvedOtherUserId = roomOther
                                        }
                                    }
                                }
                            }

                            // Si no se pudo resolver localmente, intentar resolverlo contra Supabase
                            if (canonicalThreadId == null || !com.example.data.repository.MessagesRepository.isValidUuid(canonicalThreadId)) {
                                try {
                                    val identity = com.example.data.repository.MessagesRepository.getInstance().resolveChatIdentity(
                                        chatId = chatId,
                                        receiverHint = resolvedOtherUserId
                                    )
                                    if (identity.kind == com.example.data.repository.MessagesRepository.ChatKind.DM && !identity.threadId.isNullOrEmpty()) {
                                        canonicalThreadId = identity.threadId
                                        if (!identity.receiverId.isNullOrEmpty()) {
                                            if (resolvedOtherUserId != null && resolvedOtherUserId != identity.receiverId) {
                                                android.util.Log.e("MainActivity", "Contradicción remota: sender=$resolvedOtherUserId != identity.receiver=${identity.receiverId}. Abortando.")
                                                return@withContext null
                                            }
                                            resolvedOtherUserId = identity.receiverId
                                        }
                                    } else if (identity.kind == com.example.data.repository.MessagesRepository.ChatKind.CHANNEL || identity.kind == com.example.data.repository.MessagesRepository.ChatKind.LEGACY) {
                                        canonicalThreadId = identity.chatId
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("MainActivity", "Error al resolver identidad en Supabase para $chatId", e)
                                }
                            }

                            if (!canonicalThreadId.isNullOrEmpty()) {
                                val finalOther = resolvedOtherUserId
                                    ?: db2.chatDao().getChatById(canonicalThreadId)?.otherUserId
                                    ?: db2.messageDao().getMessagesForChat(canonicalThreadId)?.firstOrNull { it.senderId != currentUid && !it.senderId.isNullOrBlank() }?.senderId
                                    ?: "unknown"
                                Pair(canonicalThreadId, finalOther)
                            } else {
                                null
                            }
                        }

                        if (resolvedPair != null) {
                            val (targetThreadId, targetOtherUserId) = resolvedPair
                            delay(300)
                            mainNavController.navigate("chat/$targetThreadId/$targetOtherUserId") { launchSingleTop = true }
                        }
                    } else if (!stateId.isNullOrEmpty()) {
                        intent.removeExtra("state_id")
                        intent.removeExtra("stateId")
                        delay(300)
                        if (type == "new_story") {
                            mainNavController.navigate("viewState/$stateId") { launchSingleTop = true }
                        } else if (type == "new_reel") {
                            mainNavController.navigate("tiktok/$stateId") { launchSingleTop = true }
                        } else if (type == "new_post" || type == "new_like" || type == "new_comment") {
                            mainNavController.navigate("postDetail/$stateId") { launchSingleTop = true }
                        }
                    } else if (type == "system_news" || type == "app_update" || type == "new_content") {
                        intent.removeExtra("notification_type")
                        intent.removeExtra("notificationType")
                        delay(300)
                        mainNavController.navigate("notifications") { launchSingleTop = true }
                    }
                }
            }
            // Invitaciones de Co-Host en tiempo real: si el host invita a este usuario
            // (fila PENDING en live_guests para su id) se navega automáticamente a
            // LiveGuestScreen para aceptar/rechazar, igual que una llamada entrante..
            val guestRoutePrefix = com.example.live.LiveRoutes.LIVE_GUEST.substringBefore("{")
            val invitationScope = rememberCoroutineScope()
            DisposableEffect(com.example.data.supabase.SupabaseClient.currentUser) {
                var watcher: com.example.live.data.remote.LiveGuestInvitationWatcher? = null
                var watcherActive = false
                val routeJob = snapshotFlow { mainNavController.currentDestination?.route }
                    .distinctUntilChanged()
                    .onEach { route ->
                        val inGuestRoute = route?.startsWith(guestRoutePrefix) ?: false
                        if (inGuestRoute) {
                            if (watcherActive) {
                                watcher?.stop()
                                watcherActive = false
                            }
                        } else {
                            if (!watcherActive && com.example.data.supabase.SupabaseClient.currentUser != null) {
                                val w = com.example.live.data.remote.LiveGuestInvitationWatcher { liveId ->
                                    mainNavController.navigate(com.example.live.LiveRoutes.createGuestRoute(liveId)) { launchSingleTop = true }
                                }
                                w.start()
                                watcher = w
                                watcherActive = true
                            }
                        }
                    }
                    .launchIn(invitationScope)
                onDispose {
                    routeJob.cancel()
                    watcher?.stop()
                    watcher = null
                }
            }
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
        navController = mainNavController,
        startDestination = "chatsList",
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(350))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(350))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(350))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(350))
        }
    ) {
        // Main Chats List & States Dashboard
        composable("chatsList") {
            ChatsListScreen(
                chatsViewModel = chatsViewModel,
                statesViewModel = statesViewModel,
                authViewModel = authViewModel,
                profileViewModel = profileViewModel,
                notificationsViewModel = notificationsViewModel,
                onNavigateToChat = { chatId, otherUserId ->
                    mainNavController.navigate("chat/$chatId/$otherUserId") { launchSingleTop = true }
                },
                onNavigateToSearch = { mainNavController.navigate("search") { launchSingleTop = true } },
                onNavigateToCreateState = { mainNavController.navigate("createStory") { launchSingleTop = true } },
                onNavigateToCreateStory = { mainNavController.navigate("createStory") { launchSingleTop = true } },
                onNavigateToCreateReel = { mainNavController.navigate("createReel") { launchSingleTop = true } },
                onNavigateToViewState = { stateId ->
                    mainNavController.navigate("viewState/$stateId") { launchSingleTop = true }
                },
                onNavigateToTikTok = { stateId ->
                    mainNavController.navigate("tiktok/$stateId") { launchSingleTop = true }
                },
                onNavigateToSearchReels = { mainNavController.navigate("reelSearch") { launchSingleTop = true } },
                onNavigateToProfile = { mainNavController.navigate("profile") { launchSingleTop = true } },
                onNavigateToUserProfile = { userId ->
                    mainNavController.navigate("userProfile/$userId") { launchSingleTop = true }
                },
                onNavigateToNotifications = { mainNavController.navigate("notifications") { launchSingleTop = true } },
                onNavigateToFavorites = { mainNavController.navigate("favorites") { launchSingleTop = true } },
                onNavigateToMusic = { mainNavController.navigate("musicHome") { launchSingleTop = true } },
                onNavigateToVoiceRoom = { mainNavController.navigate("voiceRooms") { launchSingleTop = true } },
                onNavigateToLive = { mainNavController.navigate("live_feed") { launchSingleTop = true } }
            )
        }

        // Directorio de Salas de Voz (grid mini + crear)
        composable("voiceRooms") {
            com.example.rooms.ui.VoiceRoomBrowserScreen(
                onBack = { mainNavController.popBackStack() },
                onEnterRoom = { id ->
                    mainNavController.navigate("voiceRoom/$id")
                }
            )
        }

        // Sala de Voz individual (modulo independiente com.example.rooms)
        composable(
            route = "voiceRoom/{roomId}",
            arguments = listOf(navArgument("roomId") { type = NavType.StringType }),
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(320, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(320))
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(280))
            },
            popEnterTransition = {
                slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = tween(320, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(320))
            },
            popExitTransition = {
                slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(280))
            }
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId")
            com.example.rooms.ui.VoiceRoomScreen(
                roomId = roomId,
                onBack = { mainNavController.popBackStack() },
                onOpenProfile = { userId ->
                    mainNavController.navigate("userProfile/$userId") { launchSingleTop = true }
                }
            )
        }

        // Panalink Live (módulo independiente com.example.live)
        composable(com.example.live.LiveRoutes.LIVE_FEED) {
            com.example.live.ui.screen.LiveFeedScreen(
                onNavigateBack = { mainNavController.popBackStack() },
                onNavigateToViewer = { liveId ->
                    mainNavController.navigate(com.example.live.LiveRoutes.createViewerRoute(liveId)) { launchSingleTop = true }
                },
                onNavigateToBroadcast = {
                    mainNavController.navigate(com.example.live.LiveRoutes.LIVE_BROADCAST) { launchSingleTop = true }
                }
            )
        }

        composable(
            route = com.example.live.LiveRoutes.LIVE_VIEWER,
            arguments = listOf(navArgument("liveId") { type = NavType.StringType })
        ) { backStackEntry ->
            val liveId = backStackEntry.arguments?.getString("liveId") ?: ""
            com.example.live.ui.screen.LiveViewerScreen(
                liveId = liveId,
                onNavigateBack = { mainNavController.popBackStack() }
            )
        }

        composable(com.example.live.LiveRoutes.LIVE_BROADCAST) {
            com.example.live.ui.screen.LiveBroadcastScreen(
                onNavigateBack = { mainNavController.popBackStack() }
            )
        }

        composable(
            route = com.example.live.LiveRoutes.LIVE_GUEST,
            arguments = listOf(navArgument("liveId") { type = NavType.StringType })
        ) { backStackEntry ->
            val liveId = backStackEntry.arguments?.getString("liveId") ?: ""
            com.example.live.ui.screen.LiveGuestScreen(
                liveId = liveId,
                onNavigateBack = { mainNavController.popBackStack() }
            )
        }

        // Music Studio Home
        composable("musicHome") {
            com.example.media.ui.MusicHomeScreen(
                onBackClick = { mainNavController.popBackStack() },
                onPlaylistClick = { playlistId ->
                    mainNavController.navigate("playlist/$playlistId")
                },
                 onInvitationsClick = { mainNavController.navigate("playlist/invitations") },
                onPlayTrack = { track ->
                    playerViewModel.playTrack(track)
                }
            )
        }

        // Playlist Details
        composable(
            route = "playlist/{playlistId}",
            arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
            val context = androidx.compose.ui.platform.LocalContext.current
            val db = com.example.data.database.PanalinkDatabase.getDatabase(context)
            val playlistRepository = remember { com.example.media.playlist.PlaylistRepository(db.playlistDao(), db.collaboratorDao()) }
            val invitationRepository = remember {
                com.example.media.playlist.PlaylistInvitationRepository(
                    db.invitationDao(),
                    com.example.data.supabase.SupabaseClient.apiService!!,
                    com.example.data.supabase.SupabaseClient.supabaseAnonKey
                )
            }
            val audioRepository = remember { com.example.media.audio.AudioRepository(db.audioDao()) }
            val playlistManager = remember { com.example.media.playlist.PlaylistManager(playlistRepository, audioRepository) }

            // P6.7.7A - Realtime Setup
            val syncManager = remember {
                com.example.media.sync.MusicSocialSyncManager(
                    context = context.applicationContext,
                    supabaseApi = com.example.data.supabase.SupabaseClient.apiService!!,
                    playlistRepo = playlistRepository,
                    invitationRepo = invitationRepository,
                    audioRepo = audioRepository,
                    apiKey = com.example.data.supabase.SupabaseClient.supabaseAnonKey
                )
            }
            val realtimeManager = remember { com.example.media.sync.MusicPlaylistRealtimeManager(syncManager) }

            val playlistViewModel: com.example.media.player.ui.PlaylistViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                key = playlistId,
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return com.example.media.player.ui.PlaylistViewModel(
                            playlistId,
                            playlistManager,
                            playlistRepository,
                            invitationRepository,
                            realtimeManager
                        ) as T
                    }
                }
            )

            val uiState by playlistViewModel.uiState.collectAsState()

            // --- Share playlist flow (chat picker) ---
            var showShareSheet by remember { androidx.compose.runtime.mutableStateOf(false) }
            var shareChats by remember { androidx.compose.runtime.mutableStateOf<List<com.example.data.model.ChatWithDetails>>(emptyList()) }
            var shareChatsLoading by remember { androidx.compose.runtime.mutableStateOf(false) }
            var isPreparingShare by remember { androidx.compose.runtime.mutableStateOf(false) }
            val shareScope = androidx.compose.runtime.rememberCoroutineScope()

            uiState.playlist?.let { p ->
                com.example.media.ui.PlaylistScreen(
                    playlist = p,
                    songs = uiState.tracks,
                    userRole = uiState.userRole,
                    onBackClick = { mainNavController.popBackStack() },
                    onPlayAllClick = { playlistViewModel.playAll(playerViewModel) },
                    onShuffleClick = { playlistViewModel.shuffleAndPlay(playerViewModel) },
                    onPlayTrackClick = { track -> playlistViewModel.playTrack(track, playerViewModel) },
                    onRemoveTrackClick = { track -> playlistViewModel.removeTrack(track) },
                    onSharePlaylistClick = {
                        showShareSheet = true
                        shareChatsLoading = true
                        shareScope.launch {
                            val result = com.example.data.repository.ChatsRepository().getChatsWithDetails()
                            shareChats = result.getOrDefault(emptyList())
                            shareChatsLoading = false
                        }
                    },
                     onCollaboratorsClick = { mainNavController.navigate("playlist/$playlistId/collaborators") },
                    onGenerateCoverClick = {
                        mainNavController.navigate("playlistCoverStudio/$playlistId")
                    }
                )
            } ?: run {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = Color(0xFF38BDF8))
                }
            }

            if (showShareSheet) {
                androidx.compose.material3.ModalBottomSheet(
                    onDismissRequest = { if (!isPreparingShare) showShareSheet = false },
                    containerColor = Color(0xFF0F172A),
                    dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle(color = Color.Gray) }
                ) {
                    if (isPreparingShare) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(color = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Preparando tu playlist...", color = Color.White, fontSize = 14.sp)
                        }
                    } else {
                        com.example.media.ui.SharePlaylistSheet(
                            chats = shareChats,
                            isLoading = shareChatsLoading,
                            playlistTitle = uiState.playlist?.name ?: "",
                            onChatSelected = { selected ->
                                isPreparingShare = true
                                shareScope.launch {
                                    try {
                                        val playlist = uiState.playlist ?: return@launch
                                        val tracks = playlistRepository.getTracksForPlaylistSync(playlistId)
                                        val shareManager = com.example.media.playlist.PlaylistShareManager(playlistRepository, audioRepository)
                                        val senderName = com.example.data.supabase.SupabaseClient.currentUser
                                            ?.userMetadata?.get("display_name") as? String ?: "Un pana"
                                        val payload = shareManager.buildRichPayload(playlist, tracks, senderName)
                                        val json = kotlinx.serialization.json.Json.encodeToString(
                                            com.example.media.playlist.PlaylistSharePayload.serializer(),
                                            payload
                                        )
                                        com.example.data.repository.MessagesRepository.getInstance().sendMessage(
                                            chatId = selected.chat.id,
                                            content = json,
                                            receiverUid = selected.otherMember?.id,
                                            messageType = "playlist",
                                            mediaUrl = payload.coverPath,
                                            duration = payload.durationMs
                                        )
                                        showShareSheet = false
                                        android.widget.Toast.makeText(
                                            context,
                                            "🎵 Playlist compartida con ${selected.otherMember?.displayName ?: selected.chat.name ?: "tu pana"}",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                        mainNavController.navigate("chat/${selected.chat.id}/${selected.otherMember?.id ?: ""}") { launchSingleTop = true }
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "No se pudo compartir la playlist", android.widget.Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isPreparingShare = false
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        composable(
            route = "playlistCoverStudio/{playlistId}",
            arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
            val context = androidx.compose.ui.platform.LocalContext.current
            val db = com.example.data.database.PanalinkDatabase.getDatabase(context)

            val repository = remember { com.example.media.playlist.cover.PlaylistCoverRepository(db.creativeProjectDao()) }
            val storageManager = remember { com.example.media.storage.MediaStorageManager(context) }
            val exporter = remember { com.example.media.playlist.cover.PlaylistCoverExporter(context, storageManager) }

            val coverViewModel: com.example.media.playlist.cover.PlaylistCoverViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return com.example.media.playlist.cover.PlaylistCoverViewModel(
                            context.applicationContext as android.app.Application,
                            repository,
                            exporter
                        ) as T
                    }
                }
            )

            com.example.media.playlist.cover.PlaylistCoverStudioScreen(
                viewModel = coverViewModel,
                onBack = { mainNavController.popBackStack() },
                onFinish = { _ -> mainNavController.popBackStack() }
            )
        }

        composable(
            route = "playlist/{playlistId}/collaborators",
            arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
            val context = androidx.compose.ui.platform.LocalContext.current
            val db = com.example.data.database.PanalinkDatabase.getDatabase(context)
            val playlistRepository = remember { com.example.media.playlist.PlaylistRepository(db.playlistDao(), db.collaboratorDao()) }
            val invitationRepository = remember {
                com.example.media.playlist.PlaylistInvitationRepository(
                    db.invitationDao(),
                    com.example.data.supabase.SupabaseClient.apiService!!,
                    com.example.data.supabase.SupabaseClient.supabaseAnonKey
                )
            }
            val audioRepository = remember { com.example.media.audio.AudioRepository(db.audioDao()) }
            val playlistManager = remember { com.example.media.playlist.PlaylistManager(playlistRepository, audioRepository) }

            val viewModel: com.example.media.player.ui.PlaylistViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                key = playlistId,
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return com.example.media.player.ui.PlaylistViewModel(
                            playlistId,
                            playlistManager,
                            playlistRepository,
                            invitationRepository,
                            null
                        ) as T
                    }
                }
            )

            com.example.media.collaboration.ui.PlaylistCollaboratorsScreen(
                playlistId = playlistId,
                viewModel = viewModel,
                onBack = { mainNavController.popBackStack() }
            )
        }

        composable("playlist/invitations") {
            com.example.media.collaboration.ui.PlaylistInvitationsScreen(
                onBack = { mainNavController.popBackStack() },
                onOpenPlaylist = { playlistId ->
                    mainNavController.navigate("playlist/$playlistId")
                }
            )
        }

        // Favorites / Saved Messages Screen
        composable("favorites") {
            FavoritesScreen(
                chatViewModel = chatViewModel,
                onBack = { mainNavController.popBackStack() },
                onNavigateToChat = { chatId ->
                    mainNavController.navigate("chat/$chatId/unknown") { launchSingleTop = true }
                }
            )
        }

        // Search Users Screen
        composable("search") {
            SearchUsersScreen(
                viewModel = chatsViewModel,
                onBack = { mainNavController.popBackStack() },
                onChatOpened = { chatId, otherUserId ->
                    mainNavController.navigate("chat/$chatId/$otherUserId") { launchSingleTop = true;
                        popUpTo("search") { inclusive = true }
                    }
                }
            )
        }

        composable("notifications") {
            com.example.ui.screen.NotificationsScreen(
                viewModel = notificationsViewModel,
                onNavigateBack = { mainNavController.popBackStack() },
                onNavigateToState = { stateId ->
                    mainNavController.navigate("viewState/$stateId") { launchSingleTop = true }
                },
                onNavigateToChat = { chatId, otherUserId ->
                    mainNavController.navigate("chat/$chatId/$otherUserId") { launchSingleTop = true }
                },
                onNavigateToProfile = { userId ->
                    // Navigate to profile (we might not have another user profile view yet, so just fallback to chatsList)
                    mainNavController.navigate("userProfile/$userId") { launchSingleTop = true }
                },
                onNavigateToReel = { reelId ->
                    mainNavController.navigate("tiktok/$reelId") { launchSingleTop = true }
                },
                onNavigateToPostDetail = { postId ->
                    mainNavController.navigate("postDetail/$postId") { launchSingleTop = true }
                }
            )
        }

        // Messaging Chat Screen
        composable(
            route = "chat/{chatId}/{otherUserId}",
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType },
                navArgument("otherUserId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            val otherUserId = backStackEntry.arguments?.getString("otherUserId") ?: ""
            val context = androidx.compose.ui.platform.LocalContext.current
            val db = com.example.data.database.PanalinkDatabase.getDatabase(context)
            val playlistRepo = remember { com.example.media.playlist.PlaylistRepository(db.playlistDao(), db.collaboratorDao()) }
            val audioRepo = remember { com.example.media.audio.AudioRepository(db.audioDao()) }
            val playlistManager = remember { com.example.media.playlist.PlaylistManager(playlistRepo, audioRepo) }

            val scope = androidx.compose.runtime.rememberCoroutineScope()
            ChatScreen(
                viewModel = chatViewModel,
                chatId = chatId,
                otherUserId = otherUserId,
                onBack = { mainNavController.popBackStack() },
                onNavigateToChatMedia = { mainNavController.navigate("chatGallery/$chatId") },
                onNavigateToSearch = { mainNavController.navigate("chatSearch/$chatId") },
                navController = mainNavController,
                onPlaylistAction = { payload, action ->
                    val shareManager = com.example.media.playlist.PlaylistShareManager(playlistRepo, audioRepo)
                    val currentUid = com.example.data.supabase.SupabaseClient.currentUser?.id ?: "me"
                    when (action) {
                        "OPEN" -> {
                            scope.launch {
                                shareManager.importSharedPlaylist(payload, currentUid)
                                mainNavController.navigate("playlist/${payload.playlistId}")
                            }
                        }
                        "PLAY" -> {
                            scope.launch {
                                var tracks = playlistRepo.getTracksForPlaylistSync(payload.playlistId)
                                if (tracks.isEmpty()) {
                                    // Recipient side: import the shared playlist first so
                                    // its songs become streamable remote tracks.
                                    shareManager.importSharedPlaylist(payload, currentUid)
                                    tracks = playlistRepo.getTracksForPlaylistSync(payload.playlistId)
                                }
                                if (tracks.isNotEmpty()) {
                                    playerViewModel.playTracks(tracks, 0)
                                } else {
                                    android.widget.Toast.makeText(context, "Esta playlist no tiene canciones disponibles", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        "SAVE" -> {
                            scope.launch {
                                val created = shareManager.importSharedPlaylist(payload, currentUid)
                                android.widget.Toast.makeText(
                                    context,
                                    if (created) "🎵 Playlist guardada en tu biblioteca" else "Ya tienes esta playlist",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            )
        }

        // Chat Media Gallery Screen
        composable(
            route = "chatGallery/{chatId}",
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            ChatMediaGalleryScreen(
                chatId = chatId,
                onBack = { mainNavController.popBackStack() }
            )
        }

        // Chat Search Screen
        composable(
            route = "chatSearch/{chatId}",
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            ChatSearchScreen(
                chatId = chatId,
                onBack = { mainNavController.popBackStack() },
                onResultClick = { messageId ->
                    // Save target message ID in a shared state or pass via navigation result
                    mainNavController.previousBackStackEntry?.savedStateHandle?.set("targetMessageId", messageId)
                    mainNavController.popBackStack()
                }
            )
        }

        // Story Editor Screen (editor limpio, foto/vídeo/texto + audio real)
        composable("createStory") {
            CleanStoryEditorScreen(
                viewModel = statesViewModel,
                onBack = { mainNavController.popBackStack() }
            )
        }

        // Reel Editor Screen (FASE 4B)
        composable("createReel") {
            ReelEditorScreen(
                viewModel = statesViewModel,
                onBack = { mainNavController.popBackStack() }
            )
        }

        // View Status Player Screen
        composable(
            route = "viewState/{stateId}",
            arguments = listOf(navArgument("stateId") { type = NavType.StringType })
        ) { backStackEntry ->
            val stateId = backStackEntry.arguments?.getString("stateId") ?: ""
            ViewStateScreen(
                viewModel = statesViewModel,
                stateId = stateId,
                onClose = { mainNavController.popBackStack() },
                onNavigateToUserProfile = { userId ->
                    mainNavController.navigate("userProfile/$userId") { launchSingleTop = true }
                }
            )
        }

        // Post Detail Screen
        composable(
            route = "postDetail/{postId}",
            arguments = listOf(navArgument("postId") { type = NavType.StringType })
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: ""
            val feedViewModel: com.example.ui.viewmodel.FeedViewModel = viewModel()
            PostDetailScreen(
                postId = postId,
                viewModel = feedViewModel,
                onBackClick = { mainNavController.popBackStack() }
            )
        }

        // TikTok Video Feed Screen
        composable(
            route = "tiktok/{stateId}",
            arguments = listOf(navArgument("stateId") { type = NavType.StringType })
        ) { backStackEntry ->
            val stateId = backStackEntry.arguments?.getString("stateId") ?: ""
            ReelsFeedScreen(
                viewModel = statesViewModel,
                initialStateId = stateId,
                onBack = { mainNavController.popBackStack() },
                onSearchReels = {
                    mainNavController.navigate("reelSearch") { launchSingleTop = true }
                },
                onNavigateToUserProfile = { userId ->
                    mainNavController.navigate("userProfile/$userId") { launchSingleTop = true }
                },
                onNavigateToHashtag = { tag ->
                    mainNavController.navigate("search_results/${android.net.Uri.encode(tag)}") { launchSingleTop = true }
                },
            )
        }

        // Reels search (TikTok-style search-as-you-type); opened from the
        // magnifier icon on the floating feed pill.
        composable("reelSearch") {
            com.example.reels.ui.ReelSearchScreen(
                viewModel = statesViewModel,
                initialTag = null,
                onBack = { mainNavController.popBackStack() },
                onVideoClick = { stateId ->
                    mainNavController.navigate("tiktok/$stateId") { launchSingleTop = true }
                },
                onHashtagClick = { tag ->
                    mainNavController.navigate("search_results/${android.net.Uri.encode(tag)}") { launchSingleTop = true }
                },
                onUserClick = { userId ->
                    mainNavController.navigate("userProfile/$userId") { launchSingleTop = true }
                }
            )
        }

        // Search Results for Hashtag (grid of videos under #tag).
        composable(
            route = "search_results/{tag}",
            arguments = listOf(navArgument("tag") { type = NavType.StringType })
        ) { backStackEntry ->
            val tag = android.net.Uri.decode(backStackEntry.arguments?.getString("tag") ?: "")
            com.example.reels.ui.ReelSearchScreen(
                viewModel = statesViewModel,
                initialTag = tag,
                onBack = { mainNavController.popBackStack() },
                onVideoClick = { stateId ->
                    mainNavController.navigate("tiktok/$stateId") { launchSingleTop = true }
                },
                onHashtagClick = { newTag ->
                    mainNavController.navigate("search_results/${android.net.Uri.encode(newTag)}") { launchSingleTop = true }
                },
                onUserClick = { userId ->
                    mainNavController.navigate("userProfile/$userId") { launchSingleTop = true }
                }
            )
        }

        // Edit User Profile Screen
        composable("profile") {
            ProfileScreen(
                viewModel = profileViewModel,
                authViewModel = authViewModel,
                onBack = { mainNavController.popBackStack() },
                onNavigateToReel = { reelId ->
                    mainNavController.navigate("tiktok/$reelId") { launchSingleTop = true }
                }
            )
        }

        // Public User Profile View
        composable(
            route = "userProfile/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            UserProfileScreen(
                userId = userId,
                statesViewModel = statesViewModel,
                onBack = { mainNavController.popBackStack() },
                onNavigateToChat = { chatId, otherUserId ->
                    mainNavController.navigate("chat/$chatId/$otherUserId") { launchSingleTop = true }
                },
                onNavigateToReel = { reelId ->
                    mainNavController.navigate("tiktok/$reelId") { launchSingleTop = true }
                }
            )
        }
    }
    com.example.ui.components.FloatingPlayerBubble(
        onNavigateToReels = { stateId ->
            mainNavController.navigate("tiktok/$stateId") { launchSingleTop = true }
        }
    )
    com.example.ui.components.FloatingVideoOverlay(
        onNavigateBackToReels = {
            mainNavController.navigate("clips") { launchSingleTop = true }
        }
    )

    if (isPlayerFullVisible) {
        com.example.media.player.ui.MusicPlayerScreen(
            viewModel = playerViewModel,
            onClose = onPlayerFullClose
        )
    }
}
}
