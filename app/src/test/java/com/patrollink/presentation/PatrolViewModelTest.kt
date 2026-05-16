package com.patrollink.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import com.patrollink.data.MockPatrolCoordinatorFactory
import com.patrollink.data.MockVersionGateway
import com.patrollink.data.edge.CerebellumApi
import com.patrollink.data.edge.CerebellumAsrTranscribeRequestDto
import com.patrollink.data.edge.CerebellumAsrTranscribeResponseDto
import com.patrollink.data.edge.CerebellumCertificateStatusDto
import com.patrollink.data.edge.CerebellumDeviceStatusDto
import com.patrollink.data.edge.CerebellumEvidenceListResponseDto
import com.patrollink.data.edge.CerebellumEvidenceRegisterRequestDto
import com.patrollink.data.edge.CerebellumEvidenceRegisterResponseDto
import com.patrollink.data.edge.CerebellumHealthDto
import com.patrollink.data.edge.CerebellumObjectAnalyzeRequestDto
import com.patrollink.data.edge.CerebellumObjectAnalyzeResponseDto
import com.patrollink.data.edge.CerebellumReportDto
import com.patrollink.data.edge.CerebellumReportRequestDto
import com.patrollink.data.edge.CerebellumReportResponseDto
import com.patrollink.data.edge.CerebellumSyncTaskListResponseDto
import com.patrollink.data.edge.CerebellumSyncTaskRequestDto
import com.patrollink.data.edge.CerebellumSyncTaskResponseDto
import com.patrollink.data.edge.CerebellumEventDto
import com.patrollink.data.edge.CerebellumVideoSummaryRequestDto
import com.patrollink.data.edge.CerebellumVideoSummaryResponseDto
import com.patrollink.domain.DeviceType
import com.patrollink.domain.TransferTarget
import com.patrollink.domain.TransferStatus
import com.patrollink.domain.VersionUpdatePhase
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PatrolViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loginMovesUiToAuthenticatedState() = runTest {
        val viewModel = testViewModel()

        viewModel.login("POLICE_9527", "123456", agreed = true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLoggedIn)
        assertFalse(viewModel.uiState.value.loginLoading)
    }

    @Test
    fun deviceControlsUpdateRecordingAndTalkingState() = runTest {
        val viewModel = testViewModel()
        loginAndConnect(viewModel)

        viewModel.toggleRecord()
        viewModel.toggleTalk()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.device.isRecording)
        assertTrue(viewModel.uiState.value.device.isTalking)
    }

    @Test
    fun closingAlertMovesItToClosedList() = runTest {
        val viewModel = testViewModel()
        loginForTest(viewModel)
        val alertId = viewModel.uiState.value.alerts.first().id

        viewModel.closeAlert(alertId)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.alerts.first { it.id == alertId }.status.name == "Closed")
    }

    @Test
    fun mediaDownloadAndDeleteMutateUiCollection() = runTest {
        val viewModel = testViewModel()
        loginForTest(viewModel)
        val fileId = viewModel.uiState.value.mediaFiles.first { !it.local }.id

        viewModel.downloadMedia(fileId)
        advanceUntilIdle()
        val deviceFile = viewModel.uiState.value.mediaFiles.first { it.id == fileId && !it.local }
        val phoneFile = viewModel.uiState.value.mediaFiles.first { it.id == fileId && it.local }
        assertFalse(deviceFile.local)
        assertEquals(TransferTarget.PhoneSandbox, deviceFile.lastTransferTarget)
        assertEquals(TransferStatus.Done, deviceFile.transferStatus)
        assertTrue(phoneFile.local)
        assertEquals(TransferStatus.Idle, phoneFile.transferStatus)

        viewModel.deleteMedia(fileId, local = false)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.mediaFiles.none { it.id == fileId && !it.local })
        assertTrue(viewModel.uiState.value.mediaFiles.any { it.id == fileId && it.local })
    }

    @Test
    fun sosActivateAndCancelReflectInUiState() = runTest {
        val viewModel = testViewModel()

        viewModel.activateSos()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.sosActive)

        viewModel.cancelSos()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.sosActive)
    }

    @Test
    fun mockVersionUpdateCompletesWithoutRealInstallerDownload() = runTest {
        val viewModel = testViewModel()

        viewModel.checkVersionUpdate()
        advanceUntilIdle()
        viewModel.installVersionUpdate()
        advanceUntilIdle()

        assertEquals(VersionUpdatePhase.Ready, viewModel.uiState.value.versionUpdate.phase)
        assertEquals("1.3.0", viewModel.uiState.value.versionUpdate.currentVersionName)
    }

    @Test
    fun generateDailyReportCallsCerebellumAndStoresReport() = runTest {
        val api = FakeCerebellumReportApi()
        val viewModel = testViewModel(cerebellumApi = api)
        loginForTest(viewModel)

        viewModel.updateDailyReportMissionId("mission-test")
        viewModel.updateDailyReportOperatorNote("重点巡逻")
        viewModel.generateDailyReport()
        advanceUntilIdle()

        val report = viewModel.uiState.value.dailyReport.report
        assertEquals("mission-test", api.lastRequest?.missionId)
        assertEquals("daily", api.lastRequest?.reportType)
        assertTrue(api.lastRequest?.operatorNote?.contains("重点巡逻") == true)
        assertTrue(api.lastRequest?.preferQuality == true)
        assertEquals(1200, api.lastRequest?.maxTokens)
        assertEquals("日报正文", report?.content)
        assertEquals("llama.cpp", report?.backend)
        assertFalse(viewModel.uiState.value.dailyReport.generating)
    }

    @Test
    fun generateDailyReportFallsBackToDateAndBadgeMissionIdWhenBlank() = runTest {
        val api = FakeCerebellumReportApi()
        val viewModel = testViewModel(cerebellumApi = api)
        loginForTest(viewModel)

        viewModel.generateDailyReport()
        advanceUntilIdle()

        val missionId = api.lastRequest?.missionId.orEmpty()
        assertTrue(missionId.startsWith("mission-"))
        assertTrue(missionId.contains("POLICE_9527"))
        assertEquals(missionId, viewModel.uiState.value.dailyReport.report?.missionId)
    }
}

