package com.example.live.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.live.data.remote.LiveGuestRealtimeManager
import com.example.live.data.repository.LiveGuestRepositoryImpl
import com.example.live.domain.model.LiveGuest
import com.example.live.domain.repository.LiveGuestRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LiveGuestViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: LiveGuestRepository = LiveGuestRepositoryImpl(application)

    private val _guests = MutableStateFlow<List<LiveGuest>>(emptyList())
    val guests: StateFlow<List<LiveGuest>> = _guests.asStateFlow()

    private var realtimeManager: LiveGuestRealtimeManager? = null

    fun loadGuests(streamId: String) {
        viewModelScope.launch {
            val result = repository.getGuests(streamId)
            if (result.isSuccess) {
                _guests.value = result.getOrDefault(emptyList())
            }
        }
    }

    fun startRealtime(streamId: String) {
        realtimeManager?.stop()
        realtimeManager = LiveGuestRealtimeManager(streamId) { updatedGuest ->
            val current = _guests.value.toMutableList()
            val index = current.indexOfFirst { it.userId == updatedGuest.userId }
            if (index >= 0) {
                if (updatedGuest.status == com.example.live.domain.model.GuestStatus.REMOVED || updatedGuest.status == com.example.live.domain.model.GuestStatus.REJECTED) {
                    current.removeAt(index)
                } else {
                    current[index] = updatedGuest
                }
            } else {
                if (updatedGuest.status != com.example.live.domain.model.GuestStatus.REMOVED && updatedGuest.status != com.example.live.domain.model.GuestStatus.REJECTED) {
                    current.add(updatedGuest)
                }
            }
            _guests.value = current
        }.apply {
            start()
        }
    }

    fun inviteGuest(streamId: String, userId: String) {
        viewModelScope.launch {
            repository.inviteGuest(streamId, userId)
        }
    }

    fun requestToJoin(streamId: String) {
        viewModelScope.launch {
            repository.requestToJoin(streamId)
        }
    }

    suspend fun acceptInvitation(streamId: String, userId: String): Result<Unit> {
        return repository.acceptInvitation(streamId, userId)
    }

    fun rejectInvitation(streamId: String, userId: String) {
        viewModelScope.launch {
            repository.rejectInvitation(streamId, userId)
        }
    }

    fun removeGuest(streamId: String, userId: String) {
        viewModelScope.launch {
            repository.removeGuest(streamId, userId)
        }
    }

    fun leaveLive(streamId: String) {
        viewModelScope.launch {
            repository.leaveLive(streamId)
        }
    }

    fun stopRealtime() {
        realtimeManager?.stop()
        realtimeManager = null
    }

    override fun onCleared() {
        super.onCleared()
        stopRealtime()
    }
}
