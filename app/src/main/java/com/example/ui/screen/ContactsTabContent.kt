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

@OptIn(ExperimentalMaterial3Api::class)


@Composable
fun ContactsTabContent(
    contactsState: ContactsUiState,
    chatsViewModel: ChatsViewModel,
    isSelectingContactOnly: Boolean,
    onNavigateToChat: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onContactLongClick: (Profile) -> Unit,
    myPin: String = "",
    onScanQr: () -> Unit = {},
    onAddByPinManually: () -> Unit = {}
) {
    val colors = com.example.ui.theme.LocalAppColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val presenceMap by com.example.data.repository.PresenceRepository.presenceMap.collectAsStateWithLifecycle()

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
        when (contactsState) {
        is ContactsUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PanalinkPalette.textPrimary)
            }
        }
        is ContactsUiState.Success -> {
            val contacts = contactsState.contacts
            android.util.Log.d("CONTACTS_DEBUG", "cantidad finalmente mostrada por la UI: ${contacts.size}")
            val requestsState by chatsViewModel.friendRequestsState.collectAsState()
            val sentRequestsState by chatsViewModel.sentFriendRequestsState.collectAsState()

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (!isSelectingContactOnly) {
                    item {
                        AddPanaHeroCard(
                            myPin = myPin,
                            onScanQr = onScanQr,
                            onAddByPinManually = onAddByPinManually
                        )
                    }
                }
                if (requestsState is FriendRequestsUiState.Success) {
                    val requests = (requestsState as FriendRequestsUiState.Success).requests
                    if (requests.isNotEmpty()) {
                        item {
                            Text(
                                text = "Solicitudes pendientes (${requests.size})",
                                color = PanalinkPalette.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(16.dp, 8.dp)
                            )
                        }
                        items(requests) { request ->
                            com.example.ui.components.ContactRequestRow(
                                request = request,
                                onAccept = { chatsViewModel.acceptFriendRequest(request.id) },
                                onDecline = { chatsViewModel.declineFriendRequest(request.id) }
                            )
                        }
                    }
                }

                if (sentRequestsState is FriendRequestsUiState.Success) {
                    val sentRequests = (sentRequestsState as FriendRequestsUiState.Success).requests
                    if (sentRequests.isNotEmpty()) {
                        item {
                            Text(
                                text = "Mis solicitudes (${sentRequests.size})",
                                color = PanalinkPalette.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(16.dp, 8.dp)
                            )
                        }
                        items(sentRequests) { request ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                com.example.ui.components.PanaAvatar(
                                    avatarUrl = request.receiver?.avatarUrl,
                                    userId = request.receiver?.id,
                                    placeholderName = request.receiver?.displayName ?: "",
                                    size = 40.dp,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = request.receiver?.displayName ?: "Pana",
                                        color = PanalinkPalette.textPrimary
                                    )
                                    Text(
                                        text = "Esperando respuesta",
                                        color = PanalinkPalette.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                if (contacts.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = PanalinkPalette.surface,
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Aún no tienes panas agregados",
                                color = PanalinkPalette.textSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Presiona el botón '+' en la esquina superior para agregar a un pana usando su PIN o escaneando su QR.",
                                color = PanalinkPalette.textSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }
                } else {
                    if (isSelectingContactOnly) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.primary.copy(alpha = 0.08f)),
                                border = BorderStroke(1.dp, colors.primary)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Email, contentDescription = null, tint = PanalinkPalette.textPrimary)
                                    Text(
                                        text = "Selecciona un pana para chatear 💬",
                                        color = PanalinkPalette.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
item {
                        Text(
                            text = if (isSelectingContactOnly) "Seleccionar Contacto" else "TUS PANAS AGREGADOS (${contacts.size})",
                            color = if (isSelectingContactOnly) PanalinkPalette.textPrimary else Color(0xFF8E8E93),
                            fontSize =  13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing =  1.sp,
                            modifier = Modifier.padding(start =  32.dp, top = if (isSelectingContactOnly) 4.dp else  16.dp, bottom =  8.dp)
                        )
                    }
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal =  16.dp)
                                .background(Color(0xFF1C1C1E), RoundedCornerShape(20.dp))
                                .border(1.dp, Color.White.copy(alpha =  0.05f), RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp))
                        ) {

                        contacts.forEachIndexed { index, contact ->
                        var showContactMenu by remember { mutableStateOf(false) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        chatsViewModel.createChat(contact) { chat ->
                                            onNavigateToChat(chat.id, contact.id)
                                        }
                                    },
                                    onLongClick = {
                                        onContactLongClick(contact)
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("contact_row_${contact.displayName}")
                        ) {
                            val isContactOnline = presenceMap[contact.id]?.status == com.example.data.repository.UserPresenceStatus.ONLINE
                            Box {
                                com.example.ui.components.PanaAvatar(
                                    avatarUrl = contact.avatarUrl,
                                    userId = contact.id,
                                    placeholderName = contact.displayName,
                                    size = 50.dp,
                                    modifier = Modifier.size(50.dp)
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
                                    color = PanalinkPalette.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isContactOnline) "En línea" else "Conectado por panalink",
                                    color = if (isContactOnline) PanalinkPalette.accent else PanalinkPalette.textSecondary,
                                    fontSize = 13.sp
                                )
                            }

                            // Per-contact 3-dot overflow menu
                            Box {
                                IconButton(onClick = { showContactMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Opciones",
                                        tint = PanalinkPalette.textSecondary
                                    )
                                }
                                androidx.compose.material3.DropdownMenu(
                                    expanded = showContactMenu,
                                    onDismissRequest = { showContactMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Enviar mensaje 💬") },
                                        onClick = {
                                            showContactMenu = false
                                            chatsViewModel.createChat(contact) { chat ->
                                                onNavigateToChat(chat.id, contact.id)
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Llamada de voz 📞") },
                                        onClick = {
                                            showContactMenu = false
                                            com.example.call.CallPermissionGate.startCallIfPermitted(
                                                activity = null,
                                                context = context,
                                                targetUserId = contact.id,
                                                targetUserName = contact.displayName,
                                                type = com.example.call.CallType.AUDIO
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Videollamada 🎥") },
                                        onClick = {
                                            showContactMenu = false
                                            com.example.call.CallPermissionGate.startCallIfPermitted(
                                                activity = null,
                                                context = context,
                                                targetUserId = contact.id,
                                                targetUserName = contact.displayName,
                                                type = com.example.call.CallType.VIDEO
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Compartir contacto 🔗") },
                                        onClick = {
                                            showContactMenu = false
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TITLE, "Contacto Panalink")
                                                putExtra(Intent.EXTRA_TEXT, "Agrega a ${contact.displayName} en Panalink usando su PIN")
                                                `package` = null
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Compartir contacto"))
                                        },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Eliminar contacto 🗑️", color = Color(0xFFEF4444)) },
                                        onClick = {
                                            showContactMenu = false
                                            onContactLongClick(contact)
                                        },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp)) }
                                    )
                                }
                            }
                        }
                        if (index < contacts.size - 1) {
                            HorizontalDivider(color = Color(0xFF38383A), thickness =  0.5.dp, modifier = Modifier.padding(start =  76.dp))
                        }
                    }
                    }
                }
            }
        }
                    }
        is ContactsUiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contactsState.message,
                    color = Color.Red,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
}