private fun testViewModel(cerebellumApi: CerebellumApi? = null) = PatrolViewModel(
    coordinator = MockPatrolCoordinatorFactory.create(),
    versionGateway = MockVersionGateway(),
    cerebellumApi = cerebellumApi
)

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun TestScope.loginForTest(viewModel: PatrolViewModel) {
    viewModel.login("POLICE_9527", "123456", agreed = true)
    advanceUntilIdle()
}

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun TestScope.loginAndConnect(viewModel: PatrolViewModel) {
    loginForTest(viewModel)
    viewModel.connectDiscoveredDevice(
        id = "HEADSET_001",
        name = "ForceLink-H1",
        mac = "2C:4A:91:3F:8B:02",
        signalBars = 4,
        type = DeviceType.Headset
    )
    advanceUntilIdle()
}

private class FakeCerebellumReportApi : CerebellumApi {
    var lastRequest: CerebellumReportRequestDto? = null

    override suspend fun createReport(request: CerebellumReportRequestDto): CerebellumReportResponseDto {
        lastRequest = request
        return CerebellumReportResponseDto(
            report = CerebellumReportDto(
                missionId = request.missionId,
                reportType = request.reportType,
                model = "Qwen3.5-4B-Q4_K_M",
                contextTokens = 100,
                maxContextTokens = 1000,
                generatedAt = "2026-05-15T08:00:00Z",
                content = "日报正文",
                requiresHumanConfirmation = true,
                backend = "llama.cpp"
            ),
            event = CerebellumEventDto(
                eventId = "evt-report-1",
                eventType = "report_generated",
                createdAt = "2026-05-15T08:00:00Z",
                payload = JsonParser.parseString("{}"),
                humanStatus = "unconfirmed"
            )
        )
    }

    override suspend fun health(): CerebellumHealthDto = TODO()
    override suspend fun deviceStatus(): CerebellumDeviceStatusDto = TODO()
    override suspend fun certificateStatus(): CerebellumCertificateStatusDto = TODO()
    override suspend fun analyzeObject(request: CerebellumObjectAnalyzeRequestDto): CerebellumObjectAnalyzeResponseDto = TODO()
    override suspend fun transcribeAudio(request: CerebellumAsrTranscribeRequestDto): CerebellumAsrTranscribeResponseDto = TODO()
    override suspend fun registerEvidence(request: CerebellumEvidenceRegisterRequestDto): CerebellumEvidenceRegisterResponseDto = TODO()
    override suspend fun listEvidence(): CerebellumEvidenceListResponseDto = TODO()
    override suspend fun createSyncTask(request: CerebellumSyncTaskRequestDto): CerebellumSyncTaskResponseDto = TODO()
    override suspend fun listSyncTasks(): CerebellumSyncTaskListResponseDto = TODO()
    override suspend fun runSyncTask(taskId: String): CerebellumSyncTaskResponseDto = TODO()
    override suspend fun summarizeVideo(request: CerebellumVideoSummaryRequestDto): CerebellumVideoSummaryResponseDto = TODO()
}
