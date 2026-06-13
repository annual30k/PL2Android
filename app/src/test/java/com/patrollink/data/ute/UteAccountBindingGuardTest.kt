package com.patrollink.data.ute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UteAccountBindingGuardTest {
    @Test
    fun wifiRequiresAcceptedAccountBinding() {
        val failure = runCatching {
            UteAccountBindingGuard.requireAcceptedForWifi(accepted = false)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("设备账号不一致，请先在原应用解绑或重置设备后重新配对 PatrolLink", failure?.message)
    }

    @Test
    fun wifiContinuesWhenAccountBindingIsAccepted() {
        UteAccountBindingGuard.requireAcceptedForWifi(accepted = true)
    }
}
