package com.patrollink.debug

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import com.patrollink.data.RuntimeConfigStore
import com.patrollink.data.RuntimeTokenStore
import com.patrollink.data.ServiceFactory
import com.patrollink.data.local.WorkManagerBackgroundTaskGateway
import com.patrollink.data.ute.UteSdkBridge
import com.patrollink.data.ute.UteWifiMediaClient
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.EmptyAppState
import com.patrollink.domain.FirmwareDeviceMetadata
import com.patrollink.domain.FirmwareGateway
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaKind
import com.patrollink.domain.OfflineSyncEngine
import com.patrollink.domain.PatrolCoordinator
import com.patrollink.domain.ScannedDevice
import com.patrollink.domain.TransferStatus
import com.patrollink.domain.TransferTarget
import com.yc.nadalsdk.bean.DeviceBt3StateInfo
import com.yc.nadalsdk.bean.DeviceInfoRequest
import com.yc.nadalsdk.bean.HonorAccountConfig
import com.yc.nadalsdk.bean.Notify
import com.yc.nadalsdk.bean.Response
import com.yc.nadalsdk.bean.recorder.AudioRecordStopInfo
import com.yc.nadalsdk.bean.recorder.RequestAudioRecordFileInfo
import com.yc.nadalsdk.bean.smart.DeviceResetConfig
import com.yc.nadalsdk.bean.smart.GlassesInfo
import com.yc.nadalsdk.bean.smart.GlassesStateInfo
import com.yc.nadalsdk.bean.smart.HeadsetAccountConfig
import com.yc.nadalsdk.bean.smart.SmartAudioDataInfo
import com.yc.nadalsdk.bean.smart.SmartAuthorizationCode
import com.yc.nadalsdk.bean.smart.SmartImageDataInfo
import com.yc.nadalsdk.bean.smart.VideoParametersInfo
import com.yc.nadalsdk.ble.open.DeviceModeJX
import com.yc.nadalsdk.constants.NotifyType
import com.yc.nadalsdk.constants.recorder.AudioRecordResult
import com.yc.nadalsdk.constants.smart.GlassesHeadsetRecordingState
import com.yc.nadalsdk.constants.smart.GlassesRecordDirection
import com.yc.nadalsdk.constants.smart.GlassesState
import java.io.File
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.lang.reflect.Modifier
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class SmokeTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!SmokeTestRunGate.tryStart()) {
            Log.i(SmokeTestRunner.Tag, "SMOKE_ALREADY_RUNNING")
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = SmokeTestRunner.run(context.applicationContext, intent)
                Log.i(SmokeTestRunner.Tag, "SMOKE_REPORT ${file.absolutePath}")
            } finally {
                SmokeTestRunGate.finish()
                pending.finish()
            }
        }
    }
}

class SmokeTestActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            text = "Patrol smoke test running..."
            gravity = Gravity.CENTER
            textSize = 18f
        }
        setContentView(status)
        runSmoke(Intent(intent))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        runSmoke(Intent(intent))
    }

    private fun runSmoke(launchIntent: Intent) {
        if (!SmokeTestRunGate.tryStart()) {
            status.text = "Patrol smoke test already running or just finished"
            return
        }
        status.text = "Patrol smoke test running..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = SmokeTestRunner.run(applicationContext, launchIntent)
                Log.i(SmokeTestRunner.Tag, "SMOKE_REPORT ${file.absolutePath}")
                runOnUiThread {
                    status.text = "Patrol smoke test finished\n${file.absolutePath}"
                }
            } finally {
                SmokeTestRunGate.finish()
            }
        }
    }
}

object SmokePairingAccountResolver {
    fun resolve(override: String, currentUserBadge: String, loginAccount: String): String =
        override.trim()
            .ifBlank { currentUserBadge.trim() }
            .ifBlank { loginAccount.trim() }
            .ifBlank { "SMOKE_OPERATOR" }
}

object SmokeDangerousActionGuard {
    fun canClearDeviceAccount(enabled: Boolean, confirmation: String): Boolean =
        enabled && confirmation == ClearDeviceAccountConfirmation

    fun canFactoryResetDevice(target: String, confirmation: String): Boolean =
        target.normalizedFactoryResetTarget() != null && confirmation == FactoryResetDeviceConfirmation

    fun shouldStopAfterClearAccountAttempt(enabled: Boolean): Boolean = enabled

    fun shouldStopAfterFactoryResetAttempt(target: String): Boolean = target.isNotBlank()

    const val ClearDeviceAccountConfirmation = "CLEAR_DEVICE_ACCOUNT"
    const val FactoryResetDeviceConfirmation = "FACTORY_RESET_DEVICE"
}

private fun String.normalizedFactoryResetTarget(): String? =
    trim().lowercase(Locale.US).takeIf { it == "headset" || it == "glasses" }

private fun String.toSmokeMediaKindOrNull(): MediaKind? =
    when (trim().lowercase(Locale.US)) {
        "photo", "image", "picture", "jpg", "jpeg", "png" -> MediaKind.Photo
        "video", "mp4", "mov" -> MediaKind.Video
        "audio", "record", "recording", "voice", "opus", "wav", "amr", "aac", "pcm" -> MediaKind.Audio
        else -> null
    }

data class SmokeWifiPreflightOptions(
    val requestPairingBeforeWifi: Boolean,
    val probeAccountBeforeWifi: Boolean
) {
    val enabled: Boolean = requestPairingBeforeWifi || probeAccountBeforeWifi
}

data class SmokeHeadsetAiRecorderOptions(
    val forceCommands: Boolean
) {
    fun shouldRun(supported: Boolean): Boolean = supported || forceCommands
}

data class SmokeDirectWifiSwitchOptions(
    val noAccountGuard: Boolean
)

data class SmokeWifiMediaSyncOptions(
    val downloadFirst: Boolean,
    val downloadKind: MediaKind? = null,
    val currentPhoneWifiOnly: Boolean = false,
    val downloadLimit: Int = 1
) {
    fun selectDownloadCandidate(files: List<MediaFile>): MediaFile? =
        if (downloadKind != null) {
            files.firstOrNull { it.kind == downloadKind }
        } else {
            files.firstOrNull()
        }

    fun selectDownloadCandidates(files: List<MediaFile>): List<MediaFile> =
        if (downloadKind != null) {
            files.filter { it.kind == downloadKind }.take(downloadLimit)
        } else {
            files.take(downloadLimit)
        }
}

private object SmokeTestRunner {
    suspend fun run(context: Context, intent: Intent): File {
        val report = SmokeReport(context)
        report.step("RUN_START", "timestamp=${Timestamp.format(Date())}")
        try {
            execute(context, intent, report)
        } catch (throwable: Throwable) {
            report.fail("SMOKE_FATAL", throwable.message.orEmpty().ifBlank { throwable::class.java.simpleName })
        }
        return report.writeTo(context)
    }

