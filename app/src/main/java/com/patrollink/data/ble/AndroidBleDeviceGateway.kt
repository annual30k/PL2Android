package com.patrollink.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import com.patrollink.domain.DeviceCommand
import com.patrollink.domain.DeviceGateway
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.DeviceType
import com.patrollink.domain.ScannedDevice
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class AndroidBleDeviceGateway(
    context: Context,
    private val fallbackStatus: DeviceStatus,
    private val profile: BleGattProfile = BleGattProfile(null, null, null)
) : DeviceGateway {
    private val context = context.applicationContext
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val scanner get() = bluetoothManager.adapter.bluetoothLeScanner
    private val status = MutableStateFlow(fallbackStatus)
    private val mutex = Mutex()
    private var gatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var statusCharacteristic: BluetoothGattCharacteristic? = null
    private var connectionResult: CompletableDeferred<DeviceStatus>? = null
    private var reconnectAttempts = 0
    private var boundDevice: BluetoothDevice? = null

    @SuppressLint("MissingPermission")
    override fun scan(): Flow<List<ScannedDevice>> = callbackFlow {
        val devices = linkedMapOf<String, ScannedDevice>()
        val filters = profile.serviceUuid?.let { listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(it)).build()) }.orEmpty()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val id = device.address ?: return
                val serviceUuid = result.scanRecord?.serviceUuids?.firstOrNull()?.uuid
                if (profile.serviceUuid != null && serviceUuid != profile.serviceUuid) return
                devices[id] = ScannedDevice(
                    id = id,
                    name = device.name ?: "Patrol headset",
                    signalBars = result.rssi.toSignalBars(),
                    serviceUuid = serviceUuid?.toString().orEmpty(),
                    bonded = device.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED,
                    macAddress = id,
                    type = (device.name ?: "").toPatrolDeviceType()
                )
                trySend(devices.values.toList())
            }
        }
        scanner?.startScan(filters, settings, callback)
        awaitClose { scanner?.stopScan(callback) }
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun bind(deviceId: String): DeviceStatus = mutex.withLock {
        if (!profile.readyForGatt) {
            return fallbackStatus.copy(id = deviceId, online = true)
        }
        val device = bluetoothManager.adapter.getRemoteDevice(deviceId)
        boundDevice = device
        if (device.bondState == BluetoothDevice.BOND_NONE) {
            device.createBond()
        }
        closeGatt()
        reconnectAttempts = 0
        return connectGatt(device, deviceId) ?: fallbackStatus.copy(id = deviceId, online = false)
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun unbind(deviceId: String): DeviceStatus? = mutex.withLock {
        closeGatt()
        boundDevice = null
        commandCharacteristic = null
        statusCharacteristic = null
        val next = fallbackStatus.copy(id = deviceId, online = false, isRecording = false, isTalking = false)
        status.value = next
        next
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun sendCommand(deviceId: String, command: DeviceCommand): DeviceStatus = mutex.withLock {
        val payload = BleCommandCodec.encode(command)
        check(payload.isNotEmpty()) { "empty BLE command" }
        commandCharacteristic?.let { characteristic ->
            if (Build.VERSION.SDK_INT >= 33) {
                gatt?.writeCharacteristic(characteristic, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = payload
                @Suppress("DEPRECATION")
                gatt?.writeCharacteristic(characteristic)
            }
        }
        val optimistic = when (command) {
            DeviceCommand.StartRecord -> fallbackStatus.copy(id = deviceId, isRecording = true)
            DeviceCommand.StopRecord -> fallbackStatus.copy(id = deviceId, isRecording = false)
            DeviceCommand.StartTalk -> fallbackStatus.copy(id = deviceId, isTalking = true)
            DeviceCommand.StopTalk -> fallbackStatus.copy(id = deviceId, isTalking = false)
            DeviceCommand.TakePhoto -> fallbackStatus.copy(id = deviceId)
        }
        status.update { current -> optimistic.copy(battery = current.battery, signalBars = current.signalBars, firmware = current.firmware, online = true) }
        return status.value
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectGatt(device: BluetoothDevice, deviceId: String): DeviceStatus? {
        val result = CompletableDeferred<DeviceStatus>()
        connectionResult = result
        gatt = if (Build.VERSION.SDK_INT >= 23) {
            device.connectGatt(context, false, callback(deviceId), BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, callback(deviceId))
        }
        return withTimeoutOrNull(12_000) { result.await() }
    }

    @SuppressLint("MissingPermission")
    private fun callback(deviceId: String) = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, statusCode: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                reconnectAttempts = 0
                gatt.discoverServices()
                return
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                status.update { it.copy(id = deviceId, online = false) }
                commandCharacteristic = null
                statusCharacteristic = null
                connectionResult?.complete(status.value)
                maybeReconnect()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, statusCode: Int) {
            val service = profile.serviceUuid?.let(gatt::getService)
            commandCharacteristic = profile.commandCharacteristicUuid?.let { service?.getCharacteristic(it) }
            statusCharacteristic = profile.statusCharacteristicUuid?.let { service?.getCharacteristic(it) }
            statusCharacteristic?.let { subscribe(gatt, it) }
            val ready = fallbackStatus.copy(id = deviceId, online = commandCharacteristic != null)
            status.value = ready
            connectionResult?.complete(ready)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            handleNotify(characteristic.value)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleNotify(value)
        }
    }

    @SuppressLint("MissingPermission")
    private fun subscribe(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(BleGattProfile.ClientCharacteristicConfig) ?: return
        if (Build.VERSION.SDK_INT >= 33) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun handleNotify(payload: ByteArray) {
        status.update { BleStatusParser.parse(payload, it) }
    }

    @SuppressLint("MissingPermission")
    private fun maybeReconnect() {
        val device = boundDevice ?: return
        if (reconnectAttempts >= 3 || !profile.readyForGatt) return
        reconnectAttempts += 1
        closeGatt()
        gatt = if (Build.VERSION.SDK_INT >= 23) {
            device.connectGatt(context, false, callback(device.address), BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, callback(device.address))
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
    }

    private fun Int.toSignalBars(): Int = when {
        this >= -55 -> 5
        this >= -67 -> 4
        this >= -75 -> 3
        this >= -85 -> 2
        else -> 1
    }

    private fun String.toPatrolDeviceType(): DeviceType {
        val normalized = uppercase()
        return when {
            "GLASS" in normalized || "G1" in normalized || "眼镜" in normalized -> DeviceType.Glasses
            "RECORDER" in normalized || "A5" in normalized || "记录" in normalized -> DeviceType.Recorder
            "SENSOR" in normalized || "S9" in normalized || "传感" in normalized -> DeviceType.Sensor
            else -> DeviceType.Headset
        }
    }
}
