package com.patrollink.data

import android.content.Context
import com.google.gson.Gson
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

data class BackendRuntimeSettings(
    val restBaseUrl: String,
    val webSocketUrl: String
)

interface RuntimeConfigGateway {
    fun readCerebellumSettings(): CerebellumRuntimeSettings
    fun saveCerebellumSettings(baseUrl: String, apiKey: String): CerebellumRuntimeSettings
}

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

class RuntimeConfigStore(context: Context) : RuntimeConfigGateway {
    private val prefs = context.getSharedPreferences("patrol_runtime_config", Context.MODE_PRIVATE)
    private val assets = context.applicationContext.assets
    private val packagedConfig by lazy { readPackagedConfig() }

    fun read(): RuntimeConfig {
        migrateLegacyBackendSettings()
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

    override fun readCerebellumSettings(): CerebellumRuntimeSettings =
        read().let { config ->
            CerebellumRuntimeSettings(
                baseUrl = config.cerebellumBaseUrl,
                apiKey = config.cerebellumApiKey
            )
        }

    fun readBackendSettings(): BackendRuntimeSettings =
        read().let { config ->
            BackendRuntimeSettings(
                restBaseUrl = config.restBaseUrl,
                webSocketUrl = config.webSocketUrl
            )
        }

    fun saveBackendSettings(restBaseUrl: String, webSocketUrl: String = ""): BackendRuntimeSettings {
        val settings = normalizeBackendSettings(restBaseUrl, webSocketUrl)
        prefs.edit().apply {
            if (settings.restBaseUrl.isBlank()) {
                remove(KEY_REST_BASE_URL)
                remove(KEY_WEBSOCKET_URL)
            } else {
                putString(KEY_REST_BASE_URL, settings.restBaseUrl)
                putString(KEY_WEBSOCKET_URL, settings.webSocketUrl)
            }
        }.apply()
        return readBackendSettings()
    }

    override fun saveCerebellumSettings(baseUrl: String, apiKey: String): CerebellumRuntimeSettings {
        prefs.edit()
            .putString(KEY_CEREBELLUM_BASE_URL, baseUrl.trim().trimEnd('/'))
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

    private fun migrateLegacyBackendSettings() {
        val savedRest = prefs.getString(KEY_REST_BASE_URL, null)?.trim()?.trimEnd('/')
        val savedWebSocket = prefs.getString(KEY_WEBSOCKET_URL, null)?.trim()?.trimEnd('/')
        val shouldMigrateRest = savedRest != null && savedRest in LEGACY_REST_BASE_URLS
        val shouldMigrateWebSocket = savedWebSocket != null && savedWebSocket in LEGACY_WEBSOCKET_URLS
        if (!shouldMigrateRest && !shouldMigrateWebSocket) return

        val packagedRest = packagedConfig?.restBaseUrl?.trim()?.trimEnd('/')
            ?.takeUnless { it.isBlank() || it in LEGACY_REST_BASE_URLS }
            ?: BuildConfig.REST_BASE_URL.trim().trimEnd('/')
                .takeUnless { it.isBlank() || it in LEGACY_REST_BASE_URLS }
            ?: return
        val packagedWebSocket = packagedConfig?.webSocketUrl?.trim()?.trimEnd('/')
            ?.takeUnless { it.isBlank() || it in LEGACY_WEBSOCKET_URLS }
            ?: normalizeBackendSettings(packagedRest, "").webSocketUrl

        prefs.edit()
            .putString(KEY_REST_BASE_URL, packagedRest)
            .putString(KEY_WEBSOCKET_URL, packagedWebSocket)
            .apply()
    }

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

    companion object {
        internal fun normalizeBackendSettings(restBaseUrl: String, webSocketUrl: String): BackendRuntimeSettings {
            val rest = restBaseUrl.trim().trimEnd('/')
            val websocket = webSocketUrl.trim().trimEnd('/').ifBlank {
                when {
                    rest.startsWith("https://") -> "wss://${rest.removePrefix("https://")}/resource/websocket"
                    rest.startsWith("http://") -> "ws://${rest.removePrefix("http://")}/resource/websocket"
                    else -> ""
                }
            }
            return BackendRuntimeSettings(restBaseUrl = rest, webSocketUrl = websocket)
        }

        const val PACKAGED_CONFIG_FILE = "patrol-runtime.json"
        private const val KEY_REST_BASE_URL = "rest_base_url"
        private const val KEY_WEBSOCKET_URL = "websocket_url"
        private const val KEY_WIFI_FILE_BASE_URL = "wifi_file_base_url"
        private const val KEY_CEREBELLUM_BASE_URL = "cerebellum_base_url"
        private const val KEY_CEREBELLUM_API_KEY = "cerebellum_api_key"
        private const val KEY_BLE_SERVICE_UUID = "ble_service_uuid"
        private const val KEY_BLE_COMMAND_UUID = "ble_command_uuid"
        private const val KEY_BLE_STATUS_UUID = "ble_status_uuid"
        private const val KEY_STREAM_RELAY_URL = "stream_relay_url"
        private const val KEY_USE_REAL_BLE = "use_real_ble"
        private val LEGACY_REST_BASE_URLS = setOf(
            "http://10.0.2.2:8080",
            "http://127.0.0.1:8080",
            "http://localhost:8080",
            "http://192.168.1.3:8080",
            "https://api.patrollink.example.com"
        )
        private val LEGACY_WEBSOCKET_URLS = setOf(
            "ws://10.0.2.2:8080/resource/websocket",
            "ws://127.0.0.1:8080/resource/websocket",
            "ws://localhost:8080/resource/websocket",
            "ws://192.168.1.3:8080/resource/websocket",
            "wss://api.patrollink.example.com/resource/websocket"
        )
        private val LEGACY_CEREBELLUM_BASE_URLS = setOf(
            "http://10.0.2.2:8089",
            "http://127.0.0.1:8089",
            "http://localhost:8089",
            "http://192.168.1.3:8088"
        )
    }
}

class RuntimeTokenStore(context: Context) {
    private val pairingPrefs = context.getSharedPreferences(PairingPrefsName, Context.MODE_PRIVATE)

    @Volatile
    private var accessToken: String? = null
    @Volatile
    private var pairingAccountId: String = savedPairingAccountId() ?: DefaultPairingAccountId

    fun token(): String? = accessToken

    fun pairingAccountId(): String = pairingAccountId

    fun update(session: AuthSession?) {
        accessToken = session?.accessToken
        val saved = savedPairingAccountId()
        pairingAccountId = when {
            saved != null -> saved
            else -> pairingAccountId.ifBlank { DefaultPairingAccountId }
        }
    }

    fun updatePairingUsername(username: String?) {
        val normalized = username
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return
        pairingAccountId = normalized.also { accountId ->
            pairingPrefs.edit().putString(PairingAccountKey, accountId).apply()
        }
    }

    private companion object {
        const val PairingPrefsName = "patrollink_pairing"
        const val PairingAccountKey = "account_id"
        const val DefaultPairingAccountId = "patrollink-local-operator"
    }

    private fun savedPairingAccountId(): String? =
        pairingPrefs.getString(PairingAccountKey, null)?.takeIf { it.isNotBlank() }
}
