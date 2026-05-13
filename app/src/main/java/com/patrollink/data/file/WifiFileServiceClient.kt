package com.patrollink.data.file

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.patrollink.data.remote.ApiEnvelope
import com.patrollink.data.remote.MediaFileDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class WifiFileServiceClient(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson()
) {
    suspend fun listDeviceFiles(): ApiEnvelope<List<MediaFileDto>> = get("files")

    suspend fun download(fileId: String, target: File): File = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${baseUrl.trimEnd('/')}/files/$fileId/download").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("download failed: ${response.code}")
            target.parentFile?.mkdirs()
            target.outputStream().use { output ->
                response.body?.byteStream()?.copyTo(output)
            }
            target
        }
    }

    suspend fun upload(file: File): ApiEnvelope<MediaFileDto> = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("application/octet-stream".toMediaType()))
            .build()
        val request = Request.Builder().url("${baseUrl.trimEnd('/')}/files/upload").post(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("upload failed: ${response.code}")
            gson.fromJson(response.body?.string().orEmpty(), object : TypeToken<ApiEnvelope<MediaFileDto>>() {}.type)
        }
    }

    suspend fun delete(fileId: String): ApiEnvelope<Boolean> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${baseUrl.trimEnd('/')}/files/$fileId").delete().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("delete failed: ${response.code}")
            gson.fromJson(response.body?.string().orEmpty(), object : TypeToken<ApiEnvelope<Boolean>>() {}.type)
        }
    }

    private suspend inline fun <reified T> get(path: String): T = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${baseUrl.trimEnd('/')}/$path").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("request failed: ${response.code}")
            gson.fromJson(response.body?.string().orEmpty(), object : TypeToken<T>() {}.type)
        }
    }
}
