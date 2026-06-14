package com.patrollink.data.ute

import com.patrollink.domain.MediaKind
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UteWifiMediaParserTest {
    @Test
    fun parsesNestedJsonFilesAndResolvesRelativeUrls() {
        val files = UteWifiMediaParser.parseRemoteFiles(
            body = """
                {
                  "data": {
                    "files": [
                      {"fileName": "VID_001.MP4", "url": "/DCIM/VID_001.MP4", "fileSize": 1048576},
                      {"name": "AUD_002.opus", "path": "audio/AUD_002.opus", "size": 2048}
                    ]
                  }
                }
            """.trimIndent(),
            sourceUrl = "http://192.168.4.1/api/files"
        )

        assertEquals(2, files.size)
        assertEquals(MediaKind.Video, files[0].kind)
        assertEquals("VID_001.MP4", files[0].name)
        assertEquals("http://192.168.4.1/DCIM/VID_001.MP4", files[0].url)
        assertEquals(1048576L, files[0].sizeBytes)
        assertEquals(MediaKind.Audio, files[1].kind)
        assertEquals("http://192.168.4.1/api/audio/AUD_002.opus", files[1].url)
    }

    @Test
    fun parsesDirectoryHtmlAndBuildsStableWifiIds() {
        val files = UteWifiMediaParser.parseRemoteFiles(
            body = """
                <html>
                  <body>
                    <a href="/photo/IMG_001.jpg">IMG_001.jpg</a>
                    <a href="video/VID_002.mov">VID_002.mov</a>
                  </body>
                </html>
            """.trimIndent(),
            sourceUrl = "http://192.168.4.1/files/"
        )

        assertEquals(listOf(MediaKind.Photo, MediaKind.Video), files.map { it.kind })
        assertTrue(files.all { it.id.startsWith("ute-wifi-") })
        assertEquals("http://192.168.4.1/photo/IMG_001.jpg", files[0].url)
        assertEquals("http://192.168.4.1/files/video/VID_002.mov", files[1].url)
    }

    @Test
    fun ignoresRouterOrWebUiImageAssets() {
        val files = UteWifiMediaParser.parseRemoteFiles(
            body = """
                <html>
                  <img src="/assets/logo.png">
                  <img src="/static/icon_device.jpg">
                  <a href="/res/background.jpeg">bg</a>
                  <a href="/DCIM/100MEDIA/IMG_20260614_163000.jpg">photo</a>
                </html>
            """.trimIndent(),
            sourceUrl = "http://192.168.1.1/"
        )

        assertEquals(1, files.size)
        assertEquals("IMG_20260614_163000.jpg", files[0].name)
        assertEquals("http://192.168.1.1/DCIM/100MEDIA/IMG_20260614_163000.jpg", files[0].url)
    }

    @Test
    fun ignoresAiGlassWebUiImagesReturnedInJsonMediaList() {
        val files = UteWifiMediaParser.parseRemoteFiles(
            body = """
                {
                  "data": {
                    "files": [
                      {"name": "pictures_ute.jpg", "size": 24596, "type": "jpg"},
                      {"name": "20260613144750407.jpg", "size": 1593149, "type": "jpg"}
                    ]
                  }
                }
            """.trimIndent(),
            sourceUrl = "http://192.168.222.1:8000/media/list"
        )

        assertEquals(1, files.size)
        assertEquals("20260613144750407.jpg", files[0].name)
    }

    @Test
    fun parsesGloryStyleMediaListWithSnakeCaseAndFileUrl() {
        val files = UteWifiMediaParser.parseRemoteFiles(
            body = """
                {
                  "code": 0,
                  "mediaList": [
                    {"file_name": "GX010001.MP4", "fileUrl": "DCIM/100MEDIA/GX010001.MP4", "file_size": 7340032, "file_type": "video"},
                    {"file_name": "REC_0001.AAC", "download_url": "/record/REC_0001.AAC", "file_size": "4096", "file_type": "audio"}
                  ]
                }
            """.trimIndent(),
            sourceUrl = "http://192.168.4.1/media/list"
        )

        assertEquals(2, files.size)
        assertEquals(MediaKind.Video, files[0].kind)
        assertEquals("GX010001.MP4", files[0].name)
        assertEquals("http://192.168.4.1/media/DCIM/100MEDIA/GX010001.MP4", files[0].url)
        assertEquals(7340032L, files[0].sizeBytes)
        assertEquals(MediaKind.Audio, files[1].kind)
        assertEquals("REC_0001.AAC", files[1].name)
        assertEquals("http://192.168.4.1/record/REC_0001.AAC", files[1].url)
        assertEquals(4096L, files[1].sizeBytes)
    }

    @Test
    fun parsesAiGlassMediaListReturnedByGloryGlassHotspot() {
        val files = UteWifiMediaParser.parseRemoteFiles(
            body = """
                {
                  "code": 200,
                  "data": {
                    "files": [
                      {"name": "20260613144750407.jpg", "size": 1593149, "type": "jpg"},
                      {"name": "20260613225425409.jpg", "size": 1388813, "type": "jpg"}
                    ]
                  }
                }
            """.trimIndent(),
            sourceUrl = "http://192.168.222.1:8000/media/list"
        )

        assertEquals(2, files.size)
        assertEquals(MediaKind.Photo, files[0].kind)
        assertEquals("20260613144750407.jpg", files[0].name)
        assertEquals(1593149L, files[0].sizeBytes)
        assertEquals("http://192.168.222.1:8000/media/20260613144750407.jpg", files[0].url)
        assertEquals(
            listOf(
                "http://192.168.222.1:8000/media/20260613144750407.jpg",
                "http://192.168.222.1:8000/20260613144750407.jpg",
                "http://192.168.222.1:8000/download/20260613144750407.jpg",
                "http://192.168.222.1:8000/file/20260613144750407.jpg",
                "http://192.168.222.1:8000/media/download?name=20260613144750407.jpg"
            ),
            files[0].downloadUrls
        )
        assertTrue(files[0].toMediaFile(local = false).name.startsWith("眼镜照片_"))
    }

    @Test
    fun localTargetKeepsExtensionAndSanitizesUnsafeCharacters() {
        val file = UteWifiRemoteFile(
            id = "ute-wifi-test",
            name = "../VID 001?.mp4",
            kind = MediaKind.Video,
            sizeBytes = null,
            url = "http://192.168.4.1/DCIM/VID%20001.mp4"
        )

        assertEquals("VID_001_.mp4", file.localTarget(File("/tmp/patrol")).name)
    }
}
