package com.example.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.model.PostDto
import com.example.data.repository.FeedRepositoryImpl
import com.example.data.repository.UploadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class PostUploadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val TAG = "PostUploadWorker"
    private val uploadRepository = UploadRepository()
    private val feedRepository = FeedRepositoryImpl()

    override suspend fun doWork(): Result {
        // Red real VALIDATED (no solo CONNECTED: si no hay internet, devolver a la cola.
        if (!com.example.util.NetworkMonitor.isOnline.value) {
            return Result.retry()
        }
        val pendingPostId = inputData.getString("pendingPostId") ?: return Result.failure()
        val serverPostId = inputData.getString("serverPostId")
        val db = com.example.data.database.PanalinkDatabase.getDatabase(context)
        val pendingPostDao = db.pendingPostDao()
        
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

        pendingPostDao.updateStatusAndProgress(pendingPostId, "uploading", 0f)
        UploadRepository.setGlobalProgress(0f)

        val uris = try {
            val jsonArray = org.json.JSONArray(pendingPost.mediaUrisJson)
            List(jsonArray.length()) { jsonArray.getString(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse mediaUrisJson", e)
            emptyList()
        }

        val mediaUrls = mutableListOf<String>()
        val totalItems = uris.size + 1 // +1 for the final post creation
        var mediaKind: String? = null

        try {
            uris.forEachIndexed { index, uriStr ->
                val uri = Uri.parse(uriStr)
                
                val tempFile = createTempFileFromUri(uri)
                if (tempFile == null) {
                    Log.e(TAG, "Failed to resolve URI: $uriStr")
                    return Result.failure()
                }

                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                if (mediaKind == null) mediaKind = kindForMime(mimeType)
                val ext = if (tempFile.name.contains(".")) tempFile.name.substringAfterLast(".") else "bin"
                val stableFileName = "post_${pendingPostId}_$index.$ext"

                Log.d(TAG, "Uploading file $tempFile with mimeType $mimeType, stableFileName $stableFileName")
                // Failover total: CDN primero; si falla, B2 (aplica a TODO tipo de media).
                // VCDN (proxy edge function) para video PUBLICO del muro; el resto de
                // media del post sigue por UploadFailoverRouter (B2/CDN) sin tocarlo.
                val isPublicVideo = mimeType.startsWith("video/") && mediaKind == "VIDEO"
                val postClientUuid = pendingPost.id
                val uploadResult = if (isPublicVideo) {
                    com.example.data.repository.VideoRouter.uploadPublicVideo(
                        file = tempFile,
                        mimeType = mimeType,
                        userId = effectiveUserId,
                        uploadType = "POST",
                        customFileName = stableFileName,
                        clientMessageUuid = postClientUuid,
                        onProgress = { bytes, total ->
                            val itemProgress = bytes.toFloat() / total.toFloat().coerceAtLeast(1f)
                            val totalProgress = (index + itemProgress) / totalItems
                            // Progreso Room persistido por archivo tras el upload (sin runBlocking ni N escrituras).
                        }
                    )
                } else {
                    com.example.data.repository.UploadFailoverRouter.uploadWithFailover(
                        file = tempFile,
                        mimeType = mimeType,
                        userId = effectiveUserId,
                        uploadType = "POST",
                        customFileName = stableFileName,
                        clientMessageUuid = postClientUuid,
                        onProgress = { bytes, total ->
                            val itemProgress = bytes.toFloat() / total.toFloat().coerceAtLeast(1f)
                            val totalProgress = (index + itemProgress) / totalItems
                            UploadRepository.setGlobalProgress(totalProgress)
                            // Progreso Room persistido por archivo tras el upload (sin runBlocking ni N escrituras).
                        }
                    ) { progress ->
                        uploadRepository.uploadVideo(
                            mediaFile = tempFile,
                            mediaMimeType = mimeType,
                            caption = "Feed Post Media",
                            userId = pendingPost.userId,
                            stableFileName = stableFileName,
                            onProgress = progress
                        )
                    }
                }

                tempFile.delete()

                if (uploadResult.isSuccess) {
                    val publicUrl = uploadResult.getOrNull()?.url
                    if (publicUrl != null) {
                        mediaUrls.add(publicUrl)
                        val completedProgress = (index + 1f) / totalItems
                        pendingPostDao.updateStatusAndProgress(pendingPostId, "uploading", completedProgress)
                    } else {
                        return Result.retry()
                    }
                } else {
                    return Result.retry()
                }
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

            // Tipo final: MIME primero (un video nunca debe renderizar como audio/ALBUM),
            // luego el tipo que pidio la UI, y si solo hay texto se mantiene TEXT.
            val mediaType = if (uris.size > 1) "ALBUM" else mediaKind
            val finalType = if (previewMetadataMap != null) "YOUTUBE"
                else if (pendingPost.type != "TEXT") pendingPost.type
                else mediaType ?: "TEXT"

            // Create PostDto
            val postDto = PostDto(
                id = serverPostId,
                userId = effectiveUserId,
                type = finalType,
                content = pendingPost.content,
                mediaUrls = mediaUrls,
                privacy = pendingPost.privacy,
                createdAt = sdf.format(java.util.Date()),
                previewMetadata = previewMetadataMap
            )

            val createResult = feedRepository.createPost(postDto)
            if (createResult.isSuccess) {
                Log.i(TAG, "Feed post created successfully!")
                pendingPostDao.deletePostById(pendingPostId)
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
            
            // Try to extract original filename
            var originalName = ""
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        originalName = cursor.getString(nameIndex)
                    }
                }
            }
            
            // Clean up the original name to use as prefix if possible
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
}
