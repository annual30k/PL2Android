package com.patrollink.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockVersionGatewayTest {
    @Test
    fun versionCheckReturnsOptionalUpdateMetadata() = runTest {
        val result = MockVersionGateway().check(currentVersionCode = 1)

        assertTrue(result.hasUpdate(currentVersionCode = 1))
        assertFalse(result.forceUpdate)
        assertTrue(result.changelog.isNotEmpty())
        assertTrue(result.downloadUrl?.endsWith(".apk") == true)
    }
}
