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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.example.ui.theme.PanalinkPalette
import com.example.ui.settings.ios.IosSettingsColors
import com.example.ui.settings.ios.IosFont
import com.example.ui.settings.ios.IosGroup
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.PhoneMissed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh

@OptIn(ExperimentalMaterial3Api::class)


@Composable
fun LlamadasTabContent(
    contactsState: ContactsUiState,
    onRefresh: () -> Unit,
    onNavigateToChat: (String, String) -> Unit = { _, _ -> }
) {
    val colors = com.example.ui.theme.LocalAppColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    val callManager = remember { com.example.call.CallManager.getInstance(context) }
    class PendingCall(val targetUserId: String, val targetUserName: String, val type: com.example.call.CallType)
    var pendingCall by remember { mutableStateOf<PendingCall?>(null) }
    val callPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[android.Manifest.permission.RECORD_AUDIO] ?: false
        val cameraGranted = permissions[android.Manifest.permission.CAMERA] ?: false
        val call = pendingCall
        if (call != null) {
            val neededCamera = call.type == com.example.call.CallType.VIDEO
            val granted = if (neededCamera) audioGranted && cameraGranted else audioGranted
            if (granted) {
                callManager.startCall(call.targetUserId, call.targetUserName, call.type)
            } else {
                android.widget.Toast.makeText(context, "Permiso denegado para la llamada", android.widget.Toast.LENGTH_LONG).show()
            }
            pendingCall = null
        }
    }
    val tryStartCall: (String, String, com.example.call.CallType) -> Unit = { targetUserId, targetUserName, type ->
        val hasAudio = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCamera = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val needsCamera = type == com.example.call.CallType.VIDEO
        val hasAll = if (needsCamera) hasAudio && hasCamera else hasAudio
        if (hasAll) {
            callManager.startCall(targetUserId, targetUserName, type)
        } else {
            pendingCall = PendingCall(targetUserId, targetUserName, type)
            val perms = if (needsCamera) arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.CAMERA) else arrayOf(android.Manifest.permission.RECORD_AUDIO)
            callPermissionLauncher.launch(perms)
        }
    }
    val isConnected by callManager.isConnected.collectAsState()
    val callState by callManager.callState.collectAsState()
    val liveKitEnabled = remember { com.example.data.repository.LiveKitFeatureGate.isEnabled() }
    val presenceMap by com.example.data.repository.PresenceRepository.presenceMap.collectAsStateWithLifecycle()
    val currentProfile by com.example.data.supabase.SupabaseClient.currentProfileState.collectAsState()

    // Real call history from Room (messages with messageType="call")
    val messagesRepo = remember { com.example.data.repository.MessagesRepository.getInstance() }
    val callHistory by messagesRepo.observeCallHistory().collectAsState(initial = emptyList())
    val publicProfileRepo = remember { com.example.data.repository.PublicProfileRepository.getInstance() }

    // Resolve profile info (avatar, name) for the call history peers
    val callPeerIds = remember(callHistory, currentProfile) {
        val myId = currentProfile?.id ?: ""
        callHistory.flatMap { listOf(it.callerId, it.receiverId) }
            .filter { it.isNotEmpty() && it != myId }
            .distinct()
    }
    val peerProfiles by androidx.compose.runtime.produceState(
        initialValue = emptyMap<String, com.example.data.model.PublicProfile>(),
        callPeerIds
    ) {
        if (callPeerIds.isNotEmpty()) {
            val result = publicProfileRepo.getPublicProfiles(callPeerIds)
            if (result is com.example.data.repository.PublicProfileFetchResult.Success) {
                value = result.data.mapNotNull { (id, v) ->
                    (v as? com.example.data.repository.PublicProfileFetchResult.Success)?.data?.let { id to it }
                }.toMap()
            }
        }
    }

    // Ensure signaling is active and tries to reconnect if disconnected when screen is shown.
    LaunchedEffect(currentProfile, isConnected) {
        if (currentProfile != null && currentProfile?.isProfileComplete == true && !isConnected) {
            android.util.Log.d("ChatsListScreen", "Signaling disconnected, attempting auto-reconnect...")
            callManager.initialize(currentProfile?.id ?: "")
        }
    }

    var showClearHistoryDialog by remember { mutableStateOf(false) }

    PanalinkPullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                onRefresh()
                kotlinx.coroutines.delay(1200)
                isRefreshing = false
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            IosGroup(
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                if (!isConnected) {
                                    callManager.forceReconnect()
                                }
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = if (isConnected) IosSettingsColors.green else IosSettingsColors.red,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (liveKitEnabled) "Servicio de Llamadas LiveKit" else "Servicio de Llamadas",
                            color = IosSettingsColors.label,
                            fontFamily = IosFont,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isConnected)
                                "Conectado - Listo para llamadas"
                            else
                                "Desconectado - Reconectando...",
                            color = IosSettingsColors.secondaryLabel,
                            fontFamily = IosFont,
                            fontSize = 13.sp
                        )
                    }
                    // Top-level overflow: clear call history
                    var showCallsMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showCallsMenu = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Opciones de llamadas",
                                tint = IosSettingsColors.secondaryLabel
                            )
                        }
                        DropdownMenu(
                            expanded = showCallsMenu,
                            onDismissRequest = { showCallsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Actualizar 🔄", color = IosSettingsColors.label, fontFamily = IosFont) },
                                onClick = {
                                    showCallsMenu = false
                                    onRefresh()
                                },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null, tint = IosSettingsColors.blue, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Reconectar servicio 🔁", color = IosSettingsColors.label, fontFamily = IosFont) },
                                onClick = {
                                    showCallsMenu = false
                                    scope.launch { callManager.forceReconnect() }
                                },
                                leadingIcon = { Icon(Icons.Rounded.Sync, contentDescription = null, tint = IosSettingsColors.blue, modifier = Modifier.size(18.dp)) }
                            )
                            if (callHistory.isNotEmpty()) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Borrar historial 🗑️", color = IosSettingsColors.red, fontFamily = IosFont) },
                                    onClick = {
                                        showCallsMenu = false
                                        showClearHistoryDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = IosSettingsColors.red, modifier = Modifier.size(18.dp)) }
                                )
                            }
                        }
                    }
                }
            }

            // Call history section
            if (callHistory.isNotEmpty()) {
                Text(
                    text = "RECIENTES",
                    color = IosSettingsColors.secondaryLabel,
                    fontFamily = IosFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 7.dp)
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (callHistory.size > 3) 0.5f else  1f),
                    contentPadding = PaddingValues(bottom =  8.dp)
                ) {
                    items(callHistory) { log ->
                        val myId = currentProfile?.id ?: ""
                        val isOutgoing = log.callerId == myId
                        val peerId = if (isOutgoing) log.receiverId else log.callerId
                        val peerProfile = peerProfiles[peerId]
                        val peerName = peerProfile?.displayName ?: peerId.take(8)
                        val isVideo = log.type == com.example.data.model.CallLogType.VIDEO
                        var showCallItemMenu by remember { mutableStateOf(false) }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(vertical = 9.dp)
                        ) {
                            Box {
                                com.example.ui.components.PanaAvatar(
                                    avatarUrl = peerProfile?.avatarUrl,
                                    userId = peerId,
                                    placeholderName = peerName,
                                    size = 44.dp,
                                    modifier = Modifier.size(44.dp)
                                )
                                val isContactOnline = presenceMap[peerId]?.status == com.example.data.repository.UserPresenceStatus.ONLINE
                                com.example.ui.components.chat.list.PresenceIndicator(
                                    status = if (isContactOnline) "online" else "offline",
                                    size = 11.dp,
                                    modifier = Modifier.align(Alignment.BottomEnd)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = peerName,
                                    color = IosSettingsColors.label,
                                    fontFamily = IosFont,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val icon = when (log.status) {
                                        com.example.data.model.CallLogStatus.MISSED ->
                                            if (isOutgoing) Icons.AutoMirrored.Rounded.CallMade else Icons.AutoMirrored.Rounded.CallReceived
                                        com.example.data.model.CallLogStatus.REJECTED -> Icons.AutoMirrored.Rounded.PhoneMissed
                                        com.example.data.model.CallLogStatus.CANCELLED -> Icons.Rounded.Close
                                        else -> if (isOutgoing) Icons.AutoMirrored.Rounded.CallMade else Icons.AutoMirrored.Rounded.CallReceived
                                    }
                                    val statusColor = when (log.status) {
                                        com.example.data.model.CallLogStatus.MISSED, com.example.data.model.CallLogStatus.REJECTED -> IosSettingsColors.red
                                        else -> IosSettingsColors.secondaryLabel
                                    }
                                    val statusText = when (log.status) {
                                        com.example.data.model.CallLogStatus.COMPLETED -> {
                                            val mins = log.durationSeconds / 60
                                            val secs = log.durationSeconds % 60
                                            "(${if (isVideo) "🎥 " else ""}${if (isOutgoing) "Saliente" else "Entrante"}) $mins:${secs.toString().padStart(2, '0')}"
                                        }
                                        com.example.data.model.CallLogStatus.MISSED -> if (isOutgoing) "Sin respuesta" else "Perdida"
                                        com.example.data.model.CallLogStatus.REJECTED -> "Rechazada"
                                        com.example.data.model.CallLogStatus.CANCELLED -> "Cancelada"
                                    }
                                    Icon(icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = statusText, color = statusColor, fontFamily = IosFont, fontSize = 12.sp)
                                }
                            }
                            // Quick call-back button
                            IconButton(
                                onClick = {
                                    tryStartCall(
                                        peerId,
                                        peerName,
                                        if (isVideo) com.example.call.CallType.VIDEO else com.example.call.CallType.AUDIO
                                    )
                                },
                                modifier = Modifier
                                    .background(
                                        color = IosSettingsColors.blue.copy(alpha = 0.14f),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                                    .size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Call,
                                    contentDescription = "Llamar de nuevo",
                                    tint = IosSettingsColors.blue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            // Per-call-item 3-dot menu
                            Box {
                                IconButton(onClick = { showCallItemMenu = true }) {
                                    Icon(Icons.Rounded.MoreVert, contentDescription = "Opciones", tint = IosSettingsColors.secondaryLabel)
                                }
                                DropdownMenu(
                                    expanded = showCallItemMenu,
                                    onDismissRequest = { showCallItemMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Llamar 📞", color = IosSettingsColors.label, fontFamily = IosFont) },
                                        onClick = {
                                            showCallItemMenu = false
                                            tryStartCall(peerId, peerName, com.example.call.CallType.AUDIO)
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.Call, contentDescription = null, tint = IosSettingsColors.blue, modifier = Modifier.size(18.dp)) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Videollamar 🎥", color = IosSettingsColors.label, fontFamily = IosFont) },
                                        onClick = {
                                            showCallItemMenu = false
                                            tryStartCall(peerId, peerName, com.example.call.CallType.VIDEO)
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.Videocam, contentDescription = null, tint = IosSettingsColors.blue, modifier = Modifier.size(18.dp)) }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Eliminar del historial 🗑️", color = IosSettingsColors.red, fontFamily = IosFont) },
                                        onClick = {
                                            showCallItemMenu = false
                                            messagesRepo.deleteCallLog(log.id)
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = IosSettingsColors.red, modifier = Modifier.size(18.dp)) }
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = IosSettingsColors.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 76.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = "LLAMAR A UN PANA",
                color = IosSettingsColors.secondaryLabel,
                fontFamily = IosFont,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 7.dp)
            )

            when (contactsState) {
                is ContactsUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = IosSettingsColors.blue)
                    }
                }
                is ContactsUiState.Success -> {
                    val contacts = contactsState.contacts
                    if (contacts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Text(
                                    text = "📞",
                                    fontSize = 48.sp,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                Text(
                                    text = "No tienes panas para llamar",
                                    color = IosSettingsColors.label,
                                    fontFamily = IosFont,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Text(
                                    text = "Agrega panas usando su PIN en la pestaña 'Gente' para poder llamarlos gratis.",
                                    color = IosSettingsColors.secondaryLabel,
                                    fontFamily = IosFont,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            items(contacts) { contact ->
                                val isContactOnline = presenceMap[contact.id]?.status == com.example.data.repository.UserPresenceStatus.ONLINE
                                var showCallContactMenu by remember { mutableStateOf(false) }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(vertical = 10.dp)
                                        .testTag("call_contact_row_${contact.displayName}")
                                ) {
                                    Box {
                                        com.example.ui.components.PanaAvatar(
                                            avatarUrl = contact.avatarUrl,
                                            userId = contact.id,
                                            placeholderName = contact.displayName,
                                            size = 48.dp,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        com.example.ui.components.chat.list.PresenceIndicator(
                                            status = if (isContactOnline) "online" else "offline",
                                            size = 12.dp,
                                            modifier = Modifier.align(Alignment.BottomEnd)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = contact.displayName,
                                            color = IosSettingsColors.label,
                                            fontFamily = IosFont,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (isContactOnline) "En línea" else "Desconectado",
                                            color = if (isContactOnline) IosSettingsColors.green else IosSettingsColors.secondaryLabel,
                                            fontFamily = IosFont,
                                            fontSize = 13.sp
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                tryStartCall(
                                                    contact.id,
                                                    contact.displayName,
                                                    com.example.call.CallType.AUDIO
                                                )
                                            },
                                            modifier = Modifier
                                                .background(
                                                    color = IosSettingsColors.blue.copy(alpha = 0.14f),
                                                    shape = androidx.compose.foundation.shape.CircleShape
                                                )
                                                .size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Call,
                                                contentDescription = "Llamada de voz",
                                                tint = IosSettingsColors.blue,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                tryStartCall(
                                                    contact.id,
                                                    contact.displayName,
                                                    com.example.call.CallType.VIDEO
                                                )
                                            },
                                            modifier = Modifier
                                                .background(
                                                    color = IosSettingsColors.blue.copy(alpha = 0.14f),
                                                    shape = androidx.compose.foundation.shape.CircleShape
                                                )
                                                .size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Videocam,
                                                contentDescription = "Videollamada",
                                                tint = IosSettingsColors.blue,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Box {
                                            IconButton(onClick = { showCallContactMenu = true }) {
                                                Icon(Icons.Rounded.MoreVert, contentDescription = "Opciones", tint = IosSettingsColors.secondaryLabel)
                                            }
                                            DropdownMenu(
                                                expanded = showCallContactMenu,
                                                onDismissRequest = { showCallContactMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Mensaje 💬", color = IosSettingsColors.label, fontFamily = IosFont) },
                                                    onClick = {
                                                        showCallContactMenu = false
                                                        val repo = com.example.data.repository.ChatsRepository()
                                                        scope.launch(Dispatchers.IO) {
                                                            val chatId = repo.getChatIdByOtherUserId(contact.id)
                                                            if (!chatId.isNullOrEmpty()) {
                                                                withContext(Dispatchers.Main) {
                                                                    onNavigateToChat(chatId, contact.id)
                                                                }
                                                            }
                                                        }
                                                    },
                                                    leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null, tint = IosSettingsColors.blue, modifier = Modifier.size(18.dp)) }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Ver perfil 👤", color = IosSettingsColors.label, fontFamily = IosFont) },
                                                    onClick = {
                                                        showCallContactMenu = false
                                                    },
                                                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null, tint = IosSettingsColors.blue, modifier = Modifier.size(18.dp)) }
                                                )
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(color = IosSettingsColors.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 80.dp))
                            }
                        }
                    }
                }
                is ContactsUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Error cargando panas: ${(contactsState as ContactsUiState.Error).message}",
                            color = IosSettingsColors.red,
                            fontFamily = IosFont,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("¿Borrar historial de llamadas?", color = IosSettingsColors.label, fontFamily = IosFont, fontWeight = FontWeight.SemiBold) },
            text = { Text("Se eliminarán todos los registros de llamadas. Esta acción no se puede deshacer.", color = IosSettingsColors.secondaryLabel, fontFamily = IosFont) },
            confirmButton = {
                TextButton(onClick = {
                    showClearHistoryDialog = false
                    messagesRepo.clearCallHistory()
                    android.widget.Toast.makeText(context, "Historial borrado 🗑️", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Borrar", color = IosSettingsColors.red, fontFamily = IosFont, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancelar", color = IosSettingsColors.blue, fontFamily = IosFont) }
            },
            containerColor = IosSettingsColors.cellElevated,
            titleContentColor = IosSettingsColors.label,
            textContentColor = IosSettingsColors.secondaryLabel
        )
    }
}
