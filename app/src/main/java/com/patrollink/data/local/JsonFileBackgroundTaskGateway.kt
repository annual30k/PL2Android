package com.patrollink.data.local

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.patrollink.domain.BackgroundTask
import com.patrollink.domain.BackgroundTaskGateway
import com.patrollink.domain.BackgroundTaskReceipt
import java.io.File

class JsonFileBackgroundTaskGateway(
    private val file: File,
    private val gson: Gson = Gson()
) : BackgroundTaskGateway {
    override suspend fun enqueue(task: BackgroundTask): BackgroundTaskReceipt {
        val current = read().filterNot { it.task.id == task.id } + BackgroundTaskReceipt(task, queued = true)
        write(current)
        return current.last()
    }

    override suspend fun pending(): List<BackgroundTaskReceipt> = read()

    override suspend fun complete(taskId: String): Boolean {
        val current = read()
        val next = current.filterNot { it.task.id == taskId }
        write(next)
        return next.size < current.size
    }

    private fun read(): List<BackgroundTaskReceipt> {
        if (!file.exists()) return emptyList()
        val type = object : TypeToken<List<BackgroundTaskReceipt>>() {}.type
        return gson.fromJson(file.readText(), type) ?: emptyList()
    }

    private fun write(receipts: List<BackgroundTaskReceipt>) {
        file.parentFile?.mkdirs()
        file.writeText(gson.toJson(receipts))
    }
}
