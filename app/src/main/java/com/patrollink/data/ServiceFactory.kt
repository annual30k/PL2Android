package com.patrollink.data

import android.content.Context
import android.net.Uri
import com.patrollink.data.ble.AndroidBleDeviceGateway
import com.patrollink.data.ble.BleGattProfile
import com.patrollink.data.edge.CerebellumApi
import com.patrollink.data.edge.OkHttpCerebellumApi
import com.patrollink.data.file.WifiFileServiceClient
import com.patrollink.data.location.AndroidLocationGateway
import com.patrollink.data.notification.AndroidPatrolNotificationGateway
import com.patrollink.data.local.PatrolDatabase
import com.patrollink.data.local.RoomMediaIndex
import com.patrollink.data.remote.OkHttpPatrolRestApi
import com.patrollink.data.remote.toDomain
import com.patrollink.data.realtime.OkHttpWebSocketRealtimeGateway
import com.patrollink.data.sos.AndroidSosEvidenceRecorder
import com.patrollink.data.sos.MockEmergencyContactGateway
import com.patrollink.data.ute.UteSdkBridge
import com.patrollink.data.ute.UteSdkDeviceControlGateway
import com.patrollink.data.ute.UteSdkDeviceGateway
import com.patrollink.data.ute.UteSdkMediaGateway
import com.patrollink.data.ute.UteSdkStreamRelayGateway
import com.patrollink.data.update.AndroidVersionInstaller
import com.patrollink.data.voip.AndroidWebRtcIntercomClient
import com.patrollink.data.voip.BluetoothVoipAudioRouter
import com.patrollink.domain.AppUiState
import com.patrollink.domain.DeviceControlGateway
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaGateway
import com.patrollink.domain.PatrolCoordinator
import com.patrollink.domain.StreamMode
import com.patrollink.domain.StreamRelayGateway
import com.patrollink.domain.StreamRelayState
import com.patrollink.domain.TransferStatus
import com.patrollink.domain.TransferTarget
import com.patrollink.domain.VersionGateway
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

object ServiceFactory {
    fun createCoordinator(): PatrolCoordinator = PatrolCoordinator(
        authGateway = MockAuthGateway(),
        deviceGateway = MockDeviceGateway(),
        alertGateway = MockAlertGateway(),
        mediaGateway = MockMediaGateway(),
        realtimeGateway = MockRealtimeGateway(),
        streamRelayGateway = MockStreamRelayGateway(),
        sosGateway = MockSosGateway(),
        patrolAreaGateway = MockPatrolAreaGateway(),
        intercomGateway = MockIntercomGateway()
    )

    fun createRestCoordinator(
        baseUrl: String,
        tokenProvider: () -> String?,
        operatorIdProvider: () -> String = { "UNKNOWN_OPERATOR" }
    ): PatrolCoordinator {
        val api = OkHttpPatrolRestApi(baseUrl = baseUrl, tokenProvider = tokenProvider)
        return PatrolCoordinator(
            authGateway = RestAuthGateway(api),
            deviceGateway = RestDeviceGateway(api, operatorIdProvider),
            alertGateway = RestAlertGateway(api, operatorIdProvider),
            mediaGateway = RestMediaGateway(api),
            realtimeGateway = RestRealtimeGateway(api),
            streamRelayGateway = RestStreamRelayGateway(api),
            sosGateway = RestSosGateway(api),
            patrolAreaGateway = RestPatrolAreaGateway(api),
            intercomGateway = RestIntercomGateway(api)
        )
    }

