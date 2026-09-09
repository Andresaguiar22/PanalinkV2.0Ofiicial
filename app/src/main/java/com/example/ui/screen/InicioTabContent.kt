@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.ui.screen

import com.example.ui.components.*
import com.example.util.*

import androidx.compose.foundation.BorderStroke
import com.example.ui.components.FeedPostCard
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import com.example.ui.viewmodel.StatesViewModel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import coil.compose.AsyncImage
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.asImageBitmap
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.identity.model.toIdentityUiState
import androidx.navigation.NavGraph.Companion.findStartDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.data.model.*
import com.example.data.supabase.SupabaseClient
import com.example.ui.viewmodel.*
import com.example.ui.theme.shimmerEffect
import com.example.ui.theme.getAvatarGradient
import com.example.ui.components.PanalinkPullToRefreshBox
import com.example.ui.theme.bounceClick
import com.example.ui.components.chat.list.ChatPreviewCard
import com.example.util.ChatListScrollManager
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.text.SimpleDateFormat
import java.util.*

import com.example.ui.viewmodel.NotificationsViewModel

@OptIn(ExperimentalMaterial3Api::class)


@Composable
fun TuTabContent(
    profileViewModel: ProfileViewModel,
    authViewModel: AuthViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val profileState by profileViewModel.profileState.collectAsState()
    val currentUid = SupabaseClient.currentUser?.id ?: ""
    val email = SupabaseClient.currentUser?.email ?: "pana@panalink.com"
    val colors = com.example.ui.theme.LocalAppColors.current

    val prefs = context.getSharedPreferences("panalink_prefs", android.content.Context.MODE_PRIVATE)
    val isMinimalistMode by com.example.ui.theme.ThemeManager.isMinimalistMode.collectAsState()
    var isFloatingPipEnabled by remember { mutableStateOf(prefs.getBoolean("floating_pip_enabled", true)) }

    val contactIdentifier by profileViewModel.contactIdentifierState.collectAsStateWithLifecycle()
    val userPinState = contactIdentifier?.pin ?: (profileState as? ProfileUiState.Success)?.profile?.pin ?: ""
    val qrPayloadState = contactIdentifier?.qrPayload ?: "panalink:contact:$currentUid"

    var displayName by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("") }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var pinInputText by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }
    var nameInputText by remember { mutableStateOf("") }

    var profileThemeChoice by remember { mutableStateOf(prefs.getString("profile_theme_${currentUid}", "dark_teal") ?: "dark_teal") }

    LaunchedEffect(currentUid) {
        profileViewModel.loadProfile()
    }

    LaunchedEffect(profileState) {
        if (profileState is ProfileUiState.Success) {
            val prof = (profileState as ProfileUiState.Success).profile
            displayName = prof.displayName
            avatarUrl = prof.avatarUrl ?: ""
        }
    }

    LaunchedEffect(profileThemeChoice) {
        prefs.edit().putString("profile_theme_${currentUid}", profileThemeChoice).putString("profile_theme_global", profileThemeChoice).apply()
        com.example.ui.theme.ThemeManager.themeKey.value = profileThemeChoice
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. USER PROFILE CARD ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.secondary),
                border = BorderStroke(1.dp, Color(0xFF262629)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    com.example.ui.components.PanaAvatar(
    avatarUrl = avatarUrl.ifEmpty { null },
    size = 100.dp,
    borderColor = colors.accent,
    borderWidth = 3.dp,
    placeholderName = displayName
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = displayName,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                nameInputText = displayName
                                showEditNameDialog = true
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar nombre",
                                tint = colors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Text(
                        text = email,
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // --- 2. PIN Y CÓDIGO QR CARD ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.secondary),
                border = BorderStroke(1.dp, Color(0xFF262629)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Tu Código QR de Pana 🪪",
                        color = colors.accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Comparte este QR o PIN para que te agreguen al instante",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (userPinState.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .border(2.dp, colors.accent, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            com.example.ui.components.QrCodeView(
                                pin = userPinState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "PIN: $userPinState",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("PIN de Pana", userPinState)
                                    clipboardManager.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(context, "¡PIN Copiado! 📋", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Copiar PIN",
                                    tint = colors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        CircularProgressIndicator(color = colors.accent)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            pinInputText = userPinState
                            pinError = ""
                            showSetPinDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Configurar PIN de Seguridad", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- 3. CONFIGURACIONES DE PERFIL ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.secondary),
                border = BorderStroke(1.dp, Color(0xFF262629)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Configuración de Aplicación 🇻🇪",
                        color = colors.accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    // Tema de Pana Selector
                    Column {
                        Text("Tema Visual de Pana", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val themes = listOf(
                                "dark_teal" to "Teal",
                                "royal_purple" to "Púrpura",
                                "neon_orange" to "Naranja",
                                "nordic_ice" to "Ice"
                            )
                            themes.forEach { (key, label) ->
                                val isSelected = profileThemeChoice == key
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) colors.accent else Color(0xFF121214))
                                        .clickable { profileThemeChoice = key }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else Color.LightGray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E2E36), thickness = 0.5.dp)

                    // Minimalist Mode Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Modo Minimalista", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Simplifica los menús y acciones", color = Color.LightGray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = isMinimalistMode,
                            onCheckedChange = { checked ->
                                com.example.ui.theme.ThemeManager.isMinimalistMode.value = checked
                                prefs.edit().putBoolean("minimalist_mode_global", checked).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = colors.accent,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color(0xFF1E2E36)
                            )
                        )
                    }

                    HorizontalDivider(color = Color(0xFF1E2E36), thickness = 0.5.dp)

                    // Floating PiP Toggle Option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Ventanas Flotantes (PiP)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Permitir que Pana TV y Reels floten al salir", color = Color.LightGray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = isFloatingPipEnabled,
                            onCheckedChange = { checked ->
                                isFloatingPipEnabled = checked
                                prefs.edit().putBoolean("floating_pip_enabled", checked).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = colors.accent,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color(0xFF1E2E36)
                            )
                        )
                    }

                    HorizontalDivider(color = Color(0xFF1E2E36), thickness = 0.5.dp)

                    // Logout Button
                    Button(
                        onClick = {
                            scope.launch {
                                authViewModel.logout()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cerrar Sesión de Pana", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Edit Name Dialog
    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Editar Nombre de Pana", color = Color.White) },
            containerColor = Color(0xFF121214),
            text = {
                OutlinedTextField(
                    value = nameInputText,
                    onValueChange = { nameInputText = it },
                    label = { Text("Nombre Completo") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = Color.LightGray
                    ),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (nameInputText.isNotBlank()) {
                            profileViewModel.saveProfile(nameInputText, avatarUrl)
                            showEditNameDialog = false
                        }
                    }
                ) {
                    Text("Guardar", color = colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancelar", color = Color.LightGray)
                }
            }
        )
    }


}




@Composable
fun InicioTabContent(
    statesState: StatesUiState,
    statesViewModel: StatesViewModel,
    onNavigateToViewState: (String) -> Unit,
    onNavigateToCreateState: () -> Unit,
    onNavigateToChat: (String, String) -> Unit,
    feedViewModel: com.example.ui.viewmodel.FeedViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val colors = com.example.ui.theme.LocalAppColors.current
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val feedUiState by feedViewModel.uiState.collectAsStateWithLifecycle()
    var showCreatePostSheet by remember { mutableStateOf(false) }
    var selectedPostForComments by remember { mutableStateOf<com.example.data.model.PostDto?>(null) }
    var editingPostId by remember { mutableStateOf<String?>(null) }
    var editingPostContent by remember { mutableStateOf("") }
    
    var fullScreenMediaList by remember { mutableStateOf<List<String>?>(null) }
    var fullScreenInitialPage by remember { mutableIntStateOf(0) }
    var fullScreenBackgroundAudio by remember { mutableStateOf<String?>(null) }
    var fullScreenStartPosition by remember { mutableLongStateOf(0L) }
    var postToDeleteId by remember { mutableStateOf<String?>(null) }
    var activePlaylistPost by remember { mutableStateOf<com.example.data.model.PostDto?>(null) }

    // Comments Bottom Sheet State
    var showCommentsSheet by remember { mutableStateOf(false) }
    var activeCommentStateId by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)


    // Auto-refresh when user session becomes available
    LaunchedEffect(com.example.data.supabase.SupabaseClient.currentUser) {
        if (com.example.data.supabase.SupabaseClient.currentUser != null) {
            if (feedUiState.posts.isEmpty()) feedViewModel.refreshFeed()
        }
    }

    // Cache-first for stories: prefetch every story group's first media to disk
    // in the background so the carousel (and the viewer) render instantly on the
    // next app entry, like chats load from Room.
    val preloaderScope = rememberCoroutineScope()
    LaunchedEffect(statesState) {
        val stories = (statesState as? StatesUiState.Success)?.states ?: emptyList()
        if (stories.isNotEmpty()) {
            com.example.media.social.StoryPreloader.preloadAllStories(context, stories, preloaderScope)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PanalinkPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    statesViewModel.loadActiveStates()
                    feedViewModel.refreshFeed()
                    kotlinx.coroutines.delay(1200)
                    isRefreshing = false
                }
            }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
                // Avoid recompositions and large diff calculations on every scroll tick.
                // Items keep stable keys; feed_post_xxx keys prevent item identity loss
                // when the list changes after refresh.
                state = rememberLazyListState()
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "Historias 🇻🇪✨",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // "Mi Historia" card
                            item {
                                Card(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .height(150.dp)
                                        .clickable { onNavigateToCreateState() },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161618))
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        val resolvedAvatar = remember(SupabaseClient.currentProfile?.avatarUrl) {
                                            com.example.data.repository.CdnManager.resolveAvatarUrl(SupabaseClient.currentProfile?.avatarUrl)
                                        }
                                        if (resolvedAvatar != null) {
                                            AsyncImage(
                                                model = resolvedAvatar,
                                                contentDescription = "Mi Avatar",
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .fillMaxHeight(0.7f),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .fillMaxHeight(0.7f)
                                                    .background(Color(0xFF161618))
                                            )
                                        }
                                        // Bottom portion
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight(0.3f)
                                                .align(Alignment.BottomCenter)
                                                .background(Color(0xFF161618))
                                        ) {
                                            Text(
                                                "Tu historia",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                                            )
                                        }
                                        
                                        // Add icon overlapping the middle
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .offset(y = (-20).dp)
                                                .size(28.dp)
                                                .background(Color(0xFFD500F9), CircleShape)
                                                .border(2.dp, Color(0xFF161618), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }

                            // Contacts' stories
                            if (statesState is StatesUiState.Success) {
                                val uniqueUserStories = statesState.states.distinctBy { it.state.userId }
                                items(uniqueUserStories, key = { it.state.userId }) { stateWithUser ->
                                    val firstState = statesState.states.firstOrNull { it.state.userId == stateWithUser.state.userId } ?: stateWithUser
                                    val profile = stateWithUser.profile
                                    val state = firstState.state
                                    val userStories = statesState.states.filter { it.state.userId == stateWithUser.state.userId }
                                    val hasUnread = userStories.any { it.state.viewedByMe != true }
                                    
                                    val context = androidx.compose.ui.platform.LocalContext.current
                                    val identityRepository = androidx.compose.runtime.remember { com.example.identity.bridge.LegacyIdentityBridge(context).identityRepository }
                                    val identityState by identityRepository.observeIdentity(state.userId).collectAsStateWithLifecycle(initialValue = com.example.identity.memory.IdentityMemoryCache.profiles.get(state.userId)?.toIdentityUiState())
                                    val safeAvatarUrl = identityState?.avatarUrl ?: profile.avatarUrl
                                    val safeDisplayName = identityState?.displayName ?: profile.displayName
                                    val safeUserId = identityState?.userId ?: profile.id

                                    Card(
                                        modifier = Modifier
                                            .width(100.dp)
                                            .height(150.dp)
                                            .clickable { onNavigateToViewState(state.id) },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            // Background — cache-first: prefer the local copy of the story
                                            // media (already synced to disk) and fall back to the remote
                                            // URL. This makes the carousel render instantly on app re-entry.
                                            val resolvedStoryResource = com.example.media.social.StoryMediaResolver.rememberResolvedStoryMediaResource(state)
                                            // For VCDN video stories the resolved remote URL is an HLS
                                            // manifest (.m3u8) which Coil cannot render as an image, so
                                            // the carousel showed a black card. Resolve the VCDN poster
                                            // (a real JPG) for the thumbnail instead.
                                            var vcdnPoster by androidx.compose.runtime.remember(state.id, state.mediaUrl, state.vcdnPosterUrl) {
                                                androidx.compose.runtime.mutableStateOf<String?>(state.vcdnPosterUrl ?: state.thumbnailUrl)
                                            }
                                            androidx.compose.runtime.LaunchedEffect(state.id, state.mediaUrl, state.vcdnPosterUrl) {
                                                val raw = state.mediaUrl ?: ""
                                                if (state.vcdnPosterUrl.isNullOrBlank()) {
                                                    if (raw.startsWith("vcdn://") && state.localVideoPath.isNullOrBlank()) {
                                                        vcdnPoster = com.example.data.repository.VcdnUrlResolver.resolvePoster(raw)
                                                    }
                                                } else {
                                                    vcdnPoster = state.vcdnPosterUrl
                                                }
                                            }
                                            val thumbnailModel = when {
                                                vcdnPoster != null -> vcdnPoster
                                                resolvedStoryResource is com.example.media.model.MediaResource.Local -> java.io.File(resolvedStoryResource.path)
                                                resolvedStoryResource is com.example.media.model.MediaResource.Remote -> {
                                                    val u = resolvedStoryResource.url
                                                    // HLS manifests (.m3u8) or raw VCDN pointers (vcdn://) are not images;
                                                    // use the explicit thumbnail when available, else fall back to avatar
                                                    // until the poster is resolved or ready..
                                                    if (u.contains(".m3u8", ignoreCase = true) || u.startsWith("vcdn://")) {
                                                        state.thumbnailUrl?.takeIf { it.isNotBlank() } ?: safeAvatarUrl ?: ""
                                                    } else u
                                                }
                                                else -> {
                                                    val u = state.mediaUrl ?: ""
                                                    if (u.startsWith("vcdn://")) {
                                                        state.thumbnailUrl?.takeIf { it.isNotBlank() } ?: safeAvatarUrl ?: ""
                                                    } else u.ifBlank { state.thumbnailUrl ?: safeAvatarUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=150&q=80" }
                                                }
                                            }
                                            AsyncImage(
                                                model = thumbnailModel,
                                                contentDescription = safeDisplayName,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            
                                            // Gradient overlay for text readability
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        brush = Brush.verticalGradient(
                                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                                            startY = 100f
                                                        )
                                                    )
                                            )

                                            // Profile picture top left
                                            com.example.ui.components.PanaAvatar(
                                                avatarUrl = safeAvatarUrl,
                                                userId = safeUserId,
                                                size = 32.dp,
                                                borderWidth = 2.dp,
                                                borderColor = if (hasUnread) Color(0xFFB026FF) else Color.Gray.copy(alpha = 0.5f),
                                                placeholderName = safeDisplayName,
                                                modifier = Modifier
                                                    .padding(8.dp)
                                            )

                                            // Username bottom
                                            Text(
                                                text = safeDisplayName?.take(15) ?: "",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                                            )
                                        }
                                    }
                                }
                            } else if (statesState is StatesUiState.Loading) {
                                if (!com.example.util.NetworkMonitor.isOnline.value) {
                                    // Sin conexión total y sin caché: evita el shimmer infinito (que
                                    // parecía una app congelada) mostrando el estado offline real.
                                    items(1, key = { "stories_offline" }) {
                                        Box(
                                            modifier = Modifier
                                                .width(200.dp)
                                                .height(150.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF161618)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "Sin conexión",
                                                color = Color.Gray,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                } else {
                                    items(4, key = { "loading_story_$it" }) {
                                        Box(
                                            modifier = Modifier
                                                .width(100.dp)
                                                .height(150.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .shimmerEffect()
                                        )
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color(0xFF121214), thickness = 1.dp)
                }



                // Banner offline amistoso: cuando no hay internet pero sí hay
                // contenido cacheado (Room/ROM) seguimos mostrando el muro y las
                // historias guardadas y le decimos al usuario que está viendo copia local.
                if (!com.example.util.NetworkMonitor.isOnline.value && (feedUiState.posts.isNotEmpty() || ((statesState is StatesUiState.Success) && statesState.states.isNotEmpty()))) {
                    item(key = { "offline_banner_cached" }) {
                        Surface(
                            color = Color(0xFF1B1B20),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top =  8.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal =  12.dp, vertical =  10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.WifiOff,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sin conexión: estás viendo tus publicaciones e historias guardadas. Se actualizarán solas al volver el internet.",
                                    color = Color.White.copy(alpha =  0.85f),
                                    fontSize =  12.sp,
                                    lineHeight =  16.sp
                                )
                            }
                        }
                    }
                }

                // Header for Feed Section
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "El Muro 💬",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { showCreatePostSheet = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddBox,
                                contentDescription = "Publicar",
                                tint = Color(0xFF00E5FF)
                            )
                        }
                    }
                }

                // Pending Upload Posts: pildora minima con progreso real y cancelar
                itemsIndexed(feedUiState.pendingPosts, key = { _, pending -> "pending_${pending.id}" }) { _, pending ->
                    val globalProgress by com.example.data.repository.UploadRepository.globalUploadProgress.collectAsStateWithLifecycle()
                    val pct = ((globalProgress ?: 0f) * 100f).toInt().coerceIn(0, 100)
                    com.example.ui.components.MiniUploadBar(
                        label = "Subiendo publicación",
                        percent = pct,
                        onCancel = { feedViewModel.cancelPendingPost(pending.id) },
                        color = Color(0xFF00FF85)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Feed Posts from FeedViewModel (PostDto: TEXT, ALBUM, AUDIO)
                if (feedUiState.posts.isNotEmpty()) {
                    itemsIndexed(
                        items = feedUiState.posts,
                        key = { _, post -> "feed_post_${post.id ?: post.hashCode()}" },
                        contentType = { _, post -> post.type ?: "TEXT" }
                    ) { index, post ->
                        LaunchedEffect(index) {
                            // Preload only the next 2 posts to avoid IO saturation
                            if (index < feedUiState.posts.size - 2) {
                                com.example.media.feed.FeedMediaPreloader.preloadNextPostsMedia(context, feedUiState.posts, index + 1, scope)
                            }
                        }
                        FeedPostCard(
                            post = post,
                            onLikeClick = { feedViewModel.toggleLike(post) },
                            onShareClick = { feedViewModel.sharePost(post) },
                            onCommentClick = { selectedPostForComments = post },
                            onProfileClick = { /* Profile click */ },
                            onDeleteClick = { postToDeleteId = post.id },
                            onEditClick = { content ->
                                editingPostId = post.id
                                editingPostContent = content
                            },
                            onMediaClick = { list, page, audio, position ->
                                fullScreenMediaList = list
                                fullScreenInitialPage = page
                                fullScreenBackgroundAudio = audio
                                fullScreenStartPosition = position
                            },
                            onAudioPlaylistClick = { activePlaylistPost = it }
                        )
                        Spacer(modifier = Modifier.height(8.dp).fillMaxWidth().background(Color(0xFF0C0C0E)))
                    }
                }

                // Sin conexión total con el muro sin caché: nada que mostrar más allá de
                // un estado offline claro (en vez de un skeleton infinito del loading).
                if (feedUiState.posts.isEmpty() && !com.example.util.NetworkMonitor.isOnline.value) {

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = Color.Gray.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Sin conexión",
                                color = Color.Gray,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "El muro espera internet. Revisa tu conexión e inténtalo más tarde.",
                                color = Color.Gray.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { feedViewModel.refreshFeed() }) {
                                Text("Reintentar")
                            }
                        }
                    }
                } else if (feedUiState.posts.isEmpty() && !feedUiState.isLoading) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = Color.Gray.copy(alpha = 0.3f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "El muro está vacío por ahora",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Sé el primero en compartir algo con la comunidad",
                                color = Color.Gray.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Surface(
                                onClick = { showCreatePostSheet = true },
                                shape = RoundedCornerShape(24.dp),
                                color = Color(0xFF00A884)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Text("Crear publicación", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                } else if (feedUiState.isLoading && feedUiState.posts.isEmpty()) {
                    items(3, key = { "loading_post_$it" }) {
                        FeedPostSkeleton()
                    }
                }
            }
        }

        // Floating Action Button to create a post on El Muro
        FloatingActionButton(
            onClick = { showCreatePostSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp, end = 16.dp),
            containerColor = Color(0xFF00E5FF),
            contentColor = Color.Black
        ) {
            Icon(Icons.Default.Add, contentDescription = "Crear Publicación")
        }
    }

    if (showCreatePostSheet) {
        com.example.ui.screen.CreatePostBottomSheet(
            onDismiss = {
                showCreatePostSheet = false
                feedViewModel.refreshFeed()
            }
        )
    }

    if (showCommentsSheet && activeCommentStateId != null) {
        val commentsList by statesViewModel.currentComments.collectAsStateWithLifecycle()
        val keyboardController = LocalSoftwareKeyboardController.current
        
        LaunchedEffect(activeCommentStateId) {
            statesViewModel.loadComments(activeCommentStateId!!)
        }

        ModalBottomSheet(
            onDismissRequest = {
                showCommentsSheet = false
                activeCommentStateId = null
            },
            sheetState = sheetState,
            containerColor = Color(0xFF161618)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f) 
                    .padding(horizontal = 16.dp)
                    .imePadding() // Ensures content is pushed up by keyboard
            ) {
                Text(
                    "Comentarios",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Comments List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (commentsList.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No hay comentarios aún. Sé el primero.", color = Color.Gray, fontSize = 14.sp)
                                }
                            }
                        }
                    } else {
                        itemsIndexed(commentsList, key = { _, comment -> comment.id }) { _, comment ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1F2C34).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                com.example.ui.components.PanaAvatar(
                                    avatarUrl = comment.avatarUrl,
                                    userId = comment.userId,
                                    size = 32.dp,
                                    borderWidth = 0.dp,
                                    placeholderName = comment.authorName
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(comment.authorName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = remember(comment.createdAt) {
                                                try {
                                                    val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                                                    parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                                    val date = parser.parse(comment.createdAt)
                                                    val diff = System.currentTimeMillis() - (date?.time ?: System.currentTimeMillis())
                                                    val minutes = (diff / 60000).toInt()
                                                    when {
                                                        minutes < 1 -> "hace un momento"
                                                        minutes < 60 -> "hace ${minutes}m"
                                                        minutes < 1440 -> "hace ${minutes / 60}h"
                                                        else -> "hace ${minutes / 1440}d"
                                                    }
                                                } catch (e: Exception) { "hace poco" }
                                            },
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(comment.text, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                // Comment Input
                var commentText by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = { Text("Añade un comentario...", color = Color.Gray) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF262629),
                            unfocusedContainerColor = Color(0xFF262629),
                            focusedBorderColor = Color(0xFF00FF85),
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (commentText.isNotBlank()) {
                                statesViewModel.addComment(activeCommentStateId!!, commentText, onError = { err ->
                                    android.widget.Toast.makeText(context, "Error: $err", android.widget.Toast.LENGTH_LONG).show()
                                })
                                commentText = ""
                                keyboardController?.hide()
                            }
                        },
                        modifier = Modifier.size(48.dp).background(Color(0xFFB026FF), CircleShape)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Enviar", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }

    selectedPostForComments?.let { post ->
        com.example.ui.screen.FeedCommentsBottomSheet(
            postId = post.id ?: "",
            onDismiss = { selectedPostForComments = null },
            viewModel = feedViewModel
        )
    }

    if (editingPostId != null) {
        AlertDialog(
            onDismissRequest = { editingPostId = null },
            title = { Text("Editar publicación", color = Color.White) },
            text = {
                TextField(
                    value = editingPostContent,
                    onValueChange = { editingPostContent = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    editingPostId?.let { feedViewModel.updatePost(it, editingPostContent) }
                    editingPostId = null
                }) {
                    Text("Guardar", color = Color(0xFF00E5FF))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingPostId = null }) {
                    Text("Cancelar", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E222B)
        )
    }

    if (postToDeleteId != null) {
        AlertDialog(
            onDismissRequest = { postToDeleteId = null },
            title = { Text("Eliminar publicación", color = Color.White) },
            text = { Text("¿Estás seguro de que quieres eliminar esta publicación? Esta acción no se puede deshacer.", color = Color.LightGray) },
            confirmButton = {
                TextButton(onClick = {
                    postToDeleteId?.let { feedViewModel.deletePost(it) }
                    postToDeleteId = null
                }) {
                    Text("Eliminar", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { postToDeleteId = null }) {
                    Text("Cancelar", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E222B)
        )
    }

    // --- FULL SCREEN MEDIA VIEWER (Facebook-style: pure black, zoomable photos, video controls) ---
    if (fullScreenMediaList != null) {
        val mediaList = fullScreenMediaList!!
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(
            initialPage = fullScreenInitialPage,
            pageCount = { mediaList.size }
        )

        // Background audio player for photos with audio
        val context = LocalContext.current
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
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val mediaUrl = mediaList[page]
                val resolvedViewerUrl = com.example.ui.components.rememberResolvedMediaUrl(mediaUrl)
                val isVideo = com.example.ui.components.isVideoUrl(resolvedViewerUrl)
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
                            com.example.core.media.ExoPlayerManager.releasePlayer(player)
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
                        onClick = { backgroundAudioMuted = !backgroundAudioMuted; backgroundAudioPlayer?.volume = if (backgroundAudioMuted) 0f else 1f },
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
                            android.widget.Toast.makeText(context, "Descarga iniciada... 📥", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Descargar",
                        tint = Color.White
                    )
                }
            }
        }
    }

    // --- SEQUENTIAL AUDIO PLAYLIST PLAYER MODAL ---
    if (activePlaylistPost != null) {
        val post = activePlaylistPost!!
        val audiosList = remember(post) { 
            (post.mediaUrls ?: emptyList()).filter { it.isNotBlank() && com.example.ui.components.isAudioUrl(it) } 
        }
        
        var currentAudioIndex by remember { mutableIntStateOf(0) }
        var isPlaying by remember { mutableStateOf(false) }
        var playbackPosition by remember { mutableLongStateOf(0L) }
        var audioDuration by remember { mutableLongStateOf(0L) }
        
        val exoPlayer = remember(context) { androidx.media3.exoplayer.ExoPlayer.Builder(context).build() }
        
        DisposableEffect(exoPlayer) {
            val listener = object : androidx.media3.common.Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == androidx.media3.common.Player.STATE_READY) {
                        audioDuration = exoPlayer.duration
                    } else if (state == androidx.media3.common.Player.STATE_ENDED) {
                        if (currentAudioIndex + 1 < audiosList.size) {
                            currentAudioIndex += 1
                        } else {
                            isPlaying = false
                        }
                    }
                }
            }
            exoPlayer.addListener(listener)
            onDispose {
                exoPlayer.removeListener(listener)
                exoPlayer.release()
            }
        }
        
        LaunchedEffect(currentAudioIndex, audiosList) {
            if (audiosList.isNotEmpty()) {
                val url = audiosList[currentAudioIndex]
                val mediaItem = androidx.media3.common.MediaItem.fromUri(Uri.parse(url))
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = isPlaying
            }
        }
        
        LaunchedEffect(isPlaying, currentAudioIndex) {
            while (isPlaying) {
                playbackPosition = exoPlayer.currentPosition
                kotlinx.coroutines.delay(500)
            }
        }
        
        ModalBottomSheet(
            onDismissRequest = { 
                exoPlayer.stop()
                activePlaylistPost = null 
            },
            containerColor = Color(0xFF0F172A),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reproductor de Audios (${audiosList.size})",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { 
                        exoPlayer.stop()
                        activePlaylistPost = null 
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(audiosList, key = { _, url -> "audio_${url.hashCode()}" }) { index, url ->
                        val isCurrent = index == currentAudioIndex
                        val itemBgColor = if (isCurrent) Color(0xFF1E293B) else Color(0xFF1E1E24)
                        val itemBorderColor = if (isCurrent) Color(0xFF00E5FF) else Color.Transparent
                        
                        Card(
                            onClick = {
                                currentAudioIndex = index
                                isPlaying = true
                                exoPlayer.play()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = itemBgColor),
                            border = BorderStroke(1.dp, itemBorderColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isCurrent && isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                        contentDescription = null,
                                        tint = if (isCurrent) Color(0xFF00E5FF) else Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Audio ${index + 1}",
                                            color = Color.White,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Panalink Audio File",
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                
                                IconButton(
                                    onClick = {
                                        try {
                                            val uri = Uri.parse(url)
                                            val request = android.app.DownloadManager.Request(uri).apply {
                                                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                val fileName = url.substringAfterLast("/")
                                                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
                                                setTitle("Descargando Audio ${index + 1}")
                                                setDescription(fileName)
                                            }
                                            val manager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                            manager.enqueue(request)
                                            android.widget.Toast.makeText(context, "Descarga iniciada para Audio ${index + 1} 📥", android.widget.Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = "Descargar",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Reproduciendo: Audio ${currentAudioIndex + 1}",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val progress = if (audioDuration > 0) playbackPosition.toFloat() / audioDuration.toFloat() else 0f
                        Slider(
                            value = progress,
                            onValueChange = { 
                                val pos = (it * audioDuration).toLong()
                                exoPlayer.seekTo(pos)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E5FF),
                                activeTrackColor = Color(0xFF00E5FF),
                                inactiveTrackColor = Color.Gray
                            )
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val curMin = (playbackPosition / 1000) / 60
                            val curSec = (playbackPosition / 1000) % 60
                            val durMin = (audioDuration / 1000) / 60
                            val durSec = (audioDuration / 1000) % 60
                            Text(String.format("%02d:%02d", curMin, curSec), color = Color.Gray, fontSize = 11.sp)
                            Text(String.format("%02d:%02d", durMin, durSec), color = Color.Gray, fontSize = 11.sp)
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = {
                                    if (currentAudioIndex > 0) {
                                        currentAudioIndex -= 1
                                    }
                                },
                                enabled = currentAudioIndex > 0
                            ) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "Anterior", tint = if (currentAudioIndex > 0) Color.White else Color.Gray, modifier = Modifier.size(36.dp))
                            }
                            
                            Spacer(modifier = Modifier.width(24.dp))
                            
                            IconButton(
                                onClick = {
                                    if (isPlaying) {
                                        exoPlayer.pause()
                                    } else {
                                        exoPlayer.play()
                                    }
                                    isPlaying = !isPlaying
                                },
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color(0xFF00E5FF), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.Black,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(24.dp))
                            
                            IconButton(
                                onClick = {
                                    if (currentAudioIndex + 1 < audiosList.size) {
                                        currentAudioIndex += 1
                                    }
                                },
                                enabled = currentAudioIndex + 1 < audiosList.size
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Siguiente", tint = if (currentAudioIndex + 1 < audiosList.size) Color.White else Color.Gray, modifier = Modifier.size(36.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reproductor de video para el visor de pantalla completa del muro (estilo Facebook).
 * Autoplay con controles de toque: play/pause, seek y mute. Se libera al salir de la pagina.
 */
@Composable
internal fun FeedFullscreenVideoPlayer(
    videoUrl: String,
    isActivePage: Boolean,
    startPosition: Long = 0L
) {
    val context = LocalContext.current
    // Pooled player: swiping through fullscreen media avoids codec init/teardown.
    if (videoUrl.isBlank()) return
    val exoPlayer = remember(videoUrl) {
        com.example.core.media.ExoPlayerManager.getPlayer(context).apply {
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
            setMediaItem(androidx.media3.common.MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true
        }
    }
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var hasSeekedToStart by remember { mutableStateOf(false) }

    LaunchedEffect(isActivePage) {
        exoPlayer.playWhenReady = isActivePage
    }

    // Seek to start position when player becomes ready
    LaunchedEffect(exoPlayer, isActivePage, startPosition) {
        if (isActivePage && startPosition > 0 && !hasSeekedToStart) {
            val player = exoPlayer
            // Wait for player to be ready
            while (player.playbackState != androidx.media3.common.Player.STATE_READY) {
                kotlinx.coroutines.delay(50)
            }
            player.seekTo(startPosition)
            hasSeekedToStart = true
        } else if (!isActivePage) {
            hasSeekedToStart = false
        }
    }

    LaunchedEffect(showControls, isActivePage) {
        while (isActivePage) {
            position = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            if (showControls) {
                kotlinx.coroutines.delay(2200)
                showControls = false
            } else {
                kotlinx.coroutines.delay(250)
            }
        }
    }

    DisposableEffect(videoUrl, exoPlayer) {
        onDispose { com.example.core.media.ExoPlayerManager.releasePlayer(exoPlayer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable {
                showControls = !showControls
                if (!showControls && !isPlaying) {
                    exoPlayer.play()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (showControls) {
            // Play / Pause central
            IconButton(
                onClick = {
                    isPlaying = !isPlaying
                    if (isPlaying) exoPlayer.play() else exoPlayer.pause()
                    showControls = true
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Barra inferior: progreso + tiempos
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                fun fmt(ms: Long): String {
                    val totalSec = (ms / 1000).coerceAtLeast(0)
                    return "%d:%02d".format(totalSec / 60, totalSec % 60)
                }
                Text(text = fmt(position), color = Color.White, fontSize = 12.sp)
                Slider(
                    value = if (duration > 0) position.toFloat() / duration.toFloat() else 0f,
                    onValueChange = { frac ->
                        if (duration > 0) {
                            val target = (frac * duration).toLong()
                            exoPlayer.seekTo(target)
                            position = target
                        }
                        showControls = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                Text(text = fmt(duration), color = Color.White, fontSize = 12.sp)
            }
        }
    }
}
