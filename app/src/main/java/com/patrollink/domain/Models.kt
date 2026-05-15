package com.patrollink.domain

enum class AlertLevel { Critical, Warning, Info }
enum class AlertStatus { Pending, Handling, Closed }
enum class MediaKind { Video, Photo, Audio }
enum class TransferStatus { Idle, Hashing, Uploading, Verifying, Done, Failed }
enum class FontSizeMode { Compact, Standard, Large }
enum class DisplayThemeMode { System, Light, Dark }
enum class DeviceType { Headset, Recorder, Sensor, Glasses }
enum class OperationMessageType { Info, Success, Warning, Error }

data class OperationMessage(
    val text: String,
    val type: OperationMessageType
)

data class UserProfile(
    val name: String,
    val badgeNo: String,
    val department: String,
    val phone: String,
    val email: String,
    val dutyArea: String,
    val shiftDuration: String,
    val patrolGroup: String,
    val systemNode: String
)

data class PatrolGeoPoint(
    val latitude: Double,
    val longitude: Double
)

data class PatrolArea(
    val id: String,
    val name: String,
    val teamId: String,
    val teamName: String,
    val boundary: List<PatrolGeoPoint>,
    val route: List<PatrolGeoPoint>
)

data class DeviceStatus(
    val id: String,
    val name: String,
    val online: Boolean,
    val battery: Int,
    val signalBars: Int,
    val onlineDuration: String,
    val storageUsedGb: Float,
    val storageTotalGb: Float,
    val firmware: String,
    val isRecording: Boolean,
    val isTalking: Boolean,
    val cloudConnected: Boolean,
    val type: DeviceType = DeviceType.Headset
)

data class AlertItem(
    val id: String,
    val title: String,
    val level: AlertLevel,
    val status: AlertStatus,
    val time: String,
    val location: String,
    val source: String,
    val description: String,
    val confidence: String
)

data class MediaFile(
    val id: String,
    val name: String,
    val kind: MediaKind,
    val time: String,
    val size: String,
    val duration: String?,
    val verified: Boolean,
    val local: Boolean,
    val transferStatus: TransferStatus,
    val progress: Float,
    val contentUri: String? = null,
    val lastTransferTarget: TransferTarget? = null
)

data class IntercomSession(
    val sessionId: String,
    val deviceId: String,
    val state: IntercomState,
    val mode: String,
    val signalingUrl: String,
    val audioRoute: String,
    val iceServers: List<String>,
    val message: String
)

enum class VersionUpdatePhase { Idle, Checking, Available, Downloading, Ready, UpToDate, Failed }

data class VersionUpdateUiState(
    val phase: VersionUpdatePhase = VersionUpdatePhase.Idle,
    val currentVersionName: String = "1.2.4",
    val latestVersionName: String? = null,
    val changelog: List<String> = emptyList(),
    val downloadUrl: String? = null,
    val progress: Float = 0f,
    val message: String? = null
)

data class DailyReport(
    val missionId: String,
    val generatedAt: String,
    val content: String,
    val backend: String,
    val model: String,
    val requiresHumanConfirmation: Boolean
)

data class DailyReportUiState(
    val missionId: String = "",
    val operatorNote: String = "",
    val selectedMediaIds: Set<String> = emptySet(),
    val generating: Boolean = false,
    val report: DailyReport? = null,
    val lastError: String? = null
)

data class CerebellumSettingsUiState(
    val baseUrl: String = "",
    val apiKey: String = "",
    val saving: Boolean = false
)

data class AppUiState(
    val isLoggedIn: Boolean = false,
    val networkOnline: Boolean = true,
    val loginLoading: Boolean = false,
    val selectedAlertTab: AlertStatus = AlertStatus.Pending,
    val selectedMediaLocal: Boolean = true,
    val selectedMediaFileId: String? = null,
    val previewMediaFile: MediaFile? = null,
    val streamState: StreamRelayState = StreamRelayState.Idle,
    val sosActive: Boolean = false,
    val fontSizeMode: FontSizeMode = FontSizeMode.Standard,
    val displayThemeMode: DisplayThemeMode = DisplayThemeMode.System,
    val versionUpdate: VersionUpdateUiState = VersionUpdateUiState(),
    val dailyReport: DailyReportUiState = DailyReportUiState(),
    val cerebellumSettings: CerebellumSettingsUiState = CerebellumSettingsUiState(),
    val operationMessage: OperationMessage? = null,
    val deviceCapabilities: DeviceCapabilities = DeviceCapabilities(),
    val deviceWifiState: DeviceWifiState = DeviceWifiState(),
    val deviceSettings: DeviceAdvancedSettings = DeviceAdvancedSettings(),
    val deviceEvents: List<DeviceEvent> = emptyList(),
    val realtimeAudioSyncing: Boolean = false,
    val device: DeviceStatus,
    val connectedDevices: List<DeviceStatus> = emptyList(),
    val scannedDevices: List<ScannedDevice> = emptyList(),
    val selectedDeviceId: String? = null,
    val sosLocation: GpsLocation,
    val patrolArea: PatrolArea,
    val emergencyContacts: List<EmergencyContact> = emptyList(),
    val alerts: List<AlertItem>,
    val mediaFiles: List<MediaFile>,
    val user: UserProfile
)
