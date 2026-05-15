package com.patrollink.data

import android.content.Context
import com.patrollink.BuildConfig
import com.patrollink.domain.AuthSession

data class RuntimeConfig(
    val restBaseUrl: String,
    val webSocketUrl: String,
    val wifiFileBaseUrl: String,
    val cerebellumBaseUrl: String,
    val cerebellumApiKey: String,
    val bleServiceUuid: String,
    val bleCommandUuid: String,
    val bleStatusUuid: String,
    val streamRelayUrl: String,
    val useRealBle: Boolean
) {
    val hasRest: Boolean get() = restBaseUrl.isNotBlank()
    val hasWebSocket: Boolean get() = webSocketUrl.isNotBlank()
    val hasWifiFileService: Boolean get() = wifiFileBaseUrl.isNotBlank()
    val hasCerebellum: Boolean get() = cerebellumBaseUrl.isNotBlank()
}

data class CerebellumRuntimeSettings(
    val baseUrl: String,
    val apiKey: String
)

class RuntimeConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("patrol_runtime_config", Context.MODE_PRIVATE)

    fun read(): RuntimeConfig = RuntimeConfig(
        restBaseUrl = prefs.getString(KEY_REST_BASE_URL, null).orBuildConfig(BuildConfig.REST_BASE_URL),
        webSocketUrl = prefs.getString(KEY_WEBSOCKET_URL, null).orBuildConfig(BuildConfig.WEBSOCKET_URL),
        wifiFileBaseUrl = prefs.getString(KEY_WIFI_FILE_BASE_URL, null).orBuildConfig(BuildConfig.WIFI_FILE_BASE_URL),
        cerebellumBaseUrl = prefs.getString(KEY_CEREBELLUM_BASE_URL, null).orBuildConfig(BuildConfig.CEREBELLUM_BASE_URL),
        cerebellumApiKey = prefs.getString(KEY_CEREBELLUM_API_KEY, null).orBuildConfig(BuildConfig.CEREBELLUM_API_KEY),
        bleServiceUuid = prefs.getString(KEY_BLE_SERVICE_UUID, null).orBuildConfig(BuildConfig.BLE_SERVICE_UUID),
        bleCommandUuid = prefs.getString(KEY_BLE_COMMAND_UUID, null).orBuildConfig(BuildConfig.BLE_COMMAND_UUID),
        bleStatusUuid = prefs.getString(KEY_BLE_STATUS_UUID, null).orBuildConfig(BuildConfig.BLE_STATUS_UUID),
        streamRelayUrl = prefs.getString(KEY_STREAM_RELAY_URL, null).orBuildConfig(BuildConfig.STREAM_RELAY_URL),
        useRealBle = prefs.getBoolean(KEY_USE_REAL_BLE, BuildConfig.USE_REAL_BLE)
    )

    fun readCerebellumSettings(): CerebellumRuntimeSettings =
        read().let { config ->
            CerebellumRuntimeSettings(
                baseUrl = config.cerebellumBaseUrl,
                apiKey = config.cerebellumApiKey
            )
        }

    fun saveCerebellumSettings(baseUrl: String, apiKey: String): CerebellumRuntimeSettings {
        prefs.edit()
            .putString(KEY_CEREBELLUM_BASE_URL, baseUrl.trim())
            .putString(KEY_CEREBELLUM_API_KEY, apiKey.trim())
            .apply()
        return readCerebellumSettings()
    }

    private fun String?.orBuildConfig(buildConfigValue: String): String =
        this?.trim().takeUnless { it.isNullOrBlank() } ?: buildConfigValue.trim()

    private companion object {
        const val KEY_REST_BASE_URL = "rest_base_url"
        const val KEY_WEBSOCKET_URL = "websocket_url"
        const val KEY_WIFI_FILE_BASE_URL = "wifi_file_base_url"
        const val KEY_CEREBELLUM_BASE_URL = "cerebellum_base_url"
        const val KEY_CEREBELLUM_API_KEY = "cerebellum_api_key"
        const val KEY_BLE_SERVICE_UUID = "ble_service_uuid"
        const val KEY_BLE_COMMAND_UUID = "ble_command_uuid"
        const val KEY_BLE_STATUS_UUID = "ble_status_uuid"
        const val KEY_STREAM_RELAY_URL = "stream_relay_url"
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
