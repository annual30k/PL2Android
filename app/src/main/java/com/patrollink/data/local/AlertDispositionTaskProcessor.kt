package com.patrollink.data.local

import com.google.gson.Gson
import com.patrollink.data.remote.AlertCloseRequestDto
import com.patrollink.data.remote.PatrolRestApi
import com.patrollink.data.remote.UploadAttachmentDto
import com.patrollink.domain.BackgroundTask
import com.patrollink.domain.BackgroundTaskType

data class QueuedAlertDisposition(
    val alertId: String,
    val result: String,
    val note: String,
    val operatorId: String,
    val attachments: List<UploadAttachmentDto> = emptyList()
)

object QueuedAlertDispositionCodec {
    private val gson = Gson()

    fun encode(payload: QueuedAlertDisposition): String = gson.toJson(payload)

    fun decode(value: String): QueuedAlertDisposition? =
        runCatching { gson.fromJson(value, QueuedAlertDisposition::class.java) }
            .getOrNull()
            ?.takeIf { it.alertId.isNotBlank() && it.result.isNotBlank() }
}

class AlertDispositionTaskProcessor(
    private val api: PatrolRestApi
) {
    suspend fun process(task: BackgroundTask): Boolean {
        if (task.type != BackgroundTaskType.SyncAlertDisposition) return false
        val payload = QueuedAlertDispositionCodec.decode(task.payloadId) ?: return false
        return runCatching {
            api.acknowledgeAlert(payload.alertId)
            api.closeAlert(
                payload.alertId,
                AlertCloseRequestDto(
                    result = payload.result,
                    note = payload.note,
                    operatorId = payload.operatorId,
                    attachments = payload.attachments
                )
            )
        }.isSuccess
    }
}
