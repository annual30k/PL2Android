package com.patrollink.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import androidx.annotation.RequiresPermission
import com.patrollink.domain.DeviceCommand
import com.patrollink.domain.DeviceGateway
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.DeviceType
import com.patrollink.domain.ScannedDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AndroidBleDeviceGateway(
    context: Context,
    private val fallbackStatus: DeviceStatus
) : DeviceGateway {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val scanner get() = bluetoothManager.adapter.bluetoothLeScanner

    @SuppressLint("MissingPermission")
    override fun scan(): Flow<List<ScannedDevice>> = callbackFlow {
        val devices = linkedMapOf<String, ScannedDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val id = device.address ?: return
                devices[id] = ScannedDevice(
                    id = id,
                    name = device.name ?: "Patrol headset",
                    signalBars = result.rssi.toSignalBars(),
                    serviceUuid = result.scanRecord?.serviceUuids?.firstOrNull()?.uuid?.toString().orEmpty(),
                    bonded = device.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED,
                    macAddress = id,
                    type = DeviceType.Headset
                )
                trySend(devices.values.toList())
            }
        }
        scanner?.startScan(callback)
        awaitClose { scanner?.stopScan(callback) }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun bind(deviceId: String): DeviceStatus {
        return fallbackStatus.copy(id = deviceId, online = true)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun sendCommand(deviceId: String, command: DeviceCommand): DeviceStatus {
        val payload = BleCommandCodec.encode(command)
        check(payload.isNotEmpty()) { "empty BLE command" }
        return when (command) {
            DeviceCommand.StartRecord -> fallbackStatus.copy(id = deviceId, isRecording = true)
            DeviceCommand.StopRecord -> fallbackStatus.copy(id = deviceId, isRecording = false)
            DeviceCommand.StartTalk -> fallbackStatus.copy(id = deviceId, isTalking = true)
            DeviceCommand.StopTalk -> fallbackStatus.copy(id = deviceId, isTalking = false)
            DeviceCommand.TakePhoto -> fallbackStatus.copy(id = deviceId)
        }
    }

    private fun Int.toSignalBars(): Int = when {
        this >= -55 -> 5
        this >= -67 -> 4
        this >= -75 -> 3
        this >= -85 -> 2
        else -> 1
    }
}
