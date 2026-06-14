package com.patrollink.debug

import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaKind
import com.patrollink.domain.TransferStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmokeWifiPreflightOptionsTest {
    @Test
    fun skipsPreflightWhenBothProbesAreDisabled() {
        val options = SmokeWifiPreflightOptions(
            requestPairingBeforeWifi = false,
            probeAccountBeforeWifi = false
        )

        assertFalse(options.enabled)
    }

    @Test
    fun enablesPreflightWhenPairingOrAccountProbeIsRequested() {
        assertTrue(
            SmokeWifiPreflightOptions(
                requestPairingBeforeWifi = true,
                probeAccountBeforeWifi = false
            ).enabled
        )
        assertTrue(
            SmokeWifiPreflightOptions(
                requestPairingBeforeWifi = false,
                probeAccountBeforeWifi = true
            ).enabled
        )
    }

    @Test
    fun directWifiSwitchOptionReflectsExplicitProbe() {
        assertFalse(SmokeDirectWifiSwitchOptions(noAccountGuard = false).noAccountGuard)
        assertTrue(SmokeDirectWifiSwitchOptions(noAccountGuard = true).noAccountGuard)
    }

    @Test
    fun wifiMediaSyncOptionOnlyDownloadsWhenRequested() {
        assertFalse(SmokeWifiMediaSyncOptions(downloadFirst = false).downloadFirst)
        assertTrue(SmokeWifiMediaSyncOptions(downloadFirst = true).downloadFirst)
    }

    @Test
    fun wifiMediaSyncOptionCanUseCurrentPhoneWifiOnly() {
        assertFalse(SmokeWifiMediaSyncOptions(downloadFirst = false).currentPhoneWifiOnly)
        assertTrue(SmokeWifiMediaSyncOptions(downloadFirst = false, currentPhoneWifiOnly = true).currentPhoneWifiOnly)
    }

    @Test
    fun wifiMediaSyncOptionSelectsRequestedKindForDownloadProof() {
        val photo = smokeMedia("photo-1", MediaKind.Photo)
        val audio = smokeMedia("audio-1", MediaKind.Audio)
        val options = SmokeWifiMediaSyncOptions(downloadFirst = true, downloadKind = MediaKind.Audio)

        assertEquals(audio, options.selectDownloadCandidate(listOf(photo, audio)))
    }

    @Test
    fun wifiMediaSyncOptionDoesNotFallbackToPhotoWhenRequestedVideoIsMissing() {
        val photo = smokeMedia("photo-1", MediaKind.Photo)
        val options = SmokeWifiMediaSyncOptions(downloadFirst = true, downloadKind = MediaKind.Video)

        assertNull(options.selectDownloadCandidate(listOf(photo)))
    }

    @Test
    fun wifiMediaSyncOptionDefaultsBatchDownloadToFirstFileOnly() {
        val first = smokeMedia("photo-1", MediaKind.Photo)
        val second = smokeMedia("photo-2", MediaKind.Photo)
        val options = SmokeWifiMediaSyncOptions(downloadFirst = true)

        assertEquals(listOf(first), options.selectDownloadCandidates(listOf(first, second)))
    }

    @Test
    fun wifiMediaSyncOptionSelectsLimitedCandidatesByKind() {
        val photo1 = smokeMedia("photo-1", MediaKind.Photo)
        val video = smokeMedia("video-1", MediaKind.Video)
        val photo2 = smokeMedia("photo-2", MediaKind.Photo)
        val photo3 = smokeMedia("photo-3", MediaKind.Photo)
        val options = SmokeWifiMediaSyncOptions(
            downloadFirst = true,
            downloadKind = MediaKind.Photo,
            downloadLimit = 2
        )

        assertEquals(
            listOf(photo1, photo2),
            options.selectDownloadCandidates(listOf(photo1, video, photo2, photo3))
        )
    }

    @Test
    fun currentWifiMediaOnlyRecognizesDeviceHotspots() {
        assertTrue("UTE_00F7".isLikelyDeviceWifiHotspotSsidForSmoke())
        assertTrue("Glory Glass 2".isLikelyDeviceWifiHotspotSsidForSmoke())
        assertTrue("AI_GLASS_1234".isLikelyDeviceWifiHotspotSsidForSmoke())
        assertFalse("英英杀人女魔头5G".isLikelyDeviceWifiHotspotSsidForSmoke())
        assertFalse("Office-WiFi".isLikelyDeviceWifiHotspotSsidForSmoke())
    }

    private fun smokeMedia(id: String, kind: MediaKind): MediaFile =
        MediaFile(
            id = id,
            name = "$id.dat",
            kind = kind,
            time = "now",
            size = "1B",
            duration = null,
            verified = false,
            local = false,
            transferStatus = TransferStatus.Idle,
            progress = 0f
        )
}
