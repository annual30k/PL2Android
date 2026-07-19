package com.patrollink.domain

import java.io.File
import kotlinx.coroutines.flow.Flow

interface AuthGateway {
    suspend fun login(account: String, password: String): AuthSession
    suspend fun refresh(refreshToken: String): AuthSession
    suspend fun currentUser(): UserProfile
}

interface DeviceGateway {
    fun scan(): Flow<List<ScannedDevice>>
    suspend fun bind(deviceId: String): DeviceStatus
    suspend fun unbind(deviceId: String): DeviceStatus?
    suspend fun sendCommand(deviceId: String, command: DeviceCommand): DeviceStatus
}

interface AlertGateway {
    fun observeAlerts(): Flow<List<AlertItem>>
    suspend fun acknowledge(alertId: String): AlertItem
    suspend fun close(alertId: String, result: AlertResult, note: String, attachments: List<AlertAttachment> = emptyList()): AlertItem
}

interface MediaGateway {
    suspend fun listFiles(local: Boolean): List<MediaFile>
    fun transfer(fileId: String, target: TransferTarget): Flow<MediaFile>
    suspend fun uploadLocalFile(file: File, storageSide: String = "PHONE", bizType: String = "", bizId: String = ""): MediaFile? = null
    suspend fun delete(fileId: String, local: Boolean): Boolean
    suspend fun verifySha256(fileId: String): Boolean
}

interface RealtimeGateway {
    fun connection(): Flow<RealtimeConnection>
    suspend fun connect(token: String)
    suspend fun disconnect()
    suspend fun sendHeartbeat(device: DeviceStatus): HeartbeatAck
}

interface StreamRelayGateway {
    fun state(): Flow<StreamRelayState>
    suspend fun start(deviceId: String, mode: StreamMode)
    suspend fun stop()
}

interface IntercomGateway {
    fun state(): Flow<IntercomState>
    suspend fun start(deviceId: String): IntercomSession
    suspend fun stop()
}

interface DeviceControlGateway {
    fun events(): Flow<DeviceEvent>
    suspend fun capabilities(device: DeviceStatus): DeviceCapabilities
    suspend fun readWifi(): DeviceWifiState
    suspend fun configureWifi(enabled: Boolean, ssid: String, password: String): DeviceWifiState
    suspend fun applySettings(device: DeviceStatus, settings: DeviceAdvancedSettings): DeviceAdvancedSettings
    suspend fun startRealtimeAudioSync(sessionId: String): Boolean
    suspend fun stopRealtimeAudioSync(): Boolean
    suspend fun notifyMediaSyncCompleted(): Boolean
    suspend fun clearDeviceAccount(): Boolean
    suspend fun factoryResetDevice(target: DeviceFactoryResetTarget): Boolean
}

enum class DeviceFactoryResetTarget {
    Glasses,
    Headset
}

interface SosGateway {
    fun state(): Flow<SosState>
    suspend fun activate(location: GpsLocation, clientEventId: String = ""): SosEvent
    suspend fun cancel(): SosEvent
}

interface PatrolAreaGateway {
    suspend fun currentArea(): PatrolArea
}

interface SecureStore {
    suspend fun saveSession(session: AuthSession)
    suspend fun readSession(): AuthSession?
    suspend fun clearSession()
}

interface PermissionGateway {
    fun requiredPermissions(androidApi: Int): List<AppPermission>
    fun missingPermissions(androidApi: Int, granted: Set<AppPermission>): List<AppPermission>
}

interface BackgroundTaskGateway {
    suspend fun enqueue(task: BackgroundTask): BackgroundTaskReceipt
    suspend fun pending(): List<BackgroundTaskReceipt>
    suspend fun complete(taskId: String): Boolean
}

interface EvidenceIntegrityGateway {
    fun sha256(bytes: ByteArray): String
    fun sha256(file: File): String
    fun watermarkToken(fileId: String, officerBadgeNo: String, timestamp: Long): String
}

interface VersionGateway {
    suspend fun check(currentVersionCode: Int): VersionCheckResult
}

interface FirmwareGateway {
    suspend fun check(device: DeviceStatus, metadata: FirmwareDeviceMetadata = FirmwareDeviceMetadata()): FirmwareCheckResult
    fun install(device: DeviceStatus, firmware: FirmwareCheckResult): Flow<FirmwareUpgradeState>
    suspend fun createUpgradeTask(device: DeviceStatus, firmware: FirmwareCheckResult, operatorId: String = ""): FirmwareUpgradeTask
    suspend fun updateUpgradeTask(taskId: String, state: FirmwareUpgradeState): FirmwareUpgradeTask
}

interface LocationGateway {
    suspend fun currentLocation(): GpsLocation
}

interface SosEvidenceRecorder {
    suspend fun start(sessionId: String): SosRecording
    suspend fun stop(): SosRecording?
}

interface VersionInstaller {
    suspend fun prepare(update: VersionCheckResult, expectedSha256: String? = null): VersionInstallPackage
    fun launchInstall(packageInfo: VersionInstallPackage): Boolean
}

interface PatrolNotificationGateway {
    fun notifySosActive(location: GpsLocation)
    fun notifyAlert(title: String, body: String)
}
