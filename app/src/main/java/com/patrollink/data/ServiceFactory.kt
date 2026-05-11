package com.patrollink.data

import com.patrollink.data.remote.OkHttpPatrolRestApi
import com.patrollink.domain.PatrolCoordinator

object ServiceFactory {
    fun createCoordinator(): PatrolCoordinator = PatrolCoordinator(
        authGateway = MockAuthGateway(),
        deviceGateway = MockDeviceGateway(),
        alertGateway = MockAlertGateway(),
        mediaGateway = MockMediaGateway(),
        realtimeGateway = MockRealtimeGateway(),
        streamRelayGateway = MockStreamRelayGateway(),
        sosGateway = MockSosGateway()
    )

    fun createRestCoordinator(baseUrl: String, tokenProvider: () -> String?): PatrolCoordinator {
        val api = OkHttpPatrolRestApi(baseUrl = baseUrl, tokenProvider = tokenProvider)
        return PatrolCoordinator(
            authGateway = RestAuthGateway(api),
            deviceGateway = RestDeviceGateway(api),
            alertGateway = RestAlertGateway(api),
            mediaGateway = RestMediaGateway(api),
            realtimeGateway = RestRealtimeGateway(api),
            streamRelayGateway = RestStreamRelayGateway(api),
            sosGateway = RestSosGateway(api)
        )
    }
}
