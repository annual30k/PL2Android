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
    @Volatile
    private var retainedWifiSession: DeviceWifiSession? = null
    @Volatile
    private var retainedWifiSsid: String? = null

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
            val phoneAlreadyConnected = runCatching {
                connector.currentSession(credentials.ssid) != null
            }.getOrDefault(false)
            runCatching {
                if (shouldUseCachedWifiDownloadPath(currentPhoneWifiOnly, phoneAlreadyConnected)) {
                    withCurrentPhoneWifiSession(credentials.ssid) { session ->
                        downloadRemoteFile(remote, targetDirectory, session)
                    }
                } else {
                    error("phone is not connected to cached device wifi ${credentials.ssid}; prepare sdk AP first")
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
        val session = retainedOrConnectedSession(ssid, password)
        return try {
            block(session)
        } finally {
            Log.i(Tag, "device wifi session retained on phone side for ssid=$ssid; keeping device AP state unchanged")
        }
    }

    private suspend fun retainedOrConnectedSession(ssid: String, password: String): DeviceWifiSession {
        val retained = retainedWifiSession
        if (retained != null && retainedWifiSsid == ssid) {
            val current = connector.currentSession(ssid)
            if (current != null) {
                if (retained.network == null || retained.network == current.network) {
                    return retained
                }
                Log.i(Tag, "replacing stale retained device wifi session for ssid=$ssid")
                runCatching { retained.close() }
                retainedWifiSession = current
                retainedWifiSsid = ssid
                return current
            }
            Log.i(Tag, "dropping stale retained device wifi session for ssid=$ssid")
            runCatching { retained.close() }
            retainedWifiSession = null
            retainedWifiSsid = null
        } else if (retained != null) {
            runCatching { retained.close() }
            retainedWifiSession = null
            retainedWifiSsid = null
        }
        return connector.connect(ssid, password, WifiConnectTimeoutMillis).also { next ->
            retainedWifiSession = next
            retainedWifiSsid = ssid
        }
    }

    private suspend fun prepareDeviceWifi(currentPhoneWifiOnly: Boolean = false): PreparedDeviceWifi = coroutineScope {
        currentPhoneDeviceWifiSession()?.let { prepared ->
            if (currentPhoneWifiOnly || shouldUseCurrentHotspotBeforeSdkWifiOpen()) {
                Log.i(Tag, "phone is already on device hotspot ${prepared.ssid}; using it without sdk wifi open")
                return@coroutineScope prepared
            }
        }
        UteAccountBindingGuard.requireAcceptedForWifi(accountBinder.bind("wifi-media"))
        val current = withContext(Dispatchers.IO) {
            bridge.client.openOrCloseNotify(true)
            bridge.connection.smartGetDeviceWiFiInfo().data
        } ?: error("device wifi info unavailable")
        val sdkSsid = current.wiFiSSID.orEmpty()
        val ssid = sdkSsid.ifBlank {
            fallbackDeviceWifiSsidForKnownGlasses(
                deviceName = bridge.client.deviceName.orEmpty(),
                deviceAddress = bridge.client.deviceAddress.orEmpty()
            )?.also { fallback ->
                Log.i(Tag, "device wifi ssid blank; using known glasses fallback ssid=$fallback")
            } ?: error("device wifi ssid is blank")
        }
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
        applyGloryViewWifiWarmup(
            ssid = ssid,
            password = password,
            configureWifiCredentials = shouldConfigureGloryViewWifiWarmup(sdkSsid)
        )
        val readyState = runCatching { openDeviceApAndWait(ssid) }
            .recoverCatching { throwable ->
                if (sdkSsid.isBlank() && throwable.isWifiSwitchRejected()) {
                    Log.w(Tag, "device wifi switch rejected with blank SDK ssid; retrying after derived ssid warmup ssid=$ssid")
                    applyGloryViewWifiWarmup(
                        ssid = ssid,
                        password = password,
                        configureWifiCredentials = true
                    )
                    openDeviceApAndWait(ssid)
                } else {
                    throw throwable
                }
            }
            .getOrThrow()
        if (!readyState.isWifiConnectableState()) {
            if (shouldTryPhoneConnectedWifiFallback(readyState)) {
                connector.currentSession(ssid)?.let { session ->
                    Log.i(Tag, "device wifi AP state=$readyState but phone is already connected to $ssid; using current phone wifi")
                    cachedWifiCredentials = DeviceWifiCredentials(ssid = ssid, password = password)
                    return@coroutineScope PreparedDeviceWifi(ssid = ssid, password = password, session = session)
                }
            }
            Log.w(Tag, "device wifi AP not ready after open state=$readyState; retrying enable without closing AP ssid=$ssid")
            delay(WifiApEnableRetrySettleMillis)
            val retriedState = openDeviceApAndWait(ssid)
            if (!retriedState.isWifiConnectableState() && shouldTryPhoneConnectedWifiFallback(retriedState)) {
                connector.currentSession(ssid)?.let { session ->
                    Log.i(Tag, "device wifi AP retried state=$retriedState but phone is already connected to $ssid; using current phone wifi")
                    cachedWifiCredentials = DeviceWifiCredentials(ssid = ssid, password = password)
                    return@coroutineScope PreparedDeviceWifi(ssid = ssid, password = password, session = session)
                }
            }
            check(retriedState.isWifiConnectableState()) { "device wifi AP not ready: $retriedState" }
        }
        cachedWifiCredentials = DeviceWifiCredentials(ssid = ssid, password = password)
        PreparedDeviceWifi(ssid = ssid, password = password)
    }

    private suspend fun currentPhoneDeviceWifiSession(): PreparedDeviceWifi? {
        val ssid = connector.currentWifiSsid()
            ?.takeIf { it.isLikelyDeviceWifiHotspotSsid() }
            ?: return null
        val password = cachedWifiCredentials
            ?.takeIf { it.ssid == ssid }
            ?.password
            ?: DefaultDeviceWifiPassword
        val session = connector.currentSession(ssid) ?: return null
        cachedWifiCredentials = DeviceWifiCredentials(ssid = ssid, password = password)
        return PreparedDeviceWifi(ssid = ssid, password = password, session = session)
    }

    private fun downloadRemoteFile(
        remote: UteWifiRemoteFile,
        targetDirectory: File,
        session: DeviceWifiSession
    ): File {
        val target = remote.localTarget(targetDirectory).also { it.parentFile?.mkdirs() }
        val temp = File(target.parentFile ?: targetDirectory, "${target.name}.part")
        val client = session.downloadHttpClient()
        var lastFailure: String? = null
        remote.downloadUrls.distinct().forEach { url ->
            val response = runCatching {
                client.newCall(Request.Builder().url(url).build()).execute()
            }.getOrElse {
                lastFailure = "$url -> ${it.downloadFailureSummary()}"
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
                runCatching { temp.delete() }
                val copied = runCatching {
                    temp.outputStream().use { output ->
                        body.byteStream().copyTo(output)
                    }
                    temp.length()
                }.getOrElse { throwable ->
                    runCatching { temp.delete() }
                    lastFailure = "$url -> ${throwable.message}"
                    return@use
                }
                val expectedSize = remote.sizeBytes ?: body.contentLength().takeIf { length -> length > 0L }
                val sizeError = validateDownloadedFileSize(copied, expectedSize)
                if (sizeError != null) {
                    runCatching { temp.delete() }
                    lastFailure = "$url -> $sizeError"
                    return@use
                }
                if (target.exists() && !target.delete()) {
                    runCatching { temp.delete() }
                    lastFailure = "$url -> unable to replace existing file"
                    return@use
                }
                if (!temp.renameTo(target)) {
                    runCatching { temp.delete() }
                    lastFailure = "$url -> unable to finalize download"
                    return@use
                }
                Log.i(Tag, "downloaded wifi media ${remote.name} bytes=$copied url=$url target=${target.absolutePath}")
                return target
            }
        }
        runCatching { temp.delete() }
        error("wifi media download failed: ${lastFailure ?: remote.url}")
    }

    private fun Throwable.downloadFailureSummary(): String =
        "${this::class.java.simpleName}${message?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}"

    private fun Throwable.isWifiSwitchRejected(): Boolean =
        message.orEmpty().contains("device wifi switch rejected", ignoreCase = true)

    private suspend fun applyGloryViewWifiWarmup(
        ssid: String,
        password: String,
        configureWifiCredentials: Boolean
    ) {
        var warmupTimedOut = false
        withContext(Dispatchers.IO) {
            runCatching { bridge.client.openOrCloseNotify(true) }
            if (configureWifiCredentials) {
                runCatching { bridge.connection.smartSetDeviceWiFiSSID(ssid) }
                    .onSuccess {
                        Log.i(Tag, "smartSetDeviceWiFiSSID success=${it.isSuccess},error=${it.errorCode}")
                        warmupTimedOut = !it.isSuccess && it.errorCode == SdkRequestTimeoutErrorCode
                    }
                    .onFailure { Log.w(Tag, "smartSetDeviceWiFiSSID failed: ${it.message}") }
                if (warmupTimedOut) {
                    Log.w(Tag, "skip remaining wifi warmup because smartSetDeviceWiFiSSID returned timeout")
                } else {
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
                }
            } else {
                Log.i(Tag, "skip wifi credential warmup because SDK ssid was blank; ssidCandidate=$ssid")
            }
            if (configureWifiCredentials && !warmupTimedOut) {
                runCatching { bridge.connection.getGlassesInfo() }
                    .onSuccess { response ->
                        val store = response.data?.glassesStoreInfo
                        Log.i(
                            Tag,
                            "getGlassesInfo success=${response.isSuccess},error=${response.errorCode},state=${response.data?.state},store=${store.toStoreSummary()}"
                        )
                    }
                    .onFailure { Log.w(Tag, "getGlassesInfo failed: ${it.message}") }
            } else if (warmupTimedOut) {
                Log.i(Tag, "skip glasses info warmup because wifi warmup timed out")
            } else {
                Log.i(Tag, "skip glasses info warmup because SDK ssid was blank")
            }
            if (shouldSendMediaSyncCompletedBeforeWifiOpen()) {
                runCatching { bridge.connection.notifyMediaSyncCompleted() }
                    .onSuccess { Log.i(Tag, "notifyMediaSyncCompleted success=${it.isSuccess},error=${it.errorCode},data=${it.data}") }
                    .onFailure { Log.w(Tag, "notifyMediaSyncCompleted failed: ${it.message}") }
            }
        }
        if (configureWifiCredentials && !warmupTimedOut) {
            runCatching { UteSmartAuthWarmup(bridge).run(GloryViewAuthWarmupMillis) }
                .onSuccess { Log.i(Tag, "smart auth warmup $it") }
                .onFailure { Log.w(Tag, "smart auth warmup failed: ${it.message}") }
        } else if (warmupTimedOut) {
            Log.i(Tag, "skip smart auth warmup because wifi warmup timed out")
        } else {
            Log.i(Tag, "skip smart auth warmup because SDK ssid was blank")
        }
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
            Log.i(Tag, "smartSetDeviceWiFiSwitch enable success=${response.isSuccess},error=${response.errorCode},data=${response.data}")
            check(isWifiSwitchAccepted(enabled = true, responseSuccess = response.isSuccess, responseData = response.data)) {
                "device wifi switch rejected: error=${response.errorCode},data=${response.data}"
            }
        }
        val polled = waitForDeviceApReady()
        val notified = if (polled.isWifiConnectableState()) {
            withTimeoutOrNull(WifiNotifyAfterPollMillis) { notifyReady.await() }
                .also { if (it == null) notifyReady.cancel() }
        } else {
            notifyReady.await()
        }
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
        val discovered = linkedMapOf<String, UteWifiRemoteFile>()
        val deadline = System.currentTimeMillis() + HttpMediaServiceReadyTimeoutMillis
        var serviceResponded = false
        var lastFailure: String? = null
        do {
            val result = probeMediaFiles(session)
            serviceResponded = serviceResponded || result.serviceResponded
            lastFailure = result.lastFailure ?: lastFailure
            result.files.forEach { file -> discovered.putIfAbsent(file.id, file) }
            if (discovered.isNotEmpty()) return@withContext discovered.values.toList()
            delay(HttpMediaServicePollMillis)
        } while (System.currentTimeMillis() < deadline)
        if (!serviceResponded) {
            error("device media http service unavailable: ${lastFailure ?: "no response"}")
        }
        discovered.values.toList()
    }

    private fun probeMediaFiles(session: DeviceWifiSession): MediaProbeResult {
        val client = session.httpClient()
        val discovered = linkedMapOf<String, UteWifiRemoteFile>()
        val queuedDirectories = ArrayDeque<String>()
        val visitedUrls = linkedSetOf<String>()
        var serviceResponded = false
        var lastFailure: String? = null
        fun requestAndParse(url: String) {
            if (discovered.size >= MaxDiscoveredFiles) return
            if (!visitedUrls.add(url)) return
            val responseBody = runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code in 400..499) serviceResponded = true
                        lastFailure = "$url -> ${response.code}"
                        return@use null
                    }
                    serviceResponded = true
                    response.body?.string()
                }
            }.getOrElse {
                lastFailure = "$url -> ${it.message}"
                null
            } ?: return
            val parsedFiles = UteWifiMediaParser.parseRemoteFiles(responseBody, url)
            val directoryLinks = if (parsedFiles.isEmpty()) {
                UteWifiMediaParser.parseDirectoryLinks(responseBody, url)
            } else {
                emptyList()
            }
            if (parsedFiles.isNotEmpty() || directoryLinks.isNotEmpty()) {
                Log.i(
                    Tag,
                    "device media response url=$url bytes=${responseBody.length} files=${parsedFiles.size} dirs=${directoryLinks.size}"
                )
            }
            parsedFiles.forEach { file ->
                discovered.putIfAbsent(file.id, file)
            }
            if (parsedFiles.isEmpty()) {
                directoryLinks
                    .take(DirectoryProbeLimit)
                    .forEach { directoryUrl ->
                        if (directoryUrl !in visitedUrls && queuedDirectories.size < DirectoryProbeLimit) {
                            queuedDirectories += directoryUrl
                        }
                    }
            }
        }
        session.probeUrls().take(FileProbeRequestLimit).forEach { url ->
            requestAndParse(url)
        }
        while (queuedDirectories.isNotEmpty() && discovered.size < MaxDiscoveredFiles && visitedUrls.size < FileProbeRequestLimit + DirectoryProbeLimit) {
            requestAndParse(queuedDirectories.removeFirst())
        }
        if (discovered.isEmpty()) {
            Log.i(
                Tag,
                "device media probe completed empty visited=${visitedUrls.size},queued=${queuedDirectories.size},serviceResponded=$serviceResponded,lastFailure=${lastFailure.orEmpty()}"
            )
        }
        return MediaProbeResult(discovered.values.toList(), serviceResponded, lastFailure)
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

    private fun DeviceWifiSession.downloadHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(HttpDownloadConnectTimeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(HttpDownloadReadTimeoutMillis, TimeUnit.MILLISECONDS)
            .writeTimeout(HttpDownloadWriteTimeoutMillis, TimeUnit.MILLISECONDS)
            .callTimeout(HttpDownloadCallTimeoutMillis, TimeUnit.MILLISECONDS)
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
        hosts += UteWifiProbeCatalog.DeviceApFallbackHosts
        val linkHostSet = hosts.toSet()
        hosts += UteWifiProbeCatalog.LanGatewayFallbackHosts.filter { it in linkHostSet }
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
        const val WifiApEnableRetrySettleMillis = 3_000L
        const val WifiOpenSuccessSettleMillis = 8_000L
        const val WifiApPollMillis = 1_500L
        const val WifiNotifyAfterPollMillis = 1_000L
        const val GloryViewAuthWarmupMillis = 2_800L
        const val GloryViewRecordingDurationSeconds = 24 * 60 * 60
        const val GloryViewVideoWidth = 240
        const val GloryViewVideoHeight = 0
        const val GloryViewVideoFrameRate = 16
        const val HttpConnectTimeoutMillis = 450L
        const val HttpReadTimeoutMillis = 900L
        const val HttpCallTimeoutMillis = 1_200L
        const val HttpDownloadConnectTimeoutMillis = 5_000L
        const val HttpDownloadReadTimeoutMillis = 30_000L
        const val HttpDownloadWriteTimeoutMillis = 30_000L
        const val HttpDownloadCallTimeoutMillis = 45_000L
        const val HttpMediaServiceReadyTimeoutMillis = 30_000L
        const val HttpMediaServicePollMillis = 1_000L
        const val MaxDiscoveredFiles = 200
        const val ProbeHostLimit = 4
        const val DiagnosticProbeRequestLimit = 48
        const val FileProbeRequestLimit = 128
        const val DirectoryProbeLimit = 32
        const val DiagnosticHitLimit = 20
        const val SdkRequestTimeoutErrorCode = 408

    }
}

