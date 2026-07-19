package com.patrollink.presentation

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patrollink.data.EmptyFirmwareGateway
import com.patrollink.data.EmptyVersionGateway
import com.patrollink.data.RuntimeConfigStore
import com.patrollink.data.RuntimeConfigGateway
import com.patrollink.data.ServiceFactory
import com.patrollink.data.edge.CerebellumApi
import com.patrollink.data.edge.CerebellumCombinedVisionAnalyzeRequestDto
import com.patrollink.data.edge.CerebellumCombinedVisionAnalyzeResponseDto
import com.patrollink.data.edge.CerebellumReportRequestDto
import com.patrollink.data.edge.OkHttpCerebellumApi
import com.patrollink.data.local.UiSettingsStore
import com.patrollink.data.local.QueuedAlertDisposition
import com.patrollink.data.local.QueuedAlertDispositionCodec
import com.patrollink.data.local.QueuedDeviceCommandAck
import com.patrollink.data.local.QueuedDeviceCommandAckCodec
import com.patrollink.data.local.QueuedHeartbeat
import com.patrollink.data.local.QueuedHeartbeatCodec
import com.patrollink.data.local.QueuedSosEvidence
import com.patrollink.data.local.QueuedSosEvidenceCodec
import com.patrollink.data.local.QueuedSosSync
import com.patrollink.data.local.QueuedSosSyncCodec
import com.patrollink.data.local.normalizeAccountKey
import com.patrollink.data.remote.AlertDraftRequestDto
import com.patrollink.data.remote.CerebellumSettingsDto
import com.patrollink.data.remote.DailyReportContentUpdateDto
import com.patrollink.data.remote.DeviceCommandAckRequestDto
import com.patrollink.data.remote.HeartbeatRequestDto
import com.patrollink.data.remote.GpsLocationDto
import com.patrollink.data.remote.OkHttpPatrolRestApi
import com.patrollink.data.remote.PatrolRestApi
import com.patrollink.data.remote.UploadAttachmentDto
import com.patrollink.domain.AlertAttachment
import com.patrollink.domain.AlertResult
import com.patrollink.domain.AlertStatus
import com.patrollink.domain.AuthSession
import com.patrollink.domain.DailyReport
import com.patrollink.domain.DisplayThemeMode
import com.patrollink.domain.DeviceAdvancedSettings
import com.patrollink.domain.DeviceCapabilities
import com.patrollink.domain.DeviceCommand
import com.patrollink.domain.DeviceControlGateway
import com.patrollink.domain.DeviceEvent
import com.patrollink.domain.DeviceEventLevel
import com.patrollink.domain.DeviceFactoryResetTarget
import com.patrollink.domain.DeviceMediaSyncUiState
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.DeviceType
import com.patrollink.domain.DeviceWifiState
import com.patrollink.domain.EmptyAppState
import com.patrollink.domain.FontSizeMode
import com.patrollink.domain.FirmwareCheckResult
import com.patrollink.domain.FirmwareDeviceMetadata
import com.patrollink.domain.FirmwareGateway
import com.patrollink.domain.FirmwareUpdatePhase
import com.patrollink.domain.FirmwareUpdateUiState
import com.patrollink.domain.GpsLocation
import com.patrollink.domain.IntercomState
import com.patrollink.domain.LocationFetchStatus
import com.patrollink.domain.LocationGateway
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaKind
import com.patrollink.domain.OperationMessage
import com.patrollink.domain.OperationMessageType
import com.patrollink.domain.OfflineSyncEngine
import com.patrollink.domain.PatrolCoordinator
import com.patrollink.domain.PatrolCommandMessage
import com.patrollink.domain.PatrolNotificationGateway
import com.patrollink.domain.SecureStore
import com.patrollink.domain.ScannedDevice
import com.patrollink.domain.SosEvidenceRecorder
import com.patrollink.domain.StreamMode
import com.patrollink.domain.StreamRelayState
import com.patrollink.domain.TransferStatus
import com.patrollink.domain.TransferTarget
import com.patrollink.domain.UserProfile
import com.patrollink.domain.VersionGateway
import com.patrollink.domain.VersionInstaller
import com.patrollink.domain.VersionUpdatePhase
import com.patrollink.domain.VersionUpdateUiState
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class MediaContentRequest(
    val value: String,
    val authorization: String? = null,
    val clientId: String? = null
)

private const val ControlReadyPollAttempts = 8
private const val ControlReadyPollMillis = 750L
private const val PhotoCaptureTapDebounceMillis = 1_500L
private const val DeviceWifiMediaIdPrefix = "ute-wifi-"
private const val DeviceMediaTransferMaxAttempts = 2
private const val DeviceMediaRetryDelayMillis = 1_500L
private const val DeviceWifiRestartSettleMillis = 1_500L
private const val DeviceMediaTransferTimeoutMillis = 30_000L
private const val MaxDeviceEvents = 30
private const val SessionRefreshThresholdSeconds = 60L
private const val SessionRefreshRetryMillis = 60_000L
private const val MaxSessionRefreshDelaySeconds = 24L * 60L * 60L
private const val CloudSyncPollMillis = 5_000L
private const val HeartbeatEveryPolls = 3

