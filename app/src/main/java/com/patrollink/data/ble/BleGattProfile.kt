package com.patrollink.data.ble

import java.util.UUID

data class BleGattProfile(
    val serviceUuid: UUID?,
    val commandCharacteristicUuid: UUID?,
    val statusCharacteristicUuid: UUID?
) {
    val readyForGatt: Boolean get() =
        serviceUuid != null && commandCharacteristicUuid != null && statusCharacteristicUuid != null

    companion object {
        val ClientCharacteristicConfig: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        fun fromStrings(service: String, command: String, status: String): BleGattProfile =
            BleGattProfile(service.toUuidOrNull(), command.toUuidOrNull(), status.toUuidOrNull())
    }
}

private fun String.toUuidOrNull(): UUID? =
    trim().takeIf { it.isNotBlank() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
