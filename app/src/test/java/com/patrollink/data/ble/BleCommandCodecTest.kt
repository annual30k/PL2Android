package com.patrollink.data.ble

import com.patrollink.domain.DeviceCommand
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BleCommandCodecTest {
    @Test
    fun encodesCommandsWithHeaderVersionCodeAndChecksum() {
        assertArrayEquals(byteArrayOf(0x55, 0x01, 0x10, 0x44), BleCommandCodec.encode(DeviceCommand.TakePhoto))
        assertArrayEquals(byteArrayOf(0x55, 0x01, 0x20, 0x74), BleCommandCodec.encode(DeviceCommand.StartRecord))
        assertArrayEquals(byteArrayOf(0x55, 0x01, 0x21, 0x75), BleCommandCodec.encode(DeviceCommand.StopRecord))
    }

    @Test
    fun allCommandsProduceFixedLengthPayloads() {
        DeviceCommand.entries.forEach { command ->
            assertEquals(4, BleCommandCodec.encode(command).size)
        }
    }
}
