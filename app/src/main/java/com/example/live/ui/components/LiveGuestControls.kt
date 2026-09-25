package com.example.live.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Profile
import com.example.live.domain.model.GuestStatus
import com.example.live.domain.model.LiveGuest
import com.example.ui.components.PanaAvatar
import com.example.ui.settings.ios.IosSettingsColors

@Composable
fun LiveGuestControls(
    guests: List<LiveGuest>,
    guestNames: Map<String, String> = emptyMap(),
    onInvite: ((String) -> Unit)?,
    onRemove: ((String) -> Unit)?,
    onSearchUsers: ((String) -> Unit)? = null,
    onClearSearch: (() -> Unit)? = null,
    searchResults: List<Profile> = emptyList(),
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf<Profile?>(null) }

    Box(modifier = modifier) {
        // OutlinedButton con borde verde neón y fondo translúcido: integra el
        // botón en el lenguaje glass del directo sin perder la marca.
        OutlinedButton(
            onClick = { showDialog = true },
            shape = CircleShape,
            border = BorderStroke(1.dp, PanalinkNeonGreen),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0xFF111113).copy(alpha = 0.5f),
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Default.PersonAdd,
                contentDescription = null,
                tint = PanalinkNeonGreen,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "Invitar Co-Host",
                color = IosSettingsColors.label,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Gestionar Invitados (Co-Host)") },
                text ={
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = {
                                query = it
                                selectedUser = null
                                onSearchUsers?.invoke(it)
                            },
                            singleLine = true,
                            label = { Text("Buscar por nombre de usuario") },
                            placeholder = { Text("Escribe un nombre...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = {
                                        query = ""
                                        selectedUser = null
                                        onClearSearch?.invoke()
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = Color.Gray)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (selectedUser != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        query = selectedUser?.displayName ?: ""
                                    }
                                    .background(IosSettingsColors.groupBackground.copy(alpha = 0.7f))
                                    .padding(8.dp)
                            ) {
                                PanaAvatar(avatarUrl = selectedUser?.avatarUrl, userId = selectedUser?.id, size =  32.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(selectedUser!!.displayName ?: "", color = Color.White, fontWeight = FontWeight.Bold, fontSize =  13.sp)
                                    Text(
                                        "Invitar a este usuario",
                                        color = Color.Gray,
                                        fontSize =  11.sp
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    query = ""
                                    selectedUser = null
                                    onClearSearch?.invoke()
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Quitar seleccion", tint = Color.Gray)
                                }
                            }
                        } else if (query.isNotBlank()) {
                            if (searchResults.isEmpty()) {
                                Text(
                                    "Buscando...",
                                    color = Color.Gray,
                                    fontSize =  12.sp
                                )
                            } else {
                                LazyColumn(modifier = Modifier.height(150.dp)) {
                                    items(searchResults, key = { it.id }) { user ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    query = user.displayName ?: ""
                                                    selectedUser = user
                                                    onClearSearch?.invoke()
                                                }
                                                .padding(horizontal =  8.dp, vertical =  6.dp)
                                        ) {
                                            PanaAvatar(avatarUrl = user.avatarUrl, userId = user.id, size =  32.dp)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(user.displayName ?: "", color = Color.White, fontWeight = FontWeight.Medium, fontSize =  13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    "Tocar para invitar",
                                                    color = Color.Gray,
                                                    fontSize =  11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "El invitado recibira una notificacion en tiempo real y podra aceptar o rechazar al instante",
                            fontSize =  12.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Invitados Actuales:",
                            fontWeight = FontWeight.Bold,
                            fontSize =  14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (guests.isEmpty()) {
                            Text("No hay invitados activos", color = Color.Gray, fontSize =  13.sp)
                        } else {
                            LazyColumn(modifier = Modifier.height(120.dp)) {
                                items(guests, key = { it.userId }) { guest ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical =  4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            when (guest.status) {
                                                GuestStatus.PENDING -> "${guestNames[guest.userId] ?: guest.userId.take(6)} (Pendiente de aceptar)"
                                                GuestStatus.ACCEPTED -> "${guestNames[guest.userId] ?: guest.userId.take(6)} (Aceptado)"
                                                GuestStatus.ACTIVE -> "${guestNames[guest.userId] ?: guest.userId.take(6)} (En vivo)"
                                                else -> "${guestNames[guest.userId] ?: guest.userId.take(6)} (${guest.status})"
                                            },
                                            fontSize =  13.sp,
                                            color = when (guest.status) {
                                                GuestStatus.ACTIVE -> IosSettingsColors.blue
                                                GuestStatus.PENDING -> Color(0xFFFFC107)
                                                else -> Color.White
                                            }
                                        )
                                        if (onRemove != null) {
                                            TextButton(onClick = { onRemove(guest.userId) }) {
                                                Text("Remover", color = IosSettingsColors.red, fontSize =  12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton ={
                    TextButton(
                        onClick ={
                            selectedUser?.let {
                                onInvite?.invoke(it.id)
                                query = ""
                                selectedUser = null
                                onClearSearch?.invoke()
                                showDialog = false
                            }
                        },
                        enabled = selectedUser != null
                    ) {
                        Text("Enviar Invitación", color = IosSettingsColors.blue)
                    }
                },
                dismissButton ={
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cerrar")
                    }
                },
                containerColor = IosSettingsColors.cell,
                titleContentColor = Color.White,
                textContentColor = Color.White
            )
        }
    }
}