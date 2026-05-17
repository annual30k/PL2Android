package com.patrollink.data

import android.content.Context
import com.google.gson.Gson
import com.patrollink.BuildConfig
import com.patrollink.domain.AuthSession
import java.security.MessageDigest

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

private data class RuntimeConfigFile(
    val restBaseUrl: String? = null,
    val webSocketUrl: String? = null,
    val wifiFileBaseUrl: String? = null,
    val cerebellumBaseUrl: String? = null,
    val cerebellumApiKey: String? = null,
    val bleServiceUuid: String? = null,
    val bleCommandUuid: String? = null,
    val bleStatusUuid: String? = null,
    val streamRelayUrl: String? = null,
    val useRealBle: Boolean? = null
)

class RuntimeConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("patrol_runtime_config", Context.MODE_PRIVATE)
    private val assets = context.applicationContext.assets
    private val packagedConfig by lazy { readPackagedConfig() }

    fun read(): RuntimeConfig {
        migrateLegacyCerebellumBaseUrl()
        return RuntimeConfig(
            restBaseUrl = configString(KEY_REST_BASE_URL, packagedConfig?.restBaseUrl, BuildConfig.REST_BASE_URL),
            webSocketUrl = configString(KEY_WEBSOCKET_URL, packagedConfig?.webSocketUrl, BuildConfig.WEBSOCKET_URL),
            wifiFileBaseUrl = configString(KEY_WIFI_FILE_BASE_URL, packagedConfig?.wifiFileBaseUrl, BuildConfig.WIFI_FILE_BASE_URL),
            cerebellumBaseUrl = configString(KEY_CEREBELLUM_BASE_URL, packagedConfig?.cerebellumBaseUrl, BuildConfig.CEREBELLUM_BASE_URL),
            cerebellumApiKey = configString(KEY_CEREBELLUM_API_KEY, packagedConfig?.cerebellumApiKey, BuildConfig.CEREBELLUM_API_KEY),
            bleServiceUuid = configString(KEY_BLE_SERVICE_UUID, packagedConfig?.bleServiceUuid, BuildConfig.BLE_SERVICE_UUID),
            bleCommandUuid = configString(KEY_BLE_COMMAND_UUID, packagedConfig?.bleCommandUuid, BuildConfig.BLE_COMMAND_UUID),
            bleStatusUuid = configString(KEY_BLE_STATUS_UUID, packagedConfig?.bleStatusUuid, BuildConfig.BLE_STATUS_UUID),
            streamRelayUrl = configString(KEY_STREAM_RELAY_URL, packagedConfig?.streamRelayUrl, BuildConfig.STREAM_RELAY_URL),
            useRealBle = if (prefs.contains(KEY_USE_REAL_BLE)) {
                prefs.getBoolean(KEY_USE_REAL_BLE, BuildConfig.USE_REAL_BLE)
            } else {
                packagedConfig?.useRealBle ?: BuildConfig.USE_REAL_BLE
            }
        )
    }

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

    private fun configString(key: String, packagedValue: String?, buildConfigValue: String): String =
        prefs.getString(key, null)
            ?.trim()
            .takeUnless { it.isNullOrBlank() }
            ?: packagedValue?.trim().takeUnless { it.isNullOrBlank() }
            ?: buildConfigValue.trim()

    private fun readPackagedConfig(): RuntimeConfigFile? =
        runCatching {
            assets.open(PACKAGED_CONFIG_FILE).use { input ->
                input.reader(Charsets.UTF_8).use { reader ->
                    Gson().fromJson(reader, RuntimeConfigFile::class.java)
                }
            }
        }.getOrNull()

    private fun migrateLegacyCerebellumBaseUrl() {
        val saved = prefs.getString(KEY_CEREBELLUM_BASE_URL, null)?.trim()?.trimEnd('/') ?: return
        val packaged = packagedConfig?.cerebellumBaseUrl?.trim()?.trimEnd('/')
            ?.takeUnless { it.isBlank() || it in LEGACY_CEREBELLUM_BASE_URLS }
            ?: BuildConfig.CEREBELLUM_BASE_URL.trim().trimEnd('/')
                .takeUnless { it.isBlank() || it in LEGACY_CEREBELLUM_BASE_URLS }
            ?: return
        if (saved in LEGACY_CEREBELLUM_BASE_URLS) {
            prefs.edit()
                .putString(KEY_CEREBELLUM_BASE_URL, packaged)
                .apply()
        }
    }

    private companion object {
        const val PACKAGED_CONFIG_FILE = "patrol-runtime.json"
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
        val LEGACY_CEREBELLUM_BASE_URLS = setOf(
            "http://10.0.2.2:8089",
            "http://127.0.0.1:8089",
            "http://localhost:8089"
        )
    }
}

class RuntimeTokenStore {
    @Volatile
    private var accessToken: String? = null
    @Volatile
    private var pairingAccountId: String = DefaultPairingAccountId

    fun token(): String? = accessToken

    fun pairingAccountId(): String = pairingAccountId

    fun update(session: AuthSession?) {
        accessToken = session?.accessToken
        pairingAccountId = session?.let { createPairingAccountId(it) } ?: DefaultPairingAccountId
    }

    private fun createPairingAccountId(session: AuthSession): String {
        val source = session.refreshToken.ifBlank { session.accessToken }.ifBlank { DefaultPairingAccountId }
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) }.take(32)
    }

    private companion object {
        const val DefaultPairingAccountId = "patrollink-local-operator"
    }
}