    fun createRuntimeCoordinator(
        context: Context,
        config: RuntimeConfig,
        tokenProvider: () -> String?,
        operatorIdProvider: () -> String = { "UNKNOWN_OPERATOR" },
        fallbackState: AppUiState = MockPatrolRepository().initialState(),
        sharedUteBridge: UteSdkBridge? = null
    ): PatrolCoordinator {
        val restApi = config.restBaseUrl.takeIf { it.isNotBlank() }?.let {
            OkHttpPatrolRestApi(baseUrl = it, tokenProvider = tokenProvider)
        }
        val mockAuth = MockAuthGateway()
        val mockDevice = MockDeviceGateway()
        val mockAlert = MockAlertGateway()
        val mockMedia = MockMediaGateway()
        val mockRealtime = MockRealtimeGateway()
        val mockStream = MockStreamRelayGateway()
        val mockSos = MockSosGateway()
        val mockPatrolArea = MockPatrolAreaGateway()
        val uteBridge = if (config.useRealBle) sharedUteBridge ?: UteSdkBridge(context) else null
        val restMediaGateway = restApi?.let(::RestMediaGateway)
        val wifiMediaGateway = config.wifiFileBaseUrl.takeIf { it.isNotBlank() }?.let { baseUrl ->
            val mediaIndex = RoomMediaIndex(PatrolDatabase.get(context).mediaFileDao())
            WifiBackedMediaGateway(
                wifiClient = WifiFileServiceClient(baseUrl, tokenProvider = tokenProvider),
                fallbackGateway = restMediaGateway ?: mockMedia,
                mediaDirectory = File(context.filesDir, "patrol_media/device"),
                officerBadgeNo = fallbackState.user.badgeNo,
                mediaIndex = mediaIndex
            )
        }

        return PatrolCoordinator(
            authGateway = restApi?.let(::RestAuthGateway) ?: mockAuth,
            deviceGateway = when {
                config.useRealBle -> AndroidBleDeviceGateway(
                    context = context,
                    fallbackStatus = fallbackState.device,
                    profile = BleGattProfile.fromStrings(
                        service = config.bleServiceUuid,
                        command = config.bleCommandUuid,
                        status = config.bleStatusUuid
                    )
                ).takeIf { config.bleServiceUuid.isNotBlank() }
                    ?: UteSdkDeviceGateway(uteBridge ?: UteSdkBridge(context), fallbackState.device)
                restApi != null -> RestDeviceGateway(restApi, operatorIdProvider)
                else -> mockDevice
            },
            alertGateway = restApi?.let { RestAlertGateway(it, operatorIdProvider) } ?: mockAlert,
            mediaGateway = when {
                wifiMediaGateway != null -> wifiMediaGateway
                uteBridge != null -> UteSdkMediaGateway(
                    bridge = uteBridge,
                    fallbackGateway = restMediaGateway ?: mockMedia,
                    mediaDirectory = File(context.filesDir, "patrol_media/ute"),
                    officerBadgeNo = fallbackState.user.badgeNo
                )
                restMediaGateway != null -> restMediaGateway
                else -> mockMedia
            },
            realtimeGateway = when {
                config.webSocketUrl.isNotBlank() -> OkHttpWebSocketRealtimeGateway(config.webSocketUrl)
                restApi != null -> RestRealtimeGateway(restApi)
                else -> mockRealtime
            },
            streamRelayGateway = when {
                restApi != null -> RestStreamRelayGateway(restApi)
                config.streamRelayUrl.isNotBlank() -> ConfiguredStreamRelayGateway(config.streamRelayUrl)
                uteBridge != null -> UteSdkStreamRelayGateway(uteBridge)
                else -> mockStream
            },
            sosGateway = restApi?.let(::RestSosGateway) ?: mockSos,
            patrolAreaGateway = restApi?.let(::RestPatrolAreaGateway) ?: mockPatrolArea,
            intercomGateway = restApi?.let {
                val audioRouter = BluetoothVoipAudioRouter(context)
                RestIntercomGateway(
                    api = it,
                    audioRouter = audioRouter,
                    webRtcClient = AndroidWebRtcIntercomClient(context, it, audioRouter)
                )
            } ?: MockIntercomGateway()
        )
    }

    fun createDeviceControlGateway(
        context: Context,
        config: RuntimeConfig,
        sharedUteBridge: UteSdkBridge? = null,
        tokenProvider: () -> String? = { null },
        deviceIdProvider: () -> String = { "HEADSET_001" }
    ): DeviceControlGateway =
        when {
            config.useRealBle -> UteSdkDeviceControlGateway(sharedUteBridge ?: UteSdkBridge(context))
            config.restBaseUrl.isNotBlank() -> RestDeviceControlGateway(
                OkHttpPatrolRestApi(baseUrl = config.restBaseUrl, tokenProvider = tokenProvider),
                deviceIdProvider
            )
            else -> MockDeviceControlGateway()
        }

    fun createLocationGateway(context: Context, fallbackState: AppUiState = MockPatrolRepository().initialState()) =
        AndroidLocationGateway(context, fallbackState.sosLocation)

