package com.example

import com.example.service.NotificationDeduplicator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationDeduplicationTest {

    @Before
    fun setup() {
        NotificationDeduplicator.clearForTest()
    }

    @Test
    fun testRealtimeAndFcmDeduplicationByClientUuid() {
        val clientUuid = "uuid-test-12345"
        val messageId = "msg-db-98765"

        // 1. First arrival (e.g., from Realtime in foreground) -> Should show
        val shouldShowFirst = NotificationDeduplicator.shouldNotify(clientUuid, messageId)
        assertTrue("El primer evento de notificación debe permitirse", shouldShowFirst)

        // 2. Second arrival (e.g., from FCM background push) -> Should NOT show
        val shouldShowDuplicate = NotificationDeduplicator.shouldNotify(clientUuid, messageId)
        assertFalse("El duplicado (FCM/Realtime) con el mismo clientMessageUuid debe bloquearse", shouldShowDuplicate)
    }

    @Test
    fun testDeduplicationByRemoteMessageIdFallback() {
        val messageId = "msg-db-only-555"

        // Null clientUuid, relying only on messageId
        val first = NotificationDeduplicator.shouldNotify(null, messageId)
        assertTrue(first)

        val duplicate = NotificationDeduplicator.shouldNotify(null, messageId)
        assertFalse(duplicate)
    }

    @Test
    fun testDistinctMessagesAreAllowed() {
        val msg1 = NotificationDeduplicator.shouldNotify("uuid-1", "id-1")
        val msg2 = NotificationDeduplicator.shouldNotify("uuid-2", "id-2")
        assertTrue(msg1)
        assertTrue(msg2)
    }
}