    private suspend fun execute(context: Context, intent: Intent, report: SmokeReport) {
        val account = intent.getStringExtra("account").orEmpty()
        val password = intent.getStringExtra("password").orEmpty()
        val runCommands = intent.getBooleanExtra("commands", true)
        val enableWifi = intent.getBooleanExtra("wifi", false)
        val wifiProbeOnly = intent.getBooleanExtra("wifiProbeOnly", false)
        val localMediaOnly = intent.getBooleanExtra("localMediaOnly", false)
        val currentWifiMediaOnly = intent.getBooleanExtra("currentWifiMediaOnly", false)
        val wifiProbeMillis = intent.getLongExtra("wifiProbeMillis", WifiProbeOnlyDefaultMillis)
        val runAuth = intent.getBooleanExtra("auth", true)
        val skipLogin = intent.getBooleanExtra("skipLogin", false)
        val runPairing = intent.getBooleanExtra("pairing", false)
        val runAccountProbe = intent.getBooleanExtra("accountProbe", false)
        val runMediaCommandMatrix = intent.getBooleanExtra("mediaCommandMatrix", false)
        val runMediaCommandMatrixAudio = intent.getBooleanExtra("mediaCommandMatrixAudio", false)
        val wifiPreflightOptions = SmokeWifiPreflightOptions(
            requestPairingBeforeWifi = intent.getBooleanExtra("preWifiPairing", false),
            probeAccountBeforeWifi = intent.getBooleanExtra("preWifiAccountProbe", false)
        )
        val runBt3Probe = intent.getBooleanExtra("bt3", false)
        val clearDeviceAccount = intent.getBooleanExtra("clearDeviceAccount", false)
        val clearDeviceAccountConfirm = intent.getStringExtra("clearDeviceAccountConfirm").orEmpty()
        val factoryResetTarget = intent.getStringExtra("factoryResetTarget").orEmpty()
        val factoryResetConfirm = intent.getStringExtra("factoryResetConfirm").orEmpty()
        val authCode = intent.getStringExtra("authCode").orEmpty()
        val pairingAccountOverride = intent.getStringExtra("pairingAccountId").orEmpty()
        val aiRecorderOptions = SmokeHeadsetAiRecorderOptions(
            forceCommands = intent.getBooleanExtra("forceAiRecorder", false)
        )
        val directWifiSwitchOptions = SmokeDirectWifiSwitchOptions(
            noAccountGuard = intent.getBooleanExtra("directWifiSwitchNoAccountGuard", false)
        )
        val wifiMediaSyncOptions = SmokeWifiMediaSyncOptions(
            downloadFirst = intent.getBooleanExtra("wifiDownloadFirst", false),
            downloadKind = intent.getStringExtra("wifiDownloadKind").orEmpty().toSmokeMediaKindOrNull(),
            currentPhoneWifiOnly = intent.getBooleanExtra("wifiMediaOnly", false),
            downloadLimit = intent.getIntExtra("wifiDownloadLimit", 1).coerceIn(1, 50)
        )
        val commandHoldMillis = intent.getLongExtra("commandHoldMillis", CommandHoldMillis)
            .coerceIn(MinCommandHoldMillis, MaxCommandHoldMillis)
        report.step(
            "WIFI_MEDIA_OPTIONS",
            "downloadFirst=${wifiMediaSyncOptions.downloadFirst},downloadKind=${wifiMediaSyncOptions.downloadKind ?: "any"},currentPhoneWifiOnly=${wifiMediaSyncOptions.currentPhoneWifiOnly},downloadLimit=${wifiMediaSyncOptions.downloadLimit}"
        )
        report.step("COMMAND_OPTIONS", "holdMillis=$commandHoldMillis")
        val targetDeviceId = intent.getStringExtra("targetDeviceId").orEmpty()
        val targetDeviceName = intent.getStringExtra("targetDeviceName").orEmpty()
        val configStore = RuntimeConfigStore(context)
        val overrideRestBaseUrl = intent.getStringExtra("restBaseUrl").orEmpty()
        val overrideWebSocketUrl = intent.getStringExtra("webSocketUrl").orEmpty()
        if (overrideRestBaseUrl.isNotBlank() || overrideWebSocketUrl.isNotBlank()) {
            val saved = configStore.saveBackendSettings(overrideRestBaseUrl, overrideWebSocketUrl)
            report.step("CONFIG_OVERRIDE", "rest=${saved.restBaseUrl},websocket=${saved.webSocketUrl}")
        }
        val config = configStore.read()
        report.step("CONFIG", "rest=${config.restBaseUrl}, realBle=${config.useRealBle}")
        if (intent.getBooleanExtra("configOnly", false)) {
            report.step("CONFIG_ONLY", "done")
            return
        }
        if (wifiProbeOnly) {
            runWifiNetworkProbeLoop(report, context, wifiProbeMillis)
            return
        }
        val bridge = UteSdkBridge(context)
        val tokenStore = RuntimeTokenStore(context.applicationContext)
        val emptyState = EmptyAppState.create()
        val coordinator = ServiceFactory.createRuntimeCoordinator(
            context = context,
            config = config,
            tokenProvider = tokenStore::token,
            operatorIdProvider = { account.ifBlank { "SMOKE_OPERATOR" } },
            pairingAccountIdProvider = tokenStore::pairingAccountId,
            fallbackState = emptyState,
            sharedUteBridge = bridge
        )
        val firmwareGateway = ServiceFactory.createFirmwareGateway(
            context = context,
            config = config,
            sharedUteBridge = bridge,
            tokenProvider = tokenStore::token,
            operatorIdProvider = { account.ifBlank { "SMOKE_OPERATOR" } }
        )

        if (skipLogin) {
            report.step("LOGIN", "skipped")
            val pairingAccountId = SmokePairingAccountResolver.resolve(
                override = pairingAccountOverride,
                currentUserBadge = "",
                loginAccount = account.ifBlank { "SMOKE_OPERATOR" }
            )
            tokenStore.updatePairingUsername(pairingAccountId)
            report.step("PAIRING_ACCOUNT", pairingAccountId)
            report.step("CURRENT_USER", "skipped")
        } else {
            val session = runStep(report, "LOGIN") {
                coordinator.loginAndStartSession(account, password)
            } ?: return
            tokenStore.update(session)
            val currentUser = runStep(report, "CURRENT_USER") { coordinator.currentUser() }
            val pairingAccountId = SmokePairingAccountResolver.resolve(
                override = pairingAccountOverride,
                currentUserBadge = currentUser?.badgeNo.orEmpty(),
                loginAccount = account
            )
            tokenStore.updatePairingUsername(pairingAccountId)
            report.step("PAIRING_ACCOUNT", pairingAccountId)
        }
        if (localMediaOnly) {
            runLocalMediaOnlyChecks(report, coordinator)
            return
        }
        if (currentWifiMediaOnly) {
            runCurrentWifiMediaOnlyChecks(report, context, coordinator, wifiMediaSyncOptions)
            return
        }
        val devices = scanDevices(report, coordinator)
        val selected = devices.selectedSmokeDevice(targetDeviceId, targetDeviceName)
        if (selected == null) {
            report.fail("SCAN_BIND", "未扫描到可绑定设备")
            return
        }
        report.step("SELECT_DEVICE", "${selected.name} ${selected.id} type=${selected.type}")
        val bound = runStep(report, "BIND_DEVICE") { coordinator.bindDevice(selected.id) } ?: return
        if (bound.online) {
            report.pass("BIND_DEVICE_ONLINE", bound.summary())
        } else {
            report.fail("BIND_DEVICE_ONLINE", "control address did not come online: ${bound.summary()}")
        }
        report.step("DEVICE_STATUS", bound.summary())
        if (!bound.online) return
        runDeviceIdentityDiagnostics(report, bridge)
        if (runDangerousDeviceAccountActions(report, bridge, clearDeviceAccount, clearDeviceAccountConfirm, factoryResetTarget, factoryResetConfirm)) {
            return
        }
        if (runCommands) {
            collectInterestingNotifies(report, bridge, "COMMAND_NOTIFIES", CommandNotifyProbeMillis) {
                runDeviceCommands(report, coordinator, bridge, bound, commandHoldMillis)
            }
        }
        if (runMediaCommandMatrix) {
            runDirectMediaCommandMatrix(report, bridge, runAudio = runMediaCommandMatrixAudio)
        }
        if (enableWifi && wifiPreflightOptions.enabled) {
            runPairingAccountProbe(
                report = report,
                bridge = bridge,
                pairingAccountId = tokenStore.pairingAccountId(),
                runPairing = wifiPreflightOptions.requestPairingBeforeWifi,
                runAccountProbe = wifiPreflightOptions.probeAccountBeforeWifi,
                labelPrefix = "PRE_WIFI"
            )
        }
        runDeviceCapabilities(report, context, config, bridge, bound, enableWifi, directWifiSwitchOptions, wifiMediaSyncOptions, tokenStore.pairingAccountId(), coordinator)
        if (bound.type == com.patrollink.domain.DeviceType.Headset) {
            runHeadsetDiagnostics(report, bridge, runBt3Probe)
            runHeadsetAiRecorderCommands(report, bridge, aiRecorderOptions)
        }
        runMediaChecks(report, coordinator, bridge)
        runFirmwareCheck(report, firmwareGateway, bound)
        runControlChannelDiagnostics(report, bridge, tokenStore.pairingAccountId(), runAuth, runPairing, runAccountProbe, authCode)
    }

    private suspend fun runDangerousDeviceAccountActions(
        report: SmokeReport,
        bridge: UteSdkBridge,
        clearDeviceAccount: Boolean,
        clearDeviceAccountConfirm: String,
        factoryResetTarget: String,
        factoryResetConfirm: String
    ): Boolean {
        if (!clearDeviceAccount && factoryResetTarget.isBlank()) {
            report.step(
                "SDK_CLEAR_ACCOUNT",
                "skipped; pass --ez clearDeviceAccount true --es clearDeviceAccountConfirm ${SmokeDangerousActionGuard.ClearDeviceAccountConfirmation} to clear device account"
            )
            report.step(
                "SDK_FACTORY_RESET",
                "skipped; pass --es factoryResetTarget headset|glasses --es factoryResetConfirm ${SmokeDangerousActionGuard.FactoryResetDeviceConfirmation} to factory reset one module"
            )
            return false
        }
        if (clearDeviceAccount && factoryResetTarget.isNotBlank()) {
            report.fail("SDK_DANGEROUS_ACTION", "refused; choose only one of clearDeviceAccount or factoryResetTarget")
            return true
        }
        if (!SmokeDangerousActionGuard.canClearDeviceAccount(clearDeviceAccount, clearDeviceAccountConfirm)) {
            if (factoryResetTarget.isNotBlank()) {
                return runFactoryResetAction(report, bridge, factoryResetTarget, factoryResetConfirm)
            }
            report.fail(
                "SDK_CLEAR_ACCOUNT",
                "refused; confirmation must equal ${SmokeDangerousActionGuard.ClearDeviceAccountConfirmation}"
            )
            return SmokeDangerousActionGuard.shouldStopAfterClearAccountAttempt(clearDeviceAccount)
        }
        runStep(report, "SDK_CLEAR_ACCOUNT_ENABLE_NOTIFY") {
            bridge.client.openOrCloseNotify(true)
            "enabled=true"
        }
        delay(NotifyCollectorWarmupMillis)
        runStep(report, "SDK_CLEAR_ACCOUNT") {
            val response = bridge.connection.clearAccountID()
            check(response.isSuccess) { response.toSmokeSummary() }
            response.toSmokeSummary()
        }
        report.step("SDK_CLEAR_ACCOUNT_NEXT", "reconnect and pair PatrolLink before running more smoke steps")
        return SmokeDangerousActionGuard.shouldStopAfterClearAccountAttempt(clearDeviceAccount)
    }

    private suspend fun runFactoryResetAction(
        report: SmokeReport,
        bridge: UteSdkBridge,
        factoryResetTarget: String,
        factoryResetConfirm: String
    ): Boolean {
        val target = factoryResetTarget.normalizedFactoryResetTarget()
        if (target == null || !SmokeDangerousActionGuard.canFactoryResetDevice(factoryResetTarget, factoryResetConfirm)) {
            report.fail(
                "SDK_FACTORY_RESET",
                "refused; target must be headset|glasses and confirmation must equal ${SmokeDangerousActionGuard.FactoryResetDeviceConfirmation}"
            )
            return SmokeDangerousActionGuard.shouldStopAfterFactoryResetAttempt(factoryResetTarget)
        }
        runStep(report, "SDK_FACTORY_RESET_ENABLE_NOTIFY") {
            bridge.client.openOrCloseNotify(true)
            "enabled=true"
        }
        delay(NotifyCollectorWarmupMillis)
        runStep(report, "SDK_FACTORY_RESET") {
            val config = DeviceResetConfig().apply {
                this.config = DeviceResetConfig.FACTORY_RESET_AND_RESTART
            }
            val response = if (target == "headset") {
                bridge.connection.headsetDeviceResetOperation(config)
            } else {
                bridge.connection.glassesDeviceResetOperation(config)
            }
            check(response.isSuccess) { response.toSmokeSummary() }
            "target=$target ${response.toSmokeSummary()}"
        }
        report.step("SDK_FACTORY_RESET_NEXT", "wait for device restart, then rescan and pair PatrolLink before running more smoke steps")
        return SmokeDangerousActionGuard.shouldStopAfterFactoryResetAttempt(factoryResetTarget)
    }

