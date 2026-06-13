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
