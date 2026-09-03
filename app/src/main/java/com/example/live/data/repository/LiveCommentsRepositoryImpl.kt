package com.example.live.data.repository

import android.content.Context
import com.example.live.domain.model.LiveComment
import com.example.live.domain.repository.LiveCommentsRepository
import com.example.live.domain.repository.LiveRepository

class LiveCommentsRepositoryImpl(
    private val context: Context,
    private val liveRepository: LiveRepository = LiveRepositoryImpl(context)
) : LiveCommentsRepository {
    override suspend fun getComments(streamId: String): Result<List<LiveComment>> {
        return liveRepository.getComments(streamId)
    }

    override suspend fun postComment(streamId: String, text: String): Result<LiveComment> {
        return liveRepository.postComment(streamId, text)
    }
}