    private suspend fun scanDevices(report: SmokeReport, coordinator: PatrolCoordinator): List<ScannedDevice> {
        val collected = linkedMapOf<String, ScannedDevice>()
        withTimeoutOrNull(ScanTimeoutMillis) {
            coordinator.scanDevices().collect { list ->
                list.forEach { collected[it.id] = it }
            }
        }
        report.step("SCAN_DEVICES", "count=${collected.size}; ${collected.values.joinToString { "${it.name}/${it.id}/${it.type}/${it.serviceUuid}" }}")
        return collected.values.toList()
    }

    private suspend fun runControlChannelDiagnostics(
        report: SmokeReport,
        bridge: UteSdkBridge,
        pairingAccountId: String,
        runAuth: Boolean,
        runPairing: Boolean,
        runAccountProbe: Boolean,
        authCode: String
    ) {
        runStep(report, "SDK_CONNECTION_STATE") {
            "isConnected=${bridge.client.isConnected},deviceAddress=${bridge.client.deviceAddress},deviceName=${bridge.client.deviceName},platform=${bridge.client.devicePlatform}"
        }
        runStep(report, "SDK_ENABLE_NOTIFY") {
            bridge.client.openOrCloseNotify(true)
            "enabled=true"
        }
        runPairingAccountProbe(report, bridge, pairingAccountId, runPairing, runAccountProbe, labelPrefix = "")
        if (runAuth) {
            collectInterestingNotifies(report, bridge, "SMART_AUTH_NOTIFIES", SmartAuthNotifyProbeMillis) {
                runStep(report, "SMART_START_AUTHENTICATION") {
                    bridge.connection.startAuthentication().toSmokeSummary()
                }
                if (authCode.isNotBlank()) {
                    runStep(report, "SMART_SET_AUTH_CODE") {
                        bridge.connection.smartSetAuthorizationCode(SmartAuthorizationCode().apply {
                            type = SmartAuthorizationCode.ONLINE
                            authorizationCode = authCode
                        }).toSmokeSummary()
                    }
                }
            }
        }
        runStep(report, "SDK_FEATURE_FLAGS") {
            withTimeoutOrNull(SdkFeatureFlagsTimeoutMillis) {
                withContext(Dispatchers.IO) { sdkFeatureFlags() }
            } ?: "timeout"
        }
    }

    private suspend fun runPairingAccountProbe(
        report: SmokeReport,
        bridge: UteSdkBridge,
        pairingAccountId: String,
        runPairing: Boolean,
        runAccountProbe: Boolean,
        labelPrefix: String
    ) {
        val prefix = labelPrefix.takeIf { it.isNotBlank() }?.let { "${it}_" }.orEmpty()
        collectInterestingNotifies(report, bridge, "${prefix}PAIRING_NOTIFIES", PairingNotifyProbeMillis) {
            if (runPairing) {
                runStep(report, "${prefix}SDK_REQUEST_PAIRING") {
                    val response = bridge.connection.requestDevicePairing(1)
                    "success=${response.isSuccess},error=${response.errorCode},paired=${response.data?.pairedState}"
                }
            } else {
                report.step("${prefix}SDK_REQUEST_PAIRING", "skipped; pass --ez pairing true to probe, current headset rejects this path with 408")
            }
            if (runAccountProbe) {
                runStep(report, "${prefix}SDK_SET_HEADSET_ACCOUNT") {
                    val response = bridge.connection.setHeadsetAccount(HeadsetAccountConfig().apply {
                        currentHuid = pairingAccountId
                    })
                    "success=${response.isSuccess},error=${response.errorCode},status=${response.data?.accountJudgmentStatus}"
                }
                runStep(report, "${prefix}SDK_SET_HONOR_ACCOUNT") {
                    val response = bridge.connection.setHonorAccount(HonorAccountConfig().apply {
                        currentHuid = pairingAccountId
                    })
                    "success=${response.isSuccess},error=${response.errorCode},status=${response.data?.accountJudgmentStatus}"
                }
            } else {
                report.step("${prefix}SDK_SET_ACCOUNT", "skipped; pass --ez accountProbe true to probe, this can leave the current headset in 408 state")
            }
        }
    }

    private suspend fun runDeviceCapabilities(
        report: SmokeReport,
        context: Context,
        config: com.patrollink.data.RuntimeConfig,
        bridge: UteSdkBridge,
        device: DeviceStatus,
        enableWifi: Boolean,
        directWifiSwitchOptions: SmokeDirectWifiSwitchOptions,
        wifiMediaSyncOptions: SmokeWifiMediaSyncOptions,
        pairingAccountId: String,
        coordinator: PatrolCoordinator
    ) {
        val gateway = ServiceFactory.createDeviceControlGateway(
            context = context,
            config = config,
            sharedUteBridge = bridge,
            tokenProvider = { null },
            deviceIdProvider = { device.id },
            pairingAccountIdProvider = { pairingAccountId }
        )
        runStep(report, "DEVICE_CAPABILITIES") { gateway.capabilities(device) }
        runStep(report, "READ_WIFI") { gateway.readWifi() }
        if (directWifiSwitchOptions.noAccountGuard) {
            runDirectWifiSwitchProbe(report, context, bridge, gateway)
        }
        if (enableWifi || wifiMediaSyncOptions.currentPhoneWifiOnly) {
            runStep(report, "SDK_ENABLE_NOTIFY_BEFORE_WIFI") {
                bridge.client.openOrCloseNotify(true)
                "enabled=true"
            }
            if (wifiMediaSyncOptions.currentPhoneWifiOnly) {
                runStep(report, "ENABLE_WIFI") { "skipped; wifiMediaOnly uses current phone Wi-Fi" }
            } else {
                collectInterestingNotifies(report, bridge, "WIFI_NOTIFIES", WifiNotifyProbeMillis) {
                    val wifiInfo = runCatching { bridge.connection.smartGetDeviceWiFiInfo().data }.getOrNull()
                    report.step(
                        "READ_WIFI_RAW",
                        "state=${wifiInfo?.state},ssid=${wifiInfo?.wiFiSSID.orEmpty()},passwordLen=${wifiInfo?.wiFiPassword?.length ?: 0}"
                    )
                    runStep(report, "ENABLE_WIFI") { gateway.configureWifi(enabled = true, ssid = "", password = "") }
                    val readySummary = runStep(report, "WAIT_WIFI_READY") { waitForWifiReady(gateway) }.orEmpty()
                    if (!readySummary.startsWith("ready=true") && !wifiInfo?.wiFiSSID.isNullOrBlank() && !wifiInfo?.wiFiPassword.isNullOrBlank()) {
                        runStep(report, "ENABLE_WIFI_WITH_EXISTING_CONFIG") {
                            gateway.configureWifi(
                                enabled = true,
                                ssid = wifiInfo?.wiFiSSID.orEmpty(),
                                password = wifiInfo?.wiFiPassword.orEmpty()
                            )
                        }
                        runStep(report, "WAIT_WIFI_READY_AFTER_CONFIG") { waitForWifiReady(gateway) }
                    }
                    runStep(report, "READ_WIFI_AFTER_ENABLE") { gateway.readWifi() }
                }
            }
            runWifiNetworkProbe(report, context)
            val wifiMediaClient = UteWifiMediaClient(
                context = context,
                bridge = bridge,
                pairingAccountIdProvider = { pairingAccountId }
            )
            runStep(report, "UTE_WIFI_MEDIA_DIAGNOSTICS") {
                withTimeoutOrNull(WifiMediaDiagnosticsTimeoutMillis) {
                    wifiMediaClient.diagnostics(currentPhoneWifiOnly = wifiMediaSyncOptions.currentPhoneWifiOnly)
                } ?: "timeout"
            }
            val wifiFiles = mutableListOf<MediaFile>()
            runStep(report, "UTE_WIFI_MEDIA_LIST") {
                withTimeoutOrNull(WifiMediaListSmokeTimeoutMillis) {
                    wifiMediaClient.listFiles(currentPhoneWifiOnly = wifiMediaSyncOptions.currentPhoneWifiOnly)
                        .also { files ->
                            wifiFiles.clear()
                            wifiFiles += files
                        }
                        .map { "${it.id}:${it.kind}:${it.name}:${it.size}" }
                } ?: "timeout"
            }
            runWifiMediaClientDownloadFirst(report, context, wifiMediaClient, wifiFiles, wifiMediaSyncOptions)
            runWifiMediaDownloadFirst(report, context, coordinator, wifiMediaSyncOptions)
        }
    }

    private suspend fun runWifiMediaClientDownloadFirst(
        report: SmokeReport,
        context: Context,
        client: UteWifiMediaClient,
        files: List<MediaFile>,
        options: SmokeWifiMediaSyncOptions
    ) {
        if (!options.downloadFirst) {
            report.step("UTE_WIFI_DIRECT_DOWNLOAD_FIRST", "skipped; pass --ez wifiDownloadFirst true after device media list succeeds")
            return
        }
        val first = options.selectDownloadCandidate(files)
        if (first == null) {
            report.fail("UTE_WIFI_DIRECT_DOWNLOAD_FIRST", "no listed wifi media file to download kind=${options.downloadKind ?: "any"}")
            return
        }
        runStep(report, "UTE_WIFI_DIRECT_DOWNLOAD_FIRST") {
            val directory = File(context.getExternalFilesDir(null) ?: context.filesDir, "wifi-smoke-downloads")
                .also { it.mkdirs() }
            val downloaded = withTimeoutOrNull(WifiMediaDownloadTimeoutMillis) {
                client.download(first.id, directory, currentPhoneWifiOnly = options.currentPhoneWifiOnly)
            } ?: error("direct download timeout: ${first.id}")
            "${first.id}:${downloaded.name}:${downloaded.length()}:path=${downloaded.absolutePath}"
        }
    }

