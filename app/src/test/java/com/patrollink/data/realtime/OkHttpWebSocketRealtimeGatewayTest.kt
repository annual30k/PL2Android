package com.patrollink.data.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OkHttpWebSocketRealtimeGatewayTest {
    @Test
    fun parsesPatrolBusinessEvent() {
        val event = parsePatrolRealtimeEvent(
            """{"namespace":"PATROL","type":"APP_VERSION_PUBLISHED","module":"versions","title":"新版本","summary":"1.2.0","resourceId":"VER-3","payload":{"versionCode":3},"occurredAt":"2026-08-11T10:00:00Z"}"""
        )

        assertEquals("APP_VERSION_PUBLISHED", event?.type)
        assertEquals(3.0, event?.payload?.get("versionCode"))
    }

    @Test
    fun ignoresNonPatrolAndMalformedMessages() {
        assertNull(parsePatrolRealtimeEvent("{\"type\":\"heartbeat\"}"))
        assertNull(parsePatrolRealtimeEvent("not-json"))
    }
}
