package com.patrollink.data.local

import com.patrollink.domain.BackgroundTask
import com.patrollink.domain.BackgroundTaskType
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaGateway
import com.patrollink.domain.MediaKind
import com.patrollink.domain.TransferStatus
import com.patrollink.domain.TransferTarget
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EvidenceUploadTaskProcessorTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun uploadsLocalEvidenceFileAndStoresCompletedMedia() = runTest {
        val localFile = temp.newFile("clip.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val media = MediaFile(
            id = "ute-video-clip",
            name = "clip.mp4",
            kind = MediaKind.Video,
            time = "123",
            size = "3 B",
            duration = null,
            verified = true,
            local = true,
            transferStatus = TransferStatus.Idle,
            progress = 0f,
            contentUri = localFile.toURI().toString()
        )
        val store = FakeLocalMediaStore(media)
        val gateway = RecordingUploadGateway()
        val processor = EvidenceUploadTaskProcessor(store, gateway)

        val processed = processor.process(
            BackgroundTask(
                id = "upload-evidence-ute-video-clip-123",
                type = BackgroundTaskType.UploadEvidence,
                payloadId = "ute-video-clip",
                createdAt = 123
            )
        )

        assertTrue(processed)
        assertEquals(localFile, gateway.uploadedFile)
        assertEquals("PHONE", gateway.storageSide)
        assertEquals("MEDIA", gateway.bizType)
        assertEquals("ute-video-clip", gateway.bizId)
        assertEquals(TransferTarget.Cloud, store.upserted?.lastTransferTarget)
        assertEquals(TransferStatus.Done, store.upserted?.transferStatus)
    }

    @Test
    fun ignoresNonUploadEvidenceTasks() = runTest {
        val processor = EvidenceUploadTaskProcessor(FakeLocalMediaStore(), RecordingUploadGateway())

        val processed = processor.process(
            BackgroundTask(
                id = "sync-alert-1",
                type = BackgroundTaskType.SyncAlertDisposition,
                payloadId = "AL-1",
                createdAt = 1
            )
        )

        assertFalse(processed)
    }

    @Test
    fun keepsTaskPendingWhenLocalFileIsMissing() = runTest {
        val media = MediaFile(
            id = "ute-audio-missing",
            name = "missing.opus",
            kind = MediaKind.Audio,
            time = "123",
            size = "0 B",
            duration = null,
            verified = false,
            local = true,
            transferStatus = TransferStatus.Idle,
            progress = 0f,
            contentUri = temp.root.resolve("missing.opus").toURI().toString()
        )
        val gateway = RecordingUploadGateway()
        val processor = EvidenceUploadTaskProcessor(FakeLocalMediaStore(media), gateway)

        val processed = processor.process(
            BackgroundTask(
                id = "upload-evidence-ute-audio-missing-123",
                type = BackgroundTaskType.UploadEvidence,
                payloadId = "ute-audio-missing",
                createdAt = 123
            )
        )

        assertFalse(processed)
        assertEquals(null, gateway.uploadedFile)
    }

    @Test
    fun keepsTaskPendingWhenUploadGatewayReturnsNoUploadedMedia() = runTest {
        val localFile = temp.newFile("not-uploaded.mp4").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val media = MediaFile(
            id = "ute-video-not-uploaded",
            name = "not-uploaded.mp4",
            kind = MediaKind.Video,
            time = "123",
            size = "3 B",
            duration = null,
            verified = false,
            local = true,
            transferStatus = TransferStatus.Idle,
            progress = 0f,
            contentUri = localFile.toURI().toString()
        )
        val store = FakeLocalMediaStore(media)
        val gateway = RecordingUploadGateway(returnNullUpload = true)
        val processor = EvidenceUploadTaskProcessor(store, gateway)

        val processed = processor.process(
            BackgroundTask(
                id = "upload-evidence-ute-video-not-uploaded-123",
                type = BackgroundTaskType.UploadEvidence,
                payloadId = "ute-video-not-uploaded",
                createdAt = 123
            )
        )

        assertFalse(processed)
        assertEquals(localFile, gateway.uploadedFile)
        assertEquals(null, store.upserted)
    }

    private class FakeLocalMediaStore(private val media: MediaFile? = null) : LocalMediaStore {
        var upserted: MediaFile? = null

        override suspend fun findLocal(fileId: String): MediaFile? =
            media?.takeIf { it.id == fileId && it.local }

        override suspend fun upsertUploaded(file: MediaFile, localPath: String?, sha256: String?) {
            upserted = file
        }
    }

    private class RecordingUploadGateway(
        private val returnNullUpload: Boolean = false
    ) : MediaGateway {
        var uploadedFile: File? = null
        var storageSide: String? = null
        var bizType: String? = null
        var bizId: String? = null

        override suspend fun listFiles(local: Boolean): List<MediaFile> = emptyList()
        override fun transfer(fileId: String, target: TransferTarget): Flow<MediaFile> = emptyFlow()

        override suspend fun uploadLocalFile(file: File, storageSide: String, bizType: String, bizId: String): MediaFile? {
            uploadedFile = file
            this.storageSide = storageSide
            this.bizType = bizType
            this.bizId = bizId
            if (returnNullUpload) return null
            return MediaFile(
                id = bizId,
                name = file.name,
                kind = MediaKind.Video,
                time = "456",
                size = "${file.length()} B",
                duration = null,
                verified = true,
                local = true,
                transferStatus = TransferStatus.Done,
                progress = 1f,
                contentUri = file.toURI().toString(),
                lastTransferTarget = TransferTarget.Cloud
            )
        }

        override suspend fun delete(fileId: String, local: Boolean): Boolean = false
        override suspend fun verifySha256(fileId: String): Boolean = false
    }
}
