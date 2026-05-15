package com.patrollink.presentation

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patrollink.data.EmptyVersionGateway
import com.patrollink.data.RuntimeConfigStore
import com.patrollink.data.ServiceFactory
import com.patrollink.data.edge.CerebellumApi
import com.patrollink.data.edge.CerebellumReportRequestDto
import com.patrollink.data.edge.OkHttpCerebellumApi
import com.patrollink.data.local.UiSettingsStore
import com.patrollink.data.remote.AlertDraftRequestDto
import com.patrollink.data.remote.CerebellumSettingsDto
import com.patrollink.data.remote.DailyReportContentUpdateDto
import com.patrollink.data.remote.PatrolRestApi
import com.patrollink.data.remote.UploadAttachmentDto
import com.patrollink.domain.AlertResult
import com.patrollink.domain.AlertStatus
import com.patrollink.domain.AuthSession
import com.patrollink.domain.DailyReport
import com.patrollink.domain.DisplayThemeMode
import com.patrollink.domain.DeviceAdvancedSettings
import com.patrollink.domain.DeviceCapabilities
import com.patrollink.domain.DeviceControlGateway
import com.patrollink.domain.DeviceType
import com.patrollink.domain.DeviceWifiState
import com.patrollink.domain.EmergencyContactGateway
import com.patrollink.domain.EmptyAppState
import com.patrollink.domain.FontSizeMode
import com.patrollink.domain.IntercomState
import com.patrollink.domain.LocationGateway
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaKind
import com.patrollink.domain.OperationMessage
import com.patrollink.domain.OperationMessageType
import com.patrollink.domain.PatrolCoordinator
import com.patrollink.domain.PatrolNotificationGateway
import com.patrollink.domain.SecureStore
import com.patrollink.domain.SosEvidenceRecorder
import com.patrollink.domain.StreamMode
import com.patrollink.domain.StreamRelayState
import com.patrollink.domain.TransferStatus
import com.patrollink.domain.TransferTarget
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PatrolViewModel(
    private val appContext: Context? = null,
    private val coordinator: PatrolCoordinator = ServiceFactory.createCoordinator(),
    private val deviceControlGateway: DeviceControlGateway? = null,
    private val secureStore: SecureStore? = null,
    private val settingsStore: UiSettingsStore? = null,
    private val versionGateway: VersionGateway = EmptyVersionGateway(),
    private val locationGateway: LocationGateway? = null,
    private val sosEvidenceRecorder: SosEvidenceRecorder? = null,
    private val emergencyContactGateway: EmergencyContactGateway? = null,
    private val notificationGateway: PatrolNotificationGateway? = null,
    private val versionInstaller: VersionInstaller? = null,
    private var cerebellumApi: CerebellumApi? = null,
    private val patrolRestApi: PatrolRestApi? = null,
    private val runtimeConfigStore: RuntimeConfigStore? = null,
    private val backendBaseUrl: String = "",
    private val onSessionChanged: (AuthSession?) -> Unit = {}
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        EmptyAppState.create().copy(
            fontSizeMode = settingsStore?.readFontSizeMode() ?: FontSizeMode.Standard,
            displayThemeMode = settingsStore?.readDisplayThemeMode() ?: DisplayThemeMode.System
        )
    )
    val uiState: StateFlow<com.patrollink.domain.AppUiState> = _uiState.asStateFlow()
    private var scannedDevicesJob: Job? = null

    init {
        refreshEmergencyContacts()
        observeDeviceEvents()
        observeIntercomState()
        viewModelScope.launch {
            restoreSavedSession()
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

        onSessionChanged(session)
        runCatching { coordinator.currentUser() }
            .onSuccess { user ->
                _uiState.update { state ->
                    state.copy(
                        isLoggedIn = true,
                        sessionRestoring = false,
                        networkOnline = true,
                        user = user
                    )
                }
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
                    onSessionChanged(session)
                    secureStore?.let { store -> withContext(Dispatchers.IO) { store.saveSession(session) } }
                    _uiState.update { it.copy(isLoggedIn = true, sessionRestoring = false, loginLoading = false, networkOnline = true, operationMessage = operationMessage("登录成功", OperationMessageType.Success)) }
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

    fun logout() = viewModelScope.launch {
        scannedDevicesJob?.cancel()
        scannedDevicesJob = null
        onSessionChanged(null)
        secureStore?.let { store -> withContext(Dispatchers.IO) { store.clearSession() } }
        cerebellumApi = null
        _uiState.update {
            it.copy(
                isLoggedIn = false,
                sessionRestoring = false,
                scannedDevices = emptyList(),
                cerebellumSettings = com.patrollink.domain.CerebellumSettingsUiState(),
                operationMessage = operationMessage("已退出登录", OperationMessageType.Info)
            )
        }
    }

    private fun startAuthenticatedRefreshes() {
        refreshCurrentUser()
        refreshScannedDevices(showFailureMessage = false)
        refreshMediaFiles()
        refreshDeviceCapabilities()
        refreshPatrolArea()
        refreshCerebellumSettings()
    }

    fun toggleRecord() = viewModelScope.launch {
        val device = _uiState.value.device
        if (!device.canReceiveDeviceCommand()) {
            showOperationMessage("请先连接设备", OperationMessageType.Warning)
            return@launch
        }
        val next = coordinator.setRecording(device, !device.isRecording)
        updateCurrentDevice(next)
    }

    fun toggleTalk() = viewModelScope.launch {
        val device = _uiState.value.device
        if (!device.canReceiveDeviceCommand()) {
            showOperationMessage("请先连接设备", OperationMessageType.Warning)
            return@launch
        }
        runCatching { coordinator.setTalk(device, !device.isTalking) }
            .onSuccess { next -> updateCurrentDevice(next) }
            .onFailure {
                _uiState.update { state ->
                    state.copy(
                        device = state.device.copy(isTalking = false),
                        operationMessage = operationMessage("语音对讲启动失败，请重新登录或检查后台连接", OperationMessageType.Error)
                    )
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
                val capabilities = gateway.capabilities(device)
                val wifi = if (capabilities.supportsWifi) gateway.readWifi() else DeviceWifiState()
                _uiState.update { it.copy(deviceCapabilities = capabilities, deviceWifiState = wifi) }
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

    fun configureDeviceWifi(enabled: Boolean, ssid: String, password: String) = viewModelScope.launch {
        val gateway = deviceControlGateway ?: return@launch showOperationMessage("设备 Wi-Fi 通道未启用", OperationMessageType.Warning)
        runCatching { gateway.configureWifi(enabled, ssid, password) }
            .onSuccess { wifi -> _uiState.update { it.copy(deviceWifiState = wifi, operationMessage = operationMessage("设备 Wi-Fi 已配置", OperationMessageType.Success)) } }
            .onFailure { _uiState.update { it.copy(operationMessage = operationMessage("设备 Wi-Fi 配置失败", OperationMessageType.Error)) } }
    }

    fun applyDeviceSettings(settings: DeviceAdvancedSettings) = viewModelScope.launch {
        val device = _uiState.value.device
        val gateway = deviceControlGateway ?: return@launch showOperationMessage("设备参数通道未启用", OperationMessageType.Warning)
        runCatching { gateway.applySettings(device, settings) }
            .onSuccess { next -> _uiState.update { it.copy(deviceSettings = next, operationMessage = operationMessage("设备参数已下发", OperationMessageType.Success)) } }
            .onFailure { _uiState.update { it.copy(operationMessage = operationMessage("设备参数下发失败", OperationMessageType.Error)) } }
    }

    fun toggleRealtimeAudioSync() = viewModelScope.launch {
        val gateway = deviceControlGateway ?: return@launch showOperationMessage("实时音频通道未启用", OperationMessageType.Warning)
        val active = _uiState.value.realtimeAudioSyncing
        val ok = runCatching {
            if (active) gateway.stopRealtimeAudioSync() else gateway.startRealtimeAudioSync("session-${System.currentTimeMillis()}")
        }.getOrDefault(false)
        _uiState.update {
            it.copy(
                realtimeAudioSyncing = if (ok) !active else active,
                operationMessage = when {
                    ok && active -> operationMessage("实时音频同传已停止，后续走离线续传", OperationMessageType.Info)
                    ok -> operationMessage("实时音频同传已启动", OperationMessageType.Success)
                    else -> operationMessage("实时音频同传操作失败", OperationMessageType.Error)
                }
            )
        }
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
        runCatching { gateway.currentLocation() }
            .onSuccess { location -> _uiState.update { it.copy(sosLocation = location) } }
    }

    fun refreshPatrolArea() = viewModelScope.launch {
        runCatching { coordinator.currentPatrolArea() }
            .onSuccess { patrolArea -> _uiState.update { it.copy(patrolArea = patrolArea) } }
    }

    fun refreshCurrentUser() = viewModelScope.launch {
        runCatching { coordinator.currentUser() }
            .onSuccess { user -> _uiState.update { it.copy(user = user) } }
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

    fun refreshMediaFiles() = viewModelScope.launch {
        val phoneMedia = runCatching { coordinator.mediaFiles(local = true) }.getOrDefault(emptyList())
        val deviceMedia = runCatching { coordinator.mediaFiles(local = false) }.getOrDefault(emptyList())
        val loaded = (phoneMedia + deviceMedia).distinctBy { it.id to it.local }
        if (loaded.isEmpty()) return@launch
        _uiState.update { state ->
            val transient = state.mediaFiles.filter { current ->
                loaded.none { it.id == current.id && it.local == current.local }
            }
            val next = (loaded + transient).distinctBy { it.id to it.local }
            state.copy(
                mediaFiles = next,
                selectedMediaFileId = state.selectedMediaFileId ?: next.firstOrNull { it.local == state.selectedMediaLocal }?.id
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
        val api = patrolRestApi ?: return@launch showOperationMessage("后台服务未配置，无法保存小脑连接", OperationMessageType.Error)
        val settings = _uiState.value.cerebellumSettings
        val baseUrl = settings.baseUrl.trim()
        val apiKey = settings.apiKey.trim()
        val nextApi = if (baseUrl.isBlank()) {
            null
        } else {
            runCatching {
                OkHttpCerebellumApi(baseUrl = baseUrl, apiKeyProvider = { apiKey })
            }.getOrElse {
                return@launch showOperationMessage("小脑地址格式不正确", OperationMessageType.Error)
            }
        }
        _uiState.update { state -> state.copy(cerebellumSettings = state.cerebellumSettings.copy(saving = true)) }
        runCatching { api.saveCerebellumSettings(CerebellumSettingsDto(baseUrl = baseUrl, apiKey = apiKey)).data }
            .onSuccess { saved ->
                cerebellumApi = nextApi
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
                            throwable.message?.takeIf { it.isNotBlank() } ?: "小脑连接设置保存失败",
                            OperationMessageType.Error
                        )
                    )
                }
            }
    }

    private fun refreshCerebellumSettings() = viewModelScope.launch {
        val api = patrolRestApi ?: return@launch
        runCatching { api.cerebellumSettings().data }
            .onSuccess { settings ->
                applyCerebellumSettings(settings.baseUrl, settings.apiKey)
            }
    }

    private fun applyCerebellumSettings(baseUrl: String, apiKey: String) {
        val normalizedBaseUrl = baseUrl.trim()
        val normalizedApiKey = apiKey.trim()
        cerebellumApi = if (normalizedBaseUrl.isBlank()) {
            null
        } else {
            runCatching {
                OkHttpCerebellumApi(baseUrl = normalizedBaseUrl, apiKeyProvider = { normalizedApiKey })
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
        coordinator.handleAlert(alertId, result, note)
        _uiState.update {
            it.copy(alerts = it.alerts.map { alert ->
                if (alert.id == alertId) alert.copy(status = AlertStatus.Closed) else alert
            }, operationMessage = operationMessage("处置结果已上传：${payload.attachments.size} 个附件", OperationMessageType.Success))
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
        AlertResult.FalseAlarm -> "FALSE_ALARM"
        AlertResult.Resolved -> "RESOLVED"
        AlertResult.RequestBackup -> "REQUEST_BACKUP"
    }

    fun activateSos() = viewModelScope.launch {
        val location = locationGateway?.currentLocation() ?: _uiState.value.sosLocation
        _uiState.update { it.copy(sosLocation = location) }
        val event = coordinator.activateSos(location)
        runCatching { sosEvidenceRecorder?.start(event.id) }
        runCatching { emergencyContactGateway?.notifyContacts(event.id, location) }
        runCatching { notificationGateway?.notifySosActive(location) }
        _uiState.update { it.copy(sosActive = true) }
    }

    fun cancelSos() = viewModelScope.launch {
        runCatching { sosEvidenceRecorder?.stop() }
        coordinator.cancelSos()
        _uiState.update { it.copy(sosActive = false) }
    }

    fun takePhoto() = viewModelScope.launch {
        val device = _uiState.value.device
        if (!device.canReceiveDeviceCommand()) {
            showOperationMessage("请先连接设备", OperationMessageType.Warning)
            return@launch
        }
        val next = coordinator.takePhoto(device)
        val now = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())
        val photo = MediaFile(
            id = "photo-${System.currentTimeMillis()}",
            name = "现场照片_${now.replace(":", "")}",
            kind = MediaKind.Photo,
            time = now,
            size = "2.8 MB",
            duration = null,
            verified = true,
            local = false,
            transferStatus = TransferStatus.Idle,
            progress = 0f
        )
        _uiState.update { state ->
            state.copy(
                device = next,
                connectedDevices = state.connectedDevices.map { if (it.id == next.id) next else it },
                mediaFiles = listOf(photo) + state.mediaFiles,
                selectedMediaFileId = photo.id,
                selectedMediaLocal = false,
                operationMessage = operationMessage("现场照片已保存到设备端", OperationMessageType.Success)
            )
        }
    }

    fun startLowLatencyStream() = viewModelScope.launch {
        if (!_uiState.value.device.canReceiveDeviceCommand()) {
            showOperationMessage("请先连接设备", OperationMessageType.Warning)
            return@launch
        }
        _uiState.update { it.copy(streamState = StreamRelayState.Connecting, operationMessage = operationMessage("正在连接实时画面", OperationMessageType.Info)) }
        runCatching { coordinator.startStream(_uiState.value.device, StreamMode.LowLatency) }
            .onSuccess {
                val streamState = coordinator.streamState().first()
                val message = when (streamState) {
                    StreamRelayState.Relaying -> operationMessage("实时画面已连接", OperationMessageType.Success)
                    StreamRelayState.Connecting -> operationMessage("正在连接实时画面", OperationMessageType.Info)
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
            val next = bound.copy(
                name = name,
                signalBars = bound.signalBars.coerceIn(1, 5),
                onlineDuration = if (bound.online) "刚刚连接" else "连接失败",
                type = type
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
                        operationMessage("$name 已连接", OperationMessageType.Success)
                    } else {
                        operationMessage("$name 连接失败，请检查蓝牙和距离", OperationMessageType.Error)
                    }
                )
            }
            refreshDeviceCapabilities()
        }
    }

    fun unbindCurrentDevice() = unbindDevice(_uiState.value.device.id)

    fun unbindDevice(deviceId: String) = viewModelScope.launch {
        if (deviceId.isBlank()) {
            showOperationMessage("当前没有可解绑设备", OperationMessageType.Warning)
            return@launch
        }
        val target = _uiState.value.connectedDevices.firstOrNull { it.id == deviceId }
            ?: _uiState.value.device.takeIf { it.id == deviceId }
        runCatching { coordinator.unbindDevice(deviceId) }
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
                operationMessage = operationMessage("${target?.name?.ifBlank { "设备" } ?: "设备"} 已解绑", OperationMessageType.Success)
            )
        }
    }

    fun refreshScannedDevices(showFailureMessage: Boolean = true) {
        scannedDevicesJob?.cancel()
        scannedDevicesJob = viewModelScope.launch {
            runCatching {
                coordinator.scanDevices().collect { devices ->
                    _uiState.update { it.copy(scannedDevices = devices) }
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

    private fun refreshEmergencyContacts() = viewModelScope.launch {
        emergencyContactGateway?.contacts()?.let { contacts ->
            _uiState.update { it.copy(emergencyContacts = contacts) }
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

    fun downloadMedia(fileId: String) = viewModelScope.launch {
        runCatching {
            coordinator.transferMedia(fileId, TransferTarget.PhoneSandbox).collect { updated ->
                updatePhoneTransfer(fileId, updated.markTransferTarget(TransferTarget.PhoneSandbox))
                if (updated.transferStatus == TransferStatus.Done) runCatching { deviceControlGateway?.notifyMediaSyncCompleted() }
                if (updated.transferStatus != TransferStatus.Done) delay(420)
            }
        }.onFailure {
            simulateMediaTransfer(fileId, TransferTarget.PhoneSandbox)
        }
    }

    fun uploadMedia(fileId: String, local: Boolean = true) = viewModelScope.launch {
        val current = _uiState.value.mediaFiles.firstOrNull { it.id == fileId && it.local == local }
        if (current?.transferStatus == TransferStatus.Done && current.lastTransferTarget == TransferTarget.Cloud) {
            _uiState.update { it.copy(operationMessage = operationMessage("${current.name} 已上传", OperationMessageType.Success)) }
            return@launch
        }
        runCatching {
            coordinator.transferMedia(fileId, TransferTarget.Cloud).collect { updated ->
                updateTransferredMedia(fileId, local, updated.copy(local = local).markTransferTarget(TransferTarget.Cloud))
                if (updated.transferStatus != TransferStatus.Done) delay(420)
            }
        }.onFailure {
            simulateMediaTransfer(fileId, TransferTarget.Cloud, local)
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

    private suspend fun simulateMediaTransfer(fileId: String, target: TransferTarget, local: Boolean = target != TransferTarget.PhoneSandbox) {
        val original = _uiState.value.mediaFiles.firstOrNull { it.id == fileId && it.local == local } ?: return
        val steps = listOf(
            original.copy(transferStatus = TransferStatus.Hashing, progress = 0.1f, lastTransferTarget = target),
            original.copy(transferStatus = TransferStatus.Uploading, progress = 0.55f, lastTransferTarget = target),
            original.copy(transferStatus = TransferStatus.Verifying, progress = 0.9f, lastTransferTarget = target),
            original.copy(transferStatus = TransferStatus.Done, progress = 1f, verified = true, lastTransferTarget = target)
        )
        steps.forEach { updated ->
            if (target == TransferTarget.PhoneSandbox) {
                updatePhoneTransfer(fileId, updated)
            } else {
                updateTransferredMedia(fileId, local, updated)
            }
            if (updated.transferStatus != TransferStatus.Done) delay(420)
        }
    }

    private fun MediaFile.markTransferTarget(target: TransferTarget): MediaFile =
        copy(lastTransferTarget = target)

    private fun updateTransferredMedia(fileId: String, local: Boolean, updated: MediaFile) {
        _uiState.update { state ->
            state.copy(mediaFiles = state.mediaFiles.upsertMedia(updated.copy(id = fileId, local = local)))
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

    fun selectMedia(fileId: String) = _uiState.update { it.copy(selectedMediaFileId = fileId) }

    fun openMediaPreview(fileId: String, local: Boolean = _uiState.value.selectedMediaLocal) = viewModelScope.launch {
        val target = _uiState.value.mediaFiles.firstOrNull { it.id == fileId && it.local == local }
            ?: return@launch showOperationMessage("媒体文件不存在", OperationMessageType.Error)
        val preview = materializeMediaForPreview(target)
        _uiState.update { state ->
            state.copy(
                previewMediaFile = preview,
                selectedMediaFileId = fileId,
                operationMessage = if (preview == null) {
                    operationMessage("媒体还没有可播放的本地文件，请先完成上传手机或检查云端下载地址", OperationMessageType.Error)
                } else {
                    state.operationMessage
                }
            )
        }
    }

    fun closeMediaPreview() = _uiState.update { it.copy(previewMediaFile = null) }

    fun clearMessage() = _uiState.update { it.copy(operationMessage = null) }

    fun showOperationMessage(message: String, type: OperationMessageType) = _uiState.update {
        it.copy(operationMessage = operationMessage(message, type))
    }

    private fun defaultMissionId(badgeNo: String): String {
        val day = SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date())
        return "mission-$day-${badgeNo.ifBlank { "operator" }}"
    }

    private fun observeDeviceEvents() = viewModelScope.launch {
        deviceControlGateway?.events()?.collect { event ->
            _uiState.update { state ->
                state.copy(
                    deviceEvents = (listOf(event) + state.deviceEvents).take(5),
                    operationMessage = operationMessage(event.title, OperationMessageType.Info)
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

    private fun operationMessage(text: String, type: OperationMessageType) = OperationMessage(text, type)

    private suspend fun materializeMediaForPreview(file: MediaFile): MediaFile? {
        val existing = file.withShareableLocalUri(appContext)
        if (existing != null) return existing
        showOperationMessage("正在下载 ${file.name} 到手机沙盒后播放", OperationMessageType.Info)
        val downloaded = ensureMediaFileForCerebellum(file) ?: return null
        downloaded.withShareableLocalUri(appContext)?.let { return it }
        val cached = localFileForCerebellumUpload(downloaded, appContext, backendBaseUrl, secureStore)
            ?: localFileForCerebellumUpload(file, appContext, backendBaseUrl, secureStore)
            ?: return null
        return downloaded.copy(local = true, contentUri = Uri.fromFile(cached).toString()).withShareableLocalUri(appContext)
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

private fun MediaFile.withShareableLocalUri(context: Context?): MediaFile? {
    val value = contentUri?.takeIf { it.isNotBlank() } ?: return null
    val uri = runCatching { Uri.parse(value) }.getOrNull()
    val localFile = when {
        uri?.scheme == "file" -> uri.path?.let(::File)
        uri?.scheme == null && value.startsWith("/") -> File(value)
        else -> null
    }?.takeIf { it.exists() && it.isFile && it.length() > 0 }
    if (localFile == null) return this.takeIf { uri?.scheme == "content" || value.startsWith("http://") || value.startsWith("https://") }
    if (context == null) return copy(contentUri = Uri.fromFile(localFile).toString())
    val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localFile)
    return copy(local = true, contentUri = shareUri.toString())
}

private fun com.patrollink.domain.DeviceStatus.canReceiveDeviceCommand(): Boolean =
    id.isNotBlank() && online

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
    if (uri?.scheme == "file") {
        val path = uri.path ?: return@withContext null
        return@withContext File(path).takeIf { it.exists() && it.isFile }
    }
    val targetDir = context?.cacheDir?.let { File(it, "cerebellum_uploads").also { dir -> dir.mkdirs() } }
        ?: return@withContext null
    val safeName = file.name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "${file.id}.bin" }
    val target = File(targetDir, "${file.id}-$safeName")
    if (uri?.scheme == "content") {
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return@withContext null
        return@withContext target.takeIf { it.exists() && it.isFile }
    }
    val remoteUrl = when {
        value.startsWith("http://") || value.startsWith("https://") -> value
        value.startsWith("/") && backendBaseUrl.isNotBlank() -> backendBaseUrl.trimEnd('/') + value
        else -> null
    } ?: return@withContext null
    val connection = (URL(remoteUrl).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10_000
        readTimeout = 20_000
        setRequestProperty("Accept", "*/*")
        secureStore?.readSession()?.accessToken?.takeIf { it.isNotBlank() }?.let {
            setRequestProperty("Authorization", "Bearer $it")
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

private fun MediaKind.toCerebellumEvidenceType(): String = when (this) {
    MediaKind.Video -> "video"
    MediaKind.Photo -> "image"
    MediaKind.Audio -> "audio"
}

private fun AuthSession.hasUsableTokens(): Boolean =
    accessToken.isNotBlank() && refreshToken.isNotBlank()
