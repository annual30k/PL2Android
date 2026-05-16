package com.patrollink.data.sos

import com.patrollink.domain.EmergencyContact
import com.patrollink.domain.EmergencyContactGateway
import com.patrollink.domain.GpsLocation

class MockEmergencyContactGateway : EmergencyContactGateway {
    override suspend fun contacts(): List<EmergencyContact> = listOf(
        EmergencyContact("contact-1", "值班指挥", "指挥中心", "110"),
        EmergencyContact("contact-2", "巡逻组长", "现场负责人", "13800000000")
    )

    override suspend fun notifyContacts(sosId: String, location: GpsLocation): Boolean =
        sosId.isNotBlank() && location.address.isNotBlank()
}