class PatrolViewModel(
    private val appContext: Context? = null,
    private val coordinator: PatrolCoordinator = ServiceFactory.createCoordinator(),
    private val deviceControlGateway: DeviceControlGateway? = null,
    private val secureStore: SecureStore? = null,
    private val settingsStore: UiSettingsStore? = null,
    private val versionGateway: VersionGateway = EmptyVersionGateway(),
    private val locationGateway: LocationGateway? = null,
    private val sosEvidenceRecorder: SosEvidenceRecorder? = null,
    private val notificationGateway: PatrolNotificationGateway? = null,
    private val versionInstaller: VersionInstaller? = null,
    private val firmwareGateway: FirmwareGateway = EmptyFirmwareGateway(),
    private var cerebellumApi: CerebellumApi? = null,
    private val cerebellumApiFactory: (String, String) -> CerebellumApi? = { baseUrl, apiKey ->
        OkHttpCerebellumApi(baseUrl = baseUrl, apiKeyProvider = { apiKey })
    },
    private val patrolRestApi: PatrolRestApi? = null,
    private val runtimeConfigStore: RuntimeConfigGateway? = null,
    private val backendBaseUrl: String = "",
    private val offlineSyncEngine: OfflineSyncEngine? = null,
    private val onSessionChanged: (AuthSession?) -> Unit = {},
    private val onPairingUsernameChanged: (String?) -> Unit = {},
    private val onSelectedDeviceChanged: (String?) -> Unit = {},
    private val currentLocalAccountProvider: () -> String? = { null },
    private val clearLocalMediaCache: suspend () -> Unit = {}
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        EmptyAppState.create().copy(
            fontSizeMode = settingsStore?.readFontSizeMode() ?: FontSizeMode.Standard,
            displayThemeMode = settingsStore?.readDisplayThemeMode() ?: DisplayThemeMode.System
        )
    )
    val uiState: StateFlow<com.patrollink.domain.AppUiState> = _uiState.asStateFlow()
    private var scannedDevicesJob: Job? = null
    private var photoCaptureJob: Job? = null
    private var deviceMediaSyncJob: Job? = null
    private var sessionRefreshJob: Job? = null
    private var cloudSyncJob: Job? = null
    private val deviceMediaTransferJobs = mutableMapOf<String, Job>()
    private var lastPhotoCaptureRequestAt: Long = 0L
    private val autoBindingDeviceIds = mutableSetOf<String>()
    private var activeSosId: String? = null
    private var activeSosActivationQueued: Boolean = false
    private val knownCloudAlertIds = mutableSetOf<String>()
    private val knownCloudMessageIds = mutableSetOf<String>()
    private val cloudRecognitionSubmittedMediaIds = mutableSetOf<String>()

    init {
        observeDeviceEvents()
        observeIntercomState()
        viewModelScope.launch {
            restoreSavedSession()
        }
        viewModelScope.launch {
            uiState
                .map { state -> state.selectedDeviceId ?: state.device.id.takeIf { it.isNotBlank() } }
                .distinctUntilChanged()
                .collect(onSelectedDeviceChanged)
        }
    }

    private suspend fun restoreSavedSession() {
        val store = secureStore
        val session = store?.let { withContext(Dispatchers.IO) { it.readSession() } }
        if (session == null || !session.hasUsableTokens()) {
            if (session != null) withContext(Dispatchers.IO) { store.clearSession() }
            onSessionChanged(null)
            _uiState.update { state -> state.copy(isLoggedIn = false, sessionRestoring = false) }
            return
        }

        var activeSession = session
        if (activeSession.expiresInSeconds <= SessionRefreshThresholdSeconds) {
            activeSession = runCatching { coordinator.refreshSession(activeSession.refreshToken) }.getOrElse { activeSession }
            if (activeSession != session) {
                withContext(Dispatchers.IO) { store.saveSession(activeSession) }
            }
        }
        onSessionChanged(activeSession)
        runCatching { coordinator.connectRealtime(activeSession.accessToken) }
        var userResult = runCatching { coordinator.currentUser() }
        if (userResult.isFailure) {
            val refreshed = runCatching { coordinator.refreshSession(activeSession.refreshToken) }.getOrNull()
            if (refreshed != null) {
                activeSession = refreshed
                onSessionChanged(refreshed)
                withContext(Dispatchers.IO) { store?.saveSession(refreshed) }
                runCatching { coordinator.connectRealtime(refreshed.accessToken) }
                userResult = runCatching { coordinator.currentUser() }
            }
        }
        userResult
            .onSuccess { user ->
                clearLocalMediaCacheIfAccountChanged(user.badgeNo)
                onPairingUsernameChanged(user.badgeNo)
                _uiState.update { state ->
                    state.copy(
                        isLoggedIn = true,
                        sessionRestoring = false,
                        networkOnline = true,
                        user = user
                    )
                }
                scheduleSessionRefresh(activeSession)
                startAuthenticatedRefreshes()
                runCatching { coordinator.currentRealtimeState() }
            }
            .onFailure {
                onSessionChanged(null)
                withContext(Dispatchers.IO) { store.clearSession() }
                _uiState.update { state ->
                    state.copy(
                        isLoggedIn = false,
                        sessionRestoring = false,
                        networkOnline = false,
                        cerebellumSettings = com.patrollink.domain.CerebellumSettingsUiState()
                    )
                }
            }
    }

    fun login(account: String, password: String, agreed: Boolean) {
        when {
            account.isBlank() -> {
                showOperationMessage("请输入账号", OperationMessageType.Warning)
                return
            }
            password.isBlank() -> {
                showOperationMessage("请输入密码", OperationMessageType.Warning)
                return
            }
            !agreed -> {
                showOperationMessage("请先阅读并同意服务协议和隐私政策", OperationMessageType.Warning)
                return
            }
        }
        viewModelScope.launch {
            _uiState.update { it.copy(loginLoading = true) }
            runCatching { coordinator.loginAndStartSession(account, password) }
                .onSuccess { session ->
                    clearLocalMediaCacheIfAccountChanged(account)
                    onSessionChanged(session)
                    onPairingUsernameChanged(account)
                    secureStore?.let { store -> withContext(Dispatchers.IO) { store.saveSession(session) } }
                    _uiState.update { it.copy(isLoggedIn = true, sessionRestoring = false, loginLoading = false, networkOnline = true, operationMessage = operationMessage("登录成功", OperationMessageType.Success)) }
                    scheduleSessionRefresh(session)
                    startAuthenticatedRefreshes()
                }
                .onFailure { throwable ->
                    val message = throwable.message?.takeIf { it.isNotBlank() } ?: "登录失败，请检查账号、密码和后台连接"
                    _uiState.update {
                        it.copy(
                            sessionRestoring = false,
                            loginLoading = false,
                            networkOnline = false,
                            operationMessage = operationMessage(message, OperationMessageType.Error)
                        )
                    }
                }
        }
    }

    private suspend fun clearLocalMediaCacheIfAccountChanged(nextAccount: String?) {
        val previous = normalizeAccountKey(currentLocalAccountProvider())
        val next = normalizeAccountKey(nextAccount)
        if (previous != next) {
            clearLocalMediaCache()
            _uiState.update {
                it.copy(
                    mediaFiles = emptyList(),
                    selectedMediaFileId = null,
                    previewMediaFile = null,
                    mediaLoading = false,
                    deviceMediaSync = DeviceMediaSyncUiState()
                )
            }
        }
    }

    fun logout() = viewModelScope.launch {
        if (_uiState.value.sosActive) {
            showOperationMessage("SOS 仍处于活动或待补传状态，请先完成取消再退出账号", OperationMessageType.Warning)
            return@launch
        }
        scannedDevicesJob?.cancel()
        scannedDevicesJob = null
        sessionRefreshJob?.cancel()
        sessionRefreshJob = null
        cloudSyncJob?.cancel()
        cloudSyncJob = null
        onSessionChanged(null)
        knownCloudAlertIds.clear()
        knownCloudMessageIds.clear()
        cloudRecognitionSubmittedMediaIds.clear()
        secureStore?.let { store -> withContext(Dispatchers.IO) { store.clearSession() } }
        cerebellumApi = null
        _uiState.update {
            it.copy(
                isLoggedIn = false,
                sessionRestoring = false,
                scannedDevices = emptyList(),
                mediaFiles = emptyList(),
                selectedMediaFileId = null,
                previewMediaFile = null,
                mediaLoading = false,
                deviceMediaSync = DeviceMediaSyncUiState(),
                platformMessages = emptyList(),
                cerebellumSettings = com.patrollink.domain.CerebellumSettingsUiState(),
                operationMessage = operationMessage("已退出登录", OperationMessageType.Info)
            )
        }
    }

    private fun startAuthenticatedRefreshes() {
        refreshCurrentUser()
        refreshAlerts()
        refreshScannedDevices(showFailureMessage = false)
        refreshMediaFiles()
        refreshDeviceCapabilities()
        refreshPatrolArea()
        refreshCerebellumSettings()
        startCloudSyncLoop()
    }

    private fun startCloudSyncLoop() {
        val api = patrolRestApi ?: return
        cloudSyncJob?.cancel()
        cloudSyncJob = viewModelScope.launch {
            var pollCount = 0
            while (_uiState.value.isLoggedIn) {
                val state = _uiState.value
                val device = state.device.takeIf { it.id.isNotBlank() && it.online }
                if (device != null) {
                    if (pollCount % HeartbeatEveryPolls == 0) {
                        syncDeviceHeartbeat(api, device)
                    }
                    syncPendingPlatformCommands(api, device)
                }
                syncCloudAlerts()
                syncCloudMessages(api)
                pollCount += 1
                delay(CloudSyncPollMillis)
            }
        }
    }

    private suspend fun syncDeviceHeartbeat(api: PatrolRestApi, device: DeviceStatus) {
        val location = runCatching { locationGateway?.currentLocation() }.getOrNull()
        val request = HeartbeatRequestDto(
            deviceId = device.id,
            online = device.online,
            batteryPercent = device.battery,
            signalBars = device.signalBars,
            recordingStatus = if (device.isRecording) "RECORDING" else "IDLE",
            clientTimestamp = System.currentTimeMillis(),
            latitude = location?.latitude,
            longitude = location?.longitude,
            accuracyMeters = location?.accuracyMeters,
            address = location?.address
        )
        runCatching {
            api.heartbeat(request)
        }.onSuccess {
            _uiState.update { state ->
                state.copy(
                    networkOnline = true,
                    sosLocation = location ?: state.sosLocation,
                    device = if (state.device.id == device.id) state.device.copy(cloudConnected = true) else state.device,
                    connectedDevices = state.connectedDevices.map { if (it.id == device.id) it.copy(cloudConnected = true) else it }
                )
            }
        }.onFailure {
            offlineSyncEngine?.let { engine ->
                runCatching {
                    engine.enqueueHeartbeat(
                        deviceId = device.id,
                        payloadJson = QueuedHeartbeatCodec.encode(QueuedHeartbeat(request)),
                        createdAt = System.currentTimeMillis()
                    )
                }
            }
            _uiState.update { state ->
                state.copy(
                    networkOnline = false,
                    device = if (state.device.id == device.id) state.device.copy(cloudConnected = false) else state.device,
                    connectedDevices = state.connectedDevices.map { if (it.id == device.id) it.copy(cloudConnected = false) else it }
                )
            }
        }
    }

    private suspend fun syncPendingPlatformCommands(api: PatrolRestApi, device: DeviceStatus) {
        val commands = runCatching { api.pendingDeviceCommands(device.id).data }.getOrElse { return }
        commands.forEach { pending ->
            val ackAlreadyQueued = runCatching {
                offlineSyncEngine?.hasPendingDeviceCommandAck(pending.commandId) == true
            }.getOrDefault(false)
            if (ackAlreadyQueued) return@forEach
            val command = pending.command.toDeviceCommandOrNull()
            val result = if (command == null) {
                Result.failure(IllegalArgumentException("当前设备不支持平台指令：${pending.command}"))
            } else {
                runCatching { coordinator.executeRemoteDeviceCommand(_uiState.value.device, command) }
            }
            result.onSuccess { next ->
                updateCurrentDevice(next)
                addDeviceEvent("平台指令执行成功", pending.command, DeviceEventLevel.Info)
            }.onFailure { throwable ->
                addDeviceEvent("平台指令执行失败", "${pending.command}：${throwable.message.orEmpty()}", DeviceEventLevel.Error)
            }
            val succeeded = result.isSuccess
            val ackRequest = DeviceCommandAckRequestDto(
                deviceId = device.id,
                status = if (succeeded) "ACKED" else "FAILED",
                message = result.exceptionOrNull()?.message ?: "设备已执行并确认"
            )
            runCatching {
                api.acknowledgeDeviceCommand(
                    pending.commandId,
                    ackRequest
                )
            }.onFailure {
                val engine = offlineSyncEngine ?: return@onFailure
                val queued = QueuedDeviceCommandAck(pending.commandId, ackRequest)
                runCatching {
                    engine.enqueueDeviceCommandAck(
                        commandId = pending.commandId,
                        payloadJson = QueuedDeviceCommandAckCodec.encode(queued),
                        createdAt = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    private suspend fun syncCloudAlerts() {
        val alerts = runCatching { coordinator.observeAlerts().first() }.getOrElse { return }
        val newAlerts = alerts.filter { it.id !in knownCloudAlertIds }
        if (knownCloudAlertIds.isNotEmpty()) {
            newAlerts.filter { it.status != AlertStatus.Closed }.forEach { alert ->
                notificationGateway?.notifyAlert(alert.title, "${alert.location} · ${alert.description}")
            }
        }
        knownCloudAlertIds += alerts.map { it.id }
        _uiState.update { it.copy(alerts = alerts, networkOnline = true) }
    }

    private suspend fun syncCloudMessages(api: PatrolRestApi) {
        val targetId = _uiState.value.user.badgeNo.takeIf { it.isNotBlank() } ?: return
        val messages = runCatching { api.messages(targetId, 1, 50).data.items }.getOrElse { return }
        val pendingReadIds = runCatching { offlineSyncEngine?.pendingMessageReadIds().orEmpty() }.getOrDefault(emptySet())
        val newMessages = messages.filter { it.messageId !in knownCloudMessageIds }
        if (knownCloudMessageIds.isNotEmpty()) {
            newMessages.forEach { message ->
                notificationGateway?.notifyAlert("指挥消息：${message.title}", message.content)
            }
        }
        knownCloudMessageIds += messages.map { it.messageId }
        _uiState.update { state ->
            state.copy(
                platformMessages = messages.map { message ->
                    PatrolCommandMessage(
                        id = message.messageId,
                        title = message.title,
                        content = message.content,
                        sentAt = message.sentAt,
                        read = message.readAt.isNotBlank() || message.status.equals("READ", ignoreCase = true) || message.messageId in pendingReadIds
                    )
                }
            )
        }
    }

    fun markPlatformMessageRead(messageId: String) = viewModelScope.launch {
        val api = patrolRestApi ?: return@launch
        runCatching { api.readMessage(messageId) }
            .onSuccess {
                _uiState.update { state ->
                    state.copy(platformMessages = state.platformMessages.map { message ->
                        if (message.id == messageId) message.copy(read = true) else message
                    })
                }
            }
            .onFailure { throwable ->
                val engine = offlineSyncEngine
                if (engine == null) {
                    showOperationMessage(throwable.message ?: "消息已读回执失败", OperationMessageType.Error)
                    return@onFailure
                }
                runCatching { engine.enqueueMessageRead(messageId, System.currentTimeMillis()) }
                    .onSuccess {
                        _uiState.update { state ->
                            state.copy(
                                platformMessages = state.platformMessages.map { message ->
                                    if (message.id == messageId) message.copy(read = true) else message
                                },
                                operationMessage = operationMessage("网络不可用，消息已读回执已进入补传队列", OperationMessageType.Warning)
                            )
                        }
                    }
                    .onFailure { queueError ->
                        showOperationMessage(queueError.message ?: "消息已读回执保存失败", OperationMessageType.Error)
                    }
            }
    }

    private fun String.toDeviceCommandOrNull(): DeviceCommand? = when (uppercase()) {
        "TAKE_PHOTO" -> DeviceCommand.TakePhoto
        "START_RECORD" -> DeviceCommand.StartRecord
        "STOP_RECORD" -> DeviceCommand.StopRecord
        // 当前硬件 SDK 没有双向实时音频接口，不把 START_TALK/STOP_TALK 伪装成成功。
        else -> null
    }

    private fun scheduleSessionRefresh(session: AuthSession) {
        if (appContext == null) return
        sessionRefreshJob?.cancel()
        sessionRefreshJob = viewModelScope.launch {
            var activeSession = session
            var waitMillis = sessionRefreshDelayMillis(activeSession)
            while (true) {
                delay(waitMillis)
                val refreshed = runCatching { coordinator.refreshSession(activeSession.refreshToken) }.getOrNull()
                if (refreshed == null) {
                    waitMillis = SessionRefreshRetryMillis
                    continue
                }
                activeSession = refreshed
                onSessionChanged(refreshed)
                secureStore?.let { store -> withContext(Dispatchers.IO) { store.saveSession(refreshed) } }
                runCatching { coordinator.connectRealtime(refreshed.accessToken) }
                waitMillis = sessionRefreshDelayMillis(refreshed)
            }
        }
    }

    private fun sessionRefreshDelayMillis(session: AuthSession): Long =
        (session.expiresInSeconds - SessionRefreshThresholdSeconds)
            .coerceIn(1L, MaxSessionRefreshDelaySeconds) * 1_000L

    private fun refreshAlerts() = viewModelScope.launch {
        runCatching { coordinator.observeAlerts().first() }
            .onSuccess { alerts -> _uiState.update { it.copy(alerts = alerts) } }
    }

    fun toggleRecord() = viewModelScope.launch {
        runDeviceCommandWithOverlay("正在等待录像指令回复") {
        val device = prepareDeviceForCommand(
            capabilityReady = { it.supportsVideo },
            unavailableMessage = "录像失败，耳机控制通道未就绪"
        ) ?: return@runDeviceCommandWithOverlay
        val enabled = !device.isRecording
        runCatching { coordinator.setRecording(device, enabled) }
            .onSuccess { next ->
                updateCurrentDevice(next.copy(isRecording = enabled))
                addDeviceEvent(
                    title = if (enabled) "录像已开始" else "录像已停止",
                    detail = device.name,
                    level = DeviceEventLevel.Info
                )
            }
            .onFailure { throwable ->
                val detail = throwable.message?.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
                _uiState.update { state ->
                    val title = "录像控制失败"
                    state.copy(
                        deviceEvents = (listOf(newDeviceEvent(title, "请确认耳机已配对并连接控制通道$detail", DeviceEventLevel.Error)) + state.deviceEvents).take(MaxDeviceEvents),
                        operationMessage = operationMessage("$title，请确认耳机已配对并连接控制通道$detail", OperationMessageType.Error)
                    )
                }
            }
        }
    }

    fun toggleTalk() = viewModelScope.launch {
        runDeviceCommandWithOverlay("正在等待录音指令回复") {
        val device = prepareDeviceForCommand(
            capabilityReady = { it.supportsAudioRecord },
            unavailableMessage = "录音失败，当前设备不支持录音或控制通道未就绪"
        ) ?: return@runDeviceCommandWithOverlay
        val enabled = !device.isTalking
        runCatching {
            // 这里是设备端录音，不是 App/WebRTC 实时对讲。所有硬件类型都必须走设备真实指令。
            coordinator.setDeviceTalk(device, enabled)
        }
            .onSuccess { next ->
                updateCurrentDevice(next.copy(isTalking = enabled))
                addDeviceEvent(
                    title = if (enabled) "录音已开始" else "录音已停止",
                    detail = device.name,
                    level = DeviceEventLevel.Info
                )
                if (!enabled) refreshMediaFiles()
            }
            .onFailure { throwable ->
                val detail = throwable.message?.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
                _uiState.update { state ->
                    state.copy(
                        deviceEvents = (listOf(newDeviceEvent("录音控制失败", "请确认耳机已配对并连接控制通道$detail", DeviceEventLevel.Error)) + state.deviceEvents).take(MaxDeviceEvents),
                        operationMessage = operationMessage("录音控制失败，请确认耳机已配对并连接控制通道$detail", OperationMessageType.Error)
                    )
                }
            }
        }
    }

    private fun observeIntercomState() {
        val intercomState = coordinator.intercomState() ?: return
        viewModelScope.launch {
            intercomState.collect { state ->
                val talking = when (state) {
                    IntercomState.Idle,
                    IntercomState.Closed,
                    IntercomState.Failed -> false
                    IntercomState.WaitingApp,
                    IntercomState.Signaling,
                    IntercomState.Active -> true
                }
                _uiState.update { current ->
                    val message = when (state) {
                        IntercomState.Closed -> operationMessage("对讲已结束", OperationMessageType.Info)
                        IntercomState.Failed -> operationMessage("对讲连接失败", OperationMessageType.Error)
                        else -> current.operationMessage
                    }
                    if (current.device.isTalking == talking && message == current.operationMessage) {
                        current
                    } else {
                        current.copy(device = current.device.copy(isTalking = talking), operationMessage = message)
                    }
                }
            }
        }
    }

    fun refreshDeviceCapabilities() = viewModelScope.launch {
        val device = _uiState.value.device
        if (!device.canReceiveDeviceCommand()) {
            _uiState.update { it.copy(deviceCapabilities = DeviceCapabilities(), deviceWifiState = DeviceWifiState()) }
            return@launch
        }
        deviceControlGateway?.let { gateway ->
            runCatching {
                val (checkedDevice, capabilities) = ensureDeviceControlCapabilities(device, gateway, showMessage = false)
                val wifi = if (capabilities.supportsWifi) gateway.readWifi() else DeviceWifiState()
                _uiState.update {
                    it.copy(
                        device = checkedDevice,
                        deviceCapabilities = capabilities,
                        deviceWifiState = wifi
                    )
                }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        deviceCapabilities = DeviceCapabilities(),
                        deviceWifiState = DeviceWifiState(),
                        operationMessage = operationMessage("设备 SDK 能力读取失败", OperationMessageType.Error)
                    )
                }
            }
        }
    }

    fun runDeviceSelfCheck() = viewModelScope.launch {
        runDeviceCommandWithOverlay("正在执行设备自检") {
        val device = _uiState.value.device
        if (!device.canReceiveDeviceCommand()) {
            showOperationMessage("请先连接设备后再执行自检", OperationMessageType.Warning)
            return@runDeviceCommandWithOverlay
        }
        val refreshedDevice = runCatching { coordinator.bindDevice(device.id) }.getOrNull()
        if (refreshedDevice != null) updateCurrentDevice(refreshedDevice)
        val checkedDevice = refreshedDevice ?: _uiState.value.device
        val capabilities = deviceControlGateway?.let { gateway ->
            runCatching { gateway.capabilities(checkedDevice) }.getOrNull()
        } ?: _uiState.value.deviceCapabilities
        val wifi = deviceControlGateway?.let { gateway ->
            if (capabilities.supportsWifi) runCatching { gateway.readWifi() }.getOrNull() else DeviceWifiState()
        } ?: _uiState.value.deviceWifiState
        val localCount = runCatching { coordinator.mediaFiles(local = true).size }.getOrDefault(0)
        val deviceCount = runCatching { coordinator.mediaFiles(local = false).size }.getOrDefault(0)
        refreshMediaFiles()
        val eventTitle = "设备自检完成"
        val eventDetail = "电量 ${_uiState.value.device.batteryTextForMessage()}，存储 ${_uiState.value.device.storageTextForMessage()}，本地媒体 $localCount，设备媒体 $deviceCount"
        _uiState.update { state ->
            state.copy(
                deviceEvents = (listOf(newDeviceEvent(eventTitle, eventDetail, DeviceEventLevel.Info)) + state.deviceEvents).take(MaxDeviceEvents),
                deviceCapabilities = capabilities,
                deviceWifiState = wifi
            )
        }
        }
    }

    fun configureDeviceWifi(enabled: Boolean, ssid: String, password: String) = viewModelScope.launch {
        val gateway = deviceControlGateway ?: return@launch showOperationMessage("设备 Wi-Fi 通道未启用", OperationMessageType.Warning)
        runCatching { gateway.configureWifi(enabled, ssid, password) }
            .onSuccess { wifi -> _uiState.update { it.copy(deviceWifiState = wifi, operationMessage = operationMessage("设备 Wi-Fi 已配置", OperationMessageType.Success)) } }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(operationMessage = operationMessage(throwable.operatorFacingWifiError(), OperationMessageType.Error))
                }
            }
    }

    fun applyDeviceSettings(settings: DeviceAdvancedSettings) = viewModelScope.launch {
        val device = _uiState.value.device
        val gateway = deviceControlGateway ?: return@launch showOperationMessage("设备参数通道未启用", OperationMessageType.Warning)
        runCatching { gateway.applySettings(device, settings) }
            .onSuccess { next -> _uiState.update { it.copy(deviceSettings = next, operationMessage = operationMessage("设备参数已下发", OperationMessageType.Success)) } }
            .onFailure { _uiState.update { it.copy(operationMessage = operationMessage("设备参数下发失败", OperationMessageType.Error)) } }
    }

    fun notifyDeviceMediaSyncCompleted() = viewModelScope.launch {
        val ok = runCatching { deviceControlGateway?.notifyMediaSyncCompleted() == true }.getOrDefault(false)
        _uiState.update {
            it.copy(
                operationMessage = if (ok) {
                    operationMessage("已通知设备媒体同步完成", OperationMessageType.Success)
                } else {
                    operationMessage("通知设备同步完成失败", OperationMessageType.Error)
                }
            )
        }
    }

    fun setAlertTab(status: AlertStatus) = _uiState.update { it.copy(selectedAlertTab = status) }

    fun setMediaLocal(local: Boolean) = _uiState.update { it.copy(selectedMediaLocal = local) }

    fun setFontSizeMode(mode: FontSizeMode) {
        settingsStore?.saveFontSizeMode(mode)
        _uiState.update { it.copy(fontSizeMode = mode, operationMessage = operationMessage("字体大小已保存", OperationMessageType.Success)) }
    }

    fun setDisplayThemeMode(mode: DisplayThemeMode) {
        settingsStore?.saveDisplayThemeMode(mode)
        _uiState.update { it.copy(displayThemeMode = mode, operationMessage = operationMessage("主题模式已保存", OperationMessageType.Success)) }
    }

    fun refreshCurrentLocation() = viewModelScope.launch {
        val gateway = locationGateway ?: return@launch
        if (_uiState.value.locationFetchStatus == LocationFetchStatus.Loading) return@launch
        _uiState.update { it.copy(locationFetchStatus = LocationFetchStatus.Loading) }
        runCatching { gateway.currentLocation() }
            .onSuccess { location ->
                _uiState.update {
                    it.copy(
                        sosLocation = location,
                        locationFetchStatus = if (location.hasUsableCoordinate()) {
                            LocationFetchStatus.Available
                        } else {
                            LocationFetchStatus.Unavailable
                        }
                    )
                }
            }
            .onFailure {
                _uiState.update { it.copy(locationFetchStatus = LocationFetchStatus.Unavailable) }
            }
    }

    fun refreshPatrolArea() = viewModelScope.launch {
        runCatching { coordinator.currentPatrolArea() }
            .onSuccess { patrolArea ->
                _uiState.update { state ->
                    state.copy(
                        patrolArea = patrolArea,
                        user = state.user.withDutyArea(patrolArea.name)
                    )
                }
            }
    }

    fun refreshCurrentUser() = viewModelScope.launch {
        runCatching { coordinator.currentUser() }
            .onSuccess { user ->
                _uiState.update { state ->
                    state.copy(user = user.withDutyArea(state.patrolArea.name))
                }
            }
    }

    fun updateDailyReportMissionId(missionId: String) = _uiState.update { state ->
        state.copy(dailyReport = state.dailyReport.copy(missionId = missionId, lastError = null))
    }

    fun updateDailyReportOperatorNote(note: String) = _uiState.update { state ->
        state.copy(dailyReport = state.dailyReport.copy(operatorNote = note, lastError = null))
    }

    fun updateDailyReportContent(content: String) = _uiState.update { state ->
        val report = state.dailyReport.report ?: return@update state
        state.copy(dailyReport = state.dailyReport.copy(report = report.copy(content = content), lastError = null))
    }

    fun saveDailyReportContent() = viewModelScope.launch {
        val report = _uiState.value.dailyReport.report ?: return@launch
        val reportId = report.reportId ?: return@launch showOperationMessage("日报编号缺失，无法保存正文", OperationMessageType.Warning)
        val api = patrolRestApi ?: return@launch showOperationMessage("后台服务未配置，无法保存正文", OperationMessageType.Warning)
        _uiState.update { state -> state.copy(dailyReport = state.dailyReport.copy(contentSaving = true, lastError = null)) }
        runCatching { api.updateDailyReportContent(reportId, DailyReportContentUpdateDto(report.content)) }
            .onSuccess {
                _uiState.update { state ->
                    state.copy(
                        dailyReport = state.dailyReport.copy(contentSaving = false),
                        operationMessage = operationMessage("日报正文已保存", OperationMessageType.Success)
                    )
                }
            }
            .onFailure { throwable ->
                val message = throwable.message?.takeIf { it.isNotBlank() } ?: "日报正文保存失败"
                _uiState.update { state ->
                    state.copy(
                        dailyReport = state.dailyReport.copy(contentSaving = false, lastError = message),
                        operationMessage = operationMessage("日报正文保存失败", OperationMessageType.Error)
                    )
                }
            }
    }

    fun toggleDailyReportMedia(fileId: String) = _uiState.update { state ->
        val selected = state.dailyReport.selectedMediaIds
        state.copy(
            dailyReport = state.dailyReport.copy(
                selectedMediaIds = if (fileId in selected) selected - fileId else selected + fileId,
                lastError = null
            )
        )
    }

    fun clearDailyReportMediaSelection() = _uiState.update { state ->
        state.copy(dailyReport = state.dailyReport.copy(selectedMediaIds = emptySet(), lastError = null))
    }

    fun refreshMediaFiles(showFailureMessage: Boolean = false) = viewModelScope.launch {
        _uiState.update { it.copy(mediaLoading = true) }
        val viewingPhone = _uiState.value.selectedMediaLocal
        val existingDeviceMedia = _uiState.value.mediaFiles.filter { !it.local }
        val phoneResult = runCatching { coordinator.mediaFiles(local = true) }
        val deviceResult = if (viewingPhone) {
            Result.success(existingDeviceMedia)
        } else {
            runCatching { coordinator.mediaFiles(local = false) }
        }
        val phoneMedia = phoneResult.getOrDefault(emptyList())
        val deviceMedia = if (viewingPhone) existingDeviceMedia else deviceResult.getOrDefault(emptyList())
        val loaded = (phoneMedia + deviceMedia).distinctBy { it.id to it.local }
        _uiState.update { state ->
            val loadedWithState = loaded.map { incoming ->
                val current = state.mediaFiles.firstOrNull { it.id == incoming.id && it.local == incoming.local }
                incoming.inheritCompletedCloudState(current)
            }
            val transient = state.mediaFiles.filter { current ->
                current.transferStatus.inProgress &&
                    loadedWithState.none { it.id == current.id && it.local == current.local }
            }
            val next = (loadedWithState + transient)
                .distinctBy { it.id to it.local }
                .markDeviceFilesPresentInPhoneSandbox()
            val shouldReturnToPhoneMedia =
                !showFailureMessage &&
                    !viewingPhone &&
                    phoneMedia.isNotEmpty() &&
                    (deviceResult.isFailure || deviceMedia.isEmpty())
            val nextMediaLocal = if (shouldReturnToPhoneMedia) true else state.selectedMediaLocal
            val nextSelected = state.selectedMediaFileId?.takeIf { selectedId ->
                next.any { it.id == selectedId && it.local == nextMediaLocal }
            } ?: next.firstOrNull { it.local == nextMediaLocal }?.id
            state.copy(
                mediaFiles = next,
                selectedMediaFileId = nextSelected,
                selectedMediaLocal = nextMediaLocal,
                mediaLoading = false,
                operationMessage = when {
                    showFailureMessage && !state.selectedMediaLocal && deviceResult.isFailure ->
                        operationMessage(deviceResult.exceptionOrNull().operatorFacingDeviceMediaError(), OperationMessageType.Error)
                    showFailureMessage && !state.selectedMediaLocal && deviceResult.isSuccess && deviceMedia.isEmpty() ->
                        operationMessage("设备端没有读取到媒体文件；请先拍照/录像/录音，或确认手机已连接设备热点后重试", OperationMessageType.Warning)
                    else -> state.operationMessage
                }
            )
        }
    }

    fun updateCerebellumBaseUrl(baseUrl: String) = _uiState.update { state ->
        state.copy(cerebellumSettings = state.cerebellumSettings.copy(baseUrl = baseUrl))
    }

    fun updateCerebellumApiKey(apiKey: String) = _uiState.update { state ->
        state.copy(cerebellumSettings = state.cerebellumSettings.copy(apiKey = apiKey))
    }

    fun saveCerebellumSettings() = viewModelScope.launch {
        val settings = _uiState.value.cerebellumSettings
        val baseUrl = settings.baseUrl.trim().trimEnd('/')
        val apiKey = settings.apiKey.trim()
        val nextApi = if (baseUrl.isBlank()) {
            null
        } else {
            runCatching {
                cerebellumApiFactory(baseUrl, apiKey)
                    ?: error("小脑地址格式不正确")
            }.getOrElse {
                return@launch showOperationMessage("小脑地址格式不正确", OperationMessageType.Error)
            }
        }
        _uiState.update { state -> state.copy(cerebellumSettings = state.cerebellumSettings.copy(saving = true)) }
        val localSaved = runCatching {
            runtimeConfigStore?.saveCerebellumSettings(baseUrl = baseUrl, apiKey = apiKey)
                ?: com.patrollink.data.CerebellumRuntimeSettings(baseUrl = baseUrl.trim().trimEnd('/'), apiKey = apiKey)
        }.getOrElse { throwable ->
            _uiState.update { state ->
                state.copy(
                    cerebellumSettings = state.cerebellumSettings.copy(saving = false),
                    operationMessage = operationMessage(
                        throwable.message?.takeIf { it.isNotBlank() } ?: "小脑连接设置保存失败",
                        OperationMessageType.Error
                    )
                )
            }
            return@launch
        }
        cerebellumApi = nextApi
        _uiState.update { state ->
            state.copy(
                cerebellumSettings = state.cerebellumSettings.copy(baseUrl = localSaved.baseUrl, apiKey = localSaved.apiKey, saving = false),
                operationMessage = operationMessage(
                    if (localSaved.baseUrl.isBlank()) "小脑连接已清空" else "小脑连接设置已保存",
                    OperationMessageType.Success
                )
            )
        }
        val api = patrolRestApi ?: return@launch
        runCatching { api.saveCerebellumSettings(CerebellumSettingsDto(baseUrl = localSaved.baseUrl, apiKey = localSaved.apiKey)).data }
            .onSuccess { saved ->
                _uiState.update { state ->
                    state.copy(
                        cerebellumSettings = state.cerebellumSettings.copy(baseUrl = saved.baseUrl, apiKey = saved.apiKey, saving = false),
                        operationMessage = operationMessage(
                            if (saved.baseUrl.isBlank()) "小脑连接已清空" else "小脑连接设置已保存",
                            OperationMessageType.Success
                        )
                    )
                }
            }
            .onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(
                        cerebellumSettings = state.cerebellumSettings.copy(saving = false),
                        operationMessage = operationMessage(
                            throwable.message?.takeIf { it.isNotBlank() } ?: "小脑连接已保存到本机，后台同步失败",
                            OperationMessageType.Warning
                        )
                    )
                }
            }
    }

    private fun refreshCerebellumSettings() = viewModelScope.launch {
        runtimeConfigStore?.readCerebellumSettings()?.let { local ->
            applyCerebellumSettings(local.baseUrl, local.apiKey)
        }
        val api = patrolRestApi ?: return@launch
        runCatching { api.cerebellumSettings().data }
            .onSuccess { settings ->
                if (_uiState.value.cerebellumSettings.baseUrl.isBlank()) {
                    applyCerebellumSettings(settings.baseUrl, settings.apiKey)
                }
            }
    }

    private fun applyCerebellumSettings(baseUrl: String, apiKey: String) {
        val normalizedBaseUrl = baseUrl.trim()
        val normalizedApiKey = apiKey.trim()
        cerebellumApi = if (normalizedBaseUrl.isBlank()) {
            null
        } else {
            runCatching {
                cerebellumApiFactory(normalizedBaseUrl, normalizedApiKey)
            }.getOrNull()
        }
        _uiState.update { state ->
            state.copy(
                cerebellumSettings = state.cerebellumSettings.copy(
                    baseUrl = normalizedBaseUrl,
                    apiKey = normalizedApiKey,
                    saving = false
                )
            )
        }
    }

    fun generateDailyReport() = viewModelScope.launch {
        val api = cerebellumApi ?: return@launch showOperationMessage("小脑服务地址未配置", OperationMessageType.Warning)
        val current = _uiState.value
        val missionId = current.dailyReport.missionId.ifBlank { defaultMissionId(current.user.badgeNo) }
        val reportableMedia = current.mediaFiles.reportableMedia()
        val selectedMedia = current.mediaFiles.filter { it.id in current.dailyReport.selectedMediaIds }
            .ifEmpty { reportableMedia }
            .preferPhoneCopies()
        _uiState.update { state ->
            state.copy(
                dailyReport = state.dailyReport.copy(
                    missionId = missionId,
                    generating = true,
                    lastError = null
                ),
                operationMessage = operationMessage("正在请求小脑生成日报", OperationMessageType.Info)
            )
        }
        runCatching {
            val uploadableMedia = selectedMedia.mapNotNull { file ->
                val localReady = ensureMediaFileForCerebellum(file)
                localReady?.let { ready ->
                    localFileForCerebellumUpload(ready, appContext, backendBaseUrl, secureStore)?.let { localFile -> ready to localFile }
                }
            }.distinctBy { (file, _) -> file.id }
            val skippedMedia = selectedMedia.filterNot { selected ->
                uploadableMedia.any { (file, _) -> file.id == selected.id }
            }
            val uploadedMedia = uploadableMedia.map { (file, localFile) ->
                api.uploadFile(
                    file = localFile,
                    missionId = missionId,
                    evidenceType = file.kind.toCerebellumEvidenceType(),
                    note = "App选择日报分析：${file.name}",
                    register = true
                )
            }
            val note = dailyReportOperatorNote(
                current.dailyReport.operatorNote,
                selectedMedia = selectedMedia,
                skippedMedia = skippedMedia
            )
            api.createReport(
                CerebellumReportRequestDto(
                    missionId = missionId,
                    reportType = "daily",
                    preferQuality = true,
                    operatorNote = note,
                    selectedMediaIds = uploadedMedia.mapNotNull { it.evidence?.evidenceId },
                    selectedMediaUris = uploadedMedia.mapNotNull { it.evidence?.sourceUri ?: it.file.fileUri },
                    includeTodayMediaDefault = uploadedMedia.isEmpty(),
                    submitToBackend = true,
                    operatorId = current.user.badgeNo,
                    officerName = current.user.name,
                    deviceId = current.device.id
                )
            ).report
        }.onSuccess { report ->
            _uiState.update { state ->
                state.copy(
                    dailyReport = state.dailyReport.copy(
                        generating = false,
                        report = DailyReport(
                            reportId = report.reportId,
                            missionId = report.missionId,
                            generatedAt = report.generatedAt,
                            content = report.content,
                            backend = report.backend,
                            model = report.model,
                            requiresHumanConfirmation = report.requiresHumanConfirmation
                        ),
                        lastError = null
                    ),
                    operationMessage = operationMessage("小脑日报已生成", OperationMessageType.Success)
                )
            }
        }.onFailure { throwable ->
            val message = throwable.message?.takeIf { it.isNotBlank() } ?: "小脑日报生成失败"
            _uiState.update { state ->
                state.copy(
                    dailyReport = state.dailyReport.copy(generating = false, lastError = message),
                    operationMessage = operationMessage("小脑日报生成失败", OperationMessageType.Error)
                )
            }
        }
    }

    fun refreshCerebellumFiles() = viewModelScope.launch {
        val api = cerebellumApi ?: return@launch showOperationMessage("小脑服务地址未配置", OperationMessageType.Warning)
        runCatching { api.listFiles() }
            .onSuccess { files ->
                _uiState.update { state ->
                    state.copy(
                        cerebellumSettings = state.cerebellumSettings.copy(
                            lastFileCount = files.count,
                            lastFileNames = files.files.take(3).map { it.fileName }
                        ),
                        operationMessage = operationMessage("小脑文件 ${files.count} 个，可用于日报分析或证据登记", OperationMessageType.Success)
                    )
                }
            }
            .onFailure {
                showOperationMessage("读取小脑文件失败", OperationMessageType.Error)
            }
    }

    fun checkCerebellumHealth() = viewModelScope.launch {
        val api = cerebellumApi ?: return@launch showOperationMessage("小脑服务地址未配置", OperationMessageType.Warning)
        runCatching { api.health() }
            .onSuccess { health ->
                _uiState.update { state ->
                    state.copy(
                        cerebellumSettings = state.cerebellumSettings.copy(
                            healthStatus = "${health.status} · ${health.deviceId}",
                            healthDetail = "主模型 ${health.primaryModel} · 已运行 ${health.uptimeSeconds.toUptimeLabel()}"
                        ),
                        operationMessage = operationMessage("小脑健康检查通过", OperationMessageType.Success)
                    )
                }
            }
            .onFailure {
                showOperationMessage("小脑健康检查失败", OperationMessageType.Error)
            }
    }

    fun sendCerebellumCommand(command: String) = viewModelScope.launch {
        val api = cerebellumApi ?: return@launch showOperationMessage("小脑服务地址未配置", OperationMessageType.Warning)
        runCatching {
            api.sendCommand(
                com.patrollink.data.edge.CerebellumCommandRequestDto(
                    command = command,
                    requestId = "app-${System.currentTimeMillis()}",
                    operatorId = _uiState.value.user.badgeNo
                )
            )
        }.onSuccess {
            val resultText = "${command.toCerebellumCommandLabel()}已下发 · ${if (it.accepted) "已接受" else "待确认"}"
            _uiState.update { state ->
                val settings = when (command) {
                    "refresh_files" -> state.cerebellumSettings.copy(lastFileCommandResult = resultText)
                    "sync_face_library" -> state.cerebellumSettings.copy(lastFaceLibrarySyncResult = resultText)
                    else -> state.cerebellumSettings
                }
                state.copy(
                    cerebellumSettings = settings,
                    operationMessage = operationMessage("小脑指令已下发：${command.toCerebellumCommandLabel()}", OperationMessageType.Success)
                )
            }
        }.onFailure {
            showOperationMessage("小脑指令下发失败", OperationMessageType.Error)
        }
    }

    fun closeAlert(
        alertId: String,
        result: AlertResult = AlertResult.Resolved,
        note: String = "",
        attachments: List<UploadAttachmentDto> = emptyList()
    ) = viewModelScope.launch {
        val payload = alertSubmitPayload(alertId, result, note, attachments)
        runCatching {
            coordinator.handleAlert(alertId, result, note, payload.attachments.map { it.toDomainAttachment() })
        }.onSuccess {
            _uiState.update {
                it.copy(alerts = it.alerts.map { alert ->
                    if (alert.id == alertId) alert.copy(status = AlertStatus.Closed) else alert
                }, operationMessage = operationMessage("处置结果已上传：${payload.attachments.size} 个附件", OperationMessageType.Success))
            }
        }.onFailure { throwable ->
            val engine = offlineSyncEngine
            if (engine == null) {
                showOperationMessage(throwable.message ?: "处置结果上传失败", OperationMessageType.Error)
                return@onFailure
            }
            val queued = QueuedAlertDisposition(
                alertId = alertId,
                result = payload.result,
                note = payload.note,
                operatorId = payload.operatorId,
                attachments = payload.attachments
            )
            runCatching {
                engine.enqueueAlertDisposition(
                    alertId = alertId,
                    payloadJson = QueuedAlertDispositionCodec.encode(queued),
                    createdAt = System.currentTimeMillis()
                )
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        alerts = state.alerts.map { alert ->
                            if (alert.id == alertId) alert.copy(status = AlertStatus.Handling) else alert
                        },
                        operationMessage = operationMessage("网络不可用，处置结果已进入离线补传队列", OperationMessageType.Warning)
                    )
                }
            }.onFailure { queueError ->
                showOperationMessage(queueError.message ?: "处置结果保存失败", OperationMessageType.Error)
            }
        }
    }

    fun saveAlertDraft(
        alertId: String,
        result: AlertResult,
        note: String,
        attachments: List<UploadAttachmentDto> = emptyList()
    ) {
        val payload = alertDraftPayload(alertId, result, note, attachments)
        val resultLabel = when (result) {
            AlertResult.Questioned -> "已盘问"
            AlertResult.TakenAway -> "已带离"
            AlertResult.Resolved -> "已处置"
            AlertResult.FalseAlarm -> "误报"
            AlertResult.RequestBackup -> "请求增援"
        }
        _uiState.update { it.copy(operationMessage = operationMessage("草稿已保存：$resultLabel，${payload.attachments.size} 个附件待提交", OperationMessageType.Warning)) }
    }

    private fun alertDraftPayload(
        alertId: String,
        result: AlertResult,
        note: String,
        attachments: List<UploadAttachmentDto>
    ) = AlertDraftRequestDto(
        alertId = alertId,
        result = result.toApiValue(),
        note = note,
        operatorId = _uiState.value.user.badgeNo,
        attachments = attachments
    )

    private fun alertSubmitPayload(
        alertId: String,
        result: AlertResult,
        note: String,
        attachments: List<UploadAttachmentDto>
    ) = AlertDraftRequestDto(
        alertId = alertId,
        result = result.toApiValue(),
        note = note,
        operatorId = _uiState.value.user.badgeNo,
        attachments = attachments.map { it.copy(uploadIntent = "UPLOAD_NOW") }
    )

    private fun AlertResult.toApiValue(): String = when (this) {
        AlertResult.Questioned -> "QUESTIONED"
        AlertResult.TakenAway -> "TAKEN_AWAY"
        AlertResult.FalseAlarm -> "FALSE_ALARM"
        AlertResult.Resolved -> "RESOLVED"
        AlertResult.RequestBackup -> "REQUEST_BACKUP"
    }

    private fun UploadAttachmentDto.toDomainAttachment() = AlertAttachment(
        clientFileId = clientFileId,
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        source = source,
        localUri = localUri,
        uploadIntent = uploadIntent
    )

    fun activateSos() = viewModelScope.launch {
        val location = locationGateway?.currentLocation() ?: _uiState.value.sosLocation
        val clientSosId = "SOS-APP-${UUID.randomUUID()}"
        _uiState.update { it.copy(sosLocation = location) }
        val activation = runCatching { coordinator.activateSos(location, clientSosId) }
        val queued = activation.exceptionOrNull()?.let {
            val engine = offlineSyncEngine ?: return@let false
            val deviceId = _uiState.value.device.id.takeIf { id -> id.isNotBlank() }
            val payload = QueuedSosSync(
                clientSosId = clientSosId,
                action = "ACTIVATE",
                location = GpsLocationDto(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracyMeters,
                    address = location.address,
                    deviceId = deviceId,
                    clientEventId = clientSosId
                )
            )
            runCatching {
                engine.enqueueSosState(
                    clientSosId = clientSosId,
                    payloadJson = QueuedSosSyncCodec.encode(payload),
                    action = payload.action,
                    createdAt = System.currentTimeMillis()
                )
            }.isSuccess
        } ?: false
        activeSosId = activation.getOrNull()?.id?.takeIf { it.isNotBlank() } ?: clientSosId
        activeSosActivationQueued = activation.isFailure && queued
        val recordingStarted = sosEvidenceRecorder?.let { recorder ->
            runCatching { recorder.start(activeSosId!!) }.isSuccess
        } ?: false
        runCatching { notificationGateway?.notifySosActive(location) }
        _uiState.update { state ->
            state.copy(
                sosActive = true,
                networkOnline = activation.isSuccess || state.networkOnline,
                operationMessage = when {
                    activation.isSuccess && recordingStarted -> operationMessage("SOS 已送达平台，现场录音已开始", OperationMessageType.Success)
                    activation.isSuccess -> operationMessage("SOS 已送达平台，但现场录音启动失败", OperationMessageType.Warning)
                    queued && recordingStarted -> operationMessage("网络不可用，SOS 已进入补传队列并开始本地录音", OperationMessageType.Warning)
                    queued -> operationMessage("网络不可用，SOS 已进入补传队列，但现场录音启动失败", OperationMessageType.Warning)
                    else -> operationMessage("SOS 平台上报和本地补传均失败，请保持安全并立即重试", OperationMessageType.Error)
                }
            )
        }
    }

    fun cancelSos() = viewModelScope.launch {
        val sosId = activeSosId
        val recording = runCatching { sosEvidenceRecorder?.stop() }.getOrNull()
        val cancelResult = runCatching { coordinator.cancelSos() }
        val requiresOrderedCancel = activeSosActivationQueued
        val cancelQueued = if ((cancelResult.isFailure || requiresOrderedCancel) && !sosId.isNullOrBlank()) {
            val engine = offlineSyncEngine
            val payload = QueuedSosSync(clientSosId = sosId, action = "CANCEL")
            engine != null && runCatching {
                engine.enqueueSosState(
                    clientSosId = sosId,
                    payloadJson = QueuedSosSyncCodec.encode(payload),
                    action = payload.action,
                    createdAt = System.currentTimeMillis() + 1L
                )
            }.isSuccess
        } else {
            false
        }
        if ((cancelResult.isFailure || requiresOrderedCancel) && !cancelQueued) {
            _uiState.update {
                it.copy(
                    sosActive = true,
                    operationMessage = operationMessage("SOS 取消未送达平台且无法加入补传，请重试", OperationMessageType.Error)
                )
            }
            return@launch
        }
        val recordingFile = recording?.filePath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() && it.length() > 0L }
        val uploaded = recordingFile
            ?.let { file -> runCatching { coordinator.uploadLocalEvidence(file, "SOS_AUDIO", sosId.orEmpty()) }.getOrNull() }
        val recordingQueued = if (recordingFile != null && uploaded == null && !sosId.isNullOrBlank()) {
            val engine = offlineSyncEngine
            val payload = QueuedSosEvidence(sosId, recordingFile.absolutePath)
            engine != null && runCatching {
                engine.enqueueSosEvidence(
                    clientSosId = sosId,
                    payloadJson = QueuedSosEvidenceCodec.encode(payload),
                    createdAt = System.currentTimeMillis() + 2L
                )
            }.isSuccess
        } else {
            false
        }
        activeSosId = null
        activeSosActivationQueued = false
        _uiState.update {
            it.copy(
                sosActive = false,
                operationMessage = when {
                    cancelQueued && recordingQueued -> operationMessage("SOS 取消与现场录音均已进入补传队列", OperationMessageType.Warning)
                    cancelQueued && uploaded != null -> operationMessage("SOS 取消已进入补传队列，现场录音已上传", OperationMessageType.Warning)
                    cancelQueued -> operationMessage("SOS 取消已进入补传队列，未取得可上传录音", OperationMessageType.Warning)
                    recording == null -> operationMessage("SOS 已取消，但未取得现场录音", OperationMessageType.Warning)
                    uploaded == null && recordingQueued -> operationMessage("SOS 已取消，现场录音已进入补传队列", OperationMessageType.Warning)
                    uploaded == null -> operationMessage("SOS 已取消，现场录音保留在本机但补传入队失败", OperationMessageType.Warning)
                    else -> operationMessage("SOS 已取消，现场录音已关联上传", OperationMessageType.Success)
                }
            )
        }
    }

    fun takePhoto() {
        val now = System.currentTimeMillis()
        if (photoCaptureJob?.isActive == true) {
            showOperationMessage("正在处理上一张照片，请等待回传完成", OperationMessageType.Warning)
            return
        }
        if (now - lastPhotoCaptureRequestAt < PhotoCaptureTapDebounceMillis) return
        lastPhotoCaptureRequestAt = now
        photoCaptureJob = viewModelScope.launch {
            runDeviceCommandWithOverlay("正在等待拍照指令回复") {
            _uiState.update { state ->
                state.copy(
                    photoCaptureInProgress = true
                )
            }
            try {
                val device = prepareDeviceForCommand(
                    capabilityReady = { it.supportsPhoto },
                    unavailableMessage = "拍照失败，耳机控制通道未就绪"
                ) ?: return@runDeviceCommandWithOverlay
                val commandStartedAt = System.currentTimeMillis()
                val next = runCatching { coordinator.takePhoto(device) }
                    .getOrElse { throwable ->
                        val detail = throwable.message?.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
                        _uiState.update { state ->
                            state.copy(
                                deviceEvents = (listOf(newDeviceEvent("拍照失败", "未取得摄录耳机控制权$detail", DeviceEventLevel.Error)) + state.deviceEvents).take(MaxDeviceEvents),
                                operationMessage = operationMessage("拍照失败，未取得摄录耳机控制权$detail", OperationMessageType.Error)
                            )
                        }
                        return@runDeviceCommandWithOverlay
                    }
                val captured = runCatching {
                    coordinator.mediaFiles(local = true)
                        .filter { it.kind == MediaKind.Photo && it.contentUri.hasUsableValue() }
                        .filter { it.lastModifiedFromContentUri() >= commandStartedAt }
                        .maxByOrNull { it.lastModifiedFromContentUri() }
                }.getOrNull()
                if (captured != null) {
                    _uiState.update { state ->
                        state.copy(
                            deviceEvents = (listOf(newDeviceEvent("现场照片已保存", captured.name, DeviceEventLevel.Info)) + state.deviceEvents).take(MaxDeviceEvents),
                            device = next,
                            connectedDevices = state.connectedDevices.map { if (it.id == next.id) next else it },
                            mediaFiles = state.mediaFiles.upsertMedia(captured),
                            selectedMediaFileId = captured.id,
                            selectedMediaLocal = true
                        )
                    }
                    submitPhotoForCloudRecognition(captured)
                } else {
                    _uiState.update { state ->
                        state.copy(
                            deviceEvents = (listOf(newDeviceEvent("拍照命令已下发", device.name, DeviceEventLevel.Info)) + state.deviceEvents).take(MaxDeviceEvents),
                            device = next,
                            connectedDevices = state.connectedDevices.map { if (it.id == next.id) next else it },
                            operationMessage = operationMessage("拍照命令已下发；若媒体列表仍为空，请在设备文件页通过 Wi-Fi 同步", OperationMessageType.Info)
                        )
                    }
                }
            } finally {
                _uiState.update { state -> state.copy(photoCaptureInProgress = false) }
            }
            }
        }
    }

    fun startLowLatencyStream() = startStream(StreamMode.LowLatency)

    fun startStream(mode: StreamMode) = viewModelScope.launch {
        if (!_uiState.value.device.canReceiveDeviceCommand()) {
            showOperationMessage("请先连接设备", OperationMessageType.Warning)
            return@launch
        }
        val modeText = when (mode) {
            StreamMode.LowLatency -> "低延迟"
            StreamMode.Balanced -> "均衡"
            StreamMode.EvidenceQuality -> "取证质量"
        }
        _uiState.update { it.copy(streamState = StreamRelayState.Connecting, operationMessage = operationMessage("正在连接${modeText}实时画面", OperationMessageType.Info)) }
        runCatching { coordinator.startStream(_uiState.value.device, mode) }
            .onSuccess {
                val streamState = coordinator.streamState().first()
                val message = when (streamState) {
                    StreamRelayState.Relaying -> operationMessage("${modeText}实时画面已连接", OperationMessageType.Success)
                    StreamRelayState.Connecting -> operationMessage("正在连接${modeText}实时画面", OperationMessageType.Info)
                    StreamRelayState.Failed -> operationMessage("实时画面暂不可用，等待 SDK 能力接入", OperationMessageType.Error)
                    StreamRelayState.Idle -> operationMessage("实时画面暂未开启", OperationMessageType.Info)
                }
                _uiState.update { it.copy(streamState = streamState, operationMessage = message) }
            }
            .onFailure { _uiState.update { it.copy(streamState = StreamRelayState.Failed, operationMessage = operationMessage("实时画面连接失败", OperationMessageType.Error)) } }
    }

    fun stopStream() = viewModelScope.launch {
        runCatching { coordinator.stopStream() }
        _uiState.update { it.copy(streamState = StreamRelayState.Idle, operationMessage = operationMessage("实时画面已关闭", OperationMessageType.Info)) }
    }

    fun connectDiscoveredDevice(id: String, name: String, mac: String, signalBars: Int, type: DeviceType) = _uiState.update { state ->
        state.copy(operationMessage = operationMessage("正在连接 $name", OperationMessageType.Info))
    }.also {
        viewModelScope.launch {
            val bound = runCatching { coordinator.bindDevice(id) }.getOrElse {
                _uiState.value.device.copy(id = id, name = name, online = false, signalBars = signalBars.coerceIn(1, 5), type = type)
            }
            val resolvedType = resolveConnectedDeviceType(bound, scannedName = name, scannedType = type)
            val next = bound.copy(
                name = name,
                signalBars = bound.signalBars.coerceIn(1, 5),
                onlineDuration = if (bound.online) "刚刚连接" else "连接失败",
                type = resolvedType
            )
            _uiState.update { state ->
                val connected = if (next.online) {
                    state.connectedDevices.filterNot { it.id == next.id || it.type == next.type } + next
                } else {
                    state.connectedDevices.filterNot { it.id == next.id }
                }
                state.copy(
                    device = if (next.online) next else state.device,
                    connectedDevices = connected,
                    selectedDeviceId = if (next.online) next.id else state.selectedDeviceId,
                    deviceCapabilities = DeviceCapabilities(),
                    deviceWifiState = DeviceWifiState(),
                    realtimeAudioSyncing = false,
                    operationMessage = if (next.online) {
                        operationMessage("$name 已配对并连接", OperationMessageType.Success)
                    } else {
                        operationMessage("$name 配对失败，请检查蓝牙、距离和设备确认状态", OperationMessageType.Error)
                    }
                )
            }
            refreshDeviceCapabilities()
        }
    }

    fun unbindCurrentDevice() = unbindDevice(_uiState.value.device.id)

    fun unbindDiscoveredDevice(
        scannedId: String,
        macAddress: String,
        scannedName: String = "",
        scannedType: DeviceType = DeviceType.Headset
    ) = viewModelScope.launch {
        val requestedKeys = listOf(scannedId, macAddress).filter { it.isNotBlank() }
        requestedKeys.forEach { setDeviceUnbinding(it, true) }
        var target: DeviceStatus? = null
        try {
            target = resolveConnectedDevice(scannedId, macAddress, scannedName, scannedType)
            val resolvedTarget = target
            if (resolvedTarget == null) {
                showOperationMessage("当前没有可解绑设备", OperationMessageType.Warning)
                return@launch
            }
            setDeviceUnbinding(resolvedTarget.id, true)
            val gateway = deviceControlGateway
            if (gateway != null && resolvedTarget.canReceiveDeviceCommand()) {
                val cleared = runCatching { gateway.clearDeviceAccount() }.getOrDefault(false)
                if (!cleared) {
                    unbindDeviceLocally(
                        deviceId = resolvedTarget.id,
                        message = "设备端账号清除失败，已移除 PatrolLink 本地绑定；如仍无法重连，请在设备侧重置配对",
                        messageType = OperationMessageType.Warning
                    )
                    return@launch
                }
                markDeviceRequiresRepairing(resolvedTarget, "设备账号已清除，请重新配对 PatrolLink")
                return@launch
            }
            unbindDeviceLocally(resolvedTarget.id)
        } finally {
            requestedKeys.forEach { setDeviceUnbinding(it, false) }
            target?.id?.let { setDeviceUnbinding(it, false) }
        }
    }

    fun clearConnectedDeviceAccount() = viewModelScope.launch {
        runDeviceCommandWithOverlay("正在清除设备账号") {
        val device = _uiState.value.device
        if (!device.canReceiveDeviceCommand()) {
            showOperationMessage("请先连接设备后再清除设备账号", OperationMessageType.Warning)
            return@runDeviceCommandWithOverlay
        }
        val gateway = deviceControlGateway ?: return@runDeviceCommandWithOverlay showOperationMessage("设备账号清除通道未启用", OperationMessageType.Warning)
        val cleared = runCatching { gateway.clearDeviceAccount() }.getOrDefault(false)
        if (!cleared) {
            showOperationMessage("设备账号清除失败，请确认耳机已连接控制通道", OperationMessageType.Error)
            return@runDeviceCommandWithOverlay
        }
        markDeviceRequiresRepairing(device, "设备账号已清除，请重新配对 PatrolLink")
        }
    }

    fun factoryResetConnectedDevice(target: DeviceFactoryResetTarget) = viewModelScope.launch {
        runDeviceCommandWithOverlay("正在等待恢复出厂回复") {
        val device = _uiState.value.device
        if (!device.canReceiveDeviceCommand()) {
            showOperationMessage("请先连接设备控制通道后再恢复出厂", OperationMessageType.Warning)
            return@runDeviceCommandWithOverlay
        }
        val gateway = deviceControlGateway ?: return@runDeviceCommandWithOverlay showOperationMessage("设备恢复出厂通道未启用", OperationMessageType.Warning)
        val reset = runCatching { gateway.factoryResetDevice(target) }.getOrDefault(false)
        if (!reset) {
            val targetName = if (target == DeviceFactoryResetTarget.Headset) "耳机" else "眼镜"
            showOperationMessage("${targetName}恢复出厂失败，请确认设备控制通道已连接", OperationMessageType.Error)
            return@runDeviceCommandWithOverlay
        }
        val targetName = if (target == DeviceFactoryResetTarget.Headset) "耳机" else "眼镜"
        markDeviceRequiresRepairing(device, "${targetName}已恢复出厂并重启，请重新搜索并配对 PatrolLink")
        }
    }

    private suspend fun markDeviceRequiresRepairing(device: DeviceStatus, message: String) {
        val unbindQueued = unbindDeviceOrQueue(device.id)
        _uiState.update { state ->
            val remaining = state.connectedDevices.filterNot { it.id == device.id }
            val next = if (state.device.id == device.id) {
                remaining.firstOrNull() ?: EmptyAppState.create().device
            } else {
                state.device
            }
            state.copy(
                device = next,
                connectedDevices = remaining,
                selectedDeviceId = next.id.takeIf { it.isNotBlank() },
                streamState = StreamRelayState.Idle,
                deviceCapabilities = DeviceCapabilities(),
                deviceWifiState = DeviceWifiState(),
                realtimeAudioSyncing = false,
                unbindingDeviceIds = state.unbindingDeviceIds - device.id,
                operationMessage = operationMessage(
                    if (unbindQueued) "$message；平台解绑正在自动补传" else message,
                    OperationMessageType.Warning
                )
            )
        }
    }

    fun unbindDevice(deviceId: String) = viewModelScope.launch {
        setDeviceUnbinding(deviceId, true)
        try {
            unbindDeviceLocally(deviceId)
        } finally {
            setDeviceUnbinding(deviceId, false)
        }
    }

    private suspend fun unbindDeviceLocally(
        deviceId: String,
        message: String? = null,
        messageType: OperationMessageType = OperationMessageType.Success
    ) {
        if (deviceId.isBlank()) {
            showOperationMessage("当前没有可解绑设备", OperationMessageType.Warning)
            return
        }
        val target = _uiState.value.connectedDevices.firstOrNull { it.id == deviceId }
            ?: _uiState.value.device.takeIf { it.id == deviceId }
        val unbindResult = runCatching { coordinator.unbindDevice(deviceId) }
        val unbindQueued = unbindResult.isFailure && enqueueDeviceUnbind(deviceId)
        _uiState.update { state ->
            val remaining = state.connectedDevices.filterNot { it.id == deviceId }
            val next = if (state.device.id == deviceId) {
                remaining.firstOrNull() ?: EmptyAppState.create().device
            } else {
                state.device
            }
            state.copy(
                device = next,
                connectedDevices = remaining,
                selectedDeviceId = next.id.takeIf { it.isNotBlank() },
                streamState = StreamRelayState.Idle,
                deviceCapabilities = DeviceCapabilities(),
                deviceWifiState = DeviceWifiState(),
                realtimeAudioSyncing = false,
                unbindingDeviceIds = state.unbindingDeviceIds - deviceId,
                operationMessage = operationMessage(
                    when {
                        unbindResult.isSuccess -> message ?: "${target?.name?.ifBlank { "设备" } ?: "设备"} 已解绑"
                        unbindQueued -> "${message ?: "${target?.name?.ifBlank { "设备" } ?: "设备"} 已在本机解绑"}；平台解绑正在自动补传"
                        else -> "${message ?: "${target?.name?.ifBlank { "设备" } ?: "设备"} 已在本机解绑"}；平台解绑失败，请联网后重试"
                    },
                    if (unbindResult.isSuccess) messageType else OperationMessageType.Warning
                )
            )
        }
    }

    private suspend fun unbindDeviceOrQueue(deviceId: String): Boolean {
        val result = runCatching { coordinator.unbindDevice(deviceId) }
        return result.isFailure && enqueueDeviceUnbind(deviceId)
    }

    private suspend fun enqueueDeviceUnbind(deviceId: String): Boolean {
        val engine = offlineSyncEngine ?: return false
        return runCatching { engine.enqueueDeviceUnbind(deviceId, System.currentTimeMillis()) }.isSuccess
    }

    private fun setDeviceUnbinding(deviceId: String, loading: Boolean) {
        if (deviceId.isBlank()) return
        _uiState.update { state ->
            val nextIds = if (loading) {
                state.unbindingDeviceIds + deviceId
            } else {
                state.unbindingDeviceIds - deviceId
            }
            if (nextIds == state.unbindingDeviceIds) state else state.copy(unbindingDeviceIds = nextIds)
        }
    }

    private fun resolveConnectedDevice(
        scannedId: String,
        macAddress: String,
        scannedName: String,
        scannedType: DeviceType
    ): DeviceStatus? {
        val state = _uiState.value
        val candidates = (state.connectedDevices + state.device).distinctBy { it.id }
        val normalizedScannedId = scannedId.normalizedDeviceKey()
        val normalizedMac = macAddress.normalizedDeviceKey()
        val scanned = ScannedDevice(
            id = scannedId,
            name = scannedName,
            signalBars = 0,
            serviceUuid = "",
            bonded = false,
            macAddress = macAddress,
            type = scannedType
        )
        return candidates.firstOrNull { candidate ->
            val candidateKey = candidate.id.normalizedDeviceKey()
            candidate.id.equals(scannedId, ignoreCase = true) ||
                macAddress.isNotBlank() && candidate.id.equals(macAddress, ignoreCase = true) ||
                candidateKey.isNotBlank() && (
                    candidateKey == normalizedScannedId ||
                        candidateKey == normalizedMac ||
                        normalizedScannedId.contains(candidateKey) ||
                        normalizedMac.contains(candidateKey)
                    ) ||
                scannedName.isNotBlank() && candidate.representsSameAudioDevice(scanned)
        }
    }

    fun refreshScannedDevices(showFailureMessage: Boolean = true) {
        scannedDevicesJob?.cancel()
        scannedDevicesJob = viewModelScope.launch {
            runCatching {
                coordinator.scanDevices().collect { devices ->
                    _uiState.update { state ->
                        val systemConnected = devices
                            .filter { it.isSystemBluetoothAudioConnected() }
                            .map { it.toConnectedAudioStatus(state.device) }
                        val sdkConnected = state.connectedDevices.filter { it.hasSdkControlChannel() }
                        val systemPlaceholders = systemConnected.filterNot { system ->
                            sdkConnected.any { it.representsSameAudioDevice(system) }
                        }
                        val mergedConnected = (state.connectedDevices.filterNot { existing ->
                            systemPlaceholders.any { it.id == existing.id || (!existing.hasSdkControlChannel() && it.type == existing.type) } ||
                                existing.isStaleAudioConnection(systemConnected)
                        } + systemPlaceholders).distinctBy { it.id }
                        val systemSelected = systemConnected.firstOrNull()
                        val selectedDevice = when {
                            state.device.isStaleAudioConnection(systemConnected) && systemSelected != null -> systemSelected
                            state.device.isControllableDevice() -> state.device
                            systemSelected != null -> systemSelected
                            else -> state.device
                        }
                        state.copy(
                            scannedDevices = devices,
                            connectedDevices = mergedConnected,
                            device = selectedDevice,
                            selectedDeviceId = selectedDevice.id.takeIf { it.isNotBlank() }
                        )
                    }
                    val autoControlDevice = devices.firstOrNull { it.isNativeUteHeadsetControl() }
                        ?: devices.firstOrNull { it.isSystemBluetoothAudioControlConnected() }
                        ?: devices.firstOrNull { it.isSystemBluetoothAudioConnected() }
                    if (autoControlDevice != null &&
                        _uiState.value.connectedDevices.none {
                            it.hasSdkControlChannel() && it.representsSameAudioDevice(autoControlDevice)
                        } &&
                        autoBindingDeviceIds.add(autoControlDevice.id)
                    ) {
                        viewModelScope.launch {
                            runCatching { coordinator.bindDevice(autoControlDevice.id) }
                                .onSuccess { bound ->
                                    val resolvedType = resolveConnectedDeviceType(
                                        bound = bound,
                                        scannedName = autoControlDevice.name,
                                        scannedType = autoControlDevice.type
                                    )
                                    val next = bound.copy(
                                        name = autoControlDevice.name,
                                        signalBars = maxOf(bound.signalBars, autoControlDevice.signalBars).coerceIn(1, 5),
                                        type = resolvedType
                                    )
                                    _uiState.update { state ->
                                        if (!next.hasSdkControlChannel()) return@update state
                                        val connected = state.connectedDevices.filterNot {
                                            it.id == next.id || (!it.hasSdkControlChannel() && it.type == next.type)
                                        } + next
                                        state.copy(
                                            device = next,
                                            connectedDevices = connected,
                                            selectedDeviceId = next.id
                                        )
                                    }
                                }
                            autoBindingDeviceIds.remove(autoControlDevice.id)
                        }
                    }
                }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        scannedDevices = emptyList(),
                        operationMessage = if (showFailureMessage) {
                            operationMessage("设备扫描失败，请先登录或检查后台连接", OperationMessageType.Error)
                        } else {
                            state.operationMessage
                        }
                    )
                }
            }
        }
    }

    fun selectConnectedDevice(deviceId: String) = _uiState.update { state ->
        val selected = state.connectedDevices.firstOrNull { it.id == deviceId } ?: return@update state
        state.copy(
            device = selected,
            selectedDeviceId = selected.id,
            streamState = StreamRelayState.Idle,
            deviceCapabilities = DeviceCapabilities(),
            deviceWifiState = DeviceWifiState(),
            realtimeAudioSyncing = false
        )
    }.also {
        refreshDeviceCapabilities()
    }

    private fun updateCurrentDevice(next: com.patrollink.domain.DeviceStatus) {
        _uiState.update { state ->
            state.copy(
                device = next,
                connectedDevices = state.connectedDevices.map { if (it.id == next.id) next else it }
            )
        }
    }

    private fun updateCurrentDeviceWithMessage(next: DeviceStatus, message: String) {
        _uiState.update { state ->
            state.copy(
                device = next,
                connectedDevices = state.connectedDevices.map { if (it.id == next.id) next else it },
                operationMessage = operationMessage(message, OperationMessageType.Success)
            )
        }
    }

    private suspend fun prepareDeviceForCommand(
        capabilityReady: (DeviceCapabilities) -> Boolean,
        unavailableMessage: String
    ): DeviceStatus? {
        var device = currentCommandDevice()
        if (!device.canReceiveDeviceCommand()) {
            showOperationMessage("请先连接设备", OperationMessageType.Warning)
            return null
        }
        if (device.id != _uiState.value.device.id || !_uiState.value.device.canReceiveDeviceCommand()) {
            updateControlDevice(device)
        }
        val gateway = deviceControlGateway
        if (gateway != null && device.requiresSdkControlReadiness()) {
            val ready = runCatching { ensureDeviceControlCapabilities(device, gateway, showMessage = true) }
                .getOrElse { throwable ->
                    val detail = throwable.message?.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
                    showOperationMessage("控制通道连接失败$detail", OperationMessageType.Error)
                    return null
                }
            device = ready.first
            if (!capabilityReady(ready.second)) {
                showOperationMessage(unavailableMessage, OperationMessageType.Error)
                return null
            }
        }
        return device
    }

    private fun currentCommandDevice(): DeviceStatus {
        val state = _uiState.value
        val selected = state.selectedDeviceId?.let { selectedId ->
            state.connectedDevices.firstOrNull { it.id == selectedId && it.canReceiveDeviceCommand() }
        }
        val sdkConnected = state.connectedDevices.firstOrNull { it.hasSdkControlChannel() }
        val connectedHeadset = state.connectedDevices.firstOrNull {
            it.canReceiveDeviceCommand() && it.type in setOf(DeviceType.Headset, DeviceType.Glasses)
        }
        val scannedControlHeadset = state.scannedDevices
            .firstOrNull {
                it.isNativeUteHeadsetControl() ||
                    it.isSystemBluetoothAudioControlConnected()
            }
            ?.toConnectedAudioStatus(state.device)
        val scannedSystemHeadset = state.scannedDevices
            .firstOrNull {
                it.isSystemBluetoothAudioConnected()
            }
            ?.toConnectedAudioStatus(state.device)
        val stateDevice = state.device.takeIf { it.canReceiveDeviceCommand() }
        return listOf(
            stateDevice?.takeIf { it.hasSdkControlChannel() },
            selected?.takeIf { it.hasSdkControlChannel() },
            sdkConnected,
            scannedControlHeadset,
            stateDevice,
            selected,
            connectedHeadset,
            scannedSystemHeadset
        ).firstOrNull { it?.canReceiveDeviceCommand() == true } ?: state.device
    }

    private suspend fun ensureDeviceControlCapabilities(
        device: DeviceStatus,
        gateway: DeviceControlGateway,
        showMessage: Boolean
    ): Pair<DeviceStatus, DeviceCapabilities> {
        var checkedDevice = device
        var capabilities = gateway.capabilities(checkedDevice)
        if (!checkedDevice.shouldReconnectControlChannel(capabilities)) return checkedDevice to capabilities
        if (showMessage) {
            _uiState.update { state ->
                state.copy(operationMessage = operationMessage("正在连接耳机控制通道", OperationMessageType.Info))
            }
        }
        val rebound = coordinator.bindDevice(checkedDevice.id)
        checkedDevice = rebound.copy(
            name = checkedDevice.name.ifBlank { rebound.name },
            type = resolveConnectedDeviceType(rebound, scannedName = checkedDevice.name, scannedType = checkedDevice.type)
        )
        updateControlDevice(checkedDevice)
        capabilities = waitForControlCapabilities(checkedDevice, gateway)
        return checkedDevice to capabilities
    }

    private suspend fun waitForControlCapabilities(device: DeviceStatus, gateway: DeviceControlGateway): DeviceCapabilities {
        var latest = DeviceCapabilities()
        repeat(ControlReadyPollAttempts) { attempt ->
            latest = gateway.capabilities(device)
            if (!device.shouldReconnectControlChannel(latest)) return latest
            if (attempt < ControlReadyPollAttempts - 1) delay(ControlReadyPollMillis)
        }
        return latest
    }

    private fun updateControlDevice(next: DeviceStatus) {
        _uiState.update { state ->
            val connected = state.connectedDevices.filterNot {
                it.id == next.id || (!it.hasSdkControlChannel() && it.type == next.type)
            } + next
            state.copy(
                device = next,
                connectedDevices = connected,
                selectedDeviceId = next.id
            )
        }
    }

    fun downloadMedia(fileId: String) {
        viewModelScope.launch {
            deviceMediaTransferJobs.remove(fileId)?.takeIf { it.isActive }?.cancelAndJoin()
            val job = launch {
                val deviceWifiFile = shouldPrepareDeviceWifiForMediaDownload(fileId)
                if (deviceWifiFile) {
                    showOperationMessage("正在连接设备热点并同步到手机", OperationMessageType.Info)
                    _uiState.update { state ->
                        val file = state.mediaFiles.firstOrNull { it.id == fileId && !it.local }
                        state.copy(
                            deviceMediaSync = DeviceMediaSyncUiState(
                                active = true,
                                fileId = fileId,
                                fileName = file?.name ?: "设备文件",
                                status = TransferStatus.Uploading,
                                progress = 0.03f,
                                completedCount = 0,
                                totalCount = 1
                            )
                        )
                    }
                }
                val success = transferDeviceMediaToPhone(fileId)
                if (success) refreshPhoneMediaAfterDeviceSync()
                if (deviceWifiFile) {
                    finishDeviceMediaSync(successCount = if (success) 1 else 0, failedCount = if (success) 0 else 1)
                }
            }
            deviceMediaTransferJobs[fileId] = job
            job.invokeOnCompletion {
                if (deviceMediaTransferJobs[fileId] == job) {
                    deviceMediaTransferJobs.remove(fileId)
                }
            }
        }
    }

    fun syncDeviceMediaToPhone(fileIds: Set<String> = emptySet(), refreshFirst: Boolean = false) {
        if (deviceMediaSyncJob?.isActive == true) {
            showOperationMessage("正在同步设备文件，请稍候", OperationMessageType.Info)
            return
        }
        deviceMediaSyncJob = viewModelScope.launch {
            cancelActiveDeviceMediaTransferJobs()
            val requestedDeviceFilesBeforeRefresh = selectedDeviceMediaRequests(fileIds)
            val requestedIdentityKeys = requestedDeviceFilesBeforeRefresh
                .flatMap { it.mediaIdentityKeys() }
                .toSet()
            _uiState.update {
                it.copy(
                    deviceMediaSync = DeviceMediaSyncUiState(
                        active = true,
                        fileName = "正在读取设备文件",
                        status = TransferStatus.Uploading,
                        progress = 0.03f
                    ),
                    operationMessage = null
                )
            }
            if (refreshFirst) {
                _uiState.update {
                    it.copy(
                        mediaLoading = true,
                        operationMessage = null
                    )
                }
                val phoneResult = runCatching { coordinator.mediaFiles(local = true) }
                val deviceResult = runCatching { coordinator.mediaFiles(local = false) }
                if (deviceResult.isFailure) {
                    mergeLoadedMediaFiles(
                        phoneMedia = phoneResult.getOrDefault(emptyList()).preserveCurrentPhoneMediaOnFailure(),
                        deviceMedia = emptyList()
                    )
                    _uiState.update {
                        it.copy(
                            mediaLoading = false,
                            deviceMediaSync = DeviceMediaSyncUiState(
                                active = false,
                                fileName = "设备文件读取失败",
                                status = TransferStatus.Failed
                            ),
                            operationMessage = operationMessage(deviceResult.exceptionOrNull().operatorFacingDeviceMediaError(), OperationMessageType.Error)
                        )
                    }
                    return@launch
                }
                mergeLoadedMediaFiles(
                    phoneMedia = phoneResult.getOrDefault(emptyList()),
                    deviceMedia = deviceResult.getOrDefault(emptyList())
                )
                _uiState.update { it.copy(mediaLoading = false) }
                if (deviceResult.getOrDefault(emptyList()).isEmpty()) {
                    cancelDeviceMediaTransfers()
                    return@launch
                }
            }
            val currentFiles = _uiState.value.mediaFiles
            val candidates = currentFiles
                .filter { !it.local && !it.transferStatus.inProgress }
                .filter { fileIds.isEmpty() || it.id in fileIds || it.matchesAnyIdentityKey(requestedIdentityKeys) }
            val phoneCopies = currentFiles.filter { it.local && it.contentUri.hasUsableValue() }
            val pending = candidates.filterNot { deviceFile ->
                phoneCopies.any { phoneFile -> phoneFile.matchesPhoneSandboxCopyOf(deviceFile) }
            }
            when {
                candidates.isEmpty() -> {
                    clearDeviceMediaSync()
                    return@launch
                }
                pending.isEmpty() -> {
                    refreshPhoneMediaAfterDeviceSync()
                    _uiState.update { it.copy(selectedMediaLocal = true) }
                    finishDeviceMediaSync(successCount = 0, failedCount = 0)
                    return@launch
                }
            }
            markDeviceMediaTransferPreparing(pending)
            var successCount = 0
            var failedCount = 0
            pending.forEachIndexed { index, file ->
                beginDeviceMediaSyncFile(file, completedCount = index, totalCount = pending.size)
                if (transferDeviceMediaToPhone(file.id, notifyDeviceWhenDone = false, showFailureMessage = false)) {
                    successCount += 1
                } else {
                    failedCount += 1
                }
            }
            if (successCount > 0) {
                runCatching { deviceControlGateway?.notifyMediaSyncCompleted() }
                refreshPhoneMediaAfterDeviceSync()
            }
            _uiState.update {
                it.copy(
                    selectedMediaLocal = successCount.takeIf { count -> count > 0 }?.let { true } ?: it.selectedMediaLocal,
                    deviceMediaSync = it.deviceMediaSync.copy(
                        active = false,
                        status = if (failedCount > 0 && successCount == 0) TransferStatus.Failed else TransferStatus.Done,
                        progress = if (successCount > 0) 1f else it.deviceMediaSync.progress,
                        completedCount = successCount,
                        totalCount = pending.size
                    )
                )
            }
            finishDeviceMediaSync(successCount = successCount, failedCount = failedCount)
        }
    }

    private fun shouldPrepareDeviceWifiForMediaDownload(fileId: String): Boolean =
        fileId.startsWith(DeviceWifiMediaIdPrefix)

    private fun selectedDeviceMediaRequests(fileIds: Set<String>): List<MediaFile> {
        if (fileIds.isEmpty()) return emptyList()
        return _uiState.value.mediaFiles.filter { !it.local && it.id in fileIds }
    }

    private fun markDeviceMediaTransferPreparing(files: List<MediaFile>) {
        if (files.isEmpty()) return
        val ids = files.map { it.id }.toSet()
        val firstId = files.first().id
        val first = files.first()
        _uiState.update { state ->
            state.copy(
                selectedMediaLocal = false,
                selectedMediaFileId = firstId,
                deviceMediaSync = DeviceMediaSyncUiState(
                    active = true,
                    fileId = firstId,
                    fileName = first.name,
                    status = TransferStatus.Uploading,
                    progress = 0.05f,
                    completedCount = 0,
                    totalCount = files.size
                ),
                mediaFiles = state.mediaFiles.map { file ->
                    if (!file.local && file.id in ids) {
                        file.copy(
                            transferStatus = TransferStatus.Uploading,
                            progress = file.progress.coerceAtLeast(0.05f),
                            lastTransferTarget = TransferTarget.PhoneSandbox
                        )
                    } else {
                        file
                    }
                }
            )
        }
    }

    private suspend fun openDeviceWifiForMediaSync(forceRestart: Boolean = false): Boolean {
        val gateway = deviceControlGateway ?: return true
        return runCatching {
            val current = gateway.readWifi()
            if (forceRestart) {
                val ssid = current.ssid.ifBlank { _uiState.value.deviceWifiState.ssid }
                runCatching { gateway.configureWifi(enabled = false, ssid = ssid, password = "") }
                delay(DeviceWifiRestartSettleMillis)
                gateway.configureWifi(enabled = true, ssid = ssid, password = "")
            } else if (current.enabled && current.connected) {
                current
            } else {
                gateway.configureWifi(enabled = true, ssid = current.ssid, password = "")
            }
        }.fold(
            onSuccess = { wifi ->
                _uiState.update { it.copy(deviceWifiState = wifi) }
                true
            },
            onFailure = { throwable ->
                _uiState.update {
                    it.copy(operationMessage = operationMessage(throwable.operatorFacingWifiError(), OperationMessageType.Error))
                }
                false
            }
        )
    }

    private suspend fun closeDeviceWifiAfterMediaSync() {
        val gateway = deviceControlGateway ?: return
        runCatching {
            gateway.configureWifi(
                enabled = false,
                ssid = _uiState.value.deviceWifiState.ssid,
                password = ""
            )
        }.onSuccess { wifi ->
            _uiState.update { it.copy(deviceWifiState = wifi) }
        }
    }

    private fun mergeLoadedMediaFiles(phoneMedia: List<MediaFile>, deviceMedia: List<MediaFile>) {
        val loaded = (phoneMedia + deviceMedia).distinctBy { it.id to it.local }
        _uiState.update { state ->
            val loadedWithState = loaded.map { incoming ->
                val current = state.mediaFiles.firstOrNull { it.id == incoming.id && it.local == incoming.local }
                incoming.inheritCompletedCloudState(current)
            }
            val transient = state.mediaFiles.filter { current ->
                current.transferStatus.inProgress &&
                    loadedWithState.none { it.id == current.id && it.local == current.local }
            }
            val next = (loadedWithState + transient)
                .distinctBy { it.id to it.local }
                .markDeviceFilesPresentInPhoneSandbox()
            val nextSelected = state.selectedMediaFileId?.takeIf { selectedId ->
                next.any { it.id == selectedId && it.local == state.selectedMediaLocal }
            } ?: next.firstOrNull { it.local == state.selectedMediaLocal }?.id
            state.copy(
                mediaFiles = next,
                selectedMediaFileId = nextSelected
            )
        }
    }

    private fun List<MediaFile>.preserveCurrentPhoneMediaOnFailure(): List<MediaFile> {
        val currentPhoneMedia = _uiState.value.mediaFiles.filter { it.local && it.contentUri.hasUsableValue() }
        return (this + currentPhoneMedia).distinctBy { it.id to it.local }
    }

    private fun cancelDeviceMediaTransfers() {
        cancelActiveDeviceMediaTransferJobs()
        _uiState.update { state ->
            val nextFiles = state.mediaFiles.filterNot { !it.local && it.id.startsWith(DeviceWifiMediaIdPrefix) }
            state.copy(
                mediaFiles = nextFiles,
                deviceMediaSync = DeviceMediaSyncUiState(),
                selectedMediaFileId = state.selectedMediaFileId?.takeIf { selectedId ->
                    nextFiles.any { it.id == selectedId && it.local == state.selectedMediaLocal }
                }
            )
        }
    }

    private fun cancelActiveDeviceMediaTransferJobs() {
        deviceMediaTransferJobs.values.forEach { it.cancel() }
        deviceMediaTransferJobs.clear()
    }

    private fun removeStaleDeviceMedia(fileId: String) {
        _uiState.update { state ->
            state.copy(
                mediaFiles = state.mediaFiles.filterNot { it.id == fileId && !it.local },
                selectedMediaFileId = state.selectedMediaFileId?.takeUnless { it == fileId && !state.selectedMediaLocal }
            )
        }
    }

    private suspend fun transferDeviceMediaToPhone(
        fileId: String,
        notifyDeviceWhenDone: Boolean = true,
        showFailureMessage: Boolean = true
    ): Boolean {
        val maxAttempts = if (shouldPrepareDeviceWifiForMediaDownload(fileId)) DeviceMediaTransferMaxAttempts else 1
        var lastFailure: Throwable? = null
        repeat(maxAttempts) { index ->
            val attempt = index + 1
            val failure = runCatching {
                withTimeout(DeviceMediaTransferTimeoutMillis) {
                    transferDeviceMediaToPhoneOnce(fileId, notifyDeviceWhenDone)
                }
            }.exceptionOrNull()
            if (failure == null) return true
            lastFailure = failure
            if (failure.isWifiMediaFileMissing()) {
                removeStaleDeviceMedia(fileId)
                markMediaTransferFailed(
                    fileId = fileId,
                    local = false,
                    target = TransferTarget.PhoneSandbox,
                    throwable = failure.toOperatorFacingMediaTransferFailure(),
                    action = "媒体文件下载失败",
                    showMessage = showFailureMessage
                )
                return false
            }
            if (attempt < maxAttempts) {
                if (showFailureMessage) _uiState.update {
                    it.copy(operationMessage = operationMessage("设备文件下载失败，正在保持设备热点并重试 $attempt/$maxAttempts", OperationMessageType.Warning))
                }
                delay(DeviceMediaRetryDelayMillis)
            }
        }
        markMediaTransferFailed(
            fileId = fileId,
            local = false,
            target = TransferTarget.PhoneSandbox,
            throwable = lastFailure.toOperatorFacingMediaTransferFailure(),
            action = "媒体文件下载失败",
            showMessage = showFailureMessage
        )
        return false
    }

    private fun Throwable?.toOperatorFacingMediaTransferFailure(): Throwable =
        when (this) {
            null -> IllegalStateException("媒体传输通道未返回进度")
            is TimeoutCancellationException -> IllegalStateException("设备热点下载超时，请确认手机已连接设备热点后重试")
            else -> if (isWifiMediaFileMissing()) {
                IllegalStateException("设备端文件已不存在，请刷新设备媒体列表后重试")
            } else {
                this
            }
        }

    private fun Throwable?.isWifiMediaFileMissing(): Boolean =
        this?.message.orEmpty().contains("wifi media file not found", ignoreCase = true)

    private suspend fun transferDeviceMediaToPhoneOnce(
        fileId: String,
        notifyDeviceWhenDone: Boolean
    ) {
        var emitted = false
        coordinator.transferMedia(fileId, TransferTarget.PhoneSandbox).collect { updated ->
            emitted = true
            val phoneSyncUpdate = updated.markTransferTarget(TransferTarget.PhoneSandbox)
            updatePhoneTransfer(fileId, phoneSyncUpdate)
            updateDeviceMediaSync(fileId, phoneSyncUpdate)
            if (updated.transferStatus == TransferStatus.Done) {
                if (notifyDeviceWhenDone) {
                    runCatching { deviceControlGateway?.notifyMediaSyncCompleted() }
                }
                enqueueEvidenceUploadIfLocal(
                    fileId = fileId,
                    local = true,
                    file = updated.copy(id = fileId, local = true)
                        .takeIf { it.contentUri.hasUsableValue() }
                        ?: _uiState.value.mediaFiles.firstOrNull { it.id == fileId && it.local }
                )
                val localCopy = updated.copy(id = fileId, local = true)
                    .takeIf { it.contentUri.hasUsableValue() }
                    ?: _uiState.value.mediaFiles.firstOrNull { it.id == fileId && it.local }
                if (localCopy?.kind == MediaKind.Photo) submitPhotoForCloudRecognition(localCopy)
            }
            if (updated.transferStatus != TransferStatus.Done) delay(420)
        }
        check(emitted) { "媒体传输通道未返回进度" }
    }

    fun uploadMedia(fileId: String, local: Boolean = true) = viewModelScope.launch {
        if (!local) {
            downloadMedia(fileId)
            return@launch
        }
        val current = _uiState.value.mediaFiles.firstOrNull { it.id == fileId && it.local == local }
        if (current?.transferStatus == TransferStatus.Done && current.lastTransferTarget == TransferTarget.Cloud) {
            _uiState.update { it.copy(operationMessage = operationMessage("${current.name} 已上传", OperationMessageType.Success)) }
            return@launch
        }
        val failure = runCatching {
            var emitted = false
            var completed: MediaFile? = null
            coordinator.transferMedia(fileId, TransferTarget.Cloud).collect { updated ->
                emitted = true
                val next = updated.copy(local = local).markTransferTarget(TransferTarget.Cloud)
                updateTransferredMedia(fileId, local, next)
                if (next.transferStatus == TransferStatus.Done) completed = next
                if (updated.transferStatus != TransferStatus.Done) delay(420)
            }
            check(emitted) { "媒体传输通道未返回进度" }
            completed ?: error("媒体上传未返回完成状态")
        }.exceptionOrNull()
        if (failure != null) {
            enqueueEvidenceUploadIfLocal(fileId, local, current)
            markMediaTransferFailed(fileId, local = local, target = TransferTarget.Cloud, throwable = failure, action = "媒体文件上传失败")
        } else {
            _uiState.update { state ->
                state.copy(
                    previewMediaFile = state.previewMediaFile?.takeUnless { it.id == fileId && it.local == local },
                    operationMessage = operationMessage("${current?.name ?: "媒体文件"} 已上传云端", OperationMessageType.Success)
                )
            }
        }
    }

    private suspend fun enqueueEvidenceUploadIfLocal(fileId: String, local: Boolean, file: MediaFile?) {
        val engine = offlineSyncEngine ?: return
        if (!local || file?.contentUri.hasUsableValue().not()) return
        runCatching { engine.enqueueEvidenceUpload(fileId, System.currentTimeMillis()) }
    }

    private suspend fun submitPhotoForCloudRecognition(media: MediaFile) {
        val api = cerebellumApi ?: return
        if (media.kind != MediaKind.Photo || !cloudRecognitionSubmittedMediaIds.add(media.id)) return
        val localFile = localFileForCerebellumUpload(media, appContext, backendBaseUrl, secureStore)
        if (localFile == null) {
            cloudRecognitionSubmittedMediaIds.remove(media.id)
            return
        }
        val result = runCatching {
            val missionId = _uiState.value.dailyReport.missionId.ifBlank {
                defaultMissionId(_uiState.value.user.badgeNo)
            }
            val uploaded = api.uploadFile(
                file = localFile,
                missionId = missionId,
                evidenceType = "image",
                note = "云端人脸和车辆布控识别：${media.name}",
                register = true
            )
            val imageUri = uploaded.file.fileUri
            val originDeviceId = _uiState.value.device.id.trim().takeIf(String::isNotEmpty)
            api.analyzeVision(
                CerebellumCombinedVisionAnalyzeRequestDto(
                    frameId = media.id,
                    cameraId = originDeviceId ?: "patrol-mobile",
                    imageUri = imageUri,
                    deviceId = originDeviceId,
                )
            )
        }
        result.onSuccess { response ->
            val display = response.toRecognitionDisplay(media.name)
            addDeviceEvent(display.title, display.detail, display.level)
            showOperationMessage(display.message, display.messageType)
            syncCloudAlerts()
        }.onFailure { throwable ->
            cloudRecognitionSubmittedMediaIds.remove(media.id)
            addDeviceEvent("云端识别提交失败", "${media.name}：${throwable.message.orEmpty()}", DeviceEventLevel.Warning)
        }
    }

    private suspend fun ensureMediaFileForCerebellum(file: MediaFile): MediaFile? {
        val existingLocal = _uiState.value.mediaFiles.firstOrNull { it.id == file.id && it.local && it.contentUri.hasUsableValue() }
            ?: file.takeIf { it.local && it.contentUri.hasUsableValue() }
        if (existingLocal != null) return existingLocal
        showOperationMessage("正在补齐 ${file.name} 到手机沙盒，准备上传小脑", OperationMessageType.Info)
        return runCatching {
            var finalMedia: MediaFile? = null
            coordinator.transferMedia(file.id, TransferTarget.PhoneSandbox).collect { updated ->
                updatePhoneTransfer(file.id, updated.markTransferTarget(TransferTarget.PhoneSandbox))
                if (updated.transferStatus == TransferStatus.Done) {
                    finalMedia = updated.copy(local = true)
                } else {
                    delay(220)
                }
            }
            finalMedia?.takeIf { it.contentUri.hasUsableValue() }
                ?: _uiState.value.mediaFiles.firstOrNull { it.id == file.id && it.local && it.contentUri.hasUsableValue() }
        }.getOrNull()
    }

    private fun MediaFile.markTransferTarget(target: TransferTarget): MediaFile =
        copy(lastTransferTarget = target)

    private fun markMediaTransferFailed(
        fileId: String,
        local: Boolean,
        target: TransferTarget,
        throwable: Throwable,
        action: String,
        showMessage: Boolean = true
    ) {
        val detail = throwable.message?.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
        _uiState.update { state ->
            val current = state.mediaFiles.firstOrNull { it.id == fileId && it.local == local }
            val nextFiles = current?.let {
                state.mediaFiles.upsertMedia(
                    it.copy(
                        transferStatus = TransferStatus.Failed,
                        progress = 0f,
                        contentUri = it.contentUri,
                        lastTransferTarget = target
                    )
                )
            } ?: state.mediaFiles
            state.copy(
                mediaFiles = nextFiles,
                deviceMediaSync = if (state.deviceMediaSync.fileId == fileId) {
                    state.deviceMediaSync.copy(
                        active = false,
                        status = TransferStatus.Failed,
                        progress = 0f
                    )
                } else {
                    state.deviceMediaSync
                },
                operationMessage = if (showMessage) {
                    operationMessage("$action$detail", OperationMessageType.Error)
                } else {
                    state.operationMessage
                }
            )
        }
    }

    private fun updateTransferredMedia(fileId: String, local: Boolean, updated: MediaFile) {
        _uiState.update { state ->
            val current = state.mediaFiles.firstOrNull { it.id == fileId && it.local == local }
            val merged = updated.copy(
                id = fileId,
                local = local,
                contentUri = updated.contentUri ?: current?.contentUri,
                verified = updated.verified || current?.verified == true
            )
            state.copy(
                mediaFiles = state.mediaFiles.upsertMedia(merged),
                previewMediaFile = state.previewMediaFile?.let { preview ->
                    if (preview.id == fileId && preview.local == local) {
                        if (merged.transferStatus == TransferStatus.Done && merged.lastTransferTarget == TransferTarget.Cloud) {
                            null
                        } else {
                            merged.copy(contentUri = preview.contentUri ?: merged.contentUri)
                        }
                    } else {
                        preview
                    }
                }
            )
        }
    }

    private fun updatePhoneTransfer(fileId: String, updated: MediaFile) {
        _uiState.update { state ->
            val deviceFile = state.mediaFiles.firstOrNull { it.id == fileId && !it.local }
                ?: updated.copy(id = fileId, local = false)
            val deviceUpdate = deviceFile.copy(
                transferStatus = updated.transferStatus,
                progress = updated.progress,
                verified = deviceFile.verified || (updated.transferStatus == TransferStatus.Done && updated.verified),
                lastTransferTarget = TransferTarget.PhoneSandbox
            )
            val withDevice = state.mediaFiles.upsertMedia(deviceUpdate)
            val nextFiles = if (updated.transferStatus == TransferStatus.Done) {
                val existingPhoneFile = state.mediaFiles.firstOrNull { it.id == fileId && it.local }
                val phoneCopy = deviceFile.copy(
                    local = true,
                    transferStatus = TransferStatus.Idle,
                    progress = 0f,
                    verified = updated.verified || deviceFile.verified || existingPhoneFile?.verified == true,
                    contentUri = updated.contentUri ?: existingPhoneFile?.contentUri,
                    lastTransferTarget = null
                )
                withDevice.upsertMedia(phoneCopy)
            } else {
                withDevice
            }
            state.copy(mediaFiles = nextFiles)
        }
    }

    private fun beginDeviceMediaSyncFile(file: MediaFile, completedCount: Int, totalCount: Int) {
        _uiState.update { state ->
            state.copy(
                deviceMediaSync = DeviceMediaSyncUiState(
                    active = true,
                    fileId = file.id,
                    fileName = file.name,
                    status = TransferStatus.Uploading,
                    progress = 0.05f,
                    completedCount = completedCount,
                    totalCount = totalCount
                )
            )
        }
    }

    private fun updateDeviceMediaSync(fileId: String, updated: MediaFile) {
        _uiState.update { state ->
            val current = state.deviceMediaSync
            if (!current.active || current.fileId != fileId) {
                state
            } else {
                state.copy(
                    deviceMediaSync = current.copy(
                        fileName = updated.name.ifBlank { current.fileName },
                        status = updated.transferStatus,
                        progress = updated.progress.coerceIn(0f, 1f)
                    )
                )
            }
        }
    }

    private fun finishDeviceMediaSync(successCount: Int, failedCount: Int) {
        _uiState.update { state ->
            val message = when {
                successCount > 0 && failedCount == 0 -> "已同步 $successCount 个设备文件到手机本地媒体文件，设备热点保持连接"
                successCount > 0 -> "已同步 $successCount 个设备文件，$failedCount 个文件同步失败，设备热点保持连接"
                failedCount > 0 -> "$failedCount 个设备文件同步失败，设备热点保持连接"
                else -> "设备端文件已在手机端，无需重复同步"
            }
            state.copy(
                deviceMediaSync = state.deviceMediaSync.copy(
                    active = false,
                    status = if (failedCount > 0 && successCount == 0) TransferStatus.Failed else TransferStatus.Done,
                    progress = if (successCount > 0 || failedCount == 0) 1f else state.deviceMediaSync.progress,
                    completedCount = successCount,
                    totalCount = state.deviceMediaSync.totalCount.coerceAtLeast(successCount + failedCount)
                ),
                operationMessage = operationMessage(
                    message,
                    if (failedCount > 0 && successCount == 0) OperationMessageType.Error else OperationMessageType.Success
                )
            )
        }
    }

    private fun clearDeviceMediaSync() {
        _uiState.update { it.copy(deviceMediaSync = DeviceMediaSyncUiState()) }
    }

    private suspend fun refreshPhoneMediaAfterDeviceSync() {
        val phoneMedia = runCatching { coordinator.mediaFiles(local = true) }.getOrDefault(emptyList())
        val current = _uiState.value.mediaFiles
        val currentLocal = current.filter { it.local && it.contentUri.hasUsableValue() }
        val mergedPhone = (phoneMedia + currentLocal).distinctBy { it.id to it.local }
        if (mergedPhone.isEmpty()) return
        val deviceMedia = current.filter { !it.local }
        mergeLoadedMediaFiles(phoneMedia = mergedPhone, deviceMedia = deviceMedia)
    }

    private fun List<MediaFile>.upsertMedia(file: MediaFile): List<MediaFile> {
        var replaced = false
        val updated = map {
            if (it.id == file.id && it.local == file.local) {
                replaced = true
                file
            } else {
                it
            }
        }
        return if (replaced) updated else listOf(file) + updated
    }

    private fun List<MediaFile>.markDeviceFilesPresentInPhoneSandbox(): List<MediaFile> {
        val phoneCopies = filter { it.local && it.contentUri.hasUsableValue() }
        if (phoneCopies.isEmpty()) return this
        return map { file ->
            if (!file.local && !file.transferStatus.inProgress && phoneCopies.any { it.matchesPhoneSandboxCopyOf(file) }) {
                file.copy(
                    transferStatus = TransferStatus.Done,
                    progress = 1f,
                    lastTransferTarget = TransferTarget.PhoneSandbox
                )
            } else {
                file
            }
        }
    }

    fun verifyMedia(fileId: String, local: Boolean = _uiState.value.selectedMediaLocal) = viewModelScope.launch {
        val verified = runCatching { coordinator.verifyMedia(fileId) }.getOrDefault(false)
        _uiState.update { state ->
            state.copy(
                mediaFiles = state.mediaFiles.map { if (it.id == fileId && it.local == local) it.copy(verified = verified) else it },
                operationMessage = if (verified) {
                    operationMessage("证据完整性校验通过", OperationMessageType.Success)
                } else {
                    operationMessage("证据完整性校验失败", OperationMessageType.Error)
                }
            )
        }
    }

    fun deleteMedia(fileId: String, local: Boolean = _uiState.value.selectedMediaLocal) = viewModelScope.launch {
        if (coordinator.deleteMedia(fileId, local)) {
            _uiState.update { state ->
                state.copy(
                    mediaFiles = state.mediaFiles.filterNot { it.id == fileId && it.local == local },
                    selectedMediaFileId = if (state.selectedMediaFileId == fileId && state.selectedMediaLocal == local) null else state.selectedMediaFileId,
                    previewMediaFile = state.previewMediaFile?.takeUnless { it.id == fileId && it.local == local },
                    operationMessage = operationMessage(
                        if (local) "手机端媒体文件已删除" else "设备端删除指令已发送，文件已移除",
                        OperationMessageType.Success
                    )
                )
            }
        } else {
            _uiState.update { it.copy(operationMessage = operationMessage("媒体文件删除失败", OperationMessageType.Error)) }
        }
    }

    fun deleteMediaBatch(fileIds: Set<String>, local: Boolean = _uiState.value.selectedMediaLocal) = viewModelScope.launch {
        if (fileIds.isEmpty()) {
            showOperationMessage("请选择要删除的媒体文件", OperationMessageType.Warning)
            return@launch
        }
        val currentFiles = _uiState.value.mediaFiles.filter { it.local == local && it.id in fileIds }
        val busyIds = currentFiles.filter { it.transferStatus.inProgress }.map { it.id }.toSet()
        val deleteIds = fileIds - busyIds
        val successIds = mutableSetOf<String>()
        var successCount = 0
        var failedCount = 0
        deleteIds.forEach { id ->
            if (runCatching { coordinator.deleteMedia(id, local) }.getOrDefault(false)) {
                successCount += 1
                successIds += id
            } else {
                failedCount += 1
            }
        }
        _uiState.update { state ->
            state.copy(
                mediaFiles = state.mediaFiles.filterNot { it.local == local && it.id in successIds },
                selectedMediaFileId = state.selectedMediaFileId?.takeUnless { it in successIds },
                previewMediaFile = state.previewMediaFile?.takeUnless { it.local == local && it.id in successIds },
                operationMessage = operationMessage(
                    buildString {
                        append("已删除 $successCount 个媒体文件")
                        if (busyIds.isNotEmpty()) append("，跳过处理中 ${busyIds.size} 个")
                        if (failedCount > 0) append("，失败 $failedCount 个")
                    },
                    if (failedCount > 0) OperationMessageType.Warning else OperationMessageType.Success
                )
            )
        }
    }

    fun selectMedia(fileId: String) = _uiState.update { it.copy(selectedMediaFileId = fileId) }

    fun openMediaPreview(fileId: String, local: Boolean = _uiState.value.selectedMediaLocal) = viewModelScope.launch {
        val target = _uiState.value.mediaFiles.firstOrNull { it.id == fileId && it.local == local }
            ?: return@launch showOperationMessage("媒体文件不存在", OperationMessageType.Error)
        val preview = runCatching { materializeMediaForPreview(target) }.getOrNull()
            ?: target.takeIf { it.local }?.withInternalLocalUri(allowRemote = false)
        _uiState.update { state ->
            state.copy(
                previewMediaFile = preview,
                selectedMediaFileId = fileId,
                operationMessage = if (preview == null) {
                    val message = if (target.local) {
                        "媒体还没有可播放的本地文件，请检查手机端文件或云端下载地址"
                    } else {
                        "设备端文件没有可用预览地址，请刷新设备文件或重新连接设备热点"
                    }
                    operationMessage(message, OperationMessageType.Error)
                } else {
                    state.operationMessage
                }
            )
        }
    }

    fun closeMediaPreview() = _uiState.update { it.copy(previewMediaFile = null) }

    suspend fun mediaContentRequest(file: MediaFile): MediaContentRequest? = withContext(Dispatchers.IO) {
        val value = file.contentUri?.takeIf { it.isNotBlank() } ?: return@withContext null
        val uri = runCatching { Uri.parse(value) }.getOrNull()
        val localFileExists = when {
            uri?.scheme == "file" -> uri.path?.let(::File)?.exists() == true
            uri?.scheme == null && value.startsWith("/") -> File(value).exists()
            else -> false
        }
        val resolved = if (!localFileExists) value.toRemoteMediaUrl(backendBaseUrl) ?: value else value
        val backendRequest = resolved.isSameOriginAs(backendBaseUrl)
        val authorization = if (backendRequest) {
            secureStore?.readSession()?.accessToken?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
        } else {
            null
        }
        MediaContentRequest(
            value = resolved,
            authorization = authorization,
            clientId = OkHttpPatrolRestApi.DEFAULT_CLIENT_ID.takeIf { backendRequest }
        )
    }

    fun clearMessage() = _uiState.update { it.copy(operationMessage = null) }

    fun showOperationMessage(message: String, type: OperationMessageType) = _uiState.update {
        it.copy(operationMessage = operationMessage(message, type))
    }

    private fun DeviceEvent.shouldShowOperationMessage(): Boolean =
        level != DeviceEventLevel.Info

    private fun DeviceEvent.toOperationMessageType(): OperationMessageType =
        when (level) {
            DeviceEventLevel.Info -> OperationMessageType.Info
            DeviceEventLevel.Warning -> OperationMessageType.Warning
            DeviceEventLevel.Error -> OperationMessageType.Error
        }

    private fun defaultMissionId(badgeNo: String): String {
        val day = SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date())
        return "mission-$day-${badgeNo.ifBlank { "operator" }}"
    }

    private fun observeDeviceEvents() = viewModelScope.launch {
        deviceControlGateway?.events()?.collect { event ->
            if (event.shouldRefreshMedia()) refreshMediaFiles()
            if (event.shouldRefreshDeviceCapabilities()) refreshDeviceCapabilities()
            _uiState.update { state ->
                state.copy(
                    deviceEvents = (listOf(event) + state.deviceEvents).take(MaxDeviceEvents),
                    operationMessage = if (event.shouldShowOperationMessage()) {
                        operationMessage(event.title, event.toOperationMessageType())
                    } else {
                        state.operationMessage
                    }
                )
            }
        }
    }

    fun checkVersionUpdate() = viewModelScope.launch {
        _uiState.update { it.copy(versionUpdate = it.versionUpdate.copy(phase = VersionUpdatePhase.Checking, message = "正在检查更新")) }
        runCatching { versionGateway.check(currentVersionCode = 1) }
            .onSuccess { result ->
                _uiState.update {
                    it.copy(
                        versionUpdate = if (result.hasUpdate) {
                            VersionUpdateUiState(
                                phase = VersionUpdatePhase.Available,
                                currentVersionName = it.versionUpdate.currentVersionName,
                                latestVersionName = result.latestVersionName,
                                changelog = result.changelog,
                                downloadUrl = result.downloadUrl,
                                message = "发现新版本 ${result.latestVersionName}"
                            )
                        } else {
                            it.versionUpdate.copy(phase = VersionUpdatePhase.UpToDate, message = "当前已是最新版本")
                        }
                    )
                }
            }
            .onFailure {
                _uiState.update { state -> state.copy(versionUpdate = state.versionUpdate.copy(phase = VersionUpdatePhase.Failed, message = "检查更新失败")) }
            }
    }

    fun checkFirmwareUpdate() = viewModelScope.launch {
        val device = _uiState.value.device
        if (!device.online || device.id.isBlank()) {
            _uiState.update { state ->
                state.copy(operationMessage = operationMessage("请先连接耳机后再检查固件", OperationMessageType.Warning))
            }
            return@launch
        }
        _uiState.update {
            it.copy(
                firmwareUpdate = it.firmwareUpdate.copy(
                    phase = FirmwareUpdatePhase.Checking,
                    currentVersionName = device.firmware,
                    message = "正在检查设备固件"
                )
            )
        }
        runCatching {
            firmwareGateway.check(device = device, metadata = FirmwareDeviceMetadata(vendor = "UTE"))
        }.onSuccess { result ->
            _uiState.update { state ->
                val next = if (result.hasUpdate) {
                    FirmwareUpdateUiState(
                        phase = FirmwareUpdatePhase.Available,
                        currentVersionName = result.currentFirmwareVersion.ifBlank { device.firmware },
                        latestVersionName = result.versionName,
                        changelog = result.changelog,
                        downloadUrl = result.downloadUrl,
                        firmwareId = result.firmwareId,
                        packageFormat = result.packageFormat,
                        upgradeMode = result.upgradeMode,
                        sha256 = result.sha256,
                        fileSizeBytes = result.fileSizeBytes,
                        forceUpdate = result.forceUpdate,
                        progress = 0f,
                        message = "发现设备固件 ${result.versionName}"
                    )
                } else {
                    state.firmwareUpdate.copy(
                        phase = FirmwareUpdatePhase.UpToDate,
                        currentVersionName = result.currentFirmwareVersion.ifBlank { device.firmware },
                        latestVersionName = null,
                        changelog = emptyList(),
                        downloadUrl = null,
                        firmwareId = null,
                        packageFormat = "",
                        upgradeMode = "",
                        sha256 = null,
                        fileSizeBytes = 0L,
                        forceUpdate = false,
                        progress = 0f,
                        message = result.message.ifBlank { "当前已是最新固件" }
                    )
                }
                state.copy(
                    firmwareUpdate = next,
                    operationMessage = operationMessage(
                        next.message ?: "固件检查完成",
                        if (result.hasUpdate) OperationMessageType.Warning else OperationMessageType.Success
                    )
                )
            }
        }.onFailure {
            _uiState.update { state ->
                state.copy(
                    firmwareUpdate = state.firmwareUpdate.copy(phase = FirmwareUpdatePhase.Failed, message = "设备固件检查失败"),
                    operationMessage = operationMessage("设备固件检查失败", OperationMessageType.Error)
                )
            }
        }
    }

    fun startFirmwareUpgrade() = viewModelScope.launch {
        val device = _uiState.value.device
        val update = _uiState.value.firmwareUpdate
        if (!device.canReceiveDeviceCommand()) {
            showOperationMessage("请先连接设备后再升级固件", OperationMessageType.Warning)
            return@launch
        }
        if (update.phase != FirmwareUpdatePhase.Available) {
            showOperationMessage("请先检查并选择可用固件", OperationMessageType.Warning)
            return@launch
        }
        val firmware = update.toFirmwareCheckResult(device)
        _uiState.update {
            it.copy(
                firmwareUpdate = update.copy(
                    phase = FirmwareUpdatePhase.Downloading,
                    progress = 0.05f,
                    message = "正在准备固件包"
                )
            )
        }
        runCatching {
            firmwareGateway.install(device, firmware).collect { progress ->
                _uiState.update { state ->
                    state.copy(
                        firmwareUpdate = state.firmwareUpdate.copy(
                            phase = progress.status.toFirmwarePhase(),
                            progress = progress.progress.coerceIn(0f, 1f),
                            message = progress.status.toFirmwareMessage(progress.errorMessage)
                        )
                    )
                }
            }
        }.onSuccess {
            _uiState.update { state ->
                state.copy(
                    firmwareUpdate = state.firmwareUpdate.copy(
                        phase = FirmwareUpdatePhase.Succeeded,
                        progress = 1f,
                        message = "固件升级已启动，请保持设备连接并等待设备完成重启"
                    ),
                    operationMessage = operationMessage("固件升级已启动", OperationMessageType.Success)
                )
            }
        }.onFailure {
            _uiState.update { state ->
                state.copy(
                    firmwareUpdate = state.firmwareUpdate.copy(
                        phase = FirmwareUpdatePhase.Failed,
                        message = "固件升级启动失败"
                    ),
                    operationMessage = operationMessage("固件升级启动失败", OperationMessageType.Error)
                )
            }
        }
    }

    fun installVersionUpdate() = viewModelScope.launch {
        val latest = _uiState.value.versionUpdate.latestVersionName ?: return@launch
        val updateState = _uiState.value.versionUpdate
        _uiState.update { it.copy(versionUpdate = it.versionUpdate.copy(phase = VersionUpdatePhase.Downloading, progress = 0f, message = "正在下载更新")) }
        if (versionInstaller == null || updateState.downloadUrl?.contains("example.test") == true) {
            for (step in 1..10) {
                delay(90)
                _uiState.update { it.copy(versionUpdate = it.versionUpdate.copy(progress = step / 10f)) }
            }
            _uiState.update {
                it.copy(
                    versionUpdate = it.versionUpdate.copy(
                        phase = VersionUpdatePhase.Ready,
                        currentVersionName = latest,
                        progress = 1f,
                        message = "更新包已准备完成"
                    ),
                    operationMessage = operationMessage("更新包已下载，等待系统安装确认", OperationMessageType.Warning)
                )
            }
            return@launch
        }
        val result = runCatching {
            val check = versionGateway.check(currentVersionCode = 1)
            versionInstaller.prepare(check, check.sha256)
        }
        if (result.isSuccess && result.getOrNull() != null) {
            _uiState.update { it.copy(versionUpdate = it.versionUpdate.copy(progress = 0.92f, message = "更新包已校验，等待系统安装")) }
            val launched = versionInstaller?.launchInstall(result.getOrNull()!!) == true
            _uiState.update {
                it.copy(
                    versionUpdate = it.versionUpdate.copy(
                        phase = VersionUpdatePhase.Ready,
                        currentVersionName = latest,
                        progress = 1f,
                        message = if (launched) "已打开系统安装确认" else "更新包已准备完成"
                    ),
                    operationMessage = operationMessage("更新包 SHA-256 已校验", OperationMessageType.Success)
                )
            }
            return@launch
        }
        for (step in 1..10) {
            delay(90)
            _uiState.update { it.copy(versionUpdate = it.versionUpdate.copy(progress = step / 10f)) }
        }
        _uiState.update {
            it.copy(
                versionUpdate = updateState.copy(phase = VersionUpdatePhase.Failed, progress = 0f, message = "更新包下载或校验失败"),
                operationMessage = operationMessage("更新失败，请检查更新地址和网络", OperationMessageType.Error)
            )
        }
    }

    fun dismissVersionUpdate() = _uiState.update {
        it.copy(versionUpdate = it.versionUpdate.copy(phase = VersionUpdatePhase.Idle, progress = 0f, message = null))
    }

    private fun operationMessage(text: String, type: OperationMessageType): OperationMessage {
        runCatching { Log.i("PatrolOperation", "${type.name}: $text") }
        return OperationMessage(text, type)
    }

    private suspend fun runDeviceCommandWithOverlay(message: String, block: suspend () -> Unit) {
        _uiState.update {
            it.copy(
                deviceCommandInProgress = true,
                deviceCommandMessage = message
            )
        }
        try {
            block()
        } finally {
            _uiState.update {
                it.copy(
                    deviceCommandInProgress = false,
                    deviceCommandMessage = ""
                )
            }
        }
    }

    private fun addDeviceEvent(title: String, detail: String, level: DeviceEventLevel) {
        val event = newDeviceEvent(title, detail, level)
        _uiState.update { state ->
            state.copy(deviceEvents = (listOf(event) + state.deviceEvents).take(MaxDeviceEvents))
        }
    }

    private fun newDeviceEvent(title: String, detail: String, level: DeviceEventLevel): DeviceEvent =
        DeviceEvent(
            id = "local-${System.currentTimeMillis()}-${title.hashCode()}",
            title = title,
            detail = detail,
            level = level,
            timestamp = System.currentTimeMillis()
        )

    private fun Throwable.operatorFacingWifiError(): String =
        message
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.toOperatorFacingWifiError()
            ?: "设备 Wi-Fi 配置失败"

    private fun String.toOperatorFacingWifiError(): String =
        when {
            contains("device wifi did not enable: 5", ignoreCase = true) ||
                contains("WIFI_AP_STOP", ignoreCase = true) ->
                "设备热点未开启，请确认设备电量和当前模式后重试；若仍失败，请在设备侧重启 Wi-Fi 或重启设备"
            else -> this
        }

    private fun Throwable?.operatorFacingDeviceMediaError(): String {
        val detail = this?.message?.trim()?.takeIf { it.isNotBlank() } ?: "设备热点或文件服务未响应"
        if (detail.contains("device wifi switch rejected", ignoreCase = true) ||
            detail.contains("smartSetDeviceWiFiSwitch", ignoreCase = true)
        ) {
            return "设备文件读取失败：设备热点开启被拒绝或超时；请确认设备电量充足、蓝牙仍连接，并等待设备空闲后重试"
        }
        if (detail.contains("device media http service unavailable", ignoreCase = true) ||
            detail.contains("device media http service did not expose media list", ignoreCase = true)
        ) {
            return "设备文件读取失败：手机已连接设备热点，但设备文件服务没有响应；请保持蓝牙连接，等待设备空闲后重新查看设备文件"
        }
        return "设备文件读取失败：$detail；请确认手机已连接设备热点后重试"
    }

    private suspend fun materializeMediaForPreview(file: MediaFile): MediaFile? {
        if (!file.local) {
            file.takeIf { it.contentUri.hasUsableValue() }
                ?.withInternalLocalUri(allowRemote = true)
                ?.let { return it }
            return null
        }
        val existing = file.withInternalLocalUri(allowRemote = false)
        if (existing != null) return existing
        val needsCloudDownload = file.requiresCloudDownloadBeforeLocalPreview(backendBaseUrl)
        if (needsCloudDownload) {
            showOperationMessage("正在从云端下载 ${file.name}，完成后播放", OperationMessageType.Info)
        }
        localFileForCerebellumUpload(file, appContext, backendBaseUrl, secureStore)?.let { cached ->
            return rememberPreviewCache(file, cached)
        }
        if (needsCloudDownload) {
            return null
        }
        showOperationMessage("正在下载 ${file.name} 到手机沙盒后播放", OperationMessageType.Info)
        val downloaded = ensureMediaFileForCerebellum(file) ?: return null
        downloaded.withInternalLocalUri(allowRemote = false)?.let { return rememberPreviewCache(it, it.localContentFile() ?: return it) }
        val cached = localFileForCerebellumUpload(downloaded, appContext, backendBaseUrl, secureStore)
            ?: localFileForCerebellumUpload(file, appContext, backendBaseUrl, secureStore)
            ?: return null
        return rememberPreviewCache(downloaded, cached)
    }

    private fun rememberPreviewCache(file: MediaFile, cached: File): MediaFile? {
        val cachedFile = cached.takeIf { it.exists() && it.isFile && it.length() > 0 } ?: return null
        val existing = _uiState.value.mediaFiles.firstOrNull { it.id == file.id && it.local }
        val cachedMedia = file.copy(
            local = true,
            transferStatus = TransferStatus.Idle,
            progress = 0f,
            verified = true,
            contentUri = Uri.fromFile(cachedFile).toString(),
            lastTransferTarget = file.lastTransferTarget
        ).inheritCompletedCloudState(existing ?: file)
        _uiState.update { state ->
            state.copy(mediaFiles = state.mediaFiles.upsertMedia(cachedMedia))
        }
        return cachedMedia.withInternalLocalUri(allowRemote = false)
    }
}

