package com.patrollink.data.remote

import com.patrollink.domain.AlertItem
import com.patrollink.domain.AlertLevel
import com.patrollink.domain.AlertStatus
import com.patrollink.domain.AuthSession
import com.patrollink.domain.DeviceAdvancedSettings
import com.patrollink.domain.DeviceCapabilities
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.DeviceType
import com.patrollink.domain.DeviceWifiState
import com.patrollink.domain.FirmwareCheckResult
import com.patrollink.domain.FirmwareUpgradeTask
import com.patrollink.domain.GpsLocation
import com.patrollink.domain.HeartbeatAck
import com.patrollink.domain.IntercomSession
import com.patrollink.domain.IntercomState
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaKind
import com.patrollink.domain.PatrolArea
import com.patrollink.domain.PatrolGeoPoint
import com.patrollink.domain.ScannedDevice
import com.patrollink.domain.SosEvent
import com.patrollink.domain.SosPhase
import com.patrollink.domain.SosState
import com.patrollink.domain.StreamRelayState
import com.patrollink.domain.TransferStatus
import com.patrollink.domain.TransferTarget
import com.patrollink.domain.UserProfile
import com.patrollink.domain.VersionCheckResult

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
        "GLASSES" -> DeviceType.Glasses
        else -> DeviceType.Headset
    }
)

fun DeviceCapabilitiesDto.toDomain() = DeviceCapabilities(
    supportsGlasses = supportsGlasses,
    supportsEarphone = supportsEarphone,
    supportsWifi = supportsWifi,
    supportsFileTransfer = supportsFileTransfer,
    supportsPhoto = supportsPhoto,
    supportsVideo = supportsVideo,
    supportsAudioRecord = supportsAudioRecord,
    supportsRealtimeAudio = supportsRealtimeAudio
)

fun DeviceWifiStateDto.toDomain() = DeviceWifiState(enabled, ssid, passwordConfigured, connected)

fun DeviceWifiState.toDto(password: String = "") = DeviceWifiStateDto(
    enabled = enabled,
    ssid = ssid,
    passwordConfigured = password.isNotBlank() || passwordConfigured,
    connected = connected
)

fun DeviceAdvancedSettingsDto.toDomain() = DeviceAdvancedSettings(
    videoWidth = videoWidth,
    videoHeight = videoHeight,
    videoFrameRate = videoFrameRate,
    recordingDurationSeconds = recordingDurationSeconds,
    verticalRecording = verticalRecording,
    enhancedSound = enhancedSound,
    brightnessLevel = brightnessLevel
)

