package com.patrollink.data

import android.content.Context
import com.patrollink.BuildConfig
import com.patrollink.domain.AuthSession

data class RuntimeConfig(
    val restBaseUrl: String,
    val webSocketUrl: String,
    val wifiFileBaseUrl: String,
    val useRealBle: Boolean
) {
    val hasRest: Boolean get() = restBaseUrl.isNotBlank()
    val hasWebSocket: Boolean get() = webSocketUrl.isNotBlank()
    val hasWifiFileService: Boolean get() = wifiFileBaseUrl.isNotBlank()
}

class RuntimeConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("patrol_runtime_config", Context.MODE_PRIVATE)

    fun read(): RuntimeConfig = RuntimeConfig(
        restBaseUrl = prefs.getString(KEY_REST_BASE_URL, null).orBuildConfig(BuildConfig.REST_BASE_URL),
        webSocketUrl = prefs.getString(KEY_WEBSOCKET_URL, null).orBuildConfig(BuildConfig.WEBSOCKET_URL),
        wifiFileBaseUrl = prefs.getString(KEY_WIFI_FILE_BASE_URL, null).orBuildConfig(BuildConfig.WIFI_FILE_BASE_URL),
        useRealBle = prefs.getBoolean(KEY_USE_REAL_BLE, BuildConfig.USE_REAL_BLE)
    )

    private fun String?.orBuildConfig(buildConfigValue: String): String =
        this?.trim().takeUnless { it.isNullOrBlank() } ?: buildConfigValue.trim()

    private companion object {
        const val KEY_REST_BASE_URL = "rest_base_url"
        const val KEY_WEBSOCKET_URL = "websocket_url"
        const val KEY_WIFI_FILE_BASE_URL = "wifi_file_base_url"
        const val KEY_USE_REAL_BLE = "use_real_ble"
    }
}

class RuntimeTokenStore {
    @Volatile
    private var accessToken: String? = null

    fun token(): String? = accessToken

    fun update(session: AuthSession?) {
        accessToken = session?.accessToken
    }
}
