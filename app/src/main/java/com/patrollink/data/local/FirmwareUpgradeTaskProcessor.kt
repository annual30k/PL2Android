package com.patrollink.data.local

import com.google.gson.Gson
import com.patrollink.data.remote.FirmwareUpgradeTaskUpdateDto
import com.patrollink.data.remote.PatrolRestApi
import com.patrollink.domain.BackgroundTask
import com.patrollink.domain.BackgroundTaskType
import com.patrollink.domain.FirmwareUpgradeState

data class QueuedFirmwareUpgradeStatus(
    val taskId: String,
    val request: FirmwareUpgradeTaskUpdateDto
)

object QueuedFirmwareUpgradeStatusCodec {
    private val gson = Gson()

    fun encode(taskId: String, state: FirmwareUpgradeState): String = gson.toJson(
        QueuedFirmwareUpgradeStatus(
            taskId,
            FirmwareUpgradeTaskUpdateDto(state.status, state.progress, state.errorCode, state.errorMessage)
        )
    )

    fun decode(value: String): QueuedFirmwareUpgradeStatus? =
        runCatching { gson.fromJson(value, QueuedFirmwareUpgradeStatus::class.java) }
            .getOrNull()
            ?.takeIf { it.taskId.isNotBlank() && it.request.status.isNotBlank() }
}

class FirmwareUpgradeTaskProcessor(
    private val update: suspend (String, FirmwareUpgradeTaskUpdateDto) -> Unit
) {
    constructor(api: PatrolRestApi) : this({ taskId, request -> api.updateFirmwareUpgradeTask(taskId, request) })

    suspend fun process(task: BackgroundTask): Boolean {
        if (task.type != BackgroundTaskType.SyncFirmwareUpgrade) return false
        val payload = QueuedFirmwareUpgradeStatusCodec.decode(task.payloadId) ?: return false
        return runCatching { update(payload.taskId, payload.request) }.isSuccess
    }
}
