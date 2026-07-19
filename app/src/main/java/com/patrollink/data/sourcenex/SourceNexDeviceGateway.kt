package com.patrollink.data.sourcenex

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.annotation.RequiresPermission
import com.patrollink.domain.DeviceCommand
import com.patrollink.domain.DeviceGateway
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.DeviceType
import com.patrollink.domain.ScannedDevice
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import net.sourcenex.aig.client.sdk.v2.TaskType
import net.sourcenex.aig.protocol.AigMessage
import net.sourcenex.aig.protocol.ReqCamCapture
import net.sourcenex.aig.protocol.ReqCamRecord
import net.sourcenex.aig.protocol.ReqCamRecordStop
import net.sourcenex.aig.protocol.ReqMicRecord
import net.sourcenex.aig.protocol.ReqMicRecordStop

class SourceNexDeviceGateway(
    private val bridge: SourceNexBridge,
    private val fallback: DeviceStatus
) : DeviceGateway {
    private val known = ConcurrentHashMap<String, BluetoothDevice>()
    @Volatile private var active = fallback

    @SuppressLint("MissingPermission")
    override fun scan(): Flow<List<ScannedDevice>> = callbackFlow {
        val collector = launch {
            combine(
                bridge.scanHelper.scannedDevicesFlow,
                bridge.scanHelper.pairedDevicesFlow,
                bridge.scanHelper.scannedRssiFlow
            ) { scanned, paired, rssi ->
                (scanned + paired).distinctBy { it.address }.mapNotNull { device ->
                    val name = runCatching { device.name }.getOrNull().orEmpty()
                    if (!isSourceNex(name)) return@mapNotNull null
                    known[device.address] = device
                    ScannedDevice(
                        id = deviceId(device.address),
                        name = name,
                        signalBars = signalBars(rssi[device.address]),
                        serviceUuid = ServiceMarker,
                        bonded = bridge.scanHelper.isBonded(device),
                        macAddress = device.address,
                        type = DeviceType.Glasses
                    )
                }
            }.collect(::trySend)
        }
        bridge.scanHelper.refreshPairedDevices()
        bridge.scanHelper.startScan(false)
        awaitClose {
            collector.cancel()
            bridge.scanHelper.stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun bind(deviceId: String): DeviceStatus {
        val address = addressOf(deviceId)
        val device = known[address] ?: bridge.scanHelper.getRemoteDevice(address)
        if (!bridge.scanHelper.isBonded(device)) {
            check(bridge.scanHelper.pairDevice(device)) { "无法发起系统蓝牙配对，请先连续短按右镜腿按键 3 次进入配对模式" }
            withTimeout(PairTimeoutMillis) {
                bridge.scanHelper.pairedDevicesFlow.filter { list -> list.any { it.address == address } }.first()
            }
        }
        return try {
            bridge.client.connect(device)
            withTimeout(ConnectTimeoutMillis) { bridge.client.isConnected.filter { it }.first() }
            delay(300)
            bridge.selectDevice(deviceId)
            currentStatus(deviceId, device)
        } catch (throwable: Throwable) {
            bridge.selectDevice(null)
            bridge.client.disconnect()
            throw throwable
        }
    }

    override suspend fun unbind(deviceId: String): DeviceStatus {
        val device = known[addressOf(deviceId)] ?: runCatching { bridge.scanHelper.getRemoteDevice(addressOf(deviceId)) }.getOrNull()
        bridge.client.disconnect()
        delay(300)
        if (device != null && bridge.scanHelper.isBonded(device)) {
            bridge.scanHelper.unpairDevice(device)
        }
        bridge.selectDevice(null)
        active = active.copy(id = deviceId, online = false, isRecording = false, isTalking = false)
        return active
    }

    override suspend fun sendCommand(deviceId: String, command: DeviceCommand): DeviceStatus {
        check(bridge.client.isConnected.value) { "SourceNex 眼镜控制通道未连接" }
        val (message, expected) = when (command) {
            DeviceCommand.TakePhoto -> AigMessage.newBuilder()
                .setReqCamCapture(ReqCamCapture.newBuilder().setSilent(false).setTemporary(false).build()).build() to
                AigMessage.MessageCase.RES_CAM_CAPTURE
            DeviceCommand.StartRecord -> AigMessage.newBuilder()
                .setReqCamRecord(ReqCamRecord.newBuilder().setDuration(24 * 60 * 60).setSilent(false).build()).build() to
                AigMessage.MessageCase.RES_CAM_RECORD
            DeviceCommand.StopRecord -> AigMessage.newBuilder()
                .setReqCamRecordStop(ReqCamRecordStop.newBuilder().setSilent(false).build()).build() to
                AigMessage.MessageCase.RES_CAM_RECORD_STOP
            DeviceCommand.StartTalk -> AigMessage.newBuilder()
                .setReqMicRecord(ReqMicRecord.newBuilder().setDuration(24 * 60 * 60).setForce(false).setSilent(false).build()).build() to
                AigMessage.MessageCase.RES_MIC_RECORD
            DeviceCommand.StopTalk -> AigMessage.newBuilder()
                .setReqMicRecordStop(ReqMicRecordStop.newBuilder().setSilent(false).build()).build() to
                AigMessage.MessageCase.RES_MIC_RECORD_STOP
        }
        // Several current firmwares execute capture/stop successfully but omit the documented RES_* ACK.
        // The Hmd task state and subsequent MediaFile event remain the source of truth.
        bridge.requestOrNull(message, expected)
        val device = known[addressOf(deviceId)] ?: bridge.client.currentBluetoothDevice.value
        val status = currentStatus(deviceId, device).let {
            when (command) {
                DeviceCommand.StartRecord -> it.copy(isRecording = true)
                DeviceCommand.StopRecord -> it.copy(isRecording = false)
                DeviceCommand.StartTalk -> it.copy(isTalking = true)
                DeviceCommand.StopTalk -> it.copy(isTalking = false)
                DeviceCommand.TakePhoto -> it
            }
        }
        active = status
        return status
    }

    @SuppressLint("MissingPermission")
    private fun currentStatus(deviceId: String, device: BluetoothDevice?): DeviceStatus {
        val hmd = bridge.client.hmd.value
        val tasks = hmd?.tasks.orEmpty()
        return DeviceStatus(
            id = deviceId,
            name = hmd?.name ?: runCatching { device?.name }.getOrNull() ?: "SourceNex 智能眼镜",
            online = bridge.client.isConnected.value,
            battery = hmd?.batteryLevel?.toInt()?.coerceIn(0, 100) ?: 0,
            signalBars = signalBars(hmd?.rssi),
            onlineDuration = "已连接",
            storageUsedGb = 0f,
            storageTotalGb = 0f,
            firmware = hmd?.firmwareVersion ?: hmd?.version.orEmpty(),
            isRecording = TaskType.VIDEO_RECORD in tasks,
            isTalking = TaskType.AUDIO_RECORD in tasks,
            cloudConnected = false,
            type = DeviceType.Glasses,
            batteryKnown = hmd?.batteryLevel != null,
            storageKnown = false
        ).also { active = it }
    }

    companion object {
        const val IdPrefix = "sourcenex:"
        const val ServiceMarker = "SOURCENEX_AIG_V2"
        private const val PairTimeoutMillis = 30_000L
        private const val ConnectTimeoutMillis = 20_000L
        fun deviceId(address: String) = "$IdPrefix$address"
        fun addressOf(id: String) = id.removePrefix(IdPrefix)
        fun isSourceNex(name: String) = name.startsWith("SourceNex-", true) || name.startsWith("Aig-Glass", true) || name.startsWith("Aig-", true)
        private fun signalBars(rssi: Int?): Int = when {
            rssi == null -> 3
            rssi >= -55 -> 4
            rssi >= -67 -> 3
            rssi >= -80 -> 2
            else -> 1
        }
    }
}
