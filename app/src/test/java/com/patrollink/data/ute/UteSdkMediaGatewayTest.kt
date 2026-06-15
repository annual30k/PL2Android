package com.patrollink.data.ute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UteSdkMediaGatewayTest {
    @get:Rule
    val temp = TemporaryFolder()

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

    @Test
    fun webUiAssetPredicateCoversAiGlassPlaceholderImage() {
        assertEquals(true, "pictures_ute.jpg".isLikelyWebUiAssetPath())
        assertEquals(false, "20260613144750407.jpg".isLikelyWebUiAssetPath())
    }

    @Test
    fun supportedLocalMediaFilesScansDownloadedUteSubdirectory() {
        val root = temp.newFolder("patrol_media")
        val ute = root.resolve("ute").apply { mkdirs() }
        val downloadedPhoto = ute.resolve("20260613144750407.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        ute.resolve("20260613144750407.integrity").writeText("sha256=test")
        root.resolve("pictures_ute.jpg").writeBytes(byteArrayOf(4, 5, 6))

        val files = root.supportedLocalMediaFiles()

        assertTrue(files.contains(downloadedPhoto))
        assertEquals(listOf(downloadedPhoto), files)
    }

    @Test
    fun wifiMediaListFailurePropagatesBeforeBackendFallbackWhenNoSdkFilesExist() {
        assertEquals(
            true,
            shouldPropagateWifiMediaListFailure(
                hasWifiMediaClient = true,
                wifiFilesFailure = IllegalStateException("设备账号不一致"),
                sdkFilesEmpty = true
            )
        )
        assertEquals(
            false,
            shouldPropagateWifiMediaListFailure(
                hasWifiMediaClient = true,
                wifiFilesFailure = IllegalStateException("设备账号不一致"),
                sdkFilesEmpty = false
            )
        )
        assertEquals(
            false,
            shouldPropagateWifiMediaListFailure(
                hasWifiMediaClient = false,
                wifiFilesFailure = IllegalStateException("设备账号不一致"),
                sdkFilesEmpty = true
            )
        )
    }
}
