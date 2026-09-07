package com.example.media.social

import android.content.Context
import android.util.Log
import com.example.data.database.PanalinkDatabase
import com.example.media.storage.MediaStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object ReelOfflineMediaManager {
    private const val TAG = "ReelOfflineMedia"
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    suspend fun ensureLocalCopy(context: Context, reelId: String, url: String): String? = withContext(Dispatchers.IO) {
        if (!com.example.util.NetworkMonitor.isOnline.value || url.isBlank() || url.contains(".m3u8", ignoreCase = true)) {
            return@withContext null
        }
        if (!inFlight.add(reelId)) return@withContext null

        try {
            val dao = PanalinkDatabase.getDatabase(context.applicationContext).statesDao()
            val existing = dao.getStateById(reelId)?.localVideoPath
            if (!existing.isNullOrBlank() && File(existing).let { it.exists() && it.length() > 0L }) {
                return@withContext existing
            }

            val file = MediaStorageManager(context.applicationContext)
                .downloadMediaSafely(url, "REEL", "reel_$reelId")
                ?: return@withContext null
            dao.updateLocalPath(reelId, file.absolutePath)
            Log.i(TAG, "Saved offline Reel copy: ${file.absolutePath}")
            file.absolutePath
        } catch (error: Exception) {
            Log.w(TAG, "Could not save offline Reel copy: $reelId", error)
            null
        } finally {
            inFlight.remove(reelId)
        }
    }
}