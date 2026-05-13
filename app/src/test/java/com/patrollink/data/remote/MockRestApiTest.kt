package com.patrollink.data.remote

import com.patrollink.domain.AlertLevel
import com.patrollink.domain.AlertStatus
import com.patrollink.domain.GpsLocation
import com.patrollink.domain.MediaKind
import com.patrollink.domain.StreamRelayState
import com.patrollink.domain.TransferStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockRestApiTest {
    @Test
    fun loginUsesSpringBootStyleEnvelopeAndTokenPayload() {
        val response = MockRestApi().login(LoginRequestDto("POLICE_9527", "123456"))

        assertEquals(200, response.code)
        assertEquals("OK", response.message)
        assertTrue(response.success)
        assertTrue(response.traceId.isNotBlank())
        assertEquals("Bearer", response.data.tokenType)
        assertTrue(response.data.accessToken.startsWith("mock-access"))
    }

    @Test
    fun pagedAlertsUseBackendCompatiblePageEnvelopeAndMapToDomain() {
        val response = MockRestApi().alerts(page = 1, pageSize = 20)
        val first = response.data.items.first().toDomain()

        assertEquals(1, response.data.page)
        assertEquals(20, response.data.pageSize)
        assertEquals(3, response.data.total)
        assertFalse(response.data.hasMore)
        assertEquals(AlertLevel.Critical, first.level)
        assertEquals(AlertStatus.Pending, first.status)
    }

    @Test
    fun mediaTransferProducesRestProgressStatesAndDomainMediaFile() {
        val api = MockRestApi()
        val steps = api.transferMedia("VID-042", TransferRequestDto("PHONE_SANDBOX"))
        val last = steps.last().data.toDomain()

        assertEquals(4, steps.size)
        assertEquals("HASHING", steps.first().data.transferStatus)
        assertEquals(MediaKind.Video, last.kind)
        assertEquals(TransferStatus.Done, last.transferStatus)
        assertTrue(last.local)
        assertTrue(last.verified)
        assertTrue(api.mediaFiles(local = false).data.items.any { it.fileId == "VID-042" })
        assertEquals("IDLE", api.mediaFiles(local = true).data.items.first { it.fileId == "VID-042" }.transferStatus)
    }

    @Test
    fun heartbeatAndStreamResponsesMapToDomain() {
        val api = MockRestApi()
        val ack = api.heartbeat(HeartbeatRequestDto("HEADSET_001", true, 80, 4, "IDLE", 1L)).data.toDomain()
        val stream = api.startStream(StreamRelayRequestDto("HEADSET_001", "LOW_LATENCY")).data.toDomain()

        assertTrue(ack.accepted)
        assertEquals(1715832000L, ack.serverTime)
        assertEquals(StreamRelayState.Relaying, stream)
    }

    @Test
    fun sosResponseContainsLocationAudioAndEtaFields() = runTest {
        val location = GpsLocation(39.9, 116.3, 8f, "CBD-North").toDto()
        val response = MockRestApi().activateSos(location).data

        assertEquals("ACTIVE", response.phase)
        assertEquals("CBD-North", response.location?.address)
        assertTrue(response.recordingAudio)
        assertEquals(4, response.backupEtaMinutes)
    }
}
