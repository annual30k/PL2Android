package com.patrollink.data.ute

import com.patrollink.domain.DeviceAdvancedSettings
import com.patrollink.domain.DeviceCapabilities
import com.patrollink.domain.DeviceControlGateway
import com.patrollink.domain.DeviceEvent
import com.patrollink.domain.DeviceEventLevel
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.DeviceType
import com.patrollink.domain.DeviceWifiState
import com.yc.nadalsdk.bean.Response
import com.yc.nadalsdk.bean.recorder.AudioRecordInfo
import com.yc.nadalsdk.bean.recorder.AudioRecordStopInfo
import com.yc.nadalsdk.bean.smart.SmartAudioDataInfo
import com.yc.nadalsdk.bean.smart.GlassesStoreInfo
import com.yc.nadalsdk.bean.smart.SmartImageDataInfo
import com.yc.nadalsdk.bean.smart.SmartRealAudioDataInfo
import com.yc.nadalsdk.bean.smart.VideoParametersInfo
import com.yc.nadalsdk.ble.open.DeviceModeJX
import com.yc.nadalsdk.constants.NotifyType
import com.yc.nadalsdk.constants.recorder.AudioRecordResult
import com.yc.nadalsdk.constants.smart.GlassesHeadsetRecordingState
import com.yc.nadalsdk.constants.smart.GlassesRecordDirection
import com.yc.nadalsdk.constants.smart.HeadsetBrightnessLevel
import com.yc.nadalsdk.constants.smart.HeadsetSoundMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext

