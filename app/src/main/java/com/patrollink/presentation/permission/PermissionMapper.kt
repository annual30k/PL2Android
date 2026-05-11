package com.patrollink.presentation.permission

import android.Manifest
import android.os.Build
import com.patrollink.domain.AppPermission

fun AppPermission.toAndroidPermission(): String? = when (this) {
    AppPermission.Internet,
    AppPermission.NetworkState,
    AppPermission.ForegroundService -> null
    AppPermission.FineLocation -> Manifest.permission.ACCESS_FINE_LOCATION
    AppPermission.BluetoothScan -> if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_SCAN else null
    AppPermission.BluetoothConnect -> if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_CONNECT else null
    AppPermission.BluetoothAdvertise -> if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_ADVERTISE else null
    AppPermission.Camera -> Manifest.permission.CAMERA
    AppPermission.RecordAudio -> Manifest.permission.RECORD_AUDIO
    AppPermission.PostNotifications -> if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null
}
