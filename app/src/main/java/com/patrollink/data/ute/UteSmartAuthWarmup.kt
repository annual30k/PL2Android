package com.patrollink.data.ute

import android.util.Log
import com.google.gson.Gson
import com.yc.nadalsdk.bean.smart.ThirdPartyDataTransmitNotify
import com.yc.nadalsdk.constants.NotifyType
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UteSmartAuthWarmup(
    private val bridge: UteSdkBridge
) {
    suspend fun run(timeoutMillis: Long = SmartAuthWarmupMillis): SmartAuthWarmupResult = coroutineScope {
        val protocol = StarburstCompatProtocol()
        val stats = SmartAuthWarmupStats()
        val collector = launch(Dispatchers.IO) {
            bridge.notifies
                .filter { it.type == NotifyType.THIRD_PARTY_DATA_TRANSMIT_NOTIFY }
                .collect { notify ->
                    val data = notify.data as? ThirdPartyDataTransmitNotify ?: return@collect
                    if (!data.crcResult) {
                        stats.badCrcPackets += 1
                        return@collect
                    }
                    val packet = data.data ?: return@collect
                    stats.rxPackets += 1
                    protocol.responses(packet).forEach { response ->
                        val result = runCatching { bridge.connection.thirdPartyDataTransmitToBle(response) }
                        stats.txPackets += 1
                        Log.i(Tag, "third-party auth warmup tx=${response.toHexPreview()},success=${result.getOrNull()?.isSuccess},error=${result.getOrNull()?.errorCode}")
                    }
                }
        }
        val start = withContext(Dispatchers.IO) {
            runCatching {
                bridge.client.openOrCloseNotify(true)
                bridge.connection.startAuthentication()
            }
        }
        delay(timeoutMillis)
        collector.cancelAndJoin()
        val response = start.getOrNull()
        SmartAuthWarmupResult(
            started = response?.isSuccess == true,
            errorCode = response?.errorCode,
            receivedPackets = stats.rxPackets,
            sentPackets = stats.txPackets,
            badCrcPackets = stats.badCrcPackets
        )
    }

    private class StarburstCompatProtocol {
        private val gson = Gson()
        private var requestSequence = 1
        private var sentFgsStart = false
        private var sentFndTimestamp = false

        fun responses(packet: ByteArray): List<ByteArray> {
            if (packet.size < 4 || packet[2].toUnsignedInt() != TransportPrefix || packet[3].toUnsignedInt() !in SupportedCommands) {
                return emptyList()
            }
            return when (packet[3].toUnsignedInt()) {
                CommandAppInteracting -> packet.appInteractingAck()
                CommandLpRequest -> fgsStartRequest()
                CommandJson -> jsonResponses(packet)
                else -> emptyList()
            }
        }

        private fun ByteArray.appInteractingAck(): List<ByteArray> {
            val message = getOrNull(4)?.toUnsignedInt() ?: return emptyList()
            val ack = when (message) {
                0x00 -> 0x01
                0x02 -> 0x03
                else -> return emptyList()
            }
            return listOf(byteArrayOf(0x00, this[1], TransportPrefix.toByte(), CommandAppInteracting.toByte(), ack.toByte(), 0x00))
        }

        private fun fgsStartRequest(): List<ByteArray> {
            if (sentFgsStart) return emptyList()
            sentFgsStart = true
            val body = gson.toJson(
                mapOf(
                    "sid" to "FGS",
                    "data" to gson.toJson(mapOf("msg_type" to "FGS_MSG_TYPE_START_FGS_REQ", "sidver" to 1)),
                    "ver" to 1
                )
            )
            return listOf(jsonRequest(body))
        }

        private fun jsonResponses(packet: ByteArray): List<ByteArray> {
            val text = packet.copyOfRange(4, packet.size).toString(StandardCharsets.UTF_8)
            if (!text.contains("FGS_MSG_TYPE_START_FGS_RESP") || sentFndTimestamp) return emptyList()
            sentFndTimestamp = true
            val timestamp = System.currentTimeMillis().toString().toByteArray(StandardCharsets.US_ASCII)
            val fndPayload = byteArrayOf(0x01, timestamp.size.toByte(), 0x00) + timestamp
            val body = gson.toJson(
                mapOf(
                    "sid" to "FND",
                    "data" to android.util.Base64.encodeToString(fndPayload, android.util.Base64.NO_WRAP),
                    "ver" to 1
                )
            )
            return listOf(jsonRequest(body))
        }

        private fun jsonRequest(json: String): ByteArray =
            byteArrayOf(0x00, (requestSequence++ and 0xFF).toByte(), 0x00, CommandJson.toByte()) +
                json.toByteArray(StandardCharsets.UTF_8)
    }

    private class SmartAuthWarmupStats {
        var rxPackets: Int = 0
        var txPackets: Int = 0
        var badCrcPackets: Int = 0
    }

    private companion object {
        const val Tag = "UteSmartAuth"
        const val SmartAuthWarmupMillis = 2_800L
        const val TransportPrefix = 0xF0
        const val CommandAppInteracting = 0x60
        const val CommandLpRequest = 0x4F
        const val CommandJson = 0x41
        val SupportedCommands = setOf(CommandAppInteracting, CommandLpRequest, CommandJson)
    }
}

data class SmartAuthWarmupResult(
    val started: Boolean,
    val errorCode: Int?,
    val receivedPackets: Int,
    val sentPackets: Int,
    val badCrcPackets: Int
) {
    override fun toString(): String =
        "started=$started,error=$errorCode,rx=$receivedPackets,tx=$sentPackets,badCrc=$badCrcPackets"
}

private fun Byte.toUnsignedInt(): Int = toInt() and 0xFF

private fun ByteArray.toHexPreview(maxBytes: Int = 24): String =
    take(maxBytes).joinToString(separator = "") { "%02X".format(it.toUnsignedInt()) } +
        if (size > maxBytes) "..." else ""
