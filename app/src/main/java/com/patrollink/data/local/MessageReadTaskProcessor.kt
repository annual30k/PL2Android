package com.patrollink.data.local

import com.patrollink.data.remote.PatrolRestApi
import com.patrollink.domain.BackgroundTask
import com.patrollink.domain.BackgroundTaskType

class MessageReadTaskProcessor(
    private val markRead: suspend (String) -> Unit
) {
    constructor(api: PatrolRestApi) : this(markRead = { messageId ->
        api.readMessage(messageId)
        Unit
    })

    suspend fun process(task: BackgroundTask): Boolean {
        if (task.type != BackgroundTaskType.SyncMessageRead || task.payloadId.isBlank()) return false
        return runCatching { markRead(task.payloadId) }.isSuccess
    }
}
