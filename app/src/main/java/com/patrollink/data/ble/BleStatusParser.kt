package com.patrollink.data.ble

import com.patrollink.domain.DeviceStatus

object BleStatusParser {
    fun parse(payload: ByteArray, current: DeviceStatus): DeviceStatus {
        if (payload.isEmpty()) return current
        val battery = payload.getOrNull(0)?.toInt()?.and(0xFF)?.coerceIn(0, 100) ?: current.battery
        val flags = payload.getOrNull(1)?.toInt() ?: 0
        val signalBars = payload.getOrNull(2)?.toInt()?.and(0xFF)?.coerceIn(1, 5) ?: current.signalBars
        val firmware = payload.drop(3)
            .takeIf { it.isNotEmpty() }
            ?.toByteArray()
            ?.decodeToString()
            ?.trim(Char.MIN_VALUE, ' ', '\n', '\r', '\t')
            ?.takeIf { it.isNotBlank() }
            ?: current.firmware
        return current.copy(
            online = true,
            battery = battery,
            batteryKnown = true,
            signalBars = signalBars,
            firmware = firmware,
            isRecording = flags and 0x01 != 0,
            isTalking = flags and 0x02 != 0
        )
    }
}
