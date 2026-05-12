package com.patrollink.domain

import com.patrollink.data.InMemoryBackgroundTaskGateway
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineSyncEngineTest {
    @Test
    fun enqueueAndDrainCompletesOnlySuccessfullyProcessedTasks() = runTest {
        val gateway = InMemoryBackgroundTaskGateway()
        val engine = OfflineSyncEngine(gateway)

        engine.enqueueAlertDisposition("AL-1", 100)
        engine.enqueueEvidenceUpload("VID-1", 101)

        val completed = engine.drain { task -> task.type == BackgroundTaskType.SyncAlertDisposition }

        assertEquals(1, completed)
        assertEquals(1, gateway.pending().size)
        assertEquals(BackgroundTaskType.UploadEvidence, gateway.pending().first().task.type)
    }
}