class UteSdkDeviceControlGateway(
    private val bridge: UteSdkBridge,
    private val mediaDirectory: File? = null
) : DeviceControlGateway {
    private var aiRecorderSessionId: String? = null

    override fun events(): Flow<DeviceEvent> =
        bridge.notifies.mapNotNull { notify ->
            when (notify.type) {
                NotifyType.SMART_GLASSES_IMAGE_DATA_NOTIFY -> {
                    val data = notify.data as? SmartImageDataInfo
                    data?.persistSmartFile("glasses-photo", data.imaType.orEmpty(), "jpg")
                    DeviceEvent(
                        id = "image-${System.currentTimeMillis()}",
                        title = if (data?.crcSuccess == true) "图片上传完成" else "图片上传异常",
                        detail = "${data?.imaType.orEmpty()} ${data?.imaSize ?: 0} bytes",
                        level = if (data?.crcSuccess == true) DeviceEventLevel.Info else DeviceEventLevel.Warning,
                        timestamp = System.currentTimeMillis()
                    )
                }
                NotifyType.SMART_GLASSES_AUDIO_DATA_NOTIFY -> {
                    val data = notify.data as? SmartAudioDataInfo
                    data?.persistSmartFile("glasses-audio", data.audioType.orEmpty(), "opus")
                    DeviceEvent(
                        id = "audio-${System.currentTimeMillis()}",
                        title = if (data?.crcSuccess == true) "音频上传完成" else "音频上传异常",
                        detail = "${data?.audioType.orEmpty()} ${data?.audioSize ?: data?.data?.size ?: 0} bytes",
                        level = if (data?.crcSuccess == true) DeviceEventLevel.Info else DeviceEventLevel.Warning,
                        timestamp = System.currentTimeMillis()
                    )
                }
                NotifyType.SMART_GLASSES_AUDIO_DATA_REAL_NOTIFY -> {
                    val data = notify.data as? SmartRealAudioDataInfo
                    DeviceEvent(
                        id = "real-audio-${System.currentTimeMillis()}",
                        title = "实时音频片段",
                        detail = "${data?.audioType.orEmpty()} ${data?.data?.size ?: 0} bytes",
                        level = DeviceEventLevel.Info,
                        timestamp = System.currentTimeMillis()
                    )
                }
                NotifyType.SMART_WIFI_STATE_NOTIFY -> DeviceEvent(
                    id = "wifi-${System.currentTimeMillis()}",
                    title = "设备 Wi-Fi 状态变化",
                    detail = notify.data?.toString().orEmpty(),
                    level = DeviceEventLevel.Info,
                    timestamp = System.currentTimeMillis()
                )
                NotifyType.SMART_GLASSES_STORE_INFO_NOTIFY -> DeviceEvent(
                    id = "store-${System.currentTimeMillis()}",
                    title = "设备存储状态更新",
                    detail = (notify.data as? GlassesStoreInfo)?.toSummary().orEmpty().ifBlank { notify.data?.toString().orEmpty() },
                    level = DeviceEventLevel.Info,
                    timestamp = System.currentTimeMillis()
                )
                NotifyType.SMART_ISP_OTA_STATE_NOTIFY -> DeviceEvent(
                    id = "ota-isp-${System.currentTimeMillis()}",
                    title = "眼镜 ISP 固件升级状态更新",
                    detail = notify.data?.toString().orEmpty(),
                    level = DeviceEventLevel.Info,
                    timestamp = System.currentTimeMillis()
                )
                NotifyType.JL_OTA_SECOND_MODE -> DeviceEvent(
                    id = "ota-jl-second-${System.currentTimeMillis()}",
                    title = "杰理 OTA 第二阶段状态更新",
                    detail = notify.data?.toString().orEmpty(),
                    level = DeviceEventLevel.Warning,
                    timestamp = System.currentTimeMillis()
                )
                NotifyType.DEVICE_CHECK_UPGRADE_REQUEST -> DeviceEvent(
                    id = "ota-check-${System.currentTimeMillis()}",
                    title = "设备请求检查固件升级",
                    detail = notify.data?.toString().orEmpty(),
                    level = DeviceEventLevel.Warning,
                    timestamp = System.currentTimeMillis()
                )
                NotifyType.OTA_CONFIRM_DOWNLOAD -> DeviceEvent(
                    id = "ota-confirm-${System.currentTimeMillis()}",
                    title = "设备请求确认固件下载",
                    detail = notify.data?.toString().orEmpty(),
                    level = DeviceEventLevel.Warning,
                    timestamp = System.currentTimeMillis()
                )
                else -> null
            }
        }

    override suspend fun capabilities(device: DeviceStatus): DeviceCapabilities = withContext(Dispatchers.IO) {
        val selectedGlasses = device.type == DeviceType.Glasses
        val selectedHeadset = device.type == DeviceType.Headset
        val glassesInfo = if (selectedGlasses) {
            runCatching { bridge.connection.getGlassesInfo().data }.getOrNull()
        } else {
            null
        }
        val headsetInfo = runCatching { bridge.connection.getHeadsetInfo().data }.getOrNull()
        val hasGlassesInfo = glassesInfo != null
        val hasHeadsetInfo = headsetInfo != null
        val sdkControlConnected = runCatching { bridge.client.isConnected(device.id) }.getOrDefault(false) ||
            (bridge.client.isConnected && bridge.client.deviceAddress.equals(device.id, ignoreCase = true)) ||
            (selectedHeadset && bridge.client.isConnected)
        val hasSmartStore = glassesInfo?.glassesStoreInfo != null
        val supportsAiRecorder = DeviceModeJX.isHasFunction_4(DeviceModeJX.IS_SUPPORT_AI_RECORDER_MEETING_RECORDING)
        val supportsFileSpp = DeviceModeJX.isHasFunction_4(DeviceModeJX.IS_SUPPORT_FILE_TRANSFER_SPP)
        val smartCameraControl = sdkControlConnected && hasGlassesInfo
        val headsetCameraControl = selectedHeadset && (sdkControlConnected || device.online)
        val cameraControl = (selectedGlasses && smartCameraControl) || headsetCameraControl
        DeviceCapabilities(
            supportsGlasses = selectedGlasses && hasGlassesInfo,
            supportsEarphone = selectedHeadset && (sdkControlConnected || hasHeadsetInfo),
            supportsWifi = selectedGlasses && hasGlassesInfo,
            supportsFileTransfer = sdkControlConnected && (hasSmartStore || supportsFileSpp),
            supportsPhoto = cameraControl,
            supportsVideo = cameraControl,
            supportsAudioRecord = selectedHeadset && (supportsAiRecorder || cameraControl),
            supportsRealtimeAudio = selectedHeadset && (supportsAiRecorder || cameraControl)
        )
    }

    override suspend fun readWifi(): DeviceWifiState = withContext(Dispatchers.IO) {
        val wifi = runCatching { bridge.connection.smartGetDeviceWiFiInfo().data }.getOrNull()
        val state = runCatching { bridge.connection.smartGetDeviceWiFiStateInfo().data }.getOrNull()
        DeviceWifiState(
            enabled = (wifi?.state ?: state?.state ?: 0) != 0,
            ssid = wifi?.wiFiSSID.orEmpty(),
            passwordConfigured = !wifi?.wiFiPassword.isNullOrBlank(),
            connected = (state?.state ?: wifi?.state ?: 0) != 0
        )
    }

    override suspend fun configureWifi(enabled: Boolean, ssid: String, password: String): DeviceWifiState = withContext(Dispatchers.IO) {
        if (ssid.isNotBlank()) {
            val ssidResponse = bridge.connection.smartSetDeviceWiFiSSID(ssid)
            check(ssidResponse.isSuccess) { "device wifi ssid failed: ${ssidResponse.errorCode}" }
        }
        if (password.isNotBlank()) {
            val passwordResponse = bridge.connection.smartSetDeviceWiFiPassword(password)
            check(passwordResponse.isSuccess) { "device wifi password failed: ${passwordResponse.errorCode}" }
        }
        val switchResponse = bridge.connection.smartSetDeviceWiFiSwitch(enabled)
        check(switchResponse.isSuccess) { "device wifi switch failed: ${switchResponse.errorCode}" }
        delay(WifiStateSettleMillis)
        val next = readWifi()
        check(!enabled || next.enabled) { "device wifi switch accepted but stayed disabled: ${next.ssid}" }
        next
    }

    override suspend fun applySettings(device: DeviceStatus, settings: DeviceAdvancedSettings): DeviceAdvancedSettings = withContext(Dispatchers.IO) {
        when (device.type) {
            DeviceType.Glasses -> {
                bridge.connection.setVideoParameters(VideoParametersInfo(settings.videoWidth, settings.videoHeight, settings.videoFrameRate))
                bridge.connection.setGlassesRecordingDuration(settings.recordingDurationSeconds)
                bridge.connection.setGlassesRecordingDirection(
                    if (settings.verticalRecording) GlassesRecordDirection.VERTICAL_SCREEN else GlassesRecordDirection.HORIZONTAL_SCREEN
                )
            }
            DeviceType.Headset -> {
                bridge.connection.setVideoParameters(VideoParametersInfo(settings.videoWidth, settings.videoHeight, settings.videoFrameRate))
                bridge.connection.setHeadsetSoundMode(if (settings.enhancedSound) HeadsetSoundMode.ENHANCED_SOUND_MODE else HeadsetSoundMode.STANDARD_SOUND_MODE)
                bridge.connection.setHeadsetBrightnessLevel(settings.toHeadsetBrightnessLevel())
            }
            DeviceType.Recorder -> {
                bridge.connection.setVideoParameters(VideoParametersInfo(settings.videoWidth, settings.videoHeight, settings.videoFrameRate))
            }
            DeviceType.Sensor -> Unit
        }
        settings
    }

    override suspend fun startRealtimeAudioSync(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            bridge.client.openOrCloseNotify(true)
            val aiRecorderStarted = if (supportsAiRecorder()) {
                val response = bridge.connection.appStartAudioRecord()
                if (response.isAudioStartAccepted()) {
                    aiRecorderSessionId = response.data?.sessionId ?: sessionId
                    true
                } else {
                    false
                }
            } else {
                false
            }
            aiRecorderStarted ||
                bridge.connection.toggleHeadsetAudioRecording(GlassesHeadsetRecordingState.RECORDING_STATE_START).isSuccess
        }.getOrDefault(false)
    }

    override suspend fun stopRealtimeAudioSync(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val aiRecorderStopped = if (aiRecorderSessionId != null || supportsAiRecorder()) {
                val response = bridge.connection.appStopAudioRecord()
                if (response.isAudioStopAccepted()) {
                    aiRecorderSessionId = null
                    true
                } else {
                    false
                }
            } else {
                false
            }
            aiRecorderStopped ||
                bridge.connection.toggleHeadsetAudioRecording(GlassesHeadsetRecordingState.RECORDING_STATE_STOP).isSuccess
        }.getOrDefault(false)
    }

    override suspend fun notifyMediaSyncCompleted(): Boolean = withContext(Dispatchers.IO) {
        runCatching { bridge.connection.notifyMediaSyncCompleted().isSuccess }.getOrDefault(false)
    }

    private fun DeviceAdvancedSettings.toHeadsetBrightnessLevel(): Int =
        when (brightnessLevel.coerceIn(1, 3)) {
            1 -> HeadsetBrightnessLevel.BRIGHTNESS_LEVEL_1
            3 -> HeadsetBrightnessLevel.BRIGHTNESS_LEVEL_3
            else -> HeadsetBrightnessLevel.BRIGHTNESS_LEVEL_2
        }

    private suspend fun SmartImageDataInfo.persistSmartFile(prefix: String, type: String, fallbackExtension: String) {
        file?.persistSmartFile(prefix, type, fallbackExtension)
    }

    private suspend fun SmartAudioDataInfo.persistSmartFile(prefix: String, type: String, fallbackExtension: String) {
        file?.persistSmartFile(prefix, type, fallbackExtension)
    }

    private suspend fun File.persistSmartFile(prefix: String, type: String, fallbackExtension: String) = withContext(Dispatchers.IO) {
        val source = this@persistSmartFile
        val directory = mediaDirectory ?: return@withContext
        source.persistUniqueSmartMedia(directory, prefix, type, fallbackExtension)
    }

    private fun GlassesStoreInfo.toSummary(): String =
        "照片 $newTakenPictures/$totalPictures，音频 $newRecordAudio/$totalRecordAudio，视频 $newRecordVideo/$totalRecordVideo，剩余 ${freeSpace.toReadableGb()}/${maxSpace.toReadableGb()}"

    private fun Long.toReadableGb(): String =
        if (this <= 0L) "未知" else "%.1fGB".format(this / 1024f / 1024f / 1024f)

    private fun Response<AudioRecordInfo>.isAudioStartAccepted(): Boolean {
        val result = data?.result ?: return false
        return result == AudioRecordResult.RECORD_START_SUCCESS || result == AudioRecordResult.RECORD_ALREADY_IN_PROGRESS
    }

    private fun Response<AudioRecordStopInfo>.isAudioStopAccepted(): Boolean =
        data != null || isSuccess

    private fun supportsAiRecorder(): Boolean =
        DeviceModeJX.isHasFunction_4(DeviceModeJX.IS_SUPPORT_AI_RECORDER_MEETING_RECORDING)

    private companion object {
        const val WifiStateSettleMillis = 1_500L
    }
}
