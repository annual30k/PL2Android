package com.patrollink.data.local

import com.patrollink.data.remote.PatrolRestApi
import com.patrollink.domain.BackgroundTask
import com.patrollink.domain.BackgroundTaskType

class DeviceUnbindTaskProcessor(private val unbind: suspend (String) -> Unit) {
    constructor(api: PatrolRestApi) : this({ deviceId -> api.unbindDevice(deviceId) })

    suspend fun process(task: BackgroundTask): Boolean {
        if (task.type != BackgroundTaskType.SyncDeviceUnbind || task.payloadId.isBlank()) return false
        return runCatching { unbind(task.payloadId) }.isSuccess
    }
}
