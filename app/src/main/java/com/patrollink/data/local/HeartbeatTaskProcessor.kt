package com.patrollink.data.local

import com.google.gson.Gson
import com.patrollink.data.remote.HeartbeatRequestDto
import com.patrollink.data.remote.PatrolRestApi
import com.patrollink.domain.BackgroundTask
import com.patrollink.domain.BackgroundTaskType

data class QueuedHeartbeat(val request: HeartbeatRequestDto)

object QueuedHeartbeatCodec {
    private val gson = Gson()

    fun encode(payload: QueuedHeartbeat): String = gson.toJson(payload)

    fun decode(value: String): QueuedHeartbeat? =
        runCatching { gson.fromJson(value, QueuedHeartbeat::class.java) }
            .getOrNull()
            ?.takeIf { it.request.deviceId.isNotBlank() }
}

class HeartbeatTaskProcessor(private val api: PatrolRestApi) {
    suspend fun process(task: BackgroundTask): Boolean {
        if (task.type != BackgroundTaskType.Heartbeat) return false
        val payload = QueuedHeartbeatCodec.decode(task.payloadId) ?: return false
        return runCatching { api.heartbeat(payload.request) }.isSuccess
    }
}
