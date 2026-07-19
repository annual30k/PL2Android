package com.patrollink.data.sourcenex

import android.content.Context
import net.sourcenex.aig.client.sdk.v2.AigClient
import net.sourcenex.aig.client.sdk.v2.BluetoothScanHelper
import net.sourcenex.aig.protocol.AigMessage
import net.sourcenex.aig.protocol.MediaFile as SdkMediaFile
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

class SourceNexBridge(context: Context) {
    val client: AigClient = AigClient.getInstance(context.applicationContext)
    val scanHelper = BluetoothScanHelper(context.applicationContext)
    private val incoming = MutableSharedFlow<AigMessage>(extraBufferCapacity = 128)
    val messages = incoming.asSharedFlow()
    val mediaFiles = ConcurrentHashMap<String, SdkMediaFile>()
    private val requestMutex = Mutex()
    @Volatile private var selectedDeviceId: String? = null

    init {
        client.onAigMessage = { message ->
            if (message.messageCase == AigMessage.MessageCase.MEDIA_FILE) {
                mediaFiles[message.mediaFile.path] = message.mediaFile
            }
            incoming.tryEmit(message)
        }
    }

    suspend fun request(message: AigMessage, expected: AigMessage.MessageCase, timeoutMillis: Long = 8_000): AigMessage =
        requestMutex.withLock {
            coroutineScope {
                val response = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(timeoutMillis) { messages.filter { it.messageCase == expected }.first() }
                }
                client.sendMessage(message)
                response.await()
            }
        }

    suspend fun requestOrNull(message: AigMessage, expected: AigMessage.MessageCase, timeoutMillis: Long = 3_000): AigMessage? =
        requestMutex.withLock {
            coroutineScope {
                val response = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeoutOrNull(timeoutMillis) { messages.filter { it.messageCase == expected }.first() }
                }
                client.sendMessage(message)
                response.await()
            }
        }

    fun selectDevice(deviceId: String?) {
        selectedDeviceId = deviceId?.takeIf(::isSourceNexDeviceId)
    }

    fun isActive(): Boolean = isSourceNexDeviceId(selectedDeviceId)
}

internal fun isSourceNexDeviceId(deviceId: String?): Boolean =
    deviceId?.startsWith(SourceNexDeviceGateway.IdPrefix) == true
