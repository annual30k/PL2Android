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
import com.patrollink.domain.DeviceAdvancedSettings
import com.patrollink.domain.DeviceCapabilities
import com.patrollink.domain.DeviceControlGateway
import com.patrollink.domain.DeviceType
import com.patrollink.domain.DeviceWifiState
import com.patrollink.domain.EmergencyContactGateway
import com.patrollink.domain.FontSizeMode
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PatrolViewModel(
    private val coordinator: PatrolCoordinator = ServiceFactory.createCoordinator(),
    private val deviceControlGateway: DeviceControlGateway? = null,
    private val secureStore: SecureStore? = null,
    private val settingsStore: UiSettingsStore? = null,
    private val versionGateway: VersionGateway = MockVersionGateway(),
    private val locationGateway: LocationGateway? = null,
    private val sosEvidenceRecorder: SosEvidenceRecorder? = null,
    private val emergencyContactGateway: EmergencyContactGateway? = null,
    private val notificationGateway: PatrolNotificationGateway? = null,
    private val versionInstaller: VersionInstaller? = null,
    private val onSessionChanged: (AuthSession?) -> Unit = {}
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
        refreshScannedDevices()
        refreshEmergencyContacts()
        observeDeviceEvents()
        refreshDeviceCapabilities()
        refreshPatrolArea()
        viewModelScope.launch {
            secureStore?.readSession()?.let {
                onSessionChanged(it)
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
                    onSessionChanged(session)
                    secureStore?.saveSession(session)
                    _uiState.update { it.copy(isLoggedIn = true, loginLoading = false, networkOnline = true, operationMessage = operationMessage("登录成功", OperationMessageType.Success)) }
                }
                .onFailure { _uiState.update { it.copy(loginLoading = false, networkOnline = false) } }
        }
    }

    fun logout() = viewModelScope.launch {
        onSessionChanged(null)
        secureStore?.clearSession()
        _uiState.update { it.copy(isLoggedIn = false, operationMessage = operationMessage("已退出登录", OperationMessageType.Info)) }
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

    fun refreshDeviceCapabilities() = viewModelScope.launch {
        val device = _uiState.value.device
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
                operationMessage = operationMessage("现场照片已保存到设备端", OperationMessageType.Success)
            )
        }
    }

    fun startLowLatencyStream() = viewModelScope.launch {
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
                val connected = (state.connectedDevices.filterNot { it.id == next.id || it.type == next.type } + next)
                state.copy(
                    device = next,
                    connectedDevices = connected,
                    selectedDeviceId = next.id,
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

    fun refreshScannedDevices() = viewModelScope.launch {
        coordinator.scanDevices().collect { devices ->
            _uiState.update { it.copy(scannedDevices = devices) }
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

    fun openMediaPreview(fileId: String, local: Boolean = _uiState.value.selectedMediaLocal) = _uiState.update { state ->
        state.copy(previewMediaFile = state.mediaFiles.firstOrNull { it.id == fileId && it.local == local }, selectedMediaFileId = fileId)
    }

    fun closeMediaPreview() = _uiState.update { it.copy(previewMediaFile = null) }

    fun clearMessage() = _uiState.update { it.copy(operationMessage = null) }

    fun showOperationMessage(message: String, type: OperationMessageType) = _uiState.update {
        it.copy(operationMessage = operationMessage(message, type))
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
}
