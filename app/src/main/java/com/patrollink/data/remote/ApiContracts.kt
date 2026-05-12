package com.patrollink.data.remote

data class ApiEnvelope<T>(
    val code: Int,
    val message: String,
    val data: T,
    val traceId: String,
    val timestamp: Long
) {
    val success: Boolean get() = code == 200
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
    val deviceModel: String = "mock-device"
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
    val shiftDuration: String
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
    val bonded: Boolean
)

data class DeviceCommandRequestDto(
    val command: String,
    val operatorId: String,
    val requestId: String
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
    val operatorId: String
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
    val clientTimestamp: Long
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

data class GpsLocationDto(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val address: String
)

data class SosEventDto(
    val sosId: String,
    val phase: String,
    val message: String,
    val location: GpsLocationDto?,
    val recordingAudio: Boolean,
    val backupEtaMinutes: Int?
)
