package com.patrollink.data.ute

import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaKind
import com.patrollink.domain.TransferStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UteCloudUploadResultTest {
    @Test
    fun rejectsMissingUploadResult() {
        val failure = runCatching {
            requireUteCloudUploadResult(fileId = "ute-wifi-video-1", uploaded = null)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("media upload did not return uploaded file: ute-wifi-video-1", failure?.message)
    }

    @Test
    fun returnsUploadedMediaResult() {
        val media = MediaFile(
            id = "server-media-id",
            name = "clip.mp4",
            kind = MediaKind.Video,
            time = "123",
            size = "4 B",
            duration = null,
            verified = true,
            local = true,
            transferStatus = TransferStatus.Done,
            progress = 1f
        )

        assertEquals(media, requireUteCloudUploadResult(fileId = "ute-wifi-video-1", uploaded = media))
    }
}
