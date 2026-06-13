package com.patrollink.data.ute

import com.yc.nadalsdk.constants.smart.WifiState
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
}
