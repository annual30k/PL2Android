package com.patrollink.data

import android.content.Context
import com.patrollink.data.local.AndroidKeystoreSecureStore
import com.patrollink.data.local.UiSettingsStore
import com.patrollink.domain.PatrolCoordinator
import com.patrollink.domain.SecureStore

data class RuntimeDependencies(
    val coordinator: PatrolCoordinator,
    val secureStore: SecureStore,
    val settingsStore: UiSettingsStore,
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
        val coordinator = ServiceFactory.createRuntimeCoordinator(
            context = appContext,
            config = config,
            tokenProvider = tokenStore::token,
            operatorIdProvider = { fallbackState.user.badgeNo },
            fallbackState = fallbackState
        )
        return RuntimeDependencies(
            coordinator = coordinator,
            secureStore = secureStore,
            settingsStore = UiSettingsStore(appContext),
            tokenStore = tokenStore,
            config = config
        )
    }
}
