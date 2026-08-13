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
    fun currentUserMapsAvatarFromBackendProfile() = runTest {
        val server = MockWebServer()
        server.use {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        envelope(
                            """
                            {
                              "userId":"1",
                              "name":"张警官",
                              "badgeNo":"POLICE_9527",
                              "department":"第一巡逻支队",
                              "phone":"13800009527",
                              "email":"zhang.police@city.gov.cn",
                              "dutyArea":"福州温泉公园",
                              "shiftDuration":"05:24:12",
                              "patrolGroup":"巡逻组 A-42",
                              "systemNode":"0x4F2A",
                              "avatar":"http://192.168.11.157:9000/patrol/avatar/officer.png"
                            }
                            """.trimIndent()
                        )
                    )
            )
            val api = OkHttpPatrolRestApi(
                baseUrl = server.url("/").toString(),
                tokenProvider = { "access-token" },
                clientIdProvider = { "client-id" }
            )

            val user = api.currentUser().data.toDomain()

            assertEquals("http://192.168.11.157:9000/patrol/avatar/officer.png", user.avatarUrl)
            assertEquals("/api/v1/users/me", server.takeRequest().path)
        }
    }

    @Test
    fun platformCommandsArePulledAndAcknowledgedThroughDedicatedEndpoints() = runTest {
        val server = MockWebServer()
        server.use {
            server.enqueue(
                MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
                    envelope(
                        """[{"commandId":"CMD-1","deviceId":"HEADSET_001","command":"TAKE_PHOTO","requestId":"REQ-1","operatorId":"admin","sentAt":123}]"""
                    )
                )
            )
            server.enqueue(
                MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
                    envelope("""{"success":true,"state":"ACKED","message":"设备已执行并确认"}""")
                )
            )
            val api = OkHttpPatrolRestApi(server.url("/").toString(), tokenProvider = { "access-token" })

            val commands = api.pendingDeviceCommands("HEADSET_001", limit = 99).data
            api.acknowledgeDeviceCommand(
                commands.single().commandId,
                DeviceCommandAckRequestDto("HEADSET_001", "ACKED", "设备已执行并确认")
            )

            val pull = server.takeRequest()
            val ack = server.takeRequest()
            assertEquals("/api/v1/devices/HEADSET_001/commands/pending?limit=50", pull.path)
            assertEquals("/api/v1/devices/commands/CMD-1/ack", ack.path)
            assertTrue(ack.body.readUtf8().contains("\"status\":\"ACKED\""))
            assertEquals("Bearer access-token", ack.getHeader("Authorization"))
        }
    }

    @Test
    fun versionCheckResolvesBackendRelativeDownloadUrl() = runTest {
        val server = MockWebServer()
        server.use {
            server.enqueue(
                MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
                    envelope(
                        """{"latestVersionCode":3,"latestVersionName":"1.2.0","forceUpdate":true,"changelog":["fix"],"downloadUrl":"/files/FILE-3/download","sha256":"abc"}"""
                    )
                )
            )
            val api = OkHttpPatrolRestApi(server.url("/gateway/").toString(), tokenProvider = { "access-token" })

            val result = api.checkVersion(1).data

            assertEquals(server.url("/files/FILE-3/download").toString(), result.downloadUrl)
            assertEquals("/gateway/api/v1/version/check?currentVersionCode=1", server.takeRequest().path)
        }
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
