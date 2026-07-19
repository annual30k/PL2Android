package com.patrollink.domain

enum class DeviceCommand { TakePhoto, StartRecord, StopRecord, StartTalk, StopTalk }
enum class AlertResult { Questioned, TakenAway, FalseAlarm, Resolved, RequestBackup }
enum class TransferTarget { PhoneSandbox, Cloud }
enum class RealtimeConnection { Disconnected, Connecting, Connected, Reconnecting }
enum class StreamMode { LowLatency, Balanced, EvidenceQuality }
enum class StreamRelayState { Idle, Connecting, Relaying, Failed }
enum class IntercomState { Idle, WaitingApp, Signaling, Active, Closed, Failed }
enum class SosPhase { Idle, Activating, Active, Cancelled }
enum class AppPermission { Internet, NetworkState, FineLocation, NearbyWifiDevices, BluetoothScan, BluetoothConnect, BluetoothAdvertise, Camera, RecordAudio, PostNotifications, ForegroundService }
enum class BackgroundTaskType {
    Heartbeat,
    UploadEvidence,
    UploadSosEvidence,
    SyncAlertDisposition,
    SyncDeviceCommandAck,
    SyncMessageRead,
    SyncSosState,
    SyncDeviceUnbind
}
enum class DeviceEventLevel { Info, Warning, Error }

data class DeviceCapabilities(
    val supportsGlasses: Boolean = false,
    val supportsEarphone: Boolean = false,
    val supportsWifi: Boolean = false,
    val supportsFileTransfer: Boolean = false,
    val supportsPhoto: Boolean = false,
    val supportsVideo: Boolean = false,
    val supportsAudioRecord: Boolean = false,
    val supportsRealtimeAudio: Boolean = false
)

data class DeviceWifiState(
    val enabled: Boolean = false,
    val ssid: String = "",
    val passwordConfigured: Boolean = false,
    val connected: Boolean = false
)

data class AlertAttachment(
    val clientFileId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val source: String,
    val localUri: String?,
    val uploadIntent: String = "SUBMIT_WITH_FORM"
)

data class DeviceAdvancedSettings(
    val videoWidth: Int = 240,
    val videoHeight: Int = 0,
    val videoFrameRate: Int = 16,
    val recordingDurationSeconds: Int = 24 * 60 * 60,
    val verticalRecording: Boolean = true,
    val enhancedSound: Boolean = true,
    val brightnessLevel: Int = 2
)

data class DeviceEvent(
    val id: String,
    val title: String,
    val detail: String,
    val level: DeviceEventLevel,
    val timestamp: Long
)

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long
)

data class ScannedDevice(
    val id: String,
    val name: String,
    val signalBars: Int,
    val serviceUuid: String,
    val bonded: Boolean,
    val macAddress: String,
    val type: DeviceType
)

data class HeartbeatAck(
    val accepted: Boolean,
    val serverTime: Long
)

data class GpsLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val address: String
)

data class SosState(
    val phase: SosPhase,
    val location: GpsLocation?,
    val recordingAudio: Boolean,
    val backupEtaMinutes: Int?
)

data class SosEvent(
    val id: String,
    val phase: SosPhase,
    val message: String
)

data class BackgroundTask(
    val id: String,
    val type: BackgroundTaskType,
    val payloadId: String,
    val createdAt: Long
)

data class BackgroundTaskReceipt(
    val task: BackgroundTask,
    val queued: Boolean
)

data class VersionCheckResult(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val forceUpdate: Boolean,
    val changelog: List<String>,
    val downloadUrl: String?,
    val sha256: String? = null
) {
    val hasUpdate: Boolean get() = latestVersionCode > 1
}

data class SosRecording(
    val sessionId: String,
    val filePath: String,
    val startedAt: Long,
    val stoppedAt: Long?,
    val sizeBytes: Long
)

data class VersionInstallPackage(
    val versionName: String,
    val filePath: String,
    val sha256: String,
    val verified: Boolean
)

data class FirmwareDeviceMetadata(
    val vendor: String = "",
    val chipset: String = "",
    val deviceModel: String = "",
    val hardwareVersion: String = ""
)

data class FirmwareCheckResult(
    val hasUpdate: Boolean,
    val firmwareId: String?,
    val deviceType: String,
    val vendor: String,
    val chipset: String,
    val deviceModel: String,
    val hardwareVersion: String,
    val firmwareType: String,
    val versionCode: Int?,
    val versionName: String,
    val forceUpdate: Boolean,
    val changelog: List<String>,
    val downloadUrl: String?,
    val sha256: String?,
    val fileId: String?,
    val fileSizeBytes: Long,
    val packageFormat: String,
    val upgradeMode: String,
    val currentFirmwareVersion: String,
    val message: String
)

data class FirmwareUpgradeState(
    val status: String,
    val progress: Float,
    val errorCode: String = "",
    val errorMessage: String = ""
)

data class FirmwareUpgradeTask(
    val taskId: String,
    val deviceId: String,
    val firmwareId: String,
    val operatorId: String,
    val fromVersion: String,
    val toVersion: String,
    val status: String,
    val progress: Float,
    val errorCode: String,
    val errorMessage: String,
    val startedAt: String,
    val finishedAt: String
)
