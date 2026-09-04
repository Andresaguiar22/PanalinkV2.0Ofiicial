package com.example.worker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialMediaUploadWorkerGuardTest {

    @Test
    fun `remoteUrl present allows worker to continue when local file is missing`() {
        assertFalse(
            SocialMediaUploadWorker.shouldFailForMissingLocalFile(
                fileExists = false,
                hasRemoteUrl = true
            )
        )
    }

    @Test
    fun `missing local file without remoteUrl fails worker`() {
        assertTrue(
            SocialMediaUploadWorker.shouldFailForMissingLocalFile(
                fileExists = false,
                hasRemoteUrl = false
            )
        )
    }

    @Test
    fun `existing local file always bypasses missing-file failure guard`() {
        assertFalse(
            SocialMediaUploadWorker.shouldFailForMissingLocalFile(
                fileExists = true,
                hasRemoteUrl = false
            )
        )
    }
}
