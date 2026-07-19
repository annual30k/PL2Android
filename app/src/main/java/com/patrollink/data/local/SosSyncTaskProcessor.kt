package com.patrollink.data.local

import com.google.gson.Gson
import com.patrollink.data.remote.GpsLocationDto
import com.patrollink.data.remote.PatrolRestApi
import com.patrollink.domain.BackgroundTask
import com.patrollink.domain.BackgroundTaskType

data class QueuedSosSync(
    val clientSosId: String,
    val action: String,
    val location: GpsLocationDto? = null
)

object QueuedSosSyncCodec {
    private val gson = Gson()

    fun encode(payload: QueuedSosSync): String = gson.toJson(payload)

    fun decode(value: String): QueuedSosSync? =
        runCatching { gson.fromJson(value, QueuedSosSync::class.java) }
            .getOrNull()
            ?.takeIf {
                it.clientSosId.startsWith("SOS-APP-") &&
                    (it.action == "ACTIVATE" || it.action == "CANCEL") &&
                    (it.action != "ACTIVATE" || it.location != null)
            }
}

class SosSyncTaskProcessor(
    private val sync: suspend (QueuedSosSync) -> Unit
) {
    constructor(api: PatrolRestApi) : this(sync = { payload ->
        when (payload.action) {
            "ACTIVATE" -> api.activateSos(requireNotNull(payload.location).copy(clientEventId = payload.clientSosId))
            "CANCEL" -> api.cancelSos()
            else -> error("unsupported SOS action")
        }
        Unit
    })

    suspend fun process(task: BackgroundTask): Boolean {
        if (task.type != BackgroundTaskType.SyncSosState) return false
        val payload = QueuedSosSyncCodec.decode(task.payloadId) ?: return false
        return runCatching { sync(payload) }.isSuccess
    }
}
