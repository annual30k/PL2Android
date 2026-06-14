package com.patrollink.data.ute

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaKind
import com.patrollink.domain.TransferStatus
import java.io.File
import java.security.MessageDigest
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object UteWifiMediaParser {
    fun parseRemoteFiles(body: String, sourceUrl: String): List<UteWifiRemoteFile> {
        val jsonFiles = runCatching { parseJsonFiles(JsonParser.parseString(body), sourceUrl) }
            .getOrDefault(emptyList())
        if (jsonFiles.isNotEmpty()) return jsonFiles
        return parseTextFiles(body, sourceUrl)
    }

    private fun parseJsonFiles(element: JsonElement, sourceUrl: String): List<UteWifiRemoteFile> {
        if (element.isJsonArray) return element.asJsonArray.flatMap { parseJsonFiles(it, sourceUrl) }
        if (!element.isJsonObject) return emptyList()
        val obj = element.asJsonObject
        val nested = listOf("data", "files", "fileList", "mediaList", "list", "result", "items", "records")
            .firstNotNullOfOrNull { key -> obj.get(key)?.takeIf { it.isJsonArray || it.isJsonObject } }
        if (nested != null && nested !== element) {
            val nestedFiles = parseJsonFiles(nested, sourceUrl)
            if (nestedFiles.isNotEmpty()) return nestedFiles
        }
        val path = obj.stringValue(
            "url",
            "downloadUrl",
            "download_url",
            "fileUrl",
            "file_url",
            "href",
            "path",
            "filePath",
            "file_path",
            "name",
            "fileName",
            "file_name"
        ) ?: return emptyList()
        val name = obj.stringValue("fileName", "file_name", "name", "filename")
            ?: path.substringAfterLast('/').ifBlank { "device-media" }
        if (name.isLikelyWebUiAssetPath() || path.isLikelyWebUiAssetPath()) return emptyList()
        val kind = name.toMediaKind() ?: path.toMediaKind() ?: obj.stringValue("fileType", "file_type", "type", "mediaType", "media_type").toMediaKindHint() ?: return emptyList()
        val size = obj.longValue("size", "fileSize", "file_size", "length", "bytes")
        return listOf(remoteFile(path, sourceUrl, name, kind, size))
    }

    private fun parseTextFiles(body: String, sourceUrl: String): List<UteWifiRemoteFile> {
        val hrefMatches = Regex("""href\s*=\s*["']([^"']+\.(?:jpg|jpeg|png|mp4|mov|opus|wav|amr|aac|pcm))["']""", RegexOption.IGNORE_CASE)
            .findAll(body)
            .map { it.groupValues[1] }
        val plainMatches = Regex("""(?:^|[\s"'=])([A-Za-z0-9_./%+\-]+?\.(?:jpg|jpeg|png|mp4|mov|opus|wav|amr|aac|pcm))(?:$|[\s"'<])""", RegexOption.IGNORE_CASE)
            .findAll(body)
            .map { it.groupValues[1] }
        return (hrefMatches + plainMatches)
            .distinct()
            .mapNotNull { path ->
                if (path.isLikelyWebUiAssetPath()) return@mapNotNull null
                val name = path.substringAfterLast('/').ifBlank { return@mapNotNull null }
                val kind = name.toMediaKind() ?: return@mapNotNull null
                remoteFile(path, sourceUrl, name, kind, null)
            }
            .toList()
    }

    private fun remoteFile(
        pathOrUrl: String,
        sourceUrl: String,
        name: String,
        kind: MediaKind,
        size: Long?
    ): UteWifiRemoteFile {
        val url = resolveUrl(pathOrUrl, sourceUrl)
        val downloadUrls = candidateDownloadUrls(pathOrUrl, sourceUrl, url)
        return UteWifiRemoteFile(
            id = "$WifiMediaPrefix${url.stableHash().take(16)}",
            name = name,
            kind = kind,
            sizeBytes = size,
            url = url,
            downloadUrls = downloadUrls
        )
    }

    private fun resolveUrl(pathOrUrl: String, sourceUrl: String): String {
        pathOrUrl.toHttpUrlOrNull()?.let { return it.toString() }
        val base = sourceUrl.toHttpUrlOrNull() ?: return pathOrUrl
        return base.resolve(pathOrUrl)?.toString()
            ?: base.newBuilder().encodedPath(pathOrUrl.ensureLeadingSlash()).build().toString()
    }

    private fun String.ensureLeadingSlash(): String =
        if (startsWith('/')) this else "/$this"

    private fun candidateDownloadUrls(pathOrUrl: String, sourceUrl: String, primary: String): List<String> {
        if (!pathOrUrl.isBareMediaFileName()) return listOf(primary)
        val base = sourceUrl.toHttpUrlOrNull() ?: return listOf(primary)
        val fileName = pathOrUrl.substringBefore('?').substringAfterLast('/').takeIf { it.isNotBlank() }
            ?: return listOf(primary)
        return listOf(
            primary,
            base.newBuilder().encodedPath("/").query(null).addPathSegment(fileName).build().toString(),
            base.newBuilder().encodedPath("/download").query(null).addPathSegment(fileName).build().toString(),
            base.newBuilder().encodedPath("/file").query(null).addPathSegment(fileName).build().toString(),
            base.newBuilder().encodedPath("/media/download").query(null).addQueryParameter("name", fileName).build().toString()
        ).distinct()
    }

    private fun String.isBareMediaFileName(): Boolean =
        isNotBlank() &&
            toHttpUrlOrNull() == null &&
            !contains('/') &&
            !contains('\\') &&
            substringBefore('?').toMediaKind() != null

    private fun JsonObject.stringValue(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            get(key)?.takeIf { !it.isJsonNull }?.asString?.takeIf { value -> value.isNotBlank() }
        }

    private fun JsonObject.longValue(vararg keys: String): Long? =
        keys.firstNotNullOfOrNull { key ->
            get(key)?.takeIf { !it.isJsonNull }?.let { value ->
                runCatching { value.asLong }.getOrNull()
            }
        }?.takeIf { it > 0L }

    private fun String.toMediaKind(): MediaKind? =
        when (substringBefore('?').substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg", "png" -> MediaKind.Photo
            "mp4", "mov" -> MediaKind.Video
            "opus", "wav", "amr", "aac", "pcm" -> MediaKind.Audio
            else -> null
        }

    private fun String?.toMediaKindHint(): MediaKind? =
        when (this?.trim()?.lowercase()) {
            "photo", "picture", "image", "img", "jpg", "jpeg", "png" -> MediaKind.Photo
            "video", "movie", "mp4", "mov" -> MediaKind.Video
            "audio", "record", "recording", "voice", "opus", "wav", "amr", "aac", "pcm" -> MediaKind.Audio
            else -> null
        }

    private fun String.stableHash(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(toByteArray(Charsets.UTF_8)).joinToString(separator = "") { "%02x".format(it) }
    }

    private const val WifiMediaPrefix = "ute-wifi-"
}

