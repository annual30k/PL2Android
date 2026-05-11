package com.patrollink.domain

import kotlinx.coroutines.flow.Flow

interface AuthGateway {
    suspend fun login(account: String, password: String): AuthSession
    suspend fun refresh(refreshToken: String): AuthSession
    suspend fun currentUser(): UserProfile
}

interface DeviceGateway {
    fun scan(): Flow<List<ScannedDevice>>
    suspend fun bind(deviceId: String): DeviceStatus
    suspend fun sendCommand(deviceId: String, command: DeviceCommand): DeviceStatus
}

interface AlertGateway {
    fun observeAlerts(): Flow<List<AlertItem>>
    suspend fun acknowledge(alertId: String): AlertItem
    suspend fun close(alertId: String, result: AlertResult, note: String): AlertItem
}

interface MediaGateway {
    suspend fun listFiles(local: Boolean): List<MediaFile>
    fun transfer(fileId: String, target: TransferTarget): Flow<MediaFile>
    suspend fun delete(fileId: String): Boolean
    suspend fun verifySha256(fileId: String): Boolean
}

interface RealtimeGateway {
    fun connection(): Flow<RealtimeConnection>
    suspend fun connect(token: String)
    suspend fun disconnect()
    suspend fun sendHeartbeat(device: DeviceStatus): HeartbeatAck
}

interface StreamRelayGateway {
    fun state(): Flow<StreamRelayState>
    suspend fun start(deviceId: String, mode: StreamMode)
    suspend fun stop()
}

interface SosGateway {
    fun state(): Flow<SosState>
    suspend fun activate(location: GpsLocation): SosEvent
    suspend fun cancel(): SosEvent
}

interface SecureStore {
    suspend fun saveSession(session: AuthSession)
    suspend fun readSession(): AuthSession?
    suspend fun clearSession()
}

interface PermissionGateway {
    fun requiredPermissions(androidApi: Int): List<AppPermission>
    fun missingPermissions(androidApi: Int, granted: Set<AppPermission>): List<AppPermission>
}

interface BackgroundTaskGateway {
    suspend fun enqueue(task: BackgroundTask): BackgroundTaskReceipt
    suspend fun pending(): List<BackgroundTaskReceipt>
    suspend fun complete(taskId: String): Boolean
}

interface EvidenceIntegrityGateway {
    fun sha256(bytes: ByteArray): String
    fun watermarkToken(fileId: String, officerBadgeNo: String, timestamp: Long): String
}
