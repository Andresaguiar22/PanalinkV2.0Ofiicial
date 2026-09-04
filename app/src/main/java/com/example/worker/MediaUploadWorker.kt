package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.database.PanalinkDatabase
import com.example.data.repository.MessagesRepository
import com.example.data.repository.UploadFailoverRouter
import com.example.data.repository.VideoRouter
import com.example.util.PanalinkMediaManager
import java.io.File

class MediaUploadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val TAG = "MediaUploadWorker"
    private val messageDao = PanalinkDatabase.getDatabase(context).messageDao()
    private val messagesRepository = MessagesRepository.getInstance()

    companion object {
        private const val MAX_UPLOAD_ATTEMPTS = 5

        fun evaluateFilePrecondition(fileExists: Boolean, mediaUrl: String?): FilePreconditionResult {
            val hasRemoteUrl = !mediaUrl.isNullOrBlank()
            return when {
                !fileExists && !hasRemoteUrl -> FilePreconditionResult.FAIL_MISSING_FILE
                hasRemoteUrl -> FilePreconditionResult.CONTINUE_WITH_REMOTE_URL
                else -> FilePreconditionResult.PROCEED_TO_UPLOAD
            }
        }

        /** Production decision used by the album worker before committing Room state. */
        fun isAlbumComplete(remoteUrlCount: Int, totalRequired: Int): Boolean =
            totalRequired > 0 && remoteUrlCount == totalRequired

        /** Production decision used by the album worker before starting uploads. */
        fun albumHasUnrecoverableFile(
            paths: List<String>,
            existingRemoteUrls: List<String>
        ): Boolean {
            for ((index, path) in paths.withIndex()) {
                val existingUrl = existingRemoteUrls.getOrNull(index)?.takeIf { it.startsWith("http") }
                if (evaluateFilePrecondition(File(path).exists(), existingUrl) == FilePreconditionResult.FAIL_MISSING_FILE) {
                    return true
                }
            }
            return false
        }

        /** Production retry gate: never retry when a required local file has disappeared. */
        fun shouldRetryAlbum(
            remoteUrlCount: Int,
            totalRequired: Int,
            allStillExist: Boolean,
            runAttemptCount: Int,
            maxAttempts: Int = MAX_UPLOAD_ATTEMPTS
        ): Boolean =
            remoteUrlCount != totalRequired &&
                allStillExist &&
                runAttemptCount + 1 < maxAttempts

        /** Production stable object-name contract: same message/index => same object key. */
        fun albumStableFileName(stableUuid: String, index: Int, extension: String): String =
            "${stableUuid}_$index.${extension.trimStart('.').ifEmpty { "jpg" }}"

        /** Decisión P2-A: vídeo de chat va a vCDN (VideoRouter); el resto a B2. */
        fun shouldRouteVideoToVcdn(messageType: String?, mimeType: String?): Boolean {
            val type = messageType?.lowercase()?.trim().orEmpty()
            val mime = mimeType?.lowercase()?.trim().orEmpty()
            return type == "video" || type.startsWith("video/") || mime.startsWith("video/")
        }
    }

    enum class FilePreconditionResult {
        PROCEED_TO_UPLOAD,
        CONTINUE_WITH_REMOTE_URL,
        FAIL_MISSING_FILE
    }

    private suspend fun markFailed(messageId: String) {
        try {
            messageDao.updateMessageStatus(messageId, "failed")
        } catch (dbEx: Exception) {
            Log.e(TAG, "Failed to persist terminal failed state for $messageId", dbEx)
        }
    }

    override suspend fun doWork(): Result {
        val messageId = inputData.getString("messageId") ?: run {
            Log.e(TAG, "MEDIA_WORK_RESULT = FAILURE (missing messageId)")
            return Result.failure()
        }
        val authUid = com.example.data.supabase.SupabaseClient.currentUser?.id
        val entity = messageDao.getMessageById(messageId) ?: run {
            Log.e(TAG, "MEDIA_WORK_RESULT = FAILURE (message $messageId not found in Room)")
            return Result.failure()
        }
        val localUri = entity.localMediaUri

        val fileCheck = if (!localUri.isNullOrBlank()) File(localUri) else null
        val fileExists = fileCheck?.exists() == true
        val fileSizeBytes = if (fileExists) fileCheck?.length() ?: 0L else 0L

        Log.i(TAG, "MEDIA_UPLOAD_INIT: messageId=$messageId, runAttemptCount=$runAttemptCount, fileExists=$fileExists, fileSizeBytes=$fileSizeBytes, messageType=${entity.messageType}, localMediaUri=$localUri, roomStatus=${entity.status}, receiverId=${entity.receiverId}, clientMessageUuid=${entity.clientMessageUuid}")

        if (localUri.isNullOrBlank()) {
            if (!entity.mediaUrl.isNullOrBlank()) {
                messagesRepository.scheduleSync()
                return logFinalStateAndResult(messageId, Result.success())
            }
            markFailed(messageId)
            return logFinalStateAndResult(messageId, Result.failure())
        }

        return try {
            val stableUuid = entity.clientMessageUuid?.takeIf { it.isNotBlank() } ?: entity.id

            if (entity.messageType == "image" && localUri.contains(",")) {
                Log.i(TAG, "Detected image album (paths joined by ',')")
                val allPaths = localUri.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val totalRequired = allPaths.size

                val existingRemoteUrls = entity.mediaUrl?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                if (isAlbumComplete(existingRemoteUrls.size, totalRequired) && existingRemoteUrls.all { it.startsWith("http") }) {
                    Log.i(TAG, "Album already fully uploaded with $totalRequired URLs; skipping physical upload and scheduling sync")
                    if (entity.status != "sending") messageDao.updateMessageStatus(messageId, "sending")
                    messagesRepository.scheduleSync()
                    return logFinalStateAndResult(messageId, Result.success())
                }

                Log.i(TAG, "MEDIA_UPLOAD_START: messageId=$messageId, type=album[$totalRequired], hasLocalMediaUri=true, attempt=$runAttemptCount")

                if (albumHasUnrecoverableFile(allPaths, existingRemoteUrls)) {
                    Log.e(TAG, "Album: at least one required local file is missing and unrecoverable; marking failed (no partial album)")
                    markFailed(messageId)
                    return logFinalStateAndResult(messageId, Result.failure())
                }

                val remoteUrls = mutableListOf<String>()
                val uploadedPaths = mutableListOf<String>()
                var remoteThumb: String? = null
                var allUploadsSucceeded = true

                for ((index, path) in allPaths.withIndex()) {
                    val albumFile = File(path)
                    val existingUrl = existingRemoteUrls.getOrNull(index)?.takeIf { it.startsWith("http") }
                    if (existingUrl != null) {
                        remoteUrls += existingUrl
                        uploadedPaths += path
                        continue
                    }
                    if (!albumFile.exists()) {
                        allUploadsSucceeded = false
                        break
                    }

                    val mime = detectImageMime(albumFile)
                    val ext = if (albumFile.name.contains(".")) albumFile.name.substringAfterLast(".") else "jpg"
                    val stableFileName = albumStableFileName(stableUuid, index, ext)
                    val res = UploadFailoverRouter.uploadWithFailover(
                        file = albumFile,
                        mimeType = mime,
                        userId = entity.senderId,
                        uploadType = "image",
                        customFileName = stableFileName,
                        clientMessageUuid = stableUuid
                    ) {
                        PanalinkMediaManager.uploadMediaAndThumbnail(
                            context = context,
                            mediaFile = albumFile,
                            mimeType = mime,
                            typeLabel = "image",
                            userId = entity.senderId,
                            caption = entity.content ?: "Album image"
                        )
                    }

                    if (res.isSuccess) {
                        val mediaInfo = res.getOrThrow()
                        remoteUrls += mediaInfo.url
                        if (remoteThumb == null) remoteThumb = mediaInfo.thumbnailUrl
                        uploadedPaths += path
                    } else {
                        Log.e(TAG, "Album image $index upload failed: ${res.exceptionOrNull()?.message}")
                        allUploadsSucceeded = false
                        break
                    }
                }

                if (!isAlbumComplete(remoteUrls.size, totalRequired) || !allUploadsSucceeded) {
                    val allStillExist = allPaths.all { File(it).exists() }
                    if (shouldRetryAlbum(remoteUrls.size, totalRequired, allStillExist, runAttemptCount)) {
                        Log.w(TAG, "Album: partial upload ${remoteUrls.size}/$totalRequired; retrying entire album (no partial commit)")
                        return logFinalStateAndResult(messageId, Result.retry())
                    }
                    Log.e(TAG, "Album: failed to upload all $totalRequired images; marking failed")
                    markFailed(messageId)
                    return logFinalStateAndResult(messageId, Result.failure())
                }

                val updated = entity.copy(
                    mediaUrl = remoteUrls.joinToString(","),
                    thumbnailUrl = remoteThumb ?: entity.thumbnailUrl?.takeIf { it.startsWith("http") },
                    localMediaUri = null,
                    status = "sending"
                )
                messageDao.insertMessage(updated)
                uploadedPaths.forEach { runCatching { File(it).delete() } }
                messagesRepository.scheduleSync()
                return logFinalStateAndResult(messageId, Result.success())
            }

            val file = File(localUri)
            when (evaluateFilePrecondition(file.exists(), entity.mediaUrl)) {
                FilePreconditionResult.FAIL_MISSING_FILE -> {
                    Log.e(TAG, "Local file does not exist: $localUri and no remoteUrl present")
                    markFailed(messageId)
                    return logFinalStateAndResult(messageId, Result.failure())
                }
                FilePreconditionResult.CONTINUE_WITH_REMOTE_URL -> {
                    Log.i(TAG, "Media already uploaded with remoteUrl=${entity.mediaUrl}; skipping physical upload and scheduling sync")
                    if (entity.status != "sending") messageDao.updateMessageStatus(messageId, "sending")
                    messagesRepository.scheduleSync()
                    return logFinalStateAndResult(messageId, Result.success())
                }
                FilePreconditionResult.PROCEED_TO_UPLOAD -> Unit
            }

            val mimeType = entity.mediaMime ?: "application/octet-stream"
            val typeLabel = entity.messageType ?: "text"
            val userId = entity.senderId
            val stableKey = "${stableUuid}_0"
            val ext = if (file.name.contains(".")) file.name.substringAfterLast(".") else "bin"
            val stableFileName = "${stableKey}.$ext"

            val progressCb: (Long, Long) -> Unit = { written, total ->
                if (total > 0L) {
                    val pct = ((written.toDouble() / total.toDouble()) * 100.0).toInt()
                    setProgressAsync(androidx.work.workDataOf("messageId" to messageId, "progress" to pct, "bytesWritten" to written, "totalBytes" to total, "status" to "Subiendo ($pct%)"))
                }
            }
            val isVideo = shouldRouteVideoToVcdn(typeLabel, mimeType)
            val uploadResult = if (isVideo) {
                VideoRouter.uploadPublicVideo(
                    file = file,
                    mimeType = mimeType,
                    userId = userId,
                    uploadType = typeLabel,
                    customFileName = stableFileName,
                    clientMessageUuid = stableUuid,
                    onProgress = progressCb
                )
            } else {
                UploadFailoverRouter.uploadWithFailover(
                    file = file,
                    mimeType = mimeType,
                    userId = userId,
                    uploadType = typeLabel,
                    customFileName = stableFileName,
                    clientMessageUuid = stableUuid,
                    onProgress = progressCb
                ) {
                    PanalinkMediaManager.uploadMediaAndThumbnail(
                        context = context,
                        mediaFile = file,
                        mimeType = mimeType,
                        typeLabel = typeLabel,
                        userId = userId,
                        caption = entity.content ?: "Multimedia message"
                    )
                }
            }

            if (uploadResult.isSuccess) {
                val mediaInfo = uploadResult.getOrThrow()
                val updatedEntity = entity.copy(
                    mediaUrl = mediaInfo.url,
                    thumbnailUrl = mediaInfo.thumbnailUrl ?: entity.thumbnailUrl?.takeIf { it.startsWith("http") },
                    mediaMime = mediaInfo.mime ?: entity.mediaMime,
                    mediaSize = mediaInfo.size ?: entity.mediaSize,
                    mediaDuration = mediaInfo.duration?.takeIf { it > 0L } ?: entity.mediaDuration,
                    mediaWidth = mediaInfo.width?.takeIf { it > 0 } ?: entity.mediaWidth,
                    mediaHeight = mediaInfo.height?.takeIf { it > 0 } ?: entity.mediaHeight,
                    localMediaUri = null,
                    status = "sending"
                )
                val effectiveClearedAt = messagesRepository.getEffectiveClearedAt(updatedEntity.chatId, null)
                val shouldKeep = com.example.util.MessageFilter.shouldKeepMessage(messageId = updatedEntity.id, messageClientUuid = updatedEntity.clientMessageUuid, messageCreatedAt = updatedEntity.createdAt, lastClearedAt = effectiveClearedAt, deletedMessageIds = messagesRepository.getUserDeletedMessageIds())
                if (shouldKeep) {
                    messageDao.insertMessage(updatedEntity)
                    entity.localMediaUri?.let { runCatching { File(it).delete() } }
                    entity.localThumbnailUri?.let { runCatching { File(it).delete() } }
                } else {
                    messageDao.deleteMessageById(updatedEntity.id)
                    entity.localMediaUri?.let { runCatching { File(it).delete() } }
                    entity.localThumbnailUri?.let { runCatching { File(it).delete() } }
                }
                messagesRepository.scheduleSync()
                logFinalStateAndResult(messageId, Result.success())
            } else {
                val error = uploadResult.exceptionOrNull()
                val willRetry = File(localUri).exists() && runAttemptCount + 1 < MAX_UPLOAD_ATTEMPTS
                if (willRetry) {
                    logFinalStateAndResult(messageId, Result.retry())
                } else {
                    markFailed(messageId)
                    logFinalStateAndResult(messageId, Result.failure())
                }
            }
        } catch (e: Exception) {
            val willRetry = (!localUri.isNullOrBlank() && File(localUri).exists()) && (runAttemptCount + 1 < MAX_UPLOAD_ATTEMPTS)
            if (!willRetry) {
                markFailed(messageId)
                logFinalStateAndResult(messageId, Result.failure())
            } else {
                logFinalStateAndResult(messageId, Result.retry())
            }
        }
    }

    private suspend fun logFinalStateAndResult(messageId: String, result: Result): Result {
        try {
            val finalEntity = messageDao.getMessageById(messageId)
            val resultName = when (result) {
                is Result.Success -> "SUCCESS"
                is Result.Retry -> "RETRY"
                else -> "FAILURE"
            }
            Log.i(TAG, "MEDIA_UPLOAD_FINAL_STATE: messageId=$messageId, status=${finalEntity?.status}, hasMediaUrl=${!finalEntity?.mediaUrl.isNullOrBlank()}, hasLocalMediaUri=${!finalEntity?.localMediaUri.isNullOrBlank()}, retries=$runAttemptCount")
            Log.i(TAG, "MEDIA_WORK_RESULT = $resultName (messageId=$messageId)")
        } catch (e: Exception) {
            Log.w(TAG, "Error logging final state for $messageId", e)
        }
        return result
    }
}

private fun detectImageMime(file: java.io.File): String {
    return try {
        java.io.BufferedInputStream(file.inputStream()).use { input ->
            val header = ByteArray(12)
            val read = input.read(header)
            var png = read >= 4
            if (read < 4) png = false
            var jpeg = read >= 2
            if (read < 2) jpeg = false
            var gif = read >= 3
            if (read < 3) gif = false
            var webp = read >= 12
            if (read < 12) webp = false
            if (png && header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()) return "image/png"
            if (jpeg && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()) return "image/jpeg"
            if (gif && header[0] == 0x47.toByte() && header[1] == 0x49.toByte() && header[2] == 0x46.toByte()) return "image/gif"
            if (webp && header[0] == 0x52.toByte() && header[1] == 0x49.toByte() && header[2] == 0x46.toByte() && header[3] == 0x46.toByte() && header[8] == 0x57.toByte() && header[9] == 0x45.toByte() && header[10] == 0x42.toByte() && header[11] == 0x50.toByte()) return "image/webp"
            "image/jpeg"
        }
    } catch (_: Exception) {
        "image/jpeg"
    }
}
