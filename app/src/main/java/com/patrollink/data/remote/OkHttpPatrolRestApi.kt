package com.patrollink.data.remote

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl

class OkHttpPatrolRestApi(
    private val baseUrl: String,
    private val tokenProvider: () -> String? = { null },
    private val client: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson()
) : PatrolRestApi {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val endpoint = baseUrl.trimEnd('/').toHttpUrl()

    init {
        require(endpoint.isHttps || endpoint.host in setOf("localhost", "127.0.0.1", "::1")) {
            "REST baseUrl must use HTTPS outside local development"
        }
    }

    override suspend fun login(request: LoginRequestDto): ApiEnvelope<AuthSessionDto> =
        post("api/v1/auth/login", request)

    override suspend fun refresh(refreshToken: String): ApiEnvelope<AuthSessionDto> =
        post("api/v1/auth/refresh", mapOf("refreshToken" to refreshToken))

    override suspend fun currentUser(): ApiEnvelope<UserProfileDto> =
        get("api/v1/users/me")

    override suspend fun scanDevices(): ApiEnvelope<List<ScannedDeviceDto>> =
        get("api/v1/devices/scan")

    override suspend fun bindDevice(deviceId: String): ApiEnvelope<DeviceStatusDto> =
        post("api/v1/devices/${deviceId.pathId()}/bind", emptyMap<String, String>())

    override suspend fun sendDeviceCommand(deviceId: String, request: DeviceCommandRequestDto): ApiEnvelope<DeviceStatusDto> =
        post("api/v1/devices/${deviceId.pathId()}/commands", request)

    override suspend fun alerts(page: Int, pageSize: Int): ApiEnvelope<PageEnvelope<AlertDto>> =
        get("api/v1/alerts?page=$page&pageSize=$pageSize")

    override suspend fun acknowledgeAlert(alertId: String): ApiEnvelope<AlertDto> =
        post("api/v1/alerts/${alertId.pathId()}/ack", emptyMap<String, String>())

    override suspend fun closeAlert(alertId: String, request: AlertCloseRequestDto): ApiEnvelope<AlertDto> =
        post("api/v1/alerts/${alertId.pathId()}/close", request)

    override suspend fun mediaFiles(local: Boolean, page: Int, pageSize: Int): ApiEnvelope<PageEnvelope<MediaFileDto>> =
        get("api/v1/media?side=${if (local) "PHONE" else "DEVICE"}&page=$page&pageSize=$pageSize")

    override suspend fun transferMedia(fileId: String, request: TransferRequestDto): List<ApiEnvelope<MediaFileDto>> =
        post("api/v1/media/${fileId.pathId()}/transfer", request)

    override suspend fun deleteMedia(fileId: String, storageSide: String): ApiEnvelope<Boolean> =
        delete("api/v1/media/${fileId.pathId()}?side=$storageSide")

    override suspend fun verifyMedia(fileId: String): ApiEnvelope<Boolean> =
        post("api/v1/media/${fileId.pathId()}/verify", emptyMap<String, String>())

    override suspend fun heartbeat(request: HeartbeatRequestDto): ApiEnvelope<HeartbeatAckDto> =
        post("api/v1/realtime/heartbeat", request)

    override suspend fun startStream(request: StreamRelayRequestDto): ApiEnvelope<StreamRelayStateDto> =
        post("api/v1/stream/start", request)

    override suspend fun stopStream(): ApiEnvelope<StreamRelayStateDto> =
        post("api/v1/stream/stop", emptyMap<String, String>())

    override suspend fun activateSos(location: GpsLocationDto): ApiEnvelope<SosEventDto> =
        post("api/v1/sos/activate", location)

    override suspend fun cancelSos(): ApiEnvelope<SosEventDto> =
        post("api/v1/sos/cancel", emptyMap<String, String>())

    private suspend inline fun <reified T> get(path: String): T = execute("GET", path, null)

    private suspend inline fun <reified T> post(path: String, body: Any): T =
        execute("POST", path, gson.toJson(body))

    private suspend inline fun <reified T> delete(path: String): T = execute("DELETE", path, null)

    private suspend inline fun <reified T> execute(method: String, path: String, bodyJson: String?): T {
        val body = bodyJson?.toRequestBody(jsonMediaType)
        val builder = Request.Builder()
            .url(urlFor(path))
            .method(method, if (method == "GET") null else body ?: ByteArray(0).toRequestBody(jsonMediaType))
            .header("Accept", "application/json")
        tokenProvider()?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
        val request = builder.build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}: $raw")
                }
                gson.fromJson(raw, object : TypeToken<T>() {}.type)
            }
        }
    }

    private fun urlFor(path: String): okhttp3.HttpUrl {
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
}
