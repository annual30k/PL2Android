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
import com.patrollink.data.ute.UteSdkBridge
import com.patrollink.data.ute.UteSdkDeviceControlGateway
import com.patrollink.data.ute.UteSdkDeviceGateway
import com.patrollink.data.ute.UteSdkFirmwareGateway
import com.patrollink.data.ute.UteSdkMediaGateway
import com.patrollink.data.ute.UteWifiMediaClient
import com.patrollink.data.ute.requireUteCloudUploadResult
import com.patrollink.data.sourcenex.RoutingDeviceControlGateway
import com.patrollink.data.sourcenex.RoutingDeviceGateway
import com.patrollink.data.sourcenex.RoutingMediaGateway
import com.patrollink.data.sourcenex.SourceNexBridge
import com.patrollink.data.sourcenex.SourceNexDeviceGateway
import com.patrollink.data.sourcenex.SourceNexMediaGateway
import com.patrollink.data.update.AndroidVersionInstaller
import com.patrollink.data.voip.AndroidWebRtcIntercomClient
import com.patrollink.data.voip.BluetoothVoipAudioRouter
import com.patrollink.domain.AppUiState
import com.patrollink.domain.DeviceControlGateway
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaGateway
import com.patrollink.domain.PatrolCoordinator
import com.patrollink.domain.TransferStatus
import com.patrollink.domain.TransferTarget
import com.patrollink.domain.VersionGateway
import com.patrollink.domain.FirmwareGateway
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

