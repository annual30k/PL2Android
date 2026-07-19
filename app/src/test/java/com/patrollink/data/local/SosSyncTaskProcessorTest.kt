package com.patrollink.data.local

import com.patrollink.data.remote.GpsLocationDto
import com.patrollink.domain.BackgroundTask
import com.patrollink.domain.BackgroundTaskType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SosSyncTaskProcessorTest {
    @Test
    fun replaysActivationAndCancellationWithStableClientId() = runTest {
        val actions = mutableListOf<String>()
        val processor = SosSyncTaskProcessor { actions += "${it.action}:${it.clientSosId}" }
        val id = "SOS-APP-12345678"

        assertTrue(processor.process(task(QueuedSosSync(id, "ACTIVATE", location(id)), 1)))
        assertTrue(processor.process(task(QueuedSosSync(id, "CANCEL"), 2)))

        assertEquals(listOf("ACTIVATE:$id", "CANCEL:$id"), actions)
    }

    @Test
    fun rejectsActivationWithoutLocationAndKeepsNetworkFailurePending() = runTest {
        val invalid = BackgroundTask(
            "invalid",
            BackgroundTaskType.SyncSosState,
            """{"clientSosId":"SOS-APP-12345678","action":"ACTIVATE"}""",
            1
        )
        assertFalse(SosSyncTaskProcessor { }.process(invalid))

        val failed = SosSyncTaskProcessor { error("offline") }
        assertFalse(failed.process(task(QueuedSosSync("SOS-APP-87654321", "CANCEL"), 2)))
    }

    private fun task(payload: QueuedSosSync, time: Long) = BackgroundTask(
        "sync-sos-$time",
        BackgroundTaskType.SyncSosState,
        QueuedSosSyncCodec.encode(payload),
        time
    )

    private fun location(id: String) = GpsLocationDto(26.1, 119.3, 8f, "测试位置", "HEADSET-1", id)
}
