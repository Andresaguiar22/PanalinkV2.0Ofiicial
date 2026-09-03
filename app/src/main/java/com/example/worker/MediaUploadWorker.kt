package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.database.PanalinkDatabase
import com.example.data.repository.MessagesRepository
import com.example.data.repository.UploadFailoverRouter
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

        Log.i(
            TAG,
            "MEDIA_UPLOAD_INIT: messageId=$messageId, runAttemptCount=$runAttemptCount, fileExists=$fileExists, fileSizeBytes=$fileSizeBytes, messageType=${entity.messageType}, localMediaUri=$localUri, roomStatus=${entity.status}, receiverId=${entity.receiverId}, clientMessageUuid=${entity.clientMessageUuid}"
        )

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

            // Album de imagenes: el entity junta los paths locales con ",".
            // Subimos cada uno y guardamos las URLs remotas tambien con ",".
            if (entity.messageType == "image" && localUri.contains(",")) {
                Log.i(TAG, "Detected image album (paths joined by ',')")
                val allPaths = localUri.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val remoteUrls = mutableListOf<String>()
                val uploadedPaths = mutableListOf<String>()
                var remoteThumb: String? = null
                
                Log.i(TAG, "MEDIA_UPLOAD_START: messageId=$messageId, type=album[${allPaths.size}], hasLocalMediaUri=true, attempt=$runAttemptCount")
                allPaths.forEachIndexed { index, path ->
                    val albumFile = File(path)
                    if (albumFile.exists()) {
                        val mime = detectImageMime(albumFile)
                        val stableKey = "${stableUuid}_$index"
                        val ext = if (albumFile.name.contains(".")) albumFile.name.substringAfterLast(".") else "jpg"
                        val stableFileName = "${stableKey}.$ext"
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
                            remoteUrls += res.getOrThrow().url
                            if (remoteThumb == null) remoteThumb = res.getOrThrow().thumbnailUrl
                            uploadedPaths += path
                        } else {
                            Log.e(TAG, "Album image upload failed: ${res.exceptionOrNull()?.message}")
                        }
                    }
                }

                // Album atomico: NUNCA confirmar un album parcial ante fallos transitorios
                // (red/B2/JWT). Las imagenes que fallen reintentaran con el worker (backoff)
                // y el sync revivira "sending" siempre que quede archivo local utilizable.
                // Si TODOS los archivos locales se perdieron, es irrecuperable -> "failed".
                val extantPaths = allPaths.filter { File(it).exists() }
                if (extantPaths.isEmpty()) {
                    Log.e(TAG, "Album: no local files remain; marking failed (irrecuperable)")
                    Log.e(TAG, "MEDIA_UPLOAD_FAILURE: messageId=$messageId, exception=IllegalStateException, message=No local files remain, attempt=$runAttemptCount, returningResult=FAILURE")
                    markFailed(messageId)
                    return logFinalStateAndResult(messageId, Result.failure())
                }

                if (remoteUrls.size != extantPaths.size) {
                    if (runAttemptCount + 1 < MAX_UPLOAD_ATTEMPTS) {
                        Log.w(TAG, "Album: partial upload ${remoteUrls.size}/${extantPaths.size}; retrying whole album (no commit)")
                        Log.w(TAG, "MEDIA_UPLOAD_FAILURE: messageId=$messageId, exception=PartialUploadException, message=Uploaded ${remoteUrls.size}/${extantPaths.size}, attempt=$runAttemptCount, returningResult=RETRY")
                        return logFinalStateAndResult(messageId, Result.retry())
                    }
                    Log.w(TAG, "Album: still partial after $runAttemptCount attempts; marking failed (sync will revive)")
                    Log.e(TAG, "MEDIA_UPLOAD_FAILURE: messageId=$messageId, exception=PartialUploadException, message=Max attempts reached, attempt=$runAttemptCount, returningResult=FAILURE")
                    markFailed(messageId)
                    return logFinalStateAndResult(messageId, Result.failure())
                }

                Log.i(
                    TAG,
                    "MEDIA_UPLOAD_SUCCESS: messageId=$messageId, type=image_album, hasMediaUrl=true, hasThumbnail=${remoteThumb != null}, size=${uploadedPaths.size}, duration=0"
                )

                val updated = entity.copy(
                    mediaUrl = remoteUrls.joinToString(","),
                    thumbnailUrl = remoteThumb ?: entity.thumbnailUrl?.takeIf { it.startsWith("http") },
                    localMediaUri = null,
                    status = "sending"
                )
                messageDao.insertMessage(updated)
                // Originals confirmados en Room: ya no se necesitan. El thumbnail remoto (si
                // hubo) se persiste en thumbnail_url; si no, el local se conserva para la UI.
                uploadedPaths.forEach { runCatching { java.io.File(it).delete() } }
                messagesRepository.scheduleSync()
                return logFinalStateAndResult(messageId, Result.success())
            }

            val file = File(localUri)
            if (!file.exists()) {
                Log.e(TAG, "Local file does not exist: $localUri")
                Log.e(TAG, "MEDIA_UPLOAD_FAILURE: messageId=$messageId, exception=FileNotFoundException, message=Local file missing, attempt=$runAttemptCount, returningResult=FAILURE")
                markFailed(messageId)
                return logFinalStateAndResult(messageId, Result.failure())
            }

            val mimeType = entity.mediaMime ?: "application/octet-stream"
            val typeLabel = entity.messageType ?: "text"
            val userId = entity.senderId
            val stableKey = "${stableUuid}_0"
            val ext = if (file.name.contains(".")) file.name.substringAfterLast(".") else "bin"
            val stableFileName = "${stableKey}.$ext"

            Log.i(TAG, "Processing and uploading $typeLabel ($mimeType), size=${file.length()} bytes, stableKey=$stableKey")
            Log.i(TAG, "MEDIA_UPLOAD_START: messageId=$messageId, type=$typeLabel, runAttemptCount=$runAttemptCount, fileExists=${file.exists()}, sizeBytes=${file.length()}, mimeType=$mimeType")

            // Failover total para TODO tipo de media: CDN primero (conserva thumbnails
            // server-side); si falla, B2. El circuit breaker evita quemar timeouts.
            val progressCb: (Long, Long) -> Unit = { written, total ->
                if (total > 0L) {
                    val pct = ((written.toDouble() / total.toDouble()) * 100.0).toInt()
                    setProgressAsync(androidx.work.workDataOf(
                        "messageId" to messageId,
                        "progress" to pct,
                        "bytesWritten" to written,
                        "totalBytes" to total,
                        "status" to "Subiendo ($pct%)"
                    ))
                }
            }
            val uploadResult = UploadFailoverRouter.uploadWithFailover(
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

            if (uploadResult.isSuccess) {
                val mediaInfo = uploadResult.getOrThrow()
                Log.i(
                    TAG,
                    "MEDIA_UPLOAD_SUCCESS: messageId=$messageId, type=$typeLabel, runAttemptCount=$runAttemptCount, hasMediaUrl=${!mediaInfo.url.isNullOrBlank()}, hasThumbnail=${!mediaInfo.thumbnailUrl.isNullOrBlank()}, sizeBytes=${mediaInfo.size ?: file.length()}, duration=${mediaInfo.duration ?: entity.mediaDuration ?: 0L}"
                )

                val updatedEntity = entity.copy(
                    mediaUrl = mediaInfo.url,
                    thumbnailUrl = mediaInfo.thumbnailUrl ?: entity.thumbnailUrl?.takeIf { it.startsWith("http") },
                    mediaMime = mediaInfo.mime ?: entity.mediaMime,
                    mediaSize = mediaInfo.size ?: entity.mediaSize,
                    // The B2 fallback returns 0 for media it can't probe; keep the
                    // locally extracted metadata in that case.
                    mediaDuration = mediaInfo.duration?.takeIf { it > 0L } ?: entity.mediaDuration,
                    mediaWidth = mediaInfo.width?.takeIf { it > 0 } ?: entity.mediaWidth,
                    mediaHeight = mediaInfo.height?.takeIf { it > 0 } ?: entity.mediaHeight,
                    localMediaUri = null,
                    status = "sending"
                )

                val effectiveClearedAt = messagesRepository.getEffectiveClearedAt(updatedEntity.chatId, null)
                val shouldKeep = com.example.util.MessageFilter.shouldKeepMessage(
                    messageId = updatedEntity.id,
                    messageClientUuid = updatedEntity.clientMessageUuid,
                    messageCreatedAt = updatedEntity.createdAt,
                    lastClearedAt = effectiveClearedAt,
                    deletedMessageIds = messagesRepository.getUserDeletedMessageIds()
                )
                if (shouldKeep) {
                    messageDao.insertMessage(updatedEntity)
                    Log.i(TAG, "MEDIA_ROOM_WRITE: messageId=$messageId, success=true, writtenStatus=${updatedEntity.status}, hasMediaUrl=${!updatedEntity.mediaUrl.isNullOrBlank()}")
                    // Upload confirmado en Room con mediaUrl remoto: los paths locales ya no se necesitan.
                    entity.localMediaUri?.let { runCatching { java.io.File(it).delete() } }
                    entity.localThumbnailUri?.let { runCatching { java.io.File(it).delete() } }
                } else {
                    messageDao.deleteMessageById(updatedEntity.id)
                    Log.i(TAG, "MEDIA_ROOM_WRITE: messageId=$messageId, success=true, deleted=true (filtered)")
                    // Mensaje filtrado (no visible): limpiar igualmente los locales.
                    entity.localMediaUri?.let { runCatching { java.io.File(it).delete() } }
                    entity.localThumbnailUri?.let { runCatching { java.io.File(it).delete() } }
                }

                messagesRepository.scheduleSync()
                logFinalStateAndResult(messageId, Result.success())
            } else {
                val error = uploadResult.exceptionOrNull()
                val willRetry = File(localUri).exists() && runAttemptCount + 1 < MAX_UPLOAD_ATTEMPTS
                val resultLabel = if (willRetry) "RETRY" else "FAILURE"
                val sanitizedMsg = error?.message?.replace(Regex("eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+"), "[REDACTED_TOKEN]")?.take(200) ?: "Unknown error"
                Log.e(
                    TAG,
                    "MEDIA_UPLOAD_FAILURE: messageId=$messageId, runAttemptCount=$runAttemptCount, exception=${error?.javaClass?.simpleName ?: "Exception"}, message=$sanitizedMsg, returningResult=$resultLabel",
                    error
                )
                if (willRetry) {
                    logFinalStateAndResult(messageId, Result.retry())
                } else {
                    // Terminal cuando se agotan los reintentos (MAX_UPLOAD_ATTEMPTS) o el archivo local no existe.
                    // Queda en 'failed' hasta un reintento manual explícito vía retryMessage().
                    markFailed(messageId)
                    Log.i(TAG, "MEDIA_ROOM_WRITE: messageId=$messageId, success=true, writtenStatus=failed, attemptsExhausted=true")
                    logFinalStateAndResult(messageId, Result.failure())
                }
            }
        } catch (e: Exception) {
            val willRetry = (!localUri.isNullOrBlank() && File(localUri).exists()) && (runAttemptCount + 1 < MAX_UPLOAD_ATTEMPTS)
            val resultLabel = if (willRetry) "RETRY" else "FAILURE"
            val sanitizedMsg = e.localizedMessage?.replace(Regex("eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+"), "[REDACTED_TOKEN]")?.take(200) ?: "Unknown error"
            Log.e(
                TAG,
                "MEDIA_UPLOAD_FAILURE: messageId=$messageId, exception=${e.javaClass.simpleName}, message=$sanitizedMsg, attempt=$runAttemptCount, returningResult=$resultLabel",
                e
            )
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
            Log.i(
                TAG,
                "MEDIA_UPLOAD_FINAL_STATE: messageId=$messageId, status=${finalEntity?.status}, hasMediaUrl=${!finalEntity?.mediaUrl.isNullOrBlank()}, hasLocalMediaUri=${!finalEntity?.localMediaUri.isNullOrBlank()}, retries=$runAttemptCount"
            )
            Log.i(TAG, "MEDIA_WORK_RESULT = $resultName (messageId=$messageId)")
        } catch (e: Exception) {
            Log.w(TAG, "Error logging final state for $messageId", e)
        }
        return result
    }
}

