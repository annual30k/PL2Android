package com.patrollink.data.realtime

import com.google.gson.Gson
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.HeartbeatAck
import com.patrollink.domain.RealtimeConnection
import com.patrollink.domain.RealtimeGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class OkHttpWebSocketRealtimeGateway(
    private val url: String,
    private val client: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson()
) : RealtimeGateway {
    private val state = MutableStateFlow(RealtimeConnection.Disconnected)
    private var webSocket: WebSocket? = null

    override fun connection(): Flow<RealtimeConnection> = state.asStateFlow()

    override suspend fun connect(token: String) {
        state.value = RealtimeConnection.Connecting
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                state.value = RealtimeConnection.Connected
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                state.value = RealtimeConnection.Disconnected
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                state.value = RealtimeConnection.Reconnecting
            }
        })
    }

    override suspend fun disconnect() {
        webSocket?.close(1000, "client logout")
        webSocket = null
        state.value = RealtimeConnection.Disconnected
    }

    override suspend fun sendHeartbeat(device: DeviceStatus): HeartbeatAck {
        val payload = mapOf(
            "type" to "HEARTBEAT",
            "deviceId" to device.id,
            "online" to device.online,
            "battery" to device.battery,
            "signalBars" to device.signalBars,
            "recording" to device.isRecording,
            "timestamp" to System.currentTimeMillis()
        )
        val sent = webSocket?.send(gson.toJson(payload)) == true
        return HeartbeatAck(accepted = sent, serverTime = System.currentTimeMillis())
    }
}
