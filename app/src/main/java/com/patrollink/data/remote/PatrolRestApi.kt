package com.patrollink.data.remote

interface PatrolRestApi {
    suspend fun login(request: LoginRequestDto): ApiEnvelope<AuthSessionDto>
    suspend fun refresh(refreshToken: String): ApiEnvelope<AuthSessionDto>
    suspend fun currentUser(): ApiEnvelope<UserProfileDto>
    suspend fun scanDevices(): ApiEnvelope<List<ScannedDeviceDto>>
    suspend fun bindDevice(deviceId: String): ApiEnvelope<DeviceStatusDto>
    suspend fun sendDeviceCommand(deviceId: String, request: DeviceCommandRequestDto): ApiEnvelope<DeviceStatusDto>
    suspend fun alerts(page: Int, pageSize: Int): ApiEnvelope<PageEnvelope<AlertDto>>
    suspend fun acknowledgeAlert(alertId: String): ApiEnvelope<AlertDto>
    suspend fun closeAlert(alertId: String, request: AlertCloseRequestDto): ApiEnvelope<AlertDto>
    suspend fun mediaFiles(local: Boolean, page: Int, pageSize: Int): ApiEnvelope<PageEnvelope<MediaFileDto>>
    suspend fun transferMedia(fileId: String, request: TransferRequestDto): List<ApiEnvelope<MediaFileDto>>
    suspend fun deleteMedia(fileId: String): ApiEnvelope<Boolean>
    suspend fun verifyMedia(fileId: String): ApiEnvelope<Boolean>
    suspend fun heartbeat(request: HeartbeatRequestDto): ApiEnvelope<HeartbeatAckDto>
    suspend fun startStream(request: StreamRelayRequestDto): ApiEnvelope<StreamRelayStateDto>
    suspend fun stopStream(): ApiEnvelope<StreamRelayStateDto>
    suspend fun activateSos(location: GpsLocationDto): ApiEnvelope<SosEventDto>
    suspend fun cancelSos(): ApiEnvelope<SosEventDto>
}
