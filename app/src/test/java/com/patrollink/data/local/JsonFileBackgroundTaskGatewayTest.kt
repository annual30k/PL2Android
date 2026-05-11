package com.patrollink.data.local

import com.patrollink.domain.BackgroundTask
import com.patrollink.domain.BackgroundTaskType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JsonFileBackgroundTaskGatewayTest {
    @Test
    fun queuePersistsAcrossGatewayInstancesAndCompletesById() = runTest {
        val file = File.createTempFile("patrol_tasks", ".json").also { it.deleteOnExit() }
        val task = BackgroundTask("TASK-JSON-1", BackgroundTaskType.SyncAlertDisposition, "AL-99824-03", 1715832000L)

        JsonFileBackgroundTaskGateway(file).enqueue(task)
        val reloaded = JsonFileBackgroundTaskGateway(file)

        assertEquals(1, reloaded.pending().size)
        assertEquals(task, reloaded.pending().first().task)
        assertTrue(reloaded.complete(task.id))
        assertTrue(reloaded.pending().isEmpty())
    }
}
