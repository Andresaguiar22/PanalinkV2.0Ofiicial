package com.example.worker

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.WorkerFactory
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.example.data.database.PanalinkDatabase
import com.example.data.database.PendingPostEntity
import com.example.data.database.PendingPostMediaEntity
import com.example.data.database.PendingPostMediaStatus
import com.example.data.model.PostDto
import com.example.data.model.UploadMediaResult
import com.example.data.repository.FeedRepository
import com.example.data.repository.UploadRepository
import com.example.util.NetworkMonitor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.lang.reflect.Field

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PostUploadWorkerRealIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: PanalinkDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, PanalinkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        setNetworkOnline(true)
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun setNetworkOnline(online: Boolean) {
        val field: Field = NetworkMonitor::class.java.getDeclaredField("_isOnline")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val mutable = field.get(NetworkMonitor) as kotlinx.coroutines.flow.MutableStateFlow<Boolean>
        mutable.value = online
    }

    private fun pendingPost(id: String, mediaJson: String = "[]") = PendingPostEntity(
        id = id,
        userId = "user-123",
        content = "hello",
        type = "ALBUM",
        mediaUrisJson = mediaJson,
        privacy = "PUBLIC",
        status = "pending",
        createdAt = System.currentTimeMillis(),
        progress = 0f
    )

    private fun media(postId: String, index: Int, status: String, remoteUrl: String? = null) = PendingPostMediaEntity(
        id = "$postId:$index",
        postId = postId,
        mediaIndex = index,
        localUri = Uri.fromFile(File(context.cacheDir, "$postId-$index.jpg").apply { writeBytes(byteArrayOf(1)) }).toString(),
        mimeType = "image/jpeg",
        sizeBytes = 1024L,
        status = status,
        remoteUrl = remoteUrl,
        updatedAt = System.currentTimeMillis()
    )

    private fun buildWorker(
        postId: String,
        uploadRepository: UploadRepository,
        feedRepository: FeedRepository
    ): TestablePostUploadWorker {

        return TestListenableWorkerBuilder.from(context, TestablePostUploadWorker::class.java)
            .setInputData(workDataOf("pendingPostId" to postId))
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters
                    ): ListenableWorker =
                        TestablePostUploadWorker(appContext, workerParameters, db, uploadRepository, feedRepository)
                }
            )
            .build()

    }

    @Test
    fun `A recovery of partially uploaded media skips uploaded and uploads only pending`() = runBlocking {
        val postId = "post-a"
        db.pendingPostDao().insertPost(pendingPost(postId))
        db.pendingPostMediaDao().upsertMedia(media(postId, 0, PendingPostMediaStatus.UPLOADED, "https://cdn.example/0"))
        db.pendingPostMediaDao().upsertMedia(media(postId, 1, PendingPostMediaStatus.PENDING))

        val uploadRepo = FakeUploadRepository()
        val feedRepo = FakeFeedRepository(success = true)
        val worker = buildWorker(postId, uploadRepo, feedRepo)

        val result = worker.doWork()

        assertTrue(result == ListenableWorker.Result.success())
        assertEquals(1, uploadRepo.calls.size)
        assertEquals("https://cdn.example/upload-1", uploadRepo.calls.single())
        assertTrue(db.pendingPostMediaDao().getMediaByIndex(postId, 0) == null)
        assertTrue(db.pendingPostMediaDao().getMediaByIndex(postId, 1) == null)
        assertEquals(listOf("https://cdn.example/0", "https://cdn.example/upload-1"), feedRepo.createdPosts.single().mediaUrls)
    }

    @Test
    fun `B createPost failure after all media uploaded should not force re upload on retry`() = runBlocking {
        val postId = "post-b"
        db.pendingPostDao().insertPost(pendingPost(postId))
        db.pendingPostMediaDao().upsertMedia(media(postId, 0, PendingPostMediaStatus.PENDING))
        db.pendingPostMediaDao().upsertMedia(media(postId, 1, PendingPostMediaStatus.PENDING))

        val uploadRepo = FakeUploadRepository()
        val failThenSuccessFeedRepo = FakeFeedRepository(success = false, failFirst = true)

        val firstWorker = buildWorker(postId, uploadRepo, failThenSuccessFeedRepo)
        val firstResult = firstWorker.doWork()

        assertTrue(firstResult == ListenableWorker.Result.retry())
        assertEquals(2, uploadRepo.calls.size)
        assertTrue(db.pendingPostMediaDao().getMediaByIndex(postId, 0)?.status == PendingPostMediaStatus.UPLOADED)
        assertTrue(db.pendingPostMediaDao().getMediaByIndex(postId, 1)?.status == PendingPostMediaStatus.UPLOADED)
        assertTrue(db.pendingPostDao().getPostById(postId)?.status == "failed")

        val secondWorker = buildWorker(postId, uploadRepo, FakeFeedRepository(success = true))
        val secondResult = secondWorker.doWork()

        assertTrue(secondResult == ListenableWorker.Result.success())
        assertEquals(2, uploadRepo.calls.size)
        assertTrue(db.pendingPostDao().getPostById(postId) == null)
    }

    @Test
    fun `C durable Room state is read back and uploaded media is skipped across a new worker instance`() = runBlocking {
        val postId = "post-c"
        db.pendingPostDao().insertPost(pendingPost(postId))
        db.pendingPostMediaDao().upsertMedia(media(postId, 0, PendingPostMediaStatus.UPLOADED, "https://cdn.example/0"))
        db.pendingPostMediaDao().upsertMedia(media(postId, 1, PendingPostMediaStatus.UPLOADING))

        val uploadRepo = FakeUploadRepository()
        val feedRepo = FakeFeedRepository(success = true)

        val worker = buildWorker(postId, uploadRepo, feedRepo)
        val result = worker.doWork()

        assertTrue(result == ListenableWorker.Result.success())
        assertEquals(1, uploadRepo.calls.size)
        assertEquals("https://cdn.example/upload-1", uploadRepo.calls.single())
        assertTrue(db.pendingPostMediaDao().getMediaByIndex(postId, 1) == null)
        assertTrue(db.pendingPostMediaDao().getMediaByIndex(postId, 1)?.remoteUrl == null)
    }

    @Test
    fun `D FAILED_TERMINAL is never retried or converted silently`() = runBlocking {
        val postId = "post-d"
        db.pendingPostDao().insertPost(pendingPost(postId))
        db.pendingPostMediaDao().upsertMedia(media(postId, 0, PendingPostMediaStatus.FAILED_TERMINAL))

        val uploadRepo = FakeUploadRepository()
        val feedRepo = FakeFeedRepository(success = true)
        val worker = buildWorker(postId, uploadRepo, feedRepo)

        val result = worker.doWork()

        assertFalse(result == ListenableWorker.Result.success())
        assertEquals(0, uploadRepo.calls.size)
        assertEquals(PendingPostMediaStatus.FAILED_TERMINAL, db.pendingPostMediaDao().getMediaByIndex(postId, 0)?.status)
        assertTrue(feedRepo.createdPosts.isEmpty())
    }

    class TestablePostUploadWorker(
        context: Context,
        workerParams: WorkerParameters,
        private val testDatabase: PanalinkDatabase,
        private val testUploadRepository: UploadRepository,
        private val testFeedRepository: FeedRepository
    ) : PostUploadWorker(context, workerParams) {
        override val database: PanalinkDatabase = testDatabase
        override val uploadRepository: UploadRepository = testUploadRepository
        override val feedRepository: FeedRepository = testFeedRepository

        override suspend fun performUpload(
            file: java.io.File,
            mimeType: String,
            mediaKind: String?,
            userId: String,
            stableFileName: String,
            clientMessageUuid: String,
            onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
        ): kotlin.Result<UploadMediaResult> = testUploadRepository.uploadVideo(
            mediaFile = file,
            mediaMimeType = mimeType,
            caption = "Feed Post Media",
            userId = userId,
            stableFileName = stableFileName,
            onProgress = onProgress
        )
    }

    class FakeUploadRepository : UploadRepository() {
        val calls = mutableListOf<String>()

        override suspend fun uploadVideo(
            mediaFile: java.io.File,
            mediaMimeType: String,
            caption: String,
            userId: String,
            fileNamePrefix: String?,
            stableFileName: String?,
            type: String?,
            onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)?
        ): Result<com.example.data.model.UploadMediaResult> {
            val url = "https://cdn.example/upload-${calls.size + 1}"
            calls.add(url)
            return Result.success(com.example.data.model.UploadMediaResult(url = url))
        }
    }

    class FakeFeedRepository(
        private val success: Boolean,
        private val failFirst: Boolean = false
    ) : FeedRepository {
        var createCalls = 0
        val createdPosts = mutableListOf<PostDto>()

        override fun getLocalPostsFlow(limit: Int): kotlinx.coroutines.flow.Flow<List<PostDto>> = kotlinx.coroutines.flow.flow { emit(emptyList()) }
        override suspend fun getFeed(limit: Int, lastCreatedAt: String?): Result<Unit> = Result.success(Unit)
        override suspend fun createPost(post: PostDto): Result<PostDto> {
            createCalls += 1
            if (!success && createCalls == 1 && failFirst) {
                return Result.failure(Exception("createPost failed"))
            }
            createdPosts.add(post)
            return Result.success(post)
        }
        override suspend fun toggleLike(postId: String, userId: String, isLiked: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun sharePost(postId: String, userId: String): Result<Unit> = Result.success(Unit)
        override suspend fun addComment(postId: String, userId: String, content: String): Result<com.example.data.model.PostCommentDto> = Result.failure(Exception("not implemented"))
        override suspend fun getCommentsForPost(postId: String): Result<Unit> = Result.success(Unit)
        override fun getCommentsFlow(postId: String): kotlinx.coroutines.flow.Flow<List<com.example.data.model.PostCommentDto>> = kotlinx.coroutines.flow.flow { emit(emptyList()) }
        override suspend fun deletePost(postId: String): Result<Unit> = Result.success(Unit)
        override suspend fun updatePost(postId: String, content: String): Result<PostDto> = Result.success(PostDto())
        override suspend fun getPostById(postId: String): Result<PostDto> = Result.success(PostDto())
    }
}
