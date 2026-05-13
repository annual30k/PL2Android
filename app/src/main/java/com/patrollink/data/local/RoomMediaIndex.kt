package com.patrollink.data.local

import com.patrollink.domain.MediaFile

class RoomMediaIndex(
    private val dao: MediaFileDao
) {
    suspend fun upsert(file: MediaFile, localPath: String? = file.contentUri, sha256: String? = null, watermarkToken: String? = null) {
        dao.upsert(MediaFileEntity.from(file, localPath, sha256, watermarkToken))
    }

    suspend fun files(local: Boolean): List<MediaFile> =
        dao.files(local).map { it.toDomain() }

    suspend fun find(fileId: String): MediaFile? =
        dao.find(fileId)?.toDomain()

    suspend fun delete(fileId: String): Boolean =
        dao.delete(fileId) > 0
}
