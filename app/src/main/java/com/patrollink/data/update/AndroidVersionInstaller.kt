package com.patrollink.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.patrollink.data.DefaultEvidenceIntegrityGateway
import com.patrollink.domain.EvidenceIntegrityGateway
import com.patrollink.domain.VersionCheckResult
import com.patrollink.domain.VersionInstallPackage
import com.patrollink.domain.VersionInstaller
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request

class AndroidVersionInstaller(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient(),
    private val integrity: EvidenceIntegrityGateway = DefaultEvidenceIntegrityGateway()
) : VersionInstaller {
    private val directory = File(context.filesDir, "patrol_media_cache/updates").apply { mkdirs() }

    override suspend fun prepare(update: VersionCheckResult, expectedSha256: String?): VersionInstallPackage {
        val url = update.downloadUrl ?: error("missing update downloadUrl")
        val file = File(directory, "patrollink-${update.latestVersionName}.apk")
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("download update failed: ${response.code}")
            file.outputStream().use { output ->
                response.body?.byteStream()?.copyTo(output)
            }
        }
        val sha256 = integrity.sha256(file.readBytes())
        val expected = expectedSha256 ?: update.sha256
        val verified = expected.isNullOrBlank() || expected.equals(sha256, ignoreCase = true)
        check(verified) { "update package sha256 mismatch" }
        return VersionInstallPackage(update.latestVersionName, file.absolutePath, sha256, verified = true)
    }

    override fun launchInstall(packageInfo: VersionInstallPackage): Boolean {
        val file = File(packageInfo.filePath)
        if (!file.exists() || !packageInfo.verified) return false
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }
}
