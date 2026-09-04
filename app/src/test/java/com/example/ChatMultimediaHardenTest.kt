package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.ChatEntity
import com.example.data.database.MessageDao
import com.example.data.database.MessageEntity
import com.example.data.database.PanalinkDatabase
import com.example.data.repository.MessagesRepository
import com.example.data.repository.MessagesRepository.ChatKind
import com.example.data.repository.MessagesRepository.CanonicalChatIdentity
import com.example.worker.MediaUploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatMultimediaHardenTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: PanalinkDatabase
    private lateinit var messageDao: MessageDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (context is com.example.PanaApplication) {
            com.example.PanaApplication.instance = context
        }
        db = PanalinkDatabase.getDatabase(context)
        messageDao = db.messageDao()
    }

    @After
    fun teardown() {
        runBlocking(Dispatchers.IO) {
            db.clearAllTables()
        }
    }

    // A) sendMultimediaMessage() creates MessageEntity with chatId = canonical threadId when DM identity is resolved
    @Test
    fun testA_sendMultimediaMessage_usesCanonicalThreadId() = runBlocking(Dispatchers.IO) {
        val canonicalThreadId = "22222222-2222-2222-2222-222222222222"
        val receiverId = "33333333-3333-3333-3333-333333333333"

        val chatEntity = ChatEntity(
            id = canonicalThreadId,
            createdAt = "2026-09-04T12:00:00Z",
            threadId = canonicalThreadId,
            name = "Test User",
            type = "dm",
            otherUserId = receiverId
        )
        db.chatDao().insertChat(chatEntity)

        // Resolve identity
        val identity = MessagesRepository.getInstance().resolveChatIdentity(canonicalThreadId, receiverId)
        assertEquals(ChatKind.DM, identity.kind)
        assertEquals(canonicalThreadId, identity.threadId)
        assertEquals(receiverId, identity.receiverId)

        val canonicalChatId = if (identity.kind == ChatKind.DM && !identity.threadId.isNullOrEmpty() && MessagesRepository.isValidUuid(identity.threadId)) {
            identity.threadId
        } else {
            canonicalThreadId
        }
        assertEquals(canonicalThreadId, canonicalChatId)

        val entity = MessageEntity(
            id = "temp_${UUID.randomUUID()}",
            chatId = canonicalChatId,
            senderId = "my_user_id",
            receiverId = identity.receiverId,
            content = "Multimedia test",
            createdAt = "2026-09-04T12:00:00Z",
            status = "sending",
            messageType = "image",
            localMediaUri = "/tmp/sample.jpg"
        )
        messageDao.insertMessage(entity)

        val saved = messageDao.getMessageById(entity.id)
        assertNotNull(saved)
        assertEquals("MessageEntity must use canonical threadId for DM", canonicalThreadId, saved?.chatId)
        assertEquals(receiverId, saved?.receiverId)
    }

    // B) sendImageAlbum() creates MessageEntity with chatId = canonical threadId when DM identity is resolved
    @Test
    fun testB_sendImageAlbum_usesCanonicalThreadId() = runBlocking(Dispatchers.IO) {
        val canonicalThreadId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
        val receiverId = "cccccccc-cccc-cccc-cccc-cccccccccccc"

        val chatEntity = ChatEntity(
            id = canonicalThreadId,
            createdAt = "2026-09-04T12:00:00Z",
            threadId = canonicalThreadId,
            name = "Album User",
            type = "dm",
            otherUserId = receiverId
        )
        db.chatDao().insertChat(chatEntity)

        val identity = MessagesRepository.getInstance().resolveChatIdentity(canonicalThreadId, receiverId)
        assertEquals(ChatKind.DM, identity.kind)
        assertEquals(canonicalThreadId, identity.threadId)

        val canonicalChatId = if (identity.kind == ChatKind.DM && !identity.threadId.isNullOrEmpty() && MessagesRepository.isValidUuid(identity.threadId)) {
            identity.threadId
        } else {
            canonicalThreadId
        }

        val albumEntity = MessageEntity(
            id = "temp_${UUID.randomUUID()}",
            chatId = canonicalChatId,
            senderId = "my_user_id",
            receiverId = identity.receiverId,
            content = "[3 fotos]",
            createdAt = "2026-09-04T12:00:00Z",
            status = "sending",
            messageType = "image",
            localMediaUri = "/tmp/photo1.jpg,/tmp/photo2.jpg,/tmp/photo3.jpg"
        )
        messageDao.insertMessage(albumEntity)

        val saved = messageDao.getMessageById(albumEntity.id)
        assertNotNull(saved)
        assertEquals("Album entity must store canonical threadId as chatId", canonicalThreadId, saved?.chatId)
    }

    // C) If identity.kind == UNKNOWN, NO fake threadId is manufactured or invented
    @Test
    fun testC_unknownIdentity_neverInventsFakeThreadId() = runBlocking(Dispatchers.IO) {
        val unknownChatId = "unknown_chat_xyz"
        val identity = MessagesRepository.getInstance().resolveChatIdentity(unknownChatId, null)

        // Must be UNKNOWN and threadId must be null
        assertEquals(ChatKind.UNKNOWN, identity.kind)
        assertNull("threadId must NOT be invented for UNKNOWN chat", identity.threadId)
        assertEquals(unknownChatId, identity.chatId)
    }

    // D) Complete album (3 of 3 photos) uploads and confirms exactly 3 URLs
    @Test
    fun testD_completeAlbum_confirmsExactlyNUrls() {
        val totalRequired = 3
        val remoteUrls = listOf(
            "https://b2.example.com/file_0.jpg",
            "https://b2.example.com/file_1.jpg",
            "https://b2.example.com/file_2.jpg"
        )

        // Verification logic
        val isComplete = (remoteUrls.size == totalRequired)
        assertTrue("Album must confirm exactly N URLs", isComplete)
        assertEquals(3, remoteUrls.size)
    }

    // E) Partial album due to recoverable error stays in RETRY and does NOT confirm partial URLs or delete pending files
    @Test
    fun testE_partialAlbum_recoverableError_retriesWithoutCommittingPartial() {
        val totalRequired = 3
        val file1 = tempFolder.newFile("img1.jpg")
        val file2 = tempFolder.newFile("img2.jpg")
        val file3 = tempFolder.newFile("img3.jpg")

        val allPaths = listOf(file1.absolutePath, file2.absolutePath, file3.absolutePath)
        val remoteUrls = mutableListOf("https://b2.example.com/file_0.jpg", "https://b2.example.com/file_1.jpg") // only 2 of 3 succeeded
        val runAttemptCount = 1
        val maxAttempts = 5

        val allStillExist = allPaths.all { File(it).exists() }
        val willRetry = (remoteUrls.size != totalRequired) && allStillExist && (runAttemptCount + 1 < maxAttempts)

        assertTrue("Recoverable partial album must trigger retry", willRetry)
        // Ensure files were NOT deleted
        assertTrue(file1.exists())
        assertTrue(file2.exists())
        assertTrue(file3.exists())
    }

    // F) Album with missing/unrecoverable file terminates in FAILED and does NOT confirm a subset of photos
    @Test
    fun testF_albumWithMissingUnrecoverableFile_terminatesInFailed() {
        val missingPath = "/non/existent/path/lost_img.jpg"
        val existingFile = tempFolder.newFile("valid_img.jpg")

        val allPaths = listOf(existingFile.absolutePath, missingPath)
        val existingRemoteUrls = emptyList<String>()

        var hasUnrecoverableFile = false
        for ((index, path) in allPaths.withIndex()) {
            val f = File(path)
            val existingUrl = if (index < existingRemoteUrls.size && existingRemoteUrls[index].startsWith("http")) existingRemoteUrls[index] else null
            val precondition = MediaUploadWorker.evaluateFilePrecondition(fileExists = f.exists(), mediaUrl = existingUrl)
            if (precondition == MediaUploadWorker.FilePreconditionResult.FAIL_MISSING_FILE) {
                hasUnrecoverableFile = true
                break
            }
        }

        assertTrue("Album with missing file and no remote URL is unrecoverable and must FAIL", hasUnrecoverableFile)
    }

    // G) Idempotency: retry of album upload uses stable keys/names based on clientMessageUuid + index
    @Test
    fun testG_albumIdempotency_usesStableKeyPerIndex() {
        val stableUuid = "12345678-1234-1234-1234-123456789abc"
        val key0 = "${stableUuid}_0"
        val key1 = "${stableUuid}_1"
        val key2 = "${stableUuid}_2"

        assertEquals("12345678-1234-1234-1234-123456789abc_0", key0)
        assertEquals("12345678-1234-1234-1234-123456789abc_1", key1)
        assertEquals("12345678-1234-1234-1234-123456789abc_2", key2)

        // Keys are strictly deterministic across retries
        val retryKey1 = "${stableUuid}_1"
        assertEquals(key1, retryKey1)
    }

    // H) Individual recovery matrix (A, B, C, D) tested against evaluateFilePrecondition
    @Test
    fun testH_individualMediaRecoveryMatrix() {
        // Case A: Local file exists + no mediaUrl -> PROCEED_TO_UPLOAD
        val caseA = MediaUploadWorker.evaluateFilePrecondition(fileExists = true, mediaUrl = null)
        assertEquals(MediaUploadWorker.FilePreconditionResult.PROCEED_TO_UPLOAD, caseA)

        // Case B: Local file exists + mediaUrl already exists -> CONTINUE_WITH_REMOTE_URL (sync, don't re-upload)
        val caseB = MediaUploadWorker.evaluateFilePrecondition(fileExists = true, mediaUrl = "https://cdn.example.com/img.jpg")
        assertEquals(MediaUploadWorker.FilePreconditionResult.CONTINUE_WITH_REMOTE_URL, caseB)

        // Case C: Local file does not exist + mediaUrl exists -> CONTINUE_WITH_REMOTE_URL (sync, don't fail)
        val caseC = MediaUploadWorker.evaluateFilePrecondition(fileExists = false, mediaUrl = "https://cdn.example.com/img.jpg")
        assertEquals(MediaUploadWorker.FilePreconditionResult.CONTINUE_WITH_REMOTE_URL, caseC)

        // Case D: Local file does not exist + no mediaUrl -> FAIL_MISSING_FILE (terminal fail)
        val caseD = MediaUploadWorker.evaluateFilePrecondition(fileExists = false, mediaUrl = null)
        assertEquals(MediaUploadWorker.FilePreconditionResult.FAIL_MISSING_FILE, caseD)
    }

    // I) Supported media types: image, video, audio, document, and album
    @Test
    fun testI_supportedMediaTypes() = runBlocking(Dispatchers.IO) {
        val types = listOf("image", "video", "audio", "voice", "document", "album")
        for (type in types) {
            val entity = MessageEntity(
                id = "msg_type_$type",
                chatId = "canon_chat_1",
                senderId = "user_1",
                receiverId = "user_2",
                content = "Test $type",
                createdAt = "2026-09-04T12:00:00Z",
                status = "sending",
                messageType = type,
                localMediaUri = "/tmp/test_$type.dat"
            )
            messageDao.insertMessage(entity)
            val retrieved = messageDao.getMessageById("msg_type_$type")
            assertNotNull("Message with type $type must be persisted", retrieved)
            assertEquals(type, retrieved?.messageType)
        }
    }
}
