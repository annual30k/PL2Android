package com.patrollink.data.ute

import com.patrollink.data.EmptyFirmwareGateway
import com.patrollink.data.resolveBackendDownloadUrl
import com.patrollink.data.shouldAttachBackendAuthorization
import com.patrollink.data.remote.OkHttpPatrolRestApi
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.FirmwareCheckResult
import com.patrollink.domain.FirmwareDeviceMetadata
import com.patrollink.domain.FirmwareGateway
import com.patrollink.domain.FirmwareUpgradeState
import com.patrollink.domain.FirmwareUpgradeTask
import com.yc.nadalsdk.bean.DownloadConfig
import com.yc.nadalsdk.bean.UpgradeConfig
import com.yc.nadalsdk.bean.VersionConfig
import java.io.File
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class UteSdkFirmwareGateway(
    private val bridge: UteSdkBridge,
    private val firmwareDirectory: File,
    private val delegate: FirmwareGateway = EmptyFirmwareGateway(),
    private val operatorIdProvider: () -> String = { "" },
    private val tokenProvider: () -> String? = { null },
    private val apiBaseUrl: String = "",
    private val onStatusSyncFailed: suspend (String, FirmwareUpgradeState) -> Unit = { _, _ -> },
    private val httpClient: OkHttpClient = OkHttpClient()
) : FirmwareGateway {
    override suspend fun check(device: DeviceStatus, metadata: FirmwareDeviceMetadata): FirmwareCheckResult {
        val enriched = metadata.withUteDeviceInfo()
        return runCatching { delegate.check(device, enriched) }
            .getOrElse { device.localFirmwareResult(enriched, "固件检查服务不可用，已读取本机设备版本") }
    }

    override fun install(device: DeviceStatus, firmware: FirmwareCheckResult): Flow<FirmwareUpgradeState> = flow {
        require(bridge.client.isConnected) { "UTE device is not connected" }
        var task = createUpgradeTask(device, firmware, operatorIdProvider())

        suspend fun emitAndUpdate(state: FirmwareUpgradeState) {
            val normalized = state.toBackendTerminalState()
            emit(normalized)
            task = runCatching { updateUpgradeTask(task.taskId, normalized) }
                .getOrElse {
                    onStatusSyncFailed(task.taskId, normalized)
                    task
                }
        }

        try {
            emitAndUpdate(FirmwareUpgradeState("DOWNLOADING", 0.05f))
            val packageFile = downloadFirmwarePackage(firmware)
            if (packageFile != null && firmware.requiresJlOta(packageFile)) {
                UteJlOtaController(bridge).install(packageFile).collect { emitAndUpdate(it) }
                return@flow
            }
            emitAndUpdate(FirmwareUpgradeState("PREPARING_DEVICE", 0.35f))

            val connection = bridge.connection
            withContext(Dispatchers.IO) {
                connection.openUpgradeChannel(firmware, packageFile)
            }
            emitAndUpdate(FirmwareUpgradeState("DEVICE_UPGRADE_STARTED", 0.75f))
            if (firmware.packageFormat.contains("ISP", ignoreCase = true)) {
                withContext(Dispatchers.IO) { runCatching { connection.setUpgradeIspFirmware() } }
            }
            emitAndUpdate(FirmwareUpgradeState("DEVICE_UPGRADE_HANDOFF", 1f))
        } catch (throwable: Throwable) {
            emitAndUpdate(
                FirmwareUpgradeState(
                    status = "DEVICE_UPGRADE_FAILED",
                    progress = 0f,
                    errorCode = throwable::class.java.simpleName,
                    errorMessage = throwable.message.orEmpty().ifBlank { "固件升级失败" }
                )
            )
            throw throwable
        }
    }

    override suspend fun createUpgradeTask(device: DeviceStatus, firmware: FirmwareCheckResult, operatorId: String): FirmwareUpgradeTask =
        delegate.createUpgradeTask(device, firmware, operatorId)

    override suspend fun updateUpgradeTask(taskId: String, state: FirmwareUpgradeState): FirmwareUpgradeTask =
        delegate.updateUpgradeTask(taskId, state)

    private suspend fun FirmwareDeviceMetadata.withUteDeviceInfo(): FirmwareDeviceMetadata = withContext(Dispatchers.IO) {
        val info = runCatching { bridge.connection.smartGetDeviceInfo().data }.getOrNull()
        copy(
            vendor = vendor.ifBlank { "UTE" },
            chipset = chipset.ifBlank { info?.deviceVersionType.orEmpty() },
            deviceModel = deviceModel.ifBlank { info?.certModel.orEmpty() },
            hardwareVersion = hardwareVersion.ifBlank { info?.deviceHardwareVersion.orEmpty() }
        )
    }

    private fun DeviceStatus.localFirmwareResult(metadata: FirmwareDeviceMetadata, message: String): FirmwareCheckResult =
        FirmwareCheckResult(
            hasUpdate = false,
            firmwareId = null,
            deviceType = type.name.uppercase(),
            vendor = metadata.vendor,
            chipset = metadata.chipset,
            deviceModel = metadata.deviceModel,
            hardwareVersion = metadata.hardwareVersion,
            firmwareType = "",
            versionCode = null,
            versionName = firmware,
            forceUpdate = false,
            changelog = emptyList(),
            downloadUrl = null,
            sha256 = null,
            fileId = null,
            fileSizeBytes = 0L,
            packageFormat = "",
            upgradeMode = "",
            currentFirmwareVersion = firmware,
            message = message
        )

    private suspend fun downloadFirmwarePackage(firmware: FirmwareCheckResult): File? = withContext(Dispatchers.IO) {
        val url = resolveBackendDownloadUrl(apiBaseUrl, firmware.downloadUrl) ?: return@withContext null
        firmwareDirectory.mkdirs()
        val suffix = File(url.substringBefore('?')).extension.takeIf { it.isNotBlank() } ?: "bin"
        val fileName = (firmware.firmwareId ?: firmware.versionName.ifBlank { "firmware" }).safeFirmwareFileName()
        val file = File(firmwareDirectory, "$fileName.$suffix")
        if (url.startsWith("file://")) {
            File(URI(url)).copyTo(file, overwrite = true)
        } else {
            val request = Request.Builder()
                .url(url)
                .header("clientid", OkHttpPatrolRestApi.DEFAULT_CLIENT_ID)
                .apply {
                    if (shouldAttachBackendAuthorization(apiBaseUrl, url)) {
                        tokenProvider()?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
                    }
                }
                .build()
            httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "firmware download failed: HTTP ${response.code}" }
                val body = response.body ?: error("firmware download body is empty")
                file.outputStream().use { output -> body.byteStream().copyTo(output) }
            }
        }
        if (firmware.fileSizeBytes > 0L) {
            check(file.length() == firmware.fileSizeBytes) {
                "firmware size mismatch: expected ${firmware.fileSizeBytes}, actual ${file.length()}"
            }
        }
        firmware.sha256?.takeIf { it.isNotBlank() }?.let { expected ->
            val actual = file.sha256()
            check(actual.equals(expected, ignoreCase = true)) { "firmware sha256 mismatch" }
        }
        file
    }

    private fun FirmwareCheckResult.requiresJlOta(packageFile: File): Boolean =
        packageFormat.contains("JL", ignoreCase = true) ||
            packageFormat.contains("JIELI", ignoreCase = true) ||
            upgradeMode.contains("JL", ignoreCase = true) ||
            packageFile.extension.equals("ufw", ignoreCase = true)

    private fun com.yc.nadalsdk.ble.open.UteBleConnection.openUpgradeChannel(
        firmware: FirmwareCheckResult,
        packageFile: File?
    ) {
        val versionName = firmware.versionName.ifBlank { firmware.currentFirmwareVersion }
        if (firmware.downloadUrl != null || packageFile != null) {
            notifyNewVersion(
                VersionConfig().apply {
                    setSize(packageFile?.length() ?: firmware.fileSizeBytes)
                    setVersionNumber(versionName)
                    setDetected(true)
                    setCheckTime((System.currentTimeMillis() / 1000).toInt())
                }
            )
            setDownloadConfirmResponse(
                DownloadConfig().apply {
                    setNetworkConfirm(false)
                    setAppUpgradeStatus(2)
                }
            )
        }
        val accepted = prepareUpgrade(
            UpgradeConfig().apply {
                setVersion(versionName)
                setMode(UpgradeConfig.FOREGROUND)
            }
        ).isSuccess
        check(accepted) { "device rejected firmware upgrade preparation" }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun String.safeFirmwareFileName(): String =
        replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "firmware" }
}

private fun FirmwareUpgradeState.toBackendTerminalState(): FirmwareUpgradeState = when {
    status.contains("COMPLETED", ignoreCase = true) || status.equals("SUCCESS", ignoreCase = true) ->
        copy(status = "SUCCESS", progress = 1f, errorCode = "", errorMessage = "")
    status.contains("CANCELLED", ignoreCase = true) -> copy(status = "CANCELLED")
    status.contains("FAILED", ignoreCase = true) || status.contains("ERROR", ignoreCase = true) ->
        copy(status = "FAILED", progress = 0f)
    else -> this
}
