package com.patrollink.data.wifi

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.MacAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

class DeviceWifiNetworkConnector(
    context: Context
) {
    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)

    suspend fun connect(
        ssid: String,
        password: String,
        timeoutMillis: Long = ConnectTimeoutMillis
    ): DeviceWifiSession = withContext(Dispatchers.IO) {
        require(ssid.isNotBlank()) { "device wifi ssid is blank" }
        currentSession(ssid)?.let { return@withContext it }
        val bssid = scanForDeviceWifi(ssid)
        connectWithSavedNetwork(ssid, timeoutMillis)?.let { return@withContext it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { connectWithSpecifier(ssid, password, VisibleFirstTimeoutMillis, hiddenSsid = false, bssid = bssid) }
                .getOrElse { visibleError ->
                    Log.w(Tag, "visible device wifi request failed for $ssid: ${visibleError.message}; retrying hidden ssid")
                    runCatching { connectWithSpecifier(ssid, password, timeoutMillis, hiddenSsid = true, bssid = bssid) }
                        .getOrElse { hiddenError ->
                            Log.w(Tag, "hidden device wifi request failed for $ssid: ${hiddenError.message}; trying network suggestion")
                            connectWithSuggestion(ssid, password, bssid, timeoutMillis)
                        }
                }
        } else {
            connectWithLegacyConfig(ssid, password, timeoutMillis)
        }
    }

    suspend fun currentSession(ssid: String): DeviceWifiSession? = withContext(Dispatchers.IO) {
        currentWifiSession(ssid)
    }

    @SuppressLint("MissingPermission")
    suspend fun currentWifiSsid(): String? = withContext(Dispatchers.IO) {
        wifiManager?.connectionInfo?.ssid
            ?.unquoteWifiValue()
            ?.takeUnless { it.isBlank() || it == "<unknown ssid>" }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private suspend fun connectWithSavedNetwork(
        ssid: String,
        timeoutMillis: Long
    ): DeviceWifiSession? {
        val wifi = wifiManager ?: return null
        val quotedSsid = ssid.quoteWifiValue()
        val networkId = runCatching {
            wifi.configuredNetworks
                ?.firstOrNull { it.SSID == quotedSsid }
                ?.networkId
        }.getOrNull() ?: return null
        Log.i(Tag, "connecting saved device wifi ssid=$ssid networkId=$networkId")
        val enabled = runCatching { wifi.enableNetwork(networkId, true) }
            .onFailure { Log.w(Tag, "enable saved device wifi failed for $ssid: ${it.message}") }
            .getOrElse { return null }
        if (!enabled) {
            Log.w(Tag, "enable saved device wifi returned false for $ssid networkId=$networkId")
            return null
        }
        runCatching { wifi.reconnect() }
            .onFailure { Log.w(Tag, "reconnect saved device wifi failed for $ssid: ${it.message}") }
        val connected = withTimeoutOrNull(timeoutMillis) {
            var session: DeviceWifiSession? = null
            while (session == null) {
                session = currentWifiSession(ssid)
                if (session != null) break
                delay(SavedNetworkPollMillis)
            }
            session
        }
        if (connected == null) {
            Log.w(Tag, "saved device wifi connect timed out ssid=$ssid networkId=$networkId")
        }
        return connected
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun connectWithSuggestion(
        ssid: String,
        password: String,
        bssid: String?,
        timeoutMillis: Long
    ): DeviceWifiSession {
        val wifi = wifiManager ?: error("wifi service unavailable")
        val suggestion = WifiNetworkSuggestion.Builder()
            .setSsid(ssid)
            .apply {
                if (password.isNotBlank()) setWpa2Passphrase(password)
                bssid?.let { setBssid(MacAddress.fromString(it)) }
            }
            .build()
        val status = wifi.addNetworkSuggestions(listOf(suggestion))
        Log.i(Tag, "addNetworkSuggestions ssid=$ssid bssid=${bssid.orEmpty()} status=$status")
        check(
            status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS ||
                status == WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE
        ) { "wifi suggestion failed: $status" }
        val connected: DeviceWifiSession? = withTimeoutOrNull(timeoutMillis) {
            var session: DeviceWifiSession? = null
            while (session == null) {
                session = currentWifiSession(ssid)
                if (session != null) break
                delay(SuggestionPollMillis)
                runCatching { wifi.startScan() }
            }
            session
        }
        return connected ?: throw DeviceWifiUserConnectionRequiredException(ssid)
    }

    @SuppressLint("MissingPermission")
    private fun currentWifiSession(ssid: String): DeviceWifiSession? {
        val wifi = wifiManager ?: return null
        val connectivity = connectivityManager ?: return null
        val currentSsid = wifi.connectionInfo?.ssid?.unquoteWifiValue()
        val activeNetwork = connectivity.activeNetwork
        val activeCapabilities = activeNetwork?.let(connectivity::getNetworkCapabilities)
        val wifiNetworks = connectivity.allNetworks.orEmpty()
            .filter { network ->
                connectivity.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
        val reusableNetwork = selectReusableWifiNetwork(
            currentSsid = currentSsid,
            targetSsid = ssid,
            activeNetwork = activeNetwork,
            activeNetworkIsWifi = activeCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            wifiNetworks = wifiNetworks
        ) ?: return null
        Log.i(Tag, "reusing current device wifi ssid=$ssid network=$reusableNetwork active=$activeNetwork wifiNetworks=${wifiNetworks.joinToString()}")
        return DeviceWifiSession(
            network = reusableNetwork,
            connectivityManager = connectivity,
            callback = null,
            legacyNetworkId = null,
            wifiManager = wifiManager
        )
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun connectWithSpecifier(
        ssid: String,
        password: String,
        timeoutMillis: Long,
        hiddenSsid: Boolean,
        bssid: String?
    ): DeviceWifiSession {
        val connectivity = connectivityManager ?: error("connectivity service unavailable")
        val specifierBuilder = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setIsHiddenSsid(hiddenSsid)
        bssid?.let { specifierBuilder.setBssid(MacAddress.fromString(it)) }
        if (password.isNotBlank()) {
            specifierBuilder.setWpa2Passphrase(password)
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifierBuilder.build())
            .build()
        val deferred = CompletableDeferred<Network>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(Tag, "device wifi available ssid=$ssid hidden=$hiddenSsid network=$network")
                deferred.complete(network)
            }

            override fun onUnavailable() {
                Log.w(Tag, "device wifi unavailable ssid=$ssid hidden=$hiddenSsid")
                deferred.completeExceptionally(IllegalStateException("device wifi unavailable: $ssid"))
            }

            override fun onLost(network: Network) {
                Log.w(Tag, "device wifi lost ssid=$ssid hidden=$hiddenSsid network=$network")
                if (!deferred.isCompleted) {
                    deferred.completeExceptionally(IllegalStateException("device wifi lost before available: $ssid"))
                }
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                Log.i(Tag, "device wifi link ssid=$ssid hidden=$hiddenSsid link=$linkProperties")
            }
        }
        Log.i(Tag, "requesting device wifi ssid=$ssid hidden=$hiddenSsid bssid=${bssid.orEmpty()} timeout=${timeoutMillis}ms")
        connectivity.requestNetwork(request, callback, timeoutMillis.toInt())
        val network = try {
            withTimeout(timeoutMillis + RequestCallbackGraceMillis) { deferred.await() }
        } catch (throwable: Throwable) {
            runCatching { connectivity.unregisterNetworkCallback(callback) }
            throw throwable
        }
        return DeviceWifiSession(
            network = network,
            connectivityManager = connectivity,
            callback = callback,
            legacyNetworkId = null,
            wifiManager = wifiManager
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun scanForDeviceWifi(ssid: String): String? {
        val wifi = wifiManager ?: return null
        return withContext(Dispatchers.IO) {
            runCatching { wifi.startScan() }
                .onSuccess { Log.i(Tag, "startScan for device wifi ssid=$ssid started=$it") }
                .onFailure { Log.w(Tag, "startScan for device wifi ssid=$ssid failed: ${it.message}") }
            delay(PreScanSettleMillis)
            val matches = runCatching {
                wifi.scanResults.orEmpty()
                    .filter { it.SSID == ssid }
                    .sortedByDescending { it.level }
            }.getOrDefault(emptyList())
            Log.i(
                Tag,
                "scan results for device wifi ssid=$ssid matches=${matches.joinToString { "${it.BSSID}/rssi=${it.level}/freq=${it.frequency}" }.ifBlank { "none" }}"
            )
            matches.firstOrNull()?.BSSID
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private suspend fun connectWithLegacyConfig(
        ssid: String,
        password: String,
        timeoutMillis: Long
    ): DeviceWifiSession {
        val wifi = wifiManager ?: error("wifi service unavailable")
        if (!wifi.isWifiEnabled) {
            wifi.isWifiEnabled = true
        }
        val quotedSsid = ssid.quoteWifiValue()
        val configuredId = wifi.configuredNetworks
            ?.firstOrNull { it.SSID == quotedSsid }
            ?.networkId
        val networkId = configuredId ?: wifi.addNetwork(
            WifiConfiguration().apply {
                SSID = quotedSsid
                if (password.isNotBlank()) {
                    preSharedKey = password.quoteWifiValue()
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                } else {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                }
            }
        )
        check(networkId >= 0) { "legacy wifi addNetwork failed: $ssid" }
        wifi.disconnect()
        check(wifi.enableNetwork(networkId, true)) { "legacy wifi enableNetwork failed: $ssid" }
        wifi.reconnect()
        val connected = withTimeoutOrNull(timeoutMillis) {
            while (wifi.connectionInfo?.ssid?.unquoteWifiValue() != ssid) {
                delay(LegacyPollMillis)
            }
            true
        } == true
        check(connected) { "legacy wifi connect timed out: $ssid" }
        return DeviceWifiSession(
            network = connectivityManager?.activeNetwork,
            connectivityManager = connectivityManager,
            callback = null,
            legacyNetworkId = networkId,
            wifiManager = wifi
        )
    }

    private fun String.quoteWifiValue(): String =
        if (startsWith('"') && endsWith('"')) this else "\"$this\""

    private fun String.unquoteWifiValue(): String =
        trim('"')

    private companion object {
        const val ConnectTimeoutMillis = 30_000L
        const val VisibleFirstTimeoutMillis = 15_000L
        const val RequestCallbackGraceMillis = 1_000L
        const val LegacyPollMillis = 500L
        const val SavedNetworkPollMillis = 500L
        const val PreScanSettleMillis = 4_000L
        const val SuggestionPollMillis = 1_000L
        const val Tag = "DeviceWifiConnector"
    }
}

class DeviceWifiSession internal constructor(
    val network: Network?,
    private val connectivityManager: ConnectivityManager?,
    private val callback: ConnectivityManager.NetworkCallback?,
    private val legacyNetworkId: Int?,
    private val wifiManager: WifiManager?
) : AutoCloseable {
    val linkProperties: LinkProperties?
        get() = network?.let { connectivityManager?.getLinkProperties(it) }

    fun bindProcess(): Boolean =
        network != null && connectivityManager?.bindProcessToNetwork(network) == true

    override fun close() {
        if (ownsTemporaryNetworkBinding(callback != null, legacyNetworkId != null)) {
            runCatching {
                if (connectivityManager?.boundNetworkForProcess == network) {
                    connectivityManager?.bindProcessToNetwork(null)
                }
            }
        }
        callback?.let { runCatching { connectivityManager?.unregisterNetworkCallback(it) } }
        @Suppress("DEPRECATION")
        legacyNetworkId?.let { id ->
            runCatching { wifiManager?.disableNetwork(id) }
        }
    }
}

internal fun ownsTemporaryNetworkBinding(hasNetworkCallback: Boolean, hasLegacyNetworkId: Boolean): Boolean =
    hasNetworkCallback || hasLegacyNetworkId

class DeviceWifiUserConnectionRequiredException(ssid: String) : IllegalStateException(
    "手机系统未授权连接设备热点 $ssid；请在系统 Wi-Fi 弹窗或设置中手动选择 $ssid，连接后返回 PatrolLink 重试媒体同步"
)

internal fun <T> selectReusableWifiNetwork(
    currentSsid: String?,
    targetSsid: String,
    activeNetwork: T?,
    activeNetworkIsWifi: Boolean,
    wifiNetworks: List<T>
): T? {
    if (currentSsid != targetSsid) return null
    if (activeNetwork != null && activeNetworkIsWifi) return activeNetwork
    return wifiNetworks.firstOrNull()
}
