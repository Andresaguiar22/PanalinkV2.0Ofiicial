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
        val messageId = inputData.getString("messageId") ?: return Result.failure()
        Log.i(TAG, "Starting media upload for message: $messageId (attempt=$runAttemptCount)")

        val entity = messageDao.getMessageById(messageId) ?: return Result.failure()
        val localUri = entity.localMediaUri

        if (localUri.isNullOrBlank()) {
            if (!entity.mediaUrl.isNullOrBlank()) {
                messagesRepository.scheduleSync()
                return Result.success()
            }
            markFailed(messageId)
            return Result.failure()
        }

        return try {
            // Album de imagenes: el entity junta los paths locales con ",".
            // Subimos cada uno y guardamos las URLs remotas tambien con ",".
            if (entity.messageType == "image" && localUri.contains(",")) {
                Log.i(TAG, "Detected image album (paths joined by ',')")
                val allPaths = localUri.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val remoteUrls = mutableListOf<String>()
                val uploadedPaths = mutableListOf<String>()
                var remoteThumb: String? = null
                for (path in allPaths) {
                    val albumFile = File(path)
                    if (!albumFile.exists()) continue
                    val mime = detectImageMime(albumFile)
                    val res = UploadFailoverRouter.uploadWithFailover(
                        file = albumFile,
                        mimeType = mime,
                        userId = entity.senderId,
                        uploadType = "image"
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
                    }
                    else {
                        Log.e(TAG, "Album image upload failed: ${res.exceptionOrNull()?.message}")
                    }
                }

                // Album atomico: NUNCA confirmar un album parcial ante fallos transitorios
                // (red/B2/JWT). Las imagenes que fallen reintentaran con el worker (backoff)
                // y el sync revivira "sending" siempre que quede archivo local utilizable.
                // Si TODOS los archivos locales se perdieron, es irrecuperable -> "failed".
                val extantPaths = allPaths.filter { File(it).exists() }
                if (extantPaths.isEmpty()) {
                    Log.e(TAG, "Album: no local files remain; marking failed (irrecuperable)")
                    markFailed(messageId)
                    return Result.failure()
                }

                if (remoteUrls.size != extantPaths.size) {
                    if (runAttemptCount + 1 < MAX_UPLOAD_ATTEMPTS) {
                        Log.w(TAG, "Album: partial upload ${remoteUrls.size}/${extantPaths.size}; retrying whole album (no commit)")
                        return Result.retry()
                    }
                    Log.w(TAG, "Album: still partial after $runAttemptCount attempts; marking failed (sync will revive)")
                    markFailed(messageId)
                    return Result.failure()
                }

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
                return Result.success()
            }

            val file = File(localUri)
            if (!file.exists()) {
                Log.e(TAG, "Local file does not exist: $localUri")
                markFailed(messageId)
                return Result.failure()
            }

            val mimeType = entity.mediaMime ?: "application/octet-stream"
            val typeLabel = entity.messageType ?: "text"
            val userId = entity.senderId
            Log.i(TAG, "Processing and uploading $typeLabel ($mimeType), size=${file.length()} bytes")
            Log.i(TAG, "Attempting upload with failover router...")

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
                Log.i(TAG, "Upload successful: mediaUrl=${mediaInfo.url}, thumbUrl=${mediaInfo.thumbnailUrl}")

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
                    // Upload confirmado en Room con mediaUrl remoto: los paths locales ya no se necesitan.
                    entity.localMediaUri?.let { runCatching { java.io.File(it).delete() } }
                    entity.localThumbnailUri?.let { runCatching { java.io.File(it).delete() } }
                } else {
                    messageDao.deleteMessageById(updatedEntity.id)
                    // Mensaje filtrado (no visible): limpiar igualmente los locales.
                    entity.localMediaUri?.let { runCatching { java.io.File(it).delete() } }
                    entity.localThumbnailUri?.let { runCatching { java.io.File(it).delete() } }
                }

                messagesRepository.scheduleSync()
                Result.success()
            } else {
                val error = uploadResult.exceptionOrNull()
                Log.e(TAG, "Upload failed (attempt=$runAttemptCount): ${error?.message}", error)
                if (File(localUri).exists() && runAttemptCount + 1 < MAX_UPLOAD_ATTEMPTS) {
                    Result.retry()
                } else {
                    // Terminal SOLO cuando el archivo local ya no existe (fue purgado/perdido:
                    // en ese caso reintentar es imposible.y el estado "failed" informa al usuario.
                    markFailed(messageId)
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in MediaUploadWorker (attempt=$runAttemptCount): ${e.localizedMessage}", e)
            if (runAttemptCount + 1 >= MAX_UPLOAD_ATTEMPTS) {
                markFailed(messageId)
                Result.failure()
            } else {
                Result.retry()
            }
        }
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