    fun createSosEvidenceRecorder(context: Context) = AndroidSosEvidenceRecorder(context)

    fun createEmergencyContactGateway() = MockEmergencyContactGateway()

    fun createNotificationGateway(context: Context) = AndroidPatrolNotificationGateway(context)

    fun createVersionInstaller(context: Context) = AndroidVersionInstaller(context)

    fun createVersionGateway(config: RuntimeConfig, tokenProvider: () -> String? = { null }): VersionGateway =
        config.restBaseUrl.takeIf { it.isNotBlank() }
            ?.let { RestVersionGateway(OkHttpPatrolRestApi(baseUrl = it, tokenProvider = tokenProvider)) }
            ?: MockVersionGateway()

    fun createCerebellumApi(config: RuntimeConfig): CerebellumApi? =
        config.cerebellumBaseUrl.takeIf { it.isNotBlank() }?.let { baseUrl ->
            OkHttpCerebellumApi(
                baseUrl = baseUrl,
                apiKeyProvider = { config.cerebellumApiKey }
            )
        }
}

private class ConfiguredStreamRelayGateway(
    private val relayUrl: String
) : StreamRelayGateway {
    private val state = MutableStateFlow(StreamRelayState.Idle)

    override fun state(): Flow<StreamRelayState> = state.asStateFlow()

    override suspend fun start(deviceId: String, mode: StreamMode) {
        require(relayUrl.isNotBlank()) { "stream relay url required" }
        require(deviceId.isNotBlank()) { "device required" }
        state.value = StreamRelayState.Connecting
        state.value = StreamRelayState.Relaying
    }

    override suspend fun stop() {
        state.value = StreamRelayState.Idle
    }
}

