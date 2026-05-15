package com.patrollink.data.voip

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.patrollink.data.remote.IntercomSessionDto
import com.patrollink.data.remote.IntercomSignalDto
import com.patrollink.data.remote.IntercomSignalRequestDto
import com.patrollink.data.remote.PatrolRestApi
import com.patrollink.domain.IntercomState
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule

class AndroidWebRtcIntercomClient(
    context: Context,
    private val api: PatrolRestApi,
    private val audioRouter: BluetoothVoipAudioRouter,
    private val gson: Gson = Gson()
) {
    private companion object {
        private const val TAG = "PatrolWebRtc"
        private const val FALLBACK_STUN_SERVER = "stun:stun.l.google.com:19302"
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val factory: PeerConnectionFactory by lazy { createFactory() }
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var pollingJob: Job? = null
    private var activeSessionId: String? = null
    private var lastSignalId: String = ""
    private var routeStarted: Boolean = false
    private var remoteDescriptionReady: Boolean = false
    private val pendingRemoteIceCandidates = mutableListOf<IceCandidate>()
    private val stopLock = Any()

    fun start(session: IntercomSessionDto, onState: (IntercomState) -> Unit) {
        stopLocal()
        activeSessionId = session.sessionId
        lastSignalId = ""
        remoteDescriptionReady = false
        routeStarted = audioRouter.startBluetoothRoute()
        peerConnection = runCatching { createPeerConnection(session) }
            .onFailure { Log.e(TAG, "Failed to create WebRTC peer connection", it) }
            .getOrNull()
        if (peerConnection == null) {
            onState(IntercomState.Failed)
            stopLocal()
            return
        }
        addLocalAudioTrack()
        pollingJob = scope.launch {
            runCatching {
                api.acceptIntercomSession(session.sessionId)
                api.sendIntercomSignal(
                    session.sessionId,
                    IntercomSignalRequestDto(
                        sender = "APP",
                        type = "ready",
                        payload = """{"audioRoute":"BLUETOOTH_HEADSET","media":"audio"}"""
                    )
                )
                onState(IntercomState.Signaling)
                pollSignals(session.sessionId, onState)
            }.onFailure {
                onState(IntercomState.Failed)
                stopLocal()
            }
        }
    }

    suspend fun stop(sendHangup: Boolean = true) {
        val sessionId = activeSessionId
        if (sendHangup && sessionId != null) {
            runCatching { api.sendIntercomSignal(sessionId, IntercomSignalRequestDto(sender = "APP", type = "hangup")) }
            runCatching { api.closeIntercomSession(sessionId) }
        }
        stopLocal()
    }

    private fun createFactory(): PeerConnectionFactory {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        val audioDeviceModule = JavaAudioDeviceModule.builder(appContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        return PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()
    }

    private fun createPeerConnection(session: IntercomSessionDto): PeerConnection {
        val iceServers = usableIceServerUrls(session.iceServers)
            .map { PeerConnection.IceServer.builder(it).createIceServer() }
        val configuration = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        return checkNotNull(factory.createPeerConnection(configuration, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val sessionId = activeSessionId ?: return
                scope.launch {
                    api.sendIntercomSignal(
                        sessionId,
                        IntercomSignalRequestDto(
                            sender = "APP",
                            type = "ice",
                            payload = gson.toJson(candidate.toPayload())
                        )
                    )
                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
            override fun onAddStream(stream: MediaStream) = Unit
            override fun onRemoveStream(stream: MediaStream) = Unit
            override fun onDataChannel(dataChannel: org.webrtc.DataChannel) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) = Unit
        })) { "WebRTC SDK returned null PeerConnection for ICE servers: ${iceServers.map { it.urls }}" }
    }

    private fun usableIceServerUrls(urls: List<String>): List<String> {
        val usable = urls
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { url ->
                when {
                    url.startsWith("stun:", ignoreCase = true) || url.startsWith("stuns:", ignoreCase = true) ->
                        !url.contains(".local", ignoreCase = true)
                    url.startsWith("turn:", ignoreCase = true) || url.startsWith("turns:", ignoreCase = true) ->
                        false
                    else -> false
                }
            }
            .toList()
        return usable.ifEmpty { listOf(FALLBACK_STUN_SERVER) }
    }

    private fun addLocalAudioTrack() {
        val source = factory.createAudioSource(MediaConstraints())
        val track = factory.createAudioTrack("patrollink-app-audio", source)
        peerConnection?.addTrack(track, listOf("patrollink-intercom"))
        audioSource = source
        audioTrack = track
    }

    private suspend fun pollSignals(sessionId: String, onState: (IntercomState) -> Unit) {
        while (activeSessionId == sessionId) {
            val signals = api.intercomSignals(sessionId, lastSignalId).data
            for (signal in signals) {
                lastSignalId = signal.signalId
                if (signal.sender.equals("APP", ignoreCase = true)) continue
                handleSignal(signal, onState)
            }
            delay(600)
        }
    }

    private suspend fun handleSignal(signal: IntercomSignalDto, onState: (IntercomState) -> Unit) {
        when (signal.type.lowercase()) {
            "offer" -> {
                val answer = acceptOffer(signal.payload)
                api.sendIntercomSignal(
                    signal.sessionId,
                    IntercomSignalRequestDto(sender = "APP", type = "answer", payload = answer.description)
                )
                onState(IntercomState.Active)
            }
            "ice" -> addIceCandidate(signal.payload)
            "hangup" -> {
                onState(IntercomState.Closed)
                stopLocal()
            }
        }
    }

    private suspend fun acceptOffer(sdp: String): SessionDescription {
        val pc = requireNotNull(peerConnection) { "peer connection not ready" }
        pc.setRemoteDescriptionAwait(SessionDescription(SessionDescription.Type.OFFER, sdp))
        remoteDescriptionReady = true
        flushPendingRemoteIceCandidates(pc)
        val answer = pc.createAnswerAwait()
        pc.setLocalDescriptionAwait(answer)
        return answer
    }

    private fun addIceCandidate(payloadJson: String) {
        val payload = runCatching { gson.fromJson(payloadJson, IceCandidatePayload::class.java) }.getOrNull() ?: return
        if (payload.candidate.isBlank()) return
        val candidate = IceCandidate(payload.sdpMid, payload.sdpMLineIndex, payload.candidate)
        val pc = peerConnection ?: return
        if (!remoteDescriptionReady) {
            pendingRemoteIceCandidates += candidate
            return
        }
        pc.addIceCandidate(candidate)
    }

    private fun flushPendingRemoteIceCandidates(pc: PeerConnection) {
        val candidates = pendingRemoteIceCandidates.toList()
        pendingRemoteIceCandidates.clear()
        candidates.forEach { pc.addIceCandidate(it) }
    }

    private fun stopLocal() {
        synchronized(stopLock) {
            pollingJob?.cancel()
            pollingJob = null
            activeSessionId = null
            lastSignalId = ""
            remoteDescriptionReady = false
            pendingRemoteIceCandidates.clear()

            val pc = peerConnection
            val track = audioTrack
            val source = audioSource
            val shouldStopRoute = routeStarted

            peerConnection = null
            audioTrack = null
            audioSource = null
            routeStarted = false

            track?.setEnabled(false)
            pc?.close()
            track?.dispose()
            source?.dispose()
            if (shouldStopRoute) {
                audioRouter.stopBluetoothRoute()
            }
        }
    }

    private fun IceCandidate.toPayload() = IceCandidatePayload(sdpMid, sdpMLineIndex, sdp)

    private suspend fun PeerConnection.setRemoteDescriptionAwait(description: SessionDescription) =
        suspendSdp { observer -> setRemoteDescription(observer, description) }

    private suspend fun PeerConnection.setLocalDescriptionAwait(description: SessionDescription) =
        suspendSdp { observer -> setLocalDescription(observer, description) }

    private suspend fun PeerConnection.createAnswerAwait(): SessionDescription =
        suspendCancellableCoroutine { continuation ->
            createAnswer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(description: SessionDescription) {
                    continuation.resume(description)
                }

                override fun onCreateFailure(error: String) {
                    continuation.resumeWithException(IllegalStateException(error))
                }
            }, MediaConstraints())
        }

    private suspend fun suspendSdp(block: (SdpObserver) -> Unit) =
        suspendCancellableCoroutine { continuation ->
            block(object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    continuation.resume(Unit)
                }

                override fun onSetFailure(error: String) {
                    continuation.resumeWithException(IllegalStateException(error))
                }
            })
        }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String) = Unit
        override fun onSetFailure(error: String) = Unit
    }

    private data class IceCandidatePayload(
        val sdpMid: String?,
        val sdpMLineIndex: Int,
        val candidate: String
    )
}
