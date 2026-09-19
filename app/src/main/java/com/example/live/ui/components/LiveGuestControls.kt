package com.example.live.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.live.domain.model.GuestStatus
import com.example.live.domain.model.LiveGuest

@Composable
fun LiveGuestControls(
    guests: List<LiveGuest>,
    onInvite: ((String) -> Unit)?,
    onRemove: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    var inviteUserId by remember { mutableStateOf("") }

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
                color = Color.White,
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
                            value = inviteUserId,
                            onValueChange = { inviteUserId = it },
                            singleLine = true,
                            label = { Text("ID del usuario") },
                            placeholder = { Text("UUID del usuario") },
                            modifier = Modifier.fillMaxWidth()
                        )
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
                                                GuestStatus.PENDING -> "${guest.userId.take(6)} (Pendiente de aceptar)"
                                                GuestStatus.ACCEPTED -> "${guest.userId.take(6)} (Aceptado)"
                                                GuestStatus.ACTIVE -> "${guest.userId.take(6)} (En vivo)"
                                                else -> "${guest.userId.take(6)} (${guest.status})"
                                            },
                                            fontSize =  13.sp,
                                            color = when (guest.status) {
                                                GuestStatus.ACTIVE -> Color(0xFF00E5FF)
                                                GuestStatus.PENDING -> Color(0xFFFFC107)
                                                else -> Color.White
                                            }
                                        )
                                        if (onRemove != null) {
                                            TextButton(onClick = { onRemove(guest.userId) }) {
                                                Text("Remover", color = Color(0xFFEF5350), fontSize =  12.sp)
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
                            if (inviteUserId.isNotBlank()) {
                                onInvite?.invoke(inviteUserId.trim())
                                inviteUserId = ""
                                showDialog = false
                            }
                        },
                        enabled = inviteUserId.isNotBlank()
                    ) {
                        Text("Enviar Invitación", color = Color(0xFF00A884))
                    }
                },
                dismissButton ={
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cerrar")
                    }
                },
                containerColor = Color(0xFF161618),
                titleContentColor = Color.White,
                textContentColor = Color.White
            )
        }
    }
}