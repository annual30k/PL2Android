package com.patrollink.data.ute

import java.io.File
import java.security.MessageDigest

internal fun File.persistUniqueSmartMedia(
    mediaDirectory: File,
    prefix: String,
    type: String,
    fallbackExtension: String
): File? {
    val source = this
    if (!source.exists() || !source.isFile || source.length() <= 0L) return null
    mediaDirectory.mkdirs()
    val extension = source.smartMediaExtension(type, fallbackExtension)
    val hash = source.sha256()
    val target = File(mediaDirectory, "$prefix-${hash.take(16)}.$extension")
    if (target.exists() && target.length() == source.length()) return target
    return runCatching { source.copyTo(target, overwrite = true) }.getOrNull()
}

private fun File.smartMediaExtension(type: String, fallbackExtension: String): String =
    extension.ifBlank {
        type.substringAfterLast('/', fallbackExtension)
            .substringAfterLast('.', fallbackExtension)
            .ifBlank { fallbackExtension }
    }.lowercase()

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString(separator = "") { "%02x".format(it) }
}
