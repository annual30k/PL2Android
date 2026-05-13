package com.patrollink.data.ute

import com.patrollink.domain.StreamMode
import com.patrollink.domain.StreamRelayGateway
import com.patrollink.domain.StreamRelayState
import com.yc.nadalsdk.bean.smart.VideoParametersInfo
import com.yc.nadalsdk.constants.smart.GlassesHeadsetRecordingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class UteSdkStreamRelayGateway(
    private val bridge: UteSdkBridge
) : StreamRelayGateway {
    private val state = MutableStateFlow(StreamRelayState.Idle)

    override fun state(): Flow<StreamRelayState> = state.asStateFlow()

    override suspend fun start(deviceId: String, mode: StreamMode) {
        require(bridge.client.isConnected) { "UTE device is not connected" }
        state.value = StreamRelayState.Connecting
        withContext(Dispatchers.IO) {
            val parameters = when (mode) {
                StreamMode.LowLatency -> VideoParametersInfo(240, 0, 16)
                StreamMode.Balanced -> VideoParametersInfo(480, 0, 20)
                StreamMode.EvidenceQuality -> VideoParametersInfo(720, 0, 24)
            }
            bridge.connection.setVideoParameters(parameters)
            bridge.connection.toggleGlassesVideoRecording(GlassesHeadsetRecordingState.RECORDING_STATE_START)
        }
        state.value = StreamRelayState.Relaying
    }

    override suspend fun stop() {
        withContext(Dispatchers.IO) {
            runCatching {
                bridge.connection.toggleGlassesVideoRecording(GlassesHeadsetRecordingState.RECORDING_STATE_STOP)
            }
        }
        state.value = StreamRelayState.Idle
    }
}