private data class DeviceWifiCredentials(
    val ssid: String,
    val password: String
)

private data class MediaProbeResult(
    val files: List<UteWifiRemoteFile>,
    val serviceResponded: Boolean,
    val lastFailure: String?
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
    currentPhoneWifiOnly

internal fun shouldShortCircuitSdkWifiOpenForCurrentHotspot(currentPhoneWifiOnly: Boolean): Boolean =
    currentPhoneWifiOnly

internal fun shouldUseCurrentHotspotBeforeSdkWifiOpen(): Boolean = true

internal fun shouldUseCachedWifiDownloadPath(currentPhoneWifiOnly: Boolean, phoneAlreadyConnected: Boolean): Boolean =
    currentPhoneWifiOnly || phoneAlreadyConnected

internal fun shouldSendMediaSyncCompletedBeforeWifiOpen(): Boolean = false

internal fun shouldConfigureGloryViewWifiWarmup(sdkSsid: String): Boolean =
    sdkSsid.isNotBlank()

internal fun String.isLikelyDeviceWifiHotspotSsid(): Boolean =
    startsWith("UTE_", ignoreCase = true) ||
        startsWith("GLORY_", ignoreCase = true) ||
        startsWith("AI_GLASS", ignoreCase = true)

internal fun fallbackDeviceWifiSsidForKnownGlasses(deviceName: String, deviceAddress: String?): String? {
    if (!PatrolDeviceNameClassifier.isKnownGlassesName(deviceName)) return null
    val suffix = deviceAddress?.bluetoothAddressWifiSuffix()
        ?: deviceName.lastHex4Suffix()
        ?: return null
    return "UTE_$suffix"
}

private fun String.bluetoothAddressWifiSuffix(): String? {
    val octets = trim()
        .split(':', '-')
        .filter { part -> part.length == 2 && part.all { it.isDigit() || it.uppercaseChar() in 'A'..'F' } }
    if (octets.size >= 2) {
        return (octets[octets.lastIndex - 1] + octets[octets.lastIndex]).uppercase()
    }
    return lastHex4Suffix()
}

private fun String.lastHex4Suffix(): String? =
    Regex("(?i)([0-9a-f]{4})(?!.*[0-9a-f])")
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.uppercase()

internal fun isWifiSwitchAccepted(enabled: Boolean, responseSuccess: Boolean, responseData: Boolean?): Boolean {
    if (!responseSuccess) return false
    return responseData != false
}

internal fun validateDownloadedFileSize(actualBytes: Long, expectedBytes: Long?): String? {
    if (expectedBytes == null || expectedBytes <= 0L) return null
    if (actualBytes == expectedBytes) return null
    return "download size mismatch actual=$actualBytes expected=$expectedBytes"
}