    private suspend fun runWifiMediaDownloadFirst(
        report: SmokeReport,
        context: Context,
        coordinator: PatrolCoordinator,
        options: SmokeWifiMediaSyncOptions
    ) {
        if (!options.downloadFirst) {
            report.step("UTE_WIFI_MEDIA_DOWNLOAD_FIRST", "skipped; pass --ez wifiDownloadFirst true after manually connecting the phone to device hotspot")
            return
        }
        val deviceFiles = runStep(report, "UTE_WIFI_MEDIA_DOWNLOAD_CANDIDATES") {
            withTimeoutOrNull(WifiMediaListSmokeTimeoutMillis) {
                coordinator.mediaFiles(local = false)
            } ?: error("device media list timeout")
        }.orEmpty()
        val candidates = options.selectDownloadCandidates(deviceFiles)
        if (candidates.isEmpty()) {
            report.fail("UTE_WIFI_MEDIA_DOWNLOAD_FIRST", "no device media file to download kind=${options.downloadKind ?: "any"}")
            return
        }
        val downloadedIds = mutableListOf<String>()
        candidates.forEachIndexed { index, candidate ->
            val emitted = mutableListOf<String>()
            val stepName = if (index == 0) {
                "UTE_WIFI_MEDIA_DOWNLOAD_FIRST"
            } else {
                "UTE_WIFI_MEDIA_DOWNLOAD_${index + 1}"
            }
            runStep(report, stepName) {
                withTimeoutOrNull(WifiMediaDownloadTimeoutMillis) {
                    coordinator.transferMedia(candidate.id, TransferTarget.PhoneSandbox).collect { media ->
                        emitted += media.toTransferSmokeSummary()
                    }
                } ?: error("download timeout: ${candidate.id}")
                check(emitted.isNotEmpty()) { "download emitted no transfer states: ${candidate.id}" }
                downloadedIds += candidate.id
                emitted.joinToString(separator = " | ")
            }
            runStep(report, "${stepName}_LOCAL") {
                withTimeoutOrNull(MediaCheckTimeoutMillis) {
                    coordinator.mediaFiles(local = true)
                        .filter {
                            it.id == candidate.id ||
                                it.name == candidate.name ||
                                it.contentUri?.contains(candidate.name.substringBeforeLast('.')) == true
                        }
                        .map {
                            "${it.id}:${it.kind}:${it.size}:uri=${it.contentUri.orEmpty()}:status=${it.transferStatus}"
                        }
                } ?: "timeout"
            }
        }
        runStep(report, "UTE_WIFI_MEDIA_BATCH_SUMMARY") {
            check(downloadedIds.size == candidates.size) {
                "downloaded ${downloadedIds.size}/${candidates.size}: ${downloadedIds.joinToString()}"
            }
            "downloaded=${downloadedIds.size},ids=${downloadedIds.joinToString()}"
        }
        val first = candidates.first()
        runStep(report, "UTE_WIFI_MEDIA_LOCAL_AFTER_DOWNLOAD") {
            withTimeoutOrNull(MediaCheckTimeoutMillis) {
                coordinator.mediaFiles(local = true)
                    .filter {
                        it.id == first.id ||
                            it.name == first.name ||
                            it.contentUri?.contains(first.name.substringBeforeLast('.')) == true
                    }
                    .map { "${it.id}:${it.kind}:${it.size}:uri=${it.contentUri.orEmpty()}:status=${it.transferStatus}" }
            }
                ?: "timeout"
        }
        runStep(report, "UTE_BACKGROUND_UPLOAD_QUEUE_AFTER_DOWNLOAD") {
            val local = withTimeoutOrNull(MediaCheckTimeoutMillis) {
                coordinator.mediaFiles(local = true).firstOrNull {
                    it.id == first.id && !it.contentUri.isNullOrBlank()
                }
            } ?: error("downloaded local media not indexed for upload queue: ${first.id}")
            val taskGateway = WorkManagerBackgroundTaskGateway(context)
            val receipt = OfflineSyncEngine(taskGateway).enqueueEvidenceUpload(first.id, System.currentTimeMillis())
            val pending = taskGateway.pending()
                .filter { it.task.id == receipt.task.id || it.task.payloadId == first.id }
                .joinToString(separator = " | ") {
                    "${it.task.id}:${it.task.type}:payload=${it.task.payloadId}:queued=${it.queued}"
                }
            check(pending.isNotBlank()) { "queued upload task not found: ${receipt.task.id}" }
            "local=${local.id}:${local.kind}:${local.size}:uri=${local.contentUri.orEmpty()},receipt=${receipt.task.id},pending=$pending"
        }
    }

    private suspend fun runDirectWifiSwitchProbe(
        report: SmokeReport,
        context: Context,
        bridge: UteSdkBridge,
        gateway: com.patrollink.domain.DeviceControlGateway
    ) {
        runStep(report, "DIRECT_WIFI_PROBE_WARNING") {
            "debug-only probe bypasses PatrolLink account guard and calls SDK smartSetDeviceWiFiSwitch directly"
        }
        collectInterestingNotifies(report, bridge, "DIRECT_WIFI_NOTIFIES", WifiNotifyProbeMillis) {
            runStep(report, "DIRECT_WIFI_ENABLE_NOTIFY") {
                bridge.client.openOrCloseNotify(true)
                "enabled=true"
            }
            runStep(report, "DIRECT_READ_WIFI_RAW") {
                val wifiInfo = bridge.connection.smartGetDeviceWiFiInfo().data
                "state=${wifiInfo?.state},ssid=${wifiInfo?.wiFiSSID.orEmpty()},passwordLen=${wifiInfo?.wiFiPassword?.length ?: 0}"
            }
            runStep(report, "DIRECT_WIFI_SWITCH_ON_NO_ACCOUNT_GUARD") {
                val response = bridge.connection.smartSetDeviceWiFiSwitch(true)
                check(response.isSuccess || response.data == true) {
                    "success=${response.isSuccess},error=${response.errorCode},data=${response.data}"
                }
                "success=${response.isSuccess},error=${response.errorCode},data=${response.data}"
            }
            runStep(report, "DIRECT_WAIT_WIFI_READY_NO_ACCOUNT_GUARD") { waitForWifiReady(gateway) }
            runStep(report, "DIRECT_READ_WIFI_AFTER_SWITCH") { gateway.readWifi() }
        }
        runWifiNetworkProbe(report, context)
        runStep(report, "DIRECT_WIFI_SWITCH_OFF_NO_ACCOUNT_GUARD") {
            val response = bridge.connection.smartSetDeviceWiFiSwitch(false)
            "success=${response.isSuccess},error=${response.errorCode},data=${response.data}"
        }
    }

    private suspend fun waitForWifiReady(gateway: com.patrollink.domain.DeviceControlGateway): String {
        val samples = mutableListOf<String>()
        val deadline = System.currentTimeMillis() + WifiReadyWaitMillis
        while (System.currentTimeMillis() < deadline) {
            delay(WifiReadyPollMillis)
            val state = gateway.readWifi()
            samples += "enabled=${state.enabled},connected=${state.connected},ssid=${state.ssid}"
            if (state.enabled || state.connected) {
                return "ready=true; ${samples.joinToString(separator = " | ")}"
            }
        }
        return "ready=false; ${samples.joinToString(separator = " | ")}"
    }

    private suspend fun runWifiNetworkProbe(report: SmokeReport, context: Context) {
        runStep(report, "WIFI_ANDROID_NETWORK") { androidWifiSummary(context) }
        runStep(report, "WIFI_FILE_PROBE") { probeWifiFileService(context) }
    }

    private suspend fun runWifiNetworkProbeLoop(report: SmokeReport, context: Context, durationMillis: Long) {
        val boundedDuration = durationMillis.coerceIn(WifiProbeOnlyMinMillis, WifiProbeOnlyMaxMillis)
        val deadline = System.currentTimeMillis() + boundedDuration
        var index = 1
        report.step("WIFI_PROBE_ONLY", "durationMs=$boundedDuration")
        do {
            runStep(report, "WIFI_ANDROID_NETWORK_$index") { androidWifiSummary(context) }
            runStep(report, "WIFI_FILE_PROBE_$index") { probeWifiFileService(context) }
            index += 1
            if (System.currentTimeMillis() < deadline) delay(WifiProbeOnlyIntervalMillis)
        } while (System.currentTimeMillis() < deadline)
    }

    private fun androidWifiSummary(context: Context): String {
        val wifi = context.getSystemService(WifiManager::class.java)
        val info = wifi?.connectionInfo
        val uteScanResults = runCatching {
            wifi?.scanResults.orEmpty()
                .filter { it.SSID.startsWith("UTE", ignoreCase = true) }
                .take(8)
                .joinToString { "${it.SSID}/${it.BSSID}/rssi=${it.level}" }
        }.getOrDefault("scan-unavailable")
        return "ssid=${info?.ssid},bssid=${info?.bssid},ip=${info?.ipAddress?.toIpv4String()},link=${info?.linkSpeed}Mbps,uteScan=[${uteScanResults.ifBlank { "none" }}]"
    }

    @Suppress("DEPRECATION")
    private fun currentWifiSsid(context: Context): String? =
        context.getSystemService(WifiManager::class.java)
            ?.connectionInfo
            ?.ssid
            ?.trim('"')
            ?.takeUnless { it.isBlank() || it == "<unknown ssid>" }

    private suspend fun probeWifiFileService(context: Context): String = withContext(Dispatchers.IO) {
        val targets = wifiProbeTargets(context)
        val openPorts = mutableListOf<String>()
        val hits = mutableListOf<String>()
        val startedAt = System.currentTimeMillis()
        targets.forEach { target ->
            ProbePorts.forEach { port ->
                if (isTcpOpen(target.network, target.host, port)) {
                    openPorts += "${target.label}/${target.host}:$port"
                    val paths = if (port in FtpLikeProbePorts) listOf("/") else ProbePaths
                    paths.forEach { path ->
                        if (hits.size >= ProbeHitLimit) return@forEach
                        rawHttpProbe(target.network, target.label, target.host, port, path)?.let { hit ->
                            hits += hit
                        }
                    }
                }
            }
        }
        "elapsed=${System.currentTimeMillis() - startedAt}ms,targets=${targets.joinToString { "${it.label}/${it.host}" }},open=${openPorts.joinToString().ifBlank { "none" }},hits=${hits.joinToString(separator = " | ").ifBlank { "none" }}"
    }

