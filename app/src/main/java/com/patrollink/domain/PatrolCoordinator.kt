package com.patrollink.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class PatrolCoordinator(
    private val authGateway: AuthGateway,
    private val deviceGateway: DeviceGateway,
    private val alertGateway: AlertGateway,
    private val mediaGateway: MediaGateway,
    private val realtimeGateway: RealtimeGateway,
    private val streamRelayGateway: StreamRelayGateway,
    private val sosGateway: SosGateway
) {
    suspend fun loginAndStartSession(account: String, password: String): AuthSession {
        val session = authGateway.login(account, password)
        realtimeGateway.connect(session.accessToken)
        return session
    }

    fun scanDevices(): Flow<List<ScannedDevice>> = deviceGateway.scan()

    suspend fun bindDevice(deviceId: String): DeviceStatus = deviceGateway.bind(deviceId)

    suspend fun takePhoto(device: DeviceStatus): DeviceStatus =
        deviceGateway.sendCommand(device.id, DeviceCommand.TakePhoto)

    suspend fun setRecording(device: DeviceStatus, enabled: Boolean): DeviceStatus =
        deviceGateway.sendCommand(device.id, if (enabled) DeviceCommand.StartRecord else DeviceCommand.StopRecord)

    suspend fun setTalk(device: DeviceStatus, enabled: Boolean): DeviceStatus =
        deviceGateway.sendCommand(device.id, if (enabled) DeviceCommand.StartTalk else DeviceCommand.StopTalk)

    fun observeAlerts(): Flow<List<AlertItem>> = alertGateway.observeAlerts()

    suspend fun handleAlert(alertId: String, result: AlertResult, note: String = ""): AlertItem {
        if (result == AlertResult.RequestBackup) {
            alertGateway.acknowledge(alertId)
            return alertGateway.close(alertId, result, note.ifBlank { "请求支援" })
        }
        return alertGateway.close(alertId, result, note.ifBlank { result.name })
    }

    suspend fun mediaFiles(local: Boolean): List<MediaFile> = mediaGateway.listFiles(local)

    fun transferMedia(fileId: String, target: TransferTarget): Flow<MediaFile> = mediaGateway.transfer(fileId, target)

    suspend fun deleteMedia(fileId: String, local: Boolean): Boolean = mediaGateway.delete(fileId, local)

    suspend fun verifyMedia(fileId: String): Boolean = mediaGateway.verifySha256(fileId)

    suspend fun sendHeartbeat(device: DeviceStatus): HeartbeatAck = realtimeGateway.sendHeartbeat(device)

    fun realtimeConnection(): Flow<RealtimeConnection> = realtimeGateway.connection()

    suspend fun startStream(device: DeviceStatus, mode: StreamMode = StreamMode.LowLatency) {
        streamRelayGateway.start(device.id, mode)
    }

    suspend fun stopStream() = streamRelayGateway.stop()

    fun streamState(): Flow<StreamRelayState> = streamRelayGateway.state()

    suspend fun activateSos(location: GpsLocation): SosEvent = sosGateway.activate(location)

    suspend fun cancelSos(): SosEvent = sosGateway.cancel()

    fun sosState(): Flow<SosState> = sosGateway.state()

    suspend fun currentRealtimeState(): RealtimeConnection = realtimeGateway.connection().first()
}
