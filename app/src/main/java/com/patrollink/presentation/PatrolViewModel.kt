package com.patrollink.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patrollink.data.MockVersionGateway
import com.patrollink.data.MockPatrolRepository
import com.patrollink.data.ServiceFactory
import com.patrollink.data.local.UiSettingsStore
import com.patrollink.data.remote.AlertDraftRequestDto
import com.patrollink.data.remote.UploadAttachmentDto
import com.patrollink.domain.AlertResult
import com.patrollink.domain.AlertStatus
import com.patrollink.domain.AuthSession
import com.patrollink.domain.DisplayThemeMode
import com.patrollink.domain.DeviceType
import com.patrollink.domain.FontSizeMode
import com.patrollink.domain.GpsLocation
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaKind
import com.patrollink.domain.PatrolCoordinator
import com.patrollink.domain.SecureStore
import com.patrollink.domain.StreamMode
import com.patrollink.domain.StreamRelayState
import com.patrollink.domain.TransferStatus
import com.patrollink.domain.TransferTarget
import com.patrollink.domain.VersionGateway
import com.patrollink.domain.VersionUpdatePhase
import com.patrollink.domain.VersionUpdateUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PatrolViewModel(
    private val coordinator: PatrolCoordinator = ServiceFactory.createCoordinator(),
    private val secureStore: SecureStore? = null,
    private val settingsStore: UiSettingsStore? = null,
    private val versionGateway: VersionGateway = MockVersionGateway()
) : ViewModel() {
    private val repository = MockPatrolRepository()
    private val _uiState = MutableStateFlow(
        repository.initialState().copy(
            fontSizeMode = settingsStore?.readFontSizeMode() ?: FontSizeMode.Standard,
            displayThemeMode = settingsStore?.readDisplayThemeMode() ?: DisplayThemeMode.System
        )
    )
    val uiState: StateFlow<com.patrollink.domain.AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            secureStore?.readSession()?.let {
                _uiState.update { state -> state.copy(isLoggedIn = true, networkOnline = true) }
                runCatching { coordinator.currentRealtimeState() }
            }
        }
    }

    fun login(account: String, password: String, agreed: Boolean) {
        if (account.isBlank() || password.isBlank() || !agreed) return
        viewModelScope.launch {
            _uiState.update { it.copy(loginLoading = true) }
            runCatching { coordinator.loginAndStartSession(account, password) }
                .onSuccess { session ->
                    secureStore?.saveSession(session)
                    _uiState.update { it.copy(isLoggedIn = true, loginLoading = false, networkOnline = true, operationMessage = "登录成功") }
                }
                .onFailure { _uiState.update { it.copy(loginLoading = false, networkOnline = false) } }
        }
    }

    fun logout() = viewModelScope.launch {
        secureStore?.clearSession()
        _uiState.update { it.copy(isLoggedIn = false, operationMessage = "已退出登录") }
    }

    fun toggleRecord() = viewModelScope.launch {
        val device = _uiState.value.device
        val next = coordinator.setRecording(device, !device.isRecording)
        updateCurrentDevice(next)
    }

    fun toggleTalk() = viewModelScope.launch {
        val device = _uiState.value.device
        val next = coordinator.setTalk(device, !device.isTalking)
        updateCurrentDevice(next)
    }

    fun setAlertTab(status: AlertStatus) = _uiState.update { it.copy(selectedAlertTab = status) }

    fun setMediaLocal(local: Boolean) = _uiState.update { it.copy(selectedMediaLocal = local) }

    fun setFontSizeMode(mode: FontSizeMode) {
        settingsStore?.saveFontSizeMode(mode)
        _uiState.update { it.copy(fontSizeMode = mode, operationMessage = "字体大小已保存") }
    }

    fun setDisplayThemeMode(mode: DisplayThemeMode) {
        settingsStore?.saveDisplayThemeMode(mode)
        _uiState.update { it.copy(displayThemeMode = mode, operationMessage = "主题模式已保存") }
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
            }, operationMessage = "处置结果已上传：${payload.attachments.size} 个附件")
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
        _uiState.update { it.copy(operationMessage = "草稿已保存：$resultLabel，${payload.attachments.size} 个附件待提交") }
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
        operatorId = "POLICE_9527",
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
        operatorId = "POLICE_9527",
        attachments = attachments.map { it.copy(uploadIntent = "UPLOAD_NOW") }
    )

    private fun AlertResult.toApiValue(): String = when (this) {
        AlertResult.FalseAlarm -> "FALSE_ALARM"
        AlertResult.Resolved -> "RESOLVED"
        AlertResult.RequestBackup -> "REQUEST_BACKUP"
    }

    fun activateSos() = viewModelScope.launch {
        coordinator.activateSos(
            GpsLocation(
                latitude = 39.9087,
                longitude = 116.3975,
                accuracyMeters = 8.5f,
                address = "核心商务区 CBD-North"
            )
        )
        _uiState.update { it.copy(sosActive = true) }
    }

    fun cancelSos() = viewModelScope.launch {
        coordinator.cancelSos()
        _uiState.update { it.copy(sosActive = false) }
    }

    fun takePhoto() = viewModelScope.launch {
        val next = coordinator.takePhoto(_uiState.value.device)
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
                operationMessage = "现场照片已保存到设备端"
            )
        }
    }

    fun startLowLatencyStream() = viewModelScope.launch {
        _uiState.update { it.copy(streamState = StreamRelayState.Connecting, operationMessage = "正在连接实时画面") }
        runCatching { coordinator.startStream(_uiState.value.device, StreamMode.LowLatency) }
            .onSuccess { _uiState.update { it.copy(streamState = StreamRelayState.Relaying, operationMessage = "实时画面已连接") } }
            .onFailure { _uiState.update { it.copy(streamState = StreamRelayState.Failed, operationMessage = "实时画面连接失败") } }
    }

    fun stopStream() = viewModelScope.launch {
        runCatching { coordinator.stopStream() }
        _uiState.update { it.copy(streamState = StreamRelayState.Idle, operationMessage = "实时画面已关闭") }
    }

    fun connectDiscoveredDevice(id: String, name: String, mac: String, signalBars: Int, type: DeviceType) = _uiState.update { state ->
        val next = state.device.copy(
            id = id,
            name = name,
            online = true,
            battery = 100,
            signalBars = signalBars.coerceIn(1, 5),
            onlineDuration = "刚刚连接",
            storageUsedGb = if (type == DeviceType.Recorder) state.device.storageUsedGb else 0f,
            storageTotalGb = if (type == DeviceType.Recorder) state.device.storageTotalGb else 0f,
            isRecording = false,
            isTalking = false,
            cloudConnected = type != DeviceType.Sensor,
            type = type
        )
        val connected = (state.connectedDevices.filterNot { it.type == next.type } + next)
        state.copy(
            device = next,
            connectedDevices = connected,
            selectedDeviceId = next.id,
            operationMessage = "$name 已连接"
        )
    }

    fun selectConnectedDevice(deviceId: String) = _uiState.update { state ->
        val selected = state.connectedDevices.firstOrNull { it.id == deviceId } ?: return@update state
        state.copy(
            device = selected,
            selectedDeviceId = selected.id,
            streamState = StreamRelayState.Idle
        )
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
        coordinator.transferMedia(fileId, TransferTarget.PhoneSandbox).collect { updated ->
            _uiState.update { state ->
                state.copy(mediaFiles = state.mediaFiles.map { if (it.id == fileId) updated else it })
            }
        }
    }

    fun uploadMedia(fileId: String) = viewModelScope.launch {
        coordinator.transferMedia(fileId, TransferTarget.Cloud).collect { updated ->
            _uiState.update { state ->
                state.copy(mediaFiles = state.mediaFiles.map { if (it.id == fileId) updated else it })
            }
        }
    }

    fun deleteMedia(fileId: String) = viewModelScope.launch {
        if (coordinator.deleteMedia(fileId)) {
            _uiState.update { state ->
                state.copy(
                    mediaFiles = state.mediaFiles.filterNot { it.id == fileId },
                    selectedMediaFileId = if (state.selectedMediaFileId == fileId) null else state.selectedMediaFileId,
                    previewMediaFile = if (state.previewMediaFile?.id == fileId) null else state.previewMediaFile,
                    operationMessage = "媒体文件已删除"
                )
            }
        }
    }

    fun selectMedia(fileId: String) = _uiState.update { it.copy(selectedMediaFileId = fileId) }

    fun openMediaPreview(fileId: String) = _uiState.update { state ->
        state.copy(previewMediaFile = state.mediaFiles.firstOrNull { it.id == fileId }, selectedMediaFileId = fileId)
    }

    fun closeMediaPreview() = _uiState.update { it.copy(previewMediaFile = null) }

    fun clearMessage() = _uiState.update { it.copy(operationMessage = null) }

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
        _uiState.update { it.copy(versionUpdate = it.versionUpdate.copy(phase = VersionUpdatePhase.Downloading, progress = 0f, message = "正在下载更新")) }
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
                operationMessage = "更新包已下载，等待系统安装确认"
            )
        }
    }

    fun dismissVersionUpdate() = _uiState.update {
        it.copy(versionUpdate = it.versionUpdate.copy(phase = VersionUpdatePhase.Idle, progress = 0f, message = null))
    }
}
