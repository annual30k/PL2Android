package com.patrollink.data.edge

import kotlinx.coroutines.test.runTest
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
}
