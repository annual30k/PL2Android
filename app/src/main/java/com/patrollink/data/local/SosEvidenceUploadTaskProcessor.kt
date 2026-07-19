package com.patrollink.data.local

import com.google.gson.Gson
import com.patrollink.data.remote.PatrolRestApi
import com.patrollink.domain.BackgroundTask
import com.patrollink.domain.BackgroundTaskType
import java.io.File

data class QueuedSosEvidence(
    val clientSosId: String,
    val filePath: String
)

object QueuedSosEvidenceCodec {
    private val gson = Gson()

    fun encode(payload: QueuedSosEvidence): String = gson.toJson(payload)

    fun decode(value: String): QueuedSosEvidence? =
        runCatching { gson.fromJson(value, QueuedSosEvidence::class.java) }
            .getOrNull()
            ?.takeIf { it.clientSosId.startsWith("SOS-APP-") && it.filePath.isNotBlank() }
}

class SosEvidenceUploadTaskProcessor(
    private val upload: suspend (File, String) -> Unit
) {
    constructor(api: PatrolRestApi) : this(upload = { file, sosId ->
        api.uploadMediaResumable(file, storageSide = "PHONE", bizType = "SOS_AUDIO", bizId = sosId)
        Unit
    })

    suspend fun process(task: BackgroundTask): Boolean {
        if (task.type != BackgroundTaskType.UploadSosEvidence) return false
        val payload = QueuedSosEvidenceCodec.decode(task.payloadId) ?: return false
        val file = File(payload.filePath).takeIf { it.isFile && it.length() > 0L } ?: return false
        return runCatching { upload(file, payload.clientSosId) }.isSuccess
    }
}
