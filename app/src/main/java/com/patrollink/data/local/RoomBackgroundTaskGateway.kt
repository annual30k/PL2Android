package com.patrollink.data.local

import com.patrollink.domain.BackgroundTask
import com.patrollink.domain.BackgroundTaskGateway
import com.patrollink.domain.BackgroundTaskReceipt

class RoomBackgroundTaskGateway(
    private val dao: OfflineTaskDao
) : BackgroundTaskGateway {
    override suspend fun enqueue(task: BackgroundTask): BackgroundTaskReceipt {
        val entity = OfflineTaskEntity.from(task)
        dao.upsert(entity)
        return entity.toReceipt()
    }

    override suspend fun pending(): List<BackgroundTaskReceipt> =
        dao.pending().map { it.toReceipt() }

    override suspend fun complete(taskId: String): Boolean =
        dao.delete(taskId) > 0
}
