package com.patrollink.data

import com.patrollink.data.remote.MockRestApi
import com.patrollink.data.remote.toDomain
import com.patrollink.domain.AppUiState
import com.patrollink.domain.DeviceType
import com.patrollink.domain.GpsLocation

class MockPatrolRepository {
    private val api = MockRestApi()

    fun initialState(): AppUiState {
        val primary = api.bindDevice("HEADSET_001").data.toDomain().copy(type = DeviceType.Headset)
        return AppUiState(
            isLoggedIn = true,
            sessionRestoring = false,
            device = primary,
            connectedDevices = listOf(primary),
            scannedDevices = api.scanDevices().data.map { it.toDomain() },
            selectedDeviceId = primary.id,
            sosLocation = GpsLocation(
                latitude = 26.10058,
                longitude = 119.30771,
                accuracyMeters = 5f,
                address = "福州温泉公园"
            ),
            patrolArea = api.currentPatrolArea().data.toDomain(),
            alerts = api.alerts().data.items.map { it.toDomain() },
            mediaFiles = api.mediaFiles(local = false).data.items.map { it.toDomain() } +
                api.mediaFiles(local = true).data.items.map { it.toDomain() },
            user = api.currentUser().data.toDomain()
        )
    }
}
