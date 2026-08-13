package com.patrollink.data.local

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.patrollink.data.RestMediaGateway
import com.patrollink.data.RuntimeConfigStore
import com.patrollink.data.remote.OkHttpPatrolRestApi
import com.patrollink.data.remote.PatrolRestApi
import com.patrollink.data.remote.toDomain
import com.patrollink.domain.BackgroundTaskType
import com.patrollink.domain.BackgroundTask
import com.patrollink.domain.BackgroundTaskGateway
import com.patrollink.domain.BackgroundTaskReceipt
import java.util.concurrent.TimeUnit

class WorkManagerBackgroundTaskGateway(
    context: Context,
    private val taskGateway: BackgroundTaskGateway = RoomBackgroundTaskGateway(
        PatrolDatabase.get(context).offlineTaskDao()
    )
) : BackgroundTaskGateway {
    private val appContext = context.applicationContext

    override suspend fun enqueue(task: BackgroundTask): BackgroundTaskReceipt {
        val receipt = taskGateway.enqueue(task)
        val request = OneTimeWorkRequestBuilder<OfflineCompensationWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // 新任务到达时必须排在当前补偿任务之后，KEEP 会在 Worker 正运行时直接丢掉调度请求，
        // 造成数据库里有待补传任务却再也没有 Worker 被唤醒。
        WorkManager.getInstance(appContext).enqueueUniqueWork("patrol-offline-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        return receipt
    }

    override suspend fun pending(): List<BackgroundTaskReceipt> = taskGateway.pending()

    override suspend fun complete(taskId: String): Boolean = taskGateway.complete(taskId)
}

class OfflineCompensationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val database = PatrolDatabase.get(applicationContext)
        val gateway = RoomBackgroundTaskGateway(database.offlineTaskDao())
        val api = createAuthenticatedApi()
        val uploader = createEvidenceUploadProcessor(database, api)
        val alertProcessor = api?.let(::AlertDispositionTaskProcessor)
        val commandAckProcessor = api?.let(::DeviceCommandAckTaskProcessor)
        val heartbeatProcessor = api?.let(::HeartbeatTaskProcessor)
        val messageReadProcessor = api?.let(::MessageReadTaskProcessor)
        val firmwareUpgradeProcessor = api?.let(::FirmwareUpgradeTaskProcessor)
        val sosSyncProcessor = api?.let(::SosSyncTaskProcessor)
        val sosEvidenceProcessor = api?.let(::SosEvidenceUploadTaskProcessor)
        val deviceUnbindProcessor = api?.let(::DeviceUnbindTaskProcessor)
        val pending = gateway.pending()
        var hasIncompleteTask = false
        val blockedSosIds = mutableSetOf<String>()
        pending.forEach { receipt ->
            val completed = when (receipt.task.type) {
                BackgroundTaskType.UploadEvidence -> uploader?.process(receipt.task) == true
                BackgroundTaskType.UploadSosEvidence -> sosEvidenceProcessor?.process(receipt.task) == true
                BackgroundTaskType.SyncAlertDisposition -> alertProcessor?.process(receipt.task) == true
                BackgroundTaskType.SyncDeviceCommandAck -> commandAckProcessor?.process(receipt.task) == true
                BackgroundTaskType.Heartbeat -> heartbeatProcessor?.process(receipt.task) == true
                BackgroundTaskType.SyncMessageRead -> messageReadProcessor?.process(receipt.task) == true
                BackgroundTaskType.SyncFirmwareUpgrade -> firmwareUpgradeProcessor?.process(receipt.task) == true
                BackgroundTaskType.SyncDeviceUnbind -> deviceUnbindProcessor?.process(receipt.task) == true
                BackgroundTaskType.SyncSosState -> {
                    val sos = QueuedSosSyncCodec.decode(receipt.task.payloadId)
                    if (sos == null || sos.clientSosId in blockedSosIds) {
                        false
                    } else {
                        val synced = sosSyncProcessor?.process(receipt.task) == true
                        if (!synced) blockedSosIds += sos.clientSosId
                        synced
                    }
                }
                // 未实现的任务不能再被静默标记成功，保留队列并重试。
                else -> false
            }
            if (completed) gateway.complete(receipt.task.id)
            else hasIncompleteTask = true
        }
        if (hasIncompleteTask) return Result.retry()
        return Result.success()
    }

    private fun createEvidenceUploadProcessor(database: PatrolDatabase, api: PatrolRestApi?): EvidenceUploadTaskProcessor? {
        api ?: return null
        return EvidenceUploadTaskProcessor(
            localMediaStore = RoomLocalMediaStore(RoomMediaIndex(database.mediaFileDao())),
            mediaGateway = RestMediaGateway(api)
        )
    }


    private suspend fun createAuthenticatedApi(): OkHttpPatrolRestApi? {
        val config = RuntimeConfigStore(applicationContext).read()
        if (config.restBaseUrl.isBlank()) return null
        val secureStore = AndroidKeystoreSecureStore(applicationContext)
        var session = secureStore.readSession() ?: return null
        var api = OkHttpPatrolRestApi(
            baseUrl = config.restBaseUrl,
            tokenProvider = { session.accessToken }
        )
        if (runCatching { api.currentUser() }.isSuccess) return api
        session = runCatching { api.refresh(session.refreshToken).data.toDomain() }.getOrNull() ?: return null
        secureStore.saveSession(session)
        api = OkHttpPatrolRestApi(config.restBaseUrl, tokenProvider = { session.accessToken })
        return api
    }
}
