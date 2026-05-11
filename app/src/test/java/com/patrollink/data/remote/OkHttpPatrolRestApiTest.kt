package com.patrollink.data.remote

import org.junit.Assert.assertNotNull
import org.junit.Test

class OkHttpPatrolRestApiTest {
    @Test
    fun restClientCanBeConstructedWithBaseUrlAndTokenProvider() {
        val api = OkHttpPatrolRestApi(
            baseUrl = "https://backend.example.test",
            tokenProvider = { "token" }
        )

        assertNotNull(api)
    }
}