/**
 * Tarjeta principal del tab Gente: identidad propia (PIN + QR real) y accesos
 * directos para agregar contactos escaneando o escribiendo el PIN.
 */
@Composable
private fun AddPanaHeroCard(
    myPin: String,
    onScanQr: () -> Unit,
    onAddByPinManually: () -> Unit
) {
    val context = LocalContext.current
    var isRevealed by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(Color(0xFF1C1C1E), RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha =  0.05f), RoundedCornerShape(24.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom =  8.dp)) {

            Text(
                text = "Agregar un Pana",
                color = Color.White,
                fontSize =  20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(6.dp))
            Text("🤝", fontSize =  20.sp)
        }

        Text(
            text = "Comparte tu PIN o QR, o agrega a quien quieras",
            color = Color(0xFF8E8E93),
            fontSize =  15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal =  8.dp)
        )

        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isRevealed = !isRevealed }) {

            Text(
                text = if (isRevealed) "Ocultar tu PIN/QR" else "Mostrar tu PIN/QR",
                color = Color(0xFF10B981),
                fontSize =  15.sp,
                fontWeight = FontWeight.Medium
            )
            Icon(
                imageVector = if (isRevealed) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Ocultar",
                tint = Color(0xFF10B981),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF000000), RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha =  0.1f), RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            if (isRevealed) {
            if (myPin.isNotEmpty()) {



                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {

                    com.example.ui.components.QrCodeView(
                        pin = myPin,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.width(16.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {



                Text(
                    text = "TU PIN",
                    color = Color(0xFF10B981),
                    fontSize =  11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing =  2.sp
                )
                Spacer(Modifier.height(4.dp))
                if (myPin.isNotEmpty()) {



                    Row(verticalAlignment = Alignment.CenterVertically) {



                        Text(
                            text = myPin.chunked(3).joinToString(" "),
                            color = Color.White,
                            fontSize =  28.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            letterSpacing =  2.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Compartir",
                            tint = Color(0xFF10B981),
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {



                                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("PIN de Pana", myPin))
                                    android.widget.Toast.makeText(context, "¡PIN copiado! 📋", android.widget.Toast.LENGTH_SHORT).show()
                                }
                        )
                    }
                } else {
                    CircularProgressIndicator(
                        color = Color(0xFF10B981),
                        modifier = Modifier.size(22.dp),
                        strokeWidth =  2.dp
                    )
                }
            }
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {



            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF10B981), RoundedCornerShape(14.dp))
                    .padding(vertical =  14.dp)
                    .clickable { onScanQr() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {



                Icon(Icons.Default.CheckCircle, contentDescription = "Scan", tint = Color(0xFF000000), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Escanear QR", color = Color(0xFF000000), fontSize =  16.sp, fontWeight = FontWeight.SemiBold)
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF2C2C2E), RoundedCornerShape(14.dp))
                    .padding(vertical =  14.dp)
                    .clickable { onAddByPinManually() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {


                Icon(Icons.Default.Person, contentDescription = "PIN", tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ingresar PIN", color = Color.White, fontSize =  16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
