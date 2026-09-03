package com.example.util

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.database.PanalinkDatabase
import com.example.data.database.PendingUploadEntity
import com.example.worker.SocialMediaUploadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

object SocialUploadRecoveryHelper {
    private const val TAG = "SocialUploadRecovery"
    const val STALE_THRESHOLD_MS = 3 * 60 * 1000L // 3 minutes

    fun reconcilePendingUploads(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            reconcileInternal(context)
        }
    }

    suspend fun reconcileInternal(context: Context) = withContext(Dispatchers.IO) {
        try {
            val db = PanalinkDatabase.getDatabase(context)
            val dao = db.pendingUploadDao()
            val allUploads = dao.getUploadsByStatus("pending") + dao.getUploadsByStatus("uploading")
            val now = System.currentTimeMillis()

            val workManager = try {
                WorkManager.getInstance(context)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "WorkManager instance not available: ${e.message}")
                null
            }

            for (entity in allUploads) {
                var currentEntity = entity
                val uploadId = currentEntity.id
                val uniqueWorkName = "social_upload_$uploadId"

                // 1. Check local file existence: if file is gone and media was never uploaded, fail permanently
                val localFile = File(currentEntity.localFilePath)
                if (currentEntity.remoteUrl.isNullOrBlank() && !localFile.exists()) {
                    android.util.Log.w(TAG, "Local file not found for upload $uploadId: ${currentEntity.localFilePath}")
                    dao.updateUpload(
                        currentEntity.copy(
                            status = "failed",
                            errorMessage = "Archivo local no encontrado",
                            updatedAt = now
                        )
                    )
                    continue
                }

                // 2. Query WorkManager state if available
                var isActivelyRunningOrEnqueued = false
                var isSucceeded = false
                if (workManager != null) {
                    try {
                        val workInfos = workManager.getWorkInfosForUniqueWork(uniqueWorkName).get()
                        for (info in workInfos) {
                            when (info.state) {
                                WorkInfo.State.SUCCEEDED -> isSucceeded = true
                                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> isActivelyRunningOrEnqueued = true
                                else -> {}
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "Failed to query WorkManager for $uniqueWorkName: ${e.message}")
                    }
                }

                // If WorkManager already finished successfully, reconcile Room state
                if (isSucceeded) {
                    android.util.Log.i(TAG, "WorkManager indicates $uploadId succeeded, marking completed in Room")
                    dao.updateUpload(currentEntity.copy(status = "completed", updatedAt = now))
                    continue
                }

                // If Room says "uploading" but WorkManager is not active, or stale threshold exceeded (process died)
                val isStale = (now - currentEntity.updatedAt > STALE_THRESHOLD_MS)
                if (currentEntity.status == "uploading" && (!isActivelyRunningOrEnqueued || isStale)) {
                    android.util.Log.i(TAG, "Resetting stale/dead uploading task $uploadId to pending")
                    currentEntity = currentEntity.copy(status = "pending", updatedAt = now)
                    dao.updateUpload(currentEntity)
                }

                // If pending, enqueue work if not already running in WorkManager
                if (currentEntity.status == "pending") {
                    if (workManager != null && !isActivelyRunningOrEnqueued) {
                        val constraints = Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                        val request = OneTimeWorkRequestBuilder<SocialMediaUploadWorker>()
                            .setConstraints(constraints)
                            .setInputData(workDataOf("uploadId" to uploadId))
                            .addTag("social_upload")
                            .addTag("upload_$uploadId")
                            .addTag("social_upload_$uploadId")
                            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, androidx.work.WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                            .build()

                        workManager.enqueueUniqueWork(
                            uniqueWorkName,
                            ExistingWorkPolicy.KEEP,
                            request
                        )
                        android.util.Log.d(TAG, "Enqueued/reconciled work for pending upload $uploadId")
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to reconcile pending uploads", e)
        }
    }
}
