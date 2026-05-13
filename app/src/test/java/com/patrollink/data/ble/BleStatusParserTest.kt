package com.patrollink.data.ble

import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleStatusParserTest {
    @Test
    fun parsesBatteryFlagsSignalAndFirmwareFromNotifyPayload() {
        val current = DeviceStatus(
            id = "HEADSET_001",
            name = "耳机",
            online = false,
            battery = 10,
            signalBars = 1,
            onlineDuration = "离线",
            storageUsedGb = 0f,
            storageTotalGb = 0f,
            firmware = "old",
            isRecording = false,
            isTalking = false,
            cloudConnected = false,
            type = DeviceType.Headset
        )

        val next = BleStatusParser.parse(byteArrayOf(88.toByte(), 0x03, 0x04, '1'.code.toByte(), '.'.code.toByte(), '2'.code.toByte()), current)

        assertTrue(next.online)
        assertEquals(88, next.battery)
        assertEquals(4, next.signalBars)
        assertEquals("1.2", next.firmware)
        assertTrue(next.isRecording)
        assertTrue(next.isTalking)
    }

    @Test
    fun emptyPayloadKeepsCurrentStatus() {
        val current = DeviceStatus("id", "name", true, 55, 3, "1h", 0f, 0f, "fw", false, false, true)

        val next = BleStatusParser.parse(byteArrayOf(), current)

        assertEquals(current, next)
        assertFalse(next.isRecording)
    }
}