internal fun String.isLikelyWebUiAssetPath(): Boolean {
    val normalized = substringBefore('?').lowercase()
    val name = normalized.substringAfterLast('/')
    return listOf(
        "/assets/",
        "/asset/",
        "/static/",
        "/res/",
        "/resources/",
        "/images/",
        "/img/",
        "/css/",
        "/js/"
    ).any { it in normalized } ||
        name == "favicon.ico" ||
        name.startsWith("favicon") ||
        name.startsWith("logo") ||
        name.startsWith("icon") ||
        name.startsWith("sprite") ||
        name.startsWith("background") ||
        name.startsWith("banner") ||
        name == "pictures_ute.jpg" ||
        name == "pictures_ute.jpeg" ||
        name == "pictures_ute.png"
}

internal data class UteWifiRemoteFile(
    val id: String,
    val name: String,
    val kind: MediaKind,
    val sizeBytes: Long?,
    val url: String,
    val downloadUrls: List<String> = listOf(url)
) {
    fun toMediaFile(local: Boolean): MediaFile =
        MediaFile(
            id = id,
            name = when (kind) {
                MediaKind.Photo -> "眼镜照片_$name"
                MediaKind.Video -> "眼镜视频_$name"
                MediaKind.Audio -> "设备录音_$name"
            },
            kind = kind,
            time = System.currentTimeMillis().toString(),
            size = formatSize(sizeBytes),
            duration = null,
            verified = false,
            local = local,
            transferStatus = TransferStatus.Idle,
            progress = 0f
        )

    fun localTarget(directory: File): File {
        val cleanName = name.substringAfterLast('/').ifBlank { id }
            .replace(Regex("""[^A-Za-z0-9._-]"""), "_")
        return File(directory, cleanName)
    }

    private fun formatSize(sizeBytes: Long?): String =
        when {
            sizeBytes == null || sizeBytes <= 0L -> "未知"
            sizeBytes >= 1024L * 1024L -> "%.1f MB".format(sizeBytes / 1024f / 1024f)
            else -> "${(sizeBytes / 1024L).coerceAtLeast(1L)} KB"
        }
}
