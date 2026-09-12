package com.example.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.room.withTransaction
import com.example.data.database.PanalinkDatabase
import com.example.data.database.PendingPostEntity
import com.example.data.database.PendingPostMediaDao
import com.example.data.database.PendingPostMediaEntity
import com.example.data.database.PendingPostMediaStatus
import com.example.data.model.PostDto
import com.example.data.model.UploadMediaResult
import com.example.data.repository.FeedRepository
import com.example.data.repository.FeedRepositoryImpl
import com.example.data.repository.UploadRepository
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

open class PostUploadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val TAG = "PostUploadWorker"
    protected open val uploadRepository: UploadRepository = UploadRepository()
    protected open val feedRepository: FeedRepository = FeedRepositoryImpl()
    protected open val database: PanalinkDatabase
        get() = PanalinkDatabase.getDatabase(applicationContext)

    override suspend fun doWork(): Result {
        if (!com.example.util.NetworkMonitor.isOnline.value) {
            return Result.retry()
        }

        val pendingPostId = inputData.getString("pendingPostId") ?: return Result.failure()
        val serverPostId = inputData.getString("serverPostId")
        val db = database
        val pendingPostDao = db.pendingPostDao()
        val mediaDao = db.pendingPostMediaDao()

        val pendingPost = pendingPostDao.getPostById(pendingPostId) ?: return Result.failure()

        val effectiveUserId = if (pendingPost.userId.isNotBlank()) {



            pendingPost.userId
        } else {
            com.example.data.supabase.SupabaseClient.currentUser?.id ?: ""
        }

        if (effectiveUserId.isBlank()) {
            Log.e(TAG, "Cannot process post: userId is blank")
            pendingPostDao.updateStatusAndProgress(pendingPostId, "failed", 0f)
            UploadRepository.setGlobalProgress(null)
            return Result.failure()
        }

        Log.i(TAG, "Starting post upload for pendingPostId $pendingPostId, type ${pendingPost.type}, userId $effectiveUserId")

        var mediaRows = mediaDao.getMediaForPost(pendingPostId)

        if (mediaRows.isEmpty() && pendingPost.mediaUrisJson.isNotBlank() && pendingPost.mediaUrisJson != "[]") {
            mediaRows = reconcileMediaRows(pendingPost, mediaDao, db)
        }

        val terminalMedia = mediaRows.firstOrNull { it.status == PendingPostMediaStatus.FAILED_TERMINAL }
        if (terminalMedia != null) {
            Log.w(TAG, "Stopping post $pendingPostId because media ${terminalMedia.id} is in FAILED_TERMINAL state; leaving durable Room state unchanged.")
            UploadRepository.setGlobalProgress(null)
            return Result.failure()
        }

        pendingPostDao.updateStatusAndProgress(pendingPostId, "uploading", 0f)
        UploadRepository.setGlobalProgress(0f)

        val mediaUrls = mutableListOf<String>()
        var mediaKind: String? = null
        val totalItems = (mediaRows.size + 1).coerceAtLeast(1)

        try {
            mediaRows.forEachIndexed { index, seededRow ->
                val mediaRow = mediaDao.getMediaById(seededRow.id) ?: seededRow

                if (mediaRow.status == PendingPostMediaStatus.FAILED_TERMINAL) {
                    Log.w(TAG, "Media ${mediaRow.id} is FAILED_TERMINAL; stopping without re-uploading.")
                    UploadRepository.setGlobalProgress(null)
                    return Result.failure()
                }

                if (mediaRow.status == PendingPostMediaStatus.UPLOADED) {
                    if (!mediaRow.remoteUrl.isNullOrBlank()) {
                        mediaUrls.add(mediaRow.remoteUrl)
                    } else {
                        mediaDao.markFailed(
                            mediaRow.id,
                            PendingPostMediaStatus.FAILED_RETRYABLE,
                            "UPLOADED without remoteUrl",
                            null,
                            null,
                            System.currentTimeMillis()
                        )
                        return Result.retry()
                    }
                    return@forEachIndexed
                }

                val now = System.currentTimeMillis()
                mediaDao.updateStatus(mediaRow.id, PendingPostMediaStatus.UPLOADING, now)

                val uri = Uri.parse(mediaRow.localUri)

                val tempFile = createTempFileFromUri(uri)
                if (tempFile == null) {
                    Log.e(TAG, "Failed to resolve URI: ${mediaRow.localUri}")
                    mediaDao.markFailed(
                        mediaRow.id,
                        PendingPostMediaStatus.FAILED_RETRYABLE,
                        "Failed to resolve URI: ${mediaRow.localUri}",
                        null,
                        null,
                        System.currentTimeMillis()
                    )
                    return Result.retry()
                }

                val mimeType = if (mediaRow.mimeType.isNotBlank() && mediaRow.mimeType != "application/octet-stream") {
                    mediaRow.mimeType
                } else {
                    context.contentResolver.getType(uri) ?: "application/octet-stream"
                }
                if (mediaKind == null) mediaKind = kindForMime(mimeType)

                // FIX (Acción 2 - codec 4006): Transcode videos (especially HEVC/H.265 10-bit)
                // to standard H.264/AVC before uploading to VCDN. This prevents hardware codec
                // failures on devices that don't fully support HEVC/10-bit decode, which causes
                // MediaCodecVideoDecoderException and freezes the reel feed.
                var finalUploadFile = tempFile
                if (mimeType.startsWith("video/") && !tempFile.name.contains("_compressed_")) {
                    try {
                        val compressed = com.example.util.VideoCompressorHelper.compressVideo(
                            context,
                            uri,
                            tempFile,
                            { }
                        )
                        if (compressed.exists() && compressed.length() > 0 && compressed.absolutePath != tempFile.absolutePath) {
                            finalUploadFile = compressed
                        } else if (compressed.exists() && compressed.absolutePath != tempFile.absolutePath) {
                            compressed.delete()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Video compression failed, uploading original", e)
                    }
                }

                val ext = if (finalUploadFile.name.contains(".")) finalUploadFile.name.substringAfterLast(".") else "bin"
                val stableFileName = "post_${pendingPostId}_${mediaRow.mediaIndex}.$ext"

                Log.d(TAG, "Uploading file $finalUploadFile with mimeType $mimeType, stableFileName $stableFileName")

                val uploadResult = performUpload(
                    file = finalUploadFile,
                    mimeType = mimeType,
                    mediaKind = mediaKind,
                    userId = effectiveUserId,
                    stableFileName = stableFileName,
                    clientMessageUuid = pendingPost.id,
                    onProgress = { bytes, total ->
                        val itemProgress = bytes.toFloat() / total.toFloat().coerceAtLeast(1f)
                        val totalProgress = (index + itemProgress) / totalItems
                        UploadRepository.setGlobalProgress(totalProgress)
                    }
                )

                if (uploadResult.isSuccess) {
                    val result = uploadResult.getOrNull()
                    val publicUrl = result?.url
                    if (publicUrl != null) {
                        // Invariante principal: persistir en Room ANTES de continuar con el siguiente archivo.


                        mediaDao.markUploaded(
                            mediaRow.id,
                            PendingPostMediaStatus.UPLOADED,
                            null,
                            publicUrl,
                            System.currentTimeMillis()
                        )
                        mediaUrls.add(publicUrl)



                        // Ya hay evidencia durable (UPLOADED + remote ref persistidos en Room):
                        // el archivo temporal puede limpiarse de forma segura..

                        tempFile.delete()
                        if (finalUploadFile.absolutePath != tempFile.absolutePath) finalUploadFile.delete()

                        val completedProgress = (index + 1f) / totalItems
                        pendingPostDao.updateStatusAndProgress(pendingPostId, "uploading", completedProgress)
                    } else {
                        tempFile.delete()
                        if (finalUploadFile.absolutePath != tempFile.absolutePath) finalUploadFile.delete()
                        mediaDao.markFailed(
                            mediaRow.id,
                            PendingPostMediaStatus.FAILED_RETRYABLE,
                            "Upload succeeded but no public URL",
                            null,
                            null,
                            System.currentTimeMillis()
                        )
                        return Result.retry()
                    }
                } else {
                    tempFile.delete()
                    if (finalUploadFile.absolutePath != tempFile.absolutePath) finalUploadFile.delete()
                    val err = uploadResult.exceptionOrNull()?.message ?: "Upload failed"
                    mediaDao.markFailed(
                        mediaRow.id,
                        PendingPostMediaStatus.FAILED_RETRYABLE,
                        err,
                        null,
                        null,
                        System.currentTimeMillis()
                    )
                    return Result.retry()
                }
            }

            val uploadedCount = mediaDao.countUploaded(pendingPostId)
            val totalCount = mediaDao.countMedia(pendingPostId)


            if (totalCount > 0 && uploadedCount < totalCount) {


                return Result.retry()
            }

            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }

            val previewMetadataMap: Map<String, String>? = try {
                if (!pendingPost.previewDataJson.isNullOrBlank()) {
                    val jsonObject = org.json.JSONObject(pendingPost.previewDataJson)
                    val map = mutableMapOf<String, String>()
                    jsonObject.keys().forEach { key ->
                        map[key] = jsonObject.getString(key)


                    }
                    map
                } else {
                    val extractedId = com.example.util.YouTubeUrlParser.extractYouTubeVideoId(pendingPost.content ?: "")
                    if (!extractedId.isNullOrBlank()) {
                        mapOf(
                            "provider" to "youtube",
                            "video_id" to extractedId,
                            "title" to "Video de YouTube",
                            "thumbnail_url" to "https://img.youtube.com/vi/$extractedId/hqdefault.jpg",
                            "embed_url" to "https://www.youtube.com/embed/$extractedId"
                        )
                    } else null
                }
            } catch (e: Exception) {
                null
            }


            val orderedUrls = mediaDao.getMediaForPost(pendingPostId).
                filter { it.status == PendingPostMediaStatus.UPLOADED && !it.remoteUrl.isNullOrBlank() }.
                map { it.remoteUrl!! }

            val mediaType = if (orderedUrls.size > 1) "ALBUM" else mediaKind
            val finalType = if (previewMetadataMap != null) "YOUTUBE"
                else if (pendingPost.type != "TEXT") pendingPost.type
                else mediaType ?: "TEXT"


            val postDto = PostDto(
                id = serverPostId,
                userId = effectiveUserId,
                type = finalType,
                content = pendingPost.content,
                mediaUrls = orderedUrls,
                privacy = pendingPost.privacy,
                createdAt = sdf.format(java.util.Date()),
                previewMetadata = previewMetadataMap
            )


            val createResult = feedRepository.createPost(postDto )

            if (createResult.isSuccess) {
                Log.i(TAG, "Feed post created successfully!")


                db.withTransaction {



                    mediaDao.deleteForPost(pendingPostId)




                    pendingPostDao.deletePostById(pendingPostId)

                }
                UploadRepository.setGlobalProgress(null)
                UploadRepository.triggerUploadSuccess()
                return Result.success()
            } else {
                Log.e(TAG, "Failed to create feed post", createResult.exceptionOrNull())
                pendingPostDao.updateStatusAndProgress(pendingPostId, "failed", 0f)
                UploadRepository.setGlobalProgress(null)
                return Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during post upload", e)
            pendingPostDao.updateStatusAndProgress(pendingPostId, "failed", 0f)
            UploadRepository.setGlobalProgress(null)
            return Result.retry()
        }
    }

    private suspend fun reconcileMediaRows(
        pendingPost: PendingPostEntity,
        mediaDao: PendingPostMediaDao,
        db: PanalinkDatabase
    ): List<PendingPostMediaEntity> {



        val uris = try {
            val jsonArray = org.json.JSONArray(pendingPost.mediaUrisJson)
            List(jsonArray.length()) { jsonArray.getString(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse mediaUrisJson", e)
            emptyList()
        }

        if (uris.isEmpty()) return emptyList()


        val now = System.currentTimeMillis()
        val rows = uris.mapIndexed { index, uriStr ->
            val mimeType = try {
                context.contentResolver.getType(Uri.parse(uriStr)) ?: "application/octet-stream"
            } catch (e: Exception) {
                "application/octet-stream"
            }
            PendingPostMediaEntity(
                id = "${pendingPost.id}:$index",
                postId = pendingPost.id,
                mediaIndex = index,
                localUri = uriStr,
                mimeType = mimeType,
                sizeBytes = 0L,
                status = PendingPostMediaStatus.PENDING,
                updatedAt = now
            )
        }

        db.withTransaction {
            if (mediaDao.getMediaForPost(pendingPost.id).isEmpty()) {
                rows.forEach { mediaDao.upsertMedia(it) }
            }
        }

        return mediaDao.getMediaForPost(pendingPost.id)




    }

    protected open suspend fun performUpload(
        file: File,
        mimeType: String,
        mediaKind: String?,
        userId: String,
        stableFileName: String,
        clientMessageUuid: String,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
    ): kotlin.Result<UploadMediaResult> {
        val isPublicVideo = mimeType.startsWith("video/") && mediaKind == "VIDEO"
        return if (isPublicVideo) {
            com.example.data.repository.VideoRouter.uploadPublicVideo(
                file = file,
                mimeType = mimeType,
                userId = userId,
                uploadType = "POST",
                customFileName = stableFileName,
                clientMessageUuid = clientMessageUuid,
                onProgress = onProgress
            )
        } else {
            com.example.data.repository.UploadFailoverRouter.uploadWithFailover(
                file = file,
                mimeType = mimeType,
                userId = userId,
                uploadType = "POST",
                customFileName = stableFileName,
                clientMessageUuid = clientMessageUuid,
                onProgress = onProgress
            ) { progress ->
                uploadRepository.uploadVideo(
                    mediaFile = file,
                    mediaMimeType = mimeType,
                    caption = "Feed Post Media",
                    userId = userId,
                    stableFileName = stableFileName,
                    onProgress = progress
                )
            }
        }
    }

    private fun kindForMime(mime: String): String? = when {
        mime.startsWith("video/") -> "VIDEO"
        mime.startsWith("audio/") -> "AUDIO"
        mime.startsWith("image/") -> "IMAGE"
        else -> null
    }

    private fun createTempFileFromUri(uri: Uri): File? {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return null

            var originalName = ""
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        originalName = cursor.getString(nameIndex)
                    }
                }
            }

            val safeName = if (originalName.isNotBlank()) {
                val nameWithoutExt = originalName.substringBeforeLast(".")
                nameWithoutExt.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30) + "-"
            } else {
                "feed_upload_tmp_"
            }

            val extension = if (originalName.contains(".")) {
                "." + originalName.substringAfterLast(".")
            } else {
                ""
            }

            val tempFile = File(context.cacheDir, "$safeName${System.currentTimeMillis()}$extension")
            FileOutputStream(tempFile).use { outputStream ->
                inputStream.copyTo(outputStream)



            }
            return tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving URI to temp file: $uri", e)
            return null
        }
    }

    companion object {
        @JvmStatic
        fun shouldSkipUpload(status: String): Boolean = status == PendingPostMediaStatus.UPLOADED ||
            status == PendingPostMediaStatus.FAILED_TERMINAL

        @JvmStatic
        fun shouldAttemptUpload(status: String): Boolean = status == PendingPostMediaStatus.PENDING ||
            status == PendingPostMediaStatus.FAILED_RETRYABLE ||
            status == PendingPostMediaStatus.UPLOADING

        @JvmStatic
        fun uploadedUrls(mediaRows: List<PendingPostMediaEntity>): List<String> =
            mediaRows
                .filter { it.status == PendingPostMediaStatus.UPLOADED && !it.remoteUrl.isNullOrBlank() }
                .map { it.remoteUrl!! }

        @JvmStatic
        fun allMediaUploaded(total: Int, uploaded: Int): Boolean = total == 0 || uploaded >= total
    }
}
