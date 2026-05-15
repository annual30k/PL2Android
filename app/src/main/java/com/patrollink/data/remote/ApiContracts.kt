package com.patrollink.data.remote

data class ApiEnvelope<T>(
    val code: Int,
    val message: String = "",
    val msg: String? = null,
    val data: T,
    val traceId: String = "",
    val timestamp: Long = 0
) {
    val success: Boolean get() = code == 200
    val displayMessage: String get() = listOf(message, msg).firstOrNull { !it.isNullOrBlank() } ?: "API request failed"
}

data class PageEnvelope<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val total: Long,
    val hasMore: Boolean
)

data class LoginRequestDto(
    val account: String,
    val password: String,
    val clientType: String = "ANDROID",
    val deviceModel: String = "android-device"
)

data class AuthSessionDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val tokenType: String = "Bearer"
)

data class UserProfileDto(
    val userId: String,
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

data class CerebellumSettingsDto(
    val baseUrl: String = "",
    val apiKey: String = ""
)

data class DeviceStatusDto(
    val deviceId: String,
    val deviceName: String,
    val online: Boolean,
    val batteryPercent: Int,
    val signalBars: Int,
    val onlineDuration: String,
    val storageUsedGb: Float,
    val storageTotalGb: Float,
    val firmwareVersion: String,
    val recordingStatus: String,
    val talking: Boolean,
    val cloudConnected: Boolean
)

data class ScannedDeviceDto(
    val deviceId: String,
    val deviceName: String,
    val signalBars: Int,
    val serviceUuid: String,
    val bonded: Boolean,
    val macAddress: String,
    val deviceType: String
)

data class DeviceCommandRequestDto(
    val command: String,
    val operatorId: String,
    val requestId: String
)

data class DeviceCapabilitiesDto(
    val supportsGlasses: Boolean,
    val supportsEarphone: Boolean,
    val supportsWifi: Boolean,
    val supportsFileTransfer: Boolean,
    val supportsPhoto: Boolean,
    val supportsVideo: Boolean,
    val supportsAudioRecord: Boolean,
    val supportsRealtimeAudio: Boolean
)

data class DeviceWifiStateDto(
    val enabled: Boolean,
    val ssid: String,
    val passwordConfigured: Boolean,
    val connected: Boolean
)

data class DeviceAdvancedSettingsDto(
    val videoWidth: Int,
    val videoHeight: Int,
    val videoFrameRate: Int,
    val recordingDurationSeconds: Int,
    val verticalRecording: Boolean,
    val enhancedSound: Boolean,
    val brightnessLevel: Int
)

data class DeviceControlResultDto(
    val success: Boolean,
    val state: String,
    val message: String
)

data class AlertDto(
    val alertId: String,
    val title: String,
    val level: String,
    val status: String,
    val occurredAt: String,
    val locationText: String,
    val source: String,
    val description: String,
    val confidence: String
)

data class AlertCloseRequestDto(
    val result: String,
    val note: String,
    val operatorId: String,
    val attachments: List<UploadAttachmentDto> = emptyList()
)

data class AlertDraftRequestDto(
    val alertId: String,
    val result: String,
    val note: String,
    val operatorId: String,
    val attachments: List<UploadAttachmentDto>
)

data class UploadAttachmentDto(
    val clientFileId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val source: String,
    val localUri: String?,
    val uploadIntent: String = "SUBMIT_WITH_FORM"
)

data class MediaFileDto(
    val fileId: String,
    val fileName: String,
    val mediaType: String,
    val capturedAt: String,
    val sizeText: String,
    val durationText: String?,
    val sha256Verified: Boolean,
    val storageSide: String,
    val transferStatus: String,
    val progress: Float,
    val contentUri: String? = null
)

data class MediaUploadTaskCreateDto(
    val fileName: String,
    val mediaType: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val chunkSizeBytes: Long,
    val totalChunks: Int,
    val sha256: String,
    val storageSide: String,
    val bizType: String,
    val bizId: String
)

data class MediaUploadTaskDto(
    val taskId: String,
    val fileId: String?,
    val fileName: String,
    val mediaType: String,
    val mimeType: String?,
    val fileSizeBytes: Long,
    val chunkSizeBytes: Long,
    val totalChunks: Int,
    val uploadedChunks: Int,
    val uploadedChunkIndexes: List<Int> = emptyList(),
    val uploadedBytes: Long,
    val expectedSha256: String?,
    val actualSha256: String?,
    val storageSide: String,
    val bizType: String?,
    val bizId: String?,
    val status: String,
    val progress: Float,
    val errorMessage: String?,
    val badgeNo: String?,
    val officerName: String?,
    val deviceId: String?,
    val completedAt: String?
)

data class TransferRequestDto(
    val target: String,
    val chunkSizeBytes: Int = 1024 * 512,
    val resumeToken: String? = null
)

data class HeartbeatRequestDto(
    val deviceId: String,
    val online: Boolean,
    val batteryPercent: Int,
    val signalBars: Int,
    val recordingStatus: String,
    val clientTimestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val address: String? = null
)

data class HeartbeatAckDto(
    val accepted: Boolean,
    val serverTime: Long,
    val nextHeartbeatSeconds: Int
)

data class StreamRelayRequestDto(
    val deviceId: String,
    val mode: String,
    val protocol: String = "WEBRTC"
)

data class StreamRelayStateDto(
    val state: String,
    val relayUrl: String?,
    val latencyMs: Int?
)

data class IntercomSessionRequestDto(
    val deviceId: String,
    val mode: String = "FULL_DUPLEX",
    val initiatorId: String = "android-app"
)

data class IntercomSessionDto(
    val sessionId: String,
    val deviceId: String,
    val state: String,
    val mode: String,
    val signalingUrl: String,
    val audioRoute: String,
    val iceServers: List<String> = emptyList(),
    val startedAt: Long? = null,
    val expiresAt: Long? = null,
    val message: String
)

data class IntercomSignalRequestDto(
    val sender: String = "APP",
    val type: String,
    val payload: String = ""
)

data class IntercomSignalDto(
    val signalId: String,
    val sessionId: String,
    val sender: String,
    val type: String,
    val payload: String,
    val timestamp: Long
)

data class GpsLocationDto(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val address: String
)

data class PatrolGeoPointDto(
    val latitude: Double,
    val longitude: Double
)

data class PatrolAreaDto(
    val areaId: String,
    val areaName: String,
    val teamId: String,
    val teamName: String,
    val boundary: List<PatrolGeoPointDto>,
    val route: List<PatrolGeoPointDto>
)

data class SosEventDto(
    val sosId: String,
    val phase: String,
    val message: String,
    val location: GpsLocationDto?,
    val recordingAudio: Boolean,
    val backupEtaMinutes: Int?
)

data class PatrolMessageDto(
    val messageId: String,
    val title: String,
    val content: String,
    val targetType: String,
    val deliveryStatus: String = "",
    val deliveredAt: String = "",
    val readAt: String = "",
    val status: String,
    val sentAt: String
)

data class DailyReportContentUpdateDto(
    val content: String
)

data class DailyReportDto(
    val reportId: String? = null,
    val content: String? = null
)

data class VersionCheckResultDto(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val forceUpdate: Boolean,
    val changelog: List<String>,
    val downloadUrl: String?,
    val sha256: String?
)
