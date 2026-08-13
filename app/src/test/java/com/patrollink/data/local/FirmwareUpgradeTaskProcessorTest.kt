package com.patrollink.data.local

import com.patrollink.domain.BackgroundTask
import com.patrollink.domain.BackgroundTaskType
import com.patrollink.domain.FirmwareUpgradeState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirmwareUpgradeTaskProcessorTest {
    @Test
    fun replaysLatestFirmwareTerminalStatus() = runTest {
        var syncedTaskId = ""
        var syncedStatus = ""
        val processor = FirmwareUpgradeTaskProcessor { taskId, request ->
            syncedTaskId = taskId
            syncedStatus = request.status
        }
        val task = firmwareTask("FUT-1", FirmwareUpgradeState("SUCCESS", 1f))

        assertTrue(processor.process(task))
        assertEquals("FUT-1", syncedTaskId)
        assertEquals("SUCCESS", syncedStatus)
    }

    @Test
    fun keepsFirmwareReceiptQueuedWhenBackendIsUnavailable() = runTest {
        val processor = FirmwareUpgradeTaskProcessor { _, _ -> error("offline") }

        assertFalse(processor.process(firmwareTask("FUT-2", FirmwareUpgradeState("FAILED", 0f))))
    }

    private fun firmwareTask(taskId: String, state: FirmwareUpgradeState) = BackgroundTask(
        id = "sync-firmware-upgrade-$taskId",
        type = BackgroundTaskType.SyncFirmwareUpgrade,
        payloadId = QueuedFirmwareUpgradeStatusCodec.encode(taskId, state),
        createdAt = 1L
    )
}