    private fun wifiProbeTargets(context: Context): List<WifiProbeTarget> {
        val targets = mutableListOf<WifiProbeTarget>()
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
            ?: return DefaultProbeHosts.map { WifiProbeTarget("default", null, it) }
        val activeNetwork = connectivity.activeNetwork
        val networks = buildList {
            activeNetwork?.let(::add)
            addAll(connectivity.allNetworks)
        }.distinct()
        networks.forEach { network ->
            val capabilities = connectivity.getNetworkCapabilities(network)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return@forEach
            val linkProperties = connectivity.getLinkProperties(network) ?: return@forEach
            val label = if (network == activeNetwork) "active:$network" else "network:$network"
            linkProperties.routes.mapNotNull { it.gateway as? Inet4Address }.forEach { address ->
                targets += WifiProbeTarget(label, network, address.hostAddress.orEmpty())
            }
            linkProperties.linkAddresses.mapNotNull { linkAddress ->
                (linkAddress.address as? Inet4Address)?.hostAddress?.let { address ->
                    likelySubnetGateways(address, linkAddress.prefixLength)
                }
            }.flatten().forEach { targets += WifiProbeTarget(label, network, it) }
        }
        targets += DefaultProbeHosts.map { WifiProbeTarget("default", null, it) }
        val seen = mutableSetOf<String>()
        return targets
            .filter { it.host.isValidProbeHost() }
            .filter { seen.add("${it.label}/${it.host}") }
            .take(ProbeHostLimit)
    }

    private fun likelySubnetGateways(address: String, prefixLength: Int): List<String> {
        val parts = address.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return emptyList()
        val baseThird = if (prefixLength <= 23) parts[2] and 0xfe else parts[2]
        return listOf(
            "${parts[0]}.${parts[1]}.$baseThird.1",
            "${parts[0]}.${parts[1]}.$baseThird.254",
            "${parts[0]}.${parts[1]}.${baseThird + 1}.1",
            "${parts[0]}.${parts[1]}.${baseThird + 1}.254",
            "${parts[0]}.${parts[1]}.${parts[2]}.1",
            "${parts[0]}.${parts[1]}.${parts[2]}.254"
        ).distinct()
    }

    private fun isTcpOpen(network: Network?, host: String, port: Int): Boolean =
        runCatching {
            newProbeSocket(network).use { socket ->
                socket.connect(InetSocketAddress(host, port), TcpProbeTimeoutMillis.toInt())
            }
        }.isSuccess

    private fun rawHttpProbe(network: Network?, label: String, host: String, port: Int, path: String): String? =
        runCatching {
            newProbeSocket(network).use { socket ->
                socket.connect(InetSocketAddress(host, port), TcpProbeTimeoutMillis.toInt())
                socket.soTimeout = HttpProbeReadTimeoutMillis.toInt()
                val request = "GET $path HTTP/1.1\r\nHost: $host\r\nUser-Agent: PatrolSmokeProbe\r\nAccept: */*\r\nConnection: close\r\n\r\n"
                socket.getOutputStream().write(request.toByteArray(Charsets.US_ASCII))
                socket.getOutputStream().flush()
                val buffer = ByteArray(HttpProbePreviewBytes)
                val read = socket.getInputStream().read(buffer)
                if (read <= 0) return@runCatching null
                val preview = String(buffer, 0, read, Charsets.ISO_8859_1)
                    .replace(Regex("\\s+"), " ")
                    .take(160)
                val status = Regex("HTTP/\\S+\\s+(\\d+)").find(preview)?.groupValues?.getOrNull(1) ?: "raw"
                "$label http://$host:$port$path status=$status preview=$preview"
            }
        }.getOrNull()

    private fun newProbeSocket(network: Network?): Socket =
        network?.socketFactory?.createSocket() ?: Socket()

    private fun Int.toIpv4String(): String =
        listOf(this and 0xff, this shr 8 and 0xff, this shr 16 and 0xff, this shr 24 and 0xff)
            .joinToString(separator = ".") { (it and 0xff).toString() }

    private data class WifiProbeTarget(
        val label: String,
        val network: Network?,
        val host: String
    )

    private fun String.isValidProbeHost(): Boolean =
        isNotBlank() && this != "0.0.0.0" && !endsWith(".0") && !endsWith(".255")

    private suspend fun runHeadsetDiagnostics(report: SmokeReport, bridge: UteSdkBridge, runBt3Probe: Boolean) {
        runStep(report, "SMART_DEVICE_INFO") {
            bridge.connection.smartGetDeviceInfo().toSmokeSummary()
        }
        runStep(report, "DEVICE_INFO") {
            bridge.connection.getDeviceInfo(DeviceInfoRequest().apply {
                address = true
                deviceBtModel = true
                deviceVersionType = true
            }).toSmokeSummary()
        }
        runStep(report, "HEADSET_INFO") {
            bridge.connection.getHeadsetInfo().data?.let {
                "soundMode=${it.soundMode},brightness=${it.brightnessLevel}"
            } ?: "null"
        }
        runStep(report, "HEADSET_SDK_FEATURES") {
            "platform=${bridge.client.devicePlatform},aiRecorder=${DeviceModeJX.isHasFunction_4(DeviceModeJX.IS_SUPPORT_AI_RECORDER_MEETING_RECORDING)},fileSpp=${DeviceModeJX.isHasFunction_4(DeviceModeJX.IS_SUPPORT_FILE_TRANSFER_SPP)}"
        }
        val bt3 = runStep(report, "HEADSET_BT3_STATE_BEFORE") { bridge.connection.queryDeviceBt3State().data?.toSummary() ?: "null" }
        if (runBt3Probe && bt3 != null && !bt3.contains("connect=1")) {
            runStep(report, "HEADSET_OPEN_BT3") {
                val response = bridge.connection.openDeviceBt3(true)
                "success=${response.isSuccess},error=${response.errorCode},data=${response.data}"
            }
            runStep(report, "HEADSET_REQUEST_BT3_PAIR") {
                val response = bridge.connection.requestDevicePairBt3()
                "success=${response.isSuccess},error=${response.errorCode},data=${response.data}"
            }
            delay(3_000L)
            runStep(report, "HEADSET_BT3_STATE_AFTER") { bridge.connection.queryDeviceBt3State().data?.toSummary() ?: "null" }
        } else if (bt3 != null && !bt3.contains("connect=1")) {
            report.step("HEADSET_BT3_PROBE", "skipped; pass --ez bt3 true to probe, this can leave the current headset in 408 state")
        }
        runStep(report, "HEADSET_STORAGE") {
            bridge.connection.getDeviceStorageInfo().data?.let {
                "total=${it.total},free=${it.free},recSizePs=${it.recSizePs},freeState=${it.freeSizeState}"
            } ?: "null"
        }
        runStep(report, "SMART_GLASSES_INFO") {
            bridge.connection.getGlassesInfo().data?.let {
                val store = it.glassesStoreInfo
                "state=${it.state},store=photo ${store?.newTakenPictures}/${store?.totalPictures},audio ${store?.newRecordAudio}/${store?.totalRecordAudio},video ${store?.newRecordVideo}/${store?.totalRecordVideo},free=${store?.freeSpace},total=${store?.maxSpace}"
            } ?: "null"
        }
        runStep(report, "HEADSET_FILES_BEFORE") { headsetFileListSummary(bridge) }
    }

    private suspend fun runDeviceIdentityDiagnostics(report: SmokeReport, bridge: UteSdkBridge) {
        runStep(report, "SMART_BATTERY_INFO") {
            bridge.connection.smartGetBatteryInfo().toSmokeSummary()
        }
        runStep(report, "DEVICE_BATTERY_INFO") {
            bridge.connection.getBatteryInfo().toSmokeSummary()
        }
        runStep(report, "SMART_DEVICE_INFO") {
            bridge.connection.smartGetDeviceInfo().toSmokeSummary()
        }
        runStep(report, "DEVICE_INFO") {
            bridge.connection.getDeviceInfo(DeviceInfoRequest().apply {
                address = true
                deviceBtModel = true
                deviceVersionType = true
            }).toSmokeSummary()
        }
    }

    private suspend fun runDeviceCommands(
        report: SmokeReport,
        coordinator: PatrolCoordinator,
        bridge: UteSdkBridge,
        initial: DeviceStatus,
        commandHoldMillis: Long
    ) {
        var device = initial
        runStep(report, "TAKE_PHOTO") { coordinator.takePhoto(device).also { device = it } }
        runStep(report, "START_VIDEO") { coordinator.setRecording(device, true).also { device = it } }
        delay(commandHoldMillis)
        runStep(report, "STOP_VIDEO") { coordinator.setRecording(device, false).also { device = it } }
        if (initial.type == com.patrollink.domain.DeviceType.Headset) {
            runStep(report, "START_HEADSET_AUDIO") { coordinator.setDeviceTalk(device, true).also { device = it } }
            delay(commandHoldMillis)
            runStep(report, "STOP_HEADSET_AUDIO") { coordinator.setDeviceTalk(device, false).also { device = it } }
        }
    }

