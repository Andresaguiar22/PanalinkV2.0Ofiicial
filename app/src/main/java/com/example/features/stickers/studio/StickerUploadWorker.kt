package com.example.features.stickers.studio

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.logger.AppLogger
import com.example.data.model.StickerResult
import com.example.data.repository.StickerRepository
import com.example.data.supabase.SupabaseClient
import com.example.features.stickers.editor.StickerCreationRepository
import java.io.File

/**
 * WorkManager worker que sube un sticker (estático o animado) de forma
 * duradera. Se encola cuando no hay conectividad y se reanuda
 * automáticamente al volver la señal.
 */
class StickerUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val localPath = inputData.getString(KEY_LOCAL_PATH)
            val previewPath = inputData.getString(KEY_PREVIEW_PATH) ?: localPath
            val stickerUrl = inputData.getString(KEY_STICKER_URL)
            val previewUrl = inputData.getString(KEY_PREVIEW_URL)
            val name = inputData.getString(KEY_NAME) ?: "Panalink Sticker"
            val emoji = inputData.getString(KEY_EMOJI) ?: ""

            val localFile = if (localPath != null) File(localPath) else null
            if (localFile == null || !localFile.exists()) {
                return Result.failure()
            }

            val mimeType = when (localFile.extension.lowercase()) {
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                else -> "image/webp"
            }

            // Si ya tenemos una URL remota (upload previo exitoso), solo persistimos localmente
            if (!stickerUrl.isNullOrBlank()) {
                StickerRepository.saveSticker(
                    applicationContext,
                    StickerResult(url = stickerUrl, preview = (previewUrl ?: previewPath) ?: "", width = null, height = null)
                )
                return Result.success()
            }

            // Subir a CDN vía PanalinkMediaManager
            val uploadResult = StickerCreationRepository.uploadAndCreateSticker(
                context = applicationContext,
                file = localFile,
                name = name,
                emoji = emoji,
                mimeType = mimeType
            )

            val finalUrl = uploadResult.getOrNull() ?: localFile.absolutePath

            // Guardar en la base de datos local (SharedPreferences + default dir)
            StickerRepository.saveSticker(
                applicationContext,
                StickerResult(url = finalUrl, preview = localFile.absolutePath)
            )

            // Registrar como reciente
            StickerRepository.addRecentSticker(
                applicationContext,
                StickerResult(url = finalUrl, preview = localFile.absolutePath)
            )

            Result.success()
        } catch (e: Exception) {
            AppLogger.e(message = "StickerUploadWorker failed", throwable = e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_LOCAL_PATH = "local_path"
        const val KEY_PREVIEW_PATH = "preview_path"
        const val KEY_STICKER_URL = "sticker_url"
        const val KEY_PREVIEW_URL = "preview_url"
        const val KEY_NAME = "name"
        const val KEY_EMOJI = "emoji"

        fun createInputData(
            localPath: String,
            previewPath: String? = null,
            stickerUrl: String? = null,
            previewUrl: String? = null,
            name: String = "Panalink Sticker",
            emoji: String = ""
        ): androidx.work.Data = androidx.work.workDataOf(
            KEY_LOCAL_PATH to localPath,
            KEY_PREVIEW_PATH to (previewPath ?: localPath),
            KEY_STICKER_URL to (stickerUrl ?: ""),
            KEY_PREVIEW_URL to (previewUrl ?: ""),
            KEY_NAME to name,
            KEY_EMOJI to emoji
        )
    }
}
