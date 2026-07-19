package com.patrollink.domain

class OfflineSyncEngine(
    private val backgroundTaskGateway: BackgroundTaskGateway
) {
    suspend fun enqueueAlertDisposition(alertId: String, payloadJson: String, createdAt: Long): BackgroundTaskReceipt {
        return backgroundTaskGateway.enqueue(
            BackgroundTask(
                id = "sync-alert-$alertId-$createdAt",
                type = BackgroundTaskType.SyncAlertDisposition,
                payloadId = payloadJson,
                createdAt = createdAt
            )
        )
    }

    suspend fun enqueueAlertDisposition(alertId: String, createdAt: Long): BackgroundTaskReceipt =
        enqueueAlertDisposition(alertId, alertId, createdAt)

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

    suspend fun enqueueHeartbeat(deviceId: String, payloadJson: String, createdAt: Long): BackgroundTaskReceipt {
        return backgroundTaskGateway.enqueue(
            BackgroundTask(
                id = "sync-heartbeat-$deviceId",
                type = BackgroundTaskType.Heartbeat,
                payloadId = payloadJson,
                createdAt = createdAt
            )
        )
    }

    suspend fun enqueueDeviceCommandAck(commandId: String, payloadJson: String, createdAt: Long): BackgroundTaskReceipt {
        return backgroundTaskGateway.enqueue(
            BackgroundTask(
                id = "sync-command-ack-$commandId",
                type = BackgroundTaskType.SyncDeviceCommandAck,
                payloadId = payloadJson,
                createdAt = createdAt
            )
        )
    }

    suspend fun enqueueMessageRead(messageId: String, createdAt: Long): BackgroundTaskReceipt {
        require(messageId.isNotBlank()) { "messageId required" }
        return backgroundTaskGateway.enqueue(
            BackgroundTask(
                id = "sync-message-read-$messageId",
                type = BackgroundTaskType.SyncMessageRead,
                payloadId = messageId,
                createdAt = createdAt
            )
        )
    }

    suspend fun enqueueDeviceUnbind(deviceId: String, createdAt: Long): BackgroundTaskReceipt {
        require(deviceId.isNotBlank()) { "deviceId required" }
        return backgroundTaskGateway.enqueue(
            BackgroundTask(
                id = "sync-device-unbind-$deviceId",
                type = BackgroundTaskType.SyncDeviceUnbind,
                payloadId = deviceId,
                createdAt = createdAt
            )
        )
    }

    suspend fun enqueueSosState(clientSosId: String, payloadJson: String, action: String, createdAt: Long): BackgroundTaskReceipt {
        require(clientSosId.isNotBlank()) { "clientSosId required" }
        require(action == "ACTIVATE" || action == "CANCEL") { "unsupported SOS action" }
        return backgroundTaskGateway.enqueue(
            BackgroundTask(
                id = "sync-sos-${clientSosId.removePrefix("SOS-APP-")}-$action",
                type = BackgroundTaskType.SyncSosState,
                payloadId = payloadJson,
                createdAt = createdAt
            )
        )
    }

    suspend fun enqueueSosEvidence(clientSosId: String, payloadJson: String, createdAt: Long): BackgroundTaskReceipt {
        require(clientSosId.isNotBlank()) { "clientSosId required" }
        return backgroundTaskGateway.enqueue(
            BackgroundTask(
                id = "upload-sos-evidence-${clientSosId.removePrefix("SOS-APP-")}",
                type = BackgroundTaskType.UploadSosEvidence,
                payloadId = payloadJson,
                createdAt = createdAt
            )
        )
    }

    suspend fun pendingMessageReadIds(): Set<String> =
        backgroundTaskGateway.pending()
            .asSequence()
            .map { it.task }
            .filter { it.type == BackgroundTaskType.SyncMessageRead }
            .map { it.payloadId }
            .filter { it.isNotBlank() }
            .toSet()

    suspend fun hasPendingDeviceCommandAck(commandId: String): Boolean =
        backgroundTaskGateway.pending().any {
            it.task.type == BackgroundTaskType.SyncDeviceCommandAck &&
                it.task.id == "sync-command-ack-$commandId"
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
