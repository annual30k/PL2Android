package com.patrollink.data.ute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UteSdkMediaGatewayTest {
    @Test
    fun wifiDeviceDeleteNameStripsDisplayPrefix() {
        assertEquals(
            "20260613144750407.jpg",
            wifiDeviceFileNameForDelete("ute-wifi-abcd", "眼镜照片_20260613144750407.jpg")
        )
        assertEquals(
            "GX010002.MP4",
            wifiDeviceFileNameForDelete("ute-wifi-video", "眼镜视频_GX010002.MP4")
        )
        assertEquals(
            "REC001.opus",
            wifiDeviceFileNameForDelete("ute-wifi-audio", "设备录音_REC001.opus")
        )
    }

    @Test
    fun wifiDeviceDeleteNameRequiresWifiFileAndKnownName() {
        assertNull(wifiDeviceFileNameForDelete("ute-photo-local", "眼镜照片_IMG.jpg"))
        assertNull(wifiDeviceFileNameForDelete("ute-wifi-abcd", null))
        assertNull(wifiDeviceFileNameForDelete("ute-wifi-abcd", ""))
    }
}
