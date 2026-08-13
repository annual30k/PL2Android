package com.patrollink.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import androidx.core.content.FileProvider
import com.patrollink.data.DefaultEvidenceIntegrityGateway
import com.patrollink.data.resolveBackendDownloadUrl
import com.patrollink.data.shouldAttachBackendAuthorization
import com.patrollink.domain.EvidenceIntegrityGateway
import com.patrollink.domain.VersionCheckResult
import com.patrollink.domain.VersionInstallPackage
import com.patrollink.domain.VersionInstaller
import com.patrollink.data.remote.OkHttpPatrolRestApi
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request

class AndroidVersionInstaller(
    private val context: Context,
    private val tokenProvider: () -> String? = { null },
    private val apiBaseUrl: String = "",
    private val clientId: String = OkHttpPatrolRestApi.DEFAULT_CLIENT_ID,
    private val client: OkHttpClient = OkHttpClient(),
    private val integrity: EvidenceIntegrityGateway = DefaultEvidenceIntegrityGateway()
) : VersionInstaller {
    private val directory = File(context.filesDir, "patrol_media_cache/updates").apply { mkdirs() }

    override suspend fun prepare(update: VersionCheckResult, expectedSha256: String?): VersionInstallPackage {
        val url = resolveBackendDownloadUrl(apiBaseUrl, update.downloadUrl) ?: error("missing update downloadUrl")
        val file = File(directory, "patrollink-${update.latestVersionName}.apk")
        val request = Request.Builder()
            .url(url)
            .header("clientid", clientId)
            .apply {
                if (shouldAttachBackendAuthorization(apiBaseUrl, url)) {
                    tokenProvider()?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
                }
            }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("download update failed: ${response.code}")
            file.outputStream().use { output ->
                response.body?.byteStream()?.copyTo(output)
            }
        }
        val sha256 = integrity.sha256(file)
        val expected = expectedSha256 ?: update.sha256
        val verified = expected.isNullOrBlank() || expected.equals(sha256, ignoreCase = true)
        check(verified) { "update package sha256 mismatch" }
        verifyPackageIdentity(file, update.latestVersionCode)
        return VersionInstallPackage(update.latestVersionName, file.absolutePath, sha256, verified = true)
    }

    override fun launchInstall(packageInfo: VersionInstallPackage): Boolean {
        val file = File(packageInfo.filePath)
        if (!file.exists() || !packageInfo.verified) return false
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    private fun verifyPackageIdentity(file: File, expectedVersionCode: Int) {
        val archive = packageInfo(file.absolutePath) ?: error("invalid APK package")
        check(archive.packageName == context.packageName) { "update package applicationId mismatch" }
        check(archive.longVersionCodeCompat() == expectedVersionCode.toLong()) {
            "update package versionCode mismatch"
        }
        val installed = packageInfo(context.packageName, installed = true) ?: error("installed package metadata unavailable")
        val installedSigners = installed.signerBytes()
        val archiveSigners = archive.signerBytes()
        check(installedSigners.isNotEmpty() && installedSigners == archiveSigners) {
            "update package signing certificate mismatch"
        }
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(value: String, installed: Boolean = false): PackageInfo? =
        if (Build.VERSION.SDK_INT >= 33) {
            val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            if (installed) context.packageManager.getPackageInfo(value, flags)
            else context.packageManager.getPackageArchiveInfo(value, flags)
        } else {
            if (installed) context.packageManager.getPackageInfo(value, PackageManager.GET_SIGNING_CERTIFICATES)
            else context.packageManager.getPackageArchiveInfo(value, PackageManager.GET_SIGNING_CERTIFICATES)
        }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun PackageInfo.signerBytes(): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            signingInfo?.apkContentsSigners.orEmpty()
        } else {
            signatures.orEmpty()
        }
        return signatures.map { signature -> signature.toByteArray().joinToString("") { "%02x".format(it) } }.toSet()
    }
}
