package com.patrollink.data.realtime

import com.google.gson.Gson
import com.patrollink.data.remote.OkHttpPatrolRestApi
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.HeartbeatAck
import com.patrollink.domain.RealtimeConnection
import com.patrollink.domain.RealtimeEvent
import com.patrollink.domain.RealtimeGateway
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class OkHttpWebSocketRealtimeGateway(
    private val url: String,
    private val clientId: String = OkHttpPatrolRestApi.DEFAULT_CLIENT_ID,
    private val client: OkHttpClient = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build(),
    private val gson: Gson = Gson(),
    private val reconnectDelayMillis: Long = DefaultReconnectDelayMillis
) : RealtimeGateway {
    private val state = MutableStateFlow(RealtimeConnection.Disconnected)
    private val eventFlow = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectionGeneration = AtomicLong(0L)
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    @Volatile private var accessToken: String? = null
    @Volatile private var closedByClient = false

    override fun connection(): Flow<RealtimeConnection> = state.asStateFlow()

    override fun events(): Flow<RealtimeEvent> = eventFlow.asSharedFlow()

    override suspend fun connect(token: String) {
        require(token.isNotBlank()) { "token required" }
        closedByClient = false
        accessToken = token
        reconnectJob?.cancel()
        openSocket(token)
    }

    private fun openSocket(token: String) {
        val generation = connectionGeneration.incrementAndGet()
        webSocket?.cancel()
        state.value = if (state.value == RealtimeConnection.Connected) {
            RealtimeConnection.Reconnecting
        } else {
            RealtimeConnection.Connecting
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("clientid", clientId)
            .build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (generation != connectionGeneration.get()) return
                state.value = RealtimeConnection.Connected
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (generation != connectionGeneration.get()) return
                parsePatrolRealtimeEvent(text, gson)?.let(eventFlow::tryEmit)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onMessage(webSocket, bytes.utf8())
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (generation != connectionGeneration.get()) return
                if (closedByClient) {
                    state.value = RealtimeConnection.Disconnected
                } else {
                    scheduleReconnect(generation)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (generation != connectionGeneration.get() || closedByClient) return
                scheduleReconnect(generation)
            }
        })
    }

    private fun scheduleReconnect(failedGeneration: Long) {
        if (closedByClient || failedGeneration != connectionGeneration.get()) return
        state.value = RealtimeConnection.Reconnecting
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(reconnectDelayMillis)
            val token = accessToken
            if (!closedByClient && !token.isNullOrBlank() && failedGeneration == connectionGeneration.get()) {
                openSocket(token)
            }
        }
    }

    override suspend fun disconnect() {
        closedByClient = true
        accessToken = null
        reconnectJob?.cancel()
        connectionGeneration.incrementAndGet()
        webSocket?.close(1000, "client logout")
        webSocket = null
        state.value = RealtimeConnection.Disconnected
    }

    override suspend fun sendHeartbeat(device: DeviceStatus): HeartbeatAck {
        val sent = webSocket?.send(ByteString.EMPTY) == true
        return HeartbeatAck(accepted = sent, serverTime = System.currentTimeMillis())
    }

    fun close() {
        scope.cancel()
        webSocket?.cancel()
    }

    private companion object {
        const val DefaultReconnectDelayMillis = 3_000L
    }
}

internal fun parsePatrolRealtimeEvent(raw: String, gson: Gson = Gson()): RealtimeEvent? = runCatching {
    val event = gson.fromJson(raw, RealtimeEvent::class.java)
    event.takeIf {
        it.namespace.equals("PATROL", ignoreCase = true) && it.type.isNotBlank()
    }
}.getOrNull()
