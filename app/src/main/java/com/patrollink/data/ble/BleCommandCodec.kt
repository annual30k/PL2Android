package com.patrollink.data.ble

import com.patrollink.domain.DeviceCommand

object BleCommandCodec {
    private const val Header: Byte = 0x55
    private const val Version: Byte = 0x01

    fun encode(command: DeviceCommand): ByteArray {
        val code = when (command) {
            DeviceCommand.TakePhoto -> 0x10
            DeviceCommand.StartRecord -> 0x20
            DeviceCommand.StopRecord -> 0x21
            DeviceCommand.StartTalk -> 0x30
            DeviceCommand.StopTalk -> 0x31
        }.toByte()
        val checksum = (Header.toInt() xor Version.toInt() xor code.toInt()).toByte()
        return byteArrayOf(Header, Version, code, checksum)
    }
}
