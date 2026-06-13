package com.patrollink.data.ute

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import com.yc.nadalsdk.bean.Notify
import com.yc.nadalsdk.ble.open.UteBleClient
import com.yc.nadalsdk.ble.open.UteBleConnection
import com.yc.nadalsdk.ble.open.UteBleDevice
import com.yc.nadalsdk.constants.NotifyType
import com.yc.nadalsdk.listener.DeviceNotifyListener
import com.yc.nadalsdk.utils.open.SPUtil
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class UteSdkBridge(context: Context) {
    val appContext: Context = context.applicationContext
    val client: UteBleClient = UteBleClient.initialize(appContext).also {
        it.setSupportUserIdPair(true)
    }

    val connection: UteBleConnection
        get() = client.uteBleConnection

    private val _notifies = MutableSharedFlow<Notify>(extraBufferCapacity = 64)
    val notifies: SharedFlow<Notify> = _notifies

    init {
        SPUtil.initialize(appContext)
        connection.setDeviceNotifyListener(object : DeviceNotifyListener {
            override fun onNotify(device: UteBleDevice, notify: Notify) {
                emitNotify(notify)
            }
        })
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(address: String): UteBleConnection = client.connect(address).also {
        it.setDeviceNotifyListener(object : DeviceNotifyListener {
            override fun onNotify(device: UteBleDevice, notify: Notify) {
                emitNotify(notify)
            }
        })
    }

    fun disconnect() {
        client.disconnect()
    }

    fun bluetoothDevice(): BluetoothDevice? = runCatching { client.bluetoothDevice }.getOrNull()

    fun hasBluetoothScanPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            appContext.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

    fun hasBluetoothConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            appContext.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun emitNotify(notify: Notify) {
        logInterestingNotify(notify)
        _notifies.tryEmit(notify)
    }

    private fun logInterestingNotify(notify: Notify) {
        val typeName = when (notify.type) {
            NotifyType.DEVICE_PAIRED_STATE_NOTIFY -> "DEVICE_PAIRED_STATE_NOTIFY"
            NotifyType.CAMERA_CONTROL -> "CAMERA_CONTROL"
            NotifyType.SMART_GLASSES_STATE_NOTIFY -> "SMART_GLASSES_STATE_NOTIFY"
            NotifyType.SMART_GLASSES_IMAGE_DATA_NOTIFY -> "SMART_GLASSES_IMAGE_DATA_NOTIFY"
            NotifyType.SMART_GLASSES_AUDIO_DATA_NOTIFY -> "SMART_GLASSES_AUDIO_DATA_NOTIFY"
            NotifyType.SMART_GLASSES_AUDIO_DATA_REAL_NOTIFY -> "SMART_GLASSES_AUDIO_DATA_REAL_NOTIFY"
            NotifyType.SMART_GLASSES_STORE_INFO_NOTIFY -> "SMART_GLASSES_STORE_INFO_NOTIFY"
            NotifyType.SMART_GLASSES_DATA_STATE_NOTIFY -> "SMART_GLASSES_DATA_STATE_NOTIFY"
            NotifyType.SMART_WIFI_STATE_NOTIFY -> "SMART_WIFI_STATE_NOTIFY"
            NotifyType.THIRD_PARTY_DATA_TRANSMIT_NOTIFY -> "THIRD_PARTY_DATA_TRANSMIT_NOTIFY"
            else -> return
        }
        Log.i(
            NotifyLogTag,
            "$typeName error=${notify.errorCode},data=${notify.data?.javaClass?.simpleName ?: "null"}:${notify.data}"
        )
    }

    private companion object {
        const val NotifyLogTag = "PatrolUteNotify"
    }
}
