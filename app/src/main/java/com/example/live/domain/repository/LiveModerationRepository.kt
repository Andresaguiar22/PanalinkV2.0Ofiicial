package com.example.live.domain.repository

interface LiveModerationRepository {
    suspend fun deleteComment(commentId: String): Result<Unit>
    suspend fun blockUser(streamId: String, userId: String): Result<Unit>
    suspend fun muteUser(streamId: String, userId: String): Result<Unit>
}
