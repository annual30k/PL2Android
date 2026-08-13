package com.patrollink.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendDownloadUrlTest {
    @Test
    fun relativePackageUrlResolvesAgainstBackendOrigin() {
        assertEquals(
            "http://192.168.10.8:18080/files/FILE-2/download",
            resolveBackendDownloadUrl("http://192.168.10.8:18080/api", "/files/FILE-2/download")
        )
    }

    @Test
    fun authorizationIsOnlyAttachedToBackendOrigin() {
        val baseUrl = "https://patrol.example.com/api"

        assertTrue(shouldAttachBackendAuthorization(baseUrl, "https://patrol.example.com/files/FILE-2/download"))
        assertFalse(shouldAttachBackendAuthorization(baseUrl, "https://cdn.example.com/files/FILE-2/download"))
        assertFalse(shouldAttachBackendAuthorization(baseUrl, "http://patrol.example.com/files/FILE-2/download"))
    }
}
