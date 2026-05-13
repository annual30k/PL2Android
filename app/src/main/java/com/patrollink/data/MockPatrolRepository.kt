package com.patrollink.data

import com.patrollink.domain.AlertItem
import com.patrollink.domain.AlertLevel
import com.patrollink.domain.AlertStatus
import com.patrollink.domain.AppUiState
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.DeviceType
import com.patrollink.domain.GpsLocation
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaKind
import com.patrollink.domain.TransferStatus
import com.patrollink.domain.UserProfile
import com.patrollink.data.remote.MockRestApi
import com.patrollink.data.remote.toDomain

class MockPatrolRepository {
    private val api = MockRestApi()

    fun initialState(): AppUiState {
        val primary = api.bindDevice("HEADSET_001").data.toDomain().copy(type = DeviceType.Headset)
        return AppUiState(
            device = primary,
            connectedDevices = listOf(primary),
            scannedDevices = api.scanDevices().data.map { it.toDomain() },
            selectedDeviceId = primary.id,
            sosLocation = GpsLocation(
                latitude = 39.9087,
                longitude = 116.3975,
                accuracyMeters = 8.5f,
                address = "核心商务区 CBD-North"
            ),
            alerts = api.alerts().data.items.map { it.toDomain() },
            mediaFiles = api.mediaFiles(local = false).data.items.map { it.toDomain() } +
                api.mediaFiles(local = true).data.items.map { it.toDomain() },
            user = api.currentUser().data.toDomain()
        )
    }
}
