package com.patrollink.debug

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import com.patrollink.data.RuntimeConfigStore
import com.patrollink.data.RuntimeTokenStore
import com.patrollink.data.ServiceFactory
import com.patrollink.data.ute.UteSdkBridge
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.EmptyAppState
import com.patrollink.domain.FirmwareDeviceMetadata
import com.patrollink.domain.FirmwareGateway
import com.patrollink.domain.PatrolCoordinator
import com.patrollink.domain.ScannedDevice
import com.yc.nadalsdk.bean.DeviceBt3StateInfo
import com.yc.nadalsdk.bean.HonorAccountConfig
import com.yc.nadalsdk.bean.Notify
import com.yc.nadalsdk.bean.Response
import com.yc.nadalsdk.bean.recorder.AudioRecordStopInfo
import com.yc.nadalsdk.bean.recorder.RequestAudioRecordFileInfo
import com.yc.nadalsdk.bean.smart.GlassesStateInfo
import com.yc.nadalsdk.bean.smart.HeadsetAccountConfig
import com.yc.nadalsdk.bean.smart.SmartAudioDataInfo
import com.yc.nadalsdk.bean.smart.SmartAuthorizationCode
import com.yc.nadalsdk.bean.smart.SmartImageDataInfo
import com.yc.nadalsdk.ble.open.DeviceModeJX
import com.yc.nadalsdk.constants.NotifyType
import com.yc.nadalsdk.constants.recorder.AudioRecordResult
import com.yc.nadalsdk.constants.smart.GlassesState
import java.io.File
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).apply {
            text = "Patrol smoke test running..."
            gravity = Gravity.CENTER
            textSize = 18f
        }
        setContentView(status)
        if (!SmokeTestRunGate.tryStart()) {
            status.text = "Patrol smoke test already running or just finished"
            return
        }
        val launchIntent = Intent(intent)
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

private object SmokeTestRunner {
    suspend fun run(context: Context, intent: Intent): File {
        val report = SmokeReport()
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
        val runAuth = intent.getBooleanExtra("auth", true)
        val runPairing = intent.getBooleanExtra("pairing", false)
        val runAccountProbe = intent.getBooleanExtra("accountProbe", false)
        val runBt3Probe = intent.getBooleanExtra("bt3", false)
        val authCode = intent.getStringExtra("authCode").orEmpty()
        val config = RuntimeConfigStore(context).read()
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

        report.step("CONFIG", "rest=${config.restBaseUrl}, realBle=${config.useRealBle}")
        val session = runStep(report, "LOGIN") {
            coordinator.loginAndStartSession(account, password)
        } ?: return
        tokenStore.update(session)
        tokenStore.updatePairingUsername(account)
        val currentUser = runStep(report, "CURRENT_USER") { coordinator.currentUser() }
        tokenStore.updatePairingUsername(currentUser?.badgeNo ?: account)
        val devices = scanDevices(report, coordinator)
        val selected = devices.preferredControlDevice()
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
        if (runCommands) {
            collectInterestingNotifies(report, bridge, "COMMAND_NOTIFIES", CommandNotifyProbeMillis) {
                runDeviceCommands(report, coordinator, bridge, bound)
            }
        }
        runDeviceCapabilities(report, context, config, bridge, bound, enableWifi)
        if (bound.type == com.patrollink.domain.DeviceType.Headset) {
            runHeadsetDiagnostics(report, bridge, runBt3Probe)
            runHeadsetAiRecorderCommands(report, bridge)
        }
        runMediaChecks(report, coordinator, bridge)
        runFirmwareCheck(report, firmwareGateway, bound)
        runControlChannelDiagnostics(report, bridge, tokenStore.pairingAccountId(), runAuth, runPairing, runAccountProbe, authCode)
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
        collectInterestingNotifies(report, bridge, "PAIRING_NOTIFIES", PairingNotifyProbeMillis) {
            if (runPairing) {
                runStep(report, "SDK_REQUEST_PAIRING") {
                    val response = bridge.connection.requestDevicePairing(1)
                    "success=${response.isSuccess},error=${response.errorCode},paired=${response.data?.pairedState}"
                }
            } else {
                report.step("SDK_REQUEST_PAIRING", "skipped; pass --ez pairing true to probe, current headset rejects this path with 408")
            }
            if (runAccountProbe) {
                runStep(report, "SDK_SET_HEADSET_ACCOUNT") {
                    val response = bridge.connection.setHeadsetAccount(HeadsetAccountConfig().apply {
                        currentHuid = pairingAccountId
                    })
                    "success=${response.isSuccess},error=${response.errorCode},status=${response.data?.accountJudgmentStatus}"
                }
                runStep(report, "SDK_SET_HONOR_ACCOUNT") {
                    val response = bridge.connection.setHonorAccount(HonorAccountConfig().apply {
                        currentHuid = pairingAccountId
                    })
                    "success=${response.isSuccess},error=${response.errorCode},status=${response.data?.accountJudgmentStatus}"
                }
            } else {
                report.step("SDK_SET_ACCOUNT", "skipped; pass --ez accountProbe true to probe, this can leave the current headset in 408 state")
            }
        }
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
        runStep(report, "SDK_FEATURE_FLAGS") { sdkFeatureFlags() }
    }

