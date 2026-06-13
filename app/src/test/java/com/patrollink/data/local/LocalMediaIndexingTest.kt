package com.patrollink.data.local

import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaKind
import com.patrollink.domain.TransferStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalMediaIndexingTest {
    @Test
    fun indexesDiscoveredLocalMediaWithUsableContentUri() = runTest {
        val writer = RecordingMediaIndexWriter()
        val file = MediaFile(
            id = "ute-audio-session-1",
            name = "session-1.opus",
            kind = MediaKind.Audio,
            time = "123",
            size = "1 KB",
            duration = null,
            verified = true,
            local = true,
            transferStatus = TransferStatus.Done,
            progress = 1f,
            contentUri = "file:///tmp/session-1.opus"
        )

        writer.upsertLocalMediaSnapshot(listOf(file))

        assertEquals(file.copy(transferStatus = TransferStatus.Idle, progress = 0f, lastTransferTarget = null), writer.file)
        assertEquals("file:///tmp/session-1.opus", writer.localPath)
    }

    private class RecordingMediaIndexWriter : MediaIndexWriter {
        var file: MediaFile? = null
        var localPath: String? = null

        override suspend fun upsert(file: MediaFile, localPath: String?, sha256: String?, watermarkToken: String?) {
            this.file = file
            this.localPath = localPath
        }
    }
}
