package com.example.live.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.live.domain.model.GuestStatus
import com.example.live.domain.model.LiveGuest
import com.example.ui.components.PanaAvatar
import com.example.ui.settings.ios.IosSettingsColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveRequestsSheet(
    isBroadcaster: Boolean,
    guests: List<LiveGuest>,
    myUserId: String?,
    presentUsers: List<String>,
    onDismiss: () -> Unit,
    onRequestToJoin: () -> Unit,
    onLeaveAsGuest: () -> Unit,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    val pending = guests.filter { it.status == GuestStatus.PENDING }
    val active = guests.filter {
        it.status == GuestStatus.ACCEPTED ||
            it.status == GuestStatus.ACTIVE ||
            it.status == GuestStatus.CONNECTED
    }
    val myRequest = guests.firstOrNull { it.userId == myUserId }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = IosSettingsColors.cell,
        contentColor = IosSettingsColors.label
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = if (isBroadcaster) "Solicitudes de co-host" else "Co-host y espectadores",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = IosSettingsColors.label
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isBroadcaster) {
                if (pending.isEmpty()) {
                    Text(
                        text = "No hay solicitudes pendientes.",
                        color = IosSettingsColors.secondaryLabel,
                        fontSize = 13.sp
                    )
                } else {
                    guests.forEach { guest ->
                        GuestRow(
                            guest = guest,
                            subtitle = "Quiere ser co-host",
                            actions = {
                                TextButton(onClick = { onAccept(guest.userId) }) {
                                    Text("Aceptar", color = IosSettingsColors.blue, fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = { onReject(guest.userId) }) {
                                    Text("Rechazar", color = IosSettingsColors.red)
                                }
                            }
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = IosSettingsColors.cellElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        when (myRequest?.status) {
                            GuestStatus.PENDING -> Text(
                                text = "⏳ Tu solicitud está pendiente de aprobación.",
                                color = IosSettingsColors.yellow,
                                fontSize = 13.sp
                            )
                            GuestStatus.ACCEPTED, GuestStatus.ACTIVE, GuestStatus.CONNECTED -> Text(
                                text = "✅ Eres co-host de este directo.",
                                color = IosSettingsColors.blue,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            GuestStatus.REJECTED -> Text(
                                text = "❌ El anfitrión rechazó tu solicitud. Puedes intentarlo de nuevo.",
                                color = IosSettingsColors.red,
                                fontSize = 13.sp
                            )
                            else -> Text(
                                text = "Solicita al anfitrión unirte al directo como co-host.",
                                color = IosSettingsColors.label.copy(alpha = 0.85f),
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        when (myRequest?.status) {
                            GuestStatus.PENDING -> Button(
                                onClick = { onLeaveAsGuest() },
                                colors = ButtonDefaults.buttonColors(containerColor = IosSettingsColors.cellElevated),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Cancelar solicitud", color = IosSettingsColors.label) }

                            GuestStatus.ACCEPTED, GuestStatus.ACTIVE, GuestStatus.CONNECTED -> Button(
                                onClick = { onLeaveAsGuest() },
                                colors = ButtonDefaults.buttonColors(containerColor = IosSettingsColors.red),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Salir como co-host", color = IosSettingsColors.label) }

                            else -> Button(
                                onClick = { onRequestToJoin() },
                                colors = ButtonDefaults.buttonColors(containerColor = IosSettingsColors.blue),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Solicitar ser co-host", color = IosSettingsColors.label, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Espectadores ahora: ${presentUsers.size}",
                color = IosSettingsColors.label.copy(alpha = 0.8f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (presentUsers.isEmpty()) {
                Text(
                    text = "Aún no hay otros espectadores.",
                    color = IosSettingsColors.secondaryLabel,
                    fontSize = 12.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 180.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(presentUsers, key = { it }) { userId ->
                        val identity = rememberLiveIdentity(userId)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PanaAvatar(
                                avatarUrl = identity?.avatarUrl,
                                userId = userId,
                                size = 30.dp,
                                borderWidth = 0.dp,
                                placeholderName = identity.displayNameOr(userId)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (userId == myUserId) "Tú" else identity.displayNameOr(userId),
                                color = IosSettingsColors.label,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isBroadcaster && active.isNotEmpty()) {
                Text(
                    text = "Co-hosts activos",
                    color = IosSettingsColors.label.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                active.forEach { guest ->
                    GuestRow(
                        guest = guest,
                        subtitle = "En el directo",
                        actions = {
                            TextButton(onClick = { onRemove(guest.userId) }) {
                                Text("Quitar", color = IosSettingsColors.red)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun GuestRow(
    guest: LiveGuest,
    subtitle: String,
    actions: @Composable RowScope.() -> Unit
) {
    val identity = rememberLiveIdentity(guest.userId)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PanaAvatar(
            avatarUrl = identity?.avatarUrl,
            userId = guest.userId,
            size = 34.dp,
            borderWidth = 0.dp,
            placeholderName = identity.displayNameOr(guest.userId)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = identity.displayNameOr(guest.userId),
                color = IosSettingsColors.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = IosSettingsColors.label.copy(alpha = 0.65f),
                fontSize = 11.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}
