package com.patrollink.data

import com.patrollink.domain.AlertResult
import com.patrollink.domain.AlertStatus
import com.patrollink.domain.DeviceCommand
import com.patrollink.domain.DeviceType
import com.patrollink.domain.GpsLocation
import com.patrollink.domain.RealtimeConnection
import com.patrollink.domain.SosPhase
import com.patrollink.domain.StreamMode
import com.patrollink.domain.StreamRelayState
import com.patrollink.domain.TransferStatus
import com.patrollink.domain.TransferTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockGatewayTest {
    @Test
    fun authLoginRejectsShortPasswordAndReturnsSessionForValidCredentials() = runTest {
        val gateway = MockAuthGateway()
        val session = gateway.login("POLICE_9527", "123456")

        assertTrue(session.accessToken.startsWith("mock-access"))
        assertEquals(7200, session.expiresInSeconds)
    }

    @Test
    fun deviceScanBindAndCommandsUpdateDeviceState() = runTest {
        val gateway = MockDeviceGateway()
        val scanned = gateway.scan().last()
        val bound = gateway.bind(scanned.first().id)
        val recording = gateway.sendCommand(bound.id, DeviceCommand.StartRecord)
        val talking = gateway.sendCommand(bound.id, DeviceCommand.StartTalk)

        assertEquals("HEADSET_001", bound.id)
        assertTrue(scanned.any { it.type == DeviceType.Glasses && it.name == "ForceLink-G1" })
        assertTrue(recording.isRecording)
        assertTrue(talking.isTalking)
    }

    @Test
    fun alertAcknowledgeAndCloseMoveStatusForward() = runTest {
        val gateway = MockAlertGateway()
        val alert = gateway.observeAlerts().first().first { it.status == AlertStatus.Pending }
        val handling = gateway.acknowledge(alert.id)
        val closed = gateway.close(alert.id, AlertResult.Resolved, "现场已处理")

        assertEquals(AlertStatus.Handling, handling.status)
        assertEquals(AlertStatus.Closed, closed.status)
    }

    @Test
    fun mediaTransferEmitsHashUploadVerifyDoneStates() = runTest {
        val gateway = MockMediaGateway()
        val file = gateway.listFiles(local = false).first()
        val result = gateway.transfer(file.id, TransferTarget.PhoneSandbox).last()

        assertEquals(TransferStatus.Done, result.transferStatus)
        assertEquals(1f, result.progress)
        assertTrue(result.local)
        assertTrue(result.verified)
        assertTrue(gateway.listFiles(local = false).any { it.id == file.id })
        assertEquals(TransferStatus.Idle, gateway.listFiles(local = true).first { it.id == file.id }.transferStatus)
    }

    @Test
    fun mediaDeleteAndVerifyUpdateFileCollection() = runTest {
        val gateway = MockMediaGateway()
        val file = gateway.listFiles(local = false).first()

        assertTrue(gateway.verifySha256(file.id))
        assertTrue(gateway.delete(file.id, local = false))
        assertTrue(gateway.listFiles(local = false).none { it.id == file.id })
    }

    @Test
    fun realtimeConnectHeartbeatAndDisconnectFollowExpectedStates() = runTest {
        val realtime = MockRealtimeGateway()
        val device = MockPatrolRepository().initialState().device

        realtime.connect("token")
        val ack = realtime.sendHeartbeat(device)
        realtime.disconnect()

        assertTrue(ack.accepted)
        assertEquals(RealtimeConnection.Disconnected, realtime.connection().first())
    }

    @Test
    fun streamRelayStartsAndStops() = runTest {
        val gateway = MockStreamRelayGateway()

        gateway.start("HEADSET_001", StreamMode.LowLatency)
        assertEquals(StreamRelayState.Relaying, gateway.state().first())

        gateway.stop()
        assertEquals(StreamRelayState.Idle, gateway.state().first())
    }

    @Test
    fun sosActivateAndCancelUpdateEmergencyState() = runTest {
        val gateway = MockSosGateway()
        val event = gateway.activate(GpsLocation(39.9, 116.3, 8f, "CBD-North"))

        assertEquals(SosPhase.Active, event.phase)
        assertEquals(SosPhase.Active, gateway.state().first().phase)

        val cancelled = gateway.cancel()
        assertEquals(SosPhase.Cancelled, cancelled.phase)
        assertEquals(SosPhase.Cancelled, gateway.state().first().phase)
    }
}
