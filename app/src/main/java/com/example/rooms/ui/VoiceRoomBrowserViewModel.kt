package com.example.rooms.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rooms.model.VoiceRoom
import com.example.rooms.repository.CreateRoomRequest
import com.example.rooms.repository.VoiceRoomRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VoiceRoomBrowserUiState(
    val rooms: List<VoiceRoom> = emptyList(),
    val memberCounts: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val createdRoom: VoiceRoom? = null
)

/** ViewModel del navegador de salas: lista live rooms, crea sala propia. */
class VoiceRoomBrowserViewModel : ViewModel() {

    private val repository = VoiceRoomRepository.getInstance()

    private val _uiState = MutableStateFlow(VoiceRoomBrowserUiState())
    val uiState: StateFlow<VoiceRoomBrowserUiState> = _uiState

    init { refresh() }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, error = null, createdRoom = null) }
        viewModelScope.launch {
            val rooms = repository.listLiveRooms().getOrElse { e ->
                _uiState.update { s -> s.copy(isLoading = false, error = e.message) }
                return@launch
            }
            val counts = repository.memberCounts(rooms.map { it.id }).getOrNull() ?: emptyMap()
            _uiState.update {
                it.copy(rooms = rooms, memberCounts = counts, isLoading = false, error = null)
            }
        }
    }

    fun createRoom(request: CreateRoomRequest) {
        viewModelScope.launch {
            repository.createRoom(request)
                .onSuccess { room -> _uiState.update { it.copy(createdRoom = room) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
}
