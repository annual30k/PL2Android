package com.patrollink.data.sos

import com.patrollink.domain.GpsLocation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockEmergencyContactGatewayTest {
    @Test
    fun contactsAndNotifyAreDeterministicForLocalTesting() = runTest {
        val gateway = MockEmergencyContactGateway()

        val contacts = gateway.contacts()

        assertTrue(contacts.isNotEmpty())
        assertTrue(gateway.notifyContacts("SOS-1", GpsLocation(1.0, 2.0, 5f, "test")))
        assertFalse(gateway.notifyContacts("", GpsLocation(1.0, 2.0, 5f, "test")))
    }
}
