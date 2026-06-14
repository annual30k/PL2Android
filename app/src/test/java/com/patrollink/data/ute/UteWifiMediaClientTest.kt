package com.patrollink.data.ute

import com.yc.nadalsdk.constants.smart.WifiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun cachedWifiDownloadDoesNotConnectBeforeSdkOpensApWhenPhoneIsNotOnDeviceHotspot() {
        assertEquals(
            false,
            shouldUseCachedWifiDownloadPath(currentPhoneWifiOnly = false, phoneAlreadyConnected = false)
        )
    }

    @Test
    fun cachedWifiDownloadCanReuseCurrentDeviceHotspotConnection() {
        assertTrue(shouldUseCachedWifiDownloadPath(currentPhoneWifiOnly = false, phoneAlreadyConnected = true))
        assertTrue(shouldUseCachedWifiDownloadPath(currentPhoneWifiOnly = true, phoneAlreadyConnected = false))
    }

    @Test
    fun wifiOpenWarmupDoesNotNotifyMediaSyncCompletedBeforeApOpen() {
        assertFalse(shouldSendMediaSyncCompletedBeforeWifiOpen())
    }

    @Test
    fun wifiOpenWarmupSkipsCredentialWritesWhenSdkSsidIsBlank() {
        assertFalse(shouldConfigureGloryViewWifiWarmup(""))
        assertTrue(shouldConfigureGloryViewWifiWarmup("UTE_00F7"))
    }

    @Test
    fun recognizesUteHotspotAsDeviceWifiForManualConnectionFallback() {
        assertTrue("UTE_00F7".isLikelyDeviceWifiHotspotSsid())
        assertTrue("ute_00f7".isLikelyDeviceWifiHotspotSsid())
        assertTrue("AI_Glass_AP".isLikelyDeviceWifiHotspotSsid())
        assertFalse("英英杀人女魔头5G".isLikelyDeviceWifiHotspotSsid())
        assertFalse("office-wifi".isLikelyDeviceWifiHotspotSsid())
    }

    @Test
    fun knownGlassesCanDeriveWifiSsidFromBluetoothAddressWhenSdkSsidIsBlank() {
        assertEquals(
            "UTE_00F7",
            fallbackDeviceWifiSsidForKnownGlasses(
                deviceName = "Glory Glass 2-00F7",
                deviceAddress = "78:02:B7:66:00:F7"
            )
        )
    }

    @Test
    fun knownGlassesCanDeriveWifiSsidFromNameWhenAddressIsMissing() {
        assertEquals(
            "UTE_00F7",
            fallbackDeviceWifiSsidForKnownGlasses(
                deviceName = "Glory Glass 2-00F7",
                deviceAddress = null
            )
        )
    }

    @Test
    fun nonGlassesDoNotUseUteWifiSsidFallback() {
        assertNull(
            fallbackDeviceWifiSsidForKnownGlasses(
                deviceName = "E1-Pro-A243",
                deviceAddress = "FD:4A:BA:43:A2:43"
            )
        )
    }

    @Test
    fun wifiSwitchRequiresAcceptedBooleanAckWhenSdkReturnsData() {
        assertTrue(isWifiSwitchAccepted(enabled = true, responseSuccess = true, responseData = true))
        assertTrue(isWifiSwitchAccepted(enabled = false, responseSuccess = true, responseData = true))
        assertFalse(isWifiSwitchAccepted(enabled = true, responseSuccess = true, responseData = false))
        assertFalse(isWifiSwitchAccepted(enabled = true, responseSuccess = false, responseData = true))
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
