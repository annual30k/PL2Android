package com.patrollink.data.ute

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.yc.nadalsdk.bean.DeviceBt3StateInfo
import com.patrollink.domain.DeviceCommand
import com.patrollink.domain.DeviceGateway
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.DeviceType
import com.patrollink.domain.GpsLocation
import com.patrollink.domain.ScannedDevice
import com.yc.nadalsdk.bean.DeviceInfoRequest
import com.yc.nadalsdk.bean.DevicePairedState
import com.yc.nadalsdk.bean.HonorAccountConfig
import com.yc.nadalsdk.bean.Response
import com.yc.nadalsdk.bean.recorder.AudioRecordInfo
import com.yc.nadalsdk.bean.recorder.AudioRecordStopInfo
import com.yc.nadalsdk.bean.smart.GlassesStateInfo
import com.yc.nadalsdk.bean.smart.HeadsetAccountConfig
import com.yc.nadalsdk.bean.smart.SmartImageDataInfo
import com.yc.nadalsdk.bean.smart.SmartGpsInfo
import com.yc.nadalsdk.ble.open.DeviceModeJX
import com.yc.nadalsdk.ble.open.UteBleConnection
import com.yc.nadalsdk.constants.NotifyType
import com.yc.nadalsdk.constants.recorder.AudioRecordResult
import com.yc.nadalsdk.constants.smart.GlassesHeadsetRecordingState
import com.yc.nadalsdk.constants.smart.GlassesState
import com.yc.nadalsdk.listener.BleConnectStateListener
import com.yc.nadalsdk.scan.UteScanCallback
import com.yc.nadalsdk.scan.UteScanDevice
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
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
    private val pairingAccountIdProvider: () -> String = { DefaultPairingAccountId },
    private val photoLocationProvider: suspend () -> GpsLocation? = { null }
) : DeviceGateway {
    private val scanned = ConcurrentHashMap<String, ScannedDevice>()
    private val connectedAtByDevice = ConcurrentHashMap<String, Long>()
    private val lastKnownBatteryByDevice = ConcurrentHashMap<String, Int>()
    private val lastKnownStorageByDevice = ConcurrentHashMap<String, StorageSnapshot>()
    private val accountBoundByDevice = ConcurrentHashMap<String, Boolean>()
    private val photoCommandInFlightByDevice = ConcurrentHashMap<String, Long>()
    private val lastPhotoCommandFinishedAtByDevice = ConcurrentHashMap<String, Long>()
    private val statusCache by lazy {
        bridge.appContext.getSharedPreferences(StatusCacheName, Context.MODE_PRIVATE)
    }
    private val mutex = Mutex()
    private var activeDevice: DeviceStatus = fallbackStatus
    private var connectResult: CompletableDeferred<Boolean>? = null
    private var aiRecorderSessionId: String? = null

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
            if (!systemDiscovered.isSystemAudioConnected()) {
                activeDevice = systemAudioStatus(systemDiscovered)
                return activeDevice
            }
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
        accountBoundByDevice.remove(deviceId)
        photoCommandInFlightByDevice.remove(deviceId)
        lastPhotoCommandFinishedAtByDevice.remove(deviceId)
        activeDevice = fallbackStatus.copy(id = deviceId, online = false, isRecording = false, isTalking = false)
        activeDevice
    }

    override suspend fun sendCommand(deviceId: String, command: DeviceCommand): DeviceStatus {
        val guardedPhoto = command == DeviceCommand.TakePhoto
        if (guardedPhoto) {
            val now = System.currentTimeMillis()
            val lastFinishedAt = lastPhotoCommandFinishedAtByDevice[deviceId] ?: 0L
            check(now - lastFinishedAt >= PhotoCommandSettleMillis) { "photo command is still settling" }
            check(photoCommandInFlightByDevice.putIfAbsent(deviceId, now) == null) { "photo command already in progress" }
        }
        return try {
            mutex.withLock {
                if (!bridge.hasBluetoothConnectPermission()) {
                    error("bluetooth connect permission missing")
                }
                val discovered = scanned[deviceId]
                if (!bridge.client.isConnected || activeDevice.id != deviceId || !activeDevice.online) {
                    val connectedStatus = connectAndReadStatus(deviceId, discovered)
                    check(connectedStatus.online) { "device control channel disconnected" }
                }
                var connection = bridge.connection
                prepareCommandControl(connection, deviceId, command)
                var commandResult = executeClosedCommand(connection, command)
                val acceptedPhotoWithoutTransfer = command == DeviceCommand.TakePhoto && commandResult.accepted
                if (!commandResult.success && activeDevice.type == DeviceType.Headset && !acceptedPhotoWithoutTransfer) {
                    Log.w(SdkCommandLogTag, "command failed after control retry precheck, reconnecting once: ${commandResult.detail}")
                    withContext(Dispatchers.IO) { runCatching { bridge.disconnect() } }
                    connectResult = null
                    connectedAtByDevice.remove(deviceId)
                    val reconnected = connectAndReadStatus(deviceId, discovered)
                    check(reconnected.online) { "device control channel disconnected after command retry" }
                    connection = bridge.connection
                    prepareCommandControl(connection, deviceId, command)
                    commandResult = executeClosedCommand(connection, command)
                }
                check(commandResult.success || acceptedPhotoWithoutTransfer) { commandResult.detail }
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
        } finally {
            if (guardedPhoto) {
                photoCommandInFlightByDevice.remove(deviceId)
                lastPhotoCommandFinishedAtByDevice[deviceId] = System.currentTimeMillis()
            }
        }
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
                DeviceCommand.TakePhoto -> photoCommandAttempts(connection)
                DeviceCommand.StartRecord -> listOf(
                    runCommandAttempt("toggleGlassesVideoRecording(start)", acceptTimeoutAsSuccess = true) {
                        connection.toggleGlassesVideoRecording(GlassesHeadsetRecordingState.RECORDING_STATE_START)
                    }
                )
                DeviceCommand.StopRecord -> listOf(
                    runCommandAttempt("toggleGlassesVideoRecording(stop)", acceptTimeoutAsSuccess = true) {
                        connection.toggleGlassesVideoRecording(GlassesHeadsetRecordingState.RECORDING_STATE_STOP)
                    }
                )
                DeviceCommand.StartTalk -> audioRecordCommandAttempts(connection, start = true)
                DeviceCommand.StopTalk -> audioRecordCommandAttempts(connection, start = false)
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
            val uploaded = imageResult?.let {
                withTimeoutOrNull(PhotoInlineSyncTimeoutMillis) { it.await() }
            } == true
            val stateConfirmed = if (uploaded) {
                stateResult.cancel()
                false
            } else {
                withTimeoutOrNull(PhotoStateGraceMillis) { stateResult.await() } == true
            }
            val retryUploaded = if (uploaded || stateConfirmed) {
                imageResult?.cancel()
                false
            } else {
                imageResult?.cancel()
                stateResult.cancel()
                retryImageUploadAndConfirm(connection)
            }
            return@coroutineScope CommandExecutionResult(
                success = stateConfirmed || uploaded || retryUploaded,
                detail = "$detail; stateConfirmed=$stateConfirmed,inlineUpload=$uploaded,retryUpload=$retryUploaded",
                attempts = attempts,
                accepted = commandAccepted
            )
        }
        val stateConfirmed = stateResult.await()
        imageResult?.cancel()
        CommandExecutionResult(
            success = stateConfirmed || command.isAsyncHeadsetCommand(),
            detail = "$detail; stateConfirmed=$stateConfirmed,acceptedWithoutState=${!stateConfirmed && command.isAsyncHeadsetCommand()}",
            attempts = attempts
        )
    }

    private inline fun runCommandAttempt(
        name: String,
        acceptTimeoutAsSuccess: Boolean = false,
        block: () -> Response<*>
    ): CommandAttempt =
        runCatching {
            val response = block()
            val timeoutAccepted = acceptTimeoutAsSuccess &&
                response.errorCode == SdkTimeoutError &&
                bridge.client.isConnected
            val dataAccepted = response.data?.toString() == "true"
            val success = response.isSuccess || dataAccepted || timeoutAccepted
            Log.i(
                SdkCommandLogTag,
                "$name accepted=$success,sdkSuccess=${response.isSuccess},error=${response.errorCode},data=${response.data},timeoutAccepted=$timeoutAccepted"
            )
            CommandAttempt(
                name = name,
                success = success,
                errorCode = response.errorCode,
                data = response.data?.toString()
            )
        }.getOrElse { throwable ->
            Log.w(SdkCommandLogTag, "$name failed: ${throwable.message}", throwable)
            CommandAttempt(
                name = name,
                success = false,
                errorCode = null,
                data = throwable.message.orEmpty().ifBlank { throwable::class.java.simpleName }
            )
        }

    private suspend fun photoCommandAttempts(connection: UteBleConnection): List<CommandAttempt> {
        val gpsInfo = currentPhotoGpsInfo()
        if (activeDevice.type == DeviceType.Headset || activeDevice.type == DeviceType.Glasses) {
            return listOf(
                runCommandAttempt("triggerGlassesPhotoCapture(gps=${gpsInfo != null})") {
                    connection.triggerGlassesPhotoCapture(gpsInfo)
                }
            )
        }
        return listOf(
            runCommandAttempt("triggerGlassesPhotoCapture(gps=${gpsInfo != null})") {
                connection.triggerGlassesPhotoCapture(gpsInfo)
            }
        )
    }

    private suspend fun currentPhotoGpsInfo(): SmartGpsInfo? {
        val location: GpsLocation = withTimeoutOrNull(PhotoGpsTimeoutMillis) {
            try {
                photoLocationProvider()
            } catch (_: Throwable) {
                null
            }
        }?.takeIf { it.isUsableForSmartPhoto() } ?: run {
            Log.i(SdkCommandLogTag, "photo gps unavailable, sending SDK photo command without gps")
            return null
        }
        Log.i(
            SdkCommandLogTag,
            "photo gps latitude=${String.format(Locale.US, "%.6f", location.latitude)},longitude=${String.format(Locale.US, "%.6f", location.longitude)}"
        )
        return SmartGpsInfo().apply {
            setLatitude(location.latitude)
            setLongitude(location.longitude)
        }
    }

    private fun GpsLocation.isUsableForSmartPhoto(): Boolean =
        latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 &&
            (latitude != 0.0 || longitude != 0.0)

    private fun DeviceCommand.isAsyncHeadsetCommand(): Boolean =
        this == DeviceCommand.StartRecord ||
            this == DeviceCommand.StopRecord ||
            this == DeviceCommand.StartTalk ||
            this == DeviceCommand.StopTalk

    private fun audioRecordCommandAttempts(connection: UteBleConnection, start: Boolean): List<CommandAttempt> {
        val attempts = mutableListOf<CommandAttempt>()
        if (supportsAiRecorder()) {
            attempts += if (start) {
                runAudioStartAttempt(connection)
            } else {
                runAudioStopAttempt(connection)
            }
            if (attempts.lastOrNull()?.success == true) return attempts
        }
        attempts += runCommandAttempt(
            name = if (start) "toggleHeadsetAudioRecording(start)" else "toggleHeadsetAudioRecording(stop)",
            acceptTimeoutAsSuccess = true
        ) {
            connection.toggleHeadsetAudioRecording(
                if (start) {
                    GlassesHeadsetRecordingState.RECORDING_STATE_START
                } else {
                    GlassesHeadsetRecordingState.RECORDING_STATE_STOP
                }
            )
        }
        return attempts
    }

    private fun runAudioStartAttempt(connection: UteBleConnection): CommandAttempt =
        runCatching {
            val response = connection.appStartAudioRecord()
            val success = response.isAudioStartAccepted()
            if (success) aiRecorderSessionId = response.data?.sessionId ?: aiRecorderSessionId
            Log.i(
                SdkCommandLogTag,
                "appStartAudioRecord accepted=$success,sdkSuccess=${response.isSuccess},error=${response.errorCode},session=${response.data?.sessionId},result=${response.data?.result}"
            )
            CommandAttempt(
                name = "appStartAudioRecord",
                success = success,
                errorCode = response.errorCode,
                data = "session=${response.data?.sessionId.orEmpty()},result=${response.data?.result}"
            )
        }.getOrElse { throwable ->
            Log.w(SdkCommandLogTag, "appStartAudioRecord failed: ${throwable.message}", throwable)
            CommandAttempt("appStartAudioRecord", success = false, errorCode = null, data = throwable.message.orEmpty())
        }

    private fun runAudioStopAttempt(connection: UteBleConnection): CommandAttempt =
        runCatching {
            val response = connection.appStopAudioRecord()
            val success = response.isAudioStopAccepted()
            if (success) aiRecorderSessionId = null
            Log.i(
                SdkCommandLogTag,
                "appStopAudioRecord accepted=$success,sdkSuccess=${response.isSuccess},error=${response.errorCode},fileExist=${response.data?.fileExist},fileSize=${response.data?.fileSize}"
            )
            CommandAttempt(
                name = "appStopAudioRecord",
                success = success,
                errorCode = response.errorCode,
                data = "fileExist=${response.data?.fileExist},fileSize=${response.data?.fileSize}"
            )
        }.getOrElse { throwable ->
            Log.w(SdkCommandLogTag, "appStopAudioRecord failed: ${throwable.message}", throwable)
            CommandAttempt("appStopAudioRecord", success = false, errorCode = null, data = throwable.message.orEmpty())
        }

    private fun Response<AudioRecordInfo>.isAudioStartAccepted(): Boolean {
        val result = data?.result ?: return isSuccess
        return result == AudioRecordResult.RECORD_START_SUCCESS || result == AudioRecordResult.RECORD_ALREADY_IN_PROGRESS
    }

    private fun Response<AudioRecordStopInfo>.isAudioStopAccepted(): Boolean =
        data != null || isSuccess

    private fun supportsAiRecorder(): Boolean =
        DeviceModeJX.isHasFunction_4(DeviceModeJX.IS_SUPPORT_AI_RECORDER_MEETING_RECORDING)

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
        val attempts: List<CommandAttempt> = emptyList(),
        val accepted: Boolean = attempts.lastOrNull()?.success == true
    )

    private suspend fun awaitGlassesState(successStates: Set<Int>, failureStates: Set<Int>): Boolean =
        withTimeoutOrNull(CommandStateTimeoutMillis) {
            bridge.notifies
                .filter { it.type == NotifyType.SMART_GLASSES_STATE_NOTIFY }
                .mapNotNull { notify -> notify.data.toGlassesStateBits() }
                .mapNotNull { stateBits ->
                    when {
                        successStates.any { stateBits.hasStateFlag(it) } -> true
                        failureStates.any { stateBits.hasStateFlag(it) } -> false
                        else -> null
                    }
                }
                .first()
        } == true

    private fun Any?.toGlassesStateBits(): Long? =
        when (this) {
            is GlassesStateInfo -> state
            is Int -> toLong()
            is Long -> this
            else -> null
        }

    private fun Long.hasStateFlag(flag: Int): Boolean =
        this and flag.toLong() != 0L

    private suspend fun awaitImageDataAndConfirm(connection: UteBleConnection): Boolean {
        val image = withTimeoutOrNull(PhotoDataTimeoutMillis) {
            bridge.notifies
                .filter { it.type == NotifyType.SMART_GLASSES_IMAGE_DATA_NOTIFY }
                .mapNotNull { it.data as? SmartImageDataInfo }
                .mapNotNull { it.takeIf { image -> image.crcSuccess == true } }
                .first()
        }
        if (image == null) return false
        persistImageData(image)
        withContext(Dispatchers.IO) {
            runCatching { connection.notifyMediaSyncCompleted() }
        }
        return true
    }

    private suspend fun retryImageUploadAndConfirm(connection: UteBleConnection): Boolean = coroutineScope {
        val imageResult = async(start = CoroutineStart.UNDISPATCHED) {
            awaitImageDataAndConfirm(connection)
        }
        val requested = withContext(Dispatchers.IO) {
            runCatching {
                bridge.client.openOrCloseNotify(true)
                connection.retryImageUpload().isSuccess
            }.getOrDefault(false)
        }
        if (!requested) {
            imageResult.cancel()
            return@coroutineScope false
        }
        val uploaded = withTimeoutOrNull(PhotoRetryUploadTimeoutMillis) { imageResult.await() } == true
        if (!uploaded) imageResult.cancel()
        uploaded
    }

    private suspend fun persistImageData(image: SmartImageDataInfo) = withContext(Dispatchers.IO) {
        val source = image.file?.takeIf { it.exists() && it.isFile && it.length() > 0 } ?: return@withContext
        val directory = mediaDirectory ?: return@withContext
        source.persistUniqueSmartMedia(directory, "glasses-photo", image.imaType.orEmpty(), "jpg")
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
    private suspend fun connectAndReadStatus(deviceId: String, discovered: ScannedDevice?): DeviceStatus = coroutineScope {
        val listener = connectStateListener()
        runCatching { bridge.connection.setConnectStateListener(listener) }
        connectResult = CompletableDeferred()
        val useSdkAutoUserIdPairing = discovered.shouldUseSdkAutoUserIdPairing()
        runCatching { bridge.client.setSupportUserIdPair(useSdkAutoUserIdPairing) }
        val pendingPairingNotify = if (useSdkAutoUserIdPairing) {
            async(start = CoroutineStart.UNDISPATCHED) { awaitDevicePairingAccepted() }
        } else {
            null
        }
        val connection = withContext(Dispatchers.IO) { bridge.connect(deviceId) }
        connection.setConnectStateListener(listener)
        val connectedBeforeAccount = withTimeoutOrNull(PreAccountConnectTimeoutMillis) { connectResult?.await() } == true ||
            bridge.client.isConnected(deviceId) ||
            bridge.client.isConnected
        if (useSdkAutoUserIdPairing) {
            val paired = completeDevicePairing(
                connection = connection,
                discovered = discovered,
                deviceId = deviceId,
                pendingNotify = pendingPairingNotify
            )
            if (!paired) {
                Log.w(SdkCommandLogTag, "device pairing/account binding not confirmed before connect callback")
            }
        }
        pendingPairingNotify?.cancel()
        val connected = connectedBeforeAccount ||
            withTimeoutOrNull(ConnectTimeoutMillis) { connectResult?.await() } == true ||
            bridge.client.isConnected(deviceId) ||
            bridge.client.isConnected
        if (!connected) {
            withContext(Dispatchers.IO) { runCatching { bridge.disconnect() } }
            activeDevice = quickStatus(deviceId, discovered, connected = false)
            return@coroutineScope activeDevice
        }
        enableControlNotifications()
        if (discovered.requiresHeadsetAccountBinding()) {
            connectedAtByDevice.putIfAbsent(deviceId, System.currentTimeMillis())
            bindPairingAccount(connection, discovered?.type ?: DeviceType.Headset, deviceId)
            logHeadsetControlState(connection, "afterHeadsetSystemPairing")
            activeDevice = quickStatus(deviceId, discovered, connected = true)
            return@coroutineScope activeDevice
        }
        if (!useSdkAutoUserIdPairing) {
            bindPairingAccount(connection, discovered?.type ?: DeviceType.Headset, deviceId)
        }
        connectedAtByDevice.putIfAbsent(deviceId, System.currentTimeMillis())
        activeDevice = readStatus(deviceId, discovered, connected = true)
        return@coroutineScope activeDevice
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
        discovered: ScannedDevice?,
        deviceId: String = activeDevice.id,
        pendingNotify: Deferred<Boolean>? = null
    ): Boolean = coroutineScope {
        val notifyResult = pendingNotify ?: async(start = CoroutineStart.UNDISPATCHED) { awaitDevicePairingAccepted() }

        val requestResult = withContext(Dispatchers.IO) {
            runCatching {
                connection.requestDevicePairing(SdkPairingRequestEnable)
            }.getOrNull()
        }
        Log.i(
            SdkCommandLogTag,
            "requestDevicePairing success=${requestResult?.isSuccess},error=${requestResult?.errorCode},paired=${requestResult?.data?.pairedState}"
        )
        val requestAccepted = requestResult?.isSuccess == true
        val immediatePaired = requestResult?.data?.pairedState == DevicePairedState.STATE_CONNECTED
        val notifyAlreadyPaired = notifyResult.takeIf { it.isCompleted }
            ?.let { runCatching { it.await() }.getOrDefault(false) } == true
        val sdkPaired = when {
            immediatePaired -> {
                notifyResult.cancel()
                true
            }
            notifyAlreadyPaired -> true
            requestAccepted -> notifyResult.await()
            else -> {
                withTimeoutOrNull(PairingNotifyGraceMillis) { notifyResult.await() } == true
            }
        }
        if (!sdkPaired) notifyResult.cancel()
        if (!sdkPaired) return@coroutineScope false

        val accountBound = bindPairingAccount(connection, discovered?.type ?: activeDevice.type, deviceId)
        if (accountBound && discovered?.bonded != true) {
            requestSystemBond()
        }
        accountBound
    }

    private suspend fun awaitDevicePairingAccepted(): Boolean =
        withTimeoutOrNull(PairingNotifyTimeoutMillis) {
            bridge.notifies
                .filter { it.type == NotifyType.DEVICE_PAIRED_STATE_NOTIFY }
                .mapNotNull { it.data as? DevicePairedState }
                .first()
                .pairedState == DevicePairedState.STATE_CONNECTED
        } == true

    private suspend fun enableControlNotifications() {
        withContext(Dispatchers.IO) {
            runCatching { bridge.client.openOrCloseNotify(true) }
        }
        delay(NotifyEnableSettleMillis)
    }

    private suspend fun prepareCommandControl(connection: UteBleConnection, deviceId: String, command: DeviceCommand) {
        enableControlNotifications()
        if (activeDevice.type !in setOf(DeviceType.Headset, DeviceType.Glasses)) return
        if (accountBoundByDevice[deviceId] != true) {
            val accountReady = bindPairingAccount(connection, activeDevice.type, deviceId)
            if (!accountReady) {
                Log.w(SdkCommandLogTag, "beforeCommand account binding not ready for command=$command")
            }
        }
        awaitHeadsetControlWarmup(deviceId)
        Log.i(SdkCommandLogTag, "beforeCommand command=$command,control=systemBluetoothPairing")
    }

    private suspend fun awaitHeadsetControlWarmup(deviceId: String) {
        if (activeDevice.type != DeviceType.Headset) return
        val connectedAt = connectedAtByDevice[deviceId] ?: return
        val remaining = HeadsetControlWarmupMillis - (System.currentTimeMillis() - connectedAt)
        if (remaining > 0L) delay(remaining)
    }

    private suspend fun bindPairingAccount(
        connection: UteBleConnection,
        type: DeviceType,
        deviceId: String
    ): Boolean = withContext(Dispatchers.IO) {
        val accountId = pairingAccountIdProvider().ifBlank { DefaultPairingAccountId }
        val honorBound = runCatching {
            val response = connection.setHonorAccount(HonorAccountConfig().apply { currentHuid = accountId })
            val accepted = response.isSuccess && response.data.isHonorAccountAccepted()
            Log.i(
                SdkCommandLogTag,
                "setHonorAccount success=${response.isSuccess},accepted=$accepted,error=${response.errorCode},status=${response.data?.accountJudgmentStatus},account=${accountId.take(8)}..."
            )
            accepted
        }.getOrDefault(false)
        val headsetBound = if (type == DeviceType.Headset) {
            runCatching {
                val firstResponse = connection.setHeadsetAccount(HeadsetAccountConfig().apply { currentHuid = accountId })
                val response = if (firstResponse.data?.accountJudgmentStatus == HeadsetAccountConfig.ACCOUNT_DIFFERENT) {
                    delay(AccountRebindConfirmWaitMillis)
                    connection.setHeadsetAccount(HeadsetAccountConfig().apply { currentHuid = accountId })
                } else {
                    firstResponse
                }
                val accepted = response.isSuccess && response.data.isHeadsetAccountAccepted()
                Log.i(
                    SdkCommandLogTag,
                    "setHeadsetAccount success=${response.isSuccess},accepted=$accepted,error=${response.errorCode},status=${response.data?.accountJudgmentStatus},account=${accountId.take(8)}..."
                )
                accepted
            }.getOrDefault(false)
        } else {
            false
        }
        val bound = if (type == DeviceType.Headset) headsetBound || honorBound else honorBound
        if (bound) accountBoundByDevice[deviceId] = true
        bound
    }

    private fun HonorAccountConfig?.isHonorAccountAccepted(): Boolean =
        this == null ||
            accountJudgmentStatus == HonorAccountConfig.ACCOUNT_SAME ||
            accountJudgmentStatus == HonorAccountConfig.ACCOUNT_NO

    private fun HeadsetAccountConfig?.isHeadsetAccountAccepted(): Boolean =
        this == null ||
            accountJudgmentStatus == HeadsetAccountConfig.ACCOUNT_SAME ||
            accountJudgmentStatus == HeadsetAccountConfig.ACCOUNT_NO

    private suspend fun logHeadsetControlState(connection: UteBleConnection, reason: String) = withContext(Dispatchers.IO) {
        val bt3 = runCatching { connection.queryDeviceBt3State().data }.getOrNull()
        Log.i(SdkCommandLogTag, "$reason bt3=${bt3.toBt3Summary()}")
        val glassesInfo = runCatching { connection.getGlassesInfo().data }.getOrNull()
        val store = glassesInfo?.glassesStoreInfo
        Log.i(
            SdkCommandLogTag,
            "$reason glassesState=${glassesInfo?.state},photos=${store?.newTakenPictures}/${store?.totalPictures},audio=${store?.newRecordAudio}/${store?.totalRecordAudio},video=${store?.newRecordVideo}/${store?.totalRecordVideo},free=${store?.freeSpace},total=${store?.maxSpace}"
        )
    }

    private fun DeviceBt3StateInfo?.toBt3Summary(): String =
        this?.let {
            "name=$deviceNameBt3,address=$deviceAddressBt3,switch=$deviceBtSwitch,paired=$deviceBtPairedState,connect=$deviceBtConnectState"
        } ?: "null"

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
        val readBattery = readBatteryPercent(connection)
        val battery = readBattery
            ?: cachedBattery(deviceId)
            ?: activeDevice.batterySnapshotForDevice(deviceId)
            ?: fallbackStatus.batterySnapshotForDevice(deviceId)
        val batteryKnown = battery != null
        if (readBattery != null) rememberBattery(deviceId, readBattery)
        val smartInfo = runCatching { connection.smartGetDeviceInfo().data }.getOrNull()
        val glassesState = runCatching { connection.getGlassesStateInfo().takeIf { it.isSuccess }?.data }.getOrNull()
        val deviceInfo = runCatching {
            connection.getDeviceInfo(DeviceInfoRequest().apply {
                address = true
                deviceBtModel = true
                deviceVersionType = true
            }).data
        }.getOrNull()
        val glassesInfo = retryStatusRead {
            connection.getGlassesInfo().takeIf { it.isSuccess }?.data
        }
        val recorderStorage = retryStatusRead {
            connection.getDeviceStorageInfo().takeIf { it.isSuccess }?.data
        }
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
        val readTotalGb = store?.maxSpace?.storageBytesToGb()?.takeIf { it > 0f }
            ?: recorderStorage?.total?.storageBytesToGb()?.takeIf { it > 0f }
        val readFreeGb = store?.freeSpace?.storageBytesToGb()?.takeIf { it >= 0f }
            ?: recorderStorage?.free?.storageBytesToGb()?.takeIf { it >= 0f }
        val cachedStorage = cachedStorage(deviceId)
            ?: activeDevice.storageSnapshotForDevice(deviceId)
            ?: fallbackStatus.storageSnapshotForDevice(deviceId)
        val readStorage = readTotalGb?.let { total ->
            StorageSnapshot(
                totalGb = total,
                freeGb = (readFreeGb ?: cachedStorage?.freeGb ?: total).coerceIn(0f, total)
            )
        }
        if (readStorage != null) rememberStorage(deviceId, readStorage)
        val storage = readStorage ?: cachedStorage
        val totalGb = storage?.totalGb ?: fallbackStatus.storageTotalGb
        val freeGb = storage?.freeGb ?: (totalGb - fallbackStatus.storageUsedGb)
        val storageKnown = storage != null
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

    private suspend inline fun <T> retryStatusRead(crossinline block: () -> T?): T? {
        repeat(StatusReadAttempts) { attempt ->
            val value = runCatching { block() }.getOrNull()
            if (value != null) return value
            if (attempt < StatusReadAttempts - 1) delay(StatusReadRetryMillis)
        }
        return null
    }

    private suspend fun readBatteryPercent(connection: UteBleConnection): Int? =
        retryStatusRead {
            runCatching { connection.smartGetBatteryInfo().takeIf { it.isSuccess }?.data?.percents }
                .getOrNull()
                ?.takeIf { it in 0..100 }
                ?: runCatching { connection.getBatteryInfo().takeIf { it.isSuccess }?.data?.percents }
                    .getOrNull()
                    ?.takeIf { it in 0..100 }
                ?: systemBluetoothBatteryPercent()
        }

    @SuppressLint("MissingPermission")
    private fun systemBluetoothBatteryPercent(): Int? {
        val device = bridge.bluetoothDevice() ?: return null
        return runCatching {
            device.javaClass.getMethod("getBatteryLevel").invoke(device) as? Int
        }.getOrNull()?.takeIf { it in 0..100 }
    }

    private fun DeviceStatus.batterySnapshotForDevice(deviceId: String): Int? =
        battery.takeIf { id.equals(deviceId, ignoreCase = true) && batteryKnown && it in 0..100 }

    private fun DeviceStatus.storageSnapshotForDevice(deviceId: String): StorageSnapshot? =
        takeIf { id.equals(deviceId, ignoreCase = true) && storageKnown && storageTotalGb > 0f }?.let {
            StorageSnapshot(
                totalGb = storageTotalGb,
                freeGb = (storageTotalGb - storageUsedGb).coerceIn(0f, storageTotalGb)
            )
        }

    private fun rememberBattery(deviceId: String, battery: Int) {
        val value = battery.coerceIn(0, 100)
        lastKnownBatteryByDevice[deviceId] = value
        statusCache.edit()
            .putInt(statusCacheKey(deviceId, "battery"), value)
            .apply()
    }

    private fun cachedBattery(deviceId: String): Int? =
        lastKnownBatteryByDevice[deviceId]
            ?: statusCache.getInt(statusCacheKey(deviceId, "battery"), UnknownBattery)
                .takeIf { it in 0..100 }

    private fun rememberStorage(deviceId: String, storage: StorageSnapshot) {
        if (storage.totalGb <= 0f) return
        val normalized = storage.copy(freeGb = storage.freeGb.coerceIn(0f, storage.totalGb))
        lastKnownStorageByDevice[deviceId] = normalized
        statusCache.edit()
            .putFloat(statusCacheKey(deviceId, "storage_total_gb"), normalized.totalGb)
            .putFloat(statusCacheKey(deviceId, "storage_free_gb"), normalized.freeGb)
            .apply()
    }

    private fun cachedStorage(deviceId: String): StorageSnapshot? =
        lastKnownStorageByDevice[deviceId] ?: run {
            val total = statusCache.getFloat(statusCacheKey(deviceId, "storage_total_gb"), 0f)
            if (total <= 0f) return@run null
            val free = statusCache.getFloat(statusCacheKey(deviceId, "storage_free_gb"), total)
            StorageSnapshot(totalGb = total, freeGb = free.coerceIn(0f, total)).also {
                lastKnownStorageByDevice[deviceId] = it
            }
        }

    private fun statusCacheKey(deviceId: String, field: String): String =
        "${deviceId.uppercase()}_$field"

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
            online = discovered.isSystemAudioConnected(),
            battery = fallbackStatus.battery,
            signalBars = discovered.signalBars,
            onlineDuration = if (discovered.isSystemAudioConnected()) {
                "系统蓝牙已连接"
            } else {
                "系统蓝牙已配对，控制通道未连接"
            },
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

    private fun Long.storageBytesToGb(): Float {
        if (this <= 0L) return 0f
        val bytes = if (this < StorageKilobyteValueCeiling) this * 1024L else this
        return bytes / 1024f / 1024f / 1024f
    }

    private data class ExpectedDeviceState(
        val success: Set<Int>,
        val failure: Set<Int>
    )

    private data class StorageSnapshot(
        val totalGb: Float,
        val freeGb: Float
    )

    private companion object {
        const val SystemBluetoothAudioConnected = "system-bluetooth-audio-connected"
        const val SystemBluetoothAudioBonded = "system-bluetooth-audio-bonded"
        const val SystemBluetoothAudioControlConnected = "system-bluetooth-audio-control-connected"
        const val UteBleControlScanned = "ute-ble-control-scanned"
        const val ScanMillis = 10_000L
        const val ConnectTimeoutMillis = 15_000L
        const val PreAccountConnectTimeoutMillis = 2_500L
        const val PairingNotifyTimeoutMillis = 12_000L
        const val PairingNotifyGraceMillis = 3_000L
        const val AccountRebindConfirmWaitMillis = 4_000L
        const val CommandStateTimeoutMillis = 20_000L
        const val PhotoDataTimeoutMillis = 60_000L
        const val PhotoInlineSyncTimeoutMillis = 8_000L
        const val PhotoStateGraceMillis = 6_000L
        const val PhotoRetryUploadTimeoutMillis = 12_000L
        const val PhotoGpsTimeoutMillis = 2_500L
        const val PhotoCommandSettleMillis = 2_000L
        const val NotifyEnableSettleMillis = 250L
        const val HeadsetControlWarmupMillis = 8_000L
        const val StatusReadAttempts = 2
        const val StatusReadRetryMillis = 350L
        const val StatusCacheName = "ute_device_status_cache"
        const val UnknownBattery = -1
        const val SdkTimeoutError = 408
        const val SdkCommandLogTag = "PatrolUteDevice"
        const val SystemBondTimeoutMillis = 20_000L
        const val SystemBondPollMillis = 250L
        const val StorageKilobyteValueCeiling = 1024L * 1024L * 16L
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
