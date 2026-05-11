package com.patrollink.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patrollink.data.MockPatrolRepository
import com.patrollink.data.ServiceFactory
import com.patrollink.domain.AlertResult
import com.patrollink.domain.AlertStatus
import com.patrollink.domain.GpsLocation
import com.patrollink.domain.PatrolCoordinator
import com.patrollink.domain.StreamMode
import com.patrollink.domain.TransferTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PatrolViewModel(
    private val coordinator: PatrolCoordinator = ServiceFactory.createCoordinator()
) : ViewModel() {
    private val repository = MockPatrolRepository()
    private val _uiState = MutableStateFlow(repository.initialState())
    val uiState: StateFlow<com.patrollink.domain.AppUiState> = _uiState.asStateFlow()

    fun login(account: String, password: String, agreed: Boolean) {
        if (account.isBlank() || password.isBlank() || !agreed) return
        viewModelScope.launch {
            _uiState.update { it.copy(loginLoading = true) }
            runCatching { coordinator.loginAndStartSession(account, password) }
                .onSuccess { _uiState.update { it.copy(isLoggedIn = true, loginLoading = false, networkOnline = true) } }
                .onFailure { _uiState.update { it.copy(loginLoading = false, networkOnline = false) } }
        }
    }

    fun logout() = _uiState.update { it.copy(isLoggedIn = false) }

    fun toggleRecord() = viewModelScope.launch {
        val device = _uiState.value.device
        val next = coordinator.setRecording(device, !device.isRecording)
        _uiState.update { it.copy(device = next) }
    }

    fun toggleTalk() = viewModelScope.launch {
        val device = _uiState.value.device
        val next = coordinator.setTalk(device, !device.isTalking)
        _uiState.update { it.copy(device = next) }
    }

    fun setAlertTab(status: AlertStatus) = _uiState.update { it.copy(selectedAlertTab = status) }

    fun setMediaLocal(local: Boolean) = _uiState.update { it.copy(selectedMediaLocal = local) }

    fun closeAlert(alertId: String) = viewModelScope.launch {
        coordinator.handleAlert(alertId, AlertResult.Resolved)
        _uiState.update {
            it.copy(alerts = it.alerts.map { alert ->
                if (alert.id == alertId) alert.copy(status = AlertStatus.Closed) else alert
            })
        }
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
        coordinator.takePhoto(_uiState.value.device)
    }

    fun startLowLatencyStream() = viewModelScope.launch {
        coordinator.startStream(_uiState.value.device, StreamMode.LowLatency)
    }

    fun stopStream() = viewModelScope.launch {
        coordinator.stopStream()
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
            _uiState.update { state -> state.copy(mediaFiles = state.mediaFiles.filterNot { it.id == fileId }) }
        }
    }
}
