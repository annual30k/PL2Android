package com.patrollink.data.wifi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceWifiNetworkConnectorTest {
    @Test
    fun reusesActiveWifiWhenCurrentSsidMatchesDeviceHotspot() {
        val selected = selectReusableWifiNetwork(
            currentSsid = "UTE_00F7",
            targetSsid = "UTE_00F7",
            activeNetwork = "wifi-active",
            activeNetworkIsWifi = true,
            wifiNetworks = listOf("wifi-active", "wifi-secondary")
        )

        assertEquals("wifi-active", selected)
    }

    @Test
    fun reusesVisibleWifiNetworkWhenDeviceHotspotHasNoInternetAndIsNotDefaultNetwork() {
        val selected = selectReusableWifiNetwork(
            currentSsid = "UTE_00F7",
            targetSsid = "UTE_00F7",
            activeNetwork = "cellular-active",
            activeNetworkIsWifi = false,
            wifiNetworks = listOf("wifi-device-hotspot")
        )

        assertEquals("wifi-device-hotspot", selected)
    }

    @Test
    fun doesNotReuseWifiWhenCurrentSsidDoesNotMatchDeviceHotspot() {
        val selected = selectReusableWifiNetwork(
            currentSsid = "office",
            targetSsid = "UTE_00F7",
            activeNetwork = "wifi-office",
            activeNetworkIsWifi = true,
            wifiNetworks = listOf("wifi-office")
        )

        assertNull(selected)
    }

    @Test
    fun reusedCurrentWifiSessionDoesNotOwnTemporaryProcessBinding() {
        assertFalse(ownsTemporaryNetworkBinding(hasNetworkCallback = false, hasLegacyNetworkId = false))
    }

    @Test
    fun requestedOrLegacyWifiSessionOwnsTemporaryProcessBinding() {
        assertTrue(ownsTemporaryNetworkBinding(hasNetworkCallback = true, hasLegacyNetworkId = false))
        assertTrue(ownsTemporaryNetworkBinding(hasNetworkCallback = false, hasLegacyNetworkId = true))
    }
}
