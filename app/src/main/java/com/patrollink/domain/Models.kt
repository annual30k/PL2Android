package com.patrollink.domain

enum class AlertLevel { Critical, Warning, Info }
enum class AlertStatus { Pending, Handling, Closed }
enum class MediaKind { Video, Photo, Audio }
enum class TransferStatus { Idle, Hashing, Uploading, Verifying, Done, Failed }
enum class FontSizeMode { Compact, Standard, Large }
enum class DisplayThemeMode { System, Light, Dark }

data class UserProfile(
    val name: String,
    val badgeNo: String,
    val department: String,
    val phone: String,
    val email: String,
    val dutyArea: String,
    val shiftDuration: String
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
    val cloudConnected: Boolean
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
    val contentUri: String? = null
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
    val operationMessage: String? = null,
    val device: DeviceStatus,
    val alerts: List<AlertItem>,
    val mediaFiles: List<MediaFile>,
    val user: UserProfile
)
