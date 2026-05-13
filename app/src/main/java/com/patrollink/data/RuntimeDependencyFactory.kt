package com.patrollink.data

import android.content.Context
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
    val versionInstaller: VersionInstaller,
    val tokenStore: RuntimeTokenStore,
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
        val deviceControlGateway = ServiceFactory.createDeviceControlGateway(appContext, config, uteBridge)
        return RuntimeDependencies(
            coordinator = coordinator,
            deviceControlGateway = deviceControlGateway,
            secureStore = secureStore,
            settingsStore = UiSettingsStore(appContext),
            locationGateway = ServiceFactory.createLocationGateway(appContext, fallbackState),
            sosEvidenceRecorder = ServiceFactory.createSosEvidenceRecorder(appContext),
            emergencyContactGateway = ServiceFactory.createEmergencyContactGateway(),
            notificationGateway = ServiceFactory.createNotificationGateway(appContext),
            versionInstaller = ServiceFactory.createVersionInstaller(appContext),
            tokenStore = tokenStore,
            config = config
        )
    }
}
