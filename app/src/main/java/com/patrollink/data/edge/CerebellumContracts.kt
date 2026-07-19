package com.patrollink.data.edge

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import java.io.File

interface CerebellumApi {
    suspend fun health(): CerebellumHealthDto
    suspend fun deviceStatus(): CerebellumDeviceStatusDto
    suspend fun certificateStatus(): CerebellumCertificateStatusDto
    suspend fun analyzeObject(request: CerebellumObjectAnalyzeRequestDto): CerebellumObjectAnalyzeResponseDto
    suspend fun analyzePlate(request: CerebellumPlateAnalyzeRequestDto): CerebellumVisionAnalyzeResponseDto = unsupportedCerebellumMethod()
    suspend fun analyzeFace(request: CerebellumFaceAnalyzeRequestDto): CerebellumVisionAnalyzeResponseDto = unsupportedCerebellumMethod()
    suspend fun analyzeVision(request: CerebellumCombinedVisionAnalyzeRequestDto): CerebellumCombinedVisionAnalyzeResponseDto = unsupportedCerebellumMethod()
    suspend fun transcribeAudio(request: CerebellumAsrTranscribeRequestDto): CerebellumAsrTranscribeResponseDto
    suspend fun registerEvidence(request: CerebellumEvidenceRegisterRequestDto): CerebellumEvidenceRegisterResponseDto
    suspend fun listEvidence(): CerebellumEvidenceListResponseDto
    suspend fun createSyncTask(request: CerebellumSyncTaskRequestDto): CerebellumSyncTaskResponseDto
    suspend fun listSyncTasks(): CerebellumSyncTaskListResponseDto
    suspend fun runSyncTask(taskId: String): CerebellumSyncTaskResponseDto
    suspend fun summarizeVideo(request: CerebellumVideoSummaryRequestDto): CerebellumVideoSummaryResponseDto
    suspend fun createReport(request: CerebellumReportRequestDto): CerebellumReportResponseDto
    suspend fun uploadFile(
        file: File,
        missionId: String? = null,
        evidenceType: String = "other",
        note: String? = null,
        register: Boolean = true
    ): CerebellumFileUploadResponseDto = unsupportedCerebellumMethod()
    suspend fun listFiles(): CerebellumFileListResponseDto = unsupportedCerebellumMethod()
    suspend fun operateFile(fileName: String, request: CerebellumFileOperationRequestDto): CerebellumFileOperationResponseDto = unsupportedCerebellumMethod()
    suspend fun sendCommand(request: CerebellumCommandRequestDto): CerebellumCommandResponseDto = unsupportedCerebellumMethod()
}

private fun unsupportedCerebellumMethod(): Nothing = error("Cerebellum API method is not implemented")

data class CerebellumHealthDto(
    val status: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("uptime_seconds") val uptimeSeconds: Long,
    @SerializedName("primary_model") val primaryModel: String
)

data class CerebellumDeviceStatusDto(
    @SerializedName("device_id") val deviceId: String,
    val profile: String,
    val accelerator: String,
    @SerializedName("target_platform") val targetPlatform: String,
    val resources: JsonElement,
    val models: JsonElement,
    val security: JsonElement,
    val streaming: JsonElement
)

data class CerebellumCertificateStatusDto(
    @SerializedName("mtls_required") val mtlsRequired: Boolean,
    @SerializedName("mtls_ready") val mtlsReady: Boolean,
    @SerializedName("api_key_required") val apiKeyRequired: Boolean,
    val mode: String,
    val note: String
)

data class CerebellumEventDto(
    @SerializedName("event_id") val eventId: String,
    @SerializedName("event_type") val eventType: String,
    @SerializedName("created_at") val createdAt: String,
    val payload: JsonElement?,
    @SerializedName("human_status") val humanStatus: String
)

data class CerebellumObjectAnalyzeRequestDto(
    @SerializedName("frame_id") val frameId: String,
    @SerializedName("camera_id") val cameraId: String = "bodycam-01",
    @SerializedName("image_uri") val imageUri: String? = null,
    @SerializedName("confidence_threshold") val confidenceThreshold: Float = 0.35f,
    @SerializedName("target_classes") val targetClasses: List<String>? = null
)

data class CerebellumObjectAnalyzeResponseDto(
    val result: CerebellumObjectResultDto,
    val event: CerebellumEventDto
)

data class CerebellumPlateAnalyzeRequestDto(
    @SerializedName("frame_id") val frameId: String,
    @SerializedName("camera_id") val cameraId: String = "patrol-mobile",
    @SerializedName("image_uri") val imageUri: String,
    @SerializedName("device_id") val deviceId: String? = null
)

data class CerebellumFaceAnalyzeRequestDto(
    @SerializedName("frame_id") val frameId: String,
    @SerializedName("camera_id") val cameraId: String = "patrol-mobile",
    @SerializedName("candidate_library") val candidateLibrary: String = "backend-authorized-watchlist",
    @SerializedName("image_uri") val imageUri: String,
    @SerializedName("device_id") val deviceId: String? = null
)

