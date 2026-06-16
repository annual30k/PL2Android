package com.patrollink.data.remote

import com.patrollink.domain.GpsLocation

class MockRestApi {
    var lastAlertCloseRequest: AlertCloseRequestDto? = null
        private set

    private var device = DeviceStatusDto(
        deviceId = "HEADSET_001",
        deviceName = "ForceLink-H1",
        online = true,
        batteryPercent = 88,
        signalBars = 4,
        onlineDuration = "02:45:12",
        storageUsedGb = 42.5f,
        storageTotalGb = 128f,
        firmwareVersion = "v1.2.4",
        recordingStatus = "IDLE",
        talking = false,
        cloudConnected = true
    )

    private var alerts = listOf(
        AlertDto("AL-99824-03", "非法侵入监测", "CRITICAL", "PENDING", "14:32", "西三区 4号围墙 节点B", "CAM-042", "围墙节点 B 检测到人员越界，耳机端已同步 12 秒现场视频片段。", "98.4%"),
        AlertDto("AL-99824-04", "未识别车辆靠近", "WARNING", "PENDING", "14:38", "北侧周界入口", "RFID-09", "车牌识别失败，建议现场复核并记录车辆去向。", "91.2%"),
        AlertDto("AL-99821-11", "夜间巡查异常声源", "INFO", "CLOSED", "13:22", "核心商务区 CBD-North", "HEADSET_001", "环境音频超过阈值，现场确认无风险。", "74.8%")
    )

    private var media = listOf(
        MediaFileDto("VID-042", "CAM_04_A", "VIDEO", "2026-05-16 14:22:05", "84.1 MB", "04:12", true, "DEVICE", "IDLE", 0f),
        MediaFileDto("IMG-8821", "IMG_8821", "PHOTO", "2026-05-16 14:45:12", "2.4 MB", null, true, "DEVICE", "DONE", 1f),
        MediaFileDto("AUD-318", "VOICE_318", "AUDIO", "2026-05-16 14:50:02", "8.6 MB", "03:55", true, "PHONE", "IDLE", 0f),
        MediaFileDto("VID-051", "PATROL_051", "VIDEO", "2026-05-16 15:02:18", "126 MB", "08:12", false, "PHONE", "IDLE", 0f)
    )

    fun login(request: LoginRequestDto): ApiEnvelope<AuthSessionDto> {
        require(request.account.isNotBlank()) { "account required" }
        require(request.password.length >= 6) { "password too short" }
        return ok(AuthSessionDto("mock-access-${request.account}", "mock-refresh-${request.account}", 7200))
    }

    fun refresh(refreshToken: String): ApiEnvelope<AuthSessionDto> {
        require(refreshToken.startsWith("mock-refresh")) { "invalid refresh token" }
        return ok(AuthSessionDto(refreshToken.replace("refresh", "access"), refreshToken, 7200))
    }

    fun currentUser(): ApiEnvelope<UserProfileDto> = ok(
        UserProfileDto(
            userId = "U-9527",
            name = "张警官",
            badgeNo = "POLICE_9527",
            department = "第一巡逻支队",
            phone = "+86 138-0000-9527",
            email = "zhang.police@city.gov.cn",
            dutyArea = "福州温泉公园",
            shiftDuration = "05:24:12",
            patrolGroup = "巡逻组 A-42 | 温泉公园重点巡区",
            systemNode = "0x4F2A"
        )
    )

    fun currentPatrolArea(): ApiEnvelope<PatrolAreaDto> = ok(
        PatrolAreaDto(
            areaId = "AREA-FZ-WQ-001",
            areaName = "福州温泉公园重点巡区",
            teamId = "TEAM-A-42",
            teamName = "巡逻组 A-42",
            boundary = listOf(
                PatrolGeoPointDto(26.10295, 119.30485),
                PatrolGeoPointDto(26.10335, 119.31010),
                PatrolGeoPointDto(26.10020, 119.31115),
                PatrolGeoPointDto(26.09795, 119.30910),
                PatrolGeoPointDto(26.09815, 119.30465)
            ),
            route = listOf(
                PatrolGeoPointDto(26.09875, 119.30495),
                PatrolGeoPointDto(26.10020, 119.30655),
                PatrolGeoPointDto(26.10058, 119.30771),
                PatrolGeoPointDto(26.10155, 119.30900),
                PatrolGeoPointDto(26.10255, 119.30795)
            )
        )
    )

    fun scanDevices(): ApiEnvelope<List<ScannedDeviceDto>> = ok(
        listOf(
            ScannedDeviceDto("HEADSET_001", "ForceLink-H1", 4, "0000-pl2-ble-control", true, "2C:4A:91:3F:8B:02", "HEADSET"),
            ScannedDeviceDto("RECORDER_A5", "ForceLink-A5", 3, "0000-pl2-ble-control", false, "4F:02:8C:76:A1:19", "RECORDER"),
            ScannedDeviceDto("SENSOR_S9", "ForceLink-S9", 2, "0000-pl2-ble-control", false, "1E:BD:55:0A:44:71", "SENSOR"),
            ScannedDeviceDto("GLASSES_G1", "ForceLink-G1", 4, "0000-pl2-ble-control", false, "6B:13:9E:41:D7:50", "GLASSES")
        )
    )

    fun bindDevice(deviceId: String): ApiEnvelope<DeviceStatusDto> {
        device = device.copy(deviceId = deviceId, online = true, cloudConnected = true)
        return ok(device)
    }

    fun unbindDevice(deviceId: String): ApiEnvelope<DeviceStatusDto> {
        require(deviceId == device.deviceId) { "device not bound" }
        device = device.copy(online = false, recordingStatus = "IDLE", talking = false)
        return ok(device)
    }

