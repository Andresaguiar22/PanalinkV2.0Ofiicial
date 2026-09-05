package com.example.util

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.database.PanalinkDatabase
import com.example.worker.PostUploadWorker
import com.example.worker.SocialSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-dispatches durable Room queues after process death, force-stop, reboot,
 * or a connectivity transition. Room remains the source of truth; this class
 * only schedules existing workers and never performs network I/O itself.
 */
object OfflineQueueRecovery {
    private const val POST_WORK_PREFIX = "post_upload_"

    fun reconcile(context: Context) {
        val appContext = context.applicationContext
        val db = PanalinkDatabase.getDatabase(appContext)
        val workManager = WorkManager.getInstance(appContext)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            try {
                val pendingPosts = db.pendingPostDao().getActivePostsFlow().first()
                pendingPosts.forEach { post ->
                    val request = OneTimeWorkRequestBuilder<PostUploadWorker>()
                        .setConstraints(constraints)
                        .setInputData(
                            workDataOf(
                                "pendingPostId" to post.id,
                                "serverPostId" to post.id
                            )
                        )
                        .addTag("post_upload")
                        .addTag("post_upload_${post.id}")
                        .build()
                    workManager.enqueueUniqueWork(
                        POST_WORK_PREFIX + post.id,
                        ExistingWorkPolicy.KEEP,
                        request
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("OfflineQueueRecovery", "Error reconciling posts", e)
            }

            try {
                if (db.pendingSocialActionDao().getPendingActions().isNotEmpty()) {
                    SocialSyncWorker.enqueue(appContext)
                }
            } catch (e: Exception) {
                android.util.Log.e("OfflineQueueRecovery", "Error reconciling social actions", e)
            }

            try {
                if (db.messageDao().getPendingMessages().isNotEmpty()) {
                    val syncRequest = OneTimeWorkRequestBuilder<com.example.worker.SyncMessagesWorker>()
                        .setConstraints(constraints)
                        .setBackoffCriteria(
                            androidx.work.BackoffPolicy.EXPONENTIAL,
                            androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                            java.util.concurrent.TimeUnit.MILLISECONDS
                        )
                        .addTag("sync_messages_work")
                        .build()
                    workManager.enqueueUniqueWork(
                        "sync_messages_unique",
                        ExistingWorkPolicy.KEEP,
                        syncRequest
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("OfflineQueueRecovery", "Error reconciling messages", e)
            }
        }
    }
}
