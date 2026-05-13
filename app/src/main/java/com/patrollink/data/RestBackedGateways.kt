package com.patrollink.data

import com.patrollink.data.remote.AlertCloseRequestDto
import com.patrollink.data.remote.DeviceCommandRequestDto
import com.patrollink.data.remote.HeartbeatRequestDto
import com.patrollink.data.remote.LoginRequestDto
import com.patrollink.data.remote.PatrolRestApi
import com.patrollink.data.remote.StreamRelayRequestDto
import com.patrollink.data.remote.TransferRequestDto
import com.patrollink.data.remote.toDomain
import com.patrollink.data.remote.toDomainEvent
import com.patrollink.data.remote.toDomainState
import com.patrollink.data.remote.toDto
import com.patrollink.domain.AlertGateway
import com.patrollink.domain.AlertItem
import com.patrollink.domain.AlertResult
import com.patrollink.domain.AuthGateway
import com.patrollink.domain.AuthSession
import com.patrollink.domain.DeviceCommand
import com.patrollink.domain.DeviceGateway
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.GpsLocation
import com.patrollink.domain.HeartbeatAck
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    override suspend fun delete(fileId: String, local: Boolean): Boolean =
        api.deleteMedia(fileId, if (local) "PHONE" else "DEVICE").data

    override suspend fun verifySha256(fileId: String): Boolean = api.verifyMedia(fileId).data
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
