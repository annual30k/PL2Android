package com.patrollink.data

import com.patrollink.domain.AlertGateway
import com.patrollink.domain.AlertItem
import com.patrollink.domain.AlertResult
import com.patrollink.domain.AlertAttachment
import com.patrollink.domain.AuthGateway
import com.patrollink.domain.AuthSession
import com.patrollink.domain.DeviceAdvancedSettings
import com.patrollink.domain.DeviceCapabilities
import com.patrollink.domain.DeviceCommand
import com.patrollink.domain.DeviceControlGateway
import com.patrollink.domain.DeviceEvent
import com.patrollink.domain.DeviceFactoryResetTarget
import com.patrollink.domain.DeviceGateway
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.DeviceWifiState
import com.patrollink.domain.EmptyAppState
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

class EmptyAuthGateway : AuthGateway {
    override suspend fun login(account: String, password: String): AuthSession {
        error("后端地址未配置")
    }

    override suspend fun refresh(refreshToken: String): AuthSession {
        error("后端地址未配置")
    }

    override suspend fun currentUser(): UserProfile {
        error("后端地址未配置")
    }
}

class EmptyDeviceGateway : DeviceGateway {
    override fun scan(): Flow<List<ScannedDevice>> = flowOf(emptyList())

    override suspend fun bind(deviceId: String): DeviceStatus = error("设备连接通道未配置")

    override suspend fun unbind(deviceId: String): DeviceStatus? = error("设备解绑通道未配置")

    override suspend fun sendCommand(deviceId: String, command: DeviceCommand): DeviceStatus = error("设备指令通道未配置")
}

class EmptyDeviceControlGateway : DeviceControlGateway {
    override fun events(): Flow<DeviceEvent> = emptyFlow()
    override suspend fun capabilities(device: DeviceStatus): DeviceCapabilities = DeviceCapabilities()
    override suspend fun readWifi(): DeviceWifiState = DeviceWifiState()
    override suspend fun configureWifi(enabled: Boolean, ssid: String, password: String): DeviceWifiState = error("设备 Wi-Fi 通道未配置")
    override suspend fun applySettings(device: DeviceStatus, settings: DeviceAdvancedSettings): DeviceAdvancedSettings = error("设备设置通道未配置")
    override suspend fun startRealtimeAudioSync(sessionId: String): Boolean = false
    override suspend fun stopRealtimeAudioSync(): Boolean = false
    override suspend fun notifyMediaSyncCompleted(): Boolean = false
    override suspend fun clearDeviceAccount(): Boolean = false
    override suspend fun factoryResetDevice(target: DeviceFactoryResetTarget): Boolean = false
}

class EmptyAlertGateway : AlertGateway {
    override fun observeAlerts(): Flow<List<AlertItem>> = flowOf(emptyList())
    override suspend fun acknowledge(alertId: String): AlertItem = error("警情后端未配置")
    override suspend fun close(alertId: String, result: AlertResult, note: String, attachments: List<AlertAttachment>): AlertItem = error("警情后端未配置")
}

class EmptyMediaGateway : MediaGateway {
    override suspend fun listFiles(local: Boolean): List<MediaFile> = emptyList()
    override fun transfer(fileId: String, target: TransferTarget): Flow<MediaFile> = kotlinx.coroutines.flow.flow { error("媒体传输通道未配置") }
    override suspend fun uploadLocalFile(file: File, storageSide: String, bizType: String, bizId: String): MediaFile? = error("媒体上传通道未配置")
    override suspend fun delete(fileId: String, local: Boolean): Boolean = false
    override suspend fun verifySha256(fileId: String): Boolean = false
}

class EmptyRealtimeGateway : RealtimeGateway {
    override fun connection(): Flow<RealtimeConnection> = flowOf(RealtimeConnection.Disconnected)
    override suspend fun connect(token: String) = error("平台实时连接未配置")
    override suspend fun disconnect() = Unit
    override suspend fun sendHeartbeat(device: DeviceStatus): HeartbeatAck = HeartbeatAck(false, System.currentTimeMillis())
}

class EmptyStreamRelayGateway : StreamRelayGateway {
    override fun state(): Flow<StreamRelayState> = flowOf(StreamRelayState.Idle)
    override suspend fun start(deviceId: String, mode: StreamMode) = error("实时画面通道未配置")
    override suspend fun stop() = Unit
}

class EmptyIntercomGateway : IntercomGateway {
    override fun state(): Flow<IntercomState> = flowOf(IntercomState.Idle)
    override suspend fun start(deviceId: String): IntercomSession = error("对讲后端未配置")
    override suspend fun stop() = Unit
}

class EmptySosGateway : SosGateway {
    override fun state(): Flow<SosState> = flowOf(SosState(SosPhase.Idle, null, recordingAudio = false, backupEtaMinutes = null))
    override suspend fun activate(location: GpsLocation, clientEventId: String): SosEvent = error("SOS 平台通道未配置")
    override suspend fun cancel(): SosEvent = error("SOS 平台通道未配置")
}

class EmptyPatrolAreaGateway : PatrolAreaGateway {
    override suspend fun currentArea(): PatrolArea = EmptyAppState.create().patrolArea
}

class EmptyVersionGateway : VersionGateway {
    override suspend fun check(currentVersionCode: Int): VersionCheckResult =
        VersionCheckResult(
            latestVersionCode = currentVersionCode,
            latestVersionName = "",
            forceUpdate = false,
            changelog = emptyList(),
            downloadUrl = null
        )
}

class EmptyFirmwareGateway : FirmwareGateway {
    override suspend fun check(device: DeviceStatus, metadata: FirmwareDeviceMetadata): FirmwareCheckResult =
        FirmwareCheckResult(
            hasUpdate = false,
            firmwareId = null,
            deviceType = device.type.name.uppercase(),
            vendor = metadata.vendor,
            chipset = metadata.chipset,
            deviceModel = metadata.deviceModel,
            hardwareVersion = metadata.hardwareVersion,
            firmwareType = "",
            versionCode = null,
            versionName = device.firmware,
            forceUpdate = false,
            changelog = emptyList(),
            downloadUrl = null,
            sha256 = null,
            fileId = null,
            fileSizeBytes = 0L,
            packageFormat = "",
            upgradeMode = "",
            currentFirmwareVersion = device.firmware,
            message = "当前已是最新固件"
        )

    override fun install(device: DeviceStatus, firmware: FirmwareCheckResult): Flow<FirmwareUpgradeState> =
        flowOf(FirmwareUpgradeState("UNAVAILABLE", 0f, "FIRMWARE_GATEWAY_EMPTY", "固件升级通道未配置"))

    override suspend fun createUpgradeTask(device: DeviceStatus, firmware: FirmwareCheckResult, operatorId: String): FirmwareUpgradeTask =
        FirmwareUpgradeTask("", device.id, firmware.firmwareId.orEmpty(), operatorId, device.firmware, firmware.versionName, "PENDING", 0f, "", "", "", "")

    override suspend fun updateUpgradeTask(taskId: String, state: FirmwareUpgradeState): FirmwareUpgradeTask =
        FirmwareUpgradeTask(taskId, "", "", "", "", "", state.status, state.progress, state.errorCode, state.errorMessage, "", "")
}
