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
        val gateway = RoomBackgroundTaskGateway(PatrolDatabase.get(applicationContext).offlineTaskDao())
        val pending = gateway.pending()
        pending.forEach { gateway.complete(it.task.id) }
        return Result.success()
    }
}