    fun sendDeviceCommand(deviceId: String, request: DeviceCommandRequestDto): ApiEnvelope<DeviceStatusDto> {
        require(deviceId == device.deviceId) { "device not bound" }
        device = when (request.command) {
            "START_RECORD" -> device.copy(recordingStatus = "RECORDING")
            "STOP_RECORD" -> device.copy(recordingStatus = "IDLE")
            "START_TALK" -> device.copy(talking = true)
            "STOP_TALK" -> device.copy(talking = false)
            else -> device
        }
        return ok(device)
    }

    fun alerts(page: Int = 1, pageSize: Int = 20): ApiEnvelope<PageEnvelope<AlertDto>> =
        ok(PageEnvelope(alerts, page, pageSize, alerts.size.toLong(), hasMore = false))

    fun acknowledgeAlert(alertId: String): ApiEnvelope<AlertDto> =
        updateAlert(alertId) { it.copy(status = "HANDLING") }

    fun closeAlert(alertId: String, request: AlertCloseRequestDto): ApiEnvelope<AlertDto> {
        require(request.note.length <= 200) { "note too long" }
        lastAlertCloseRequest = request
        return updateAlert(alertId) { it.copy(status = "CLOSED") }
    }

    fun mediaFiles(local: Boolean, page: Int = 1, pageSize: Int = 50): ApiEnvelope<PageEnvelope<MediaFileDto>> {
        val side = if (local) "PHONE" else "DEVICE"
        val items = media.filter { it.storageSide == side }
        return ok(PageEnvelope(items, page, pageSize, items.size.toLong(), hasMore = false))
    }

    fun transferMedia(fileId: String, request: TransferRequestDto): List<ApiEnvelope<MediaFileDto>> {
        val original = when (request.target) {
            "PHONE_SANDBOX" -> media.first { it.fileId == fileId && it.storageSide == "DEVICE" }
            "CLOUD" -> media.firstOrNull { it.fileId == fileId && it.storageSide == "PHONE" } ?: media.first { it.fileId == fileId }
            else -> media.first { it.fileId == fileId }
        }
        val side = if (request.target == "PHONE_SANDBOX") "PHONE" else original.storageSide
        return listOf(
            original.copy(storageSide = side, transferStatus = "HASHING", progress = 0.1f),
            original.copy(storageSide = side, transferStatus = "UPLOADING", progress = 0.55f),
            original.copy(storageSide = side, transferStatus = "VERIFYING", progress = 0.9f),
            original.copy(storageSide = side, transferStatus = "DONE", progress = 1f, sha256Verified = true)
        ).map { step ->
            val stored = if (request.target == "PHONE_SANDBOX" && step.transferStatus == "DONE") {
                step.copy(transferStatus = "IDLE", progress = 0f)
            } else {
                step
            }
            media = upsertMedia(stored)
            ok(step)
        }
    }

    fun deleteMedia(fileId: String, storageSide: String? = null): ApiEnvelope<Boolean> {
        val before = media.size
        media = media.filterNot { it.fileId == fileId && (storageSide == null || it.storageSide == storageSide) }
        return ok(media.size < before)
    }

    fun verifyMedia(fileId: String): ApiEnvelope<Boolean> {
        require(media.any { it.fileId == fileId }) { "file not found" }
        media = media.map { if (it.fileId == fileId) it.copy(sha256Verified = true) else it }
        return ok(true)
    }

    fun heartbeat(request: HeartbeatRequestDto): ApiEnvelope<HeartbeatAckDto> =
        ok(HeartbeatAckDto(accepted = request.online, serverTime = 1715832000L, nextHeartbeatSeconds = 15))

    fun startStream(request: StreamRelayRequestDto): ApiEnvelope<StreamRelayStateDto> {
        require(request.deviceId.isNotBlank()) { "device required" }
        return ok(StreamRelayStateDto("RELAYING", relayUrl = "webrtc://mock/${request.deviceId}", latencyMs = if (request.mode == "LOW_LATENCY") 80 else 160))
    }

    fun stopStream(): ApiEnvelope<StreamRelayStateDto> = ok(StreamRelayStateDto("IDLE", null, null))

    fun activateSos(location: GpsLocationDto): ApiEnvelope<SosEventDto> =
        ok(SosEventDto("SOS-1715832000", "ACTIVE", "紧急上报已激活", location, recordingAudio = true, backupEtaMinutes = 4))

    fun cancelSos(): ApiEnvelope<SosEventDto> =
        ok(SosEventDto("SOS-CANCEL", "CANCELLED", "紧急上报已取消", null, recordingAudio = false, backupEtaMinutes = null))

    fun activateSos(location: GpsLocation): ApiEnvelope<SosEventDto> = activateSos(location.toDto())

    private fun updateAlert(alertId: String, transform: (AlertDto) -> AlertDto): ApiEnvelope<AlertDto> {
        var updated: AlertDto? = null
        alerts = alerts.map { alert ->
            if (alert.alertId == alertId) transform(alert).also { updated = it } else alert
        }
        return ok(requireNotNull(updated) { "alert not found" })
    }

    private fun upsertMedia(file: MediaFileDto): List<MediaFileDto> {
        var replaced = false
        val updated = media.map {
            if (it.fileId == file.fileId && it.storageSide == file.storageSide) {
                replaced = true
                file
            } else {
                it
            }
        }
        return if (replaced) updated else updated + file
    }

    private fun <T> ok(data: T): ApiEnvelope<T> = ApiEnvelope(
        code = 200,
        message = "OK",
        data = data,
        traceId = "mock-trace-0001",
        timestamp = 1715832000L
    )
}
