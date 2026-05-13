package com.patrollink.domain

import com.patrollink.data.MockAlertGateway
import com.patrollink.data.MockAuthGateway
import com.patrollink.data.MockDeviceGateway
import com.patrollink.data.MockMediaGateway
import com.patrollink.data.MockPatrolAreaGateway
import com.patrollink.data.MockRealtimeGateway
import com.patrollink.data.MockSosGateway
import com.patrollink.data.MockStreamRelayGateway
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatrolCoordinatorTest {
    private fun coordinator() = PatrolCoordinator(
        authGateway = MockAuthGateway(),
        deviceGateway = MockDeviceGateway(),
        alertGateway = MockAlertGateway(),
        mediaGateway = MockMediaGateway(),
        realtimeGateway = MockRealtimeGateway(),
        streamRelayGateway = MockStreamRelayGateway(),
        sosGateway = MockSosGateway(),
        patrolAreaGateway = MockPatrolAreaGateway()
    )

    @Test
    fun loginAndStartSessionConnectsRealtimeChannel() = runTest {
        val coordinator = coordinator()

        val session = coordinator.loginAndStartSession("POLICE_9527", "123456")

        assertTrue(session.accessToken.isNotBlank())
        assertEquals(RealtimeConnection.Connected, coordinator.currentRealtimeState())
    }

    @Test
    fun deviceOperationsCoverBindingPhotoRecordingAndTalk() = runTest {
        val coordinator = coordinator()
        val device = coordinator.bindDevice("HEADSET_001")

        coordinator.takePhoto(device)
        val recording = coordinator.setRecording(device, true)
        val talking = coordinator.setTalk(recording, true)

        assertTrue(recording.isRecording)
        assertTrue(talking.isTalking)
    }

    @Test
    fun alertHandlingClosesPendingAlert() = runTest {
        val coordinator = coordinator()
        val alert = coordinator.observeAlerts().first().first { it.status == AlertStatus.Pending }

        val closed = coordinator.handleAlert(alert.id, AlertResult.Resolved)

        assertEquals(AlertStatus.Closed, closed.status)
    }

    @Test
    fun mediaTransferToCloudCompletesWithVerifiedFile() = runTest {
        val coordinator = coordinator()
        val file = coordinator.mediaFiles(local = false).first()

        val uploaded = coordinator.transferMedia(file.id, TransferTarget.Cloud).last()

        assertEquals(TransferStatus.Done, uploaded.transferStatus)
        assertTrue(uploaded.verified)
    }

    @Test
    fun heartbeatRequiresSuccessfulSessionThenReturnsAck() = runTest {
        val coordinator = coordinator()
        coordinator.loginAndStartSession("POLICE_9527", "123456")

        val ack = coordinator.sendHeartbeat(com.patrollink.data.MockPatrolRepository().initialState().device)

        assertTrue(ack.accepted)
    }

    @Test
    fun streamAndSosCoordinatedFlowsReachActiveStates() = runTest {
        val coordinator = coordinator()
        val device = coordinator.bindDevice("HEADSET_001")

        coordinator.startStream(device, StreamMode.LowLatency)
        val streamState = coordinator.streamState().first()
        val sos = coordinator.activateSos(GpsLocation(39.9, 116.3, 8f, "CBD-North"))

        assertEquals(StreamRelayState.Relaying, streamState)
        assertEquals(SosPhase.Active, sos.phase)
    }

    @Test
    fun currentPatrolAreaComesFromTeamAreaGateway() = runTest {
        val area = coordinator().currentPatrolArea()

        assertEquals("TEAM-A-42", area.teamId)
        assertTrue(area.boundary.size >= 3)
        assertTrue(area.route.size >= 2)
    }
}