    private suspend fun runDirectMediaCommandMatrix(report: SmokeReport, bridge: UteSdkBridge, runAudio: Boolean) {
        collectInterestingNotifies(report, bridge, "MEDIA_COMMAND_MATRIX_NOTIFIES", CommandNotifyProbeMillis) {
            runStep(report, "MATRIX_ENABLE_NOTIFY") {
                bridge.client.openOrCloseNotify(true)
                "enabled=true"
            }
            runStep(report, "MATRIX_GLASSES_INFO_BEFORE") { bridge.connection.getGlassesInfo().data.toGlassesInfoSummary() }
            runStep(report, "MATRIX_GLASSES_STATE_BEFORE") { bridge.connection.getGlassesStateInfo().data.toSmokeDataSummary() }
            runStep(report, "MATRIX_SET_STANDBY") {
                bridge.connection.setGlassesState(GlassesState.STANDBY_MODE).toSmokeSummary()
            }
            runStep(report, "MATRIX_SET_RECORD_DIRECTION") {
                bridge.connection.setGlassesRecordingDirection(GlassesRecordDirection.VERTICAL_SCREEN).toSmokeSummary()
            }
            runStep(report, "MATRIX_SET_RECORD_DURATION") {
                bridge.connection.setGlassesRecordingDuration(MatrixRecordingDurationSeconds).toSmokeSummary()
            }
            runStep(report, "MATRIX_SET_VIDEO_PARAMETERS") {
                bridge.connection.setVideoParameters(VideoParametersInfo(MatrixVideoWidth, MatrixVideoHeight, MatrixVideoFrameRate)).toSmokeSummary()
            }
            delay(MatrixCommandSettleMillis)
            runStep(report, "MATRIX_PHOTO_DIRECT") {
                bridge.connection.triggerGlassesPhotoCapture(null).toSmokeSummary()
            }
            delay(MatrixPostCommandPollMillis)
            runStep(report, "MATRIX_GLASSES_INFO_AFTER_PHOTO") { bridge.connection.getGlassesInfo().data.toGlassesInfoSummary() }
            runStep(report, "MATRIX_GLASSES_STATE_AFTER_PHOTO") { bridge.connection.getGlassesStateInfo().data.toSmokeDataSummary() }
            runStep(report, "MATRIX_VIDEO_START_DIRECT") {
                bridge.connection.toggleGlassesVideoRecording(GlassesHeadsetRecordingState.RECORDING_STATE_START).toSmokeSummary()
            }
            delay(MatrixVideoHoldMillis)
            runStep(report, "MATRIX_GLASSES_STATE_DURING_VIDEO") { bridge.connection.getGlassesStateInfo().data.toSmokeDataSummary() }
            runStep(report, "MATRIX_VIDEO_STOP_DIRECT") {
                bridge.connection.toggleGlassesVideoRecording(GlassesHeadsetRecordingState.RECORDING_STATE_STOP).toSmokeSummary()
            }
            delay(MatrixPostCommandPollMillis)
            runStep(report, "MATRIX_GLASSES_INFO_AFTER_VIDEO") { bridge.connection.getGlassesInfo().data.toGlassesInfoSummary() }
            if (runAudio) {
                runStep(report, "MATRIX_AUDIO_START_DIRECT") {
                    bridge.connection.toggleHeadsetAudioRecording(GlassesHeadsetRecordingState.RECORDING_STATE_START).toSmokeSummary()
                }
                delay(MatrixAudioHoldMillis)
                runStep(report, "MATRIX_GLASSES_STATE_DURING_AUDIO") { bridge.connection.getGlassesStateInfo().data.toSmokeDataSummary() }
                runStep(report, "MATRIX_AUDIO_STOP_DIRECT") {
                    bridge.connection.toggleHeadsetAudioRecording(GlassesHeadsetRecordingState.RECORDING_STATE_STOP).toSmokeSummary()
                }
                delay(MatrixPostCommandPollMillis)
                runStep(report, "MATRIX_GLASSES_INFO_AFTER_AUDIO") { bridge.connection.getGlassesInfo().data.toGlassesInfoSummary() }
            } else {
                report.step("MATRIX_AUDIO_DIRECT", "skipped; pass --ez mediaCommandMatrixAudio true to probe audio commands")
            }
            runStep(report, "MATRIX_RETRY_IMAGE_UPLOAD") {
                bridge.connection.retryImageUpload().toSmokeSummary()
            }
            delay(MatrixPostCommandPollMillis)
            runStep(report, "MATRIX_GLASSES_INFO_AFTER_RETRY") { bridge.connection.getGlassesInfo().data.toGlassesInfoSummary() }
        }
    }

    private suspend fun runHeadsetAiRecorderCommands(
        report: SmokeReport,
        bridge: UteSdkBridge,
        options: SmokeHeadsetAiRecorderOptions
    ) {
        val supported = DeviceModeJX.isHasFunction_4(DeviceModeJX.IS_SUPPORT_AI_RECORDER_MEETING_RECORDING)
        if (!options.shouldRun(supported)) {
            report.step("SKIP_AI_RECORDER_AUDIO", "当前设备 SDK 未声明 AI 录音能力，跳过 appStartAudioRecord/appStopAudioRecord")
            return
        }
        if (!supported) {
            report.step("FORCE_AI_RECORDER_AUDIO", "设备 SDK 未声明 AI 录音能力，按 smoke 参数强制探测接口真实返回")
        }
        runStep(report, "START_AI_RECORDER_AUDIO") {
            bridge.client.openOrCloseNotify(true)
            val response = bridge.connection.appStartAudioRecord()
            val info = response.data
            val result = info?.result ?: -1
            check(response.isSuccess && result.isStartAudioAccepted()) {
                "success=${response.isSuccess},error=${response.errorCode},result=$result ${AudioRecordResult.getDescription(result)}"
            }
            "success=${response.isSuccess},error=${response.errorCode},session=${info.sessionId},result=${AudioRecordResult.getDescription(result)},scene=${info.scene},start=${info.start}"
        }
        delay(HeadsetRecordHoldMillis)
        runStep(report, "STOP_AI_RECORDER_AUDIO") {
            val response = bridge.connection.appStopAudioRecord()
            val info = response.data
            check(response.isSuccess && info != null) { "success=${response.isSuccess},error=${response.errorCode},data=${info?.javaClass?.simpleName ?: "null"}" }
            "success=${response.isSuccess},error=${response.errorCode},session=${info.sessionId},stopSrc=${info.stopSrc},saved=${info.fileExist == AudioRecordStopInfo.SAVED},fileSize=${info.fileSize}"
        }
        runStep(report, "AI_RECORDER_FILES_AFTER") { headsetFileListSummary(bridge) }
    }

    private suspend fun runMediaChecks(report: SmokeReport, coordinator: PatrolCoordinator, bridge: UteSdkBridge) {
        runStep(report, "MEDIA_LOCAL") {
            withTimeoutOrNull(MediaCheckTimeoutMillis) {
                coordinator.mediaFiles(local = true).map { "${it.id}:${it.kind}:${it.size}:local=${it.local}" }
            } ?: "timeout"
        }
        collectInterestingNotifies(report, bridge, "MEDIA_RETRY_NOTIFIES", MediaNotifyProbeMillis) {
            runStep(report, "RETRY_IMAGE_UPLOAD") {
                val response = bridge.connection.retryImageUpload()
                "success=${response.isSuccess},error=${response.errorCode},data=${response.data}"
            }
        }
        delay(2_000L)
        runStep(report, "MEDIA_DEVICE") {
            withTimeoutOrNull(MediaCheckTimeoutMillis) {
                coordinator.mediaFiles(local = false).map { "${it.id}:${it.kind}:${it.size}:local=${it.local}" }
            } ?: "timeout"
        }
        runStep(report, "MEDIA_LOCAL_AFTER_RETRY") {
            withTimeoutOrNull(MediaCheckTimeoutMillis) {
                coordinator.mediaFiles(local = true).map { "${it.id}:${it.kind}:${it.size}:local=${it.local}" }
            } ?: "timeout"
        }
    }

    private suspend fun runLocalMediaOnlyChecks(report: SmokeReport, coordinator: PatrolCoordinator) {
        val local = runStep(report, "MEDIA_LOCAL_ONLY") {
            withTimeoutOrNull(MediaCheckTimeoutMillis) {
                coordinator.mediaFiles(local = true)
            } ?: error("local media list timeout")
        }.orEmpty()
        report.step(
            "MEDIA_LOCAL_ONLY_COUNT",
            "count=${local.size},files=${local.joinToString(separator = " | ") { "${it.id}:${it.kind}:${it.size}:uri=${it.contentUri.orEmpty()}" }}"
        )
    }

    private suspend fun runCurrentWifiMediaOnlyChecks(
        report: SmokeReport,
        context: Context,
        coordinator: PatrolCoordinator,
        options: SmokeWifiMediaSyncOptions
    ) {
        val ssid = currentWifiSsid(context).orEmpty()
        report.step("CURRENT_WIFI_MEDIA_SSID", ssid.ifBlank { "unknown" })
        check(ssid.isLikelyDeviceWifiHotspotSsidForSmoke()) {
            "phone is not connected to a device hotspot; current=$ssid"
        }
        runWifiNetworkProbe(report, context)
        val deviceFiles = runStep(report, "CURRENT_WIFI_MEDIA_DEVICE_LIST") {
            withTimeoutOrNull(WifiMediaListSmokeTimeoutMillis) {
                coordinator.mediaFiles(local = false)
            } ?: error("device media list timeout")
        }.orEmpty()
        report.step(
            "CURRENT_WIFI_MEDIA_DEVICE_COUNT",
            "count=${deviceFiles.size},files=${deviceFiles.joinToString(separator = " | ") { "${it.id}:${it.kind}:${it.name}:${it.size}" }}"
        )
        if (!options.downloadFirst) {
            report.step("CURRENT_WIFI_MEDIA_DOWNLOAD", "skipped; pass --ez wifiDownloadFirst true")
            return
        }
        val candidates = options.selectDownloadCandidates(deviceFiles)
        check(candidates.isNotEmpty()) { "no device media file to download kind=${options.downloadKind ?: "any"}" }
        val downloadedIds = mutableListOf<String>()
        candidates.forEachIndexed { index, candidate ->
            val emitted = mutableListOf<String>()
            val stepName = "CURRENT_WIFI_MEDIA_DOWNLOAD_${index + 1}"
            runStep(report, stepName) {
                withTimeoutOrNull(WifiMediaDownloadTimeoutMillis) {
                    coordinator.transferMedia(candidate.id, TransferTarget.PhoneSandbox).collect { media ->
                        emitted += media.toTransferSmokeSummary()
                    }
                } ?: error("download timeout: ${candidate.id}")
                check(emitted.isNotEmpty()) { "download emitted no transfer states: ${candidate.id}" }
                downloadedIds += candidate.id
                emitted.joinToString(separator = " | ")
            }
            runStep(report, "${stepName}_LOCAL") {
                withTimeoutOrNull(MediaCheckTimeoutMillis) {
                    coordinator.mediaFiles(local = true)
                        .filter {
                            it.id == candidate.id ||
                                it.name == candidate.name ||
                                it.contentUri?.contains(candidate.name.substringBeforeLast('.')) == true
                        }
                        .map { "${it.id}:${it.kind}:${it.size}:uri=${it.contentUri.orEmpty()}:status=${it.transferStatus}" }
                } ?: "timeout"
            }
        }
        report.step("CURRENT_WIFI_MEDIA_BATCH_SUMMARY", "downloaded=${downloadedIds.size},ids=${downloadedIds.joinToString()}")
    }

