package com.patrollink.data.ute

import com.patrollink.domain.DeviceAdvancedSettings
import com.patrollink.domain.DeviceCapabilities
import com.patrollink.domain.DeviceControlGateway
import com.patrollink.domain.DeviceEvent
import com.patrollink.domain.DeviceEventLevel
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.DeviceType
import com.patrollink.domain.DeviceWifiState
import com.yc.nadalsdk.bean.smart.SmartAudioDataInfo
import com.yc.nadalsdk.bean.smart.SmartImageDataInfo
import com.yc.nadalsdk.bean.smart.SmartRealAudioDataInfo
import com.yc.nadalsdk.bean.smart.VideoParametersInfo
import com.yc.nadalsdk.constants.smart.GlassesHeadsetRecordingState
import com.yc.nadalsdk.constants.NotifyType
import com.yc.nadalsdk.constants.smart.GlassesRecordDirection
import com.yc.nadalsdk.constants.smart.HeadsetBrightnessLevel
import com.yc.nadalsdk.constants.smart.HeadsetSoundMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext

class UteSdkDeviceControlGateway(
    private val bridge: UteSdkBridge
) : DeviceControlGateway {
    override fun events(): Flow<DeviceEvent> =
        bridge.notifies.mapNotNull { notify ->
            when (notify.type) {
                NotifyType.SMART_GLASSES_IMAGE_DATA_NOTIFY -> {
                    val data = notify.data as? SmartImageDataInfo
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
                    detail = notify.data?.toString().orEmpty(),
                    level = DeviceEventLevel.Info,
                    timestamp = System.currentTimeMillis()
                )
                else -> null
            }
        }

    override suspend fun capabilities(device: DeviceStatus): DeviceCapabilities = withContext(Dispatchers.IO) {
        val hasGlassesInfo = runCatching { bridge.connection.getGlassesInfo().isSuccess }.getOrDefault(false)
        val hasHeadsetInfo = runCatching { bridge.connection.getHeadsetInfo().isSuccess }.getOrDefault(false)
        val selectedGlasses = device.type == DeviceType.Glasses
        val selectedHeadset = device.type == DeviceType.Headset
        val selectedSdkDevice = selectedGlasses || selectedHeadset
        DeviceCapabilities(
            supportsGlasses = selectedGlasses && (hasGlassesInfo || selectedGlasses),
            supportsEarphone = selectedHeadset && (hasHeadsetInfo || selectedHeadset),
            supportsWifi = selectedGlasses && (hasGlassesInfo || selectedGlasses),
            supportsFileTransfer = selectedSdkDevice,
            supportsPhoto = selectedSdkDevice,
            supportsVideo = selectedSdkDevice,
            supportsAudioRecord = selectedHeadset && (hasHeadsetInfo || selectedHeadset),
            supportsRealtimeAudio = selectedHeadset && (hasHeadsetInfo || selectedHeadset)
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
        bridge.connection.smartSetDeviceWiFiSwitch(enabled)
        if (ssid.isNotBlank()) bridge.connection.smartSetDeviceWiFiSSID(ssid)
        if (password.isNotBlank()) bridge.connection.smartSetDeviceWiFiPassword(password)
        readWifi()
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
        runCatching { bridge.connection.toggleHeadsetAudioRecording(GlassesHeadsetRecordingState.RECORDING_STATE_START).isSuccess }.getOrDefault(false)
    }

    override suspend fun stopRealtimeAudioSync(): Boolean = withContext(Dispatchers.IO) {
        runCatching { bridge.connection.toggleHeadsetAudioRecording(GlassesHeadsetRecordingState.RECORDING_STATE_STOP).isSuccess }.getOrDefault(false)
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
}
