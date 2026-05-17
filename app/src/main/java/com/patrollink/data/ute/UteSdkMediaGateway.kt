package com.patrollink.data.ute

import android.net.Uri
import com.patrollink.data.DefaultEvidenceIntegrityGateway
import com.patrollink.data.local.RoomMediaIndex
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaGateway
import com.patrollink.domain.MediaKind
import com.patrollink.domain.TransferStatus
import com.patrollink.domain.TransferTarget
import com.yc.nadalsdk.bean.Notify
import com.yc.nadalsdk.bean.recorder.AudioRecordFile
import com.yc.nadalsdk.bean.recorder.RequestAudioRecordFileInfo
import com.yc.nadalsdk.bean.recorder.RequestDeleteAudioRecordFileInfo
import com.yc.nadalsdk.bean.recorder.RequestSyncAudioRecordFileInfo
import com.yc.nadalsdk.bean.recorder.SyncAudioDataInfo
import com.yc.nadalsdk.bean.smart.SmartAudioDataInfo
import com.yc.nadalsdk.bean.smart.SmartImageDataInfo
import com.yc.nadalsdk.constants.NotifyType
import java.io.File
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class UteSdkMediaGateway(
    private val bridge: UteSdkBridge,
    private val fallbackGateway: MediaGateway,
    private val mediaDirectory: File,
    private val officerBadgeNo: String,
    private val mediaIndex: RoomMediaIndex? = null,
    private val integrityGateway: DefaultEvidenceIntegrityGateway = DefaultEvidenceIntegrityGateway()
) : MediaGateway {
    override suspend fun listFiles(local: Boolean): List<MediaFile> {
        if (local) return (mediaIndex?.files(local = true).orEmpty() + localFiles())
            .distinctBy { it.id to it.local }
            .ifEmpty { fallbackGateway.listFiles(local = true) }
        val pushedFiles = requestPendingSmartMediaUpload()
        val audioFiles = withTimeoutOrNull(RemoteListTimeoutMillis) { queryAudioRecordFiles() }.orEmpty()
        val sdkFiles = (pushedFiles + audioFiles).distinctBy { it.id to it.local }
        if (sdkFiles.isEmpty() && bridge.client.isConnected) return emptyList()
        return sdkFiles.ifEmpty {
            withTimeoutOrNull(FallbackListTimeoutMillis) { fallbackGateway.listFiles(local = false) }.orEmpty()
        }
    }

    override fun transfer(fileId: String, target: TransferTarget): Flow<MediaFile> {
        if ((fileId.startsWith(PhotoPrefix) || fileId.startsWith(VideoPrefix)) && target == TransferTarget.Cloud) {
            return flow {
                val local = localFiles().firstOrNull { it.id == fileId } ?: error("media file not found: $fileId")
                val localFile = local.contentUri?.let { Uri.parse(it).path }?.let(::File)
                    ?.takeIf { it.exists() && it.isFile } ?: error("local media file missing: $fileId")
                emit(local.copy(transferStatus = TransferStatus.Hashing, progress = 0.12f, lastTransferTarget = TransferTarget.Cloud))
                val uploaded = fallbackGateway.uploadLocalFile(localFile, storageSide = "PHONE", bizType = "MEDIA", bizId = fileId)
                emit(
                    (uploaded ?: local).copy(
                        id = fileId,
                        local = true,
                        verified = true,
                        transferStatus = TransferStatus.Done,
                        progress = 1f,
                        contentUri = Uri.fromFile(localFile).toString(),
                        lastTransferTarget = TransferTarget.Cloud
                    )
                )
            }
        }
        if ((fileId.startsWith(PhotoPrefix) || fileId.startsWith(VideoPrefix)) && target == TransferTarget.PhoneSandbox) {
            return flow {
                val local = localFiles().firstOrNull { it.id == fileId } ?: error("media file not found: $fileId")
                emit(local.copy(transferStatus = TransferStatus.Done, progress = 1f, lastTransferTarget = TransferTarget.PhoneSandbox))
            }
        }
        if (fileId.startsWith(AudioPrefix) && target == TransferTarget.Cloud) {
            return flow {
                val sessionId = fileId.removePrefix(AudioPrefix)
                val localFile = File(mediaDirectory, "$sessionId.opus")
                if (!localFile.exists()) {
                    val remote = listFiles(local = false).firstOrNull { it.id == fileId } ?: error("media file not found: $fileId")
                    emit(remote.copy(transferStatus = TransferStatus.Uploading, progress = 0.05f, lastTransferTarget = TransferTarget.PhoneSandbox))
                    localFile.also { it.parentFile?.mkdirs() }.outputStream().use { output ->
                        syncAudio(sessionId, output::write) { progress ->
                            emit(remote.copy(transferStatus = TransferStatus.Uploading, progress = (progress * 0.58f).coerceIn(0.08f, 0.58f), lastTransferTarget = TransferTarget.PhoneSandbox))
                        }
                    }
                    emit(remote.copy(transferStatus = TransferStatus.Hashing, progress = 0.62f, lastTransferTarget = TransferTarget.Cloud))
                    val sha256 = integrityGateway.sha256(localFile.readBytes())
                    val token = integrityGateway.watermarkToken(fileId, officerBadgeNo, localFile.lastModified())
                    File(mediaDirectory, "$sessionId.integrity").writeText("sha256=$sha256\nwatermark=$token\n")
                    emit(
                        remote.copy(
                            local = true,
                            verified = true,
                            transferStatus = TransferStatus.Uploading,
                            progress = 0.72f,
                            contentUri = Uri.fromFile(localFile).toString(),
                            lastTransferTarget = TransferTarget.Cloud
                        )
                    )
                }
                val local = localFiles().firstOrNull { it.id == fileId }
                    ?: MediaFile(
                        id = fileId,
                        name = localFile.name,
                        kind = MediaKind.Audio,
                        time = localFile.lastModified().toString(),
                        size = localFile.length().toReadableSize(),
                        duration = null,
                        verified = File(mediaDirectory, "$sessionId.integrity").exists(),
                        local = true,
                        transferStatus = TransferStatus.Uploading,
                        progress = 0.15f,
                        contentUri = Uri.fromFile(localFile).toString()
                    )
                emit(local.copy(transferStatus = TransferStatus.Uploading, progress = 0.18f, lastTransferTarget = TransferTarget.Cloud))
                val uploaded = fallbackGateway.uploadLocalFile(localFile, storageSide = "PHONE", bizType = "MEDIA", bizId = fileId)
                val completed = (uploaded ?: local).copy(
                    id = fileId,
                    local = true,
                    verified = true,
                    transferStatus = TransferStatus.Done,
                    progress = 1f,
                    contentUri = Uri.fromFile(localFile).toString(),
                    lastTransferTarget = TransferTarget.Cloud
                )
                val sha256 = runCatching { integrityGateway.sha256(localFile.readBytes()) }.getOrNull()
                mediaIndex?.upsert(completed, localPath = completed.contentUri, sha256 = sha256)
                emit(completed)
            }
        }
        if (!fileId.startsWith(AudioPrefix) || target != TransferTarget.PhoneSandbox) {
            return fallbackGateway.transfer(fileId, target)
        }
        return flow {
            val remote = listFiles(local = false).firstOrNull { it.id == fileId } ?: error("media file not found: $fileId")
            emit(remote.copy(transferStatus = TransferStatus.Uploading, progress = 0.05f))
            val sessionId = fileId.removePrefix(AudioPrefix)
            val targetFile = File(mediaDirectory, "$sessionId.opus").also { it.parentFile?.mkdirs() }
            targetFile.outputStream().use { output ->
                syncAudio(sessionId, output::write) { progress ->
                    emit(remote.copy(transferStatus = TransferStatus.Uploading, progress = progress))
                }
            }
            emit(remote.copy(transferStatus = TransferStatus.Hashing, progress = 0.88f))
            val sha256 = integrityGateway.sha256(targetFile.readBytes())
            val token = integrityGateway.watermarkToken(fileId, officerBadgeNo, targetFile.lastModified())
            File(mediaDirectory, "$sessionId.integrity").writeText("sha256=$sha256\nwatermark=$token\n")
            emit(remote.copy(transferStatus = TransferStatus.Verifying, progress = 0.95f))
            emit(
                remote.copy(
                    local = true,
                    verified = true,
                    transferStatus = TransferStatus.Done,
                    progress = 1f,
                    contentUri = Uri.fromFile(targetFile).toString(),
                    lastTransferTarget = TransferTarget.PhoneSandbox
                )
            )
        }
    }

    override suspend fun delete(fileId: String, local: Boolean): Boolean {
        if (local && (fileId.startsWith(PhotoPrefix) || fileId.startsWith(VideoPrefix))) {
            val file = localFiles().firstOrNull { it.id == fileId }
                ?.contentUri
                ?.let { Uri.parse(it).path }
                ?.let(::File)
            return file?.delete() ?: false
        }
        if (!fileId.startsWith(AudioPrefix)) return fallbackGateway.delete(fileId, local)
        if (local) {
            val sessionId = fileId.removePrefix(AudioPrefix)
            File(mediaDirectory, "$sessionId.opus").takeIf { it.exists() }?.delete()
            File(mediaDirectory, "$sessionId.integrity").takeIf { it.exists() }?.delete()
            return true
        }
        val request = RequestDeleteAudioRecordFileInfo().apply {
            sessionId = fileId.removePrefix(AudioPrefix)
            fileType = AudioFileType
        }
        return withContext(Dispatchers.IO) {
            runCatching { bridge.connection.deleteAudioRecordFile(request).isSuccess }.getOrDefault(false)
        }
    }

    override suspend fun verifySha256(fileId: String): Boolean {
        if (fileId.startsWith(PhotoPrefix) || fileId.startsWith(VideoPrefix)) {
            val localFile = localFiles().firstOrNull { it.id == fileId }
                ?.contentUri
                ?.let { Uri.parse(it).path }
                ?.let(::File)
                ?.takeIf { it.exists() } ?: return false
            val hash = integrityGateway.sha256(localFile.readBytes())
            File(mediaDirectory, "${localFile.nameWithoutExtension}.integrity").writeText("sha256=$hash\n")
            return true
        }
        if (!fileId.startsWith(AudioPrefix)) return fallbackGateway.verifySha256(fileId)
        val localFile = File(mediaDirectory, "${fileId.removePrefix(AudioPrefix)}.opus")
        if (!localFile.exists()) return false
        val hash = integrityGateway.sha256(localFile.readBytes())
        val token = integrityGateway.watermarkToken(fileId, officerBadgeNo, localFile.lastModified())
        File(mediaDirectory, "${fileId.removePrefix(AudioPrefix)}.integrity").writeText("sha256=$hash\nwatermark=$token\n")
        return true
    }

    private suspend fun queryAudioRecordFiles(): List<MediaFile> = withContext(Dispatchers.IO) {
        runCatching {
            val request = RequestAudioRecordFileInfo(0x01, 0, 1)
            val result = bridge.connection.queryAudioRecordFileLists(request).data
            result?.audioRecordFiles.orEmpty().map { it.toMediaFile(local = false) }
        }.getOrDefault(emptyList())
    }

    private suspend fun requestPendingSmartMediaUpload(): List<MediaFile> = coroutineScope {
        val beforeIds = withContext(Dispatchers.IO) { localFiles().map { it.id }.toSet() }
        val receiveOne = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(SmartMediaRetryTimeoutMillis) {
                bridge.notifies.mapNotNull { notify -> persistSmartMediaNotify(notify) }.first()
            }
        }
        withContext(Dispatchers.IO) {
            runCatching { bridge.client.openOrCloseNotify(true) }
            runCatching { bridge.connection.retryImageUpload() }
        }
        val received = receiveOne.await()
        if (received != null) {
            withContext(Dispatchers.IO) {
                runCatching { bridge.connection.notifyMediaSyncCompleted() }
            }
        }
        val after = withContext(Dispatchers.IO) { localFiles() }
        when {
            received != null -> listOf(received)
            else -> after.filter { it.id !in beforeIds }
        }
    }

    private suspend fun persistSmartMediaNotify(notify: Notify): MediaFile? {
        return when (notify.type) {
            NotifyType.SMART_GLASSES_IMAGE_DATA_NOTIFY -> {
                val data = notify.data as? SmartImageDataInfo ?: return null
                if (data.crcSuccess != true) return null
                data.file?.persistSmartFile("glasses-photo", data.imaType.orEmpty(), "jpg")
            }
            NotifyType.SMART_GLASSES_AUDIO_DATA_NOTIFY -> {
                val data = notify.data as? SmartAudioDataInfo ?: return null
                if (data.crcSuccess != true) return null
                data.file?.persistSmartFile("glasses-audio", data.audioType.orEmpty(), "opus")
            }
            else -> null
        }
    }

    private suspend fun File.persistSmartFile(prefix: String, type: String, fallbackExtension: String): MediaFile? = withContext(Dispatchers.IO) {
        val source = this@persistSmartFile
        if (!source.exists() || !source.isFile || source.length() <= 0L) return@withContext null
        mediaDirectory.mkdirs()
        val extension = source.extension.ifBlank {
            type.substringAfterLast('/', fallbackExtension).substringAfterLast('.', fallbackExtension).ifBlank { fallbackExtension }
        }.lowercase()
        val target = File(mediaDirectory, "$prefix-${System.currentTimeMillis()}.$extension")
        runCatching { source.copyTo(target, overwrite = true) }.getOrNull()?.toLocalMediaFile()
    }

    private fun localFiles(): List<MediaFile> =
        mediaDirectory.listFiles { file -> file.isSupportedLocalMedia() }
            .orEmpty()
            .map { file -> file.toLocalMediaFile() }
            .sortedByDescending { it.time.toLongOrNull() ?: 0L }

    private fun File.toLocalMediaFile(): MediaFile {
        val sessionId = nameWithoutExtension
        val kind = toMediaKind()
        return MediaFile(
            id = toMediaId(),
            name = toMediaName(),
            kind = kind,
            time = lastModified().toString(),
            size = length().toReadableSize(),
            duration = null,
            verified = kind != MediaKind.Audio || File(mediaDirectory, "$sessionId.integrity").exists(),
            local = true,
            transferStatus = TransferStatus.Idle,
            progress = 0f,
            contentUri = Uri.fromFile(this).toString()
        )
    }

    private fun File.isSupportedLocalMedia(): Boolean =
        extension.equals("opus", ignoreCase = true) ||
            extension.equals("jpg", ignoreCase = true) ||
            extension.equals("jpeg", ignoreCase = true) ||
            extension.equals("png", ignoreCase = true) ||
            extension.equals("mp4", ignoreCase = true) ||
            extension.equals("mov", ignoreCase = true)

    private fun File.toMediaKind(): MediaKind = when (extension.lowercase()) {
        "jpg", "jpeg", "png" -> MediaKind.Photo
        "mp4", "mov" -> MediaKind.Video
        else -> MediaKind.Audio
    }

    private fun File.toMediaId(): String = when (toMediaKind()) {
        MediaKind.Audio -> "$AudioPrefix$nameWithoutExtension"
        MediaKind.Photo -> "$PhotoPrefix$nameWithoutExtension"
        MediaKind.Video -> "$VideoPrefix$nameWithoutExtension"
    }

    private fun File.toMediaName(): String = when (toMediaKind()) {
        MediaKind.Audio -> "设备录音_$name"
        MediaKind.Photo -> "眼镜照片_$name"
        MediaKind.Video -> "眼镜视频_$name"
    }

    private suspend fun syncAudio(
        sessionId: String,
        writeChunk: (ByteArray) -> Unit,
        onProgress: suspend (Float) -> Unit
    ) {
        var start = 0L
        var end = ChunkSize
        repeat(MaxChunks) {
            val request = RequestSyncAudioRecordFileInfo().apply {
                this.sessionId = sessionId
                this.fileType = AudioFileType
                this.start = start
                this.end = end
                this.realSync = false
            }
            withContext(Dispatchers.IO) { bridge.connection.syncAudioRecordFile(request) }
            val data = withTimeoutOrNull(NotifyTimeoutMillis) {
                bridge.notifies.mapNotNull { notify ->
                    when (notify.type) {
                        NotifyType.AI_RECORDER_SYNC_SECTION_DATA_NOTIFY,
                        NotifyType.AI_RECORDER_SYNC_ALL_DATA_NOTIFY,
                        NotifyType.AI_RECORDER_ABORT_SYNC_DATA_NOTIFY -> notify.data as? SyncAudioDataInfo
                        else -> null
                    }
                }.first()
            } ?: return
            val bytes = data.syncAudioData ?: return
            if (bytes.isEmpty()) return
            writeChunk(bytes)
            start = data.position
            end = start + ChunkSize
            onProgress((start / (start + ChunkSize).toFloat()).coerceIn(0.08f, 0.86f))
            if (data.position <= 0L || notifyIsFinal(bytes, data.position)) return
        }
    }

    private fun notifyIsFinal(bytes: ByteArray, position: Long): Boolean =
        bytes.size < ChunkSize || position < ChunkSize

    private fun AudioRecordFile.toMediaFile(local: Boolean): MediaFile {
        val sessionId = sessionId.orEmpty()
        return MediaFile(
            id = "$AudioPrefix$sessionId",
            name = "设备录音_$sessionId.opus",
            kind = MediaKind.Audio,
            time = sessionId.ifBlank { System.currentTimeMillis().toString() },
            size = fileSize.toReadableSize(),
            duration = null,
            verified = false,
            local = local,
            transferStatus = TransferStatus.Idle,
            progress = 0f
        )
    }

    private fun Long.toReadableSize(): String {
        val mb = this / 1024f / 1024f
        return if (mb >= 1f) "%.1f MB".format(mb) else "${(this / 1024).coerceAtLeast(1)} KB"
    }

    private companion object {
        const val AudioPrefix = "ute-audio-"
        const val PhotoPrefix = "ute-photo-"
        const val VideoPrefix = "ute-video-"
        const val AudioFileType = 1
        const val ChunkSize = 1600L
        const val MaxChunks = 4096
        const val NotifyTimeoutMillis = 8_000L
        const val RemoteListTimeoutMillis = 8_000L
        const val FallbackListTimeoutMillis = 5_000L
        const val SmartMediaRetryTimeoutMillis = 8_000L
    }
}
