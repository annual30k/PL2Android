package com.patrollink.data.ute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaKind
import com.patrollink.domain.TransferStatus
import com.yc.nadalsdk.constants.recorder.DeleteFileResult

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
    fun audioDeleteRequiresSdkBusinessSuccess() {
        assertEquals(true, audioDeleteSucceeded(responseSuccess = true, result = DeleteFileResult.DELETE_SUCCESS))
        assertEquals(false, audioDeleteSucceeded(responseSuccess = true, result = DeleteFileResult.DELETE_FAIL_RECORDING_IN_PROGRESS))
        assertEquals(false, audioDeleteSucceeded(responseSuccess = true, result = null))
        assertEquals(false, audioDeleteSucceeded(responseSuccess = false, result = DeleteFileResult.DELETE_SUCCESS))
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
    fun phoneMediaListIncludesBackendPhoneRecordsWhenLocalSandboxIsEmpty() {
        val backendPhone = mediaFile(
            id = "FILE-2066573218384801793",
            name = "20260101161434970.jpg",
            contentUri = "/files/FILE-2066573218384801793/download"
        )

        val merged = mergePhoneMediaSources(
            discoveredLocal = emptyList(),
            indexedLocal = emptyList(),
            backendPhone = listOf(backendPhone)
        )

        assertEquals(listOf(backendPhone), merged)
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

    private fun mediaFile(id: String, name: String, contentUri: String) =
        MediaFile(
            id = id,
            name = name,
            kind = MediaKind.Photo,
            time = "2026-06-16",
            size = "1.5 MB",
            duration = null,
            verified = true,
            local = true,
            transferStatus = TransferStatus.Done,
            progress = 1f,
            contentUri = contentUri
        )
}
