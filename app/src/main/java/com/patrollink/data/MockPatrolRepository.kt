package com.patrollink.data

import com.patrollink.domain.AlertItem
import com.patrollink.domain.AlertLevel
import com.patrollink.domain.AlertStatus
import com.patrollink.domain.AppUiState
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaKind
import com.patrollink.domain.TransferStatus
import com.patrollink.domain.UserProfile
import com.patrollink.data.remote.MockRestApi
import com.patrollink.data.remote.toDomain

class MockPatrolRepository {
    private val api = MockRestApi()

    fun initialState() = AppUiState(
        device = api.bindDevice("HEADSET_001").data.toDomain(),
        alerts = api.alerts().data.items.map { it.toDomain() },
        mediaFiles = api.mediaFiles(local = false).data.items.map { it.toDomain() } +
            api.mediaFiles(local = true).data.items.map { it.toDomain() },
        user = api.currentUser().data.toDomain()
    )
}
