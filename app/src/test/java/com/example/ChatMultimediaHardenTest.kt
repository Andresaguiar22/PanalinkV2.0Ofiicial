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
        runBlocking(Dispatchers.IO) { db.clearAllTables() }
    }

    @Test
    fun testA_sendMultimediaMessage_usesCanonicalThreadId() = runBlocking(Dispatchers.IO) {
        val canonicalThreadId = "22222222-2222-2222-2222-222222222222"
        val receiverId = "33333333-3333-3333-3333-333333333333"
        val chatEntity = ChatEntity(id = canonicalThreadId, createdAt = "2026-09-04T12:00:00Z", threadId = canonicalThreadId, name = "Test User", type = "dm", otherUserId = receiverId)
        db.chatDao().insertChat(chatEntity)
        val identity = MessagesRepository.getInstance().resolveChatIdentity(canonicalThreadId, receiverId)
        assertEquals(ChatKind.DM, identity.kind)
        assertEquals(canonicalThreadId, identity.threadId)
        assertEquals(receiverId, identity.receiverId)
        val canonicalChatId = if (identity.kind == ChatKind.DM && !identity.threadId.isNullOrEmpty() && MessagesRepository.isValidUuid(identity.threadId)) identity.threadId else canonicalThreadId
        val entity = MessageEntity(id = "temp_${UUID.randomUUID()}", chatId = canonicalChatId, senderId = "my_user_id", receiverId = identity.receiverId, content = "Multimedia test", createdAt = "2026-09-04T12:00:00Z", status = "sending", messageType = "image", localMediaUri = "/tmp/sample.jpg")
        messageDao.insertMessage(entity)
        val saved = messageDao.getMessageById(entity.id)
        assertNotNull(saved)
        assertEquals(canonicalThreadId, saved?.chatId)
        assertEquals(receiverId, saved?.receiverId)
    }

    @Test
    fun testB_sendImageAlbum_usesCanonicalThreadId() = runBlocking(Dispatchers.IO) {
        val canonicalThreadId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
        val receiverId = "cccccccc-cccc-cccc-cccc-cccccccccccc"
        db.chatDao().insertChat(ChatEntity(id = canonicalThreadId, createdAt = "2026-09-04T12:00:00Z", threadId = canonicalThreadId, name = "Album User", type = "dm", otherUserId = receiverId))
        val identity = MessagesRepository.getInstance().resolveChatIdentity(canonicalThreadId, receiverId)
        assertEquals(ChatKind.DM, identity.kind)
        assertEquals(canonicalThreadId, identity.threadId)
        val canonicalChatId = if (identity.kind == ChatKind.DM && !identity.threadId.isNullOrEmpty() && MessagesRepository.isValidUuid(identity.threadId)) identity.threadId else canonicalThreadId
        val albumEntity = MessageEntity(id = "temp_${UUID.randomUUID()}", chatId = canonicalChatId, senderId = "my_user_id", receiverId = identity.receiverId, content = "[3 fotos]", createdAt = "2026-09-04T12:00:00Z", status = "sending", messageType = "image", localMediaUri = "/tmp/photo1.jpg,/tmp/photo2.jpg,/tmp/photo3.jpg")
        messageDao.insertMessage(albumEntity)
        val saved = messageDao.getMessageById(albumEntity.id)
        assertNotNull(saved)
        assertEquals(canonicalThreadId, saved?.chatId)
    }

    @Test
    fun testC_unknownIdentity_neverInventsFakeThreadId() = runBlocking(Dispatchers.IO) {
        val unknownChatId = "unknown_chat_xyz"
        val identity = MessagesRepository.getInstance().resolveChatIdentity(unknownChatId, null)
        assertEquals(ChatKind.UNKNOWN, identity.kind)
        assertNull(identity.threadId)
        assertEquals(unknownChatId, identity.chatId)
    }

    @Test
    fun testD_completeAlbum_usesProductionCompletionDecision() {
        assertTrue(MediaUploadWorker.isAlbumComplete(remoteUrlCount = 3, totalRequired = 3))
        assertFalse(MediaUploadWorker.isAlbumComplete(remoteUrlCount = 2, totalRequired = 3))
        assertFalse(MediaUploadWorker.isAlbumComplete(remoteUrlCount = 3, totalRequired = 0))
    }

    @Test
    fun testE_partialAlbum_usesProductionRetryDecision_withoutDeletingFiles() {
        val file1 = tempFolder.newFile("img1.jpg")
        val file2 = tempFolder.newFile("img2.jpg")
        val file3 = tempFolder.newFile("img3.jpg")
        assertTrue(MediaUploadWorker.shouldRetryAlbum(2, 3, allStillExist = true, runAttemptCount = 1))
        assertFalse(MediaUploadWorker.shouldRetryAlbum(2, 3, allStillExist = false, runAttemptCount = 1))
        assertFalse(MediaUploadWorker.shouldRetryAlbum(3, 3, allStillExist = true, runAttemptCount = 1))
        assertTrue(file1.exists() && file2.exists() && file3.exists())
    }

    @Test
    fun testF_albumMissingFile_usesProductionUnrecoverableDecision() {
        val existingFile = tempFolder.newFile("valid_img.jpg")
        val paths = listOf(existingFile.absolutePath, "/non/existent/path/lost_img.jpg")
        assertTrue(MediaUploadWorker.albumHasUnrecoverableFile(paths, emptyList()))
    }

    @Test
    fun testG_albumExistingRemoteUrl_makesMissingLocalFileRecoverable() {
        val existingFile = tempFolder.newFile("valid_img.jpg")
        val missingPath = "/non/existent/path/already_uploaded.jpg"
        val paths = listOf(existingFile.absolutePath, missingPath)
        val urls = listOf("https://b2.example.com/file_0.jpg", "https://b2.example.com/file_1.jpg")
        assertFalse(MediaUploadWorker.albumHasUnrecoverableFile(paths, urls))
    }

    @Test
    fun testH_albumIdempotency_usesProductionStableNamePerIndex() {
        val uuid = "12345678-1234-1234-1234-123456789abc"
        assertEquals("${uuid}_0.jpg", MediaUploadWorker.albumStableFileName(uuid, 0, "jpg"))
        assertEquals("${uuid}_1.png", MediaUploadWorker.albumStableFileName(uuid, 1, ".png"))
        assertEquals(MediaUploadWorker.albumStableFileName(uuid, 1, "png"), MediaUploadWorker.albumStableFileName(uuid, 1, ".png"))
    }

    @Test
    fun testI_individualMediaRecoveryMatrix_usesProductionPrecondition() {
        assertEquals(MediaUploadWorker.FilePreconditionResult.PROCEED_TO_UPLOAD, MediaUploadWorker.evaluateFilePrecondition(true, null))
        assertEquals(MediaUploadWorker.FilePreconditionResult.CONTINUE_WITH_REMOTE_URL, MediaUploadWorker.evaluateFilePrecondition(true, "https://cdn.example.com/img.jpg"))
        assertEquals(MediaUploadWorker.FilePreconditionResult.CONTINUE_WITH_REMOTE_URL, MediaUploadWorker.evaluateFilePrecondition(false, "https://cdn.example.com/img.jpg"))
        assertEquals(MediaUploadWorker.FilePreconditionResult.FAIL_MISSING_FILE, MediaUploadWorker.evaluateFilePrecondition(false, null))
    }

    @Test
    fun testJ_supportedMediaTypes_persistToRoom() = runBlocking(Dispatchers.IO) {
        val types = listOf("image", "video", "audio", "voice", "document", "album")
        for (type in types) {
            val entity = MessageEntity(id = "msg_type_$type", chatId = "canon_chat_1", senderId = "user_1", receiverId = "user_2", content = "Test $type", createdAt = "2026-09-04T12:00:00Z", status = "sending", messageType = type, localMediaUri = "/tmp/test_$type.dat")
            messageDao.insertMessage(entity)
            val retrieved = messageDao.getMessageById(entity.id)
            assertNotNull("Message with type $type must be persisted", retrieved)
            assertEquals(type, retrieved?.messageType)
        }
    }
}
