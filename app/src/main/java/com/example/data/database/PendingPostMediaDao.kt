package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingPostMediaDao {

    @Query("SELECT * FROM pending_post_media WHERE postId = :postId ORDER BY mediaIndex ASC")
    suspend fun getMediaForPost(postId: String): List<PendingPostMediaEntity>

    @Query("SELECT * FROM pending_post_media WHERE postId = :postId ORDER BY mediaIndex ASC")
    fun getMediaForPostFlow(postId: String): Flow<List<PendingPostMediaEntity>>

    @Query("SELECT * FROM pending_post_media WHERE postId = :postId AND mediaIndex = :mediaIndex LIMIT 1")
    suspend fun getMediaByIndex(postId: String, mediaIndex: Int): PendingPostMediaEntity?

    @Query("SELECT * FROM pending_post_media WHERE id = :id LIMIT 1")
    suspend fun getMediaById(id: String): PendingPostMediaEntity?

    @Query("SELECT * FROM pending_post_media WHERE postId = :postId AND mediaIndex = :mediaIndex LIMIT 1")
    fun getMediaByIndexLive(postId: String, mediaIndex: Int): Flow<PendingPostMediaEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedia(media: PendingPostMediaEntity)



    @Query("UPDATE pending_post_media SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)

    @Query("""
        UPDATE pending_post_media
        SET status = :status, remoteObjectKey = :remoteObjectKey, remoteUrl = :remoteUrl, updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun markUploaded(
        id: String,
        status: String,
        remoteObjectKey: String?,
        remoteUrl: String?,
        updatedAt: Long
    )

    @Query("""
        UPDATE pending_post_media
        SET status = :status, errorMessage = :errorMessage, remoteObjectKey = :remoteObjectKey, remoteUrl = :remoteUrl, updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun markFailed(
        id: String,
        status: String,
        errorMessage: String?,
        remoteObjectKey: String?,
        remoteUrl: String?,
        updatedAt: Long
    )

    @Query("SELECT COUNT(*) FROM pending_post_media WHERE postId = :postId")
    suspend fun countMedia(postId: String): Int

    @Query("SELECT COUNT(*) FROM pending_post_media WHERE postId = :postId AND status = :status")
    suspend fun countByStatus(postId: String, status: String): Int

    @Query("SELECT COUNT(*) FROM pending_post_media WHERE postId = :postId AND status != 'UPLOADED'")
    suspend fun countNotUploaded(postId: String): Int

    @Query("SELECT COUNT(*) FROM pending_post_media WHERE postId = :postId AND status = 'UPLOADED'")
    suspend fun countUploaded(postId: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM pending_post_media WHERE postId = :postId AND status != 'UPLOADED' LIMIT 1)")
    suspend fun hasPendingMedia(postId: String): Boolean

    @Query("DELETE FROM pending_post_media WHERE postId = :postId")
    suspend fun deleteForPost(postId: String)

    @Query("DELETE FROM pending_post_media WHERE id = :id")
    suspend fun deleteById(id: String)
}