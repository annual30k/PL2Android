package com.patrollink.data.edge

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class OkHttpCerebellumApi(
    private val baseUrl: String,
    private val apiKeyProvider: () -> String? = { null },
    private val client: OkHttpClient = defaultClient(),
    private val reportClient: OkHttpClient = defaultReportClient(),
    private val gson: Gson = Gson()
) : CerebellumApi {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val endpoint = baseUrl.trimEnd('/').toHttpUrl()

    init {
        require(endpoint.isHttps || endpoint.host.isLocalDevelopmentHost() || endpoint.host.isPrivateLanHost()) {
            "Cerebellum baseUrl must use HTTPS outside local development or private device LAN"
        }
    }

    override suspend fun health(): CerebellumHealthDto =
        get("health")

    override suspend fun deviceStatus(): CerebellumDeviceStatusDto =
        get("api/v1/device/status")

    override suspend fun certificateStatus(): CerebellumCertificateStatusDto =
        get("api/v1/security/certificates")

    override suspend fun analyzeObject(request: CerebellumObjectAnalyzeRequestDto): CerebellumObjectAnalyzeResponseDto =
        post("api/v1/analyze/object", request)

    override suspend fun transcribeAudio(request: CerebellumAsrTranscribeRequestDto): CerebellumAsrTranscribeResponseDto =
        post("api/v1/asr/transcribe", request)

    override suspend fun registerEvidence(request: CerebellumEvidenceRegisterRequestDto): CerebellumEvidenceRegisterResponseDto =
        post("api/v1/evidence", request)

    override suspend fun listEvidence(): CerebellumEvidenceListResponseDto =
        get("api/v1/evidence")

    override suspend fun createSyncTask(request: CerebellumSyncTaskRequestDto): CerebellumSyncTaskResponseDto =
        post("api/v1/sync/tasks", request)

    override suspend fun listSyncTasks(): CerebellumSyncTaskListResponseDto =
        get("api/v1/sync/tasks")

    override suspend fun runSyncTask(taskId: String): CerebellumSyncTaskResponseDto =
        post("api/v1/sync/tasks/${taskId.pathId()}/run", emptyMap<String, String>())

    override suspend fun summarizeVideo(request: CerebellumVideoSummaryRequestDto): CerebellumVideoSummaryResponseDto =
        post("api/v1/video/summary", request)

    override suspend fun createReport(request: CerebellumReportRequestDto): CerebellumReportResponseDto =
        post("api/v1/llm/report", request, reportClient)

    private suspend inline fun <reified T> get(path: String): T = execute("GET", path, null)

    private suspend inline fun <reified T> post(path: String, body: Any): T =
        post(path, body, client)

    private suspend inline fun <reified T> post(path: String, body: Any, callClient: OkHttpClient): T =
        execute("POST", path, gson.toJson(body), callClient)

    private suspend inline fun <reified T> execute(
        method: String,
        path: String,
        bodyJson: String?,
        callClient: OkHttpClient = client
    ): T {
        val body = bodyJson?.toRequestBody(jsonMediaType)
        val builder = Request.Builder()
            .url(urlFor(path))
            .method(method, if (method == "GET") null else body ?: ByteArray(0).toRequestBody(jsonMediaType))
            .header("Accept", "application/json")
        apiKeyProvider()?.takeIf { it.isNotBlank() }?.let { builder.header("X-API-Key", it) }
        return executeRequest(callClient, builder.build(), object : TypeToken<T>() {}.type)
    }

    private suspend fun <T> executeRequest(callClient: OkHttpClient, request: Request, type: java.lang.reflect.Type): T =
        withContext(Dispatchers.IO) {
            callClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("Cerebellum HTTP ${response.code}: $raw")
                }
                gson.fromJson(raw, type)
            }
        }

    private fun urlFor(path: String): HttpUrl {
        val pathPart = path.substringBefore("?")
        val queryPart = path.substringAfter("?", missingDelimiterValue = "")
        return endpoint.newBuilder()
            .addPathSegments(pathPart)
            .apply { if (queryPart.isNotBlank()) encodedQuery(queryPart) }
            .build()
    }

    private fun String.pathId(): String {
        require(matches(Regex("[A-Za-z0-9_.:-]+"))) { "invalid path id" }
        return this
    }

    private fun String.isLocalDevelopmentHost(): Boolean =
        this == "localhost" || this == "127.0.0.1" || this == "::1" || this == "10.0.2.2"

    private fun String.isPrivateLanHost(): Boolean {
        val parts = split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 ||
            (parts[0] == 172 && parts[1] in 16..31) ||
            (parts[0] == 192 && parts[1] == 168)
    }

    private companion object {
        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .build()

        fun defaultReportClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(240, TimeUnit.SECONDS)
                .callTimeout(260, TimeUnit.SECONDS)
                .build()
    }
}
