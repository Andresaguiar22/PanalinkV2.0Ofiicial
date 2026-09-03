package com.example.rooms.model

/** Modelos del modulo Salas de Voz. Independientes del chat privado. */
data class VoiceRoom(
    val id: String,
    val name: String,
    val ownerId: String,
    val status: String,
    val maxSeats: Int = MAX_SEATS,
    val description: String = "",
    val coverUrl: String? = null,
    val category: String = "general",
    val visibility: String = "public",
    val isLocked: Boolean = false
) {
    companion object { const val MAX_SEATS = 9 }
    val isPrivate: Boolean get() = visibility == "private"
}

data class VoiceRoomMember(
    val id: String,
    val roomId: String,
    val userId: String,
    val role: String,
    val joinedAt: String,
    val displayName: String? = null,
    val avatarUrl: String? = null
)

data class VoiceRoomSeat(
    val index: Int,
    val userId: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val isMuted: Boolean = false,
    val isSpeaking: Boolean = false
) { val isOccupied: Boolean get() = userId != null }

data class VoiceRoomMessage(
    val id: String,
    val roomId: String,
    val senderId: String,
    val senderName: String? = null,
    val content: String,
    val createdAt: String,
    val isSystem: Boolean = false
)

data class VoiceRoomSeatRequest(
    val id: String,
    val roomId: String,
    val userId: String,
    val requestedSeatIndex: Int? = null,
    val status: String = "pending",
    val createdAt: String
)

data class VoiceRoomUiState(
    val room: VoiceRoom? = null,
    val seats: List<VoiceRoomSeat> = emptySeats(),
    val members: List<VoiceRoomMember> = emptyList(),
    val seatRequests: List<VoiceRoomSeatRequest> = emptyList(),
    val messages: List<VoiceRoomMessage> = emptyList(),
    val memberCount: Int = 0,
    val myUserId: String = "",
    val myRole: String = "listener",
    val isJoining: Boolean = true,
    val isMicEnabled: Boolean = false,
    val error: String? = null
) {
    val mySeat: VoiceRoomSeat? get() = seats.firstOrNull { it.userId == myUserId }
    val isSeated: Boolean get() = mySeat != null
    val isHost: Boolean get() = myRole == "owner"
    val isAdmin: Boolean get() = myRole == "owner" || myRole == "admin"
    val pendingSeatRequest: VoiceRoomSeatRequest? get() = seatRequests.firstOrNull { it.userId == myUserId && it.status == "pending" }

    companion object {
        fun emptySeats(): List<VoiceRoomSeat> = (0 until VoiceRoom.MAX_SEATS).map { VoiceRoomSeat(index = it) }
    }
}

object VoiceRoomSeatReducer {
    fun occupy(seats: List<VoiceRoomSeat>, seatIndex: Int, userId: String, displayName: String?, avatarUrl: String?): List<VoiceRoomSeat> {
        if (seatIndex !in 0 until VoiceRoom.MAX_SEATS) return seats
        if (seats.any { it.userId == userId }) return seats
        return seats.map { if (it.index == seatIndex && it.userId == null) it.copy(userId=userId,displayName=displayName,avatarUrl=avatarUrl,isMuted=false,isSpeaking=false) else it }
    }
    fun release(seats: List<VoiceRoomSeat>, userId: String): List<VoiceRoomSeat> = seats.map { if (it.userId == userId) VoiceRoomSeat(it.index) else it }
    fun releaseSeat(seats: List<VoiceRoomSeat>, seatIndex: Int): List<VoiceRoomSeat> = seats.map { if (it.index == seatIndex) VoiceRoomSeat(it.index) else it }
    fun setMuted(seats: List<VoiceRoomSeat>, userId: String, muted: Boolean): List<VoiceRoomSeat> = seats.map { if (it.userId == userId) it.copy(isMuted=muted,isSpeaking=if(muted) false else it.isSpeaking) else it }
    fun setSpeaking(seats: List<VoiceRoomSeat>, userId: String, speaking: Boolean): List<VoiceRoomSeat> = seats.map { if (it.userId == userId && !it.isMuted) it.copy(isSpeaking=speaking) else it }
    fun firstFreeSeatIndex(seats: List<VoiceRoomSeat>): Int? = seats.firstOrNull { it.userId == null && it.index != 0 }?.index
}

object VoiceRoomMessagesReducer {
    const val HISTORY_CAP = 200
    fun append(messages: List<VoiceRoomMessage>, message: VoiceRoomMessage, cap: Int = HISTORY_CAP): List<VoiceRoomMessage> {
        if (message.id.isEmpty() || messages.any { it.id == message.id }) return messages
        return (messages + message).takeLast(cap)
    }
}
