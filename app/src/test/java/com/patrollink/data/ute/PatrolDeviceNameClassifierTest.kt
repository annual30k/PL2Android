package com.patrollink.data.ute

import com.patrollink.domain.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatrolDeviceNameClassifierTest {
    @Test
    fun classifiesGloryAndAbaNamesAsGlassesBeforeAudioFallback() {
        assertEquals(DeviceType.Glasses, PatrolDeviceNameClassifier.typeFor("Glory Glass 2-00F7"))
        assertEquals(DeviceType.Glasses, PatrolDeviceNameClassifier.typeFor("ABA002-1234"))
        assertEquals(DeviceType.Glasses, PatrolDeviceNameClassifier.typeFor("执法眼镜"))
    }

    @Test
    fun keepsSmiAsControlCandidateButNotAudioHeadset() {
        assertTrue(PatrolDeviceNameClassifier.isKnownUteControlName("SMI-M14"))
        assertFalse(PatrolDeviceNameClassifier.isKnownAudioName("SMI-M14"))
        assertFalse(PatrolDeviceNameClassifier.hasSimilarAudioName("SMI-M14", "SMI-M14"))
        assertEquals(DeviceType.Glasses, PatrolDeviceNameClassifier.typeFor("SMI-M14"))
    }

    @Test
    fun keepsE1AndForceLinkAsAudioHeadsets() {
        assertEquals(DeviceType.Headset, PatrolDeviceNameClassifier.typeFor("E1-Pro-A243"))
        assertEquals(DeviceType.Headset, PatrolDeviceNameClassifier.typeFor("ForceLink-E1"))
        assertTrue(PatrolDeviceNameClassifier.hasSimilarAudioName("E1-Pro-A243", "E1-Pro"))
    }

    @Test
    fun doesNotTreatEveryBondedPatrolHeadsetAsConnectedWhenAnotherAudioProfileIsConnected() {
        assertFalse(isSystemBluetoothDeviceConnected(hiddenConnected = false, audioProfileConnected = true, name = "E1-Pro-A243"))
        assertTrue(isSystemBluetoothDeviceConnected(hiddenConnected = true, audioProfileConnected = true, name = "Glory Glass 2-00F7"))
    }
}