data class CerebellumVisionAnalyzeResponseDto(
    val result: JsonElement,
    val event: CerebellumEventDto,
    val alerts: List<CerebellumWatchlistAlertDto> = emptyList()
)

data class CerebellumCombinedVisionAnalyzeRequestDto(
    @SerializedName("frame_id") val frameId: String,
    @SerializedName("camera_id") val cameraId: String = "patrol-mobile",
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("candidate_library") val candidateLibrary: String = "backend-authorized-watchlist",
    @SerializedName("image_uri") val imageUri: String
)

data class CerebellumCombinedVisionAnalyzeResponseDto(
    @SerializedName("request_id") val requestId: String,
    @SerializedName("frame_id") val frameId: String,
    @SerializedName("elapsed_ms") val elapsedMs: Long = 0,
    val plate: CerebellumPlateRecognitionResultDto,
    val face: CerebellumFaceRecognitionResultDto,
    val alerts: List<CerebellumWatchlistAlertDto> = emptyList(),
    @SerializedName("platform_delivery") val platformDelivery: String = "NOT_REQUIRED",
    @SerializedName("requires_human_confirmation") val requiresHumanConfirmation: Boolean = true,
    val event: CerebellumEventDto
)

data class CerebellumPlateRecognitionResultDto(
    val backend: String,
    val candidates: List<CerebellumPlateCandidateDto> = emptyList(),
    @SerializedName("candidate_count") val candidateCount: Int = candidates.size
)

data class CerebellumPlateCandidateDto(
    @SerializedName("plate_number") val plateNumber: String? = null,
    val confidence: Double? = null,
    @SerializedName("plate_type") val plateType: String? = null,
    @SerializedName("vehicle_type") val vehicleType: String? = null
)

data class CerebellumFaceRecognitionResultDto(
    val backend: String,
    val faces: List<CerebellumFaceDetectionDto> = emptyList(),
    @SerializedName("face_count") val faceCount: Int = faces.size,
    @SerializedName("candidate_count") val candidateCount: Int = 0
)

data class CerebellumFaceDetectionDto(
    val candidate: CerebellumFaceCandidateDto? = null,
    @SerializedName("quality_score") val qualityScore: Double? = null
)

data class CerebellumFaceCandidateDto(
    @SerializedName("person_id") val personId: String? = null,
    @SerializedName("candidate_id") val candidateId: String? = null,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("risk_level") val riskLevel: String? = null,
    val category: String? = null,
    val similarity: Double? = null
)

data class CerebellumWatchlistAlertDto(
    @SerializedName("alert_id") val alertId: String = "",
    @SerializedName("backend_delivery_status") val backendDeliveryStatus: String? = null,
    @SerializedName("backend_outbox_id") val backendOutboxId: String? = null,
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("person_id") val personId: String? = null,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("plate_number") val plateNumber: String? = null,
    val confidence: Double? = null,
    @SerializedName("risk_level") val riskLevel: String? = null
)

data class CerebellumObjectResultDto(
    val backend: String,
    val model: String,
    @SerializedName("frame_id") val frameId: String,
    @SerializedName("camera_id") val cameraId: String,
    @SerializedName("generated_at") val generatedAt: String,
    val detections: List<CerebellumDetectionDto>,
    @SerializedName("detection_count") val detectionCount: Int,
    @SerializedName("requires_human_confirmation") val requiresHumanConfirmation: Boolean
)

data class CerebellumDetectionDto(
    val label: String,
    val confidence: Float,
    val box: List<Float>,
    @SerializedName("result_type") val resultType: String
)

data class CerebellumAsrTranscribeRequestDto(
    @SerializedName("audio_uri") val audioUri: String,
    @SerializedName("mission_id") val missionId: String? = null,
    val language: String = "zh",
    @SerializedName("operator_note") val operatorNote: String? = null,
    @SerializedName("max_tokens") val maxTokens: Int = 1000
)

data class CerebellumAsrTranscribeResponseDto(
    val transcript: CerebellumTranscriptDto,
    val event: CerebellumEventDto
)

data class CerebellumTranscriptDto(
    @SerializedName("mission_id") val missionId: String?,
    @SerializedName("audio_uri") val audioUri: String,
    @SerializedName("generated_at") val generatedAt: String,
    val language: String,
    @SerializedName("duration_seconds") val durationSeconds: Float?,
    val transcript: String,
    val backend: String,
    val model: String,
    @SerializedName("requires_human_confirmation") val requiresHumanConfirmation: Boolean
)

data class CerebellumEvidenceRegisterRequestDto(
    @SerializedName("file_uri") val fileUri: String,
    @SerializedName("evidence_type") val evidenceType: String = "video",
    @SerializedName("mission_id") val missionId: String? = null,
    val encrypt: Boolean? = null,
    val note: String? = null
)

data class CerebellumEvidenceRegisterResponseDto(
    val evidence: CerebellumEvidenceDto,
    val event: CerebellumEventDto
)

data class CerebellumEvidenceListResponseDto(
    val count: Int,
    val items: List<CerebellumEvidenceDto>
)

