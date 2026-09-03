package com.example.live.domain.repository

import com.example.live.domain.model.LiveComment

interface LiveCommentsRepository {
    suspend fun getComments(streamId: String): Result<List<LiveComment>>
    suspend fun postComment(streamId: String, text: String): Result<LiveComment>
}
