package com.example

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.supabase.SupabaseClient
import com.example.ui.screen.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.navigation.AuthNavHost
import com.example.ui.navigation.MainNavHost
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel
import com.example.feature.chat.presentation.ChatViewModel
import com.example.ui.viewmodel.ChatsViewModel
import com.example.ui.viewmodel.ProfileViewModel
import com.example.ui.viewmodel.StatesViewModel
import androidx.lifecycle.lifecycleScope
import com.example.data.supabase.SessionManager
import com.example.media.audio.AudioRepository
import com.example.media.playlist.PlaylistRepository
import com.example.media.sync.MusicPlaylistRealtimeManager
import com.example.media.worker.MusicSocialSyncWorker
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

@UnstableApi
class MainActivity : androidx.fragment.app.FragmentActivity() {
    private val currentIntentState = androidx.compose.runtime.mutableStateOf<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntentState.value = intent
        android.util.Log.d("MainActivity", "DEEPLINK_RECEIVED")
        android.util.Log.d("MainActivity", "DEEPLINK_URI = ${intent.data}")
    }

    override fun onResume() {
        super.onResume()
        // Restaura system bars/insets globalmente al volver al frente: previene
        // pantallas negras residuales que dejan los fullscreen Dialogs (decorFitsSystemWindows=false
        // o insets ocultos) tras atras repetido en cualquier seccion.

        try {
            val w = this.window
            if (w != null) {
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, true)
                val ctrl = androidx.core.view.WindowCompat.getInsetsController(w, w.decorView)
                ctrl.show(androidx.core.view.WindowInsetsCompat.Type.statusBars() or
                        androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            }
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Error restoring system bars on resume", e)
        }

        // Si no estamos REALMENTE en PiP, limpiar el flag stale: previene el
        // Box negro "Cargando video..." que cubre toda la app cuando el sistema
        // cierro el PiP sin llamar onPictureInPictureModeChanged(false) o el
        // player fue liberado mientras el flag quedo true..

        if (!isInPictureInPictureMode) {
            com.example.util.AppFloatingPlayerManager.isInNativePip = false
        }
        lifecycleScope.launch {
            try {
                SessionManager.validateAndRefreshSessionIfNeeded()
                SessionManager.triggerSync()
            } catch (e: Throwable) {
                android.util.Log.e("MainActivity", "Error during onResume session sync", e)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            com.example.security.AppLockManager.onAppForegrounded(this)
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "AppLock foreground check failed", e)
        }
    }

    override fun onStop() {
        try {
            com.example.security.AppLockManager.onAppBackgrounded()
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "AppLock background hook failed", e)
        }
        super.onStop()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val prefs = getSharedPreferences("panalink_prefs", android.content.Context.MODE_PRIVATE)
        val isPipEnabled = prefs.getBoolean("floating_pip_enabled", true)
        if (!isPipEnabled) return

        val manager = com.example.util.AppFloatingPlayerManager
        if (manager.exoPlayer != null && (manager.activeType == "reel" || manager.activeType == "panatv")) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val params = android.app.PictureInPictureParams.Builder()
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        com.example.util.AppFloatingPlayerManager.isInNativePip = isInPictureInPictureMode
    }

    @kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        try {
            com.example.security.AppLockManager.onAppLaunched(this)
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "AppLock launch check failed", e)
        }
        
        val authViewModel = androidx.lifecycle.ViewModelProvider(this)[com.example.ui.viewmodel.AuthViewModel::class.java]
        splashScreen.setKeepOnScreenCondition {
            val state = authViewModel.uiState.value
            state is com.example.ui.viewmodel.AuthUiState.Idle || state is com.example.ui.viewmodel.AuthUiState.Loading
        }
        
        currentIntentState.value = intent
        android.util.Log.d("MainActivity", "DEEPLINK_RECEIVED")
        android.util.Log.d("MainActivity", "DEEPLINK_URI = ${intent?.data}")
        
        // Register all separated Notification Channels (Messages, Calls, System, Alerts)
        try {
            com.example.service.NotificationHelper.createNotificationChannels(this)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error creating notification channels", e)
        }
        
        // Initialize dynamic ThemeManager from preferences on start
        try {
            val prefs = getSharedPreferences("panalink_prefs", android.content.Context.MODE_PRIVATE)
            val savedTheme = prefs.getString("profile_theme_global", "halo_dark") ?: "halo_dark"
            com.example.ui.theme.ThemeManager.themeKey.value = savedTheme

            val savedThemeMode = prefs.getString("theme_mode_global", "system") ?: "system"
            com.example.ui.theme.ThemeManager.themeMode.value = savedThemeMode

            val isMinimal = prefs.getBoolean("minimalist_mode_global", false)
            com.example.ui.theme.ThemeManager.isMinimalistMode.value = isMinimal

            val savedColorPreset = prefs.getString("bottom_bar_color_preset", "tropical") ?: "tropical"
            com.example.ui.theme.ThemeManager.bottomBarColorPreset.value = savedColorPreset

            val savedShapePreset = prefs.getString("bottom_bar_shape_preset", "pill") ?: "pill"
            com.example.ui.theme.ThemeManager.bottomBarShapePreset.value = savedShapePreset

            val customP = prefs.getInt("custom_primary", 0xFF00E5FF.toInt())
            val customB = prefs.getInt("custom_background", 0xFF000000.toInt())
            val customAc = prefs.getInt("custom_accent", 0xFF8B5CF6.toInt())
            val customS = prefs.getInt("custom_surface", 0xFF121212.toInt())
            val customSec = prefs.getInt("custom_secondary", 0xFF161618.toInt())

            com.example.ui.theme.ThemeManager.customPrimary.value = androidx.compose.ui.graphics.Color(customP)
            com.example.ui.theme.ThemeManager.customBackground.value = androidx.compose.ui.graphics.Color(customB)
            com.example.ui.theme.ThemeManager.customAccent.value = androidx.compose.ui.graphics.Color(customAc)
            com.example.ui.theme.ThemeManager.customSurface.value = androidx.compose.ui.graphics.Color(customS)
            com.example.ui.theme.ThemeManager.customSecondary.value = androidx.compose.ui.graphics.Color(customSec)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to load custom theme preferences", e)
        }

        enableEdgeToEdge()
        setContent {
            val isAppLocked by com.example.security.AppLockManager.isLocked.collectAsState()
            val baseThemeKey by com.example.ui.theme.ThemeManager.themeKey.collectAsState()
            val globalThemeMode by com.example.ui.theme.ThemeManager.themeMode.collectAsState()
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val activeThemeKey = com.example.ui.theme.resolveThemeForMode(baseThemeKey, globalThemeMode, systemDark)
            val customPrimary by com.example.ui.theme.ThemeManager.customPrimary.collectAsState()
            val customBackground by com.example.ui.theme.ThemeManager.customBackground.collectAsState()
            val customAccent by com.example.ui.theme.ThemeManager.customAccent.collectAsState()
            val customSurface by com.example.ui.theme.ThemeManager.customSurface.collectAsState()
            val customSecondary by com.example.ui.theme.ThemeManager.customSecondary.collectAsState()

            val customColors = androidx.compose.runtime.remember(customPrimary, customBackground, customAccent, customSurface, customSecondary) {
                com.example.ui.theme.AppColors(
                    primary = customPrimary,
                    secondary = customSecondary,
                    background = customBackground,
                    surface = customSurface,
                    bubbleMe = customPrimary,
                    bubbleOther = customSurface,
                    topBar = customSecondary,
                    bottomBar = customSecondary,
                    accent = customAccent,
                    isDark = activeThemeKey != "minimal_white" && activeThemeKey != "halo_light" && activeThemeKey != "whatsapp_light",
                    onPrimary = Color.White,
                    onSecondary = Color.Black,
                    onBackground = Color.Black,
                    onSurface = Color.Black
                )
            }

            MyApplicationTheme(themeKey = activeThemeKey, customColors = customColors) {
                // Instantiate central ViewModels
                val authViewModel: AuthViewModel = viewModel()
                val chatsViewModel: ChatsViewModel = viewModel()
                val chatViewModel: ChatViewModel = viewModel()
                val statesViewModel: StatesViewModel = viewModel()
                val profileViewModel: ProfileViewModel = viewModel()
                val notificationsViewModel: com.example.ui.viewmodel.NotificationsViewModel = viewModel()
                val playerViewModel: com.example.media.player.ui.PlayerViewModel = viewModel()
                
                val currentContext = androidx.compose.ui.platform.LocalContext.current
                androidx.compose.runtime.DisposableEffect(Unit) {
                    val receiver = object : android.content.BroadcastReceiver() {
                        override fun onReceive(c: android.content.Context?, intent: android.content.Intent?) {
                            statesViewModel.loadActiveStates(showLoading = false)
                        }
                    }
                    val filter = android.content.IntentFilter("com.example.REEL_UPLOADED")
                    ContextCompat.registerReceiver(
                        currentContext,
                        receiver,
                        filter,
                        ContextCompat.RECEIVER_NOT_EXPORTED
                    )
                    onDispose {
                        currentContext.unregisterReceiver(receiver)
                    }
                }

                val authUiState by authViewModel.uiState.collectAsState()
                val context = androidx.compose.ui.platform.LocalContext.current

                LaunchedEffect(currentIntentState.value) {
                    val intent = currentIntentState.value
                    if (intent != null) {
                        authViewModel.handleDeepLinkIntent(intent)
                    }
                }

                val callManager = remember { com.example.call.CallManager.getInstance(context) }
                val callState by callManager.callState.collectAsState()
                val callType by callManager.callType.collectAsState()
                val opponentName by callManager.opponentName.collectAsState()
                val opponentId by callManager.opponentId.collectAsState()
                val isMuted by callManager.isMuted.collectAsState()
                val isSpeakerOn by callManager.isSpeakerOn.collectAsState()
                val isCameraOn by callManager.isCameraOn.collectAsState()
                // The call duration MUST be collected as state, otherwise the
                // timer ticks in CallManager but the call screen never recomposes
                // to show the running minutes (it only refreshed on callState
                // changes). Derive the formatted "mm:ss" string from it.
                val callDuration by callManager.duration.collectAsState()
                val formattedDuration = remember(callDuration) {
                    val minutes = java.util.concurrent.TimeUnit.SECONDS.toMinutes(callDuration)
                    val seconds = callDuration - java.util.concurrent.TimeUnit.MINUTES.toSeconds(minutes)
                    String.format("%02d:%02d", minutes, seconds)
                }

                val callPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val recordAudioGranted = permissions[android.Manifest.permission.RECORD_AUDIO] ?: false
                    val cameraGranted = permissions[android.Manifest.permission.CAMERA] ?: false
                    android.util.Log.d("MainActivity", "Call permissions requested. Mic: $recordAudioGranted, Cam: $cameraGranted")
                }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        android.util.Log.d("MainActivity", "Notification permission granted")
                    }
                }

                // ── OTA: chequeo automatico de actualizaciones cada 10 min ──
                val updateViewModel: com.example.update.UpdateViewModel = viewModel()
                val updateStatus by updateViewModel.updateStatus.collectAsState()
                var showUpdateDialog by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    updateViewModel.checkForUpdates(force = true)
                    while (true) {
                        kotlinx.coroutines.delay(10 * 60 * 1000L)
                        updateViewModel.checkForUpdates(force = true)
                    }
                }

                LaunchedEffect(updateStatus) {
                    if (updateStatus == com.example.update.UpdateStatus.UPDATE_AVAILABLE ||
                        updateStatus == com.example.update.UpdateStatus.MANDATORY_UPDATE) {
                        showUpdateDialog = true
                    }
                }

                if (showUpdateDialog) {
                    com.example.update.UpdateDialog(
                        viewModel = updateViewModel,
                        onDismiss = { showUpdateDialog = false }
                    )
                }

                // Listen to Auth State logouts/logins globally to control top-level services
                LaunchedEffect(authUiState, com.example.data.supabase.SupabaseClient.currentProfile) {
                    val state = authUiState
                    val profile = com.example.data.supabase.SupabaseClient.currentProfile
                    
                    val isComplete = profile?.isProfileComplete == true

                    if (state is AuthUiState.Idle) {
                        try {
                            callManager.release()
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Failed to release CallManager", e)
                        }
                        try {
                            val serviceIntent = Intent(context, com.example.service.PanalinkRealtimeService::class.java)
                            context.stopService(serviceIntent)
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Failed to stop service", e)
                        }
                        com.example.util.PanalinkInitializationManager.reset()
                    } else if ((state is AuthUiState.Success || state is AuthUiState.AuthenticatedReady || state is AuthUiState.AuthenticatedIncomplete || state is AuthUiState.Authenticated || state is AuthUiState.NeedsProfileSetup) && profile != null && isComplete) {
                        // Initialize all core Panalink background services safely for complete profile users
                        com.example.util.PanalinkInitializationManager.initializeCompleteUser(
                            context = context,
                            profile = profile,
                            scope = lifecycleScope
                        )

                        // P6.7.6B - Initialize Music Social Sync
                        lifecycleScope.launch {
                            MusicSocialSyncWorker.schedulePeriodicSync(context)
                            MusicSocialSyncWorker.runOnce(context)
                        }

                        // Request POST_NOTIFICATIONS permission cleanly for Android 13+
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                var isSplashActive by remember { mutableStateOf(true) }
                var lastUserId by remember { mutableStateOf<String?>(null) }
                var didInitialAuthSync by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    delay(2000)
                    isSplashActive = false
                }

                LaunchedEffect(authUiState, com.example.data.supabase.SupabaseClient.currentProfile) {
                    val isAuthenticated = authUiState is AuthUiState.Success ||
                            authUiState is AuthUiState.AuthenticatedReady ||
                            authUiState is AuthUiState.AuthenticatedIncomplete ||
                            authUiState is AuthUiState.Authenticated ||
                            authUiState is AuthUiState.NeedsProfileSetup
                    val profile = com.example.data.supabase.SupabaseClient.currentProfile
                    val isComplete = profile?.isProfileComplete == true
                    
                    if (isAuthenticated && profile != null && isComplete) {
                        val userId = profile.id
                        if (lastUserId != userId) {
                            lastUserId = userId
                        }
                        if (!didInitialAuthSync) {
                            didInitialAuthSync = true
                            // Sync inicial silenciosa en background (sin splash adicional):
                            // el splash solo exists para el cold-start. Al relanzar el proceso,
                            // la carga inicial ya no bloquea la entrada a la interfaz.

                            chatsViewModel.loadChats(forceRefresh = true)
                            statesViewModel.loadActiveStates(showLoading = false)
                        }
                    } else {
                        // No resetear lastUserId en estados transitorios: previene re-splash
                    }
                }

                LaunchedEffect(callState) {
                    if (callState is com.example.call.CallState.RINGING || 
                        callState is com.example.call.CallState.OUTGOING) {
                        callPermissionLauncher.launch(
                            arrayOf(
                                android.Manifest.permission.RECORD_AUDIO,
                                android.Manifest.permission.CAMERA
                            )
                        )
                    }
                }

                // Determine active navigation structure from AuthUiState, ignoring transient states like Loading, Error, and Idle (unless starting up)
                val initialFlow = remember {
                    val user = com.example.data.supabase.SupabaseClient.currentUser
                    val profile = com.example.data.supabase.SupabaseClient.currentProfile
                    if (user != null) {
                        if (profile != null && profile.isProfileComplete) {
                            AuthUiState.Authenticated(user, profile)
                        } else if (profile != null) {
                            AuthUiState.NeedsProfileSetup(user, profile)
                        } else {
                            AuthUiState.LoggedOut
                        }
                    } else {
                        AuthUiState.LoggedOut
                    }
                }
                var currentFlow by remember { mutableStateOf<AuthUiState>(initialFlow) }

                LaunchedEffect(authUiState) {
                    when (val newState = authUiState) {
                        is AuthUiState.LoggedOut -> {
                            currentFlow = AuthUiState.LoggedOut
                        }
                        is AuthUiState.NeedsEmailVerification -> {
                            currentFlow = newState
                        }
                        is AuthUiState.NeedsVerification -> {
                            currentFlow = AuthUiState.NeedsEmailVerification(newState.email)
                        }
                        is AuthUiState.NeedsProfileSetup -> {
                            currentFlow = newState
                        }
                        is AuthUiState.Authenticated -> {
                            currentFlow = newState
                        }
                        is AuthUiState.Success -> {
                            val user = newState.user
                            val profile = com.example.data.supabase.SupabaseClient.currentProfile
                            val isComplete = profile?.isProfileComplete == true
                            
                            currentFlow = if (profile != null && isComplete) {
                                AuthUiState.Authenticated(user, profile)
                            } else if (profile != null) {
                                AuthUiState.NeedsProfileSetup(user, profile)
                            } else {
                                AuthUiState.NeedsProfileSetup(user, com.example.data.model.Profile(id = user.id, displayName = user.email?.substringBefore("@") ?: "", avatarUrl = null, isProfileComplete = false))
                            }
                        }
                        is AuthUiState.AuthenticatedReady -> {
                            currentFlow = AuthUiState.Authenticated(newState.user, newState.profile)
                        }
                        is AuthUiState.AuthenticatedIncomplete -> {
                            val user = newState.user
                            val profile = newState.profile
                            val isComplete = profile?.isProfileComplete == true
                            
                            currentFlow = if (profile != null && isComplete) {
                                AuthUiState.Authenticated(user, profile)
                            } else if (profile != null) {
                                AuthUiState.NeedsProfileSetup(user, profile)
                            } else {
                                AuthUiState.NeedsProfileSetup(user, com.example.data.model.Profile(id = user.id, displayName = user.email?.substringBefore("@") ?: "", avatarUrl = null, isProfileComplete = false))
                            }
                        }
                        // Loading, Error, and Idle do not change the active root layout tree!
                        is AuthUiState.Loading, is AuthUiState.Error, is AuthUiState.Idle -> {
                            // Keep previous flow
                        }
                    }
                }

                val isUserAuthenticated = remember(currentFlow) {
                    currentFlow is AuthUiState.Authenticated || 
                    currentFlow is AuthUiState.NeedsProfileSetup
                }

                var isPlayerFullVisible by remember { mutableStateOf(false) }
                val playerState by playerViewModel.playerState.collectAsState()

                if (isInPictureInPictureMode && com.example.util.AppFloatingPlayerManager.isInNativePip) {
                    val player = com.example.util.AppFloatingPlayerManager.exoPlayer
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (player != null) {
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { ctx ->
                                    androidx.media3.ui.PlayerView(ctx).apply {
                                        useController = false
                                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        this.player = player
                                    }
                                },
                                update = { playerView ->
                                    playerView.player = player
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text("Cargando video...", color = Color.White, fontSize = 14.sp)
                        }
                    }
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            if (isUserAuthenticated && playerState.currentTrack != null) {
                                com.example.media.player.ui.MiniPlayerBar(
                                    track = playerState.currentTrack,
                                    isPlaying = playerState.isPlaying,
                                    progress = if (playerState.durationMs > 0) playerState.currentPositionMs.toFloat() / playerState.durationMs else 0f,
                                    onTogglePlayPause = { playerViewModel.togglePlayPause() },
                                    onNext = { playerViewModel.nextTrack() },
                                    onClick = { isPlayerFullVisible = true }
                                )
                            }
                        }
                    ) { innerPadding ->
                    if (isSplashActive) {
                        SplashScreen()
                    } else {
                        if (isUserAuthenticated) {
                            if (callState != com.example.call.CallState.IDLE) {
                                val opponentNameStr = opponentName ?: ""
                                val videoViewModel: com.example.call.VideoCallViewModel? = if (callType == com.example.call.CallType.VIDEO) {
                                    androidx.lifecycle.viewmodel.compose.viewModel()
                                } else {
                                    null
                                }
                                
                                com.example.ui.call.CallScreen(
                                    opponentId = opponentId,
                                    opponentName = opponentNameStr,
                                    callState = callState,
                                    callType = callType,
                                    formattedDuration = formattedDuration,
                                    isMuted = isMuted,
                                    isSpeakerOn = isSpeakerOn,
                                    isCameraOn = isCameraOn,
                                    videoViewModel = videoViewModel,
                                    onAcceptCall = { callManager.acceptCall() },
                                    onRejectCall = { callManager.rejectCall() },
                                    onEndCall = { callManager.endCall() },
                                    onMuteToggle = { callManager.toggleMute() },
                                    onSpeakerToggle = { callManager.toggleSpeaker() },
                                    onCameraToggle = { callManager.toggleCamera() },
                                    onSwitchCamera = { callManager.switchCamera() },
                                    onDismissError = { callManager.endCall() }
                                )
                            } else {
                                if (currentFlow is AuthUiState.NeedsProfileSetup) {
                                    com.example.ui.screen.onboarding.OnboardingNavHost(
                                        onOnboardingComplete = {
                                            authViewModel.onOnboardingComplete()
                                        }
                                    )
                                } else {
                                // ----------------------------------------------------
                                // MAIN FLOW: Completely isolated from Auth backstack
                                // ----------------------------------------------------
                                MainNavHost(
                                    chatsViewModel = chatsViewModel,
                                    chatViewModel = chatViewModel,
                                    statesViewModel = statesViewModel,
                                    profileViewModel = profileViewModel,
                                    notificationsViewModel = notificationsViewModel,
                                    playerViewModel = playerViewModel,
                                    authViewModel = authViewModel,
                                    currentIntentState = currentIntentState,
                                    callManager = callManager,
                                    isPlayerFullVisible = isPlayerFullVisible,
                                    onPlayerFullClose = { isPlayerFullVisible = false }
                                )
                    }
                    }
                } else {
                        // ----------------------------------------------------
                        // AUTHENTICATION FLOW: Isolated welcome/login/register/verification
                        // ----------------------------------------------------
                        AuthNavHost(
                            authViewModel = authViewModel,
                            currentFlow = currentFlow,
                            modifier = Modifier
                        )
                    }
                }
                }
                }
            }
        
            // App Lock overlay: drawn last so it covers the whole UI when locked.
            if (isAppLocked) {
                com.example.ui.security.LockScreen()
            }
}
    }
}