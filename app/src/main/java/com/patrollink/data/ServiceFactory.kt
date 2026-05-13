package com.patrollink.data

import android.content.Context
import android.net.Uri
import com.patrollink.data.ble.AndroidBleDeviceGateway
import com.patrollink.data.file.WifiFileServiceClient
import com.patrollink.data.remote.OkHttpPatrolRestApi
import com.patrollink.data.remote.toDomain
import com.patrollink.data.realtime.OkHttpWebSocketRealtimeGateway
import com.patrollink.domain.AppUiState
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaGateway
import com.patrollink.domain.PatrolCoordinator
import com.patrollink.domain.TransferStatus
import com.patrollink.domain.TransferTarget
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

object ServiceFactory {
    fun createCoordinator(): PatrolCoordinator = PatrolCoordinator(
        authGateway = MockAuthGateway(),
        deviceGateway = MockDeviceGateway(),
        alertGateway = MockAlertGateway(),
        mediaGateway = MockMediaGateway(),
        realtimeGateway = MockRealtimeGateway(),
        streamRelayGateway = MockStreamRelayGateway(),
        sosGateway = MockSosGateway()
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
            sosGateway = RestSosGateway(api)
        )
    }

    fun createRuntimeCoordinator(
        context: Context,
        config: RuntimeConfig,
        tokenProvider: () -> String?,
        operatorIdProvider: () -> String = { "UNKNOWN_OPERATOR" },
        fallbackState: AppUiState = MockPatrolRepository().initialState()
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

        val restMediaGateway = restApi?.let(::RestMediaGateway)
        val wifiMediaGateway = config.wifiFileBaseUrl.takeIf { it.isNotBlank() }?.let { baseUrl ->
            WifiBackedMediaGateway(
                wifiClient = WifiFileServiceClient(baseUrl),
                fallbackGateway = restMediaGateway ?: mockMedia,
                mediaDirectory = File(context.filesDir, "patrol_media/device")
            )
        }

        return PatrolCoordinator(
            authGateway = restApi?.let(::RestAuthGateway) ?: mockAuth,
            deviceGateway = when {
                config.useRealBle -> AndroidBleDeviceGateway(context, fallbackState.device)
                restApi != null -> RestDeviceGateway(restApi, operatorIdProvider)
                else -> mockDevice
            },
            alertGateway = restApi?.let { RestAlertGateway(it, operatorIdProvider) } ?: mockAlert,
            mediaGateway = wifiMediaGateway ?: restMediaGateway ?: mockMedia,
            realtimeGateway = when {
                config.webSocketUrl.isNotBlank() -> OkHttpWebSocketRealtimeGateway(config.webSocketUrl)
                restApi != null -> RestRealtimeGateway(restApi)
                else -> mockRealtime
            },
            streamRelayGateway = restApi?.let(::RestStreamRelayGateway) ?: mockStream,
            sosGateway = restApi?.let(::RestSosGateway) ?: mockSos
        )
    }
}

private class WifiBackedMediaGateway(
    private val wifiClient: WifiFileServiceClient,
    private val fallbackGateway: MediaGateway,
    private val mediaDirectory: File
) : MediaGateway {
    override suspend fun listFiles(local: Boolean): List<MediaFile> {
        if (local) return fallbackGateway.listFiles(local = true)
        return wifiClient.listDeviceFiles().data.map { it.toDomain() }
    }

    override fun transfer(fileId: String, target: TransferTarget): Flow<MediaFile> {
        if (target != TransferTarget.PhoneSandbox) return fallbackGateway.transfer(fileId, target)
        return flow {
            val start = deviceFile(fileId).copy(transferStatus = TransferStatus.Uploading, progress = 0.05f)
            emit(start)
            val targetFile = File(mediaDirectory, "$fileId.bin")
            val downloaded = wifiClient.download(fileId, targetFile)
            emit(
                start.copy(
                    local = true,
                    transferStatus = TransferStatus.Done,
                    progress = 1f,
                    contentUri = Uri.fromFile(downloaded).toString()
                )
            )
        }
    }

    override suspend fun delete(fileId: String): Boolean = fallbackGateway.delete(fileId)

    override suspend fun verifySha256(fileId: String): Boolean = fallbackGateway.verifySha256(fileId)

    private suspend fun deviceFile(fileId: String): MediaFile =
        listFiles(local = false).firstOrNull { it.id == fileId }
            ?: fallbackGateway.listFiles(local = false).firstOrNull { it.id == fileId }
            ?: error("media file not found: $fileId")
}
