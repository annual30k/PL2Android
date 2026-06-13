package com.patrollink.domain

class OfflineSyncEngine(
    private val backgroundTaskGateway: BackgroundTaskGateway
) {
    suspend fun enqueueAlertDisposition(alertId: String, createdAt: Long): BackgroundTaskReceipt {
        return backgroundTaskGateway.enqueue(
            BackgroundTask(
                id = "sync-alert-$alertId-$createdAt",
                type = BackgroundTaskType.SyncAlertDisposition,
                payloadId = alertId,
                createdAt = createdAt
            )
        )
    }

    suspend fun enqueueEvidenceUpload(fileId: String, createdAt: Long): BackgroundTaskReceipt {
        return backgroundTaskGateway.enqueue(
            BackgroundTask(
                id = "upload-evidence-$fileId",
                type = BackgroundTaskType.UploadEvidence,
                payloadId = fileId,
                createdAt = createdAt
            )
        )
    }

    suspend fun drain(processor: suspend (BackgroundTask) -> Boolean): Int {
        var completed = 0
        for (receipt in backgroundTaskGateway.pending()) {
            if (processor(receipt.task) && backgroundTaskGateway.complete(receipt.task.id)) {
                completed += 1
            }
        }
        return completed
    }
}