    private suspend fun runFirmwareCheck(report: SmokeReport, firmwareGateway: FirmwareGateway, device: DeviceStatus) {
        runStep(report, "FIRMWARE_CHECK") {
            firmwareGateway.check(device, FirmwareDeviceMetadata(vendor = "UTE"))
        }
    }

    private suspend fun collectInterestingNotifies(
        report: SmokeReport,
        bridge: UteSdkBridge,
        name: String,
        listenMillis: Long,
        block: suspend () -> Unit
    ) = coroutineScope {
        val seen = Collections.synchronizedList(mutableListOf<String>())
        val job = launch {
            withTimeoutOrNull(listenMillis) {
                bridge.notifies.collect { notify ->
                    if (notify.type in InterestingNotifyTypes) {
                        seen += notify.toSmokeSummary()
                    }
                }
            }
        }
        delay(NotifyCollectorWarmupMillis)
        block()
        delay(NotifyCollectorTailMillis)
        job.cancel()
        report.step(name, seen.joinToString(separator = " | ").ifBlank { "none" })
    }

    private suspend fun <T> runStep(report: SmokeReport, name: String, block: suspend () -> T): T? =
        runCatching { block() }
            .onSuccess { report.pass(name, it.toString()) }
            .onFailure { throwable ->
                report.fail(name, throwable.message.orEmpty().ifBlank { throwable::class.java.simpleName })
            }
            .getOrNull()

    private fun DeviceStatus.summary(): String =
        "id=$id,name=$name,type=$type,online=$online,battery=$battery/$batteryKnown,storage=$storageUsedGb/$storageTotalGb/$storageKnown,firmware=$firmware,recording=$isRecording,talking=$isTalking"

    private fun List<ScannedDevice>.preferredControlDevice(): ScannedDevice? =
        sortedWith(
            compareByDescending<ScannedDevice> { it.name.startsWith("E1", ignoreCase = true) && it.bonded }
                .thenByDescending { it.name.startsWith("E1", ignoreCase = true) }
                .thenByDescending { it.serviceUuid.startsWith("system-bluetooth-audio") }
                .thenByDescending { it.isNativeUteHeadsetControl() }
                .thenByDescending { it.isUteControlCandidate() }
                .thenByDescending { it.type == com.patrollink.domain.DeviceType.Headset }
                .thenByDescending { it.signalBars }
        ).firstOrNull()

    private fun List<ScannedDevice>.selectedSmokeDevice(targetDeviceId: String, targetDeviceName: String): ScannedDevice? {
        if (targetDeviceId.isNotBlank()) {
            firstOrNull { it.id.equals(targetDeviceId, ignoreCase = true) }?.let { return it }
            return forcedTargetDevice(targetDeviceId, targetDeviceName)
        }
        if (targetDeviceName.isNotBlank()) {
            firstOrNull { it.name.contains(targetDeviceName, ignoreCase = true) }?.let { return it }
            return null
        }
        return preferredControlDevice()
    }

    private fun forcedTargetDevice(targetDeviceId: String, targetDeviceName: String): ScannedDevice {
        val name = targetDeviceName.ifBlank { "Target-$targetDeviceId" }
        val normalized = name.uppercase(Locale.US)
        val type = if ("GLASS" in normalized || "眼镜" in name || "GLORY" in normalized || normalized.startsWith("SMI-")) {
            com.patrollink.domain.DeviceType.Glasses
        } else {
            com.patrollink.domain.DeviceType.Headset
        }
        return ScannedDevice(
            id = targetDeviceId,
            name = name,
            signalBars = 3,
            serviceUuid = "ute-ble-control-forced",
            bonded = true,
            macAddress = targetDeviceId,
            type = type
        )
    }

    private fun ScannedDevice.isUteControlCandidate(): Boolean =
        serviceUuid != "system-bluetooth-audio-connected" && serviceUuid != "system-bluetooth-audio-bonded"

    private fun ScannedDevice.isNativeUteHeadsetControl(): Boolean =
        serviceUuid == "ute-ble-control-scanned" && type == com.patrollink.domain.DeviceType.Headset

    private fun Int.isStartAudioAccepted(): Boolean =
        this == AudioRecordResult.RECORD_START_SUCCESS || this == AudioRecordResult.RECORD_ALREADY_IN_PROGRESS

    private fun headsetFileListSummary(bridge: UteSdkBridge): String {
        val result = bridge.connection.queryAudioRecordFileLists(RequestAudioRecordFileInfo(0x01, 0, 1)).data
        val files = result?.audioRecordFiles.orEmpty()
        return "count=${result?.count ?: files.size}; " + files.joinToString { file ->
            "session=${file.sessionId},type=${file.fileType},size=${file.fileSize}"
        }
    }

    private fun sdkFeatureFlags(): String {
        val methods = (1..13).mapNotNull { index ->
            runCatching {
                DeviceModeJX::class.java.getMethod("isHasFunction_$index", Int::class.javaPrimitiveType)
            }.getOrNull()
        }
        val flags = DeviceModeJX::class.java.fields
            .filter { Modifier.isStatic(it.modifiers) && it.name.startsWith("IS_") }
            .mapNotNull { field ->
                val value = runCatching { field.getInt(null) }.getOrNull() ?: return@mapNotNull null
                val supported = methods.any { method ->
                    runCatching { method.invoke(null, value) as? Boolean == true }.getOrDefault(false)
                }
                if (supported) "${field.name}=$value" else null
            }
        return flags.joinToString(separator = ",").ifBlank { "none" }
    }

    private fun DeviceBt3StateInfo.toSummary(): String =
        "name=$deviceNameBt3,address=$deviceAddressBt3,switch=$deviceBtSwitch,paired=$deviceBtPairedState,connect=$deviceBtConnectState"

    private fun Notify.toSmokeSummary(): String =
        "${type.toNotifyName()}(type=$type,error=$errorCode,data=${data.toSmokeDataSummary()})"

    private fun Response<*>.toSmokeSummary(): String =
        "success=$isSuccess,error=$errorCode,data=${data.toSmokeDataSummary()}"

    private fun MediaFile.toTransferSmokeSummary(): String =
        "id=$id,local=$local,kind=$kind,status=$transferStatus,target=$lastTransferTarget,progress=$progress,uri=${contentUri.orEmpty()},size=$size"

    private fun Any?.toSmokeDataSummary(): String = when (this) {
        null -> "null:null"
        is GlassesStateInfo -> "GlassesStateInfo:${toStateSummary()}"
        is SmartImageDataInfo -> "SmartImageDataInfo:crc=$crcSuccess,type=$imaType,size=$imaSize,file=${file?.absolutePath}"
        is SmartAudioDataInfo -> "SmartAudioDataInfo:crc=$crcSuccess,type=$audioType,size=$audioSize,file=${file?.absolutePath},bytes=${data?.size ?: 0}"
        else -> toSmokeObjectSummary()
    }

    private fun Any?.toSmokeObjectSummary(): String {
        if (this == null) return "null:null"
        val type = javaClass.simpleName.ifBlank { javaClass.name }
        val fields = generateSequence(javaClass) { it.superclass }
            .takeWhile { it != Any::class.java }
            .flatMap { it.declaredFields.asSequence() }
            .filterNot { Modifier.isStatic(it.modifiers) }
            .take(SmokeObjectFieldLimit)
            .mapNotNull { field ->
                runCatching {
                    field.isAccessible = true
                    "${field.name}=${field.get(this)}"
                }.getOrNull()
            }
            .joinToString(separator = ",")
        val value = fields.ifBlank { toString() }
        return "$type:${value.take(SmokeObjectSummaryLimit)}"
    }

    private fun GlassesInfo?.toGlassesInfoSummary(): String {
        val store = this?.glassesStoreInfo
        return "state=${this?.state},store=photo ${store?.newTakenPictures}/${store?.totalPictures},audio ${store?.newRecordAudio}/${store?.totalRecordAudio},video ${store?.newRecordVideo}/${store?.totalRecordVideo},free=${store?.freeSpace},total=${store?.maxSpace}"
    }

    private fun GlassesStateInfo.toStateSummary(): String {
        val flags = GlassesStateFlags
            .filter { (_, value) -> getStateInfo(value.toLong()) }
            .joinToString(separator = "|") { it.first }
            .ifBlank { "none" }
        return "state=$state,flags=$flags"
    }

