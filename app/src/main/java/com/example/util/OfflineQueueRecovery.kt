package com.example.util

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.database.PanalinkDatabase
import com.example.worker.PostUploadWorker
import com.example.worker.SocialSyncWorker

/**
 * Re-dispatches durable Room queues after process death, force-stop, reboot,
 * or a connectivity transition. Room remains the source of truth; this class
 * only schedules existing workers and never performs network I/O itself.
 */
object OfflineQueueRecovery {
    private const val POST_WORK_PREFIX = "post_upload_recovery_"

    fun reconcile(context: Context) {
        val appContext = context.applicationContext
        val db = PanalinkDatabase.getDatabase(appContext)
        val workManager = WorkManager.getInstance(appContext)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Recover every durable post that can still be processed. KEEP prevents
        // duplicate dispatch when this runs both at startup and on reconnect.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val pendingPosts = db.pendingPostDao().getActivePostsFlow()
            pendingPosts.collect { posts ->
                posts.forEach { post ->
                    val request = OneTimeWorkRequestBuilder<PostUploadWorker>()
                        .setConstraints(constraints)
                        .setInputData(
                            androidx.work.workDataOf("pendingPostId" to post.id)
                        )
                        .addTag("post_upload_recovery")
                        .build()
                    workManager.enqueueUniqueWork(
                        POST_WORK_PREFIX + post.id,
                        ExistingWorkPolicy.KEEP,
                        request
                    )
                }
                // A Flow collector would otherwise live forever; the queue is
                // only needed as a snapshot for dispatch.
                kotlinx.coroutines.currentCoroutineContext().cancel()
            }
        }

        // A single unique worker drains all pending social actions. This also
        // covers actions that survived a dead worker but were never re-enqueued.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            if (db.pendingSocialActionDao().getPendingActions().isNotEmpty()) {
                SocialSyncWorker.enqueue(appContext)
            }
        }
    }
}
