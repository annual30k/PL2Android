package com.patrollink.data.sourcenex

import com.patrollink.domain.DeviceCommand
import com.patrollink.domain.DeviceGateway
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.DeviceType
import com.patrollink.domain.ScannedDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceNexRoutingTest {
    @Test fun `recognizes documented SourceNex names`() {
        assertTrue(SourceNexDeviceGateway.isSourceNex("SourceNex-6240"))
        assertTrue(SourceNexDeviceGateway.isSourceNex("Aig-Glass-01"))
        assertFalse(SourceNexDeviceGateway.isSourceNex("Glory Glass 2-00F7"))
    }

    @Test fun `only SourceNex ids activate SourceNex control routing`() {
        assertTrue(isSourceNexDeviceId("sourcenex:AA:BB:CC:DD:EE:FF"))
        assertFalse(isSourceNexDeviceId("78:02:B7:66:00:F7"))
        assertFalse(isSourceNexDeviceId(null))
    }

    @Test fun `routes SourceNex ids without affecting legacy devices`() = runTest {
        val legacy = RecordingGateway("legacy")
        val sourceNex = RecordingGateway("source")
        val routing = RoutingDeviceGateway(legacy, sourceNex)

        routing.bind("sourcenex:AA:BB:CC:DD:EE:FF")
        routing.sendCommand("78:02:B7:66:00:F7", DeviceCommand.TakePhoto)

        assertEquals(listOf("sourcenex:AA:BB:CC:DD:EE:FF"), sourceNex.bound)
        assertEquals(listOf("78:02:B7:66:00:F7"), legacy.commanded)
    }

    @Test fun `deduplicates SourceNex system audio alias`() = runTest {
        val alias = scanned("22:22", "SourceNex-6240", "system-bluetooth-audio-connected")
        val native = scanned("sourcenex:22:22", "SourceNex-6240", SourceNexDeviceGateway.ServiceMarker)
        val routing = RoutingDeviceGateway(ScanningGateway(alias), ScanningGateway(native))
        assertEquals(listOf(native), routing.scan().first())
    }

    private class RecordingGateway(private val label: String) : DeviceGateway {
        val bound = mutableListOf<String>()
        val commanded = mutableListOf<String>()
        override fun scan(): Flow<List<ScannedDevice>> = flowOf(emptyList())
        override suspend fun bind(deviceId: String): DeviceStatus { bound += deviceId; return status(deviceId) }
        override suspend fun unbind(deviceId: String): DeviceStatus = status(deviceId).copy(online = false)
        override suspend fun sendCommand(deviceId: String, command: DeviceCommand): DeviceStatus { commanded += deviceId; return status(deviceId) }
        private fun status(id: String) = DeviceStatus(id, label, true, 0, 0, "", 0f, 0f, "", false, false, false, DeviceType.Glasses)
    }

    private class ScanningGateway(private vararg val devices: ScannedDevice) : DeviceGateway {
        override fun scan(): Flow<List<ScannedDevice>> = flowOf(devices.toList())
        override suspend fun bind(deviceId: String) = error("unused")
        override suspend fun unbind(deviceId: String) = error("unused")
        override suspend fun sendCommand(deviceId: String, command: DeviceCommand) = error("unused")
    }

    private fun scanned(id: String, name: String, service: String) =
        ScannedDevice(id, name, 3, service, true, id, DeviceType.Glasses)
}
