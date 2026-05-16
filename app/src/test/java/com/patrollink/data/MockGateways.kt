package com.patrollink.data

import com.patrollink.data.remote.AlertCloseRequestDto
import com.patrollink.data.remote.DeviceCommandRequestDto
import com.patrollink.data.remote.HeartbeatRequestDto
import com.patrollink.data.remote.LoginRequestDto
import com.patrollink.data.remote.MockRestApi
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
import com.patrollink.domain.DeviceType
import com.patrollink.domain.GpsLocation
import com.patrollink.domain.HeartbeatAck
import com.patrollink.domain.IntercomGateway
import com.patrollink.domain.IntercomSession
import com.patrollink.domain.IntercomState
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaGateway
import com.patrollink.domain.PatrolArea
import com.patrollink.domain.PatrolAreaGateway
import com.patrollink.domain.PatrolCoordinator
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
import com.patrollink.domain.TransferStatus
import com.patrollink.domain.TransferTarget
import com.patrollink.domain.UserProfile
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

class MockAuthGateway(private val api: MockRestApi = MockRestApi()) : AuthGateway {
    override suspend fun login(account: String, password: String): AuthSession =
        api.login(LoginRequestDto(account, password)).data.toDomain()

    override suspend fun refresh(refreshToken: String): AuthSession =
        api.refresh(refreshToken).data.toDomain()

    override suspend fun currentUser(): UserProfile = api.currentUser().data.toDomain()
}

class MockDeviceGateway(private val api: MockRestApi = MockRestApi()) : DeviceGateway {
    private val current = MutableStateFlow(api.bindDevice("HEADSET_001").data.toDomain())

    override fun scan(): Flow<List<ScannedDevice>> = flow {
        emit(emptyList())
        delay(40)
        emit(api.scanDevices().data.map { it.toDomain() })
    }

    override suspend fun bind(deviceId: String): DeviceStatus {
        val next = api.bindDevice(deviceId).data.toDomain()
        current.value = next
        return next
    }

    override suspend fun unbind(deviceId: String): DeviceStatus? {
        if (deviceId != current.value.id) return null
        val next = api.unbindDevice(deviceId).data.toDomain()
        current.value = next
        return next
    }

    override suspend fun sendCommand(deviceId: String, command: DeviceCommand): DeviceStatus {
        val commandValue = when (command) {
            DeviceCommand.TakePhoto -> "TAKE_PHOTO"
            DeviceCommand.StartRecord -> "START_RECORD"
            DeviceCommand.StopRecord -> "STOP_RECORD"
            DeviceCommand.StartTalk -> "START_TALK"
            DeviceCommand.StopTalk -> "STOP_TALK"
        }
        current.value = api.sendDeviceCommand(deviceId, DeviceCommandRequestDto(commandValue, api.currentUser().data.badgeNo, "REQ-0001")).data.toDomain()
        return current.value
    }
}

class MockAlertGateway(private val api: MockRestApi = MockRestApi()) : AlertGateway {
    private val alerts = MutableStateFlow(api.alerts().data.items.map { it.toDomain() })

    override fun observeAlerts(): Flow<List<AlertItem>> = alerts.asStateFlow()

    override suspend fun acknowledge(alertId: String): AlertItem {
        val updated = api.acknowledgeAlert(alertId).data.toDomain()
        replace(updated)
        return updated
    }

    override suspend fun close(alertId: String, result: AlertResult, note: String): AlertItem {
        val resultValue = when (result) {
            AlertResult.FalseAlarm -> "FALSE_ALARM"
            AlertResult.Resolved -> "RESOLVED"
            AlertResult.RequestBackup -> "REQUEST_BACKUP"
        }
        val updated = api.closeAlert(alertId, AlertCloseRequestDto(resultValue, note, api.currentUser().data.badgeNo)).data.toDomain()
        replace(updated)
        return updated
    }

    private fun replace(updated: AlertItem) {
        alerts.update { list -> list.map { if (it.id == updated.id) updated else it } }
    }
}

class MockMediaGateway(private val api: MockRestApi = MockRestApi()) : MediaGateway {
    private val files = MutableStateFlow(api.mediaFiles(local = false).data.items.map { it.toDomain() } + api.mediaFiles(local = true).data.items.map { it.toDomain() })

    override suspend fun listFiles(local: Boolean): List<MediaFile> = files.value.filter { it.local == local }

    override fun transfer(fileId: String, target: TransferTarget): Flow<MediaFile> = flow {
        val targetValue = if (target == TransferTarget.PhoneSandbox) "PHONE_SANDBOX" else "CLOUD"
        val steps = api.transferMedia(fileId, TransferRequestDto(targetValue)).map { it.data.toDomain() }
        for (step in steps) {
            delay(10)
            val stored = if (target == TransferTarget.PhoneSandbox && step.transferStatus == TransferStatus.Done) {
                step.copy(transferStatus = TransferStatus.Idle, progress = 0f)
            } else {
                step
            }
            files.update { list ->
                val withTarget = list.upsertBySide(stored)
                if (target == TransferTarget.PhoneSandbox && step.transferStatus == TransferStatus.Done) {
                    withTarget.upsertBySide(
                        (withTarget.firstOrNull { it.id == fileId && !it.local } ?: step.copy(local = false)).copy(
                            transferStatus = TransferStatus.Done,
                            progress = 1f,
                            lastTransferTarget = TransferTarget.PhoneSandbox
                        )
                    )
                } else {
                    withTarget
                }
            }
            emit(step)
        }
    }

