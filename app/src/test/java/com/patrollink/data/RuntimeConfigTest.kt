package com.patrollink.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeConfigTest {
    @Test
    fun backendSettingsTrimTrailingSlashAndDeriveWebSocketUrl() {
        val settings = RuntimeConfigStore.normalizeBackendSettings(
            restBaseUrl = " http://192.168.1.6:8080/ ",
            webSocketUrl = ""
        )

        assertEquals("http://192.168.1.6:8080", settings.restBaseUrl)
        assertEquals("ws://192.168.1.6:8080/resource/websocket", settings.webSocketUrl)
    }

    @Test
    fun backendSettingsPreserveExplicitWebSocketUrl() {
        val settings = RuntimeConfigStore.normalizeBackendSettings(
            restBaseUrl = "https://api.example.test/",
            webSocketUrl = " wss://ws.example.test/resource/websocket/ "
        )

        assertEquals("https://api.example.test", settings.restBaseUrl)
        assertEquals("wss://ws.example.test/resource/websocket", settings.webSocketUrl)
    }

    @Test
    fun backendSettingsDeriveWebSocketUrlForCurrentLanDevelopmentHost() {
        val settings = RuntimeConfigStore.normalizeBackendSettings(
            restBaseUrl = "http://192.168.11.157:8080",
            webSocketUrl = ""
        )

        assertEquals("http://192.168.11.157:8080", settings.restBaseUrl)
        assertEquals("ws://192.168.11.157:8080/resource/websocket", settings.webSocketUrl)
    }
}
