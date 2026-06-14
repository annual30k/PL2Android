package com.patrollink.data.ute

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UteWifiAccountAcceptanceTest {
    @Test
    fun allowsKnownGloryGlassesWhenStoreAndWifiConfigAreReadable() {
        assertTrue(
            UteWifiAccountAcceptance.canUseKnownGlassesWifiWithoutAccountAck(
                deviceName = "Glory Glass 2-00F7",
                hasGlassesStore = true,
                ssid = "UTE_00F7",
                password = "12345678"
            )
        )
    }

    @Test
    fun rejectsHeadsetNamesEvenWhenWifiConfigIsReadable() {
        assertFalse(
            UteWifiAccountAcceptance.canUseKnownGlassesWifiWithoutAccountAck(
                deviceName = "E1-Pro-A243",
                hasGlassesStore = true,
                ssid = "UTE_A243",
                password = "12345678"
            )
        )
    }

    @Test
    fun allowsKnownGlassesEvenWhenAccountAckTimeoutAlsoBlocksInfoReads() {
        assertTrue(
            UteWifiAccountAcceptance.canUseKnownGlassesWifiWithoutAccountAck(
                deviceName = "Glory Glass 2-00F7",
                hasGlassesStore = false,
                ssid = "",
                password = ""
            )
        )
    }
}