    override suspend fun uploadLocalFile(file: File, storageSide: String, bizType: String, bizId: String): MediaFile? =
        files.value.firstOrNull { it.id == bizId }?.copy(transferStatus = TransferStatus.Done, progress = 1f, lastTransferTarget = TransferTarget.Cloud)

    override suspend fun delete(fileId: String, local: Boolean): Boolean {
        val deleted = api.deleteMedia(fileId, if (local) "PHONE" else "DEVICE").data
        if (deleted) files.update { list -> list.filterNot { it.id == fileId && it.local == local } }
        return deleted
    }

    override suspend fun verifySha256(fileId: String): Boolean {
        val verified = api.verifyMedia(fileId).data
        files.update { list -> list.map { if (it.id == fileId) it.copy(verified = true) else it } }
        return verified
    }

    private fun List<MediaFile>.upsertBySide(file: MediaFile): List<MediaFile> {
        var replaced = false
        val updated = map {
            if (it.id == file.id && it.local == file.local) {
                replaced = true
                file
            } else {
                it
            }
        }
        return if (replaced) updated else updated + file
    }
}

class MockRealtimeGateway(private val api: MockRestApi = MockRestApi()) : RealtimeGateway {
    private val state = MutableStateFlow(RealtimeConnection.Disconnected)

    override fun connection(): Flow<RealtimeConnection> = state.asStateFlow()

    override suspend fun connect(token: String) {
        require(token.isNotBlank()) { "token required" }
        state.value = RealtimeConnection.Connecting
        delay(10)
        state.value = RealtimeConnection.Connected
    }

    override suspend fun disconnect() {
        state.value = RealtimeConnection.Disconnected
    }

    override suspend fun sendHeartbeat(device: DeviceStatus): HeartbeatAck {
        require(state.value == RealtimeConnection.Connected) { "websocket disconnected" }
        return api.heartbeat(
            HeartbeatRequestDto(
                deviceId = device.id,
                online = device.online,
                batteryPercent = device.battery,
                signalBars = device.signalBars,
                recordingStatus = if (device.isRecording) "RECORDING" else "IDLE",
                clientTimestamp = 1715831990L
            )
        ).data.toDomain()
    }
}

class MockPatrolAreaGateway(private val api: MockRestApi = MockRestApi()) : PatrolAreaGateway {
    override suspend fun currentArea(): PatrolArea = api.currentPatrolArea().data.toDomain()
}

class MockStreamRelayGateway(private val api: MockRestApi = MockRestApi()) : StreamRelayGateway {
    private val state = MutableStateFlow(StreamRelayState.Idle)

    override fun state(): Flow<StreamRelayState> = state.asStateFlow()

    override suspend fun start(deviceId: String, mode: StreamMode) {
        require(deviceId.isNotBlank()) { "device required" }
        state.value = StreamRelayState.Connecting
        delay(10)
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

class MockIntercomGateway : IntercomGateway {
    private val state = MutableStateFlow(IntercomState.Idle)

    override fun state(): Flow<IntercomState> = state.asStateFlow()

    override suspend fun start(deviceId: String): IntercomSession {
        state.value = IntercomState.Signaling
        return IntercomSession(
            sessionId = "IC-MOCK",
            deviceId = deviceId,
            state = IntercomState.Signaling,
            mode = "FULL_DUPLEX",
            signalingUrl = "/api/v1/intercom/sessions/mock/signals",
            audioRoute = "BLUETOOTH_HEADSET_SCO_PREFERRED",
            iceServers = listOf("stun:turn.patrollink.local:3478"),
            message = "Mock WebRTC/VoIP 对讲会话已创建"
        )
    }

    override suspend fun stop() {
        state.value = IntercomState.Idle
    }
}

class MockSosGateway(private val api: MockRestApi = MockRestApi()) : SosGateway {
    private val state = MutableStateFlow(SosState(SosPhase.Idle, null, recordingAudio = false, backupEtaMinutes = null))

    override fun state(): Flow<SosState> = state.asStateFlow()

    override suspend fun activate(location: GpsLocation): SosEvent {
        state.value = SosState(SosPhase.Activating, location, recordingAudio = true, backupEtaMinutes = null)
        delay(10)
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

object MockPatrolCoordinatorFactory {
    fun create(api: MockRestApi = MockRestApi()) = PatrolCoordinator(
        authGateway = MockAuthGateway(api),
        deviceGateway = MockDeviceGateway(api),
        alertGateway = MockAlertGateway(api),
        mediaGateway = MockMediaGateway(api),
        realtimeGateway = MockRealtimeGateway(api),
        streamRelayGateway = MockStreamRelayGateway(api),
        sosGateway = MockSosGateway(api),
        patrolAreaGateway = MockPatrolAreaGateway(api),
        intercomGateway = MockIntercomGateway()
    )
}
