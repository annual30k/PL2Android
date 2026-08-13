package com.patrollink.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun resolveBackendDownloadUrl(baseUrl: String, downloadUrl: String?): String? {
    val rawUrl = downloadUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (rawUrl.startsWith("file://", ignoreCase = true)) return rawUrl
    if (rawUrl.toHttpUrlOrNull() != null) return rawUrl
    val backend = baseUrl.trim().trimEnd('/').toHttpUrlOrNull() ?: return rawUrl
    return backend.resolve(rawUrl)?.toString() ?: rawUrl
}

internal fun shouldAttachBackendAuthorization(baseUrl: String, downloadUrl: String): Boolean {
    val backend = baseUrl.trim().trimEnd('/').toHttpUrlOrNull() ?: return false
    val target = downloadUrl.toHttpUrlOrNull() ?: return false
    return backend.scheme == target.scheme && backend.host == target.host && backend.port == target.port
}
