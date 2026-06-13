package com.patrollink.debug

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokePairingAccountResolverTest {
    @Test
    fun overrideWinsOverCurrentUserAndLoginAccount() {
        val account = SmokePairingAccountResolver.resolve(
            override = " e19d340fb9a9b09babd2c9a2c33ae203od ",
            currentUserBadge = "POLICE_9527",
            loginAccount = "POLICE_9527"
        )

        assertEquals("e19d340fb9a9b09babd2c9a2c33ae203od", account)
    }

    @Test
    fun currentUserBadgeWinsWhenOverrideIsBlank() {
        val account = SmokePairingAccountResolver.resolve(
            override = "",
            currentUserBadge = "POLICE_9527",
            loginAccount = "fallback"
        )

        assertEquals("POLICE_9527", account)
    }
}
