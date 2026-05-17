package com.patrollink.data.ute

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import androidx.annotation.RequiresPermission
import com.patrollink.domain.DeviceCommand
import com.patrollink.domain.DeviceGateway
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.DeviceType
import com.patrollink.domain.ScannedDevice
import com.yc.nadalsdk.bean.CameraStatusConfig
import com.yc.nadalsdk.bean.DeviceInfoRequest
import com.yc.nadalsdk.bean.DevicePairedState
import com.yc.nadalsdk.bean.HonorAccountConfig
import com.yc.nadalsdk.bean.Response
import com.yc.nadalsdk.bean.recorder.AudioRecordInfo
import com.yc.nadalsdk.bean.recorder.AudioRecordStopInfo
import com.yc.nadalsdk.bean.smart.GlassesStateInfo
import com.yc.nadalsdk.bean.smart.HeadsetAccountConfig
import com.yc.nadalsdk.bean.smart.SmartImageDataInfo
import com.yc.nadalsdk.ble.open.UteBleConnection
import com.yc.nadalsdk.constants.NotifyType
import com.yc.nadalsdk.constants.recorder.AudioRecordResult
import com.yc.nadalsdk.constants.smart.GlassesHeadsetRecordingState
import com.yc.nadalsdk.constants.smart.GlassesState
import com.yc.nadalsdk.listener.BleConnectStateListener
import com.yc.nadalsdk.scan.UteScanCallback
import com.yc.nadalsdk.scan.UteScanDevice
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class UteSdkDeviceGateway(
    private val bridge: UteSdkBridge,
    private val fallbackStatus: DeviceStatus,
    private val mediaDirectory: File? = null,
    private val pairingAccountIdProvider: () -> String = { DefaultPairingAccountId }
) : DeviceGateway {
    private val scanned = ConcurrentHashMap<String, ScannedDevice>()
    private val connectedAtByDevice = ConcurrentHashMap<String, Long>()
    private val mutex = Mutex()
    private var activeDevice: DeviceStatus = fallbackStatus
    private var connectResult: CompletableDeferred<Boolean>? = null

    @SuppressLint("MissingPermission")
    override fun scan(): Flow<List<ScannedDevice>> = callbackFlow {
        if (!bridge.client.isBluetoothEnable && bluetoothAdapter()?.isEnabled != true) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        fun publishKnownDevices() {
            trySend(mergedScannedDevices())
        }
        publishKnownDevices()
        if (!bridge.hasBluetoothScanPermission()) {
            close()
            return@callbackFlow
        }
        val callback = object : UteScanCallback {
            override fun onScanning(device: UteScanDevice) {
                val mapped = device.toScannedDevice() ?: return
                scanned[mapped.id] = mapped
                publishKnownDevices()
            }

            override fun onScanComplete(scanDeviceList: MutableList<UteScanDevice>) {
                scanDeviceList.mapNotNull { it.toScannedDevice() }.forEach { scanned[it.id] = it }
                publishKnownDevices()
            }

            override fun onScanFailed(errorCode: Int) {
                publishKnownDevices()
            }
        }
        runCatching { bridge.client.scanDevice(callback, ScanMillis) }
            .onFailure {
                trySend(emptyList())
                close(it)
            }
        awaitClose { bridge.client.cancelScan() }
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun bind(deviceId: String): DeviceStatus = mutex.withLock {
        val discovered = scanned[deviceId]
        val systemDiscovered = discovered ?: systemBluetoothAudioDevices().firstOrNull { it.id == deviceId }
        if (!bridge.hasBluetoothConnectPermission()) {
            return fallbackStatus.copy(id = deviceId, online = false)
        }
        if (systemDiscovered?.isSystemAudioDevice() == true) {
            val sdkStatus = runCatching {
                connectAndReadStatus(
                    deviceId = deviceId,
                    discovered = systemDiscovered.copy(serviceUuid = SystemBluetoothAudioControlConnected)
                )
            }.getOrNull()
            if (sdkStatus?.online == true) {
                activeDevice = sdkStatus.copy(
                    name = systemDiscovered.name,
                    signalBars = maxOf(sdkStatus.signalBars, systemDiscovered.signalBars),
                    type = systemDiscovered.type
                )
                return activeDevice
            }
            activeDevice = systemAudioStatus(systemDiscovered)
            return activeDevice
        }
        connectAndReadStatus(deviceId, discovered)
    }

    override suspend fun unbind(deviceId: String): DeviceStatus? = mutex.withLock {
        withContext(Dispatchers.IO) { runCatching { bridge.disconnect() } }
        connectResult = null
        connectedAtByDevice.remove(deviceId)
        activeDevice = fallbackStatus.copy(id = deviceId, online = false, isRecording = false, isTalking = false)
        activeDevice
    }

    override suspend fun sendCommand(deviceId: String, command: DeviceCommand): DeviceStatus = mutex.withLock {
        if (!bridge.hasBluetoothConnectPermission()) {
            error("bluetooth connect permission missing")
        }
        val discovered = scanned[deviceId]
        if (!bridge.client.isConnected || activeDevice.id != deviceId || !activeDevice.online) {
            val connectedStatus = connectAndReadStatus(deviceId, discovered)
            check(connectedStatus.online) { "device control channel disconnected" }
        }
        val connection = bridge.connection
        enableControlNotifications()
        awaitHeadsetControlWarmup(deviceId)
        val commandResult = executeClosedCommand(connection, command)
        check(commandResult.success) { commandResult.detail }
        val refreshed = readStatus(deviceId, discovered, connected = true)
        val next = when (command) {
            DeviceCommand.StartRecord -> refreshed.copy(isRecording = true)
            DeviceCommand.StopRecord -> refreshed.copy(isRecording = false)
            DeviceCommand.StartTalk -> refreshed.copy(isTalking = true)
            DeviceCommand.StopTalk -> refreshed.copy(isTalking = false)
            DeviceCommand.TakePhoto -> refreshed
        }
        activeDevice = next
        next
    }

    private suspend fun executeClosedCommand(connection: UteBleConnection, command: DeviceCommand): CommandExecutionResult = coroutineScope {
        val expectedState = command.expectedState()
        val stateResult = async(start = CoroutineStart.UNDISPATCHED) {
            awaitGlassesState(expectedState.success, expectedState.failure)
        }
        val imageResult = if (command == DeviceCommand.TakePhoto) {
            async(start = CoroutineStart.UNDISPATCHED) { awaitImageDataAndConfirm(connection) }
        } else {
            null
        }
        val attempts = withContext(Dispatchers.IO) {
            when (command) {
                DeviceCommand.TakePhoto -> listOf(
                    runCommandAttempt("sendCameraPermission") { connection.sendCameraPermission(true) },
                    runCommandAttempt("setCameraStatus") { connection.setCameraStatus(CameraStatusConfig.STATUS_OPEN) },
                    runCommandAttempt("triggerGlassesPhotoCapture") { connection.triggerGlassesPhotoCapture(null) }
                )
                DeviceCommand.StartRecord -> listOf(
                    runCommandAttempt("toggleGlassesVideoRecording(start)") {
                        connection.toggleGlassesVideoRecording(GlassesHeadsetRecordingState.RECORDING_STATE_START)
                    }
                )
                DeviceCommand.StopRecord -> listOf(
                    runCommandAttempt("toggleGlassesVideoRecording(stop)") {
                        connection.toggleGlassesVideoRecording(GlassesHeadsetRecordingState.RECORDING_STATE_STOP)
                    }
                )
                DeviceCommand.StartTalk -> listOf(
                    runCommandAttempt("toggleHeadsetAudioRecording(start)") {
                        connection.toggleHeadsetAudioRecording(GlassesHeadsetRecordingState.RECORDING_STATE_START)
                    }
                )
                DeviceCommand.StopTalk -> listOf(
                    runCommandAttempt("toggleHeadsetAudioRecording(stop)") {
                        connection.toggleHeadsetAudioRecording(GlassesHeadsetRecordingState.RECORDING_STATE_STOP)
                    }
                )
            }
        }
        val commandAccepted = attempts.lastOrNull()?.success == true
        val detail = attempts.joinToString(separator = "; ") { it.summary() }
        if (!commandAccepted) {
            stateResult.cancel()
            imageResult?.cancel()
            return@coroutineScope CommandExecutionResult(false, detail.ifBlank { "device command failed" }, attempts)
        }
        if (command == DeviceCommand.TakePhoto) {
            stateResult.cancel()
            val uploaded = imageResult?.let {
                withTimeoutOrNull(PhotoInlineSyncTimeoutMillis) { it.await() }
            } == true
            if (!uploaded) imageResult?.cancel()
            return@coroutineScope CommandExecutionResult(true, detail, attempts)
        }
        stateResult.cancel()
        imageResult?.cancel()
        CommandExecutionResult(true, detail, attempts)
    }

    private inline fun runCommandAttempt(name: String, block: () -> Response<*>): CommandAttempt =
        runCatching {
            val response = block()
            CommandAttempt(
                name = name,
                success = response.isSuccess,
                errorCode = response.errorCode,
                data = response.data?.toString()
            )
        }.getOrElse { throwable ->
            CommandAttempt(
                name = name,
                success = false,
                errorCode = null,
                data = throwable.message.orEmpty().ifBlank { throwable::class.java.simpleName }
            )
        }

    private data class CommandAttempt(
        val name: String,
        val success: Boolean,
        val errorCode: Int?,
        val data: String?
    ) {
        fun summary(): String =
            "$name success=$success,error=${errorCode ?: "exception"},data=${data.orEmpty().ifBlank { "null" }}"
    }

    private data class CommandExecutionResult(
        val success: Boolean,
        val detail: String,
        val attempts: List<CommandAttempt> = emptyList()
    )

    private suspend fun awaitGlassesState(successStates: Set<Int>, failureStates: Set<Int>): Boolean =
        withTimeoutOrNull(CommandStateTimeoutMillis) {
            bridge.notifies
                .filter { it.type == NotifyType.SMART_GLASSES_STATE_NOTIFY }
                .mapNotNull { it.data as? GlassesStateInfo }
                .mapNotNull { state ->
                    when {
                        successStates.any { state.getStateInfo(it.toLong()) } -> true
                        failureStates.any { state.getStateInfo(it.toLong()) } -> false
                        else -> null
                    }
                }
                .first()
        } == true

    private suspend fun awaitImageDataAndConfirm(connection: UteBleConnection): Boolean {
        val image = withTimeoutOrNull(PhotoDataTimeoutMillis) {
            bridge.notifies
                .filter { it.type == NotifyType.SMART_GLASSES_IMAGE_DATA_NOTIFY }
                .mapNotNull { it.data as? SmartImageDataInfo }
                .first()
        }
        if (image?.crcSuccess != true) return false
        persistImageData(image)
        return withContext(Dispatchers.IO) {
            runCatching { connection.notifyMediaSyncCompleted().isSuccess }.getOrDefault(false)
        }
    }

    private suspend fun persistImageData(image: SmartImageDataInfo) = withContext(Dispatchers.IO) {
        val source = image.file?.takeIf { it.exists() && it.isFile && it.length() > 0 } ?: return@withContext
        val directory = mediaDirectory ?: return@withContext
        directory.mkdirs()
        val extension = source.extension.ifBlank {
            image.imaType.orEmpty().substringAfterLast('/', "jpg").ifBlank { "jpg" }
        }.lowercase()
        val target = File(directory, "glasses-photo-${System.currentTimeMillis()}.$extension")
        runCatching { source.copyTo(target, overwrite = true) }
    }

    private fun DeviceCommand.expectedState(): ExpectedDeviceState = when (this) {
        DeviceCommand.TakePhoto -> ExpectedDeviceState(
            success = setOf(GlassesState.PHOTO_CAPTURED_SUCCESS),
            failure = CommonOperationFailureStates + GlassesState.PHOTO_CAPTURED_FAILED
        )
        DeviceCommand.StartRecord -> ExpectedDeviceState(
            success = setOf(GlassesState.VIDEO_RECORDING_STARTED_SUCCESS),
            failure = CommonOperationFailureStates + GlassesState.VIDEO_RECORDING_STARTED_FAILED
        )
        DeviceCommand.StopRecord -> ExpectedDeviceState(
            success = setOf(GlassesState.VIDEO_RECORDING_STOP_SUCCESS),
            failure = CommonOperationFailureStates + GlassesState.VIDEO_RECORDING_STOP_FAILED
        )
        DeviceCommand.StartTalk -> ExpectedDeviceState(
            success = setOf(GlassesState.START_RECORD_AUDIO_SUCCESS),
            failure = CommonOperationFailureStates + GlassesState.START_RECORD_AUDIO_FAILED
        )
        DeviceCommand.StopTalk -> ExpectedDeviceState(
            success = setOf(GlassesState.STOP_RECORD_AUDIO_SUCCESS),
            failure = CommonOperationFailureStates + GlassesState.STOP_RECORD_AUDIO_FAILED
        )
    }

    private fun connectStateListener() = object : BleConnectStateListener {
        override fun onConnecteStateChange(status: Int) {
            when (status) {
                BleConnectStateListener.STATE_CONNECTED -> connectResult?.complete(true)
                BleConnectStateListener.STATE_DISCONNECTED -> connectResult?.complete(false)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectAndReadStatus(deviceId: String, discovered: ScannedDevice?): DeviceStatus {
        val listener = connectStateListener()
        runCatching { bridge.connection.setConnectStateListener(listener) }
        connectResult = CompletableDeferred()
        val useSdkAutoUserIdPairing = discovered.shouldUseSdkAutoUserIdPairing()
        runCatching { bridge.client.setSupportUserIdPair(useSdkAutoUserIdPairing) }
        val connection = withContext(Dispatchers.IO) { bridge.connect(deviceId) }
        connection.setConnectStateListener(listener)
        val connected = withTimeoutOrNull(ConnectTimeoutMillis) { connectResult?.await() } == true ||
            bridge.client.isConnected(deviceId) ||
            bridge.client.isConnected
        if (!connected) {
            withContext(Dispatchers.IO) { runCatching { bridge.disconnect() } }
            activeDevice = readStatus(deviceId, discovered, connected = false)
            return activeDevice
        }
        enableControlNotifications()
        if (discovered.requiresHeadsetAccountBinding()) {
            if (useSdkAutoUserIdPairing) {
                completeDevicePairing(connection, discovered)
            }
            connectedAtByDevice.putIfAbsent(deviceId, System.currentTimeMillis())
            activeDevice = quickStatus(deviceId, discovered, connected = true)
            return activeDevice
        }
        if (useSdkAutoUserIdPairing) {
            val paired = completeDevicePairing(connection, discovered)
            if (!paired) {
                bindPairingAccount(connection, discovered?.type ?: DeviceType.Headset)
            }
        }
        connectedAtByDevice.putIfAbsent(deviceId, System.currentTimeMillis())
        activeDevice = readStatus(deviceId, discovered, connected = true)
        return activeDevice
    }

    private fun ScannedDevice?.shouldUseSdkAutoUserIdPairing(): Boolean =
        this?.isHeadsetLike() != true && activeDevice.type != DeviceType.Headset

    private fun ScannedDevice?.requiresHeadsetAccountBinding(): Boolean =
        this?.isHeadsetLike() == true || activeDevice.type == DeviceType.Headset

    private fun quickStatus(
        deviceId: String,
        discovered: ScannedDevice?,
        connected: Boolean
    ): DeviceStatus =
        fallbackStatus.copy(
            id = deviceId,
            name = discovered?.name ?: bridge.client.deviceName.orEmpty().ifBlank { fallbackStatus.name },
            online = connected,
            signalBars = discovered?.signalBars ?: fallbackStatus.signalBars,
            onlineDuration = if (connected) formatOnlineDuration(connectedAtByDevice[deviceId]) else "连接失败",
            isRecording = activeDevice.isRecording,
            isTalking = activeDevice.isTalking,
            cloudConnected = activeDevice.cloudConnected,
            type = resolveStatusType(deviceId, discovered)
        )

    private suspend fun completeDevicePairing(
        connection: UteBleConnection,
        discovered: ScannedDevice?
    ): Boolean = coroutineScope {
        val notifyResult = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(PairingNotifyTimeoutMillis) {
                bridge.notifies
                    .filter { it.type == NotifyType.DEVICE_PAIRED_STATE_NOTIFY }
                    .mapNotNull { it.data as? DevicePairedState }
                    .first()
                    .pairedState == DevicePairedState.STATE_CONNECTED
            } == true
        }

        val requestResult = withContext(Dispatchers.IO) {
            runCatching {
                connection.requestDevicePairing(SdkPairingRequestEnable)
            }.getOrNull()
        }
        val requestAccepted = requestResult?.isSuccess == true
        val immediatePaired = requestResult?.data?.pairedState == DevicePairedState.STATE_CONNECTED
        val sdkPaired = when {
            immediatePaired -> {
                notifyResult.cancel()
                true
            }
            requestAccepted -> notifyResult.await()
            else -> {
                notifyResult.cancel()
                false
            }
        }
        if (!sdkPaired) return@coroutineScope false

        val accountBound = bindPairingAccount(connection, discovered?.type ?: activeDevice.type)
        if (accountBound && discovered?.bonded != true) {
            requestSystemBond()
        }
        accountBound
    }

    private suspend fun enableControlNotifications() {
        withContext(Dispatchers.IO) {
            runCatching { bridge.client.openOrCloseNotify(true) }
        }
        delay(NotifyEnableSettleMillis)
    }

    private suspend fun awaitHeadsetControlWarmup(deviceId: String) {
        if (activeDevice.type != DeviceType.Headset) return
        val connectedAt = connectedAtByDevice[deviceId] ?: return
        val remaining = HeadsetControlWarmupMillis - (System.currentTimeMillis() - connectedAt)
        if (remaining > 0L) delay(remaining)
    }

    private suspend fun bindPairingAccount(connection: UteBleConnection, type: DeviceType): Boolean = withContext(Dispatchers.IO) {
        val accountId = pairingAccountIdProvider().ifBlank { DefaultPairingAccountId }
        if (type == DeviceType.Headset) {
            return@withContext runCatching {
                connection.setHeadsetAccount(HeadsetAccountConfig().apply { currentHuid = accountId }).isSuccess
            }.getOrDefault(false)
        }
        runCatching {
            connection.setHonorAccount(HonorAccountConfig().apply { currentHuid = accountId }).isSuccess
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSystemBond(): Boolean {
        val bluetoothDevice = bridge.bluetoothDevice() ?: return true
        return when {
            bluetoothDevice.isBonded() -> true
            bluetoothDevice.bondState == BluetoothDevice.BOND_BONDING -> waitForSystemBond(bluetoothDevice)
            !runCatching { bluetoothDevice.createBond() }.getOrDefault(false) -> false
            else -> waitForSystemBond(bluetoothDevice)
        }
    }

    private suspend fun waitForSystemBond(bluetoothDevice: BluetoothDevice): Boolean =
        withTimeoutOrNull(SystemBondTimeoutMillis) {
            while (!bluetoothDevice.isBonded()) {
                delay(SystemBondPollMillis)
            }
            true
        } == true

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.isBonded(): Boolean =
        runCatching { bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)

    private suspend fun readStatus(
        deviceId: String,
        discovered: ScannedDevice?,
        connected: Boolean
    ): DeviceStatus = withContext(Dispatchers.IO) {
        val connection = bridge.connection
        val battery = runCatching {
            connection.smartGetBatteryInfo().takeIf { it.isSuccess }?.data?.percents
        }.getOrNull()
            ?: runCatching { connection.getBatteryInfo().takeIf { it.isSuccess }?.data?.percents }.getOrNull()
        val batteryKnown = battery != null
        val smartInfo = runCatching { connection.smartGetDeviceInfo().data }.getOrNull()
        val glassesState = runCatching { connection.getGlassesStateInfo().takeIf { it.isSuccess }?.data }.getOrNull()
        val deviceInfo = runCatching {
            connection.getDeviceInfo(DeviceInfoRequest().apply {
                address = true
                deviceBtModel = true
                deviceVersionType = true
            }).data
        }.getOrNull()
        val glassesInfo = runCatching { connection.getGlassesInfo().takeIf { it.isSuccess }?.data }.getOrNull()
        val recorderStorage = runCatching { connection.getDeviceStorageInfo().takeIf { it.isSuccess }?.data }.getOrNull()
        val store = glassesInfo?.glassesStoreInfo
        val sdkVideoRecording = when {
            glassesState?.getStateInfo(GlassesState.VIDEO_RECORDING_MODE.toLong()) == true -> true
            glassesInfo?.state == GlassesState.VIDEO_RECORDING_MODE -> true
            glassesState?.getStateInfo(GlassesState.STANDBY_MODE.toLong()) == true -> false
            glassesInfo?.state == GlassesState.STANDBY_MODE -> false
            else -> activeDevice.isRecording
        }
        val sdkAudioRecording = when {
            glassesState?.getStateInfo(GlassesState.AUDIO_RECORDING_MODE.toLong()) == true -> true
            glassesInfo?.state == GlassesState.AUDIO_RECORDING_MODE -> true
            glassesState?.getStateInfo(GlassesState.STANDBY_MODE.toLong()) == true -> false
            glassesInfo?.state == GlassesState.STANDBY_MODE -> false
            else -> activeDevice.isTalking
        }
        val readTotalGb = store?.maxSpace?.bytesToGb()?.takeIf { it > 0f }
            ?: recorderStorage?.total?.bytesToGb()?.takeIf { it > 0f }
        val totalGb = readTotalGb ?: fallbackStatus.storageTotalGb
        val freeGb = store?.freeSpace?.bytesToGb()?.coerceAtMost(totalGb)
            ?: recorderStorage?.free?.bytesToGb()?.coerceAtMost(totalGb)
            ?: (totalGb - fallbackStatus.storageUsedGb)
        val storageKnown = readTotalGb != null
        val type = resolveStatusType(
            deviceId = deviceId,
            discovered = discovered,
            hasSmartDeviceInfo = smartInfo != null,
            hasHeadsetDeviceInfo = deviceInfo != null
        )
        val firmware = when (type) {
            DeviceType.Headset -> smartInfo?.headSetVersion
                ?: smartInfo?.glassesVersion
                ?: deviceInfo?.deviceVersion
                ?: fallbackStatus.firmware
            else -> smartInfo?.glassesVersion
                ?: smartInfo?.headSetVersion
                ?: deviceInfo?.deviceVersion
                ?: fallbackStatus.firmware
        }

        fallbackStatus.copy(
            id = deviceId,
            name = discovered?.name ?: bridge.client.deviceName.orEmpty().ifBlank { fallbackStatus.name },
            online = connected,
            battery = (battery ?: fallbackStatus.battery).coerceIn(0, 100),
            signalBars = discovered?.signalBars ?: fallbackStatus.signalBars,
            onlineDuration = if (connected) formatOnlineDuration(connectedAtByDevice[deviceId]) else "连接失败",
            storageUsedGb = (totalGb - freeGb).coerceAtLeast(0f),
            storageTotalGb = totalGb,
            firmware = firmware,
            isRecording = sdkVideoRecording,
            isTalking = sdkAudioRecording,
            cloudConnected = activeDevice.cloudConnected,
            type = type,
            batteryKnown = batteryKnown,
            storageKnown = storageKnown
        )
    }

    private fun resolveStatusType(
        deviceId: String,
        discovered: ScannedDevice?,
        hasSmartDeviceInfo: Boolean = false,
        hasHeadsetDeviceInfo: Boolean = false
    ): DeviceType {
        if (discovered?.isHeadsetLike() == true) return DeviceType.Headset
        if (activeDevice.id.equals(deviceId, ignoreCase = true) && activeDevice.type == DeviceType.Headset) return DeviceType.Headset
        val sdkName = bridge.client.deviceName.orEmpty()
        if (isKnownPatrolAudioName(sdkName)) return DeviceType.Headset
        val pairedAudio = systemBluetoothAudioDevices().any { system ->
            system.id.equals(deviceId, ignoreCase = true) ||
                hasSimilarPatrolAudioName(system.name, discovered?.name ?: sdkName)
        }
        if (pairedAudio) return DeviceType.Headset
        if (hasHeadsetDeviceInfo && !hasSmartDeviceInfo) return DeviceType.Headset
        discovered?.type?.let { return it }
        if (activeDevice.id.equals(deviceId, ignoreCase = true)) return activeDevice.type
        return if (hasSmartDeviceInfo) DeviceType.Glasses else fallbackStatus.type
    }

    @SuppressLint("MissingPermission")
    private fun UteScanDevice.toScannedDevice(): ScannedDevice? {
        val bluetoothDevice = device ?: return null
        val name = runCatching { bluetoothDevice.name }.getOrNull().orEmpty()
        if (name.isBlank()) return null
        if (!isSupportedUteDevice(name, scanRecord)) return null
        val id = bluetoothDevice.address ?: return null
        val bonded = runCatching {
            bluetoothDevice.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED
        }.getOrDefault(false)
        return ScannedDevice(
            id = id,
            name = name,
            signalBars = rssi.toSignalBars(),
            serviceUuid = UteBleControlScanned,
            bonded = bonded,
            macAddress = id,
            type = name.toPatrolDeviceType(scanRecord)
        )
    }

    private fun isSupportedUteDevice(name: String, scanRecord: ByteArray?): Boolean {
        val hex = scanRecord?.joinToString(" ") { "%02X".format(it) }.orEmpty()
        return hex.contains("55 55") ||
            hex.contains("3A 55") ||
            name.startsWith("AT", ignoreCase = true) ||
            name.startsWith("ABA002", ignoreCase = true) ||
            name.startsWith("Tic", ignoreCase = true) ||
            isKnownPatrolAudioName(name)
    }

    private fun String.toPatrolDeviceType(scanRecord: ByteArray?): DeviceType {
        val normalized = uppercase()
        val hex = scanRecord?.joinToString(" ") { "%02X".format(it) }.orEmpty()
        return when {
            isKnownPatrolAudioName(this) -> DeviceType.Headset
            "ABA002" in normalized || "GLASS" in normalized || "眼镜" in normalized || hex.contains("3A 55") -> DeviceType.Glasses
            "RECORDER" in normalized || "AI" in normalized && "REC" in normalized -> DeviceType.Recorder
            else -> DeviceType.Headset
        }
    }

    private fun ScannedDevice.isHeadsetLike(): Boolean =
        type == DeviceType.Headset ||
            isKnownPatrolAudioName(name) ||
            serviceUuid == SystemBluetoothAudioConnected ||
            serviceUuid == SystemBluetoothAudioBonded ||
            serviceUuid == SystemBluetoothAudioControlConnected

    private fun Int.toSignalBars(): Int = when {
        this >= -55 -> 5
        this >= -67 -> 4
        this >= -75 -> 3
        this >= -85 -> 2
        else -> 1
    }

    private fun mergedScannedDevices(): List<ScannedDevice> {
        val systemDevices = systemBluetoothAudioDevices()
        val connectedSystemHeadset = systemDevices.firstOrNull { it.isSystemAudioConnected() }
        val sdkDevices = scanned.values.filterNot { it.isSystemAudioDevice() }
        val sdkHeadsets = sdkDevices.filter { device ->
            device.type == DeviceType.Headset && isKnownPatrolAudioName(device.name)
        }
        val sdkHeadset = connectedSystemHeadset?.let { systemHeadset ->
            sdkHeadsets.preferredControlHeadset(systemHeadset)
                ?.takeIf { candidate -> candidate.matchesSystemHeadset(systemHeadset) }
        }
        val output = linkedMapOf<String, ScannedDevice>()
        sdkDevices.forEach { device ->
            if (connectedSystemHeadset == null || device !in sdkHeadsets) {
                output[device.id] = device
            }
        }
        if (sdkHeadset != null) {
            output[sdkHeadset.id] = sdkHeadset.copy(
                name = connectedSystemHeadset?.name?.takeIf { it.isNotBlank() } ?: sdkHeadset.name,
                signalBars = maxOf(sdkHeadset.signalBars, connectedSystemHeadset?.signalBars ?: 0),
                serviceUuid = SystemBluetoothAudioControlConnected,
                bonded = true
            )
        } else {
            systemDevices.forEach { output[it.id] = it }
        }
        return output.values.sortedWith(
            compareByDescending<ScannedDevice> { it.isSystemAudioConnected() }
                .thenByDescending { it.bonded && it.name.startsWith("E1", ignoreCase = true) }
                .thenByDescending { it.name.startsWith("E1", ignoreCase = true) }
                .thenByDescending { it.serviceUuid.startsWith("system-bluetooth-audio") }
                .thenByDescending { it.signalBars }
        )
    }

    private fun ScannedDevice.matchesSystemHeadset(systemHeadset: ScannedDevice): Boolean =
        id.equals(systemHeadset.id, ignoreCase = true) || name.equals(systemHeadset.name, ignoreCase = true)

    private fun List<ScannedDevice>.preferredControlHeadset(systemHeadset: ScannedDevice): ScannedDevice? =
        sortedWith(
            compareByDescending<ScannedDevice> { it.id.equals(systemHeadset.id, ignoreCase = true) }
                .thenByDescending { it.name == systemHeadset.name }
                .thenByDescending { it.name.startsWith("E1", ignoreCase = true) }
                .thenByDescending { it.name.startsWith("SMI", ignoreCase = true) }
                .thenByDescending { it.signalBars }
        ).firstOrNull()

    @SuppressLint("MissingPermission")
    private fun systemBluetoothAudioDevices(): List<ScannedDevice> {
        if (!bridge.hasBluetoothConnectPermission()) return emptyList()
        val adapter = bluetoothAdapter() ?: return emptyList()
        val audioProfileConnected = adapter.hasConnectedAudioProfile()
        return runCatching { adapter.bondedDevices.orEmpty() }
            .getOrDefault(emptySet())
            .filter { device ->
                val name = runCatching { device.name }.getOrNull().orEmpty()
                name.isNotBlank() && (isKnownPatrolAudioName(name) || device.isConnectedBySystemBluetooth(audioProfileConnected))
            }
            .mapNotNull { device ->
                val address = runCatching { device.address }.getOrNull() ?: return@mapNotNull null
                val name = runCatching { device.name }.getOrNull().orEmpty().ifBlank { "蓝牙耳机" }
                val connected = device.isConnectedBySystemBluetooth(audioProfileConnected)
                ScannedDevice(
                    id = address,
                    name = name,
                    signalBars = if (connected) 5 else 3,
                    serviceUuid = if (connected) SystemBluetoothAudioConnected else SystemBluetoothAudioBonded,
                    bonded = true,
                    macAddress = address,
                    type = DeviceType.Headset
                )
            }
    }

    private fun bluetoothAdapter(): BluetoothAdapter? =
        (bridge.appContext.getSystemService(BluetoothManager::class.java))?.adapter

    private fun isKnownPatrolAudioName(name: String): Boolean {
        val normalized = name.uppercase()
        return "E1-PRO" in normalized ||
            "SMI-" in normalized ||
            "FORCELINK" in normalized ||
            "HEADSET" in normalized ||
            "耳机" in name
    }

    private fun hasSimilarPatrolAudioName(left: String, right: String): Boolean {
        if (left.isBlank() || right.isBlank()) return false
        val leftNormalized = left.uppercase()
        val rightNormalized = right.uppercase()
        return listOf("E1-PRO", "SMI-", "FORCELINK", "HEADSET", "耳机").any { marker ->
            marker in leftNormalized && marker in rightNormalized
        }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.isConnectedBySystemBluetooth(audioProfileConnected: Boolean): Boolean {
        val hiddenConnected = runCatching {
            javaClass.getMethod("isConnected").invoke(this) as? Boolean == true
        }.getOrDefault(false)
        if (hiddenConnected) return true
        val name = runCatching { name }.getOrNull().orEmpty()
        return audioProfileConnected && isKnownPatrolAudioName(name)
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothAdapter.hasConnectedAudioProfile(): Boolean =
        runCatching {
            getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothAdapter.STATE_CONNECTED ||
                getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED
        }.getOrDefault(false)

    private fun ScannedDevice.isSystemAudioDevice(): Boolean =
        serviceUuid == SystemBluetoothAudioConnected || serviceUuid == SystemBluetoothAudioBonded

    private fun ScannedDevice.isSystemAudioConnected(): Boolean =
        serviceUuid == SystemBluetoothAudioConnected || serviceUuid == SystemBluetoothAudioControlConnected

    private fun systemAudioStatus(discovered: ScannedDevice): DeviceStatus =
        fallbackStatus.copy(
            id = discovered.id,
            name = discovered.name,
            online = discovered.isSystemAudioConnected() || discovered.bonded,
            battery = fallbackStatus.battery,
            signalBars = discovered.signalBars,
            onlineDuration = if (discovered.isSystemAudioConnected()) "系统蓝牙已连接" else "系统蓝牙已配对",
            type = DeviceType.Headset
        )

    private fun formatOnlineDuration(connectedAtMillis: Long?): String {
        val connectedAt = connectedAtMillis ?: return "刚刚连接"
        val elapsedSeconds = ((System.currentTimeMillis() - connectedAt).coerceAtLeast(0L) / 1000L).toInt()
        return when {
            elapsedSeconds < 60 -> "刚刚连接"
            elapsedSeconds < 3600 -> "${elapsedSeconds / 60}分钟"
            else -> "${elapsedSeconds / 3600}小时${(elapsedSeconds % 3600) / 60}分钟"
        }
    }

    private fun Long.bytesToGb(): Float = this / 1024f / 1024f / 1024f

    private data class ExpectedDeviceState(
        val success: Set<Int>,
        val failure: Set<Int>
    )

    private companion object {
        const val SystemBluetoothAudioConnected = "system-bluetooth-audio-connected"
        const val SystemBluetoothAudioBonded = "system-bluetooth-audio-bonded"
        const val SystemBluetoothAudioControlConnected = "system-bluetooth-audio-control-connected"
        const val UteBleControlScanned = "ute-ble-control-scanned"
        const val ScanMillis = 10_000L
        const val ConnectTimeoutMillis = 15_000L
        const val PairingNotifyTimeoutMillis = 12_000L
        const val CommandStateTimeoutMillis = 20_000L
        const val PhotoDataTimeoutMillis = 60_000L
        const val PhotoInlineSyncTimeoutMillis = 8_000L
        const val NotifyEnableSettleMillis = 250L
        const val HeadsetControlWarmupMillis = 8_000L
        const val SystemBondTimeoutMillis = 20_000L
        const val SystemBondPollMillis = 250L
        const val SdkPairingRequestEnable = 1
        const val DefaultPairingAccountId = "patrollink-local-operator"
        val CommonOperationFailureStates = setOf(
            GlassesState.STORAGE_SPACE_FULL,
            GlassesState.GLASSES_OPERATION_FAILED,
            GlassesState.ISP_SD_FAILED_LOAD,
            GlassesState.ISP_CAMERA_ABNORMALITY,
            GlassesState.ISP_CAMERA_NOT_TURNED_ON
        )
    }
}
