package com.example.rooms.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun VoiceRoomScreen(
    roomId: String?,
    onBack: () -> Unit,
    viewModel: VoiceRoomViewModel = viewModel(),
    onOpenProfile: ((String) -> Unit)? = null
) {
    if (roomId.isNullOrBlank()) RoomSelectionRequired(onBack) else VoiceRoomRedesignedScreen(
        roomId = roomId,
        onBack = onBack,
        viewModel = viewModel,
        onOpenProfile = onOpenProfile
    )
}

@Composable
private fun RoomSelectionRequired(onBack:()->Unit){
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("Selecciona una sala activa para entrar")}
}
