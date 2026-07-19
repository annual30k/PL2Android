package com.patrollink.data.local

import com.patrollink.domain.BackgroundTask
import com.patrollink.domain.BackgroundTaskType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceUnbindTaskProcessorTest {
    @Test
    fun unbindTaskCallsPlatformAndCompletes() = runTest {
        var unboundDeviceId: String? = null
        val processor = DeviceUnbindTaskProcessor { unboundDeviceId = it }
        val task = BackgroundTask("sync-device-unbind-HEADSET-1", BackgroundTaskType.SyncDeviceUnbind, "HEADSET-1", 1)

        assertTrue(processor.process(task))
        assertEquals("HEADSET-1", unboundDeviceId)
    }

    @Test
    fun backendFailureKeepsTaskPending() = runTest {
        val processor = DeviceUnbindTaskProcessor { error("backend unavailable") }

        assertFalse(processor.process(BackgroundTask("unbind", BackgroundTaskType.SyncDeviceUnbind, "HEADSET-1", 1)))
    }

    @Test
    fun unrelatedTaskIsRejectedWithoutCallingPlatform() = runTest {
        var called = false
        val processor = DeviceUnbindTaskProcessor { called = true }

        assertFalse(processor.process(BackgroundTask("heartbeat", BackgroundTaskType.Heartbeat, "HEADSET-1", 1)))
        assertFalse(called)
    }
}
