package com.patrollink.data.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleGattProfileTest {
    @Test
    fun profileIsReadyOnlyWhenAllUuidsAreValid() {
        val service = "0000180f-0000-1000-8000-00805f9b34fb"
        val command = "00002a19-0000-1000-8000-00805f9b34fb"
        val status = "00002a1a-0000-1000-8000-00805f9b34fb"

        assertTrue(BleGattProfile.fromStrings(service, command, status).readyForGatt)
        assertFalse(BleGattProfile.fromStrings(service, "", status).readyForGatt)
        assertFalse(BleGattProfile.fromStrings("not-a-uuid", command, status).readyForGatt)
    }
}
