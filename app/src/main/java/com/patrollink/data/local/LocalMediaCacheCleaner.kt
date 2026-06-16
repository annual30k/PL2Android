package com.patrollink.data.local

import android.content.Context
import java.io.File

class LocalMediaCacheCleaner(
    context: Context,
    private val mediaIndex: RoomMediaIndex = RoomMediaIndex(PatrolDatabase.get(context).mediaFileDao())
) {
    private val appContext = context.applicationContext

    suspend fun clearAll() {
        mediaIndex.clearAll()
        listOf(
            File(appContext.filesDir, "patrol_media"),
            File(appContext.filesDir, "patrol_media_cache")
        ).forEach { directory ->
            if (directory.exists()) {
                directory.deleteRecursively()
            }
            directory.mkdirs()
        }
    }
}
