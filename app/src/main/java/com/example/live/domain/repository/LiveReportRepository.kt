package com.example.live.domain.repository

interface LiveReportRepository {
    suspend fun report(
        streamId: String,
        reportedUserId: String?,
        commentId: String?,
        reason: String
    ): Result<Unit>
}