    private fun Int.toNotifyName(): String = when (this) {
        NotifyType.DEVICE_PAIRED_STATE_NOTIFY -> "DEVICE_PAIRED_STATE_NOTIFY"
        NotifyType.SMART_GLASSES_STATE_NOTIFY -> "SMART_GLASSES_STATE_NOTIFY"
        NotifyType.SMART_GLASSES_IMAGE_DATA_NOTIFY -> "SMART_GLASSES_IMAGE_DATA_NOTIFY"
        NotifyType.SMART_GLASSES_AUDIO_DATA_NOTIFY -> "SMART_GLASSES_AUDIO_DATA_NOTIFY"
        NotifyType.THIRD_PARTY_DATA_TRANSMIT_NOTIFY -> "THIRD_PARTY_DATA_TRANSMIT_NOTIFY"
        NotifyType.SMART_GLASSES_DATA_STATE_NOTIFY -> "SMART_GLASSES_DATA_STATE_NOTIFY"
        NotifyType.SMART_WIFI_STATE_NOTIFY -> "SMART_WIFI_STATE_NOTIFY"
        NotifyType.SMART_GLASSES_STORE_INFO_NOTIFY -> "SMART_GLASSES_STORE_INFO_NOTIFY"
        NotifyType.AI_RECORDER_SYNC_SECTION_DATA_NOTIFY -> "AI_RECORDER_SYNC_SECTION_DATA_NOTIFY"
        NotifyType.AI_RECORDER_SYNC_ALL_DATA_NOTIFY -> "AI_RECORDER_SYNC_ALL_DATA_NOTIFY"
        NotifyType.AI_RECORDER_ABORT_SYNC_DATA_NOTIFY -> "AI_RECORDER_ABORT_SYNC_DATA_NOTIFY"
        NotifyType.AI_RECORDER_SYNCING_ALL_DATA_NOTIFY -> "AI_RECORDER_SYNCING_ALL_DATA_NOTIFY"
        else -> "NOTIFY"
    }

    private class SmokeReport(private val context: Context) {
        private val lines = mutableListOf<String>()

        fun step(name: String, detail: String) = add("INFO", name, detail)
        fun pass(name: String, detail: String) = add("PASS", name, detail)
        fun fail(name: String, detail: String) = add("FAIL", name, detail)

        private fun add(status: String, name: String, detail: String) {
            val sanitized = detail
                .replace(Regex("(accessToken|refreshToken)=[^,)]*"), "$1=<redacted>")
                .replace('\n', ' ')
                .take(1200)
            val line = "$status $name $sanitized"
            lines += line
            Log.i(Tag, line)
            writeLatest()
        }

        private fun writeLatest() {
            runCatching {
                val directory = context.getExternalFilesDir(null) ?: context.filesDir
                directory.mkdirs()
                File(directory, "smoke-test-latest.txt").writeText(lines.joinToString(separator = "\n", postfix = "\n"))
            }
        }

        fun writeTo(context: Context): File {
            val directory = context.getExternalFilesDir(null) ?: context.filesDir
            directory.mkdirs()
            val text = lines.joinToString(separator = "\n", postfix = "\n")
            File(directory, "smoke-test-latest.txt").writeText(text)
            return File(directory, "smoke-test-${Timestamp.format(Date())}.txt").also { it.writeText(text) }
        }
    }

    const val Tag = "PatrolSmoke"
    private const val ScanTimeoutMillis = 15_000L
    private const val CommandHoldMillis = 2_000L
    private const val MinCommandHoldMillis = 500L
    private const val MaxCommandHoldMillis = 60_000L
    private const val HeadsetRecordHoldMillis = 8_000L
    private const val MatrixCommandSettleMillis = 1_000L
    private const val MatrixPostCommandPollMillis = 3_000L
    private const val MatrixVideoHoldMillis = 10_000L
    private const val MatrixAudioHoldMillis = 5_000L
    private const val MatrixRecordingDurationSeconds = 24 * 60 * 60
    private const val MatrixVideoWidth = 240
    private const val MatrixVideoHeight = 0
    private const val MatrixVideoFrameRate = 16
    private const val MediaCheckTimeoutMillis = 12_000L
    private const val WifiMediaDiagnosticsTimeoutMillis = 35_000L
    private const val WifiMediaListSmokeTimeoutMillis = 70_000L
    private const val WifiMediaDownloadTimeoutMillis = 60_000L
    private const val CommandNotifyProbeMillis = 45_000L
    private const val PairingNotifyProbeMillis = 8_000L
    private const val SmartAuthNotifyProbeMillis = 8_000L
    private const val WifiNotifyProbeMillis = 30_000L
    private const val WifiReadyWaitMillis = 20_000L
    private const val WifiReadyPollMillis = 2_000L
    private const val WifiProbeOnlyDefaultMillis = 90_000L
    private const val WifiProbeOnlyMinMillis = 5_000L
    private const val WifiProbeOnlyMaxMillis = 180_000L
    private const val WifiProbeOnlyIntervalMillis = 2_000L
    private const val TcpProbeTimeoutMillis = 300L
    private const val HttpProbeReadTimeoutMillis = 600L
    private const val HttpProbePreviewBytes = 512
    private const val ProbeHostLimit = 12
    private const val ProbeHitLimit = 12
    private const val SmokeObjectFieldLimit = 16
    private const val SmokeObjectSummaryLimit = 700
    private const val MediaNotifyProbeMillis = 12_000L
    private const val NotifyCollectorWarmupMillis = 200L
    private const val NotifyCollectorTailMillis = 2_000L
    private const val SdkFeatureFlagsTimeoutMillis = 3_000L
    private val DefaultProbeHosts = listOf(
        "192.168.4.1",
        "192.168.43.1",
        "192.168.49.1",
        "192.168.1.1",
        "192.168.0.1"
    )
    private val ProbePorts = listOf(80, 8000, 8080, 8088, 5000, 2121, 21)
    private val FtpLikeProbePorts = setOf(21, 2121)
    private val ProbePaths = listOf(
        "/",
        "/files",
        "/api/files",
        "/filelist",
        "/list",
        "/sdcard",
        "/media",
        "/DCIM",
        "/photo",
        "/video",
        "/audio",
        "/record"
    )
    private val InterestingNotifyTypes = setOf(
        NotifyType.DEVICE_PAIRED_STATE_NOTIFY,
        NotifyType.SMART_GLASSES_STATE_NOTIFY,
        NotifyType.SMART_GLASSES_IMAGE_DATA_NOTIFY,
        NotifyType.SMART_GLASSES_AUDIO_DATA_NOTIFY,
        NotifyType.THIRD_PARTY_DATA_TRANSMIT_NOTIFY,
        NotifyType.SMART_GLASSES_DATA_STATE_NOTIFY,
        NotifyType.SMART_WIFI_STATE_NOTIFY,
        NotifyType.SMART_GLASSES_STORE_INFO_NOTIFY,
        NotifyType.AI_RECORDER_SYNC_SECTION_DATA_NOTIFY,
        NotifyType.AI_RECORDER_SYNC_ALL_DATA_NOTIFY,
        NotifyType.AI_RECORDER_ABORT_SYNC_DATA_NOTIFY,
        NotifyType.AI_RECORDER_SYNCING_ALL_DATA_NOTIFY
    )
    private val GlassesStateFlags = listOf(
        "FOLDED" to GlassesState.GLASSES_FRAME_FOLDED,
        "UNFOLDED" to GlassesState.GLASSES_FRAME_UNFOLDED,
        "VIDEO_MODE" to GlassesState.VIDEO_RECORDING_MODE,
        "PHOTO_MODE" to GlassesState.PHOTO_CAPTURE_MODE,
        "AUDIO_MODE" to GlassesState.AUDIO_RECORDING_MODE,
        "STANDBY" to GlassesState.STANDBY_MODE,
        "ON_HEAD" to GlassesState.ON_HEAD_STATUS,
        "OFF_HEAD" to GlassesState.OFF_HEAD_STATUS,
        "FIRMWARE_UPDATING" to GlassesState.FIRMWARE_UPDATING,
        "MEDIA_COUNT_FAILED" to GlassesState.UPDATE_MEDIA_COUNT_FAILED,
        "MEDIA_COUNT_SUCCESS" to GlassesState.UPDATE_MEDIA_COUNT_SUCCESS,
        "STORAGE_FULL" to GlassesState.STORAGE_SPACE_FULL,
        "OPERATION_FAILED" to GlassesState.GLASSES_OPERATION_FAILED,
        "START_AUDIO_OK" to GlassesState.START_RECORD_AUDIO_SUCCESS,
        "START_AUDIO_FAILED" to GlassesState.START_RECORD_AUDIO_FAILED,
        "STOP_AUDIO_OK" to GlassesState.STOP_RECORD_AUDIO_SUCCESS,
        "STOP_AUDIO_FAILED" to GlassesState.STOP_RECORD_AUDIO_FAILED,
        "PHOTO_OK" to GlassesState.PHOTO_CAPTURED_SUCCESS,
        "PHOTO_FAILED" to GlassesState.PHOTO_CAPTURED_FAILED,
        "VIDEO_START_OK" to GlassesState.VIDEO_RECORDING_STARTED_SUCCESS,
        "VIDEO_START_FAILED" to GlassesState.VIDEO_RECORDING_STARTED_FAILED,
        "VIDEO_STOP_OK" to GlassesState.VIDEO_RECORDING_STOP_SUCCESS,
        "VIDEO_STOP_FAILED" to GlassesState.VIDEO_RECORDING_STOP_FAILED,
        "ISP_SD_FAILED" to GlassesState.ISP_SD_FAILED_LOAD,
        "ISP_CAMERA_ABNORMAL" to GlassesState.ISP_CAMERA_ABNORMALITY,
        "ISP_CAMERA_OFF" to GlassesState.ISP_CAMERA_NOT_TURNED_ON
    )
    private val Timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
}

internal fun String.isLikelyDeviceWifiHotspotSsidForSmoke(): Boolean {
    val normalized = uppercase(Locale.US)
    return normalized.startsWith("UTE") ||
        normalized.startsWith("GLORY") ||
        normalized.startsWith("AI_GLASS") ||
        normalized.contains("GLASS")
}

private object SmokeTestRunGate {
    private val running = AtomicBoolean(false)
    @Volatile private var lastFinishedAt = 0L

    fun tryStart(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastFinishedAt < RecentFinishGuardMillis) return false
        return running.compareAndSet(false, true)
    }

    fun finish() {
        lastFinishedAt = System.currentTimeMillis()
        running.set(false)
    }

    private const val RecentFinishGuardMillis = 15_000L
}