object ServiceFactory {
    fun createCoordinator(): PatrolCoordinator = PatrolCoordinator(
        authGateway = EmptyAuthGateway(),
        deviceGateway = EmptyDeviceGateway(),
        alertGateway = EmptyAlertGateway(),
        mediaGateway = EmptyMediaGateway(),
        realtimeGateway = EmptyRealtimeGateway(),
        streamRelayGateway = EmptyStreamRelayGateway(),
        sosGateway = EmptySosGateway(),
        patrolAreaGateway = EmptyPatrolAreaGateway(),
        intercomGateway = EmptyIntercomGateway()
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
        pairingAccountIdProvider: () -> String = operatorIdProvider,
        selectedDeviceIdProvider: () -> String = { "" },
        fallbackState: AppUiState,
        sharedUteBridge: UteSdkBridge? = null,
        sharedSourceNexBridge: SourceNexBridge? = null
    ): PatrolCoordinator {
        val restApi = config.restBaseUrl.takeIf { it.isNotBlank() }?.let {
            OkHttpPatrolRestApi(baseUrl = it, tokenProvider = tokenProvider)
        }
        val uteBridge = if (config.useRealBle) sharedUteBridge ?: UteSdkBridge(context) else null
        val sourceNexBridge = if (config.useRealBle) sharedSourceNexBridge ?: SourceNexBridge(context) else null
        val restMediaGateway = restApi?.let(::RestMediaGateway)
        val emptyMediaGateway = EmptyMediaGateway()
        val mediaIndex = RoomMediaIndex(
            PatrolDatabase.get(context).mediaFileDao(),
            accountKeyProvider = pairingAccountIdProvider
        )
        val uteMediaDirectory = File(context.filesDir, "patrol_media/ute")
        val uteWifiMediaClient = uteBridge?.let {
            UteWifiMediaClient(
                context = context,
                bridge = it,
                pairingAccountIdProvider = pairingAccountIdProvider
            )
        }
        val photoLocationGateway by lazy { AndroidLocationGateway(context, fallbackState.sosLocation) }
        val wifiMediaGateway = config.wifiFileBaseUrl.takeIf { it.isNotBlank() }?.let { baseUrl ->
            WifiBackedMediaGateway(
                wifiClient = WifiFileServiceClient(baseUrl, tokenProvider = tokenProvider),
                fallbackGateway = restMediaGateway ?: emptyMediaGateway,
                mediaDirectory = File(context.filesDir, "patrol_media/device"),
                officerBadgeNo = fallbackState.user.badgeNo,
                mediaIndex = mediaIndex
            )
        }

        return PatrolCoordinator(
            authGateway = restApi?.let(::RestAuthGateway) ?: EmptyAuthGateway(),
            deviceGateway = when {
                config.useRealBle -> {
                    val gattProfile = BleGattProfile.fromStrings(
                        service = config.bleServiceUuid,
                        command = config.bleCommandUuid,
                        status = config.bleStatusUuid
                    )
                    val existingGateway = if (gattProfile.readyForGatt) {
                        AndroidBleDeviceGateway(
                            context = context,
                            fallbackStatus = fallbackState.device,
                            profile = gattProfile
                        )
                    } else {
                        UteSdkDeviceGateway(
                            bridge = uteBridge ?: UteSdkBridge(context),
                            fallbackStatus = fallbackState.device,
                            mediaDirectory = uteMediaDirectory,
                            pairingAccountIdProvider = pairingAccountIdProvider,
                            photoLocationProvider = { photoLocationGateway.currentLocation() }
                        )
                    }
                    val localGateway = RoutingDeviceGateway(
                        ute = existingGateway,
                        sourceNex = SourceNexDeviceGateway(
                            bridge = sourceNexBridge ?: SourceNexBridge(context),
                            fallback = fallbackState.device
                        )
                    )
                    if (restApi != null) CloudRegisteredDeviceGateway(localGateway, restApi) else localGateway
                }
                restApi != null -> RestDeviceGateway(restApi, operatorIdProvider)
                else -> EmptyDeviceGateway()
            },
            alertGateway = restApi?.let { RestAlertGateway(it, operatorIdProvider) } ?: EmptyAlertGateway(),
            mediaGateway = when {
                wifiMediaGateway != null -> wifiMediaGateway
                uteBridge != null -> {
                    val uteMedia = UteSdkMediaGateway(
                        bridge = uteBridge,
                        fallbackGateway = restMediaGateway ?: emptyMediaGateway,
                        mediaDirectory = uteMediaDirectory,
                        officerBadgeNo = fallbackState.user.badgeNo,
                        mediaIndex = mediaIndex,
                        wifiMediaClient = uteWifiMediaClient
                    )
                    if (sourceNexBridge != null) {
                        RoutingMediaGateway(
                            bridge = sourceNexBridge,
                            ute = uteMedia,
                            sourceNex = SourceNexMediaGateway(
                                bridge = sourceNexBridge,
                                fallback = restMediaGateway ?: emptyMediaGateway,
                                mediaDirectory = File(context.filesDir, "patrol_media/sourcenex"),
                                officerBadgeNo = fallbackState.user.badgeNo,
                                mediaIndex = mediaIndex
                            )
                        )
                    } else uteMedia
                }
                restMediaGateway != null -> restMediaGateway
                else -> emptyMediaGateway
            },
            realtimeGateway = when {
                config.webSocketUrl.isNotBlank() -> OkHttpWebSocketRealtimeGateway(config.webSocketUrl)
                restApi != null -> RestRealtimeGateway(restApi)
                else -> EmptyRealtimeGateway()
            },
            streamRelayGateway = when {
                restApi != null -> RestStreamRelayGateway(restApi)
                else -> EmptyStreamRelayGateway()
            },
            sosGateway = restApi?.let { RestSosGateway(it, deviceIdProvider = selectedDeviceIdProvider) } ?: EmptySosGateway(),
            patrolAreaGateway = restApi?.let(::RestPatrolAreaGateway) ?: EmptyPatrolAreaGateway(),
            intercomGateway = restApi?.let {
                val audioRouter = BluetoothVoipAudioRouter(context)
                RestIntercomGateway(
                    api = it,
                    audioRouter = audioRouter,
                    webRtcClient = AndroidWebRtcIntercomClient(context, it, audioRouter)
                )
            } ?: EmptyIntercomGateway()
        )
    }

    fun createDeviceControlGateway(
        context: Context,
        config: RuntimeConfig,
        sharedUteBridge: UteSdkBridge? = null,
        sharedSourceNexBridge: SourceNexBridge? = null,
        tokenProvider: () -> String? = { null },
        deviceIdProvider: () -> String = { "" },
        pairingAccountIdProvider: () -> String = { "UNKNOWN_OPERATOR" }
    ): DeviceControlGateway =
        when {
            config.useRealBle -> {
                val ute = UteSdkDeviceControlGateway(
                    bridge = sharedUteBridge ?: UteSdkBridge(context),
                    mediaDirectory = File(context.filesDir, "patrol_media/ute"),
                    pairingAccountIdProvider = pairingAccountIdProvider
                )
                RoutingDeviceControlGateway(
                    bridge = sharedSourceNexBridge ?: SourceNexBridge(context),
                    ute = ute
                )
            }
            config.restBaseUrl.isNotBlank() -> RestDeviceControlGateway(
                OkHttpPatrolRestApi(baseUrl = config.restBaseUrl, tokenProvider = tokenProvider),
                deviceIdProvider
            )
            else -> EmptyDeviceControlGateway()
        }

