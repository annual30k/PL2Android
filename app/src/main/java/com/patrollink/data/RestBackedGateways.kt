package com.patrollink.data

import com.patrollink.data.remote.AlertCloseRequestDto
import com.patrollink.data.remote.DeviceCommandRequestDto
import com.patrollink.data.remote.FirmwareCheckRequestDto
import com.patrollink.data.remote.FirmwareUpgradeTaskCreateDto
import com.patrollink.data.remote.FirmwareUpgradeTaskUpdateDto
import com.patrollink.data.remote.HeartbeatRequestDto
import com.patrollink.data.remote.IntercomSessionRequestDto
import com.patrollink.data.remote.IntercomSignalRequestDto
import com.patrollink.data.remote.LoginRequestDto
import com.patrollink.data.remote.PatrolRestApi
import com.patrollink.data.remote.StreamRelayRequestDto
import com.patrollink.data.remote.TransferRequestDto
import com.patrollink.data.voip.BluetoothVoipAudioRouter
import com.patrollink.data.remote.toDomain
import com.patrollink.data.remote.toDomainEvent
import com.patrollink.data.remote.toDomainState
import com.patrollink.data.remote.toDto
import com.patrollink.data.voip.AndroidWebRtcIntercomClient
import com.patrollink.domain.AlertGateway
import com.patrollink.domain.AlertItem
import com.patrollink.domain.AlertResult
import com.patrollink.domain.AuthGateway
import com.patrollink.domain.AuthSession
import com.patrollink.domain.DeviceAdvancedSettings
import com.patrollink.domain.DeviceCommand
import com.patrollink.domain.DeviceCapabilities
import com.patrollink.domain.DeviceControlGateway
import com.patrollink.domain.DeviceFactoryResetTarget
import com.patrollink.domain.DeviceEvent
import com.patrollink.domain.DeviceGateway
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.DeviceWifiState
import com.patrollink.domain.FirmwareCheckResult
import com.patrollink.domain.FirmwareDeviceMetadata
import com.patrollink.domain.FirmwareGateway
import com.patrollink.domain.FirmwareUpgradeState
import com.patrollink.domain.FirmwareUpgradeTask
import com.patrollink.domain.GpsLocation
import com.patrollink.domain.HeartbeatAck
import com.patrollink.domain.IntercomGateway
import com.patrollink.domain.IntercomSession
import com.patrollink.domain.IntercomState
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaGateway
import com.patrollink.domain.PatrolArea
import com.patrollink.domain.PatrolAreaGateway
import com.patrollink.domain.RealtimeConnection
import com.patrollink.domain.RealtimeGateway
import com.patrollink.domain.ScannedDevice
import com.patrollink.domain.SosEvent
import com.patrollink.domain.SosGateway
import com.patrollink.domain.SosPhase
import com.patrollink.domain.SosState
import com.patrollink.domain.StreamMode
import com.patrollink.domain.StreamRelayGateway
import com.patrollink.domain.StreamRelayState
import com.patrollink.domain.TransferTarget
import com.patrollink.domain.UserProfile
import com.patrollink.domain.VersionCheckResult
import com.patrollink.domain.VersionGateway
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

class RestAuthGateway(private val api: PatrolRestApi) : AuthGateway {
    override suspend fun login(account: String, password: String): AuthSession =
        api.login(LoginRequestDto(account, password)).data.toDomain()

    override suspend fun refresh(refreshToken: String): AuthSession =
        api.refresh(refreshToken).data.toDomain()

    override suspend fun currentUser(): UserProfile = api.currentUser().data.toDomain()
}

