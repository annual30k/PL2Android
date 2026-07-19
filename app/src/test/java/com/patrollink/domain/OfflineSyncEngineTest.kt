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

    @Test
    fun evidenceUploadTasksAreDedupedByFileId() = runTest {
        val gateway = InMemoryBackgroundTaskGateway()
        val engine = OfflineSyncEngine(gateway)

        engine.enqueueEvidenceUpload("VID-1", 101)
        engine.enqueueEvidenceUpload("VID-1", 202)

        val pending = gateway.pending()
        assertEquals(1, pending.size)
        assertEquals("VID-1", pending.single().task.payloadId)
        assertEquals(202, pending.single().task.createdAt)
    }

    @Test
    fun commandAckIsPersistedAndDetectedToPreventDuplicateHardwareExecution() = runTest {
        val gateway = InMemoryBackgroundTaskGateway()
        val engine = OfflineSyncEngine(gateway)

        engine.enqueueDeviceCommandAck("CMD-42", "{\"commandId\":\"CMD-42\"}", 301)
        engine.enqueueDeviceCommandAck("CMD-42", "{\"commandId\":\"CMD-42\"}", 302)

        assertEquals(1, gateway.pending().size)
        assertEquals(true, engine.hasPendingDeviceCommandAck("CMD-42"))
        assertEquals(false, engine.hasPendingDeviceCommandAck("CMD-43"))
    }

    @Test
    fun heartbeatQueueKeepsOnlyLatestLocationForDevice() = runTest {
        val gateway = InMemoryBackgroundTaskGateway()
        val engine = OfflineSyncEngine(gateway)

        engine.enqueueHeartbeat("HEADSET_001", "first", 401)
        engine.enqueueHeartbeat("HEADSET_001", "latest", 402)

        val pending = gateway.pending().single()
        assertEquals(BackgroundTaskType.Heartbeat, pending.task.type)
        assertEquals("latest", pending.task.payloadId)
        assertEquals(402, pending.task.createdAt)
    }

    @Test
    fun messageReadReceiptIsDedupedAndVisibleToCloudRefresh() = runTest {
        val gateway = InMemoryBackgroundTaskGateway()
        val engine = OfflineSyncEngine(gateway)

        engine.enqueueMessageRead("MSG-42", 501)
        engine.enqueueMessageRead("MSG-42", 502)

        assertEquals(setOf("MSG-42"), engine.pendingMessageReadIds())
        val pending = gateway.pending().single().task
        assertEquals(BackgroundTaskType.SyncMessageRead, pending.type)
        assertEquals(502, pending.createdAt)
    }

    @Test
    fun deviceUnbindIsPersistedAndDedupedUntilPlatformRecovers() = runTest {
        val gateway = InMemoryBackgroundTaskGateway()
        val engine = OfflineSyncEngine(gateway)

        engine.enqueueDeviceUnbind("HEADSET_001", 551)
        engine.enqueueDeviceUnbind("HEADSET_001", 552)

        val pending = gateway.pending().single().task
        assertEquals(BackgroundTaskType.SyncDeviceUnbind, pending.type)
        assertEquals("HEADSET_001", pending.payloadId)
        assertEquals(552, pending.createdAt)
    }

    @Test
    fun sosActivationCancellationAndEvidenceRemainOrderedAndDeduped() = runTest {
        val gateway = InMemoryBackgroundTaskGateway()
        val engine = OfflineSyncEngine(gateway)
        val sosId = "SOS-APP-12345678"

        engine.enqueueSosState(sosId, "activate", "ACTIVATE", 601)
        engine.enqueueSosState(sosId, "cancel", "CANCEL", 602)
        engine.enqueueSosEvidence(sosId, "first-file", 603)
        engine.enqueueSosEvidence(sosId, "latest-file", 604)

        val pending = gateway.pending().map { it.task }
        assertEquals(
            listOf(BackgroundTaskType.SyncSosState, BackgroundTaskType.SyncSosState, BackgroundTaskType.UploadSosEvidence),
            pending.map { it.type }
        )
        assertEquals(listOf("activate", "cancel", "latest-file"), pending.map { it.payloadId })
    }
}
