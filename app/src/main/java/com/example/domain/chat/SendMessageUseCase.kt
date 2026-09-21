package com.example.domain.chat

import android.content.Context
import com.example.data.repository.MessagesRepository
import com.example.core.error.ResultState
import com.example.core.error.ErrorMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SendMessageUseCase(private val context: Context) {
    private val messagesRepository by lazy { MessagesRepository.getInstance() }

    suspend operator fun invoke(
        chatId: String,
        receiverId: String,
        content: String,
        messageType: String = "text"
    ): ResultState<Unit> = withContext(Dispatchers.IO) {
        try {
            messagesRepository.sendMessage(
                chatId = chatId,
                receiverUid = receiverId,
                content = content,
                messageType = messageType
            )
            // Premium 2.0: registra actividad de misión (sin bloquear el envío).
            try {
                com.example.premium.domain.PremiumEventBus.publishActivity(
                    com.example.premium.domain.MissionActivities.MESSAGE_SENT
                )
            } catch (_: Exception) {
            }
            ResultState.Success(Unit)
        } catch (e: Exception) {
            ResultState.Error(ErrorMapper.map(e))
        }
    }
}