class RestDeviceGateway(
    private val api: PatrolRestApi,
    private val operatorIdProvider: () -> String = { "UNKNOWN_OPERATOR" }
) : DeviceGateway {
    override fun scan(): Flow<List<ScannedDevice>> = flow {
        emit(api.scanDevices().data.map { it.toDomain() })
    }

    override suspend fun bind(deviceId: String): DeviceStatus = api.bindDevice(deviceId).data.toDomain()

    override suspend fun unbind(deviceId: String): DeviceStatus? =
        runCatching { api.unbindDevice(deviceId).data.toDomain() }.getOrNull()

    override suspend fun sendCommand(deviceId: String, command: DeviceCommand): DeviceStatus {
        val commandValue = when (command) {
            DeviceCommand.TakePhoto -> "TAKE_PHOTO"
            DeviceCommand.StartRecord -> "START_RECORD"
            DeviceCommand.StopRecord -> "STOP_RECORD"
            DeviceCommand.StartTalk -> "START_TALK"
            DeviceCommand.StopTalk -> "STOP_TALK"
        }
        return api.sendDeviceCommand(deviceId, DeviceCommandRequestDto(commandValue, operatorIdProvider(), "REQ-${System.currentTimeMillis()}")).data.toDomain()
    }
}

class RestAlertGateway(
    private val api: PatrolRestApi,
    private val operatorIdProvider: () -> String = { "UNKNOWN_OPERATOR" }
) : AlertGateway {
    override fun observeAlerts(): Flow<List<AlertItem>> = flow {
        emit(api.alerts(1, 50).data.items.map { it.toDomain() })
    }

    override suspend fun acknowledge(alertId: String): AlertItem = api.acknowledgeAlert(alertId).data.toDomain()

    override suspend fun close(alertId: String, result: AlertResult, note: String): AlertItem {
        val resultValue = when (result) {
            AlertResult.FalseAlarm -> "FALSE_ALARM"
            AlertResult.Resolved -> "RESOLVED"
            AlertResult.RequestBackup -> "REQUEST_BACKUP"
        }
        return api.closeAlert(alertId, AlertCloseRequestDto(resultValue, note, operatorIdProvider())).data.toDomain()
    }
}

class RestMediaGateway(private val api: PatrolRestApi) : MediaGateway {
    override suspend fun listFiles(local: Boolean): List<MediaFile> =
        api.mediaFiles(local, 1, 50).data.items.map { it.toDomain() }

    override fun transfer(fileId: String, target: TransferTarget): Flow<MediaFile> = flow {
        val targetValue = if (target == TransferTarget.PhoneSandbox) "PHONE_SANDBOX" else "CLOUD"
        api.transferMedia(fileId, TransferRequestDto(targetValue)).forEach { emit(it.data.toDomain()) }
    }

    override suspend fun uploadLocalFile(file: File, storageSide: String, bizType: String, bizId: String): MediaFile? =
        api.uploadMediaResumable(file, storageSide, bizType, bizId).data.toDomain()

    override suspend fun delete(fileId: String, local: Boolean): Boolean =
        api.deleteMedia(fileId, if (local) "PHONE" else "DEVICE").data

    override suspend fun verifySha256(fileId: String): Boolean = api.verifyMedia(fileId).data
}