data class CerebellumEvidenceDto(
    @SerializedName("evidence_id") val evidenceId: String,
    @SerializedName("mission_id") val missionId: String?,
    @SerializedName("evidence_type") val evidenceType: String,
    @SerializedName("source_uri") val sourceUri: String,
    @SerializedName("source_name") val sourceName: String,
    @SerializedName("source_sha256") val sourceSha256: String,
    @SerializedName("stored_sha256") val storedSha256: String,
    @SerializedName("size_bytes") val sizeBytes: Long,
    @SerializedName("registered_at") val registeredAt: String,
    @SerializedName("storage_mode") val storageMode: String,
    val encryption: String?,
    @SerializedName("chain_status") val chainStatus: String
)

data class CerebellumUploadedFileDto(
    @SerializedName("file_id") val fileId: String,
    @SerializedName("file_name") val fileName: String,
    @SerializedName("file_uri") val fileUri: String,
    @SerializedName("size_bytes") val sizeBytes: Long,
    val sha256: String,
    @SerializedName("uploaded_at") val uploadedAt: String,
    @SerializedName("download_url") val downloadUrl: String
)

data class CerebellumFileUploadResponseDto(
    val file: CerebellumUploadedFileDto,
    val evidence: CerebellumEvidenceDto?,
    val event: CerebellumEventDto
)

data class CerebellumFileListResponseDto(
    val count: Int,
    val files: List<CerebellumUploadedFileDto>
)

data class CerebellumFileOperationRequestDto(
    val operation: String,
    @SerializedName("mission_id") val missionId: String? = null,
    @SerializedName("evidence_type") val evidenceType: String = "other",
    val note: String? = null
)

data class CerebellumFileOperationResponseDto(
    val result: JsonElement,
    val event: CerebellumEventDto
)

data class CerebellumCommandRequestDto(
    val command: String,
    @SerializedName("request_id") val requestId: String? = null,
    @SerializedName("operator_id") val operatorId: String? = null,
    val payload: Map<String, Any?> = emptyMap()
)

data class CerebellumCommandResponseDto(
    val accepted: Boolean,
    val result: JsonElement,
    val event: CerebellumEventDto
)

data class CerebellumSyncTaskRequestDto(
    @SerializedName("mission_id") val missionId: String? = null,
    @SerializedName("destination_url") val destinationUrl: String? = null,
    @SerializedName("include_events") val includeEvents: Boolean = true,
    @SerializedName("include_audit") val includeAudit: Boolean = false,
    @SerializedName("event_limit") val eventLimit: Int = 100
)

data class CerebellumSyncTaskResponseDto(
    val task: CerebellumSyncTaskDto,
    val event: CerebellumEventDto
)

data class CerebellumSyncTaskListResponseDto(
    val count: Int,
    val tasks: List<CerebellumSyncTaskDto>
)

data class CerebellumSyncTaskDto(
    @SerializedName("task_id") val taskId: String,
    @SerializedName("mission_id") val missionId: String?,
    @SerializedName("destination_url") val destinationUrl: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    val status: String,
    val attempts: Int,
    @SerializedName("last_error") val lastError: String?
)

data class CerebellumVideoSummaryRequestDto(
    @SerializedName("mission_id") val missionId: String,
    @SerializedName("stream_id") val streamId: String? = null,
    @SerializedName("operator_note") val operatorNote: String? = null,
    @SerializedName("event_limit") val eventLimit: Int = 100,
    @SerializedName("use_llm") val useLlm: Boolean = false,
    @SerializedName("max_tokens") val maxTokens: Int = 800
)

data class CerebellumVideoSummaryResponseDto(
    val summary: JsonElement,
    val event: CerebellumEventDto
)

data class CerebellumReportRequestDto(
    @SerializedName("mission_id") val missionId: String,
    @SerializedName("report_type") val reportType: String = "daily",
    @SerializedName("prefer_quality") val preferQuality: Boolean = false,
    @SerializedName("operator_note") val operatorNote: String? = null,
    @SerializedName("selected_media_ids") val selectedMediaIds: List<String> = emptyList(),
    @SerializedName("selected_media_uris") val selectedMediaUris: List<String> = emptyList(),
    @SerializedName("include_today_media_default") val includeTodayMediaDefault: Boolean = true,
    @SerializedName("submit_to_backend") val submitToBackend: Boolean = true,
    @SerializedName("operator_id") val operatorId: String? = null,
    @SerializedName("officer_name") val officerName: String? = null,
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("max_tokens") val maxTokens: Int = 1200
)

data class CerebellumReportResponseDto(
    val report: CerebellumReportDto,
    val event: CerebellumEventDto
)

data class CerebellumReportDto(
    @SerializedName("report_id") val reportId: String? = null,
    @SerializedName("mission_id") val missionId: String,
    @SerializedName("report_type") val reportType: String,
    val model: String,
    @SerializedName("context_tokens") val contextTokens: Int? = null,
    @SerializedName("max_context_tokens") val maxContextTokens: Int? = null,
    @SerializedName("generated_at") val generatedAt: String,
    val content: String,
    @SerializedName("requires_human_confirmation") val requiresHumanConfirmation: Boolean,
    val backend: String
)
