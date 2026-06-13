package com.patrollink.data.ute

import com.patrollink.domain.DeviceCommand

object UteCommandPolicy {
    fun shouldWaitForPhotoSyncBefore(command: DeviceCommand): Boolean = command == DeviceCommand.TakePhoto

    fun shouldWaitForStateConfirmation(command: DeviceCommand): Boolean = command == DeviceCommand.TakePhoto
}
