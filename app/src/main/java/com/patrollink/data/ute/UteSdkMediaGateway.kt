package com.patrollink.data.ute

import android.net.Uri
import com.patrollink.data.DefaultEvidenceIntegrityGateway
import com.patrollink.data.local.RoomMediaIndex
import com.patrollink.data.local.upsertLocalMediaSnapshot
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
import com.yc.nadalsdk.bean.smart.DeleteGlassesFilesByName
import com.yc.nadalsdk.bean.smart.SmartAudioDataInfo
import com.yc.nadalsdk.bean.smart.SmartImageDataInfo
import com.yc.nadalsdk.constants.NotifyType
import java.io.File
import java.util.concurrent.ConcurrentHashMap
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
    private val integrityGateway: DefaultEvidenceIntegrityGateway = DefaultEvidenceIntegrityGateway(),
    private val wifiMediaClient: UteWifiMediaClient? = null
) : MediaGateway {
    private val cachedWifiFiles = ConcurrentHashMap<String, MediaFile>()

    override suspend fun listFiles(local: Boolean): List<MediaFile> {
        if (local) {
            val discovered = localFiles()
            mediaIndex.upsertLocalMediaSnapshot(discovered)
            return (mediaIndex?.files(local = true).orEmpty() + discovered)
                .filterNot { it.isLikelyWebUiAssetMedia() }
                .deduplicateLocalMediaCopies()
                .distinctBy { it.id to it.local }
        }
        val wifiFilesResult = withTimeoutOrNull(WifiMediaListTimeoutMillis) {
            runCatching { wifiMediaClient?.listFiles().orEmpty() }
        }
        val wifiFilesFailure = when {
            wifiMediaClient == null -> null
            wifiFilesResult == null -> IllegalStateException(WifiMediaListTimeoutMessage)
            else -> wifiFilesResult.exceptionOrNull()
        }
        val wifiFiles = wifiFilesResult?.getOrDefault(emptyList()).orEmpty()
        if (wifiFilesResult?.isSuccess == true) {
            replaceCachedWifiFiles(wifiFiles)
            wifiFiles.forEach { mediaIndex?.upsert(it) }
        }
        val pushedFiles = requestPendingSmartMediaUpload()
        val audioFiles = withTimeoutOrNull(RemoteListTimeoutMillis) { queryAudioRecordFiles() }.orEmpty()
        val sdkFiles = (wifiFiles + pushedFiles + audioFiles).distinctBy { it.id to it.local }
        if (shouldPropagateWifiMediaListFailure(wifiMediaClient != null, wifiFilesFailure, sdkFiles.isEmpty())) {
            throw wifiFilesFailure ?: error(WifiSyncUnavailableMessage)
        }
        if (sdkFiles.isEmpty() && bridge.client.isConnected) return emptyList()
        return sdkFiles.ifEmpty {
            withTimeoutOrNull(FallbackListTimeoutMillis) { fallbackGateway.listFiles(local = false) }.orEmpty()
        }
    }

    override fun transfer(fileId: String, target: TransferTarget): Flow<MediaFile> {
        if (fileId.startsWith(WifiPrefix) && target in setOf(TransferTarget.PhoneSandbox, TransferTarget.Cloud)) {
            return flow {
                if (target == TransferTarget.Cloud) {
                    localMediaFileForUpload(fileId)?.let { (local, localFile) ->
                        emit(local.copy(transferStatus = TransferStatus.Hashing, progress = 0.12f, lastTransferTarget = TransferTarget.Cloud))
                        val uploaded = fallbackGateway.uploadLocalFile(localFile, storageSide = "PHONE", bizType = "MEDIA", bizId = fileId)
                        val completed = requireUteCloudUploadResult(fileId, uploaded).copy(
                            id = fileId,
                            local = true,
                            verified = true,
                            transferStatus = TransferStatus.Done,
                            progress = 1f,
                            contentUri = Uri.fromFile(localFile).toString(),
                            lastTransferTarget = TransferTarget.Cloud
                        )
                        mediaIndex?.upsert(completed, localPath = completed.contentUri)
                        emit(completed)
                        return@flow
                    }
                }
                val client = wifiMediaClient ?: error(WifiSyncUnavailableMessage)
                val remote = cachedWifiFiles[fileId] ?: listFiles(local = false).firstOrNull { it.id == fileId }
                    ?: error("wifi media file not found: $fileId")
                emit(remote.copy(transferStatus = TransferStatus.Uploading, progress = 0.05f, lastTransferTarget = TransferTarget.PhoneSandbox))
                val downloaded = withContext(Dispatchers.IO) {
                    client.download(fileId, mediaDirectory)
                }
                emit(remote.copy(transferStatus = TransferStatus.Hashing, progress = 0.82f, lastTransferTarget = TransferTarget.PhoneSandbox))
                val (sha256, token) = withContext(Dispatchers.IO) {
                    val hash = integrityGateway.sha256(downloaded.readBytes())
                    val watermark = integrityGateway.watermarkToken(fileId, officerBadgeNo, downloaded.lastModified())
                    File(mediaDirectory, "${downloaded.nameWithoutExtension}.integrity").writeText("sha256=$hash\nwatermark=$watermark\n")
                    hash to watermark
                }
                val local = remote.copy(
                    local = true,
                    verified = true,
                    transferStatus = if (target == TransferTarget.Cloud) TransferStatus.Uploading else TransferStatus.Done,
                    progress = if (target == TransferTarget.Cloud) 0.88f else 1f,
                    contentUri = Uri.fromFile(downloaded).toString(),
                    lastTransferTarget = if (target == TransferTarget.Cloud) TransferTarget.Cloud else TransferTarget.PhoneSandbox
                )
                mediaIndex?.upsert(local.copy(transferStatus = TransferStatus.Idle, progress = 0f, lastTransferTarget = null), localPath = local.contentUri, sha256 = sha256, watermarkToken = token)
                if (target == TransferTarget.PhoneSandbox) {
                    emit(local)
                    return@flow
                }
                val uploaded = fallbackGateway.uploadLocalFile(downloaded, storageSide = "PHONE", bizType = "MEDIA", bizId = fileId)
                val completed = requireUteCloudUploadResult(fileId, uploaded).copy(
                    id = fileId,
                    local = true,
                    verified = true,
                    transferStatus = TransferStatus.Done,
                    progress = 1f,
                    contentUri = Uri.fromFile(downloaded).toString(),
                    lastTransferTarget = TransferTarget.Cloud
                )
                mediaIndex?.upsert(completed, localPath = completed.contentUri, sha256 = sha256, watermarkToken = token)
                withContext(Dispatchers.IO) {
                    runCatching { bridge.connection.notifyMediaSyncCompleted() }
                }
                emit(completed)
            }
        }
        if ((fileId.startsWith(PhotoPrefix) || fileId.startsWith(VideoPrefix)) && target == TransferTarget.Cloud) {
            return flow {
                val local = localFiles().firstOrNull { it.id == fileId } ?: error("media file not found: $fileId")
                val localFile = local.contentUri?.let { Uri.parse(it).path }?.let(::File)
                    ?.takeIf { it.exists() && it.isFile } ?: error("local media file missing: $fileId")
                emit(local.copy(transferStatus = TransferStatus.Hashing, progress = 0.12f, lastTransferTarget = TransferTarget.Cloud))
                val uploaded = fallbackGateway.uploadLocalFile(localFile, storageSide = "PHONE", bizType = "MEDIA", bizId = fileId)
                emit(
                    requireUteCloudUploadResult(fileId, uploaded).copy(
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
        if (fileId.startsWith(VideoPrefix) && target == TransferTarget.PhoneSandbox) {
            return flow {
                val local = localFiles().firstOrNull { it.id == fileId }
                    ?: error(VideoSyncUnsupportedMessage)
                emit(local.copy(transferStatus = TransferStatus.Done, progress = 1f, lastTransferTarget = TransferTarget.PhoneSandbox))
            }
        }
        if (fileId.startsWith(PhotoPrefix) && target == TransferTarget.PhoneSandbox) {
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
                    val remote = listFiles(local = false).firstOrNull { it.id == fileId } ?: error(AudioSyncUnavailableMessage)
                    val expectedSize = findAudioRecordFile(sessionId)?.fileSize?.takeIf { it > 0L }
                        ?: remote.size.toByteSizeOrNull()
                    emit(remote.copy(transferStatus = TransferStatus.Uploading, progress = 0.05f, lastTransferTarget = TransferTarget.PhoneSandbox))
                    localFile.also { it.parentFile?.mkdirs() }.outputStream().use { output ->
                        syncAudio(sessionId, expectedSize, output::write) { progress ->
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
                val completed = requireUteCloudUploadResult(fileId, uploaded).copy(
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
            val remote = listFiles(local = false).firstOrNull { it.id == fileId } ?: error(AudioSyncUnavailableMessage)
            emit(remote.copy(transferStatus = TransferStatus.Uploading, progress = 0.05f))
            val sessionId = fileId.removePrefix(AudioPrefix)
            val expectedSize = findAudioRecordFile(sessionId)?.fileSize?.takeIf { it > 0L }
                ?: remote.size.toByteSizeOrNull()
            val targetFile = File(mediaDirectory, "$sessionId.opus").also { it.parentFile?.mkdirs() }
            targetFile.outputStream().use { output ->
                syncAudio(sessionId, expectedSize, output::write) { progress ->
                    emit(remote.copy(transferStatus = TransferStatus.Uploading, progress = progress))
                }
            }
            emit(remote.copy(transferStatus = TransferStatus.Hashing, progress = 0.88f))
            val sha256 = integrityGateway.sha256(targetFile.readBytes())
            val token = integrityGateway.watermarkToken(fileId, officerBadgeNo, targetFile.lastModified())
            File(mediaDirectory, "$sessionId.integrity").writeText("sha256=$sha256\nwatermark=$token\n")
            emit(remote.copy(transferStatus = TransferStatus.Verifying, progress = 0.95f))
            val completed = remote.copy(
                local = true,
                verified = true,
                transferStatus = TransferStatus.Done,
                progress = 1f,
                contentUri = Uri.fromFile(targetFile).toString(),
                lastTransferTarget = TransferTarget.PhoneSandbox
            )
            mediaIndex?.upsert(completed, localPath = completed.contentUri, sha256 = sha256, watermarkToken = token)
            emit(completed)
        }
    }

    private fun replaceCachedWifiFiles(files: List<MediaFile>) {
        cachedWifiFiles.clear()
        files.asSequence()
            .filter { it.id.startsWith(WifiPrefix) && !it.local }
            .forEach { cachedWifiFiles[it.id] = it }
    }

    override suspend fun delete(fileId: String, local: Boolean): Boolean {
        if (local && (fileId.startsWith(PhotoPrefix) || fileId.startsWith(VideoPrefix) || fileId.startsWith(WifiPrefix))) {
            val localMedia = (mediaIndex?.find(fileId, local = true)?.let { listOf(it) }.orEmpty() + localFiles())
                .filterNot { it.isLikelyWebUiAssetMedia() }
                .firstOrNull { it.id == fileId }
            val file = localMedia?.contentUri?.toLocalMediaFile()
            val deleted = file?.let { target ->
                !target.exists() || target.delete()
            } ?: (localMedia != null)
            if (deleted) {
                file?.deleteIntegritySidecar()
                mediaIndex?.delete(fileId, local = true)
            }
            return deleted
        }
        if (!local && fileId.startsWith(WifiPrefix)) {
            val deviceFileName = wifiDeviceFileNameForDelete(fileId, cachedWifiFiles[fileId]?.name)
                ?: wifiDeviceFileNameForDelete(fileId, mediaIndex?.find(fileId, local = false)?.name)
                ?: wifiDeviceFileNameForDelete(fileId, listFiles(local = false).firstOrNull { it.id == fileId }?.name)
                ?: return false
            val deleted = withContext(Dispatchers.IO) {
                runCatching {
                    val response = bridge.connection.deleteGlassesFilesByName(DeleteGlassesFilesByName(deviceFileName))
                    response.isSuccess &&
                        response.data in setOf(
                            DeleteGlassesFilesByName.DELETE_SUCCESS,
                            DeleteGlassesFilesByName.FILE_NOT_EXIST
                        )
                }.getOrDefault(false)
            }
            if (deleted) {
                cachedWifiFiles.remove(fileId)
                mediaIndex?.delete(fileId, local = false)
            }
            return deleted
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
        if (fileId.startsWith(PhotoPrefix) || fileId.startsWith(VideoPrefix) || fileId.startsWith(WifiPrefix)) {
            val localFile = (mediaIndex?.find(fileId, local = true)?.let { listOf(it) }.orEmpty() + localFiles())
                .filterNot { it.isLikelyWebUiAssetMedia() }
                .firstOrNull { it.id == fileId }
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

    private suspend fun queryAudioRecordFiles(): List<MediaFile> {
        val records = queryAudioRecordFileBeans(onlyOne = 0)
            .ifEmpty { queryAudioRecordFileBeans(onlyOne = 1) }
            .distinctBy { it.sessionId.orEmpty() to it.fileType }
        return records.map { it.toMediaFile(local = false) }.onEach { mediaIndex?.upsert(it) }
    }

    private suspend fun findAudioRecordFile(sessionId: String): AudioRecordFile? {
        if (sessionId.isBlank()) return null
        return queryAudioRecordFileBeans(onlyOne = 0).firstOrNull { it.sessionId == sessionId }
            ?: queryAudioRecordFileBeans(onlyOne = 1).firstOrNull { it.sessionId == sessionId }
    }

    private suspend fun queryAudioRecordFileBeans(onlyOne: Long): List<AudioRecordFile> = withContext(Dispatchers.IO) {
        runCatching {
            val request = RequestAudioRecordFileInfo(AudioUserId, 0L, onlyOne)
            val result = bridge.connection.queryAudioRecordFileLists(request).data
            result?.audioRecordFiles.orEmpty()
        }.getOrDefault(emptyList())
    }

    private suspend fun requestPendingSmartMediaUpload(): List<MediaFile> = coroutineScope {
        if (!hasPendingSmartMedia()) return@coroutineScope emptyList()
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

    private suspend fun hasPendingSmartMedia(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val store = bridge.connection.getGlassesInfo().data?.glassesStoreInfo ?: return@runCatching false
            store.newTakenPictures.isPositiveCount()
        }.getOrDefault(false)
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
        source.persistUniqueSmartMedia(mediaDirectory, prefix, type, fallbackExtension)?.toLocalMediaFile()
            ?.also { mediaIndex?.upsert(it, localPath = it.contentUri) }
    }

    private fun localFiles(): List<MediaFile> =
        mediaDirectory.supportedLocalMediaFiles()
            .map { file -> file.toLocalMediaFile() }
            .sortedByDescending { it.time.toLongOrNull() ?: 0L }

    private suspend fun localMediaFileForUpload(fileId: String): Pair<MediaFile, File>? {
        val local = (mediaIndex?.find(fileId, local = true)?.let { listOf(it) }.orEmpty() + localFiles())
            .filterNot { it.isLikelyWebUiAssetMedia() }
            .firstOrNull { it.id == fileId && it.contentUri?.isNotBlank() == true }
            ?: return null
        val file = local.contentUri
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?.let { uri ->
                when (uri.scheme) {
                    "file" -> uri.path?.let(::File)
                    null -> local.contentUri?.takeIf { it.startsWith("/") }?.let(::File)
                    else -> null
                }
            }
            ?.takeIf { it.exists() && it.isFile && it.length() > 0 }
            ?: return null
        return local to file
    }

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

    private fun MediaFile.isLikelyWebUiAssetMedia(): Boolean =
        name.removeMediaDisplayPrefix().isLikelyWebUiAssetPath() ||
            contentUri.orEmpty().substringAfterLast('/').isLikelyWebUiAssetPath()

    private fun List<MediaFile>.deduplicateLocalMediaCopies(): List<MediaFile> {
        if (isEmpty()) return this
        return groupBy { file -> file.localMediaCopyKey() }
            .values
            .map { copies ->
                copies.maxWithOrNull(
                    compareBy<MediaFile> { it.id.startsWith(WifiPrefix) }
                        .thenBy { it.verified }
                        .thenBy { it.contentUri?.isNotBlank() == true }
                ) ?: copies.first()
            }
            .sortedByDescending { it.time.toLongOrNull() ?: 0L }
    }

    private fun MediaFile.localMediaCopyKey(): String =
        contentUri
            ?.toLocalMediaFile()
            ?.absolutePath
            ?.lowercase()
            ?: name.removeMediaDisplayPrefix().lowercase()

    private fun String.removeMediaDisplayPrefix(): String =
        removePrefix("眼镜照片_")
            .removePrefix("眼镜视频_")
            .removePrefix("设备录音_")

    private fun String.toLocalMediaFile(): File? {
        val uri = runCatching { Uri.parse(this) }.getOrNull()
        return when {
            uri?.scheme == "file" -> uri.path?.let(::File)
            uri?.scheme == null && startsWith("/") -> File(this)
            else -> null
        }
    }

    private fun File.deleteIntegritySidecar() {
        parentFile?.resolve("$nameWithoutExtension.integrity")?.takeIf { it.exists() }?.delete()
    }

    private suspend fun syncAudio(
        sessionId: String,
        expectedSize: Long?,
        writeChunk: (ByteArray) -> Unit,
        onProgress: suspend (Float) -> Unit
    ) {
        var start = 0L
        val total = expectedSize?.takeIf { it > 0L }
        repeat(MaxChunks) {
            val request = RequestSyncAudioRecordFileInfo().apply {
                this.sessionId = sessionId
                this.fileType = AudioFileType
                this.start = start
                this.end = total ?: (start + ChunkSize)
                this.realSync = false
            }
            val accepted = withContext(Dispatchers.IO) { bridge.connection.syncAudioRecordFile(request) }
            if (!accepted.isSuccess) error("audio sync request rejected: ${accepted.errorCode}")
            val chunk = withTimeoutOrNull(NotifyTimeoutMillis) {
                bridge.notifies.mapNotNull { notify ->
                    when (notify.type) {
                        NotifyType.AI_RECORDER_SYNC_SECTION_DATA_NOTIFY,
                        NotifyType.AI_RECORDER_SYNC_ALL_DATA_NOTIFY,
                        NotifyType.AI_RECORDER_ABORT_SYNC_DATA_NOTIFY -> (notify.data as? SyncAudioDataInfo)?.let {
                            AudioSyncChunk(notify.type, it)
                        }
                        else -> null
                    }
                }.first()
            } ?: error("audio sync timed out: $sessionId")
            if (chunk.type == NotifyType.AI_RECORDER_ABORT_SYNC_DATA_NOTIFY) error("audio sync aborted: $sessionId")
            val data = chunk.data
            val bytes = data.syncAudioData ?: return
            if (bytes.isEmpty()) return
            writeChunk(bytes)
            val nextStart = data.position
            val written = nextStart.coerceAtLeast(start + bytes.size)
            val progress = if (total != null) {
                (written / total.toFloat()).coerceIn(0.08f, 0.86f)
            } else {
                (written / (written + ChunkSize).toFloat()).coerceIn(0.08f, 0.86f)
            }
            onProgress(progress)
            if (chunk.type == NotifyType.AI_RECORDER_SYNC_ALL_DATA_NOTIFY) return
            if (total != null && written >= total) return
            if (total == null && bytes.size < ChunkSize) return
            if (nextStart <= start) return
            start = nextStart
        }
    }

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

    private fun String.toByteSizeOrNull(): Long? {
        val trimmed = trim()
        val value = trimmed.substringBefore(' ').toDoubleOrNull() ?: return null
        return when {
            trimmed.endsWith("GB", ignoreCase = true) -> (value * 1024 * 1024 * 1024).toLong()
            trimmed.endsWith("MB", ignoreCase = true) -> (value * 1024 * 1024).toLong()
            trimmed.endsWith("KB", ignoreCase = true) -> (value * 1024).toLong()
            trimmed.endsWith("B", ignoreCase = true) -> value.toLong()
            else -> null
        }
    }

    private fun Number?.isPositiveCount(): Boolean =
        (this?.toLong() ?: 0L) > 0L

    private data class AudioSyncChunk(
        val type: Int,
        val data: SyncAudioDataInfo
    )

    private companion object {
        const val AudioPrefix = "ute-audio-"
        const val PhotoPrefix = "ute-photo-"
        const val VideoPrefix = "ute-video-"
        const val WifiPrefix = "ute-wifi-"
        const val AudioUserId = 0x01L
        const val AudioFileType = 1
        const val ChunkSize = 1600L
        const val MaxChunks = 4096
        const val NotifyTimeoutMillis = 8_000L
        const val RemoteListTimeoutMillis = 8_000L
        const val FallbackListTimeoutMillis = 5_000L
        const val SmartMediaRetryTimeoutMillis = 8_000L
        const val WifiMediaListTimeoutMillis = 45_000L
        const val WifiMediaListTimeoutMessage = "设备 Wi-Fi 媒体列表读取超时，请确认手机已连接设备热点"
        const val VideoSyncUnsupportedMessage = "当前 UTE SDK 只开放录像控制，没有开放录像文件同步到手机接口；需要设备 Wi-Fi 文件服务或厂家视频同步协议"
        const val AudioSyncUnavailableMessage = "当前没有可同步的录音文件；只有设备主动回传音频或支持 AI Recorder 文件同步时，录音才能上传到手机"
        const val WifiSyncUnavailableMessage = "设备 Wi-Fi 媒体传输客户端不可用"
    }
}

internal fun shouldPropagateWifiMediaListFailure(
    hasWifiMediaClient: Boolean,
    wifiFilesFailure: Throwable?,
    sdkFilesEmpty: Boolean
): Boolean =
    hasWifiMediaClient && wifiFilesFailure != null && sdkFilesEmpty

internal fun wifiDeviceFileNameForDelete(fileId: String, mediaName: String?): String? {
    if (!fileId.startsWith("ute-wifi-")) return null
    val cleaned = mediaName
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { name ->
            when {
                name.startsWith("眼镜照片_") -> name.removePrefix("眼镜照片_")
                name.startsWith("眼镜视频_") -> name.removePrefix("眼镜视频_")
                name.startsWith("设备录音_") -> name.removePrefix("设备录音_")
                else -> name
            }
        }
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?.takeIf { it.isNotBlank() }
    return cleaned
}

internal fun requireUteCloudUploadResult(fileId: String, uploaded: MediaFile?): MediaFile =
    uploaded ?: error("media upload did not return uploaded file: $fileId")

internal fun File.supportedLocalMediaFiles(): List<File> {
    if (!exists()) return emptyList()
    val candidates = if (isFile) sequenceOf(this) else walkTopDown()
    return candidates
        .filter { it.isFile && it.isSupportedLocalMediaFile() }
        .toList()
}

private fun File.isSupportedLocalMediaFile(): Boolean =
    !name.isLikelyWebUiAssetPath() &&
        (extension.equals("opus", ignoreCase = true) ||
            extension.equals("jpg", ignoreCase = true) ||
            extension.equals("jpeg", ignoreCase = true) ||
            extension.equals("png", ignoreCase = true) ||
            extension.equals("mp4", ignoreCase = true) ||
            extension.equals("mov", ignoreCase = true))
