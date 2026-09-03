package com.example.live.domain.repository

import com.example.live.domain.model.LiveGuest

interface LiveGuestRepository {
    suspend fun requestToJoin(streamId: String): Result<LiveGuest>
    suspend fun inviteGuest(streamId: String, guestUserId: String): Result<LiveGuest>
    suspend fun acceptInvitation(streamId: String, guestUserId: String): Result<Unit>
    suspend fun rejectInvitation(streamId: String, guestUserId: String): Result<Unit>
    suspend fun removeGuest(streamId: String, guestUserId: String): Result<Unit>
    suspend fun leaveLive(streamId: String): Result<Unit>
    suspend fun getGuests(streamId: String): Result<List<LiveGuest>>
    fun observeGuests(streamId: String): kotlinx.coroutines.flow.Flow<List<LiveGuest>>
}
