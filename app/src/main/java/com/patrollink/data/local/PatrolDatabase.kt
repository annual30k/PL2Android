package com.patrollink.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.patrollink.domain.BackgroundTask
import com.patrollink.domain.BackgroundTaskReceipt
import com.patrollink.domain.BackgroundTaskType
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaKind
import com.patrollink.domain.TransferStatus
import com.patrollink.domain.TransferTarget

@Entity(tableName = "offline_tasks")
data class OfflineTaskEntity(
    @PrimaryKey val id: String,
    val type: String,
    val payloadId: String,
    val createdAt: Long,
    val queued: Boolean,
    val retryCount: Int,
    val updatedAt: Long
) {
    fun toReceipt(): BackgroundTaskReceipt =
        BackgroundTaskReceipt(BackgroundTask(id, BackgroundTaskType.valueOf(type), payloadId, createdAt), queued)

    companion object {
        fun from(task: BackgroundTask, retryCount: Int = 0): OfflineTaskEntity =
            OfflineTaskEntity(task.id, task.type.name, task.payloadId, task.createdAt, queued = true, retryCount, System.currentTimeMillis())
    }
}

@Entity(tableName = "media_index")
data class MediaFileEntity(
    @PrimaryKey val storageKey: String,
    val accountKey: String,
    val id: String,
    val name: String,
    val kind: String,
    val time: String,
    val size: String,
    val duration: String?,
    val local: Boolean,
    val localPath: String?,
    val sha256: String?,
    val watermarkToken: String?,
    val transferStatus: String,
    val progress: Float,
    val lastTransferTarget: String?,
    val updatedAt: Long
) {
    fun toDomain(): MediaFile =
        MediaFile(
            id = id,
            name = name,
            kind = MediaKind.valueOf(kind),
            time = time,
            size = size,
            duration = duration,
            verified = !sha256.isNullOrBlank(),
            local = local,
            transferStatus = TransferStatus.valueOf(transferStatus),
            progress = progress,
            contentUri = localPath,
            lastTransferTarget = lastTransferTarget?.let(TransferTarget::valueOf)
        )

    companion object {
        private fun storageKey(accountKey: String, id: String, local: Boolean): String =
            "${normalizeAccountKey(accountKey)}:${if (local) "PHONE" else "DEVICE"}:$id"

        fun from(
            file: MediaFile,
            accountKey: String = DefaultMediaAccountKey,
            localPath: String? = file.contentUri,
            sha256: String? = null,
            watermarkToken: String? = null
        ): MediaFileEntity =
            MediaFileEntity(
                storageKey = storageKey(accountKey, file.id, file.local),
                accountKey = normalizeAccountKey(accountKey),
                id = file.id,
                name = file.name,
                kind = file.kind.name,
                time = file.time,
                size = file.size,
                duration = file.duration,
                local = file.local,
                localPath = localPath,
                sha256 = sha256,
                watermarkToken = watermarkToken,
                transferStatus = file.transferStatus.name,
                progress = file.progress,
                lastTransferTarget = file.lastTransferTarget?.name,
                updatedAt = System.currentTimeMillis()
            )
    }
}

@Dao
interface OfflineTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: OfflineTaskEntity)

    @Query("SELECT * FROM offline_tasks ORDER BY createdAt ASC")
    suspend fun pending(): List<OfflineTaskEntity>

    @Query("DELETE FROM offline_tasks WHERE id = :taskId")
    suspend fun delete(taskId: String): Int
}

@Dao
interface MediaFileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(file: MediaFileEntity)

    @Query("SELECT * FROM media_index WHERE accountKey = :accountKey AND local = :local ORDER BY updatedAt DESC")
    suspend fun files(accountKey: String, local: Boolean): List<MediaFileEntity>

    @Query("SELECT * FROM media_index WHERE accountKey = :accountKey AND id = :id AND local = :local LIMIT 1")
    suspend fun find(accountKey: String, id: String, local: Boolean): MediaFileEntity?

    @Query("DELETE FROM media_index WHERE accountKey = :accountKey AND id = :id AND local = :local")
    suspend fun delete(accountKey: String, id: String, local: Boolean): Int

    @Query("DELETE FROM media_index")
    suspend fun clearAll(): Int
}

@Database(
    entities = [OfflineTaskEntity::class, MediaFileEntity::class],
    version = 3,
    exportSchema = false
)
abstract class PatrolDatabase : RoomDatabase() {
    abstract fun offlineTaskDao(): OfflineTaskDao
    abstract fun mediaFileDao(): MediaFileDao

    companion object {
        @Volatile private var instance: PatrolDatabase? = null

        fun get(context: Context): PatrolDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, PatrolDatabase::class.java, "patrol.db")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}

const val DefaultMediaAccountKey = "anonymous"

fun normalizeAccountKey(accountKey: String?): String =
    accountKey
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
        ?: DefaultMediaAccountKey
