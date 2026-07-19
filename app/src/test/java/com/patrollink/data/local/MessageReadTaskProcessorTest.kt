package com.patrollink.data.local

import com.patrollink.domain.BackgroundTask
import com.patrollink.domain.BackgroundTaskType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageReadTaskProcessorTest {
    @Test
    fun marksQueuedMessageAsRead() = runTest {
        var markedId = ""
        val processor = MessageReadTaskProcessor { markedId = it }

        val processed = processor.process(messageTask("MSG-9"))

        assertTrue(processed)
        assertEquals("MSG-9", markedId)
    }

    @Test
    fun keepsTaskPendingWhenBackendRejectsReceipt() = runTest {
        val processor = MessageReadTaskProcessor { error("backend unavailable") }

        assertFalse(processor.process(messageTask("MSG-10")))
    }

    @Test
    fun ignoresUnrelatedTask() = runTest {
        val processor = MessageReadTaskProcessor { error("must not run") }
        val task = BackgroundTask("heartbeat-1", BackgroundTaskType.Heartbeat, "HEADSET-1", 1)

        assertFalse(processor.process(task))
    }

    private fun messageTask(messageId: String) = BackgroundTask(
        id = "sync-message-read-$messageId",
        type = BackgroundTaskType.SyncMessageRead,
        payloadId = messageId,
        createdAt = 1
    )
}
