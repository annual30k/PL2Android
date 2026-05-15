package com.patrollink.data

import android.content.Context
import com.patrollink.data.edge.CerebellumApi
import com.patrollink.data.local.AndroidKeystoreSecureStore
import com.patrollink.data.local.UiSettingsStore
import com.patrollink.data.ute.UteSdkBridge
import com.patrollink.domain.PatrolCoordinator
import com.patrollink.domain.DeviceControlGateway
import com.patrollink.domain.SecureStore
import com.patrollink.domain.LocationGateway
import com.patrollink.domain.EmergencyContactGateway
import com.patrollink.domain.PatrolNotificationGateway
import com.patrollink.domain.SosEvidenceRecorder
import com.patrollink.domain.VersionGateway
import com.patrollink.domain.VersionInstaller

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
    val versionInstaller: VersionInstaller,
    val cerebellumApi: CerebellumApi?,
    val tokenStore: RuntimeTokenStore,
    val configStore: RuntimeConfigStore,
    val config: RuntimeConfig
)

object RuntimeDependencyFactory {
    fun create(context: Context): RuntimeDependencies {
        val appContext = context.applicationContext
        val config = RuntimeConfigStore(appContext).read()
        val tokenStore = RuntimeTokenStore()
        val secureStore = AndroidKeystoreSecureStore(appContext)
        val fallbackState = MockPatrolRepository().initialState()
        val uteBridge = if (config.useRealBle) UteSdkBridge(appContext) else null
        val coordinator = ServiceFactory.createRuntimeCoordinator(
            context = appContext,
            config = config,
            tokenProvider = tokenStore::token,
            operatorIdProvider = { fallbackState.user.badgeNo },
            fallbackState = fallbackState,
            sharedUteBridge = uteBridge
        )
        val deviceControlGateway = ServiceFactory.createDeviceControlGateway(
            context = appContext,
            config = config,
            sharedUteBridge = uteBridge,
            tokenProvider = tokenStore::token,
            deviceIdProvider = { fallbackState.device.id }
        )
        return RuntimeDependencies(
            coordinator = coordinator,
            deviceControlGateway = deviceControlGateway,
            secureStore = secureStore,
            settingsStore = UiSettingsStore(appContext),
            locationGateway = ServiceFactory.createLocationGateway(appContext, fallbackState),
            sosEvidenceRecorder = ServiceFactory.createSosEvidenceRecorder(appContext),
            emergencyContactGateway = ServiceFactory.createEmergencyContactGateway(),
            notificationGateway = ServiceFactory.createNotificationGateway(appContext),
            versionGateway = ServiceFactory.createVersionGateway(config, tokenStore::token),
            versionInstaller = ServiceFactory.createVersionInstaller(appContext),
            cerebellumApi = ServiceFactory.createCerebellumApi(config),
            tokenStore = tokenStore,
            configStore = RuntimeConfigStore(appContext),
            config = config
        )
    }
}
