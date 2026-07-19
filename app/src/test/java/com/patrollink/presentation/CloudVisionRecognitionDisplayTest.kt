package com.patrollink.presentation

import com.google.gson.JsonParser
import com.patrollink.data.edge.CerebellumCombinedVisionAnalyzeResponseDto
import com.patrollink.data.edge.CerebellumEventDto
import com.patrollink.data.edge.CerebellumFaceCandidateDto
import com.patrollink.data.edge.CerebellumFaceDetectionDto
import com.patrollink.data.edge.CerebellumFaceRecognitionResultDto
import com.patrollink.data.edge.CerebellumPlateCandidateDto
import com.patrollink.data.edge.CerebellumPlateRecognitionResultDto
import com.patrollink.data.edge.CerebellumWatchlistAlertDto
import com.patrollink.domain.DeviceEventLevel
import com.patrollink.domain.OperationMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudVisionRecognitionDisplayTest {
    @Test
    fun directResultShowsPlateFaceConfidenceAndAsyncPlatformState() {
        val response = response(
            plate = CerebellumPlateRecognitionResultDto(
                backend = "hyperlpr3",
                candidates = listOf(CerebellumPlateCandidateDto(plateNumber = "闽A12345", confidence = 0.93)),
            ),
            face = CerebellumFaceRecognitionResultDto(
                backend = "opencv-zoo-yunet+sface",
                faces = listOf(
                    CerebellumFaceDetectionDto(
                        candidate = CerebellumFaceCandidateDto(
                            personId = "PERSON-1",
                            displayName = "测试人员",
                            similarity = 0.88,
                        )
                    )
                ),
                faceCount = 1,
                candidateCount = 1,
            ),
            alerts = listOf(CerebellumWatchlistAlertDto(alertId = "FACE-1", personId = "PERSON-1")),
            platformDelivery = "QUEUED",
        )

        val display = response.toRecognitionDisplay("现场照片.jpg")

        assertEquals("云端识别命中布控", display.title)
        assertEquals(DeviceEventLevel.Warning, display.level)
        assertEquals(OperationMessageType.Warning, display.messageType)
        assertTrue(display.detail.contains("闽A12345（93%）"))
        assertTrue(display.detail.contains("测试人员（88%）"))
        assertTrue(display.detail.contains("后台同步队列"))
        assertTrue(display.detail.contains("耗时 681ms"))
    }

    @Test
    fun fallbackCandidatesAreNeverPresentedAsRealRecognition() {
        val response = response(
            plate = CerebellumPlateRecognitionResultDto(
                backend = "simulated-fallback",
                candidates = listOf(CerebellumPlateCandidateDto(plateNumber = "京A00000", confidence = 0.99)),
            ),
            face = CerebellumFaceRecognitionResultDto(
                backend = "simulated-fallback",
                faces = listOf(
                    CerebellumFaceDetectionDto(
                        candidate = CerebellumFaceCandidateDto(displayName = "模拟人员", similarity = 0.99)
                    )
                ),
                faceCount = 1,
                candidateCount = 1,
            ),
        )

        val display = response.toRecognitionDisplay("现场照片.jpg")

        assertTrue(display.detail.contains("车牌算法暂不可用"))
        assertTrue(display.detail.contains("人脸算法暂不可用"))
        assertFalse(display.detail.contains("京A00000"))
        assertFalse(display.detail.contains("模拟人员"))
    }

    private fun response(
        plate: CerebellumPlateRecognitionResultDto,
        face: CerebellumFaceRecognitionResultDto,
        alerts: List<CerebellumWatchlistAlertDto> = emptyList(),
        platformDelivery: String = "NOT_REQUIRED",
    ) = CerebellumCombinedVisionAnalyzeResponseDto(
        requestId = "IMG-1",
        frameId = "IMG-1",
        elapsedMs = 681,
        plate = plate,
        face = face,
        alerts = alerts,
        platformDelivery = platformDelivery,
        event = CerebellumEventDto(
            eventId = "evt-1",
            eventType = "vision_analysis_completed",
            createdAt = "2026-07-19T08:00:00Z",
            payload = JsonParser.parseString("{}"),
            humanStatus = "unconfirmed",
        ),
    )
}
