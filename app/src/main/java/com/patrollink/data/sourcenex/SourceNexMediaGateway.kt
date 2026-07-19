package com.patrollink.data.sourcenex

import android.net.Uri
import com.patrollink.data.DefaultEvidenceIntegrityGateway
import com.patrollink.data.local.RoomMediaIndex
import com.patrollink.data.ute.requireUteCloudUploadResult
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaGateway
import com.patrollink.domain.MediaKind
import com.patrollink.domain.TransferStatus
import com.patrollink.domain.TransferTarget
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import net.sourcenex.aig.protocol.AigMessage
import net.sourcenex.aig.protocol.MediaType
import net.sourcenex.aig.protocol.ReqFileDel
import net.sourcenex.aig.protocol.ReqPicList
import net.sourcenex.aig.protocol.ReqVidList

class SourceNexMediaGateway(
    private val bridge: SourceNexBridge,
    private val fallback: MediaGateway,
    private val mediaDirectory: File,
    private val officerBadgeNo: String,
    private val mediaIndex: RoomMediaIndex?,
    private val integrity: DefaultEvidenceIntegrityGateway = DefaultEvidenceIntegrityGateway()
) : MediaGateway {
    private val remoteById = ConcurrentHashMap<String, RemoteFile>()

    override suspend fun listFiles(local: Boolean): List<MediaFile> {
        if (local) return mediaIndex?.files(true).orEmpty().filter { it.id.startsWith(IdPrefix) }
        check(bridge.client.isConnected.value) { "SourceNex 眼镜未连接" }
        val pictures = requestPictures()
        val videos = requestVideos()
        val eventMedia = bridge.mediaFiles.values.map { file ->
            RemoteFile(file.path, file.type.toKind(), file.size, file.duration.takeIf { it > 0 })
        }
        val files = (pictures + videos + eventMedia).distinctBy { it.path }.map { it.toDomain() }
        files.forEach { mediaIndex?.upsert(it) }
        return files
    }

    override fun transfer(fileId: String, target: TransferTarget): Flow<MediaFile> = flow {
        val remote = remoteById[fileId] ?: listFiles(false).firstOrNull { it.id == fileId }?.let { remoteById[fileId] }
            ?: error("设备文件不存在: $fileId")
        val start = remote.toDomain().copy(transferStatus = TransferStatus.Uploading, progress = 0.05f, lastTransferTarget = TransferTarget.PhoneSandbox)
        emit(start)
        val targetFile = File(mediaDirectory, safeName(remote.path, remote.kind)).also { it.parentFile?.mkdirs() }
        withContext(Dispatchers.IO) {
            bridge.client.download(remote.path, targetFile.absolutePath) { _, _ -> }
        }
        emit(start.copy(transferStatus = TransferStatus.Hashing, progress = 0.82f))
        val sha256 = withContext(Dispatchers.IO) { integrity.sha256(targetFile) }
        val watermark = integrity.watermarkToken(fileId, officerBadgeNo, targetFile.lastModified())
        File(mediaDirectory, "${targetFile.nameWithoutExtension}.integrity").writeText("sha256=$sha256\nwatermark=$watermark\n")
        var local = start.copy(
            local = true,
            verified = true,
            transferStatus = if (target == TransferTarget.Cloud) TransferStatus.Uploading else TransferStatus.Done,
            progress = if (target == TransferTarget.Cloud) 0.88f else 1f,
            contentUri = Uri.fromFile(targetFile).toString(),
            lastTransferTarget = target
        )
        mediaIndex?.upsert(local.copy(transferStatus = TransferStatus.Idle, progress = 0f, lastTransferTarget = null), local.contentUri, sha256, watermark)
        emit(local)
        if (target == TransferTarget.Cloud) {
            local = requireUteCloudUploadResult(
                fileId,
                fallback.uploadLocalFile(targetFile, storageSide = "PHONE", bizType = "MEDIA", bizId = fileId)
            ).copy(id = fileId, local = true, verified = true, transferStatus = TransferStatus.Done, progress = 1f,
                contentUri = Uri.fromFile(targetFile).toString(), lastTransferTarget = TransferTarget.Cloud)
            mediaIndex?.upsert(local, local.contentUri, sha256, watermark)
            emit(local)
        }
    }

    override suspend fun uploadLocalFile(file: File, storageSide: String, bizType: String, bizId: String): MediaFile? =
        fallback.uploadLocalFile(file, storageSide, bizType, bizId)

    override suspend fun delete(fileId: String, local: Boolean): Boolean {
        if (local) {
            val indexed = mediaIndex?.find(fileId, true) ?: return false
            indexed.contentUri?.let(Uri::parse)?.path?.let(::File)?.takeIf { it.exists() }?.delete()
            mediaIndex.delete(fileId, true)
            return true
        }
        val remote = remoteById[fileId] ?: return false
        val request = ReqFileDel.newBuilder().addPath(remote.path).build()
        val response = bridge.request(AigMessage.newBuilder().setReqFileDel(request).build(), AigMessage.MessageCase.RES_FILE_DEL)
        val success = response.resFileDel.listList.all { it == 0 }
        if (success) {
            remoteById.remove(fileId)
            mediaIndex?.delete(fileId, false)
        }
        return success
    }

    override suspend fun verifySha256(fileId: String): Boolean {
        val file = mediaIndex?.find(fileId, true)?.contentUri?.let(Uri::parse)?.path?.let(::File)?.takeIf { it.exists() } ?: return false
        val expected = mediaIndex.expectedSha256(fileId, true)
            ?: readStoredSha256(File(mediaDirectory, "${file.nameWithoutExtension}.integrity"))
        return withContext(Dispatchers.IO) { integrity.matchesSha256(file, expected) }
    }

    private suspend fun requestPictures(): List<RemoteFile> {
        val request = ReqPicList.newBuilder().setPage(1).setSize(PageSize).build()
        val response = bridge.request(AigMessage.newBuilder().setReqPicList(request).build(), AigMessage.MessageCase.RES_PIC_LIST)
        return response.resPicList.listList.map { RemoteFile(it.path, MediaKind.Photo, it.size, null) }
    }

    private suspend fun requestVideos(): List<RemoteFile> {
        val request = ReqVidList.newBuilder().setPage(1).setSize(PageSize).build()
        val response = bridge.request(AigMessage.newBuilder().setReqVidList(request).build(), AigMessage.MessageCase.RES_VID_LIST)
        return response.resVidList.listList.map { RemoteFile(it.path, MediaKind.Video, it.size, null) }
    }

    private fun RemoteFile.toDomain(): MediaFile {
        val id = "$IdPrefix${path.sha256Short()}"
        remoteById[id] = this
        return MediaFile(id, File(path).name.ifBlank { safeName(path, kind) }, kind, formatTime(), size.toReadableSize(),
            durationSeconds?.let { "%02d:%02d".format(it / 60, it % 60) }, false, false, TransferStatus.Idle, 0f)
    }

    private data class RemoteFile(val path: String, val kind: MediaKind, val size: Long, val durationSeconds: Int?)

    companion object {
        const val IdPrefix = "sourcenex-media:"
        private const val PageSize = 500
        private fun MediaType.toKind() = when (this) { MediaType.IMAGE -> MediaKind.Photo; MediaType.VIDEO -> MediaKind.Video; else -> MediaKind.Audio }
        private fun String.sha256Short() = MessageDigest.getInstance("SHA-256").digest(toByteArray()).take(10).joinToString("") { "%02x".format(it) }
        private fun formatTime() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        private fun safeName(path: String, kind: MediaKind): String {
            val raw = File(path).name.takeIf { it.contains('.') }
            if (raw != null) return raw
            val ext = when (kind) { MediaKind.Photo -> "jpg"; MediaKind.Video -> "mp4"; MediaKind.Audio -> "m4a" }
            return "sourcenex_${System.currentTimeMillis()}.$ext"
        }
        private fun Long.toReadableSize(): String = when {
            this >= 1024 * 1024 -> "%.1f MB".format(this / 1024f / 1024f)
            this >= 1024 -> "%.1f KB".format(this / 1024f)
            else -> "$this B"
        }

        private fun readStoredSha256(file: File): String? =
            file.takeIf { it.isFile }
                ?.useLines { lines -> lines.firstOrNull { it.startsWith("sha256=") } }
                ?.substringAfter('=')
                ?.trim()
    }
}
