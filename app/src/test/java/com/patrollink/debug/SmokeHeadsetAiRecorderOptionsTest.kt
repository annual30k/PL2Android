package com.patrollink.debug

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmokeHeadsetAiRecorderOptionsTest {
    @Test
    fun skipsAiRecorderWhenUnsupportedAndNotForced() {
        val options = SmokeHeadsetAiRecorderOptions(forceCommands = false)

        assertFalse(options.shouldRun(supported = false))
    }

    @Test
    fun runsAiRecorderWhenSupportedOrForced() {
        assertTrue(SmokeHeadsetAiRecorderOptions(forceCommands = false).shouldRun(supported = true))
        assertTrue(SmokeHeadsetAiRecorderOptions(forceCommands = true).shouldRun(supported = false))
    }
}
