package com.patrollink.data.file

import org.junit.Assert.assertNotNull
import org.junit.Test

class WifiFileServiceClientTest {
    @Test
    fun clientCanBeConstructedForDeviceHotspotBaseUrl() {
        val client = WifiFileServiceClient("http://192.168.4.1:8080")

        assertNotNull(client)
    }
}