class RestDeviceControlGateway(
    private val api: PatrolRestApi,
    private val deviceIdProvider: () -> String = { "" }
) : DeviceControlGateway {
    override fun events(): Flow<DeviceEvent> = emptyFlow()

    override suspend fun capabilities(device: DeviceStatus): DeviceCapabilities =
        api.deviceCapabilities(device.id).data.toDomain()

    override suspend fun readWifi(): DeviceWifiState =
        api.deviceWifi(deviceIdProvider()).data.toDomain()

    override suspend fun configureWifi(enabled: Boolean, ssid: String, password: String): DeviceWifiState =
        api.configureWifi(deviceIdProvider(), DeviceWifiState(enabled, ssid, password.isNotBlank(), enabled && ssid.isNotBlank()).toDto(password)).data.toDomain()

    override suspend fun applySettings(device: DeviceStatus, settings: DeviceAdvancedSettings): DeviceAdvancedSettings =
        api.applyDeviceSettings(device.id, settings.toDto()).data.toDomain()

    override suspend fun startRealtimeAudioSync(sessionId: String): Boolean =
        api.startRealtimeAudioSync(deviceIdProvider()).data.success

    override suspend fun stopRealtimeAudioSync(): Boolean =
        api.stopRealtimeAudioSync(deviceIdProvider()).data.success

    override suspend fun notifyMediaSyncCompleted(): Boolean =
        api.notifyMediaSyncCompleted(deviceIdProvider()).data.success

    override suspend fun clearDeviceAccount(): Boolean =
        api.clearDeviceAccount(deviceIdProvider()).data.success

    override suspend fun factoryResetDevice(target: DeviceFactoryResetTarget): Boolean {
        val targetValue = when (target) {
            DeviceFactoryResetTarget.Glasses -> "GLASSES"
            DeviceFactoryResetTarget.Headset -> "HEADSET"
        }
        return api.factoryResetDevice(deviceIdProvider(), targetValue).data.success
    }
}

class RestVersionGateway(private val api: PatrolRestApi) : VersionGateway {
    override suspend fun check(currentVersionCode: Int): VersionCheckResult =
        api.checkVersion(currentVersionCode).data.toDomain()
}

class RestFirmwareGateway(private val api: PatrolRestApi) : FirmwareGateway {
    override suspend fun check(device: DeviceStatus, metadata: FirmwareDeviceMetadata): FirmwareCheckResult =
        api.checkFirmware(
            device.id,
            FirmwareCheckRequestDto(
                deviceType = when (device.type) {
                    com.patrollink.domain.DeviceType.Glasses -> "GLASSES"
                    com.patrollink.domain.DeviceType.Recorder -> "RECORDER"
                    com.patrollink.domain.DeviceType.Sensor -> "SENSOR"
                    else -> "HEADSET"
                },
                vendor = metadata.vendor,
                chipset = metadata.chipset,
                deviceModel = metadata.deviceModel,
                hardwareVersion = metadata.hardwareVersion,
                currentFirmwareVersion = device.firmware
            )
        ).data.toDomain()

    override fun install(device: DeviceStatus, firmware: FirmwareCheckResult): Flow<FirmwareUpgradeState> = flow {
        val task = createUpgradeTask(device, firmware)
        emit(FirmwareUpgradeState("TASK_CREATED", 0.1f))
        updateUpgradeTask(task.taskId, FirmwareUpgradeState("PENDING_DEVICE_UPGRADE", 0.2f))
        emit(FirmwareUpgradeState("PENDING_DEVICE_UPGRADE", 0.2f))
    }

    override suspend fun createUpgradeTask(device: DeviceStatus, firmware: FirmwareCheckResult, operatorId: String): FirmwareUpgradeTask {
        val firmwareId = requireNotNull(firmware.firmwareId) { "firmwareId required" }
        return api.createFirmwareUpgradeTask(
            device.id,
            FirmwareUpgradeTaskCreateDto(
                firmwareId = firmwareId,
                operatorId = operatorId,
                fromVersion = device.firmware
            )
        ).data.toDomain()
    }

    override suspend fun updateUpgradeTask(taskId: String, state: FirmwareUpgradeState): FirmwareUpgradeTask =
        api.updateFirmwareUpgradeTask(
            taskId,
            FirmwareUpgradeTaskUpdateDto(
                status = state.status,
                progress = state.progress,
                errorCode = state.errorCode,
                errorMessage = state.errorMessage
            )
        ).data.toDomain()
}

class RestRealtimeGateway(private val api: PatrolRestApi) : RealtimeGateway {
    private val connection = MutableStateFlow(RealtimeConnection.Disconnected)

    override fun connection(): Flow<RealtimeConnection> = connection.asStateFlow()

    override suspend fun connect(token: String) {
        connection.value = RealtimeConnection.Connected
    }

