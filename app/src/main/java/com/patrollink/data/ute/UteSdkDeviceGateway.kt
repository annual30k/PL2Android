package com.patrollink.data.ute

import android.Manifest
import android.annotation.SuppressLint
import androidx.annotation.RequiresPermission
import com.patrollink.domain.DeviceCommand
import com.patrollink.domain.DeviceGateway
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.DeviceType
import com.patrollink.domain.ScannedDevice
import com.yc.nadalsdk.bean.DeviceInfoRequest
import com.yc.nadalsdk.ble.open.UteBleConnection
import com.yc.nadalsdk.constants.smart.GlassesHeadsetRecordingState
import com.yc.nadalsdk.listener.BleConnectStateListener
import com.yc.nadalsdk.scan.UteScanCallback
import com.yc.nadalsdk.scan.UteScanDevice
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class UteSdkDeviceGateway(
    private val bridge: UteSdkBridge,
    private val fallbackStatus: DeviceStatus
) : DeviceGateway {
    private val scanned = ConcurrentHashMap<String, ScannedDevice>()
    private val mutex = Mutex()
    private var activeDevice: DeviceStatus = fallbackStatus
    private var connectResult: CompletableDeferred<Boolean>? = null

    @SuppressLint("MissingPermission")
    override fun scan(): Flow<List<ScannedDevice>> = callbackFlow {
        if (!bridge.hasBluetoothScanPermission() || !bridge.client.isBluetoothEnable) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val callback = object : UteScanCallback {
            override fun onScanning(device: UteScanDevice) {
                val mapped = device.toScannedDevice() ?: return
                scanned[mapped.id] = mapped
                trySend(scanned.values.sortedByDescending { it.signalBars })
            }

            override fun onScanComplete(scanDeviceList: MutableList<UteScanDevice>) {
                scanDeviceList.mapNotNull { it.toScannedDevice() }.forEach { scanned[it.id] = it }
                trySend(scanned.values.sortedByDescending { it.signalBars })
            }

            override fun onScanFailed(errorCode: Int) {
                trySend(scanned.values.sortedByDescending { it.signalBars })
            }
        }
        runCatching { bridge.client.scanDevice(callback, ScanMillis) }
            .onFailure {
                trySend(emptyList())
                close(it)
            }
        awaitClose { bridge.client.cancelScan() }
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun bind(deviceId: String): DeviceStatus = mutex.withLock {
        val discovered = scanned[deviceId]
        if (!bridge.hasBluetoothConnectPermission()) {
            return fallbackStatus.copy(id = deviceId, online = false)
        }
        val connection = bridge.connection
        connection.setConnectStateListener(connectStateListener())
        connectResult = CompletableDeferred()
        bridge.connect(deviceId)
        val connected = withTimeoutOrNull(ConnectTimeoutMillis) { connectResult?.await() } == true || bridge.client.isConnected
        activeDevice = readStatus(deviceId, discovered, connected)
        activeDevice
    }

    override suspend fun unbind(deviceId: String): DeviceStatus? = mutex.withLock {
        withContext(Dispatchers.IO) { runCatching { bridge.disconnect() } }
        connectResult = null
        activeDevice = fallbackStatus.copy(id = deviceId, online = false, isRecording = false, isTalking = false)
        activeDevice
    }

    override suspend fun sendCommand(deviceId: String, command: DeviceCommand): DeviceStatus = mutex.withLock {
        val connection = bridge.connection
        withContext(Dispatchers.IO) {
            when (command) {
                DeviceCommand.TakePhoto -> connection.triggerGlassesPhotoCapture(null)
                DeviceCommand.StartRecord -> connection.toggleGlassesVideoRecording(GlassesHeadsetRecordingState.RECORDING_STATE_START)
                DeviceCommand.StopRecord -> connection.toggleGlassesVideoRecording(GlassesHeadsetRecordingState.RECORDING_STATE_STOP)
                DeviceCommand.StartTalk -> connection.toggleHeadsetAudioRecording(GlassesHeadsetRecordingState.RECORDING_STATE_START)
                DeviceCommand.StopTalk -> connection.toggleHeadsetAudioRecording(GlassesHeadsetRecordingState.RECORDING_STATE_STOP)
            }
        }
        val next = when (command) {
            DeviceCommand.StartRecord -> activeDevice.copy(id = deviceId, isRecording = true, online = true)
            DeviceCommand.StopRecord -> activeDevice.copy(id = deviceId, isRecording = false, online = true)
            DeviceCommand.StartTalk -> activeDevice.copy(id = deviceId, isTalking = true, online = true)
            DeviceCommand.StopTalk -> activeDevice.copy(id = deviceId, isTalking = false, online = true)
            DeviceCommand.TakePhoto -> activeDevice.copy(id = deviceId, online = true)
        }
        activeDevice = next
        next
    }

    private fun connectStateListener() = object : BleConnectStateListener {
        override fun onConnecteStateChange(status: Int) {
            when (status) {
                BleConnectStateListener.STATE_CONNECTED -> connectResult?.complete(true)
                BleConnectStateListener.STATE_DISCONNECTED -> connectResult?.complete(false)
            }
        }
    }

    private suspend fun readStatus(
        deviceId: String,
        discovered: ScannedDevice?,
        connected: Boolean
    ): DeviceStatus = withContext(Dispatchers.IO) {
        val connection = bridge.connection
        val battery = runCatching { connection.smartGetBatteryInfo().data?.percents }
            .getOrNull()
            ?: runCatching { connection.getBatteryInfo().data?.percents }.getOrNull()
            ?: fallbackStatus.battery
        val smartInfo = runCatching { connection.smartGetDeviceInfo().data }.getOrNull()
        val deviceInfo = runCatching {
            connection.getDeviceInfo(DeviceInfoRequest().apply {
                address = true
                deviceBtModel = true
                deviceVersionType = true
            }).data
        }.getOrNull()
        val glassesInfo = runCatching { connection.getGlassesInfo().data }.getOrNull()
        val store = glassesInfo?.glassesStoreInfo
        val totalGb = store?.maxSpace?.bytesToGb()?.takeIf { it > 0f } ?: fallbackStatus.storageTotalGb
        val freeGb = store?.freeSpace?.bytesToGb()?.coerceAtMost(totalGb) ?: (totalGb - fallbackStatus.storageUsedGb)
        val type = discovered?.type ?: smartInfo?.let { DeviceType.Glasses } ?: deviceInfo?.let { DeviceType.Headset } ?: fallbackStatus.type

        fallbackStatus.copy(
            id = deviceId,
            name = discovered?.name ?: bridge.client.deviceName.orEmpty().ifBlank { fallbackStatus.name },
            online = connected,
            battery = battery.coerceIn(0, 100),
            signalBars = discovered?.signalBars ?: fallbackStatus.signalBars,
            onlineDuration = if (connected) "刚刚连接" else "连接失败",
            storageUsedGb = (totalGb - freeGb).coerceAtLeast(0f),
            storageTotalGb = totalGb,
            firmware = smartInfo?.glassesVersion
                ?: smartInfo?.headSetVersion
                ?: deviceInfo?.deviceVersion
                ?: fallbackStatus.firmware,
            isRecording = activeDevice.isRecording,
            isTalking = activeDevice.isTalking,
            cloudConnected = activeDevice.cloudConnected,
            type = type
        )
    }

    private fun UteScanDevice.toScannedDevice(): ScannedDevice? {
        val bluetoothDevice = device ?: return null
        val name = runCatching { bluetoothDevice.name }.getOrNull().orEmpty()
        if (name.isBlank()) return null
        if (!isSupportedUteDevice(name, scanRecord)) return null
        val id = bluetoothDevice.address ?: return null
        return ScannedDevice(
            id = id,
            name = name,
            signalBars = rssi.toSignalBars(),
            serviceUuid = "",
            bonded = bluetoothDevice.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED,
            macAddress = id,
            type = name.toPatrolDeviceType(scanRecord)
        )
    }

    private fun isSupportedUteDevice(name: String, scanRecord: ByteArray?): Boolean {
        val hex = scanRecord?.joinToString(" ") { "%02X".format(it) }.orEmpty()
        return hex.contains("55 55") ||
            hex.contains("3A 55") ||
            name.startsWith("AT", ignoreCase = true) ||
            name.startsWith("ABA002", ignoreCase = true) ||
            name.startsWith("Tic", ignoreCase = true)
    }

    private fun String.toPatrolDeviceType(scanRecord: ByteArray?): DeviceType {
        val normalized = uppercase()
        val hex = scanRecord?.joinToString(" ") { "%02X".format(it) }.orEmpty()
        return when {
            "ABA002" in normalized || "GLASS" in normalized || "眼镜" in normalized || hex.contains("3A 55") -> DeviceType.Glasses
            "RECORDER" in normalized || "AI" in normalized && "REC" in normalized -> DeviceType.Recorder
            else -> DeviceType.Headset
        }
    }

    private fun Int.toSignalBars(): Int = when {
        this >= -55 -> 5
        this >= -67 -> 4
        this >= -75 -> 3
        this >= -85 -> 2
        else -> 1
    }

    private fun Long.bytesToGb(): Float = this / 1024f / 1024f / 1024f

    private companion object {
        const val ScanMillis = 10_000L
        const val ConnectTimeoutMillis = 15_000L
    }
}
