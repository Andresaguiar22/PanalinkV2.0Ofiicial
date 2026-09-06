package com.example.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Durable per-file upload state for a pending post.
 *
 * Room is the source of truth for media upload progress. Once a media file is
 * [PendingPostMediaStatus.UPLOADED] its remote reference is persisted here, so a process
 * death or work retry can resume without re-uploading already-uploaded files.
 */
@Entity(
    tableName = "pending_post_media",
    foreignKeys = [
        ForeignKey(
            entity = PendingPostEntity::class,
            parentColumns = ["id"],
            childColumns = ["postId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("postId")]
)
data class PendingPostMediaEntity(
    @PrimaryKey val id: String,
    val postId: String,
    val mediaIndex: Int,
    val localUri: String,
    val mimeType: String,
    val sizeBytes: Long,
    val status: String,
    val remoteObjectKey: String? = null,
    val remoteUrl: String? = null,
    val errorMessage: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

object PendingPostMediaStatus {
    const val PENDING = "PENDING"
    const val UPLOADING = "UPLOADING"
    const val UPLOADED = "UPLOADED"
    const val FAILED_RETRYABLE = "FAILED_RETRYABLE"
    const val FAILED_TERMINAL = "FAILED_TERMINAL"
}