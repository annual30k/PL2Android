package com.patrollink.data.sos

import com.patrollink.domain.EmergencyContact
import com.patrollink.domain.EmergencyContactGateway
import com.patrollink.domain.GpsLocation

class MockEmergencyContactGateway : EmergencyContactGateway {
    override suspend fun contacts(): List<EmergencyContact> = listOf(
        EmergencyContact("cmd-01", "指挥中心", "调度", "010-1100-0001"),
        EmergencyContact("backup-02", "附近巡组", "增援", "010-1100-0002")
    )

    override suspend fun notifyContacts(sosId: String, location: GpsLocation): Boolean =
        sosId.isNotBlank() && location.accuracyMeters >= 0f
}
