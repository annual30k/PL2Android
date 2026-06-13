package com.patrollink.data.ute

import android.content.Context
import android.util.Log
import com.patrollink.data.wifi.DeviceWifiNetworkConnector
import com.patrollink.data.wifi.DeviceWifiSession
import com.patrollink.data.wifi.DeviceWifiUserConnectionRequiredException
import com.patrollink.domain.MediaFile
import com.yc.nadalsdk.bean.smart.GlassesStoreInfo
import com.yc.nadalsdk.bean.smart.VideoParametersInfo
import com.yc.nadalsdk.constants.NotifyType
import com.yc.nadalsdk.constants.smart.GlassesRecordDirection
import com.yc.nadalsdk.constants.smart.WifiState
import java.io.File
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class UteWifiMediaClient(
    context: Context,
    private val bridge: UteSdkBridge,
    private val pairingAccountIdProvider: () -> String = { "patrollink-local-operator" },
    private val connector: DeviceWifiNetworkConnector = DeviceWifiNetworkConnector(context)
) {
    private val cachedFiles = ConcurrentHashMap<String, UteWifiRemoteFile>()
    private val accountBinder by lazy { UteHeadsetAccountBinder(bridge, pairingAccountIdProvider) }
    @Volatile
    private var cachedWifiCredentials: DeviceWifiCredentials? = null

    suspend fun listFiles(currentPhoneWifiOnly: Boolean = false): List<MediaFile> {
        val remote = listRemoteFiles(currentPhoneWifiOnly = currentPhoneWifiOnly)
        return remote.map { it.toMediaFile(local = false) }
    }

    suspend fun download(
        fileId: String,
        targetDirectory: File,
        currentPhoneWifiOnly: Boolean = false
    ): File {
        val remote = cachedFiles[fileId]
            ?: listRemoteFiles(currentPhoneWifiOnly = currentPhoneWifiOnly).firstOrNull { it.id == fileId }
            ?: error("wifi media file not found: $fileId")
        cachedWifiCredentials?.let { credentials ->
            runCatching {
                if (currentPhoneWifiOnly) {
                    withCurrentPhoneWifiSession(credentials.ssid) { session ->
                        downloadRemoteFile(remote, targetDirectory, session)
                    }
                } else {
                    withDeviceWifiSession(credentials.ssid, credentials.password) { session ->
                        downloadRemoteFile(remote, targetDirectory, session)
                    }
                }
            }.onSuccess { return it }
                .onFailure { Log.w(Tag, "cached wifi download path failed for $fileId: ${it.message}; retrying with sdk wifi prepare") }
        }
        return withDeviceWifiSession(currentPhoneWifiOnly = currentPhoneWifiOnly) { session ->
            downloadRemoteFile(remote, targetDirectory, session)
        }
    }

    suspend fun diagnostics(currentPhoneWifiOnly: Boolean = false): String =
        withDeviceWifiSession(currentPhoneWifiOnly = currentPhoneWifiOnly) { session ->
            val hosts = session.candidateHosts()
            val client = session.httpClient()
            val hits = mutableListOf<String>()
            session.probeUrls().take(DiagnosticProbeRequestLimit).forEach { url ->
                if (hits.size >= DiagnosticHitLimit) return@forEach
                runCatching {
                    client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        val preview = response.body?.string().orEmpty()
                            .replace(Regex("\\s+"), " ")
                            .take(160)
                        if (response.isSuccessful || response.code in setOf(401, 403, 404)) {
                            hits += "$url status=${response.code} preview=$preview"
                        }
                    }
                }
            }
            "hosts=${hosts.joinToString()},hits=${hits.joinToString(separator = " | ").ifBlank { "none" }}"
        }

    private suspend fun listRemoteFiles(currentPhoneWifiOnly: Boolean = false): List<UteWifiRemoteFile> =
        withDeviceWifiSession(currentPhoneWifiOnly = currentPhoneWifiOnly) { session ->
            discoverFiles(session).also { files ->
                files.forEach { cachedFiles[it.id] = it }
            }
        }

    private suspend fun <T> withDeviceWifiSession(
        currentPhoneWifiOnly: Boolean = false,
        block: suspend (DeviceWifiSession) -> T
    ): T {
        val prepared = prepareDeviceWifi(currentPhoneWifiOnly = currentPhoneWifiOnly)
        prepared.session?.let { session ->
            return try {
                session.bindProcess()
                block(session)
            } finally {
                session.close()
                Log.i(Tag, "manual device wifi session closed on phone side; keeping device AP state unchanged")
            }
        }
        return withDeviceWifiSession(prepared.ssid, prepared.password, block)
    }

    private suspend fun <T> withCurrentPhoneWifiSession(
        ssid: String,
        block: suspend (DeviceWifiSession) -> T
    ): T {
        val session = connector.currentSession(ssid) ?: throw DeviceWifiUserConnectionRequiredException(ssid)
        return try {
            session.bindProcess()
            block(session)
        } finally {
            session.close()
            Log.i(Tag, "current phone wifi media session closed; keeping device AP state unchanged")
        }
    }

    private suspend fun <T> withDeviceWifiSession(
        ssid: String,
        password: String,
        block: suspend (DeviceWifiSession) -> T
    ): T {
        val session = connector.connect(ssid, password, WifiConnectTimeoutMillis)
        return try {
            session.bindProcess()
            block(session)
        } finally {
            session.close()
            Log.i(Tag, "device wifi session closed on phone side; keeping device AP state unchanged")
        }
    }

    private suspend fun prepareDeviceWifi(currentPhoneWifiOnly: Boolean = false): PreparedDeviceWifi = coroutineScope {
        UteAccountBindingGuard.requireAcceptedForWifi(accountBinder.bind("wifi-media"))
        val current = withContext(Dispatchers.IO) {
            bridge.client.openOrCloseNotify(true)
            bridge.connection.smartGetDeviceWiFiInfo().data
        } ?: error("device wifi info unavailable")
        val ssid = current.wiFiSSID.orEmpty().ifBlank { error("device wifi ssid is blank") }
        val password = current.wiFiPassword.orEmpty().ifBlank { DefaultDeviceWifiPassword }
        if (shouldPreferPhoneConnectedWifiBeforeSdkOpen(currentPhoneWifiOnly)) {
            connector.currentSession(ssid)?.let { session ->
                Log.i(Tag, "phone is already connected to device wifi $ssid; using current phone wifi before sdk AP open")
                cachedWifiCredentials = DeviceWifiCredentials(ssid = ssid, password = password)
                return@coroutineScope PreparedDeviceWifi(ssid = ssid, password = password, session = session)
            }
        }
        if (currentPhoneWifiOnly) {
            val session = connector.currentSession(ssid) ?: throw DeviceWifiUserConnectionRequiredException(ssid)
            Log.i(Tag, "using current phone wifi for device media ssid=$ssid")
            cachedWifiCredentials = DeviceWifiCredentials(ssid = ssid, password = password)
            return@coroutineScope PreparedDeviceWifi(ssid = ssid, password = password, session = session)
        }
        applyGloryViewWifiWarmup(ssid, password)
        val readyState = openDeviceApAndWait(ssid)
        if (!readyState.isWifiConnectableState()) {
            if (shouldTryPhoneConnectedWifiFallback(readyState)) {
                connector.currentSession(ssid)?.let { session ->
                    Log.i(Tag, "device wifi AP state=$readyState but phone is already connected to $ssid; using current phone wifi")
                    cachedWifiCredentials = DeviceWifiCredentials(ssid = ssid, password = password)
                    return@coroutineScope PreparedDeviceWifi(ssid = ssid, password = password, session = session)
                }
            }
            Log.w(Tag, "device wifi AP not ready after open state=$readyState; restarting ssid=$ssid")
            withContext(Dispatchers.IO) { runCatching { bridge.connection.smartSetDeviceWiFiSwitch(false) } }
            delay(WifiApRestartSettleMillis)
            val restartedState = openDeviceApAndWait(ssid)
            if (!restartedState.isWifiConnectableState() && shouldTryPhoneConnectedWifiFallback(restartedState)) {
                connector.currentSession(ssid)?.let { session ->
                    Log.i(Tag, "device wifi AP restarted state=$restartedState but phone is already connected to $ssid; using current phone wifi")
                    cachedWifiCredentials = DeviceWifiCredentials(ssid = ssid, password = password)
                    return@coroutineScope PreparedDeviceWifi(ssid = ssid, password = password, session = session)
                }
            }
            check(restartedState.isWifiConnectableState()) { "device wifi AP not ready: $restartedState" }
        }
        cachedWifiCredentials = DeviceWifiCredentials(ssid = ssid, password = password)
        PreparedDeviceWifi(ssid = ssid, password = password)
    }

    private fun downloadRemoteFile(
        remote: UteWifiRemoteFile,
        targetDirectory: File,
        session: DeviceWifiSession
    ): File {
        val target = remote.localTarget(targetDirectory).also { it.parentFile?.mkdirs() }
        val client = session.httpClient()
        var lastFailure: String? = null
        remote.downloadUrls.distinct().forEach { url ->
            val response = runCatching {
                client.newCall(Request.Builder().url(url).build()).execute()
            }.getOrElse {
                lastFailure = "$url -> ${it.message}"
                return@forEach
            }
            response.use {
                if (!it.isSuccessful) {
                    lastFailure = "$url -> ${it.code}"
                    return@use
                }
                val body = it.body ?: run {
                    lastFailure = "$url -> empty body"
                    return@use
                }
                target.outputStream().use { output ->
                    body.byteStream().copyTo(output)
                }
                return target
            }
        }
        error("wifi media download failed: ${lastFailure ?: remote.url}")
    }

    private suspend fun applyGloryViewWifiWarmup(ssid: String, password: String) {
        withContext(Dispatchers.IO) {
            runCatching { bridge.client.openOrCloseNotify(true) }
            runCatching { bridge.connection.smartSetDeviceWiFiSSID(ssid) }
                .onSuccess { Log.i(Tag, "smartSetDeviceWiFiSSID success=${it.isSuccess},error=${it.errorCode}") }
                .onFailure { Log.w(Tag, "smartSetDeviceWiFiSSID failed: ${it.message}") }
            runCatching { bridge.connection.smartSetDeviceWiFiPassword(password) }
                .onSuccess { Log.i(Tag, "smartSetDeviceWiFiPassword success=${it.isSuccess},error=${it.errorCode}") }
                .onFailure { Log.w(Tag, "smartSetDeviceWiFiPassword failed: ${it.message}") }
            runCatching { bridge.connection.setGlassesRecordingDirection(GlassesRecordDirection.VERTICAL_SCREEN) }
                .onSuccess { Log.i(Tag, "setGlassesRecordingDirection success=${it.isSuccess},error=${it.errorCode}") }
                .onFailure { Log.w(Tag, "setGlassesRecordingDirection failed: ${it.message}") }
            runCatching { bridge.connection.setGlassesRecordingDuration(GloryViewRecordingDurationSeconds) }
                .onSuccess { Log.i(Tag, "setGlassesRecordingDuration success=${it.isSuccess},error=${it.errorCode}") }
                .onFailure { Log.w(Tag, "setGlassesRecordingDuration failed: ${it.message}") }
            runCatching {
                bridge.connection.setVideoParameters(
                    VideoParametersInfo(
                        GloryViewVideoWidth,
                        GloryViewVideoHeight,
                        GloryViewVideoFrameRate
                    )
                )
            }
                .onSuccess { Log.i(Tag, "setVideoParameters success=${it.isSuccess},error=${it.errorCode}") }
                .onFailure { Log.w(Tag, "setVideoParameters failed: ${it.message}") }
            runCatching { bridge.connection.getGlassesInfo() }
                .onSuccess { response ->
                    val store = response.data?.glassesStoreInfo
                    Log.i(
                        Tag,
                        "getGlassesInfo success=${response.isSuccess},error=${response.errorCode},state=${response.data?.state},store=${store.toStoreSummary()}"
                    )
                }
                .onFailure { Log.w(Tag, "getGlassesInfo failed: ${it.message}") }
            runCatching { bridge.connection.notifyMediaSyncCompleted() }
                .onSuccess { Log.i(Tag, "notifyMediaSyncCompleted success=${it.isSuccess},error=${it.errorCode},data=${it.data}") }
                .onFailure { Log.w(Tag, "notifyMediaSyncCompleted failed: ${it.message}") }
        }
        runCatching { UteSmartAuthWarmup(bridge).run(GloryViewAuthWarmupMillis) }
            .onSuccess { Log.i(Tag, "smart auth warmup $it") }
            .onFailure { Log.w(Tag, "smart auth warmup failed: ${it.message}") }
    }

    private fun GlassesStoreInfo?.toStoreSummary(): String =
        this?.let {
            "photo=$newTakenPictures/$totalPictures,audio=$newRecordAudio/$totalRecordAudio,video=$newRecordVideo/$totalRecordVideo,free=$freeSpace,total=$maxSpace"
        } ?: "null"

    private suspend fun openDeviceApAndWait(ssid: String): Int = coroutineScope {
        val notifyReady = async {
            withTimeoutOrNull(WifiApReadyTimeoutMillis) {
                bridge.notifies
                    .filter { it.type == NotifyType.SMART_WIFI_STATE_NOTIFY }
                    .mapNotNull { it.data?.toString()?.toIntOrNull() }
                    .first { it.isTerminalWifiState() || it.isWifiConnectableState() }
            }
        }
        withContext(Dispatchers.IO) {
            val response = bridge.connection.smartSetDeviceWiFiSwitch(true)
            check(response.isSuccess || response.data == true) { "device wifi switch failed: ${response.errorCode}" }
        }
        val polled = waitForDeviceApReady()
        val notified = notifyReady.await()
        if (notified != null) {
            Log.i(Tag, "device wifi notify state=$notified for ssid=$ssid")
        }
        val selected = when {
            polled.isWifiApReadyState() -> polled
            notified?.isWifiApReadyState() == true -> notified
            notified?.isWifiConnectableState() == true -> notified
            polled.isWifiConnectableState() -> polled
            notified != null -> notified
            else -> polled
        }
        if (selected.isWifiConnectableState() && !selected.isWifiApReadyState()) {
            delay(WifiOpenSuccessSettleMillis)
        }
        return@coroutineScope selected
    }

    private suspend fun waitForDeviceApReady(): Int {
        val deadline = System.currentTimeMillis() + WifiApReadyTimeoutMillis
        var lastState = 0
        var connectableSince = 0L
        while (System.currentTimeMillis() < deadline) {
            lastState = withContext(Dispatchers.IO) {
                val infoState = runCatching { bridge.connection.smartGetDeviceWiFiInfo().data?.state }.getOrNull()
                val stateInfo = runCatching { bridge.connection.smartGetDeviceWiFiStateInfo().data?.state }.getOrNull()
                stateInfo ?: infoState ?: lastState
            }
            if (lastState.isWifiApReadyState()) return lastState
            if (lastState.isWifiConnectableState()) {
                if (connectableSince == 0L) connectableSince = System.currentTimeMillis()
                if (System.currentTimeMillis() - connectableSince >= WifiOpenSuccessSettleMillis) return lastState
            } else {
                connectableSince = 0L
            }
            delay(WifiApPollMillis)
        }
        return lastState
    }

    private fun Int.isTerminalWifiState(): Boolean =
        isWifiApReadyState() ||
            this == WifiState.WIFI_OPEN_FAILED ||
            this == WifiState.IFI_AP_CONNECT_FAILED

    private fun Int.isWifiApReadyState(): Boolean =
        this == WifiState.IFI_AP_READY || this == WifiState.IFI_AP_CONNECT

    private fun Int.isWifiConnectableState(): Boolean =
        isWifiApReadyState() ||
            this == WifiState.WIFI_OPEN_SUCCESS ||
            this == WifiState.IFI_AP_STARTING

    private suspend fun discoverFiles(session: DeviceWifiSession): List<UteWifiRemoteFile> = withContext(Dispatchers.IO) {
        val client = session.httpClient()
        val discovered = linkedMapOf<String, UteWifiRemoteFile>()
        session.probeUrls().take(FileProbeRequestLimit).forEach { url ->
            if (discovered.size >= MaxDiscoveredFiles) return@forEach
            val responseBody = runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                }
            }.getOrNull() ?: return@forEach
            UteWifiMediaParser.parseRemoteFiles(responseBody, url).forEach { file ->
                discovered.putIfAbsent(file.id, file)
            }
        }
        discovered.values.toList()
    }

    private fun DeviceWifiSession.httpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(HttpConnectTimeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(HttpReadTimeoutMillis, TimeUnit.MILLISECONDS)
            .writeTimeout(HttpReadTimeoutMillis, TimeUnit.MILLISECONDS)
            .callTimeout(HttpCallTimeoutMillis, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
        network?.socketFactory?.let { builder.socketFactory(it) }
        return builder.build()
    }

    private fun DeviceWifiSession.probeUrls(): Sequence<String> =
        candidateHosts().asSequence()
            .take(ProbeHostLimit)
            .flatMap { host ->
                UteWifiProbeCatalog.Ports.asSequence().flatMap { port ->
                    UteWifiProbeCatalog.Paths.asSequence().map { path -> "http://$host:$port$path" }
                }
            }

    private fun DeviceWifiSession.candidateHosts(): List<String> {
        val hosts = linkedSetOf<String>()
        val link = linkProperties
        link?.routes
            ?.mapNotNull { it.gateway as? Inet4Address }
            ?.mapNotNullTo(hosts) { it.hostAddress }
        link?.linkAddresses
            ?.mapNotNull { it.address as? Inet4Address }
            ?.mapNotNull { it.hostAddress }
            ?.flatMapTo(hosts) { it.likelySubnetGateways() }
        hosts += UteWifiProbeCatalog.DefaultHosts
        return hosts.filter { it.isValidHost() }.distinct()
    }

    private fun String.likelySubnetGateways(): List<String> {
        val parts = split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return emptyList()
        return listOf(
            "${parts[0]}.${parts[1]}.${parts[2]}.1",
            "${parts[0]}.${parts[1]}.${parts[2]}.254",
            "${parts[0]}.${parts[1]}.0.1",
            "${parts[0]}.${parts[1]}.1.1"
        ).distinct()
    }

    private fun String.isValidHost(): Boolean =
        isNotBlank() && this != "0.0.0.0" && !endsWith(".0") && !endsWith(".255")

    private companion object {
        const val Tag = "UteWifiMedia"
        const val DefaultDeviceWifiPassword = "12345678"
        const val WifiConnectTimeoutMillis = 30_000L
        const val WifiApReadyTimeoutMillis = 30_000L
        const val WifiApRestartSettleMillis = 4_000L
        const val WifiOpenSuccessSettleMillis = 8_000L
        const val WifiApPollMillis = 1_500L
        const val GloryViewAuthWarmupMillis = 2_800L
        const val GloryViewRecordingDurationSeconds = 24 * 60 * 60
        const val GloryViewVideoWidth = 240
        const val GloryViewVideoHeight = 0
        const val GloryViewVideoFrameRate = 16
        const val HttpConnectTimeoutMillis = 450L
        const val HttpReadTimeoutMillis = 900L
        const val HttpCallTimeoutMillis = 1_200L
        const val MaxDiscoveredFiles = 200
        const val ProbeHostLimit = 4
        const val DiagnosticProbeRequestLimit = 48
        const val FileProbeRequestLimit = 64
        const val DiagnosticHitLimit = 20

    }
}

private data class DeviceWifiCredentials(
    val ssid: String,
    val password: String
)

private data class PreparedDeviceWifi(
    val ssid: String,
    val password: String,
    val session: DeviceWifiSession? = null
)

internal fun shouldTryPhoneConnectedWifiFallback(state: Int): Boolean =
    state == WifiState.WIFI_AP_STOP ||
        state == WifiState.WIFI_OPEN_FAILED ||
        state == WifiState.IFI_AP_CONNECT_FAILED

internal fun shouldPreferPhoneConnectedWifiBeforeSdkOpen(currentPhoneWifiOnly: Boolean): Boolean =
    !currentPhoneWifiOnly
