package com.patrollink.data.edge

import kotlinx.coroutines.test.runTest
import com.google.gson.JsonParser
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpCerebellumApiTest {
    @Test
    fun clientSendsApiKeyAndParsesDeviceStatus() = runTest {
        val server = MockWebServer()
        server.use {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "device_id":"PL-CB-SIM-0001",
                          "profile":"production-sim",
                          "accelerator":"jetson-orin-nx-16gb",
                          "target_platform":"NVIDIA Jetson Orin NX 16GB",
                          "linux_hardening":{},
                          "resources":{"battery_percent":92},
                          "models":{"primary":"Qwen3.5-4B-INT4"},
                          "security":{"api_key_required":true},
                          "streaming":{"max_sources":2}
                        }
                        """.trimIndent()
                    )
            )

            val api = OkHttpCerebellumApi(
                baseUrl = server.url("/").toString(),
                apiKeyProvider = { "edge-key" }
            )
            val status = api.deviceStatus()
            val request = server.takeRequest()

            assertEquals("PL-CB-SIM-0001", status.deviceId)
            assertEquals("/api/v1/device/status", request.path)
            assertEquals("edge-key", request.getHeader("X-API-Key"))
        }
    }

    @Test
    fun objectAnalyzeUsesSnakeCasePayload() = runTest {
        val server = MockWebServer()
        server.use {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "result":{
                            "backend":"simulated-fallback",
                            "model":"yolov8n.pt",
                            "frame_id":"frame-1",
                            "camera_id":"bodycam-01",
                            "generated_at":"2026-05-14T08:00:00Z",
                            "detections":[{"label":"person","confidence":0.88,"box":[1,2,3,4],"result_type":"object_candidate"}],
                            "detection_count":1,
                            "requires_human_confirmation":true
                          },
                          "event":{
                            "event_id":"evt-1",
                            "event_type":"object_candidate",
                            "created_at":"2026-05-14T08:00:00Z",
                            "payload":{},
                            "human_status":"unconfirmed"
                          }
                        }
                        """.trimIndent()
                    )
            )

            val api = OkHttpCerebellumApi(baseUrl = server.url("/").toString())
            val response = api.analyzeObject(
                CerebellumObjectAnalyzeRequestDto(
                    frameId = "frame-1",
                    cameraId = "bodycam-01",
                    imageUri = "scene.jpg",
                    targetClasses = listOf("person")
                )
            )
            val request = server.takeRequest()
            val body = request.body.readUtf8()

            assertEquals("/api/v1/analyze/object", request.path)
            assertEquals(1, response.result.detectionCount)
            assertEquals("person", response.result.detections.first().label)
            assertTrue(body.contains("frame_id"))
            assertTrue(body.contains("target_classes"))
        }
    }

    @Test
    fun cloudWatchlistRecognitionPostsOneCombinedRequestAndParsesDirectResult() = runTest {
        val server = MockWebServer()
        val response = """
            {
              "request_id":"IMG-1",
              "frame_id":"IMG-1",
              "elapsed_ms":681,
              "plate":{
                "backend":"hyperlpr3",
                "candidates":[{"plate_number":"闽A12345","confidence":0.93}],
                "candidate_count":1
              },
              "face":{
                "backend":"opencv-zoo-yunet+sface",
                "faces":[{"candidate":{"person_id":"PERSON-1","display_name":"测试人员","similarity":0.88}}],
                "face_count":1,
                "candidate_count":1
              },
              "alerts":[{
                "alert_id":"FACE-IMG-1-PERSON-1",
                "device_id":"HEADSET_001",
                "person_id":"PERSON-1",
                "display_name":"测试人员",
                "backend_delivery_status":"QUEUED",
                "backend_outbox_id":"face:FACE-IMG-1-PERSON-1"
              }],
              "platform_delivery":"QUEUED",
              "requires_human_confirmation":true,
              "event":{
                "event_id":"evt-vision-1",
                "event_type":"vision_analysis_completed",
                "created_at":"2026-07-19T08:00:00Z",
                "payload":{},
                "human_status":"unconfirmed"
              }
            }
        """.trimIndent()
        server.use {
            server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(response))
            val api = OkHttpCerebellumApi(server.url("/").toString(), apiKeyProvider = { "cloud-key" })

            val parsed = api.analyzeVision(
                CerebellumCombinedVisionAnalyzeRequestDto(
                    frameId = "IMG-1",
                    cameraId = "HEADSET_001",
                    imageUri = "/cloud/IMG-1.jpg",
                    deviceId = "HEADSET_001"
                )
            )

            val request = server.takeRequest()
            val body = request.body.readUtf8()
            assertEquals("/api/v1/analyze/vision", request.path)
            assertTrue(body.contains("\"image_uri\":\"/cloud/IMG-1.jpg\""))
            assertTrue(body.contains("\"candidate_library\":\"backend-authorized-watchlist\""))
            assertTrue(body.contains("\"device_id\":\"HEADSET_001\""))
            assertEquals("cloud-key", request.getHeader("X-API-Key"))
            assertEquals("闽A12345", parsed.plate.candidates.single().plateNumber)
            assertEquals("测试人员", parsed.face.faces.single().candidate?.displayName)
            assertEquals("QUEUED", parsed.platformDelivery)
            assertEquals("QUEUED", parsed.alerts.single().backendDeliveryStatus)
            assertEquals(681, parsed.elapsedMs)
        }
    }

    @Test
    fun createReportPostsDailyReportRequestAndParsesContent() = runTest {
        val server = MockWebServer()
        server.use {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "report":{
                            "mission_id":"mission-20260515-POLICE_9527",
                            "report_type":"daily",
                            "model":"Qwen3.5-4B-Q4_K_M",
                            "context_tokens":8192,
                            "max_context_tokens":32768,
                            "generated_at":"2026-05-15T08:00:00Z",
                            "content":"今日巡逻日报草稿",
                            "requires_human_confirmation":true,
                            "backend":"llama.cpp"
                          },
                          "event":{
                            "event_id":"evt-report-1",
                            "event_type":"report_generated",
                            "created_at":"2026-05-15T08:00:00Z",
                            "payload":{},
                            "human_status":"unconfirmed"
                          }
                        }
                        """.trimIndent()
                    )
            )

            val api = OkHttpCerebellumApi(baseUrl = server.url("/").toString())
            val response = api.createReport(
                CerebellumReportRequestDto(
                    missionId = "mission-20260515-POLICE_9527",
                    reportType = "daily",
                    preferQuality = true,
                    operatorNote = "重点巡逻商业街",
                    maxTokens = 1200
                )
            )
            val request = server.takeRequest()
            val body = request.body.readUtf8()
            val json = JsonParser.parseString(body).asJsonObject

            assertEquals("/api/v1/llm/report", request.path)
            assertEquals("今日巡逻日报草稿", response.report.content)
            assertEquals("llama.cpp", response.report.backend)
            assertEquals("mission-20260515-POLICE_9527", json["mission_id"].asString)
            assertEquals("daily", json["report_type"].asString)
            assertTrue(json["prefer_quality"].asBoolean)
            assertEquals("重点巡逻商业街", json["operator_note"].asString)
            assertEquals(1200, json["max_tokens"].asInt)
        }
    }
}
