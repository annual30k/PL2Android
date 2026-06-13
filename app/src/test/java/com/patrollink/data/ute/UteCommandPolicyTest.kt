package com.patrollink.data.ute

import com.patrollink.domain.DeviceCommand
import org.junit.Assert.assertFalse
import org.junit.Test

class UteCommandPolicyTest {
    @Test
    fun photoBackgroundSyncDoesNotBlockSubsequentMediaCommands() {
        assertFalse(UteCommandPolicy.shouldWaitForPhotoSyncBefore(DeviceCommand.StartRecord))
        assertFalse(UteCommandPolicy.shouldWaitForPhotoSyncBefore(DeviceCommand.StopRecord))
        assertFalse(UteCommandPolicy.shouldWaitForPhotoSyncBefore(DeviceCommand.StartTalk))
        assertFalse(UteCommandPolicy.shouldWaitForPhotoSyncBefore(DeviceCommand.StopTalk))
    }

    @Test
    fun acceptedAsyncMediaCommandsDoNotWaitForStateConfirmation() {
        assertFalse(UteCommandPolicy.shouldWaitForStateConfirmation(DeviceCommand.StartRecord))
        assertFalse(UteCommandPolicy.shouldWaitForStateConfirmation(DeviceCommand.StopRecord))
        assertFalse(UteCommandPolicy.shouldWaitForStateConfirmation(DeviceCommand.StartTalk))
        assertFalse(UteCommandPolicy.shouldWaitForStateConfirmation(DeviceCommand.StopTalk))
    }
}
