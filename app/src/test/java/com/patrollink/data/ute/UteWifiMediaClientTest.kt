package com.patrollink.data.ute

import com.yc.nadalsdk.constants.smart.WifiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UteWifiMediaClientTest {
    @Test
    fun sdkApStoppedStateCanFallBackToPhoneConnectedHotspot() {
        assertTrue(shouldTryPhoneConnectedWifiFallback(WifiState.WIFI_AP_STOP))
    }

    @Test
    fun productionSyncPrefersPhoneConnectedHotspotBeforeOpeningSdkAp() {
        assertTrue(shouldPreferPhoneConnectedWifiBeforeSdkOpen(currentPhoneWifiOnly = false))
    }

    @Test
    fun downloadedFileSizeValidationAcceptsUnknownOrExactSize() {
        assertNull(validateDownloadedFileSize(actualBytes = 1024L, expectedBytes = null))
        assertNull(validateDownloadedFileSize(actualBytes = 1024L, expectedBytes = 1024L))
    }

    @Test
    fun downloadedFileSizeValidationRejectsPartialFile() {
        assertEquals(
            "download size mismatch actual=3407872 expected=15833497",
            validateDownloadedFileSize(actualBytes = 3_407_872L, expectedBytes = 15_833_497L)
        )
    }
}
