package com.patrollink.data.remote

interface PatrolRestApi {
    suspend fun login(request: LoginRequestDto): ApiEnvelope<AuthSessionDto>
    suspend fun refresh(refreshToken: String): ApiEnvelope<AuthSessionDto>
    suspend fun currentUser(): ApiEnvelope<UserProfileDto>
    suspend fun cerebellumSettings(): ApiEnvelope<CerebellumSettingsDto>
    suspend fun saveCerebellumSettings(request: CerebellumSettingsDto): ApiEnvelope<CerebellumSettingsDto>
    suspend fun scanDevices(): ApiEnvelope<List<ScannedDeviceDto>>
    suspend fun bindDevice(deviceId: String): ApiEnvelope<DeviceStatusDto>
    suspend fun unbindDevice(deviceId: String): ApiEnvelope<DeviceStatusDto>
    suspend fun sendDeviceCommand(deviceId: String, request: DeviceCommandRequestDto): ApiEnvelope<DeviceStatusDto>
    suspend fun deviceCapabilities(deviceId: String): ApiEnvelope<DeviceCapabilitiesDto>
    suspend fun deviceWifi(deviceId: String): ApiEnvelope<DeviceWifiStateDto>
    suspend fun configureWifi(deviceId: String, request: DeviceWifiStateDto): ApiEnvelope<DeviceWifiStateDto>
    suspend fun applyDeviceSettings(deviceId: String, request: DeviceAdvancedSettingsDto): ApiEnvelope<DeviceAdvancedSettingsDto>
    suspend fun startRealtimeAudioSync(deviceId: String): ApiEnvelope<DeviceControlResultDto>
    suspend fun stopRealtimeAudioSync(deviceId: String): ApiEnvelope<DeviceControlResultDto>
    suspend fun notifyMediaSyncCompleted(deviceId: String): ApiEnvelope<DeviceControlResultDto>
    suspend fun clearDeviceAccount(deviceId: String): ApiEnvelope<DeviceControlResultDto>
    suspend fun factoryResetDevice(deviceId: String, target: String): ApiEnvelope<DeviceControlResultDto>
    suspend fun alerts(page: Int, pageSize: Int): ApiEnvelope<PageEnvelope<AlertDto>>
    suspend fun acknowledgeAlert(alertId: String): ApiEnvelope<AlertDto>
    suspend fun closeAlert(alertId: String, request: AlertCloseRequestDto): ApiEnvelope<AlertDto>
    suspend fun mediaFiles(local: Boolean, page: Int, pageSize: Int): ApiEnvelope<PageEnvelope<MediaFileDto>>
    suspend fun uploadMedia(file: java.io.File, storageSide: String = "PHONE", bizType: String = "", bizId: String = ""): ApiEnvelope<MediaFileDto>
    suspend fun uploadMediaResumable(file: java.io.File, storageSide: String = "PHONE", bizType: String = "", bizId: String = ""): ApiEnvelope<MediaUploadTaskDto>
    suspend fun transferMedia(fileId: String, request: TransferRequestDto): List<ApiEnvelope<MediaFileDto>>
    suspend fun deleteMedia(fileId: String, storageSide: String): ApiEnvelope<Boolean>
    suspend fun verifyMedia(fileId: String): ApiEnvelope<Boolean>
    suspend fun heartbeat(request: HeartbeatRequestDto): ApiEnvelope<HeartbeatAckDto>
    suspend fun messages(targetId: String, page: Int, pageSize: Int): ApiEnvelope<PageEnvelope<PatrolMessageDto>>
    suspend fun readMessage(messageId: String): ApiEnvelope<PatrolMessageDto>
    suspend fun updateDailyReportContent(reportId: String, request: DailyReportContentUpdateDto): ApiEnvelope<DailyReportDto>
    suspend fun startStream(request: StreamRelayRequestDto): ApiEnvelope<StreamRelayStateDto>
    suspend fun stopStream(): ApiEnvelope<StreamRelayStateDto>
    suspend fun createIntercomSession(request: IntercomSessionRequestDto): ApiEnvelope<IntercomSessionDto>
    suspend fun pendingIntercomSession(deviceId: String): ApiEnvelope<IntercomSessionDto?>
    suspend fun acceptIntercomSession(sessionId: String): ApiEnvelope<IntercomSessionDto>
    suspend fun closeIntercomSession(sessionId: String): ApiEnvelope<IntercomSessionDto>
    suspend fun sendIntercomSignal(sessionId: String, request: IntercomSignalRequestDto): ApiEnvelope<IntercomSignalDto>
    suspend fun intercomSignals(sessionId: String, afterSignalId: String = ""): ApiEnvelope<List<IntercomSignalDto>>
    suspend fun currentPatrolArea(): ApiEnvelope<PatrolAreaDto>
    suspend fun activateSos(location: GpsLocationDto): ApiEnvelope<SosEventDto>
    suspend fun cancelSos(): ApiEnvelope<SosEventDto>
    suspend fun checkVersion(currentVersionCode: Int): ApiEnvelope<VersionCheckResultDto>
    suspend fun checkFirmware(deviceId: String, request: FirmwareCheckRequestDto): ApiEnvelope<FirmwareCheckResultDto>
    suspend fun createFirmwareUpgradeTask(deviceId: String, request: FirmwareUpgradeTaskCreateDto): ApiEnvelope<FirmwareUpgradeTaskDto>
    suspend fun updateFirmwareUpgradeTask(taskId: String, request: FirmwareUpgradeTaskUpdateDto): ApiEnvelope<FirmwareUpgradeTaskDto>
}
