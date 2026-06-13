package com.patrollink.data.local

import com.patrollink.domain.BackgroundTask
import com.patrollink.domain.BackgroundTaskType
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaGateway
import com.patrollink.domain.TransferStatus
import com.patrollink.domain.TransferTarget
import java.io.File
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface LocalMediaStore {
    suspend fun findLocal(fileId: String): MediaFile?
    suspend fun upsertUploaded(file: MediaFile, localPath: String?, sha256: String?)
}

class RoomLocalMediaStore(
    private val mediaIndex: RoomMediaIndex
) : LocalMediaStore {
    override suspend fun findLocal(fileId: String): MediaFile? =
        mediaIndex.find(fileId, local = true)

    override suspend fun upsertUploaded(file: MediaFile, localPath: String?, sha256: String?) {
        mediaIndex.upsert(file, localPath = localPath, sha256 = sha256)
    }
}

class EvidenceUploadTaskProcessor(
    private val localMediaStore: LocalMediaStore,
    private val mediaGateway: MediaGateway
) {
    suspend fun process(task: BackgroundTask): Boolean {
        if (task.type != BackgroundTaskType.UploadEvidence) return false
        val localMedia = localMediaStore.findLocal(task.payloadId) ?: return false
        val localFile = localMedia.contentUri?.toExistingFile() ?: return false
        val uploaded = withContext(Dispatchers.IO) {
            mediaGateway.uploadLocalFile(
                file = localFile,
                storageSide = "PHONE",
                bizType = "MEDIA",
                bizId = task.payloadId
            )
        } ?: return false
        val completed = uploaded.copy(
            id = task.payloadId,
            local = true,
            verified = true,
            transferStatus = TransferStatus.Done,
            progress = 1f,
            contentUri = localMedia.contentUri,
            lastTransferTarget = TransferTarget.Cloud
        )
        localMediaStore.upsertUploaded(completed, localPath = localMedia.contentUri, sha256 = null)
        return true
    }

    private fun String.toExistingFile(): File? {
        val file = when {
            startsWith("file:") -> runCatching { File(URI(this)) }.getOrNull()
            startsWith("/") -> File(this)
            else -> null
        }
        return file?.takeIf { it.exists() && it.isFile }
    }
}
