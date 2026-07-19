package com.patrollink.data.local

import com.google.gson.Gson
import com.patrollink.data.remote.DeviceCommandAckRequestDto
import com.patrollink.data.remote.PatrolRestApi
import com.patrollink.domain.BackgroundTask
import com.patrollink.domain.BackgroundTaskType

data class QueuedDeviceCommandAck(
    val commandId: String,
    val request: DeviceCommandAckRequestDto
)

object QueuedDeviceCommandAckCodec {
    private val gson = Gson()

    fun encode(payload: QueuedDeviceCommandAck): String = gson.toJson(payload)

    fun decode(value: String): QueuedDeviceCommandAck? =
        runCatching { gson.fromJson(value, QueuedDeviceCommandAck::class.java) }
            .getOrNull()
            ?.takeIf { it.commandId.isNotBlank() && it.request.deviceId.isNotBlank() }
}

class DeviceCommandAckTaskProcessor(
    private val api: PatrolRestApi
) {
    suspend fun process(task: BackgroundTask): Boolean {
        if (task.type != BackgroundTaskType.SyncDeviceCommandAck) return false
        val payload = QueuedDeviceCommandAckCodec.decode(task.payloadId) ?: return false
        return runCatching {
            api.acknowledgeDeviceCommand(payload.commandId, payload.request)
        }.isSuccess
    }
}
