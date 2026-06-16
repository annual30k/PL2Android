package com.patrollink.data.local

import com.patrollink.domain.BackgroundTask
import com.patrollink.domain.BackgroundTaskType
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaKind
import com.patrollink.domain.TransferStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatrolDatabaseMappingTest {
    @Test
    fun offlineTaskEntityRoundTripsDomainReceipt() {
        val task = BackgroundTask("task-1", BackgroundTaskType.UploadEvidence, "VID-1", 123L)

        val receipt = OfflineTaskEntity.from(task).toReceipt()

        assertEquals(task, receipt.task)
        assertTrue(receipt.queued)
    }

    @Test
    fun mediaEntityPreservesIntegrityAndTransferState() {
        val file = MediaFile(
            id = "VID-1",
            name = "video",
            kind = MediaKind.Video,
            time = "10:00",
            size = "1 MB",
            duration = "00:05",
            verified = false,
            local = true,
            transferStatus = TransferStatus.Done,
            progress = 1f,
            contentUri = "file:///tmp/video.mp4"
        )

        val entity = MediaFileEntity.from(file, sha256 = "abc", watermarkToken = "wm")
        val domain = entity.toDomain()

        assertEquals(file.id, domain.id)
        assertTrue(domain.verified)
        assertEquals(TransferStatus.Done, domain.transferStatus)
        assertEquals("file:///tmp/video.mp4", domain.contentUri)
        assertFalse(entity.watermarkToken.isNullOrBlank())
    }

    @Test
    fun mediaEntityStorageKeyIncludesAccountScope() {
        val file = MediaFile(
            id = "VID-1",
            name = "video",
            kind = MediaKind.Video,
            time = "10:00",
            size = "1 MB",
            duration = "00:05",
            verified = false,
            local = true,
            transferStatus = TransferStatus.Done,
            progress = 1f,
            contentUri = "file:///tmp/video.mp4"
        )

        val police = MediaFileEntity.from(file, accountKey = "POLICE_9527")
        val test = MediaFileEntity.from(file, accountKey = "test")

        assertEquals("POLICE_9527", police.accountKey)
        assertEquals("test", test.accountKey)
        assertFalse(police.storageKey == test.storageKey)
    }
}