    override suspend fun disconnect() {
        connection.value = RealtimeConnection.Disconnected
    }

    override suspend fun sendHeartbeat(device: DeviceStatus): HeartbeatAck {
        return api.heartbeat(
            HeartbeatRequestDto(
                deviceId = device.id,
                online = device.online,
                batteryPercent = device.battery,
                signalBars = device.signalBars,
                recordingStatus = if (device.isRecording) "RECORDING" else "IDLE",
                clientTimestamp = System.currentTimeMillis()
            )
        ).data.toDomain()
    }
}

class RestStreamRelayGateway(private val api: PatrolRestApi) : StreamRelayGateway {
    private val state = MutableStateFlow(StreamRelayState.Idle)

    override fun state(): Flow<StreamRelayState> = state.asStateFlow()

    override suspend fun start(deviceId: String, mode: StreamMode) {
        val modeValue = when (mode) {
            StreamMode.LowLatency -> "LOW_LATENCY"
            StreamMode.Balanced -> "BALANCED"
            StreamMode.EvidenceQuality -> "EVIDENCE_QUALITY"
        }
        state.value = api.startStream(StreamRelayRequestDto(deviceId, modeValue)).data.toDomain()
    }

    override suspend fun stop() {
        state.value = api.stopStream().data.toDomain()
    }
}

class RestIntercomGateway(
    private val api: PatrolRestApi,
    private val audioRouter: BluetoothVoipAudioRouter? = null,
    private val webRtcClient: AndroidWebRtcIntercomClient? = null
) : IntercomGateway {
    private val state = MutableStateFlow(IntercomState.Idle)
    private var activeSessionId: String? = null

    override fun state(): Flow<IntercomState> = state.asStateFlow()

    override suspend fun start(deviceId: String): IntercomSession {
        require(deviceId.isNotBlank()) { "device required" }
        state.value = IntercomState.WaitingApp
        val session = api.pendingIntercomSession(deviceId).data
            ?: api.createIntercomSession(IntercomSessionRequestDto(deviceId = deviceId)).data
        activeSessionId = session.sessionId
        if (webRtcClient != null) {
            webRtcClient.start(session) { state.value = it }
        } else {
            audioRouter?.startBluetoothRoute()
            api.acceptIntercomSession(session.sessionId)
            api.sendIntercomSignal(session.sessionId, IntercomSignalRequestDto(type = "ready", payload = """{"audioRoute":"BLUETOOTH_HEADSET"}"""))
        }
        val domain = session.toDomain().copy(state = IntercomState.Signaling)
        state.value = domain.state
        return domain
    }

    override suspend fun stop() {
        val sessionId = activeSessionId
        if (webRtcClient != null) {
            webRtcClient.stop(sendHangup = sessionId != null)
        } else if (sessionId != null) {
            api.sendIntercomSignal(sessionId, IntercomSignalRequestDto(type = "hangup"))
            api.closeIntercomSession(sessionId)
            audioRouter?.stopBluetoothRoute()
        }
        activeSessionId = null
        state.value = IntercomState.Idle
    }
}

class RestSosGateway(private val api: PatrolRestApi) : SosGateway {
    private val state = MutableStateFlow(SosState(SosPhase.Idle, null, recordingAudio = false, backupEtaMinutes = null))

    override fun state(): Flow<SosState> = state.asStateFlow()

    override suspend fun activate(location: GpsLocation): SosEvent {
        val response = api.activateSos(location.toDto()).data
        state.value = response.toDomainState()
        return response.toDomainEvent()
    }

    override suspend fun cancel(): SosEvent {
        val response = api.cancelSos().data
        state.value = response.toDomainState()
        return response.toDomainEvent()
    }
}

class RestPatrolAreaGateway(private val api: PatrolRestApi) : PatrolAreaGateway {
    override suspend fun currentArea(): PatrolArea = api.currentPatrolArea().data.toDomain()
}
