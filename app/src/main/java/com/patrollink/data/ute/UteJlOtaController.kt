package com.patrollink.data.ute

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import com.jieli.jl_bt_ota.constant.StateCode
import com.jieli.jl_bt_ota.impl.BluetoothOTAManager
import com.jieli.jl_bt_ota.interfaces.BtEventCallback
import com.jieli.jl_bt_ota.interfaces.IUpgradeCallback
import com.jieli.jl_bt_ota.model.BluetoothOTAConfigure
import com.jieli.jl_bt_ota.model.base.BaseError
import com.patrollink.domain.FirmwareUpgradeState
import com.yc.nadalsdk.listener.GattCallbackListener
import com.yc.nadalsdk.utils.open.SPUtil
import java.io.File
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class UteJlOtaController(
    private val bridge: UteSdkBridge
) {
    fun install(packageFile: File): Flow<FirmwareUpgradeState> = callbackFlow {
        require(packageFile.exists() && packageFile.isFile) { "JL OTA firmware file missing" }
        val sendLoop = JlOtaSendLoop(bridge)
        val manager = PatrolJlOtaManager(bridge, sendLoop)
        var jlSecondStageAddress: String? = null
        val bluetoothCallback = object : BtEventCallback() {
            override fun onConnection(device: BluetoothDevice?, status: Int) {
                if (status == StateCode.CONNECTION_OK && !manager.isOTA) {
                    manager.bluetoothOption.setFirmwareFilePath(packageFile.absolutePath)
                    sendLoop.start()
                    manager.startOTA(object : IUpgradeCallback {
                        override fun onStartOTA() {
                            trySend(FirmwareUpgradeState("JL_OTA_STARTED", 0.4f))
                        }

                        override fun onNeedReconnect(addr: String?, isNewReconnectWay: Boolean) {
                            val fallbackAddress = device?.address ?: bridge.client.deviceAddress
                            val reconnectAddress = addr?.takeIf { it.isNotBlank() }
                                ?: fallbackAddress.incrementBluetoothAddress()
                            jlSecondStageAddress = reconnectAddress
                            reconnectAddress?.let {
                                manager.setReconnectAddress(it)
                                launch { reconnectJlSecondStage(it) }
                            }
                            trySend(FirmwareUpgradeState("JL_OTA_RECONNECT_REQUIRED", 0.62f))
                        }

                        override fun onProgress(type: Int, progress: Float) {
                            val normalized = if (type == 0) {
                                0.4f + progress.coerceIn(0f, 100f) / 100f * 0.25f
                            } else {
                                0.65f + progress.coerceIn(0f, 100f) / 100f * 0.33f
                            }
                            trySend(FirmwareUpgradeState("JL_OTA_PROGRESS", normalized.coerceIn(0f, 0.98f)))
                        }

                        override fun onStopOTA() {
                            trySend(FirmwareUpgradeState("JL_OTA_COMPLETED", 1f))
                            close()
                        }

                        override fun onCancelOTA() {
                            trySend(FirmwareUpgradeState("JL_OTA_CANCELLED", 0f, "JL_OTA_CANCELLED", "JL OTA cancelled"))
                            close()
                        }

                        override fun onError(error: BaseError?) {
                            trySend(
                                FirmwareUpgradeState(
                                    status = "JL_OTA_FAILED",
                                    progress = 0f,
                                    errorCode = error?.code?.toString().orEmpty(),
                                    errorMessage = error?.message.orEmpty().ifBlank { "JL OTA failed" }
                                )
                            )
                            close(error?.let { IllegalStateException(it.message) })
                        }
                    })
                }
            }
        }
        val gattListener = object : GattCallbackListener {
            override fun onConnectionStateChange(status: Int) {
                val otaStatus = when (status) {
                    BluetoothProfile.STATE_CONNECTED -> StateCode.CONNECTION_OK
                    BluetoothProfile.STATE_CONNECTING -> StateCode.CONNECTION_CONNECTING
                    else -> StateCode.CONNECTION_DISCONNECT
                }
                manager.onBtDeviceConnection(bridge.client.bluetoothDevice, otaStatus)
                if (otaStatus == StateCode.CONNECTION_DISCONNECT) {
                    jlSecondStageAddress?.let { address ->
                        trySend(FirmwareUpgradeState("JL_OTA_RECONNECTING", 0.64f))
                        launch {
                            delay(JlReconnectDelayMillis)
                            reconnectJlSecondStage(address)
                        }
                    }
                }
            }

            override fun onCharacteristicChanged(bluetoothGatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
                val value = characteristic?.value ?: return
                if (characteristic.uuid == OnlyReadUuid) {
                    manager.onReceiveDeviceData(bridge.client.bluetoothDevice, value)
                }
            }

            override fun onCharacteristicWrite(bluetoothGatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                if (characteristic?.uuid == OnlyWriteUuid) {
                    sendLoop.wakeup()
                }
            }
        }

        bridge.connection.setOnGattCallbackListener(gattListener)
        bridge.connection.isJLUpgrade(true)
        manager.configure(
            BluetoothOTAConfigure.createDefault()
                .setPriority(BluetoothOTAConfigure.PREFER_BLE)
                .setUseAuthDevice(false)
                .setBleIntervalMs(500)
                .setTimeoutMs(3000)
                .setMtu(500)
                .setNeedChangeMtu(false)
                .setUseReconnect(false)
        )
        manager.registerBluetoothCallback(bluetoothCallback)
        trySend(FirmwareUpgradeState("JL_OTA_READY", 0.35f))
        manager.onBtDeviceConnection(bridge.client.bluetoothDevice, StateCode.CONNECTION_OK)

        awaitClose {
            sendLoop.stop()
            manager.unregisterBluetoothCallback(bluetoothCallback)
            manager.release()
            runCatching { bridge.connection.isJLUpgrade(false) }
        }
    }

    private fun reconnectJlSecondStage(address: String) {
        bridge.client.setSupportUserIdPair(false)
        bridge.client.connect(address)
    }

    private class PatrolJlOtaManager(
        private val bridge: UteSdkBridge,
        private val sendLoop: JlOtaSendLoop
    ) : BluetoothOTAManager(bridge.appContext) {
        override fun getConnectedDevice(): BluetoothDevice? = bridge.client.bluetoothDevice

        override fun getConnectedBluetoothGatt(): BluetoothGatt? = bridge.client.bluetoothGatt

        override fun connectBluetoothDevice(device: BluetoothDevice?) {
            device?.address?.let { bridge.client.connect(it) }
        }

        override fun disconnectBluetoothDevice(device: BluetoothDevice?) {
            bridge.client.disconnect()
        }

        override fun sendDataToDevice(device: BluetoothDevice?, data: ByteArray?): Boolean {
            val bytes = data ?: return false
            return sendLoop.enqueue(bytes)
        }

        override fun release() {
            sendLoop.stop()
            super.release()
        }
    }

    private class JlOtaSendLoop(
        private val bridge: UteSdkBridge
    ) {
        private val queue = LinkedBlockingQueue<ByteArray>()
        private val lock = Object()
        @Volatile private var running = false
        private var worker: Thread? = null

        fun enqueue(data: ByteArray): Boolean {
            val chunkSize = resolveChunkSize()
            data.asList().chunked(chunkSize).forEach { chunk ->
                queue.offer(chunk.toByteArray())
            }
            wakeup()
            return true
        }

        fun start() {
            if (running) return
            running = true
            worker = Thread {
                while (running) {
                    val data = try {
                        queue.take()
                    } catch (_: InterruptedException) {
                        break
                    }
                    runCatching { bridge.connection.sendDataToJlDevice(data) }
                    synchronized(lock) {
                        runCatching { lock.wait(WriteTimeoutMillis) }
                    }
                }
            }.apply {
                name = "patrollink-jl-ota-send"
                isDaemon = true
                start()
            }
        }

        fun wakeup() {
            synchronized(lock) {
                lock.notifyAll()
            }
        }

        fun stop() {
            running = false
            worker?.interrupt()
            queue.clear()
            wakeup()
        }

        private fun resolveChunkSize(): Int {
            SPUtil.initialize(bridge.appContext)
            val mtu = runCatching { SPUtil.getInstance().phoneMtu }.getOrDefault(0)
            return mtu.takeIf { it in MinChunkSize..MaxChunkSize } ?: DefaultChunkSize
        }

        private companion object {
            const val MinChunkSize = 20
            const val DefaultChunkSize = 244
            const val MaxChunkSize = 509
            const val WriteTimeoutMillis = 8_000L
        }
    }

    private companion object {
        val OnlyWriteUuid: UUID = UUID.fromString("0000ae01-0000-1000-8000-00805f9b34fb")
        val OnlyReadUuid: UUID = UUID.fromString("0000ae02-0000-1000-8000-00805f9b34fb")
        const val JlReconnectDelayMillis = 1_000L
    }
}

private fun String?.incrementBluetoothAddress(): String? {
    val parts = this?.split(':')?.takeIf { it.size == 6 } ?: return null
    val bytes = parts.map { part -> part.toIntOrNull(16) ?: return null }.toMutableList()
    bytes[bytes.lastIndex] = (bytes.last() + 1) and 0xFF
    return bytes.joinToString(":") { "%02X".format(it) }
}
