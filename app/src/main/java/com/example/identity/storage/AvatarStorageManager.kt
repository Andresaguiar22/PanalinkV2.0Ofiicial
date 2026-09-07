package com.example.identity.storage

import android.content.Context
import androidx.annotation.Keep
import com.example.identity.model.AvatarDownloadResult
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Keep
class AvatarStorageManager(private val context: Context) {

    companion object {
        private const val MAX_AVATAR_BYTES = 5L * 1024L * 1024L
    }

    suspend fun downloadAvatar(userId: String, url: String): AvatarDownloadResult = withContext(Dispatchers.IO) {
        try {
            val uri = try { URI(url) } catch (_: Exception) { return@withContext AvatarDownloadResult.Error("Avatar URL inválida") }
            if (uri.scheme?.lowercase() != "https") {
                return@withContext AvatarDownloadResult.Error("Solo se permiten avatares HTTPS")
            }

            val avatarsDir = File(context.filesDir, "avatars/users")
            if (!avatarsDir.exists()) avatarsDir.mkdirs()

            val safeName = userId.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(80)
            val file = File(avatarsDir, "${safeName}_avatar.jpg")
            if (!file.canonicalPath.startsWith(avatarsDir.canonicalPath)) {

                return@withContext AvatarDownloadResult.Error("Ruta de avatar inválida")
            }

            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "image/*")
                connection.setRequestProperty("User-Agent", "PanalinkAndroid")
                connection.connectTimeout = 10_000
                connection.readTimeout = 30_000

                val code = connection.responseCode
                if (code !in 200..299) {
                    return@withContext AvatarDownloadResult.Error("HTTP $code")
                }

                val contentLength = connection.contentLengthLong
                if (contentLength > MAX_AVATAR_BYTES) {

                    return@withContext AvatarDownloadResult.Error("Avatar demasiado grande")
                }

                var totalRead = 0L
                try {
                    connection.inputStream.use { input ->
                        file.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var read = input.read(buffer)
                            while (read >= 0) {
                                totalRead += read.toLong()
                                if (totalRead > MAX_AVATAR_BYTES) {

                                    throw java.io.IOException("Avatar excede el límite de tamaño")
                                }
                                output.write(buffer, 0, read)
                                read = input.read(buffer)
                            }
                            output.flush()
                        }
                    }
                } finally {
                    if (totalRead > MAX_AVATAR_BYTES) file.delete()
                }
            } finally {
                connection.disconnect()
            }
            AvatarDownloadResult.Success(file.absolutePath)
        } catch (e: Exception) {
            AvatarDownloadResult.Error(e.message ?: "Unknown error")
        }
    }
}
