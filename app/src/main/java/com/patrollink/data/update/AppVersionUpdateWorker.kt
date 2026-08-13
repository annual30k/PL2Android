package com.patrollink.data.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.patrollink.BuildConfig
import com.patrollink.data.RuntimeConfigStore
import com.patrollink.data.local.AndroidKeystoreSecureStore
import com.patrollink.data.notification.AndroidPatrolNotificationGateway
import com.patrollink.data.remote.OkHttpPatrolRestApi
import com.patrollink.data.remote.toDomain
import java.util.concurrent.TimeUnit

class AppVersionUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val config = RuntimeConfigStore(applicationContext).read()
        if (config.restBaseUrl.isBlank()) return Result.success()
        val secureStore = AndroidKeystoreSecureStore(applicationContext)
        var session = secureStore.readSession() ?: return Result.success()
        var api = OkHttpPatrolRestApi(config.restBaseUrl, tokenProvider = { session.accessToken })
        var check = runCatching { api.checkVersion(BuildConfig.VERSION_CODE).data }.getOrNull()
        if (check == null) {
            session = runCatching { api.refresh(session.refreshToken).data.toDomain() }.getOrNull()
                ?: return Result.retry()
            secureStore.saveSession(session)
            api = OkHttpPatrolRestApi(config.restBaseUrl, tokenProvider = { session.accessToken })
            check = runCatching { api.checkVersion(BuildConfig.VERSION_CODE).data }.getOrNull()
                ?: return Result.retry()
        }
        if (check.latestVersionCode <= BuildConfig.VERSION_CODE) return Result.success()

        val preferences = applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        val notifiedVersionCode = preferences.getInt(NotifiedVersionCodeKey, 0)
        if (notifiedVersionCode < check.latestVersionCode) {
            AndroidPatrolNotificationGateway(applicationContext)
                .notifyVersionUpdate(check.latestVersionName, check.forceUpdate)
            preferences.edit().putInt(NotifiedVersionCodeKey, check.latestVersionCode).apply()
        }
        return Result.success()
    }

    private companion object {
        const val PreferencesName = "patrollink_version_updates"
        const val NotifiedVersionCodeKey = "notified_version_code"
    }
}

object AppVersionUpdateScheduler {
    private const val WorkName = "patrollink-version-check"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<AppVersionUpdateWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
