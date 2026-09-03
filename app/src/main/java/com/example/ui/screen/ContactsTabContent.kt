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
                CircularProgressIndicator(color = Color.White)
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
                                color = Color.White,
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
                                color = Color.White,
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
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Esperando respuesta",
                                        color = Color(0xFF90A4AE),
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
                                tint = Color(0xFF37474F),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Aún no tienes panas agregados",
                                color = Color(0xFF90A4AE),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Presiona el botón '+' en la esquina superior para agregar a un pana usando su PIN o escaneando su QR.",
                                color = Color(0xFF607D8B),
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
                                    Icon(Icons.Default.Email, contentDescription = null, tint = Color.White)
                                    Text(
                                        text = "Selecciona un pana para chatear 💬",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            text = if (isSelectingContactOnly) "Seleccionar Contacto" else "Tus Panas Agregados (${contacts.size})",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = if (isSelectingContactOnly) 4.dp else 16.dp, bottom = 8.dp)
                        )
                    }
                    items(contacts) { contact ->
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
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isContactOnline) "En línea" else "Conectado por panalink",
                                    color = if (isContactOnline) Color(0xFF00FF85) else Color(0xFF90A4AE),
                                    fontSize = 13.sp
                                )
                            }

                            // Per-contact 3-dot overflow menu
                            Box {
                                IconButton(onClick = { showContactMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Opciones",
                                        tint = Color(0xFF90A4AE)
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
                        HorizontalDivider(color = Color(0xFF1E2E36), thickness = 0.5.dp)
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
    // El PIN/QR propio arranca oculto; el usuario lo despliega bajo demanda. Asi no
    // se expone el identificador en screenshots o miradas de otros.
    var isRevealed by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101418)),
        border = BorderStroke(1.dp, Color(0xFF00FF85).copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Agregar un Pana 🤝",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            Text(
                text = "Comparte tu PIN o QR, o agrega a quien quieras",
                color = Color(0xFF90A4AE),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isRevealed = !isRevealed },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isRevealed) "Ocultar tu PIN/QR" else "Mostrar tu PIN/QR",
                    color = Color(0xFF00FF85),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = if (isRevealed) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFF00FF85),
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(
                visible = isRevealed,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                ) {
                    if (myPin.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White)
                                .padding(8.dp)
                        ) {
                            com.example.ui.components.QrCodeView(
                                pin = myPin,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "TU PIN",
                            color = Color(0xFF00FF85),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        if (myPin.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = myPin.chunked(3).joinToString(" "),
                                    color = Color.White,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp
                                )
                                IconButton(
                                    onClick = {
                                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        cm.setPrimaryClip(android.content.ClipData.newPlainText("PIN de Pana", myPin))
                                        android.widget.Toast.makeText(context, "¡PIN copiado! 📋", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Copiar PIN",
                                        tint = Color(0xFF00FF85),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else {
                            CircularProgressIndicator(
                                color = Color(0xFF00FF85),
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onScanQr,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .bounceClick(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Escanear QR", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                OutlinedButton(
                    onClick = onAddByPinManually,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF00FF85).copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = Color(0xFF00FF85),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Ingresar PIN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}