private fun dailyReportOperatorNote(
    note: String,
    selectedMedia: List<MediaFile>,
    skippedMedia: List<MediaFile>
): String? {
    val parts = buildList {
        note.trim().takeIf { it.isNotBlank() }?.let { add(it) }
        if (selectedMedia.isNotEmpty()) {
            add("App选择分析媒体：${selectedMedia.joinToString { it.name }}")
        }
        if (skippedMedia.isNotEmpty()) {
            add("以下媒体暂无可上传的手机本地文件，仅按文件名纳入日报线索：${skippedMedia.joinToString { it.name }}")
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

private fun List<MediaFile>.reportableMedia(): List<MediaFile> =
    filter { it.kind == MediaKind.Video || it.kind == MediaKind.Audio || it.kind == MediaKind.Photo }
        .preferPhoneCopies()

private fun List<MediaFile>.preferPhoneCopies(): List<MediaFile> =
    groupBy { it.id }
        .values
        .map { copies ->
            copies.firstOrNull { it.local && it.contentUri.hasUsableValue() }
                ?: copies.firstOrNull { it.local }
                ?: copies.first()
        }

private fun String?.hasUsableValue(): Boolean = !isNullOrBlank()

private fun MediaFile.withShareableLocalUri(context: Context?, allowRemote: Boolean = true): MediaFile? {
    val value = contentUri?.takeIf { it.isNotBlank() } ?: return null
    val uri = runCatching { Uri.parse(value) }.getOrNull()
    val localFile = value.toExistingLocalFile()
    if (localFile == null) {
        return this.takeIf {
            uri?.scheme == "content" || (allowRemote && (value.startsWith("http://") || value.startsWith("https://")))
        }
    }
    if (context == null) return copy(contentUri = localFile.toURI().toString())
    val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localFile)
    return copy(local = true, contentUri = shareUri.toString())
}

private fun MediaFile.withInternalLocalUri(allowRemote: Boolean = true): MediaFile? {
    val value = contentUri?.takeIf { it.isNotBlank() } ?: return null
    val uri = runCatching { Uri.parse(value) }.getOrNull()
    val localFile = value.toExistingLocalFile()
    if (localFile != null) return copy(local = true, contentUri = localFile.toURI().toString())
    return this.takeIf {
        uri?.scheme == "content" || (allowRemote && (value.startsWith("http://") || value.startsWith("https://")))
    }
}

private fun MediaFile.localContentFile(): File? {
    val value = contentUri?.takeIf { it.isNotBlank() } ?: return null
    return value.toExistingLocalFile()
}

internal fun MediaFile.requiresCloudDownloadBeforeLocalPreview(backendBaseUrl: String): Boolean =
    local &&
        localContentFile() == null &&
        contentUri?.toRemoteMediaUrl(backendBaseUrl) != null

private fun String.toRemoteMediaUrl(backendBaseUrl: String): String? {
    val value = trim().takeIf { it.isNotBlank() } ?: return null
    return when {
        value.startsWith("http://") || value.startsWith("https://") -> value
        value.isBackendRelativeMediaUri() && backendBaseUrl.isNotBlank() ->
            backendBaseUrl.trimEnd('/') + value
        else -> null
    }
}

private fun String.isSameOriginAs(baseUrl: String): Boolean {
    if (baseUrl.isBlank()) return false
    val remote = runCatching { Uri.parse(this) }.getOrNull() ?: return false
    val base = runCatching { Uri.parse(baseUrl) }.getOrNull() ?: return false
    if (remote.host.isNullOrBlank() || base.host.isNullOrBlank()) return false
    fun effectivePort(uri: Uri): Int = when {
        uri.port >= 0 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        uri.scheme.equals("http", ignoreCase = true) -> 80
        else -> -1
    }
    return remote.scheme.equals(base.scheme, ignoreCase = true) &&
        remote.host.equals(base.host, ignoreCase = true) &&
        effectivePort(remote) == effectivePort(base)
}

private fun String.isBackendRelativeMediaUri(): Boolean =
    startsWith("/files/") || startsWith("/api/")

private fun String.toExistingLocalFile(): File? {
    val uri = runCatching { Uri.parse(this) }.getOrNull()
    val file = when {
        startsWith("file:", ignoreCase = true) -> runCatching { File(java.net.URI(this)) }.getOrNull()
        uri?.scheme == "file" -> {
            uri.path?.takeIf { it.isNotBlank() }?.let(::File)
                ?: runCatching { File(java.net.URI(this)) }.getOrNull()
        }
        uri?.scheme == null && startsWith("/") -> File(this)
        else -> null
    }
    return file?.takeIf { it.exists() && it.isFile && it.length() > 0 }
}

private fun MediaFile.lastModifiedFromContentUri(): Long =
    localContentFile()?.lastModified() ?: time.toLongOrNull() ?: 0L

private fun com.patrollink.domain.DeviceStatus.canReceiveDeviceCommand(): Boolean =
    id.isNotBlank() && online

private fun com.patrollink.domain.DeviceStatus.shouldReconnectControlChannel(capabilities: DeviceCapabilities): Boolean =
    online &&
        id.isNotBlank() &&
        type in setOf(DeviceType.Headset, DeviceType.Glasses) &&
        (
            onlineDuration.startsWith("系统蓝牙") ||
                (
                    !capabilities.supportsPhoto &&
                        !capabilities.supportsVideo &&
                        !capabilities.supportsAudioRecord
                    )
            )

private fun com.patrollink.domain.DeviceStatus.requiresSdkControlReadiness(): Boolean =
    online &&
        id.isNotBlank() &&
        type in setOf(DeviceType.Headset, DeviceType.Glasses)

private fun com.patrollink.domain.DeviceStatus.batteryTextForMessage(): String =
    if (batteryKnown) "${battery.coerceIn(0, 100)}%" else pendingReadLabelForMessage()

private fun com.patrollink.domain.DeviceStatus.storageTextForMessage(): String =
    if (!storageKnown || storageTotalGb <= 0f) pendingReadLabelForMessage() else "%.1f/%.1fGB".format(storageUsedGb, storageTotalGb)

private fun com.patrollink.domain.DeviceStatus.pendingReadLabelForMessage(): String =
    if (online && id.isNotBlank()) "读取中" else "读取失败"

private fun Long.toUptimeLabel(): String {
    val days = this / 86_400
    val hours = (this % 86_400) / 3_600
    val minutes = (this % 3_600) / 60
    return when {
        days > 0 -> "${days}天${hours}小时"
        hours > 0 -> "${hours}小时${minutes}分钟"
        else -> "${minutes}分钟"
    }
}

private fun String.toCerebellumCommandLabel(): String = when (this) {
    "refresh_files" -> "刷新文件索引"
    "sync_face_library" -> "同步人脸库"
    "health_check" -> "健康检查"
    else -> this
}

private suspend fun localFileForCerebellumUpload(
    file: MediaFile,
    context: Context?,
    backendBaseUrl: String,
    secureStore: SecureStore?
): File? = withContext(Dispatchers.IO) {
    val value = file.contentUri?.takeIf { it.isNotBlank() } ?: return@withContext null
    val uri = runCatching { Uri.parse(value) }.getOrNull()
    if (uri?.scheme == null && !value.startsWith("/")) {
        File(value).takeIf { it.exists() && it.isFile }?.let { return@withContext it }
    }
    val direct = if (value.startsWith("/") && backendBaseUrl.isBlank()) File(value) else null
    if (direct?.exists() == true && direct.isFile) return@withContext direct
    value.toExistingLocalFile()?.let { return@withContext it }
    val targetDir = context?.filesDir?.let { File(it, "patrol_media_cache/media_preview").also { dir -> dir.mkdirs() } }
        ?: return@withContext null
    val safeName = file.name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "${file.id}.bin" }
    val target = File(targetDir, "${file.id}-$safeName")
    if (target.exists() && target.isFile && target.length() > 0) return@withContext target
    if (uri?.scheme == "content") {
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return@withContext null
        return@withContext target.takeIf { it.exists() && it.isFile }
    }
    val remoteUrl = value.toRemoteMediaUrl(backendBaseUrl) ?: return@withContext null
    val backendRequest = remoteUrl.isSameOriginAs(backendBaseUrl)
    val connection = (URL(remoteUrl).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10_000
        readTimeout = 20_000
        setRequestProperty("Accept", "*/*")
        if (backendRequest) {
            setRequestProperty("clientid", OkHttpPatrolRestApi.DEFAULT_CLIENT_ID)
            secureStore?.readSession()?.accessToken?.takeIf { it.isNotBlank() }?.let {
                setRequestProperty("Authorization", "Bearer $it")
            }
        }
    }
    try {
        if (connection.responseCode !in 200..299) return@withContext null
        connection.inputStream.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target.takeIf { it.exists() && it.isFile && it.length() > 0 }
    } finally {
        connection.disconnect()
    }
}

private fun com.patrollink.domain.DeviceEvent.shouldRefreshMedia(): Boolean =
    id.startsWith("image-") ||
        id.startsWith("audio-") ||
        id.startsWith("store-")

private fun com.patrollink.domain.DeviceEvent.shouldRefreshDeviceCapabilities(): Boolean =
    id.startsWith("wifi-") ||
        id.startsWith("store-") ||
        id.startsWith("ota-")

private fun MediaKind.toCerebellumEvidenceType(): String = when (this) {
    MediaKind.Video -> "video"
    MediaKind.Photo -> "image"
    MediaKind.Audio -> "audio"
}

private fun FirmwareUpdateUiState.toFirmwareCheckResult(device: DeviceStatus): FirmwareCheckResult =
    FirmwareCheckResult(
        hasUpdate = true,
        firmwareId = firmwareId,
        deviceType = device.type.name.uppercase(),
        vendor = "UTE",
        chipset = "",
        deviceModel = device.name,
        hardwareVersion = "",
        firmwareType = packageFormat,
        versionCode = null,
        versionName = latestVersionName.orEmpty(),
        forceUpdate = forceUpdate,
        changelog = changelog,
        downloadUrl = downloadUrl,
        sha256 = sha256,
        fileId = firmwareId,
        fileSizeBytes = fileSizeBytes,
        packageFormat = packageFormat,
        upgradeMode = upgradeMode.ifBlank { "APP_CONTROLLED" },
        currentFirmwareVersion = currentVersionName.ifBlank { device.firmware },
        message = message.orEmpty()
    )

private fun String.toFirmwarePhase(): FirmwareUpdatePhase = when {
    contains("DOWNLOADING", ignoreCase = true) -> FirmwareUpdatePhase.Downloading
    contains("PREPARING", ignoreCase = true) ||
        contains("STARTED", ignoreCase = true) ||
        contains("HANDOFF", ignoreCase = true) ||
        contains("PENDING", ignoreCase = true) -> FirmwareUpdatePhase.Upgrading
    contains("FAILED", ignoreCase = true) ||
        contains("ERROR", ignoreCase = true) ||
        contains("UNAVAILABLE", ignoreCase = true) -> FirmwareUpdatePhase.Failed
    else -> FirmwareUpdatePhase.Upgrading
}

private fun String.toFirmwareMessage(errorMessage: String): String = when {
    errorMessage.isNotBlank() -> errorMessage
    contains("DOWNLOADING", ignoreCase = true) -> "正在下载固件包"
    contains("PREPARING", ignoreCase = true) -> "正在让设备进入升级准备状态"
    contains("STARTED", ignoreCase = true) -> "设备固件升级已开始"
    contains("HANDOFF", ignoreCase = true) -> "升级流程已交给设备，请等待设备重启完成"
    contains("PENDING", ignoreCase = true) -> "固件升级任务已提交"
    else -> "正在升级固件"
}

private fun AuthSession.hasUsableTokens(): Boolean =
    accessToken.isNotBlank() && refreshToken.isNotBlank()

private fun UserProfile.withDutyArea(areaName: String): UserProfile =
    if (areaName.isBlank() || dutyArea == areaName) this else copy(dutyArea = areaName)

private fun DeviceStatus.isControllableDevice(): Boolean =
    id.isNotBlank() && online

private fun DeviceStatus.hasSdkControlChannel(): Boolean =
    isControllableDevice() && !onlineDuration.startsWith("系统蓝牙")

private fun DeviceStatus.isStaleAudioConnection(systemConnected: List<DeviceStatus>): Boolean =
    isControllableDevice() &&
        type in setOf(DeviceType.Headset, DeviceType.Glasses) &&
        systemConnected.isNotEmpty() &&
        systemConnected.none { it.representsSameAudioDevice(this) }

private fun ScannedDevice.isSystemBluetoothAudioConnected(): Boolean =
    serviceUuid == "system-bluetooth-audio-connected" ||
        serviceUuid == "system-bluetooth-audio-control-connected"

private fun ScannedDevice.isSystemBluetoothAudioControlConnected(): Boolean =
    serviceUuid == "system-bluetooth-audio-control-connected"

private fun ScannedDevice.isNativeUteHeadsetControl(): Boolean =
    serviceUuid == "ute-ble-control-scanned" && type == DeviceType.Headset

private fun ScannedDevice.toConnectedAudioStatus(fallback: DeviceStatus): DeviceStatus =
    fallback.copy(
        id = id,
        name = name,
        online = true,
        signalBars = signalBars.coerceIn(1, 5),
        onlineDuration = "系统蓝牙已连接",
        type = type
    )

private fun DeviceStatus.representsSameAudioDevice(device: ScannedDevice): Boolean =
    id == device.id || (type == DeviceType.Headset && device.type == DeviceType.Headset && hasSimilarAudioName(name, device.name))

private fun DeviceStatus.representsSameAudioDevice(device: DeviceStatus): Boolean =
    id == device.id || (type == DeviceType.Headset && device.type == DeviceType.Headset && hasSimilarAudioName(name, device.name))

private fun String.normalizedDeviceKey(): String =
    uppercase().filter { it.isLetterOrDigit() }

private fun resolveConnectedDeviceType(bound: DeviceStatus, scannedName: String, scannedType: DeviceType): DeviceType =
    when {
        isKnownGlassesName(scannedName) || isKnownGlassesName(bound.name) -> DeviceType.Glasses
        isKnownAudioName(scannedName) || isKnownAudioName(bound.name) -> DeviceType.Headset
        bound.online && bound.type == DeviceType.Headset -> DeviceType.Headset
        bound.online && bound.type != scannedType -> bound.type
        else -> scannedType
    }

private fun hasSimilarAudioName(left: String, right: String): Boolean {
    val leftNormalized = left.uppercase()
    val rightNormalized = right.uppercase()
    return listOf("E1-PRO", "FORCELINK", "HEADSET", "耳机").any { marker ->
        marker in leftNormalized && marker in rightNormalized
    }
}

private fun isKnownAudioName(name: String): Boolean {
    val normalized = name.uppercase()
    return "E1-PRO" in normalized ||
        "FORCELINK" in normalized ||
        "HEADSET" in normalized ||
        "耳机" in name
}

private fun isKnownGlassesName(name: String): Boolean {
    val normalized = name.uppercase()
    return "GLORY GLASS" in normalized ||
        "GLASS" in normalized ||
        "ABA002" in normalized ||
        "眼镜" in name
}

private fun GpsLocation.hasUsableCoordinate(): Boolean =
    latitude.isFinite() &&
        longitude.isFinite() &&
        latitude in -90.0..90.0 &&
        longitude in -180.0..180.0 &&
        !(abs(latitude) < 0.000001 && abs(longitude) < 0.000001)

private fun MediaFile.matchesPhoneSandboxCopyOf(deviceFile: MediaFile): Boolean {
    if (!local || !contentUri.hasUsableValue()) return false
    if (id == deviceFile.id) return true
    val localKeys = mediaIdentityKeys()
    if (localKeys.isEmpty()) return false
    return deviceFile.mediaIdentityKeys().any { it in localKeys }
}

private fun MediaFile.matchesAnyIdentityKey(keys: Set<String>): Boolean =
    keys.isNotEmpty() && mediaIdentityKeys().any { it in keys }

private fun MediaFile.mediaIdentityKeys(): Set<String> = buildSet {
    name.normalizedMediaFileKey()?.let(::add)
    contentUri?.normalizedMediaFileKey()?.let(::add)
}

private fun String.normalizedMediaFileKey(): String? =
    substringBefore('?')
        .substringAfterLast('/')
        .removeMediaDisplayPrefix()
        .trim()
        .lowercase()
        .takeIf { it.isNotBlank() }

private fun String.removeMediaDisplayPrefix(): String =
    removePrefix("眼镜照片_")
        .removePrefix("眼镜视频_")
        .removePrefix("设备录音_")


private val TransferStatus.inProgress: Boolean
    get() = this == TransferStatus.Hashing || this == TransferStatus.Uploading || this == TransferStatus.Verifying

private fun MediaFile.inheritCompletedCloudState(current: MediaFile?): MediaFile {
    if (current == null) return this
    if (transferStatus.inProgress) return this
    val currentUploaded = current.transferStatus == TransferStatus.Done && current.lastTransferTarget == TransferTarget.Cloud
    if (!currentUploaded) return this
    return copy(
        transferStatus = TransferStatus.Done,
        progress = 1f,
        verified = verified || current.verified,
        contentUri = contentUri ?: current.contentUri,
        lastTransferTarget = TransferTarget.Cloud
    )
}

internal data class CloudVisionRecognitionDisplay(
    val title: String,
    val detail: String,
    val message: String,
    val level: DeviceEventLevel,
    val messageType: OperationMessageType,
)

internal fun CerebellumCombinedVisionAnalyzeResponseDto.toRecognitionDisplay(
    mediaName: String,
): CloudVisionRecognitionDisplay {
    val plateAvailable = !plate.backend.equals("simulated-fallback", ignoreCase = true)
    val faceAvailable = !face.backend.equals("simulated-fallback", ignoreCase = true)
    val plateCandidates = if (plateAvailable) {
        plate.candidates.mapNotNull { candidate ->
            candidate.plateNumber?.trim()?.takeIf(String::isNotEmpty)?.let { number ->
                candidate.confidence?.let { "$number（${it.asRecognitionPercent()}）" } ?: number
            }
        }.distinct()
    } else {
        emptyList()
    }
    val faceCandidates = if (faceAvailable) {
        face.faces.mapNotNull { it.candidate }.mapNotNull { candidate ->
            val identity = candidate.displayName?.trim()?.takeIf(String::isNotEmpty)
                ?: candidate.personId?.trim()?.takeIf(String::isNotEmpty)
                ?: candidate.candidateId?.trim()?.takeIf(String::isNotEmpty)
            identity?.let { name ->
                candidate.similarity?.let { "$name（${it.asRecognitionPercent()}）" } ?: name
            }
        }.distinct()
    } else {
        emptyList()
    }

    val recognitionParts = buildList {
        add(
            when {
                !plateAvailable -> "车牌算法暂不可用"
                plateCandidates.isNotEmpty() -> "车牌：${plateCandidates.take(3).joinToString("、")}"
                else -> "未识别到车牌"
            }
        )
        add(
            when {
                !faceAvailable -> "人脸算法暂不可用"
                faceCandidates.isNotEmpty() -> "人脸候选：${faceCandidates.take(3).joinToString("、")}"
                face.faceCount > 0 -> "检测到 ${face.faceCount} 张人脸，未命中布控"
                else -> "未检测到人脸"
            }
        )
    }
    val alertCount = alerts.size
    val degraded = !plateAvailable || !faceAvailable || platformDelivery.equals("QUEUE_FAILED", ignoreCase = true)
    val deliveryText = when (platformDelivery.uppercase(Locale.ROOT)) {
        "QUEUED" -> "命中告警已进入后台同步队列"
        "QUEUE_FAILED" -> "平台告警入队失败，请立即重试"
        "SKIPPED" -> "平台告警上报未启用"
        else -> null
    }
    val confirmationText = if (alertCount > 0) "命中布控 $alertCount 条，需人工确认" else "未命中布控"
    val elapsedText = "耗时 ${elapsedMs}ms"
    val details = buildList {
        add(mediaName)
        addAll(recognitionParts)
        add(confirmationText)
        deliveryText?.let(::add)
        add(elapsedText)
    }.joinToString("；")
    val level = if (alertCount > 0 || degraded) DeviceEventLevel.Warning else DeviceEventLevel.Info
    val messageType = when {
        platformDelivery.equals("QUEUE_FAILED", ignoreCase = true) -> OperationMessageType.Error
        alertCount > 0 || degraded -> OperationMessageType.Warning
        else -> OperationMessageType.Success
    }
    val title = if (alertCount > 0) "云端识别命中布控" else "云端识别已完成"
    val message = (recognitionParts + confirmationText + elapsedText).joinToString("；")
    return CloudVisionRecognitionDisplay(title, details, message, level, messageType)
}

private fun Double.asRecognitionPercent(): String =
    String.format(Locale.CHINA, "%.0f%%", (this.coerceIn(0.0, 1.0) * 100.0))
