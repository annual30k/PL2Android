package com.patrollink.data

import android.content.Context
import com.patrollink.data.edge.CerebellumApi
import com.patrollink.data.local.AndroidKeystoreSecureStore
import com.patrollink.data.local.LocalMediaCacheCleaner
import com.patrollink.data.local.WorkManagerBackgroundTaskGateway
import com.patrollink.data.local.UiSettingsStore
import com.patrollink.data.remote.OkHttpPatrolRestApi
import com.patrollink.data.remote.PatrolRestApi
import com.patrollink.data.ute.UteSdkBridge
import com.patrollink.domain.PatrolCoordinator
import com.patrollink.domain.OfflineSyncEngine
import com.patrollink.domain.DeviceControlGateway
import com.patrollink.domain.SecureStore
import com.patrollink.domain.LocationGateway
import com.patrollink.domain.EmergencyContactGateway
import com.patrollink.domain.PatrolNotificationGateway
import com.patrollink.domain.SosEvidenceRecorder
import com.patrollink.domain.VersionGateway
import com.patrollink.domain.VersionInstaller
import com.patrollink.domain.EmptyAppState
import com.patrollink.domain.FirmwareGateway

data class RuntimeDependencies(
    val coordinator: PatrolCoordinator,
    val deviceControlGateway: DeviceControlGateway,
    val secureStore: SecureStore,
    val settingsStore: UiSettingsStore,
    val locationGateway: LocationGateway,
    val sosEvidenceRecorder: SosEvidenceRecorder,
    val emergencyContactGateway: EmergencyContactGateway,
    val notificationGateway: PatrolNotificationGateway,
    val versionGateway: VersionGateway,
    val firmwareGateway: FirmwareGateway,
    val versionInstaller: VersionInstaller,
    val cerebellumApi: CerebellumApi?,
    val patrolRestApi: PatrolRestApi?,
    val tokenStore: RuntimeTokenStore,
    val configStore: RuntimeConfigStore,
    val offlineSyncEngine: OfflineSyncEngine,
    val localMediaCacheCleaner: LocalMediaCacheCleaner,
    val config: RuntimeConfig
)

object RuntimeDependencyFactory {
    fun create(context: Context): RuntimeDependencies {
        val appContext = context.applicationContext
        val config = RuntimeConfigStore(appContext).read()
        val tokenStore = RuntimeTokenStore(appContext)
        val secureStore = AndroidKeystoreSecureStore(appContext)
        val emptyState = EmptyAppState.create()
        val uteBridge = if (config.useRealBle) UteSdkBridge(appContext) else null
        val coordinator = ServiceFactory.createRuntimeCoordinator(
            context = appContext,
            config = config,
            tokenProvider = tokenStore::token,
            operatorIdProvider = { emptyState.user.badgeNo },
            pairingAccountIdProvider = tokenStore::pairingAccountId,
            fallbackState = emptyState,
            sharedUteBridge = uteBridge
        )
        val deviceControlGateway = ServiceFactory.createDeviceControlGateway(
            context = appContext,
            config = config,
            sharedUteBridge = uteBridge,
            tokenProvider = tokenStore::token,
            deviceIdProvider = { emptyState.device.id },
            pairingAccountIdProvider = tokenStore::pairingAccountId
        )
        return RuntimeDependencies(
            coordinator = coordinator,
            deviceControlGateway = deviceControlGateway,
            secureStore = secureStore,
            settingsStore = UiSettingsStore(appContext),
            locationGateway = ServiceFactory.createLocationGateway(appContext, emptyState),
            sosEvidenceRecorder = ServiceFactory.createSosEvidenceRecorder(appContext),
            emergencyContactGateway = ServiceFactory.createEmergencyContactGateway(),
            notificationGateway = ServiceFactory.createNotificationGateway(appContext),
            versionGateway = ServiceFactory.createVersionGateway(config, tokenStore::token),
            firmwareGateway = ServiceFactory.createFirmwareGateway(appContext, config, uteBridge, tokenStore::token) { emptyState.user.badgeNo },
            versionInstaller = ServiceFactory.createVersionInstaller(appContext),
            cerebellumApi = ServiceFactory.createCerebellumApi(config),
            patrolRestApi = config.restBaseUrl.takeIf { it.isNotBlank() }?.let { OkHttpPatrolRestApi(baseUrl = it, tokenProvider = tokenStore::token) },
            tokenStore = tokenStore,
            configStore = RuntimeConfigStore(appContext),
            offlineSyncEngine = OfflineSyncEngine(WorkManagerBackgroundTaskGateway(appContext)),
            localMediaCacheCleaner = LocalMediaCacheCleaner(appContext),
            config = config
        )
    }
}
