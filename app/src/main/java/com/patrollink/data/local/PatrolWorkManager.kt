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
        WorkManager.getInstance(appContext).enqueueUniqueWork("patrol-offline-sync", ExistingWorkPolicy.KEEP, request)
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
        val uploader = createEvidenceUploadProcessor(database)
        val pending = gateway.pending()
        var hasIncompleteTask = false
        pending.forEach { receipt ->
            val completed = when (receipt.task.type) {
                BackgroundTaskType.UploadEvidence -> uploader?.process(receipt.task) == true
                else -> true
            }
            if (completed) gateway.complete(receipt.task.id)
            else hasIncompleteTask = true
        }
        if (hasIncompleteTask) return Result.retry()
        return Result.success()
    }

    private suspend fun createEvidenceUploadProcessor(database: PatrolDatabase): EvidenceUploadTaskProcessor? {
        val config = RuntimeConfigStore(applicationContext).read()
        if (config.restBaseUrl.isBlank()) return null
        val secureStore = AndroidKeystoreSecureStore(applicationContext)
        val accessToken = secureStore.readSession()?.accessToken
        val api = OkHttpPatrolRestApi(
            baseUrl = config.restBaseUrl,
            tokenProvider = { accessToken }
        )
        return EvidenceUploadTaskProcessor(
            localMediaStore = RoomLocalMediaStore(RoomMediaIndex(database.mediaFileDao())),
            mediaGateway = RestMediaGateway(api)
        )
    }
}
