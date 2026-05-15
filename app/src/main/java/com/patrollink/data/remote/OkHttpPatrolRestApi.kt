package com.patrollink.data.remote

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.BufferedSink
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.math.min

class OkHttpPatrolRestApi(
    private val baseUrl: String,
    private val tokenProvider: () -> String? = { null },
    private val clientIdProvider: () -> String? = { DEFAULT_CLIENT_ID },
    private val client: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson(),
    private val resumableChunkSizeBytes: Long = DEFAULT_CHUNK_SIZE_BYTES
) : PatrolRestApi {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val endpoint = baseUrl.trimEnd('/').toHttpUrl()

    init {
        require(endpoint.isHttps || endpoint.host.isLocalDevelopmentHost() || endpoint.host.isPrivateLanHost()) {
            "REST baseUrl must use HTTPS outside local development or private device LAN"
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

    override suspend fun deviceCapabilities(deviceId: String): ApiEnvelope<DeviceCapabilitiesDto> =
        get("api/v1/devices/${deviceId.pathId()}/capabilities")

    override suspend fun deviceWifi(deviceId: String): ApiEnvelope<DeviceWifiStateDto> =
        get("api/v1/devices/${deviceId.pathId()}/wifi")

    override suspend fun configureWifi(deviceId: String, request: DeviceWifiStateDto): ApiEnvelope<DeviceWifiStateDto> =
        post("api/v1/devices/${deviceId.pathId()}/wifi", request)

    override suspend fun applyDeviceSettings(deviceId: String, request: DeviceAdvancedSettingsDto): ApiEnvelope<DeviceAdvancedSettingsDto> =
        post("api/v1/devices/${deviceId.pathId()}/settings", request)

    override suspend fun startRealtimeAudioSync(deviceId: String): ApiEnvelope<DeviceControlResultDto> =
        post("api/v1/devices/${deviceId.pathId()}/realtime-audio/start", emptyMap<String, String>())

    override suspend fun stopRealtimeAudioSync(deviceId: String): ApiEnvelope<DeviceControlResultDto> =
        post("api/v1/devices/${deviceId.pathId()}/realtime-audio/stop", emptyMap<String, String>())

    override suspend fun notifyMediaSyncCompleted(deviceId: String): ApiEnvelope<DeviceControlResultDto> =
        post("api/v1/devices/${deviceId.pathId()}/media-sync/completed", emptyMap<String, String>())

    override suspend fun alerts(page: Int, pageSize: Int): ApiEnvelope<PageEnvelope<AlertDto>> =
        get("api/v1/alerts?page=$page&pageSize=$pageSize")

    override suspend fun acknowledgeAlert(alertId: String): ApiEnvelope<AlertDto> =
        post("api/v1/alerts/${alertId.pathId()}/ack", emptyMap<String, String>())

    override suspend fun closeAlert(alertId: String, request: AlertCloseRequestDto): ApiEnvelope<AlertDto> =
        post("api/v1/alerts/${alertId.pathId()}/close", request)

    override suspend fun mediaFiles(local: Boolean, page: Int, pageSize: Int): ApiEnvelope<PageEnvelope<MediaFileDto>> =
        get("api/v1/media?side=${if (local) "PHONE" else "DEVICE"}&page=$page&pageSize=$pageSize")

    override suspend fun uploadMedia(file: File, storageSide: String, bizType: String, bizId: String): ApiEnvelope<MediaFileDto> {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("storageSide", storageSide)
            .apply {
                if (bizType.isNotBlank()) addFormDataPart("bizType", bizType)
                if (bizId.isNotBlank()) addFormDataPart("bizId", bizId)
            }
            .addFormDataPart("file", file.name, file.asRequestBody("application/octet-stream".toMediaType()))
            .build()
        val builder = Request.Builder()
            .url(urlFor("api/v1/media/upload"))
            .post(body)
            .header("Accept", "application/json")
        clientIdProvider()?.takeIf { it.isNotBlank() }?.let { builder.header("clientid", it) }
        tokenProvider()?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
        return executeRequest(builder.build(), object : TypeToken<ApiEnvelope<MediaFileDto>>() {}.type)
    }

    override suspend fun uploadMediaResumable(file: File, storageSide: String, bizType: String, bizId: String): ApiEnvelope<MediaUploadTaskDto> {
        require(file.exists() && file.isFile) { "media file not found: ${file.absolutePath}" }
        val chunkSize = resumableChunkSizeBytes.coerceAtLeast(1L)
        val totalChunks = ceil(file.length().toDouble() / chunkSize.toDouble()).toInt().coerceAtLeast(1)
        val mediaType = mediaType(file.name)
        val create = MediaUploadTaskCreateDto(
            fileName = file.name,
            mediaType = mediaType,
            mimeType = "application/octet-stream",
            fileSizeBytes = file.length(),
            chunkSizeBytes = chunkSize,
            totalChunks = totalChunks,
            sha256 = sha256(file),
            storageSide = storageSide,
            bizType = bizType,
            bizId = bizId
        )
        val task = post<ApiEnvelope<MediaUploadTaskDto>>("api/v1/media/upload-tasks", create).data
        val latest = runCatching {
            post<ApiEnvelope<MediaUploadTaskDto>>("api/v1/media/upload-tasks/${task.taskId.pathId()}/retry", emptyMap<String, String>()).data
        }.getOrElse { task }
        val uploadedIndexes = latest.uploadedChunkIndexes.toMutableSet()
        var current = latest
        for (chunkIndex in 0 until current.totalChunks) {
            if (chunkIndex in uploadedIndexes) continue
            current = uploadMediaChunk(file, current.taskId, chunkIndex, current.chunkSizeBytes).data
            uploadedIndexes += chunkIndex
        }
        return post("api/v1/media/upload-tasks/${current.taskId.pathId()}/complete", emptyMap<String, String>())
    }

    private suspend fun uploadMediaChunk(file: File, taskId: String, chunkIndex: Int, chunkSizeBytes: Long): ApiEnvelope<MediaUploadTaskDto> {
        val offset = chunkIndex.toLong() * chunkSizeBytes
        val byteCount = min(chunkSizeBytes, file.length() - offset)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "chunk",
                "${file.name}.part$chunkIndex",
                FileChunkRequestBody(file, offset, byteCount, "application/octet-stream".toMediaType())
            )
            .build()
        val builder = Request.Builder()
            .url(urlFor("api/v1/media/upload-tasks/${taskId.pathId()}/chunks/$chunkIndex"))
            .post(body)
            .header("Accept", "application/json")
        clientIdProvider()?.takeIf { it.isNotBlank() }?.let { builder.header("clientid", it) }
        tokenProvider()?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
        return executeRequest(builder.build(), object : TypeToken<ApiEnvelope<MediaUploadTaskDto>>() {}.type)
    }

    override suspend fun transferMedia(fileId: String, request: TransferRequestDto): List<ApiEnvelope<MediaFileDto>> =
        post("api/v1/media/${fileId.pathId()}/transfer", request)

    override suspend fun deleteMedia(fileId: String, storageSide: String): ApiEnvelope<Boolean> =
        delete("api/v1/media/${fileId.pathId()}?side=$storageSide")

    override suspend fun verifyMedia(fileId: String): ApiEnvelope<Boolean> =
        post("api/v1/media/${fileId.pathId()}/verify", emptyMap<String, String>())

    override suspend fun heartbeat(request: HeartbeatRequestDto): ApiEnvelope<HeartbeatAckDto> =
        post("api/v1/realtime/heartbeat", request)

    override suspend fun messages(targetId: String, page: Int, pageSize: Int): ApiEnvelope<PageEnvelope<PatrolMessageDto>> =
        get("api/v1/messages?targetId=$targetId&page=$page&pageSize=$pageSize")

    override suspend fun readMessage(messageId: String): ApiEnvelope<PatrolMessageDto> =
        post("api/v1/messages/${messageId.pathId()}/read", emptyMap<String, String>())

    override suspend fun startStream(request: StreamRelayRequestDto): ApiEnvelope<StreamRelayStateDto> =
        post("api/v1/stream/start", request)

    override suspend fun stopStream(): ApiEnvelope<StreamRelayStateDto> =
        post("api/v1/stream/stop", emptyMap<String, String>())

    override suspend fun createIntercomSession(request: IntercomSessionRequestDto): ApiEnvelope<IntercomSessionDto> =
        post("api/v1/intercom/sessions", request)

    override suspend fun pendingIntercomSession(deviceId: String): ApiEnvelope<IntercomSessionDto?> =
        get("api/v1/intercom/sessions/pending?deviceId=${deviceId.pathId()}")

    override suspend fun acceptIntercomSession(sessionId: String): ApiEnvelope<IntercomSessionDto> =
        post("api/v1/intercom/sessions/${sessionId.pathId()}/accept", emptyMap<String, String>())

    override suspend fun closeIntercomSession(sessionId: String): ApiEnvelope<IntercomSessionDto> =
        post("api/v1/intercom/sessions/${sessionId.pathId()}/close", emptyMap<String, String>())

    override suspend fun sendIntercomSignal(sessionId: String, request: IntercomSignalRequestDto): ApiEnvelope<IntercomSignalDto> =
        post("api/v1/intercom/sessions/${sessionId.pathId()}/signals", request)

    override suspend fun intercomSignals(sessionId: String, afterSignalId: String): ApiEnvelope<List<IntercomSignalDto>> =
        get("api/v1/intercom/sessions/${sessionId.pathId()}/signals?afterSignalId=$afterSignalId")

    override suspend fun currentPatrolArea(): ApiEnvelope<PatrolAreaDto> =
        get("api/v1/patrol/areas/current")

    override suspend fun activateSos(location: GpsLocationDto): ApiEnvelope<SosEventDto> =
        post("api/v1/sos/activate", location)

    override suspend fun cancelSos(): ApiEnvelope<SosEventDto> =
        post("api/v1/sos/cancel", emptyMap<String, String>())

    override suspend fun checkVersion(currentVersionCode: Int): ApiEnvelope<VersionCheckResultDto> =
        get("api/v1/version/check?currentVersionCode=$currentVersionCode")

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
        clientIdProvider()?.takeIf { it.isNotBlank() }?.let { builder.header("clientid", it) }
        tokenProvider()?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
        val request = builder.build()
        return executeRequest(request, object : TypeToken<T>() {}.type)
    }

    private suspend fun <T> executeRequest(request: Request, type: java.lang.reflect.Type): T {
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}: $raw")
                }
                val parsed = gson.fromJson<T>(raw, type)
                if (parsed is ApiEnvelope<*> && !parsed.success) {
                    error("API ${parsed.code}: ${parsed.displayMessage}")
                }
                parsed
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

    private fun mediaType(fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".bmp") -> "PHOTO"
            lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".aac") || lower.endsWith(".m4a") || lower.endsWith(".amr") || lower.endsWith(".opus") -> "AUDIO"
            else -> "VIDEO"
        }
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

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private class FileChunkRequestBody(
        private val file: File,
        private val offset: Long,
        private val byteCount: Long,
        private val contentType: okhttp3.MediaType
    ) : RequestBody() {
        override fun contentType(): okhttp3.MediaType = contentType

        override fun contentLength(): Long = byteCount

        override fun writeTo(sink: BufferedSink) {
            FileInputStream(file).use { input ->
                var skipped = 0L
                while (skipped < offset) {
                    val delta = input.skip(offset - skipped)
                    if (delta <= 0L) error("failed to skip to chunk offset")
                    skipped += delta
                }
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var remaining = byteCount
                while (remaining > 0) {
                    val read = input.read(buffer, 0, min(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) break
                    sink.write(buffer, 0, read)
                    remaining -= read
                }
            }
        }
    }

    companion object {
        const val DEFAULT_CLIENT_ID = "428a8310cd442757ae699df5d894f051"
        private const val DEFAULT_CHUNK_SIZE_BYTES = 8L * 1024L * 1024L
    }
}