    private suspend fun runDeviceCapabilities(
        report: SmokeReport,
        context: Context,
        config: com.patrollink.data.RuntimeConfig,
        bridge: UteSdkBridge,
        device: DeviceStatus,
        enableWifi: Boolean
    ) {
        val gateway = ServiceFactory.createDeviceControlGateway(
            context = context,
            config = config,
            sharedUteBridge = bridge,
            tokenProvider = { null },
            deviceIdProvider = { device.id }
        )
        runStep(report, "DEVICE_CAPABILITIES") { gateway.capabilities(device) }
        runStep(report, "READ_WIFI") { gateway.readWifi() }
        if (enableWifi) {
            runStep(report, "ENABLE_WIFI") { gateway.configureWifi(enabled = true, ssid = "", password = "") }
            delay(2_000L)
            runStep(report, "READ_WIFI_AFTER_ENABLE") { gateway.readWifi() }
        }
    }

    private suspend fun runHeadsetDiagnostics(report: SmokeReport, bridge: UteSdkBridge, runBt3Probe: Boolean) {
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

    private suspend fun runDeviceCommands(
        report: SmokeReport,
        coordinator: PatrolCoordinator,
        bridge: UteSdkBridge,
        initial: DeviceStatus
    ) {
        var device = initial
        runStep(report, "TAKE_PHOTO") { coordinator.takePhoto(device).also { device = it } }
        runStep(report, "START_VIDEO") { coordinator.setRecording(device, true).also { device = it } }
        delay(CommandHoldMillis)
        runStep(report, "STOP_VIDEO") { coordinator.setRecording(device, false).also { device = it } }
        if (initial.type == com.patrollink.domain.DeviceType.Headset) {
            runStep(report, "START_HEADSET_AUDIO") { coordinator.setDeviceTalk(device, true).also { device = it } }
            delay(CommandHoldMillis)
            runStep(report, "STOP_HEADSET_AUDIO") { coordinator.setDeviceTalk(device, false).also { device = it } }
        }
    }

    private suspend fun runHeadsetAiRecorderCommands(report: SmokeReport, bridge: UteSdkBridge) {
        if (!DeviceModeJX.isHasFunction_4(DeviceModeJX.IS_SUPPORT_AI_RECORDER_MEETING_RECORDING)) {
            report.step("SKIP_AI_RECORDER_AUDIO", "当前设备 SDK 未声明 AI 录音能力，跳过 appStartAudioRecord/appStopAudioRecord")
            return
        }
        runStep(report, "START_AI_RECORDER_AUDIO") {
            bridge.client.openOrCloseNotify(true)
            val response = bridge.connection.appStartAudioRecord()
            val info = response.data
            val result = info?.result ?: -1
            check(result.isStartAudioAccepted()) {
                "success=${response.isSuccess},error=${response.errorCode},result=$result ${AudioRecordResult.getDescription(result)}"
            }
            "success=${response.isSuccess},error=${response.errorCode},session=${info.sessionId},result=${AudioRecordResult.getDescription(result)},scene=${info.scene},start=${info.start}"
        }
        delay(HeadsetRecordHoldMillis)
        runStep(report, "STOP_AI_RECORDER_AUDIO") {
            val response = bridge.connection.appStopAudioRecord()
            val info = response.data
            check(response.isSuccess || info != null) { "success=${response.isSuccess},error=${response.errorCode},data=null" }
            if (info == null) {
                "success=${response.isSuccess},error=${response.errorCode},data=null"
            } else {
                "success=${response.isSuccess},error=${response.errorCode},session=${info.sessionId},stopSrc=${info.stopSrc},saved=${info.fileExist == AudioRecordStopInfo.SAVED},fileSize=${info.fileSize}"
            }
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

    private fun Any?.toSmokeDataSummary(): String = when (this) {
        null -> "null:null"
        is GlassesStateInfo -> "GlassesStateInfo:${toStateSummary()}"
        is SmartImageDataInfo -> "SmartImageDataInfo:crc=$crcSuccess,type=$imaType,size=$imaSize,file=${file?.absolutePath}"
        is SmartAudioDataInfo -> "SmartAudioDataInfo:crc=$crcSuccess,type=$audioType,size=$audioSize,file=${file?.absolutePath},bytes=${data?.size ?: 0}"
        else -> "${javaClass.simpleName}:$this"
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

    private class SmokeReport {
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
    private const val HeadsetRecordHoldMillis = 8_000L
    private const val MediaCheckTimeoutMillis = 12_000L
    private const val CommandNotifyProbeMillis = 45_000L
    private const val PairingNotifyProbeMillis = 8_000L
    private const val SmartAuthNotifyProbeMillis = 8_000L
    private const val MediaNotifyProbeMillis = 12_000L
    private const val NotifyCollectorWarmupMillis = 200L
    private const val NotifyCollectorTailMillis = 2_000L
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
