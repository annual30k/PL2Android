package com.patrollink.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OkHttpPatrolRestApiTest {
    @Test
    fun restClientCanBeConstructedWithBaseUrlAndTokenProvider() {
        val api = OkHttpPatrolRestApi(
            baseUrl = "https://backend.example.test",
            tokenProvider = { "token" }
        )

        assertNotNull(api)
    }

    @Test
    fun resumableUploadCreatesTaskSkipsUploadedChunksAndCompletes() = runTest {
        val server = MockWebServer()
        val media = File.createTempFile("patrol-media", ".bin").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9))
            deleteOnExit()
        }
        server.use {
            server.enqueueTask("UP-1", status = "INIT", uploadedIndexes = emptyList())
            server.enqueueTask("UP-1", status = "UPLOADING", uploadedIndexes = listOf(1))
            server.enqueueTask("UP-1", status = "UPLOADING", uploadedIndexes = listOf(0, 1))
            server.enqueueTask("UP-1", status = "UPLOADED", uploadedIndexes = listOf(0, 1, 2))
            server.enqueueTask("UP-1", status = "DONE", uploadedIndexes = emptyList(), fileId = "FILE-1", actualSha256 = true)

            val api = OkHttpPatrolRestApi(
                baseUrl = server.url("/").toString(),
                tokenProvider = { "access-token" },
                clientIdProvider = { "client-id" },
                resumableChunkSizeBytes = 4L
            )

            val uploaded = api.uploadMediaResumable(media, storageSide = "PHONE", bizType = "MEDIA", bizId = "VID-1").data

            assertEquals("DONE", uploaded.status)
            assertEquals("FILE-1", uploaded.fileId)
            val paths = (0 until server.requestCount).map { server.takeRequest().path }
            assertEquals(
                listOf(
                    "/api/v1/media/upload-tasks",
                    "/api/v1/media/upload-tasks/UP-1/retry",
                    "/api/v1/media/upload-tasks/UP-1/chunks/0",
                    "/api/v1/media/upload-tasks/UP-1/chunks/2",
                    "/api/v1/media/upload-tasks/UP-1/complete"
                ),
                paths
            )
            assertTrue(paths.none { it?.endsWith("/chunks/1") == true })
        }
    }

    private fun MockWebServer.enqueueTask(
        taskId: String,
        status: String,
        uploadedIndexes: List<Int>,
        fileId: String? = null,
        actualSha256: Boolean = false
    ) {
        enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(envelope(taskJson(taskId, status, uploadedIndexes, fileId, actualSha256)))
        )
    }

    private fun envelope(data: String): String =
        """{"code":200,"message":"ok","data":$data,"traceId":"test","timestamp":1}"""

    private fun taskJson(
        taskId: String,
        status: String,
        uploadedIndexes: List<Int>,
        fileId: String?,
        actualSha256: Boolean
    ): String {
        val fileIdJson = fileId?.let { "\"$it\"" } ?: "null"
        val actualShaJson = if (actualSha256) "\"abc\"" else "null"
        return """
            {
              "taskId":"$taskId",
              "fileId":$fileIdJson,
              "fileName":"patrol-media.bin",
              "mediaType":"VIDEO",
              "mimeType":"application/octet-stream",
              "fileSizeBytes":9,
              "chunkSizeBytes":4,
              "totalChunks":3,
              "uploadedChunks":${uploadedIndexes.size},
              "uploadedChunkIndexes":$uploadedIndexes,
              "uploadedBytes":${uploadedIndexes.size * 4},
              "expectedSha256":"expected",
              "actualSha256":$actualShaJson,
              "storageSide":"PHONE",
              "bizType":"MEDIA",
              "bizId":"VID-1",
              "status":"$status",
              "progress":${if (status == "DONE") "1.0" else "0.5"},
              "errorMessage":null,
              "badgeNo":"POLICE_9527",
              "officerName":"张警官",
              "deviceId":"HEADSET_001",
              "completedAt":"2026-05-14 15:00:00"
            }
        """.trimIndent()
    }
}
