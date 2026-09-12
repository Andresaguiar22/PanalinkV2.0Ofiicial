package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.database.PanalinkDatabase
import com.example.data.database.PendingUploadEntity
import com.example.data.repository.ProfilesRepository
import com.example.data.repository.StatesRepository
import com.example.data.repository.SupabaseStorageRepository
import com.example.data.repository.UploadFailoverRouter
import com.example.data.repository.VideoRouter
import com.example.data.repository.UploadRepository
import com.example.data.supabase.SupabaseClient
import java.io.File

class SocialMediaUploadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val TAG = "SocialMediaUploadWorker"
    private val db = PanalinkDatabase.getDatabase(context)
    private val pendingUploadDao = db.pendingUploadDao()
    private val statesRepository = StatesRepository()
    private val profilesRepository = ProfilesRepository()
    private val supabaseStorage = SupabaseStorageRepository()

    override suspend fun doWork(): Result {
        val uploadId = inputData.getString("uploadId") ?: return Result.failure()
        val entity = pendingUploadDao.getUploadById(uploadId) ?: return Result.failure()
        val file = File(entity.localFilePath)
        val hasRemoteUrl = !entity.remoteUrl.isNullOrBlank()
        if (shouldFailForMissingLocalFile(file.exists(), hasRemoteUrl)) {
            pendingUploadDao.updateUpload(entity.copy(status = "failed", errorMessage = "Archivo local no encontrado", updatedAt = System.currentTimeMillis()))
            return Result.failure()
        }
        val uploadingEntity = entity.copy(status = "uploading", updatedAt = System.currentTimeMillis())
        pendingUploadDao.updateUpload(uploadingEntity)
        try {
            val initialBytes = if (file.exists()) file.length() else 0L
            setProgress(workDataOf("uploadId" to uploadId, "progress" to 10, "bytesWritten" to 0L, "totalBytes" to initialBytes, "status" to "Iniciando subida...", "uploadType" to entity.uploadType))

            // Avatares y portadas son pequeños: siempre van a Supabase Storage.
            // Así permanecen disponibles aunque el CDN del PC esté caído.
            if (entity.uploadType == "PROFILE" || entity.uploadType == "PROFILE_COVER") {
                if (!file.exists()) {
                    return handleFailure(entity, "Archivo local no encontrado para perfil")
                }
                val currentUid = entity.userId.ifEmpty { SupabaseClient.currentUser?.id ?: "" }
                if (currentUid.isBlank()) return handleFailure(entity, "Usuario no autenticado")
                val storageResult = supabaseStorage.uploadProfileMedia(
                    file = file,
                    userId = currentUid,
                    mimeType = entity.mimeType,
                    cover = entity.uploadType == "PROFILE_COVER"
                )
                if (storageResult.isFailure) return handleFailure(entity, storageResult.exceptionOrNull()?.localizedMessage ?: "Error subiendo imagen a Supabase Storage")
                val storageUrl = storageResult.getOrThrow()
                val currentProfile = profilesRepository.getProfile(currentUid).getOrNull() ?: SupabaseClient.currentProfile
                val displayName = currentProfile?.displayName ?: ""
                val updateResult = if (entity.uploadType == "PROFILE_COVER") {
                    profilesRepository.updateProfile(currentUid, displayName, currentProfile?.avatarUrl, coverUrl = storageUrl)
                } else {
                    profilesRepository.updateProfile(currentUid, displayName, storageUrl, coverUrl = currentProfile?.coverUrl)
                }
                if (updateResult.isFailure) return handleFailure(entity, updateResult.exceptionOrNull()?.localizedMessage ?: "No se pudo actualizar el perfil")
                pendingUploadDao.updateUpload(entity.copy(status = "completed", remoteUrl = storageUrl, updatedAt = System.currentTimeMillis()))
                try { file.delete() } catch (_: Exception) {}
                setProgress(workDataOf("uploadId" to uploadId, "progress" to 100, "bytesWritten" to file.length(), "totalBytes" to file.length(), "status" to "Completado", "uploadType" to entity.uploadType))
                return Result.success()
            }

            var uploadedUrl: String? = entity.remoteUrl
            var thumbnailUrlForCreate: String? = null
            var finalUploadFile = file
            var intermediateTempFile: File? = null

            if (uploadedUrl == null && file.exists() && entity.mimeType.startsWith("video/") && !file.name.contains("_compressed_")) {
                setProgress(workDataOf("uploadId" to uploadId, "progress" to 15, "bytesWritten" to 0L, "totalBytes" to file.length(), "status" to "Comprimiendo video...", "uploadType" to entity.uploadType))
                try {
                    val pendingMediaDir = File(context.filesDir, "pending_media")
                    if (!pendingMediaDir.exists()) pendingMediaDir.mkdirs()
                    val compressed = com.example.util.VideoCompressorHelper.compressVideo(context, android.net.Uri.fromFile(file), null) { compProgress ->
                        val p = 10 + (compProgress * 0.15).toInt()
                        setProgressAsync(workDataOf("uploadId" to uploadId, "progress" to p, "bytesWritten" to 0L, "totalBytes" to file.length(), "status" to "Comprimiendo video ($compProgress%)...", "uploadType" to entity.uploadType))
                    }
                    if (compressed.exists() && compressed.length() > 0 && compressed.absolutePath != file.absolutePath) {
                        intermediateTempFile = compressed
                        finalUploadFile = compressed
                    } else if (compressed.exists() && compressed.absolutePath != file.absolutePath) compressed.delete()
                } catch (e: Exception) { Log.e(TAG, "Fallo al comprimir video, subiendo original", e) }
            }

            if (uploadedUrl == null) {
                val totalLength = finalUploadFile.length().coerceAtLeast(1L)
                setProgress(workDataOf("uploadId" to uploadId, "progress" to 25, "bytesWritten" to 0L, "totalBytes" to totalLength, "status" to "Subiendo archivo...", "uploadType" to entity.uploadType))
                val currentUid = entity.userId.ifEmpty { SupabaseClient.currentUser?.id ?: "anonymous" }
                val captionForUpload = entity.caption ?: "Social Media Upload"
                var lastUpdateMs = 0L
                val progressCb: (Long, Long) -> Unit = { bytesWritten, totalBytes ->
                    val now = System.currentTimeMillis()
                    if (now - lastUpdateMs > 300 || bytesWritten == totalBytes) {
                        lastUpdateMs = now
                        val uploadPct = 25 + ((bytesWritten.toDouble() / totalBytes.coerceAtLeast(1L).toDouble()) * 60.0).toInt()
                        setProgressAsync(workDataOf("uploadId" to uploadId, "progress" to uploadPct, "bytesWritten" to bytesWritten, "totalBytes" to totalBytes, "status" to "Subiendo archivo...", "uploadType" to entity.uploadType))
                    }
                }
                // Failover total para TODO tipo de media: CDN primero; si falla, B2.
                // VCDN (proxy edge function) para video PUBLICO (reels/stories). El
                // resto (imagenes, audio, chat privado, thumbnails) sigue 100% por el
                // UploadFailoverRouter (B2/CDN), sin tocarlo.
                val isPublicVideo = entity.mimeType.startsWith("video/") &&
                    (entity.uploadType == "REEL" || entity.uploadType == "STATE")
                val ext = if (finalUploadFile.name.contains(".")) finalUploadFile.name.substringAfterLast(".") else "bin"
                val stableFileName = "social_${entity.id}_${entity.uploadType.lowercase()}.$ext"
                val uploadResult = if (isPublicVideo) {
                    VideoRouter.uploadPublicVideo(
                        file = finalUploadFile,
                        mimeType = entity.mimeType,
                        userId = currentUid,
                        uploadType = entity.uploadType,
                        customFileName = stableFileName,
                        clientMessageUuid = entity.id,
                        onProgress = progressCb
                    )
                } else {
                    UploadFailoverRouter.uploadWithFailover(
                        file = finalUploadFile,
                        mimeType = entity.mimeType,
                        userId = currentUid,
                        uploadType = entity.uploadType,
                        customFileName = stableFileName,
                        clientMessageUuid = entity.id,
                        onProgress = progressCb
                    ) { progress ->
                        UploadRepository().uploadVideo(
                            mediaFile = finalUploadFile,
                            mediaMimeType = entity.mimeType,
                            caption = captionForUpload,
                            userId = currentUid,
                            stableFileName = stableFileName,
                            onProgress = progress
                        )
                    }
                }
                if (uploadResult.isSuccess) {
                    val mediaInfo = uploadResult.getOrThrow()
                    uploadedUrl = mediaInfo.url
                    var uploadedThumbUrl = mediaInfo.thumbnailUrl

                    // El CDN genera thumbnails server-side; cuando la ruta ganadora es
                    // B2 (thumbnailUrl == null) se genera uno local y se sube a Supabase
                    // Storage (thumbnails bucket, fuente de verdad) con fallback al CDN.
                    if (uploadedThumbUrl == null && (entity.mimeType.startsWith("video/") || entity.mimeType.startsWith("image/"))) {
                        val thumbFile = try {
                            if (entity.mimeType.startsWith("video/")) {
                                com.example.util.PanalinkMediaManager.generateVideoThumbnail(context, finalUploadFile)
                            } else {
                                com.example.util.PanalinkMediaManager.generateImageThumbnail(finalUploadFile)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "No se pudo generar thumbnail local", e)
                            null
                        }

                        if (thumbFile != null) {
                            // 1) Supabase Storage (thumbnails bucket, fuente de verdad)
                            val currentUidForThumb = currentUid.ifEmpty { SupabaseClient.currentUser?.id ?: "anonymous" }
                            val thumbObjectName = "thumb_${entity.uploadType.lowercase()}_${entity.id}.jpg"
                            val storageThumbUrl = try {
                                supabaseStorage.uploadThumbnail(thumbFile, currentUidForThumb, thumbObjectName)
                            } catch (e: Exception) { Log.w(TAG, "Supabase Storage thumb falla", e); null }

                            if (storageThumbUrl != null) {
                                uploadedThumbUrl = storageThumbUrl
                                Log.i(TAG, "Thumbnail subido a Supabase Storage: $uploadedThumbUrl")
                            } else {
                                // 2) Fallback: CDN/B2 via failover router
                                val thumbStableName = "thumb_social_${entity.id}_${entity.uploadType.lowercase()}.jpg"
                                val thumbResult = UploadFailoverRouter.uploadWithFailover(
                                    file = thumbFile,
                                    mimeType = "image/jpeg",
                                    userId = currentUidForThumb,
                                    uploadType = "thumbnail",
                                    customFileName = thumbStableName,
                                    clientMessageUuid = entity.id
                                ) {
                                    UploadRepository().uploadVideo(
                                        mediaFile = thumbFile,
                                        mediaMimeType = "image/jpeg",
                                        caption = "thumbnail",
                                        userId = currentUidForThumb,
                                        stableFileName = thumbStableName
                                    )
                                }
                                uploadedThumbUrl = thumbResult.getOrNull()?.url
                            }
                            try { thumbFile.delete() } catch (_: Exception) {}
                        }
                    }
                    thumbnailUrlForCreate = uploadedThumbUrl

                    val updatedMetadata = try {
                        val json = if (entity.metadataJson.isNullOrBlank()) org.json.JSONObject() else org.json.JSONObject(entity.metadataJson)
                        if (uploadedThumbUrl != null) json.put("remoteThumbnailUrl", uploadedThumbUrl)
                        json.toString()
                    } catch (_: Exception) { entity.metadataJson }

                    pendingUploadDao.updateUpload(
                        uploadingEntity.copy(
                            remoteUrl = uploadedUrl,
                            thumbnailPath = entity.thumbnailPath,
                            metadataJson = updatedMetadata,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } else return handleFailure(entity, uploadResult.exceptionOrNull()?.localizedMessage ?: "Error de subida al CDN/B2")
            }

            // Si reanudamos después de que el medio ya fue subido, restaurar el thumbnailUrl persistido si está disponible
            if (thumbnailUrlForCreate == null) {
                thumbnailUrlForCreate = try {
                    entity.metadataJson?.let {
                        org.json.JSONObject(it).optString("remoteThumbnailUrl").takeIf { s -> s.isNotBlank() }
                    }
                } catch (_: Exception) { null }
            }

            val audioUrl = try {
                entity.metadataJson?.let {
                    org.json.JSONObject(it).optString("audioUrl").takeIf { s -> s.isNotBlank() }
                }
            } catch (_: Exception) { null }

            val targetStateId = com.example.util.SocialUploadRecoveryHelper.deriveTargetStateId(uploadId)

            val fileBytes = if (finalUploadFile.exists()) finalUploadFile.length() else 0L
            setProgress(workDataOf("uploadId" to uploadId, "progress" to 85, "bytesWritten" to fileBytes, "totalBytes" to fileBytes, "status" to "Registrando publicación...", "uploadType" to entity.uploadType))
            var createdState: com.example.data.model.UserState? = null
            val success = when (entity.uploadType) {
                "STATE", "REEL" -> {
                    val mediaType = when { entity.mimeType.startsWith("video/") -> "video"; entity.mimeType.startsWith("audio/") -> "audio"; else -> "image" }
                    val result = statesRepository.createState(
                        mediaType = mediaType,
                        caption = entity.caption,
                        isReel = entity.uploadType == "REEL",
                        presetMediaUrl = uploadedUrl,
                        audioUrl = audioUrl,
                        thumbnailUrl = thumbnailUrlForCreate,
                        targetStateId = targetStateId
                    )
                    createdState = result.getOrNull(); result.isSuccess
                }
                // Audio personalizado de Historias: solo subida durable del medio;
                // NO crea estado (es música de fondo, no una historia independiente).
                "AUDIO" -> true
                else -> false
            }
            if (!success) return handleFailure(entity, "Fallo al registrar la publicación en Supabase")

            // Solo STATE/REEL persisten el estado localmente; el upload AUDIO es
            // música de fondo y no debe crear filas de historias en Room.

            if (entity.uploadType == "STATE" || entity.uploadType == "REEL") {
                try {
                    val currentUid = entity.userId.ifEmpty { SupabaseClient.currentUser?.id ?: "anonymous" }
                val myProfile = profilesRepository.getProfile(currentUid).getOrNull() ?: SupabaseClient.currentProfile ?: com.example.data.model.Profile(currentUid, "", null)
                val newState = createdState ?: com.example.data.model.UserState(id = targetStateId, authorId = currentUid, userIdField = currentUid, mediaUrl = uploadedUrl ?: "", mediaType = when { entity.mimeType.startsWith("video/") -> "video"; entity.mimeType.startsWith("audio/") -> "audio"; else -> "image" }, caption = entity.caption, createdAt = SupabaseClient.getNowIsoString(), type = if (entity.uploadType == "REEL") "reel" else "story", localVideoPath = entity.localFilePath)
                try { db.statesDao().deleteById("optimistic_$uploadId"); db.statesDao().deleteOptimistic(currentUid, entity.caption) } catch (_: Exception) {}
                statesRepository.saveStateLocally(com.example.data.model.UserStateWithUser(newState, myProfile), entity.localFilePath)
                // Persist reel locally in ROM for instant profile loading
                val reelsRepo = com.example.data.repository.reels.ReelsRepository(
                    local = com.example.data.repository.reels.ReelsLocalDataSource(db.statesDao()),
                    remote = com.example.data.repository.reels.ReelsRemoteDataSource()
                )
                val userState = com.example.data.model.UserState(
                    id = newState.id,
                    authorId = newState.authorId,
                    userIdField = newState.userIdField,
                    mediaUrl = newState.mediaUrl,
                    mediaType = newState.mediaType,
                    caption = newState.caption,
                    expiresAt = newState.expiresAt,
                    createdAt = newState.createdAt,
                    type = if (newState.isReel) "reel" else "story",
                    localVideoPath = entity.localFilePath,
                    thumbnailUrl = newState.thumbnailUrl
                )
                reelsRepo.local.save(
                    com.example.data.database.StateEntity.fromUserStateWithUser(
                        com.example.data.model.UserStateWithUser(userState, myProfile)
                    )
                )
                reelsRepo.local.updateLocalPath(newState.id, entity.localFilePath)
                } catch (e: Exception) { Log.e(TAG, "Failed to save state locally", e) }
            }

            val finalMetadata = try {
                val base = entity.metadataJson
                val json = if (base.isNullOrBlank()) org.json.JSONObject() else org.json.JSONObject(base)
                if (thumbnailUrlForCreate != null) json.put("remoteThumbnailUrl", thumbnailUrlForCreate)
                json.put("publicationRegistered", true)
                json.put("publicationId", targetStateId)
                json.toString()
            } catch (_: Exception) { entity.metadataJson }

            pendingUploadDao.updateUpload(entity.copy(status = "completed", remoteUrl = uploadedUrl, metadataJson = finalMetadata, updatedAt = System.currentTimeMillis()))
            val completedBytes = if (finalUploadFile.exists()) finalUploadFile.length() else 0L
            setProgress(workDataOf("uploadId" to uploadId, "progress" to 100, "bytesWritten" to completedBytes, "totalBytes" to completedBytes, "status" to "Completado", "uploadType" to entity.uploadType))
            if (entity.uploadType != "REEL" && entity.uploadType != "STATE") {
                try { finalUploadFile.delete(); if (file.absolutePath != finalUploadFile.absolutePath) file.delete() } catch (_: Exception) {}
            }
            try { intermediateTempFile?.delete() } catch (_: Exception) {}
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Exception during social upload", e)
            return handleFailure(entity, e.localizedMessage ?: "Excepción desconocida")
        }
    }

    private suspend fun handleFailure(entity: PendingUploadEntity, error: String): Result {
        val nextRetryCount = entity.retryCount + 1
        return if (nextRetryCount >= 3) {
            pendingUploadDao.updateUpload(entity.copy(status = "failed", errorMessage = error, retryCount = nextRetryCount, updatedAt = System.currentTimeMillis()))
            Result.failure()
        } else {
            pendingUploadDao.updateUpload(entity.copy(status = "pending", retryCount = nextRetryCount, errorMessage = error, updatedAt = System.currentTimeMillis()))
            Result.retry()
        }
    }

    internal companion object {
        fun shouldFailForMissingLocalFile(fileExists: Boolean, hasRemoteUrl: Boolean): Boolean =
            !fileExists && !hasRemoteUrl
    }
}
