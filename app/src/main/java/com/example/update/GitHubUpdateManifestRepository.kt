package com.example.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class GitHubUpdateManifestRepository(
    private val config: UpdateDistributionConfig,
    private val okHttpClient: OkHttpClient = OkHttpClient()
) : UpdateManifestRepository {

    override suspend fun fetchUpdateManifest(): Result<AppVersionInfo> = withContext(Dispatchers.IO) {
        val owner = config.githubOwner
        val repo = config.githubRepo
        
        if (owner.isBlank() || repo.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("GitHub owner or repository is not configured"))
        }

        // Metadatos OTA desde raw.githubusercontent.com (rama main), mientras que
        // el APK binario vive en GitHub Releases. Separar metadatos de binarios
        // se alinea con el proceso de publicación manual desde Termux.
        // jsDelivr se usa como fallback: su CDN respeta mejor el cache-buster y
        // actualiza más rápido que raw.githubusercontent tras una publicación,
        // evitando que la app reporte "sin actualizaciones" durante la ventana
        // de cache del CDN de GitHub raw (~5 min).
        val manifestUrl = "https://raw.githubusercontent.com/$owner/$repo/main/manifest.json"
        val fallbackUrl = "https://cdn.jsdelivr.net/gh/$owner/$repo@main/manifest.json"

        if (config.isProduction && !manifestUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext Result.failure(SecurityException("Production manifest URL must use HTTPS"))
        }

        // Cache-buster para evitar que OkHttp o el CDN devuelvan un manifest viejo
        val cacheBustedUrl = "$manifestUrl?t=${System.currentTimeMillis()}"
        val cacheBustedFallback = "$fallbackUrl?t=${System.currentTimeMillis()}"

        val request = Request.Builder()
            .url(cacheBustedUrl)
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .build()

        val result = try {
            fetchAndParse(request, owner, repo)
        } catch (e: Exception) {
            // raw falló o devolvió cache; intentar jsDelivr antes de rendirse.
            null
        }

        if (result != null) return@withContext result

        val fallbackRequest = Request.Builder()
            .url(cacheBustedFallback)
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .build()

        try {
            fetchAndParse(fallbackRequest, owner, repo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchAndParse(request: Request, owner: String, repo: String): Result<AppVersionInfo> {
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IOException("GitHub returned error code: ${response.code}"))
                }

                val bodyString = response.body?.string()
                    ?: return Result.failure(IOException("Empty response body from GitHub"))

                val json = JSONObject(bodyString)
                val versionCode = json.getLong("versionCode")
                if (versionCode <= 0) {
                    return Result.failure(IllegalArgumentException("Invalid versionCode in manifest: $versionCode"))
                }

                val versionName = json.getString("versionName")
                if (versionName.isBlank()) {
                    return Result.failure(IllegalArgumentException("versionName is empty"))
                }

                val downloadUrl = json.getString("downloadUrl")
                if (downloadUrl.isBlank()) {
                    return Result.failure(IllegalArgumentException("Download URL is empty"))
                }

                // Enforce HTTPS for download url in production
                if (config.isProduction) {
                    if (!downloadUrl.startsWith("https://", ignoreCase = true)) {
                        return Result.failure(SecurityException("Production download URL must use HTTPS: $downloadUrl"))
                    }
                    // Validate that the download URL points to GitHub releases
                    if (!downloadUrl.contains("github.com/$owner/$repo/releases/download/", ignoreCase = true)) {
                        return Result.failure(SecurityException("Production GITHUB_RELEASES download URL must point to the configured repository: $downloadUrl"))
                    }
                }

                val sha256 = json.getString("sha256")
                if (sha256.isBlank()) {
                    return Result.failure(IllegalArgumentException("SHA-256 hash is empty"))
                }

                // Enforce valid SHA-256 format (64 hex characters)
                if (!sha256.matches(Regex("^[0-9a-fA-F]{64}$"))) {
                    return Result.failure(IllegalArgumentException("Invalid SHA-256 hex format: $sha256"))
                }

                val mandatory = json.optBoolean("mandatory", false)
                val minimumSupportedVersionCode = json.optLong("minimumSupportedVersionCode", 0L)

                val changelogArray = json.optJSONArray("changelog") ?: JSONArray()
                val changelogList = mutableListOf<String>()
                for (i in 0 until changelogArray.length()) {
                    changelogList.add(changelogArray.getString(i))
                }

                val info = AppVersionInfo(
                    versionCode = versionCode,
                    versionName = versionName,
                    downloadUrl = downloadUrl,
                    changelog = changelogList,
                    mandatory = mandatory,
                    sha256 = sha256,
                    minimumSupportedVersionCode = minimumSupportedVersionCode
                )
                Result.success(info)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