    fun createLocationGateway(context: Context, fallbackState: AppUiState) =
        AndroidLocationGateway(context, fallbackState.sosLocation)

    fun createSosEvidenceRecorder(context: Context) = AndroidSosEvidenceRecorder(context)

    fun createNotificationGateway(context: Context) = AndroidPatrolNotificationGateway(context)

    fun createVersionInstaller(
        context: Context,
        tokenProvider: () -> String? = { null },
        apiBaseUrl: String = ""
    ) = AndroidVersionInstaller(context, tokenProvider = tokenProvider, apiBaseUrl = apiBaseUrl)

    fun createVersionGateway(config: RuntimeConfig, tokenProvider: () -> String? = { null }): VersionGateway =
        config.restBaseUrl.takeIf { it.isNotBlank() }
            ?.let { RestVersionGateway(OkHttpPatrolRestApi(baseUrl = it, tokenProvider = tokenProvider), it) }
            ?: EmptyVersionGateway()

    fun createFirmwareGateway(
        context: Context? = null,
        config: RuntimeConfig,
        sharedUteBridge: UteSdkBridge? = null,
        tokenProvider: () -> String? = { null },
        operatorIdProvider: () -> String = { "" },
        onStatusSyncFailed: suspend (String, com.patrollink.domain.FirmwareUpgradeState) -> Unit = { _, _ -> }
    ): FirmwareGateway {
        val restGateway = config.restBaseUrl.takeIf { it.isNotBlank() }
            ?.let { RestFirmwareGateway(OkHttpPatrolRestApi(baseUrl = it, tokenProvider = tokenProvider), it) }
        val uteBridge = when {
            !config.useRealBle -> null
            sharedUteBridge != null -> sharedUteBridge
            context != null -> UteSdkBridge(context)
            else -> null
        }
        return if (uteBridge != null && context != null) {
            UteSdkFirmwareGateway(
                bridge = uteBridge,
                firmwareDirectory = File(context.filesDir, "patrol_firmware/ute"),
                delegate = restGateway ?: EmptyFirmwareGateway(),
                operatorIdProvider = operatorIdProvider,
                tokenProvider = tokenProvider,
                apiBaseUrl = config.restBaseUrl,
                onStatusSyncFailed = onStatusSyncFailed
            )
        } else {
            restGateway ?: EmptyFirmwareGateway()
        }
    }

    fun createCerebellumApi(config: RuntimeConfig): CerebellumApi? =
        config.cerebellumBaseUrl.takeIf { it.isNotBlank() }?.let { baseUrl ->
            OkHttpCerebellumApi(
                baseUrl = baseUrl,
                apiKeyProvider = { config.cerebellumApiKey }
            )
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
                    val sha256 = integrityGateway.sha256(downloaded)
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
                val completed = requireUteCloudUploadResult(fileId, uploaded).copy(
                    id = fileId,
                    local = true,
                    transferStatus = TransferStatus.Done,
                    progress = 1f,
                    verified = true,
                    contentUri = Uri.fromFile(localFile).toString(),
                    lastTransferTarget = target
                )
                val sha256 = runCatching { integrityGateway.sha256(localFile) }.getOrNull()
                mediaIndex?.upsert(completed, localPath = completed.contentUri, sha256 = sha256)
                emit(completed)
            }
        }
        return flow {
            val start = deviceFile(fileId).copy(transferStatus = TransferStatus.Uploading, progress = 0.05f)
            emit(start)
            val targetFile = File(mediaDirectory, "$fileId.bin")
            val downloaded = wifiClient.download(fileId, targetFile)
            emit(start.copy(transferStatus = TransferStatus.Hashing, progress = 0.82f))
            val sha256 = integrityGateway.sha256(downloaded)
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
            val expected = mediaIndex?.expectedSha256(fileId, local = true)
                ?: readIntegritySha256(File(mediaDirectory, "$fileId.integrity"))
            return withContext(Dispatchers.IO) { integrityGateway.matchesSha256(localFile, expected) }
        }
        return fallbackGateway.verifySha256(fileId)
    }

    private suspend fun deviceFile(fileId: String): MediaFile =
        listFiles(local = false).firstOrNull { it.id == fileId }
            ?: fallbackGateway.listFiles(local = false).firstOrNull { it.id == fileId }
            ?: error("media file not found: $fileId")
}

private fun readIntegritySha256(file: File): String? =
    file.takeIf { it.isFile }
        ?.useLines { lines -> lines.firstOrNull { it.startsWith("sha256=") } }
        ?.substringAfter('=')
        ?.trim()
