package com.patrollink.domain

enum class AlertLevel { Critical, Warning, Info }
enum class AlertStatus { Pending, Handling, Closed }
enum class MediaKind { Video, Photo, Audio }
enum class TransferStatus { Idle, Hashing, Uploading, Verifying, Done, Failed }

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
    val progress: Float
)

data class AppUiState(
    val isLoggedIn: Boolean = false,
    val networkOnline: Boolean = true,
    val loginLoading: Boolean = false,
    val selectedAlertTab: AlertStatus = AlertStatus.Pending,
    val selectedMediaLocal: Boolean = false,
    val sosActive: Boolean = false,
    val device: DeviceStatus,
    val alerts: List<AlertItem>,
    val mediaFiles: List<MediaFile>,
    val user: UserProfile
)