fun DeviceAdvancedSettings.toDto() = DeviceAdvancedSettingsDto(
    videoWidth = videoWidth,
    videoHeight = videoHeight,
    videoFrameRate = videoFrameRate,
    recordingDurationSeconds = recordingDurationSeconds,
    verticalRecording = verticalRecording,
    enhancedSound = enhancedSound,
    brightnessLevel = brightnessLevel
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

fun MediaFileDto.toDomain(): MediaFile {
    val status = when (transferStatus) {
        "HASHING" -> TransferStatus.Hashing
        "UPLOADING" -> TransferStatus.Uploading
        "VERIFYING" -> TransferStatus.Verifying
        "DONE" -> TransferStatus.Done
        "FAILED" -> TransferStatus.Failed
        else -> TransferStatus.Idle
    }
    return MediaFile(
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
        transferStatus = status,
        progress = progress,
        contentUri = contentUri,
        lastTransferTarget = if (storageSide == "PHONE" && status == TransferStatus.Done) TransferTarget.Cloud else null
    )
}

fun MediaUploadTaskDto.toDomain(): MediaFile {
    val transferStatus = when (status) {
        "MERGING", "VERIFYING" -> TransferStatus.Verifying
        "UPLOADING", "UPLOADED" -> TransferStatus.Uploading
        "DONE" -> TransferStatus.Done
        "FAILED", "CANCELLED", "EXPIRED" -> TransferStatus.Failed
        else -> TransferStatus.Idle
    }
    return MediaFile(
        id = fileId ?: taskId,
        name = fileName,
        kind = when (mediaType) {
            "PHOTO" -> MediaKind.Photo
            "AUDIO" -> MediaKind.Audio
            else -> MediaKind.Video
        },
        time = completedAt.orEmpty(),
        size = fileSizeBytes.toSizeText(),
        duration = null,
        verified = status == "DONE" && !actualSha256.isNullOrBlank(),
        local = storageSide == "PHONE",
        transferStatus = transferStatus,
        progress = progress,
        contentUri = fileId?.let { "/files/$it/download" },
        lastTransferTarget = if (storageSide == "PHONE" && transferStatus == TransferStatus.Done) TransferTarget.Cloud else null
    )
}

fun HeartbeatAckDto.toDomain() = HeartbeatAck(accepted, serverTime)

fun StreamRelayStateDto.toDomain() = when (state) {
    "CONNECTING" -> StreamRelayState.Connecting
    "RELAYING" -> StreamRelayState.Relaying
    "FAILED" -> StreamRelayState.Failed
    else -> StreamRelayState.Idle
}

fun IntercomSessionDto.toDomain() = IntercomSession(
    sessionId = sessionId,
    deviceId = deviceId,
    state = when (state) {
        "WAITING_APP" -> IntercomState.WaitingApp
        "SIGNALING" -> IntercomState.Signaling
        "ACTIVE" -> IntercomState.Active
        "CLOSED" -> IntercomState.Closed
        "FAILED" -> IntercomState.Failed
        else -> IntercomState.Idle
    },
    mode = mode,
    signalingUrl = signalingUrl,
    audioRoute = audioRoute,
    iceServers = iceServers,
    message = message
)

fun GpsLocation.toDto() = GpsLocationDto(latitude, longitude, accuracyMeters, address)

fun GpsLocationDto.toDomain() = GpsLocation(latitude, longitude, accuracyMeters, address)

fun PatrolGeoPointDto.toDomain() = PatrolGeoPoint(latitude, longitude)

fun PatrolAreaDto.toDomain() = PatrolArea(
    id = areaId,
    name = areaName,
    teamId = teamId,
    teamName = teamName,
    boundary = boundary.map { it.toDomain() },
    route = route.map { it.toDomain() }
)

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

fun VersionCheckResultDto.toDomain() = VersionCheckResult(
    latestVersionCode = latestVersionCode,
    latestVersionName = latestVersionName,
    forceUpdate = forceUpdate,
    changelog = changelog,
    downloadUrl = downloadUrl,
    sha256 = sha256
)

fun FirmwareCheckResultDto.toDomain() = FirmwareCheckResult(
    hasUpdate = hasUpdate,
    firmwareId = firmwareId,
    deviceType = deviceType,
    vendor = vendor,
    chipset = chipset,
    deviceModel = deviceModel,
    hardwareVersion = hardwareVersion,
    firmwareType = firmwareType,
    versionCode = versionCode,
    versionName = versionName,
    forceUpdate = forceUpdate,
    changelog = changelog,
    downloadUrl = downloadUrl,
    sha256 = sha256,
    fileId = fileId,
    fileSizeBytes = fileSizeBytes,
    packageFormat = packageFormat,
    upgradeMode = upgradeMode,
    currentFirmwareVersion = currentFirmwareVersion,
    message = message
)

fun FirmwareUpgradeTaskDto.toDomain() = FirmwareUpgradeTask(
    taskId = taskId,
    deviceId = deviceId,
    firmwareId = firmwareId,
    operatorId = operatorId,
    fromVersion = fromVersion,
    toVersion = toVersion,
    status = status,
    progress = progress,
    errorCode = errorCode,
    errorMessage = errorMessage,
    startedAt = startedAt,
    finishedAt = finishedAt
)

private fun String.toSosPhase() = when (this) {
    "ACTIVATING" -> SosPhase.Activating
    "ACTIVE" -> SosPhase.Active
    "CANCELLED" -> SosPhase.Cancelled
    else -> SosPhase.Idle
}

private fun Long.toSizeText(): String {
    val mb = this / 1024.0 / 1024.0
    return if (mb >= 1.0) {
        "%.1f MB".format(mb)
    } else {
        "${this / 1024} KB"
    }
}