private class WifiBackedMediaGateway(
    private val wifiClient: WifiFileServiceClient,
    private val fallbackGateway: MediaGateway,
    private val mediaDirectory: File,
    private val officerBadgeNo: String,
    private val mediaIndex: RoomMediaIndex?,
    private val integrityGateway: DefaultEvidenceIntegrityGateway = DefaultEvidenceIntegrityGateway()
) : MediaGateway {
    override suspend fun listFiles(local: Boolean): List<MediaFile> {
        val indexed = mediaIndex?.files(local).orEmpty()
        if (local && indexed.isNotEmpty()) return indexed
        if (local) return fallbackGateway.listFiles(local = true)
        val phoneIds = mediaIndex?.files(local = true).orEmpty().map { it.id }.toSet()
        val remote = wifiClient.listDeviceFiles().data.map { it.toDomain() }.map { file ->
            if (file.id in phoneIds) {
                file.copy(
                    transferStatus = TransferStatus.Done,
                    progress = 1f,
                    lastTransferTarget = TransferTarget.PhoneSandbox
                )
            } else {
                file
            }
        }
        remote.forEach { mediaIndex?.upsert(it) }
        return remote
    }

    override fun transfer(fileId: String, target: TransferTarget): Flow<MediaFile> {
        if (target != TransferTarget.PhoneSandbox) {
            return flow {
                val localFile = File(mediaDirectory, "$fileId.bin")
                if (!localFile.exists()) {
                    val start = deviceFile(fileId).copy(transferStatus = TransferStatus.Uploading, progress = 0.05f, lastTransferTarget = TransferTarget.PhoneSandbox)
                    emit(start)
                    val downloaded = wifiClient.download(fileId, localFile.also { it.parentFile?.mkdirs() })
                    emit(start.copy(transferStatus = TransferStatus.Hashing, progress = 0.45f))
                    val sha256 = integrityGateway.sha256(downloaded.readBytes())
                    val token = integrityGateway.watermarkToken(fileId, officerBadgeNo, downloaded.lastModified())
                    File(mediaDirectory, "$fileId.integrity").writeText("sha256=$sha256\nwatermark=$token\n")
                    val completed = start.copy(
                        local = true,
                        transferStatus = TransferStatus.Uploading,
                        verified = true,
                        progress = 0.62f,
                        contentUri = Uri.fromFile(downloaded).toString(),
                        lastTransferTarget = TransferTarget.Cloud
                    )
                    mediaIndex?.upsert(
                        completed.copy(transferStatus = TransferStatus.Idle, progress = 0f, lastTransferTarget = null),
                        localPath = completed.contentUri,
                        sha256 = sha256,
                        watermarkToken = token
                    )
                    emit(completed)
                }
                val local = mediaIndex?.find(fileId, local = true)
                    ?: deviceFile(fileId).copy(local = true, contentUri = Uri.fromFile(localFile).toString())
                emit(local.copy(transferStatus = TransferStatus.Uploading, progress = 0.18f, lastTransferTarget = target))
                val uploaded = fallbackGateway.uploadLocalFile(localFile, storageSide = "PHONE", bizType = "MEDIA", bizId = fileId)
                emit(
                    (uploaded ?: local).copy(
                        id = fileId,
                        local = true,
                        transferStatus = TransferStatus.Done,
                        progress = 1f,
                        verified = true,
                        contentUri = Uri.fromFile(localFile).toString(),
                        lastTransferTarget = target
                    )
                )
            }
        }
        return flow {
            val start = deviceFile(fileId).copy(transferStatus = TransferStatus.Uploading, progress = 0.05f)
            emit(start)
            val targetFile = File(mediaDirectory, "$fileId.bin")
            val downloaded = wifiClient.download(fileId, targetFile)
            emit(start.copy(transferStatus = TransferStatus.Hashing, progress = 0.82f))
            val sha256 = integrityGateway.sha256(downloaded.readBytes())
            val token = integrityGateway.watermarkToken(fileId, officerBadgeNo, downloaded.lastModified())
            File(mediaDirectory, "$fileId.integrity").writeText("sha256=$sha256\nwatermark=$token\n")
            emit(start.copy(transferStatus = TransferStatus.Verifying, progress = 0.94f))
            val completed = start.copy(
                local = true,
                transferStatus = TransferStatus.Done,
                verified = true,
                progress = 1f,
                contentUri = Uri.fromFile(downloaded).toString(),
                lastTransferTarget = TransferTarget.PhoneSandbox
            )
            mediaIndex?.upsert(
                start.copy(
                    transferStatus = TransferStatus.Done,
                    progress = 1f,
                    lastTransferTarget = TransferTarget.PhoneSandbox
                )
            )
            mediaIndex?.upsert(
                completed.copy(transferStatus = TransferStatus.Idle, progress = 0f, lastTransferTarget = null),
                localPath = completed.contentUri,
                sha256 = sha256,
                watermarkToken = token
            )
            emit(completed)
        }
    }

    override suspend fun delete(fileId: String, local: Boolean): Boolean {
        if (local) {
            mediaIndex?.find(fileId, local = true)?.contentUri
                ?.let(Uri::parse)
                ?.path
                ?.let(::File)
                ?.takeIf { it.exists() }
                ?.delete()
            File(mediaDirectory, "$fileId.bin").takeIf { it.exists() }?.delete()
            File(mediaDirectory, "$fileId.integrity").takeIf { it.exists() }?.delete()
            mediaIndex?.delete(fileId, local = true)
            return true
        }
        val deleted = runCatching { wifiClient.delete(fileId).data }
            .getOrElse { fallbackGateway.delete(fileId, local = false) }
        if (deleted) mediaIndex?.delete(fileId, local = false)
        return deleted
    }

    override suspend fun uploadLocalFile(file: File, storageSide: String, bizType: String, bizId: String): MediaFile? =
        fallbackGateway.uploadLocalFile(file, storageSide, bizType, bizId)

    override suspend fun verifySha256(fileId: String): Boolean {
        val localFile = File(mediaDirectory, "$fileId.bin")
        if (localFile.exists()) {
            val hash = integrityGateway.sha256(localFile.readBytes())
            val token = integrityGateway.watermarkToken(fileId, officerBadgeNo, localFile.lastModified())
            File(mediaDirectory, "$fileId.integrity").writeText("sha256=$hash\nwatermark=$token\n")
            mediaIndex?.find(fileId, local = true)?.let { mediaIndex.upsert(it.copy(verified = true), sha256 = hash, watermarkToken = token) }
            return true
        }
        return fallbackGateway.verifySha256(fileId)
    }

    private suspend fun deviceFile(fileId: String): MediaFile =
        listFiles(local = false).firstOrNull { it.id == fileId }
            ?: fallbackGateway.listFiles(local = false).firstOrNull { it.id == fileId }
            ?: error("media file not found: $fileId")
}
