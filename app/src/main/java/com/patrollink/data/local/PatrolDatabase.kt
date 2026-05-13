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
    @PrimaryKey val id: String,
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
            contentUri = localPath
        )

    companion object {
        fun from(file: MediaFile, localPath: String? = file.contentUri, sha256: String? = null, watermarkToken: String? = null): MediaFileEntity =
            MediaFileEntity(
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

    @Query("SELECT * FROM media_index WHERE local = :local ORDER BY updatedAt DESC")
    suspend fun files(local: Boolean): List<MediaFileEntity>

    @Query("SELECT * FROM media_index WHERE id = :id LIMIT 1")
    suspend fun find(id: String): MediaFileEntity?

    @Query("DELETE FROM media_index WHERE id = :id")
    suspend fun delete(id: String): Int
}

@Database(
    entities = [OfflineTaskEntity::class, MediaFileEntity::class],
    version = 1,
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