/**
 * Detecta el MIME real de una imagen por magic bytes. El entity del album guarda un
 * unico mediaMime para todas las fotos (a menudo "image/jpeg" generico); subir cada una
 * con su tipo real evita problemas de content-type en B2/CDN. Si no se reconoce,
 * cae a "image/jpeg".
 */
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
            if (png) {
                if (header[0] == 0x89.toByte()) {
                    if (header[1] == 0x50.toByte()) {
                        if (header[2] == 0x4E.toByte()) {
                            if (header[3] == 0x47.toByte()) {
                                return "image/png"
                            }
                        }
                    }
                }
            }
            if (jpeg) {
                if (header[0] == 0xFF.toByte()) {
                    if (header[1] == 0xD8.toByte()) {
                        return "image/jpeg"
                    }
                }
            }
            if (gif) {
                if (header[0] == 0x47.toByte()) {
                    if (header[1] == 0x49.toByte()) {
                        if (header[2] == 0x46.toByte()) {
                            return "image/gif"
                        }
                    }
                }
            }
            if (webp) {
                if (header[0] == 0x52.toByte()) {
                    if (header[1] == 0x49.toByte()) {
                        if (header[2] == 0x46.toByte()) {
                            if (header[3] == 0x46.toByte()) {
                                if (header[8] == 0x57.toByte()) {
                                    if (header[9] == 0x45.toByte()) {
                                        if (header[10] == 0x42.toByte()) {
                                            if (header[11] == 0x50.toByte()) {
                                                return "image/webp"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "image/jpeg"
        }
    } catch (_: Exception) {
        "image/jpeg"
    }
}
