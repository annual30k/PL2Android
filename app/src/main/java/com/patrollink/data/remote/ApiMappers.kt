package com.patrollink.data.remote

import com.patrollink.domain.AlertItem
import com.patrollink.domain.AlertLevel
import com.patrollink.domain.AlertStatus
import com.patrollink.domain.AuthSession
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.DeviceType
import com.patrollink.domain.GpsLocation
import com.patrollink.domain.HeartbeatAck
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaKind
import com.patrollink.domain.ScannedDevice
import com.patrollink.domain.SosEvent
import com.patrollink.domain.SosPhase
import com.patrollink.domain.SosState
import com.patrollink.domain.StreamRelayState
import com.patrollink.domain.TransferStatus
import com.patrollink.domain.UserProfile

fun AuthSessionDto.toDomain() = AuthSession(accessToken, refreshToken, expiresInSeconds)

fun UserProfileDto.toDomain() = UserProfile(name, badgeNo, department, phone, email, dutyArea, shiftDuration, patrolGroup, systemNode)

fun DeviceStatusDto.toDomain() = DeviceStatus(
    id = deviceId,
    name = deviceName,
    online = online,
    battery = batteryPercent,
    signalBars = signalBars,
    onlineDuration = onlineDuration,
    storageUsedGb = storageUsedGb,
    storageTotalGb = storageTotalGb,
    firmware = firmwareVersion,
    isRecording = recordingStatus == "RECORDING",
    isTalking = talking,
    cloudConnected = cloudConnected,
    type = DeviceType.Headset
)

fun ScannedDeviceDto.toDomain() = ScannedDevice(
    id = deviceId,
    name = deviceName,
    signalBars = signalBars,
    serviceUuid = serviceUuid,
    bonded = bonded,
    macAddress = macAddress,
    type = when (deviceType) {
        "RECORDER" -> DeviceType.Recorder
        "SENSOR" -> DeviceType.Sensor
        else -> DeviceType.Headset
    }
)

fun AlertDto.toDomain() = AlertItem(
    id = alertId,
    title = title,
    level = when (level) {
        "CRITICAL" -> AlertLevel.Critical
        "WARNING" -> AlertLevel.Warning
        else -> AlertLevel.Info
    },
    status = when (status) {
        "HANDLING" -> AlertStatus.Handling
        "CLOSED" -> AlertStatus.Closed
        else -> AlertStatus.Pending
    },
    time = occurredAt,
    location = locationText,
    source = source,
    description = description,
    confidence = confidence
)

fun MediaFileDto.toDomain() = MediaFile(
    id = fileId,
    name = fileName,
    kind = when (mediaType) {
        "PHOTO" -> MediaKind.Photo
        "AUDIO" -> MediaKind.Audio
        else -> MediaKind.Video
    },
    time = capturedAt,
    size = sizeText,
    duration = durationText,
    verified = sha256Verified,
    local = storageSide == "PHONE",
    transferStatus = when (transferStatus) {
        "HASHING" -> TransferStatus.Hashing
        "UPLOADING" -> TransferStatus.Uploading
        "VERIFYING" -> TransferStatus.Verifying
        "DONE" -> TransferStatus.Done
        "FAILED" -> TransferStatus.Failed
        else -> TransferStatus.Idle
    },
    progress = progress,
    contentUri = contentUri
)

fun HeartbeatAckDto.toDomain() = HeartbeatAck(accepted, serverTime)

fun StreamRelayStateDto.toDomain() = when (state) {
    "CONNECTING" -> StreamRelayState.Connecting
    "RELAYING" -> StreamRelayState.Relaying
    "FAILED" -> StreamRelayState.Failed
    else -> StreamRelayState.Idle
}

fun GpsLocation.toDto() = GpsLocationDto(latitude, longitude, accuracyMeters, address)

fun GpsLocationDto.toDomain() = GpsLocation(latitude, longitude, accuracyMeters, address)

fun SosEventDto.toDomainEvent() = SosEvent(
    id = sosId,
    phase = phase.toSosPhase(),
    message = message
)

fun SosEventDto.toDomainState() = SosState(
    phase = phase.toSosPhase(),
    location = location?.toDomain(),
    recordingAudio = recordingAudio,
    backupEtaMinutes = backupEtaMinutes
)

private fun String.toSosPhase() = when (this) {
    "ACTIVATING" -> SosPhase.Activating
    "ACTIVE" -> SosPhase.Active
    "CANCELLED" -> SosPhase.Cancelled
    else -> SosPhase.Idle
}
