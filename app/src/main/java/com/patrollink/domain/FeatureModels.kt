package com.patrollink.domain

enum class DeviceCommand { TakePhoto, StartRecord, StopRecord, StartTalk, StopTalk }
enum class AlertResult { FalseAlarm, Resolved, RequestBackup }
enum class TransferTarget { PhoneSandbox, Cloud }
enum class RealtimeConnection { Disconnected, Connecting, Connected, Reconnecting }
enum class StreamMode { LowLatency, Balanced, EvidenceQuality }
enum class StreamRelayState { Idle, Connecting, Relaying, Failed }
enum class SosPhase { Idle, Activating, Active, Cancelled }
enum class AppPermission { Internet, NetworkState, FineLocation, BluetoothScan, BluetoothConnect, BluetoothAdvertise, Camera, RecordAudio, PostNotifications, ForegroundService }
enum class BackgroundTaskType { Heartbeat, UploadEvidence, SyncAlertDisposition, VersionCheck }

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

data class EmergencyContact(
    val id: String,
    val name: String,
    val role: String,
    val phone: String
)
