package com.patrollink.data.local

import com.patrollink.domain.MediaFile
import com.patrollink.domain.TransferStatus

interface MediaIndexWriter {
    suspend fun upsert(file: MediaFile, localPath: String? = file.contentUri, sha256: String? = null, watermarkToken: String? = null)
}

class RoomMediaIndex(
    private val dao: MediaFileDao,
    private val accountKeyProvider: () -> String = { DefaultMediaAccountKey }
) : MediaIndexWriter {
    private val accountKey: String get() = normalizeAccountKey(accountKeyProvider())

    override suspend fun upsert(file: MediaFile, localPath: String?, sha256: String?, watermarkToken: String?) {
        dao.upsert(MediaFileEntity.from(file, accountKey = accountKey, localPath = localPath, sha256 = sha256, watermarkToken = watermarkToken))
    }

    suspend fun files(local: Boolean): List<MediaFile> =
        dao.files(accountKey, local).map { it.toDomain() }

    suspend fun find(fileId: String, local: Boolean): MediaFile? =
        dao.find(accountKey, fileId, local)?.toDomain()

    suspend fun expectedSha256(fileId: String, local: Boolean): String? =
        dao.find(accountKey, fileId, local)?.sha256

    suspend fun delete(fileId: String, local: Boolean): Boolean =
        dao.delete(accountKey, fileId, local) > 0

    suspend fun clearAll(): Int = dao.clearAll()
}

suspend fun MediaIndexWriter?.upsertLocalMediaSnapshot(files: List<MediaFile>) {
    this ?: return
    files.asSequence()
        .filter { it.local && !it.contentUri.isNullOrBlank() }
        .forEach { file ->
            upsert(
                file = file.copy(
                    transferStatus = TransferStatus.Idle,
                    progress = 0f,
                    lastTransferTarget = null
                ),
                localPath = file.contentUri
            )
        }
}
