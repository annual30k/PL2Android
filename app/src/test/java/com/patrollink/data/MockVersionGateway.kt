package com.patrollink.data

import com.patrollink.domain.VersionCheckResult
import com.patrollink.domain.VersionGateway

class MockVersionGateway : VersionGateway {
    override suspend fun check(currentVersionCode: Int): VersionCheckResult =
        VersionCheckResult(
            latestVersionCode = 2,
            latestVersionName = "1.3.0",
            forceUpdate = false,
            changelog = listOf("新增预警处置离线补偿", "优化 BLE 连接诊断", "修复媒体上传进度显示"),
            downloadUrl = "https://backend.example.test/app/patrollink-1.3.0.apk"
        )
}
