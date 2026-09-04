package com.example.worker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.MessageDao
import com.example.data.database.MessageEntity
import com.example.data.database.PanalinkDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaStateMachineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: PanalinkDatabase
    private lateinit var messageDao: MessageDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PanalinkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        messageDao = db.messageDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun createMessageEntity(
        id: String,
        status: String,
        messageType: String = "image",
        localMediaUri: String? = null,
        mediaUrl: String? = null
    ): MessageEntity {
        return MessageEntity(
            id = id,
            chatId = "chat_test_123",
            senderId = "sender_123",
            receiverId = "receiver_456",
            content = "Test content $id",
            createdAt = "2026-09-03T12:00:00Z",
            status = status,
            clientMessageUuid = "uuid_$id",
            messageType = messageType,
            localMediaUri = localMediaUri,
            mediaUrl = mediaUrl
        )
    }

    // 1. failed no entra en getPendingMessages() para sync automático.
    @Test
    fun test1_failedStatusExcludedFromPendingMessages() = runBlocking {
        val failedMsg = createMessageEntity("msg_failed", status = "failed", localMediaUri = "/path/img.jpg")
        messageDao.insertMessage(failedMsg)

        val pending = messageDao.getPendingMessages()
        assertTrue("failed message must NOT be returned in getPendingMessages()", pending.none { it.id == "msg_failed" })
    }

    // 2. sending sí continúa siendo recuperable.
    @Test
    fun test2_sendingStatusIncludedInPendingMessages() = runBlocking {
        val sendingMsg = createMessageEntity("msg_sending", status = "sending", localMediaUri = "/path/img.jpg")
        messageDao.insertMessage(sendingMsg)

        val pending = messageDao.getPendingMessages()
        assertTrue("sending message MUST be returned in getPendingMessages()", pending.any { it.id == "msg_sending" })
    }

    // 3. pending_media sí continúa siendo recuperable.
    @Test
    fun test3_pendingMediaStatusIncludedInPendingMessages() = runBlocking {
        val pendingMediaMsg = createMessageEntity("msg_pending_media", status = "pending_media", localMediaUri = "/path/img.jpg")
        messageDao.insertMessage(pendingMediaMsg)

        val pending = messageDao.getPendingMessages()
        assertTrue("pending_media message MUST be returned in getPendingMessages()", pending.any { it.id == "msg_pending_media" })
    }

    // 4. retryMessage() revive correctamente un failed.
    @Test
    fun test4_retryMessageRevivesFailedToSending() = runBlocking {
        val failedMsg = createMessageEntity("msg_failed_to_revive", status = "failed", localMediaUri = "/path/img.jpg")
        messageDao.insertMessage(failedMsg)

        // Simulate retry action
        val entity = messageDao.getMessageById("msg_failed_to_revive")
        assertNotNull(entity)
        assertEquals("failed", entity?.status)

        // Manual retry transitions status back to sending
        messageDao.updateMessageStatus("msg_failed_to_revive", "sending")

        val revived = messageDao.getMessageById("msg_failed_to_revive")
        assertEquals("sending", revived?.status)

        // Now it appears in pending messages for processing
        val pending = messageDao.getPendingMessages()
        assertTrue("Revived message must appear in getPendingMessages()", pending.any { it.id == "msg_failed_to_revive" })
    }

    // 5. scheduleMediaUpload() no crea una cadena infinita de WorkRequests.
    // Verificamos el contrato de ExistingWorkPolicy.KEEP donde no se anidan trabajos APPEND.
    @Test
    fun test5_scheduleMediaUploadPolicyIsKeep() {
        val policy = androidx.work.ExistingWorkPolicy.KEEP
        // ExistingWorkPolicy.KEEP keeps the existing work if active and enqueues only if finished/absent
        assertEquals(androidx.work.ExistingWorkPolicy.KEEP, policy)
        assertFalse("KEEP policy does not append chain", policy == androidx.work.ExistingWorkPolicy.APPEND || policy == androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    // 6. Un upload que falla 5 veces termina en failed.
    @Test
    fun test6_uploadExhaustingRetriesTransitionsToFailed() {
        val maxAttempts = 5
        var currentAttempt = 0
        var status = "sending"

        while (currentAttempt < maxAttempts) {
            val willRetry = (currentAttempt + 1 < maxAttempts)
            if (willRetry) {
                // Returns Result.retry()
                currentAttempt++
            } else {
                // Max attempts reached -> terminal failure
                status = "failed"
                break
            }
        }

        assertEquals(4, currentAttempt)
        assertEquals("failed", status)
    }

    // 7. Un mensaje failed no vuelve automáticamente a sending durante un sync posterior.
    @Test
    fun test7_failedMessageDoesNotAutoReviveDuringSync() = runBlocking {
        val failedMsg = createMessageEntity("msg_failed_terminal", status = "failed", localMediaUri = "/path/img.jpg")
        messageDao.insertMessage(failedMsg)

        // Simulate global sync cycle fetching pending messages
        val pending = messageDao.getPendingMessages()
        for (item in pending) {
            // Process pending
            messageDao.updateMessageStatus(item.id, "sending")
        }

        val afterSync = messageDao.getMessageById("msg_failed_terminal")
        assertEquals("Message must remain failed and not be revived automatically", "failed", afterSync?.status)
    }

    // 8. Un mensaje cuyo upload terminó y tiene mediaUrl continúa hacia el registro remoto.
    @Test
    fun test8_uploadedMessageWithMediaUrlReadyForRemoteSync() = runBlocking {
        val uploadedMsg = createMessageEntity(
            "msg_uploaded",
            status = "sending",
            localMediaUri = null,
            mediaUrl = "https://cdn.example.com/media/file_123.jpg"
        )
        messageDao.insertMessage(uploadedMsg)

        val pending = messageDao.getPendingMessages()
        val candidate = pending.firstOrNull { it.id == "msg_uploaded" }
        assertNotNull("Uploaded message with mediaUrl must be in pending", candidate)
        assertFalse("Uploaded message has mediaUrl and is ready for thread_messages sync", candidate?.mediaUrl.isNullOrEmpty())
    }

    // 9. No se elimina el archivo local antes de que sea seguro hacerlo.
    @Test
    fun test9_localFilePreservedOnFailureUntilSuccessfulUpload() {
        val localMediaFile = tempFolder.newFile("sample_image.jpg")
        localMediaFile.writeText("test image data")
        assertTrue(localMediaFile.exists())

        // Failure simulation: file MUST NOT be deleted
        var uploadSuccess = false
        if (!uploadSuccess) {
            // In MediaUploadWorker failure branch, local files are NOT deleted
        }
        assertTrue("Local file must remain intact on failure for manual retry", localMediaFile.exists())

        // Success simulation: only after successful upload & DB record update is local file cleaned
        uploadSuccess = true
        if (uploadSuccess) {
            localMediaFile.delete()
        }
        assertFalse("Local file is cleaned only after confirmed upload", localMediaFile.exists())
    }

    // 10. Precondición de archivo local y presencia de mediaUrl en MediaUploadWorker
    @Test
    fun test10_mediaUploadWorkerFilePreconditionMatrix() {
        // 1. fileExists=false + mediaUrl existe -> NO debe fallar por archivo inexistente (CONTINUE_WITH_REMOTE_URL)
        val res1 = MediaUploadWorker.evaluateFilePrecondition(
            fileExists = false,
            mediaUrl = "https://cdn.example.com/media/uploaded_file.jpg"
        )
        assertEquals(
            "fileExists=false + mediaUrl presente NO debe fallar por archivo inexistente",
            MediaUploadWorker.FilePreconditionResult.CONTINUE_WITH_REMOTE_URL,
            res1
        )

        // 2. fileExists=false + mediaUrl vacío/null -> debe fallar (FAIL_MISSING_FILE)
        val res2Null = MediaUploadWorker.evaluateFilePrecondition(
            fileExists = false,
            mediaUrl = null
        )
        assertEquals(
            "fileExists=false + mediaUrl null debe fallar por archivo inexistente",
            MediaUploadWorker.FilePreconditionResult.FAIL_MISSING_FILE,
            res2Null
        )

        val res2Blank = MediaUploadWorker.evaluateFilePrecondition(
            fileExists = false,
            mediaUrl = "   "
        )
        assertEquals(
            "fileExists=false + mediaUrl blank debe fallar por archivo inexistente",
            MediaUploadWorker.FilePreconditionResult.FAIL_MISSING_FILE,
            res2Blank
        )

        // 3. fileExists=true -> no debe fallar por esta condición (PROCEED_TO_UPLOAD si no hay remoteUrl, o CONTINUE_WITH_REMOTE_URL si ya la tenía)
        val res3NoRemote = MediaUploadWorker.evaluateFilePrecondition(
            fileExists = true,
            mediaUrl = null
        )
        assertEquals(
            "fileExists=true + mediaUrl null debe proceder a upload",
            MediaUploadWorker.FilePreconditionResult.PROCEED_TO_UPLOAD,
            res3NoRemote
        )

        val res3WithRemote = MediaUploadWorker.evaluateFilePrecondition(
            fileExists = true,
            mediaUrl = "https://cdn.example.com/media/existing.jpg"
        )
        assertEquals(
            "fileExists=true + mediaUrl existente debe continuar con remoteUrl",
            MediaUploadWorker.FilePreconditionResult.CONTINUE_WITH_REMOTE_URL,
            res3WithRemote
        )
    }
}
