package com.patrollink.debug

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmokeDangerousActionGuardTest {
    @Test
    fun deniesClearAccountWithoutExactConfirmation() {
        assertFalse(SmokeDangerousActionGuard.canClearDeviceAccount(enabled = true, confirmation = ""))
        assertFalse(SmokeDangerousActionGuard.canClearDeviceAccount(enabled = true, confirmation = "clear"))
        assertFalse(SmokeDangerousActionGuard.canClearDeviceAccount(enabled = false, confirmation = "CLEAR_DEVICE_ACCOUNT"))
    }

    @Test
    fun allowsClearAccountWithExactConfirmation() {
        assertTrue(
            SmokeDangerousActionGuard.canClearDeviceAccount(
                enabled = true,
                confirmation = "CLEAR_DEVICE_ACCOUNT"
            )
        )
    }

    @Test
    fun stopsAfterAnyClearAccountAttempt() {
        assertFalse(SmokeDangerousActionGuard.shouldStopAfterClearAccountAttempt(enabled = false))
        assertTrue(SmokeDangerousActionGuard.shouldStopAfterClearAccountAttempt(enabled = true))
    }

    @Test
    fun deniesFactoryResetWithoutExactConfirmationAndKnownTarget() {
        assertFalse(SmokeDangerousActionGuard.canFactoryResetDevice(target = "", confirmation = "FACTORY_RESET_DEVICE"))
        assertFalse(SmokeDangerousActionGuard.canFactoryResetDevice(target = "headset", confirmation = "reset"))
        assertFalse(SmokeDangerousActionGuard.canFactoryResetDevice(target = "unknown", confirmation = "FACTORY_RESET_DEVICE"))
    }

    @Test
    fun allowsFactoryResetWithExactConfirmationAndKnownTarget() {
        assertTrue(SmokeDangerousActionGuard.canFactoryResetDevice(target = "headset", confirmation = "FACTORY_RESET_DEVICE"))
        assertTrue(SmokeDangerousActionGuard.canFactoryResetDevice(target = "glasses", confirmation = "FACTORY_RESET_DEVICE"))
        assertTrue(SmokeDangerousActionGuard.canFactoryResetDevice(target = "HEADSET", confirmation = "FACTORY_RESET_DEVICE"))
    }

    @Test
    fun stopsAfterAnyFactoryResetAttempt() {
        assertFalse(SmokeDangerousActionGuard.shouldStopAfterFactoryResetAttempt(target = ""))
        assertTrue(SmokeDangerousActionGuard.shouldStopAfterFactoryResetAttempt(target = "headset"))
    }
}
