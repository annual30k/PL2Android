package com.patrollink.data.local

import com.patrollink.domain.BackgroundTask
import com.patrollink.domain.BackgroundTaskType
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SosEvidenceUploadTaskProcessorTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun uploadsExistingRecordingWithSameSosId() = runTest {
        val recording = temp.newFile("sos.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        var uploadedFile: File? = null
        var uploadedSosId = ""
        val processor = SosEvidenceUploadTaskProcessor { file, sosId ->
            uploadedFile = file
            uploadedSosId = sosId
        }

        assertTrue(processor.process(task(recording)))
        assertEquals(recording, uploadedFile)
        assertEquals("SOS-APP-12345678", uploadedSosId)
    }

    @Test
    fun keepsMissingOrFailedRecordingPending() = runTest {
        val missing = temp.root.resolve("missing.m4a")
        assertFalse(SosEvidenceUploadTaskProcessor { _, _ -> }.process(task(missing)))

        val existing = temp.newFile("failed.m4a").apply { writeBytes(byteArrayOf(4)) }
        assertFalse(SosEvidenceUploadTaskProcessor { _, _ -> error("offline") }.process(task(existing)))
    }

    private fun task(file: File) = BackgroundTask(
        "upload-sos-evidence",
        BackgroundTaskType.UploadSosEvidence,
        QueuedSosEvidenceCodec.encode(QueuedSosEvidence("SOS-APP-12345678", file.absolutePath)),
        1
    )
}
