package com.example.util

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.database.PanalinkDatabase
import com.example.worker.SocialMediaUploadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object SocialUploadRecoveryHelper {
    private const val TAG = "SocialUploadRecovery"

    fun reconcilePendingUploads(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = PanalinkDatabase.getDatabase(context)
                val dao = db.pendingUploadDao()
                val allUploads = dao.getUploadsByStatus("pending") + dao.getUploadsByStatus("uploading")
                val now = System.currentTimeMillis()
                val staleThreshold = 3 * 60 * 1000L // 3 minutes stale threshold for uploading

                for (entity in allUploads) {
                    var currentEntity = entity
                    // If uploading but stale (process died during upload), reset to pending
                    if (currentEntity.status == "uploading" && (now - currentEntity.updatedAt > staleThreshold)) {
                        android.util.Log.i(TAG, "Resetting stale uploading task ${currentEntity.id} to pending")
                        currentEntity = currentEntity.copy(status = "pending", updatedAt = now)
                        dao.updateUpload(currentEntity)
                    }

                    if (currentEntity.status == "pending") {
                        val uploadId = currentEntity.id
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

                        WorkManager.getInstance(context).enqueueUniqueWork(
                            "social_upload_$uploadId",
                            ExistingWorkPolicy.KEEP,
                            request
                        )
                        android.util.Log.d(TAG, "Enqueued/reconciled work for pending upload $uploadId")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to reconcile pending uploads", e)
            }
        }
    }
}
