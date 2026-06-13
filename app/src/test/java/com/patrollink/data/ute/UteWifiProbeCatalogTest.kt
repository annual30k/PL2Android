package com.patrollink.data.ute

import org.junit.Assert.assertTrue
import org.junit.Test

class UteWifiProbeCatalogTest {
    @Test
    fun includesGloryAndActionCameraStyleMediaEndpoints() {
        assertTrue("/api/media/list" in UteWifiProbeCatalog.Paths)
        assertTrue("/media/list" in UteWifiProbeCatalog.Paths)
        assertTrue("/api/file/list" in UteWifiProbeCatalog.Paths)
        assertTrue("/DCIM/100MEDIA" in UteWifiProbeCatalog.Paths)
        assertTrue("/record/" in UteWifiProbeCatalog.Paths)
        assertTrue("/video/" in UteWifiProbeCatalog.Paths)
        assertTrue("/photo/" in UteWifiProbeCatalog.Paths)
    }

    @Test
    fun includesCommonDeviceApGatewaysBeforeLanFallbacks() {
        assertTrue(UteWifiProbeCatalog.DefaultHosts.indexOf("192.168.4.1") < UteWifiProbeCatalog.DefaultHosts.indexOf("192.168.1.1"))
        assertTrue("192.168.43.1" in UteWifiProbeCatalog.DefaultHosts)
        assertTrue("192.168.49.1" in UteWifiProbeCatalog.DefaultHosts)
    }

    @Test
    fun keepsProbeSetBoundedEnoughForInteractiveDiagnostics() {
        assertTrue(UteWifiProbeCatalog.Ports.size <= 8)
        assertTrue(UteWifiProbeCatalog.Paths.size <= 36)
    }
}
