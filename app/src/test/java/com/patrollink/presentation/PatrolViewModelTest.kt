package com.patrollink.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import com.patrollink.data.MockPatrolCoordinatorFactory
import com.patrollink.data.MockVersionGateway
import com.patrollink.data.InMemoryBackgroundTaskGateway
import com.patrollink.data.CerebellumRuntimeSettings
import com.patrollink.data.RuntimeConfigGateway
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
import com.patrollink.domain.BackgroundTaskType
import com.patrollink.domain.DeviceAdvancedSettings
import com.patrollink.domain.DeviceCapabilities
import com.patrollink.domain.DeviceCommand
import com.patrollink.domain.DeviceControlGateway
import com.patrollink.domain.DeviceEvent
import com.patrollink.domain.DeviceFactoryResetTarget
import com.patrollink.domain.DeviceGateway
import com.patrollink.domain.DeviceStatus
import com.patrollink.domain.DeviceWifiState
import com.patrollink.domain.MediaFile
import com.patrollink.domain.MediaGateway
import com.patrollink.domain.OperationMessageType
import com.patrollink.domain.OfflineSyncEngine
import com.patrollink.domain.PatrolCoordinator
import com.patrollink.domain.ScannedDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PatrolViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val temp = TemporaryFolder()

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
    fun unsupportedGlassAudioDoesNotSendRecordCommand() = runTest {
        val gateway = RecordingGlassDeviceGateway()
        val viewModel = testViewModel(
            coordinator = coordinatorWithDeviceGateway(gateway),
            deviceControlGateway = FixedCapabilitiesControlGateway(
                DeviceCapabilities(
                    supportsGlasses = true,
                    supportsWifi = true,
                    supportsFileTransfer = true,
                    supportsPhoto = true,
                    supportsVideo = true,
                    supportsAudioRecord = false
                )
            )
        )
        loginForTest(viewModel)
        viewModel.connectDiscoveredDevice(
            id = "78:02:B7:66:00:F7",
            name = "Glory Glass 2-00F7",
            mac = "78:02:B7:66:00:F7",
            signalBars = 5,
            type = DeviceType.Glasses
        )
        advanceUntilIdle()

        viewModel.toggleTalk()
        advanceUntilIdle()

        assertTrue(gateway.commands.isEmpty())
        assertEquals("录音失败，当前设备不支持录音或控制通道未就绪", viewModel.uiState.value.operationMessage?.text)
        assertEquals(OperationMessageType.Error, viewModel.uiState.value.operationMessage?.type)
    }

    @Test
    fun photoCommandWithoutImmediateFilePromptsWifiMediaSync() = runTest {
        val viewModel = testViewModel()
        loginAndConnect(viewModel)

        viewModel.takePhoto()
        advanceUntilIdle()

        assertEquals("拍照命令已下发；若媒体列表仍为空，请在设备文件页通过 Wi-Fi 同步", viewModel.uiState.value.operationMessage?.text)
        assertEquals(OperationMessageType.Info, viewModel.uiState.value.operationMessage?.type)
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
        viewModel.setMediaLocal(false)
        viewModel.refreshMediaFiles()
        advanceUntilIdle()
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
    fun completedDeviceMediaDownloadEnqueuesBackgroundUpload() = runTest {
        val localFile = temp.newFile("downloaded-device-video.mp4").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val remote = MediaFile(
            id = "ute-wifi-video-1",
            name = "downloaded-device-video.mp4",
            kind = com.patrollink.domain.MediaKind.Video,
            time = "123",
            size = "4 B",
            duration = null,
            verified = false,
            local = false,
            transferStatus = TransferStatus.Idle,
            progress = 0f
        )
        val taskGateway = InMemoryBackgroundTaskGateway()
        val viewModel = testViewModel(
            coordinator = coordinatorWithMedia(DownloadingMediaGateway(remote, localFile)),
            offlineSyncEngine = OfflineSyncEngine(taskGateway)
        )
        loginForTest(viewModel)

        viewModel.downloadMedia(remote.id)
        advanceUntilIdle()

        val queued = taskGateway.pending().single().task
        assertEquals(BackgroundTaskType.UploadEvidence, queued.type)
        assertEquals(remote.id, queued.payloadId)
    }

    @Test
    fun uploadingDeviceMediaFirstDownloadsToPhoneSandboxAndQueuesBackgroundUpload() = runTest {
        val localFile = temp.newFile("uploaded-device-video.mp4").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val remote = MediaFile(
            id = "ute-wifi-video-upload-1",
            name = "uploaded-device-video.mp4",
            kind = com.patrollink.domain.MediaKind.Video,
            time = "123",
            size = "4 B",
            duration = null,
            verified = false,
            local = false,
            transferStatus = TransferStatus.Idle,
            progress = 0f
        )
        val mediaGateway = DownloadingMediaGateway(remote, localFile)
        val taskGateway = InMemoryBackgroundTaskGateway()
        val viewModel = testViewModel(
            coordinator = coordinatorWithMedia(mediaGateway),
            offlineSyncEngine = OfflineSyncEngine(taskGateway)
        )
        loginForTest(viewModel)

        viewModel.uploadMedia(remote.id, local = false)
        advanceUntilIdle()

        assertEquals(listOf(TransferTarget.PhoneSandbox), mediaGateway.transferTargets)
        val queued = taskGateway.pending().single().task
        assertEquals(BackgroundTaskType.UploadEvidence, queued.type)
        assertEquals(remote.id, queued.payloadId)
    }

    @Test
    fun syncDeviceMediaToPhoneDownloadsMissingDeviceFilesAndQueuesBackgroundUploads() = runTest {
        val devicePhoto = MediaFile(
            id = "ute-wifi-photo-1",
            name = "IMG_0001.jpg",
            kind = com.patrollink.domain.MediaKind.Photo,
            time = "123",
            size = "4 B",
            duration = null,
            verified = false,
            local = false,
            transferStatus = TransferStatus.Idle,
            progress = 0f
        )
        val deviceVideo = devicePhoto.copy(
            id = "ute-wifi-video-2",
            name = "GX010002.MP4",
            kind = com.patrollink.domain.MediaKind.Video
        )
        val alreadyLocal = deviceVideo.copy(local = true, contentUri = temp.newFile("GX010002.MP4").toURI().toString())
        val gateway = BatchDownloadingMediaGateway(
            remote = listOf(devicePhoto, deviceVideo),
            local = listOf(alreadyLocal),
            downloadedFiles = mapOf(
                devicePhoto.id to temp.newFile("IMG_0001.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) },
                deviceVideo.id to temp.newFile("GX010002-download.MP4").apply { writeBytes(byteArrayOf(5, 6, 7, 8)) }
            )
        )
        val taskGateway = InMemoryBackgroundTaskGateway()
        val viewModel = testViewModel(
            coordinator = coordinatorWithMedia(gateway),
            offlineSyncEngine = OfflineSyncEngine(taskGateway)
        )
        loginForTest(viewModel)
        viewModel.setMediaLocal(false)
        viewModel.refreshMediaFiles()
        advanceUntilIdle()

        viewModel.syncDeviceMediaToPhone()
        advanceUntilIdle()

        assertEquals(listOf(devicePhoto.id), gateway.downloadedIds)
        assertTrue(viewModel.uiState.value.mediaFiles.any { it.id == devicePhoto.id && it.local && it.contentUri != null })
        assertEquals(listOf(devicePhoto.id), taskGateway.pending().map { it.task.payloadId })
        assertEquals("已同步 1 个设备文件到手机本地媒体文件，设备热点保持连接", viewModel.uiState.value.operationMessage?.text)
    }

    @Test
    fun syncDeviceMediaToPhoneRefreshesDeviceFilesWhenListIsEmpty() = runTest {
        val devicePhoto = MediaFile(
            id = "ute-wifi-photo-refresh-1",
            name = "IMG_REFRESH.jpg",
            kind = com.patrollink.domain.MediaKind.Photo,
            time = "123",
            size = "4 B",
            duration = null,
            verified = false,
            local = false,
            transferStatus = TransferStatus.Idle,
            progress = 0f
        )
        val gateway = BatchDownloadingMediaGateway(
            remote = emptyList(),
            local = emptyList(),
            downloadedFiles = mapOf(
                devicePhoto.id to temp.newFile("IMG_REFRESH.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            )
        )
        val taskGateway = InMemoryBackgroundTaskGateway()
        val viewModel = testViewModel(
            coordinator = coordinatorWithMedia(gateway),
            offlineSyncEngine = OfflineSyncEngine(taskGateway)
        )
        loginForTest(viewModel)
        assertTrue(viewModel.uiState.value.mediaFiles.none { it.id == devicePhoto.id })

        gateway.remote = listOf(devicePhoto)
        viewModel.syncDeviceMediaToPhone(refreshFirst = true)
        advanceUntilIdle()

        assertEquals(listOf(devicePhoto.id), gateway.downloadedIds)
        assertTrue(viewModel.uiState.value.mediaFiles.any { it.id == devicePhoto.id && it.local && it.contentUri != null })
        assertEquals(listOf(devicePhoto.id), taskGateway.pending().map { it.task.payloadId })
    }

    @Test
    fun syncDeviceMediaToPhoneKeepsSelectedFileWhenRefreshChangesDeviceFileId() = runTest {
        val staleDevicePhoto = MediaFile(
            id = "ute-wifi-stale-url-id",
            name = "眼镜照片_20260614011639379.jpg",
            kind = com.patrollink.domain.MediaKind.Photo,
            time = "123",
            size = "3 MB",
            duration = null,
            verified = false,
            local = false,
            transferStatus = TransferStatus.Idle,
            progress = 0f
        )
        val refreshedDevicePhoto = staleDevicePhoto.copy(id = "ute-wifi-refreshed-url-id")
        val gateway = BatchDownloadingMediaGateway(
            remote = listOf(staleDevicePhoto),
            local = emptyList(),
            downloadedFiles = mapOf(
                refreshedDevicePhoto.id to temp.newFile("20260614011639379.jpg").apply {
                    writeBytes(byteArrayOf(1, 2, 3, 4))
                }
            )
        )
        val taskGateway = InMemoryBackgroundTaskGateway()
        val viewModel = testViewModel(
            coordinator = coordinatorWithMedia(gateway),
            offlineSyncEngine = OfflineSyncEngine(taskGateway)
        )
        loginForTest(viewModel)
        viewModel.setMediaLocal(false)
        viewModel.refreshMediaFiles()
        advanceUntilIdle()

        gateway.remote = listOf(refreshedDevicePhoto)
        viewModel.syncDeviceMediaToPhone(setOf(staleDevicePhoto.id), refreshFirst = true)
        advanceUntilIdle()

        assertEquals(listOf(refreshedDevicePhoto.id), gateway.downloadedIds)
        assertTrue(viewModel.uiState.value.mediaFiles.any { it.id == refreshedDevicePhoto.id && it.local && it.contentUri != null })
        assertEquals(listOf(refreshedDevicePhoto.id), taskGateway.pending().map { it.task.payloadId })
    }

    @Test
    fun syncDeviceMediaToPhoneLetsMediaGatewayOwnWifiWhileRefreshingDeviceFiles() = runTest {
        val events = mutableListOf<String>()
        val deviceVideo = MediaFile(
            id = "ute-wifi-video-ready-1",
            name = "GX010099.MP4",
            kind = com.patrollink.domain.MediaKind.Video,
            time = "123",
            size = "4 B",
            duration = null,
            verified = false,
            local = false,
            transferStatus = TransferStatus.Idle,
            progress = 0f
        )
        val mediaGateway = OrderRecordingMediaGateway(
            events = events,
            remote = listOf(deviceVideo),
            downloadedFiles = mapOf(
                deviceVideo.id to temp.newFile("GX010099.MP4").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            )
        )
        val viewModel = testViewModel(
            coordinator = coordinatorWithMedia(mediaGateway),
            deviceControlGateway = TrackingWifiControlGateway(events)
        )
        loginForTest(viewModel)
        events.clear()

        viewModel.syncDeviceMediaToPhone(refreshFirst = true)
        advanceUntilIdle()

        assertTrue("expected media gateway to list device files, events=$events", events.contains("listDevice"))
        assertFalse("expected ViewModel not to duplicate device wifi open, events=$events", events.contains("configureWifi:true"))
        assertEquals(listOf(deviceVideo.id), mediaGateway.downloadedIds)
    }

    @Test
    fun syncDeviceMediaToPhoneLetsMediaGatewayOwnWifiWhileTransferringExistingDeviceFiles() = runTest {
        val events = mutableListOf<String>()
        val devicePhoto = MediaFile(
            id = "ute-wifi-photo-existing-1",
            name = "IMG_EXISTING.jpg",
            kind = com.patrollink.domain.MediaKind.Photo,
            time = "123",
            size = "4 B",
            duration = null,
            verified = false,
            local = false,
            transferStatus = TransferStatus.Idle,
            progress = 0f
        )
        val mediaGateway = OrderRecordingMediaGateway(
            events = events,
            remote = listOf(devicePhoto),
            downloadedFiles = mapOf(
                devicePhoto.id to temp.newFile("IMG_EXISTING.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            )
        )
        val viewModel = testViewModel(
            coordinator = coordinatorWithMedia(mediaGateway),
            deviceControlGateway = TrackingWifiControlGateway(events)
        )
        loginForTest(viewModel)
        viewModel.setMediaLocal(false)
        viewModel.refreshMediaFiles()
        advanceUntilIdle()
        events.clear()

        viewModel.syncDeviceMediaToPhone()
        advanceUntilIdle()

        assertTrue("expected transfer through media gateway, events=$events", events.contains("transfer:${devicePhoto.id}"))
        assertFalse("expected ViewModel not to duplicate device wifi open, events=$events", events.contains("configureWifi:true"))
    }

    @Test
    fun downloadMediaLetsMediaGatewayOwnWifiForDeviceWifiFile() = runTest {
        val events = mutableListOf<String>()
        val devicePhoto = MediaFile(
            id = "ute-wifi-photo-single-1",
            name = "IMG_SINGLE.jpg",
            kind = com.patrollink.domain.MediaKind.Photo,
            time = "123",
            size = "4 B",
            duration = null,
            verified = false,
            local = false,
            transferStatus = TransferStatus.Idle,
            progress = 0f
        )
        val mediaGateway = OrderRecordingMediaGateway(
            events = events,
            remote = listOf(devicePhoto),
            downloadedFiles = mapOf(
                devicePhoto.id to temp.newFile("IMG_SINGLE.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            )
        )
        val viewModel = testViewModel(
            coordinator = coordinatorWithMedia(mediaGateway),
            deviceControlGateway = TrackingWifiControlGateway(events)
        )
        loginForTest(viewModel)
        events.clear()

        viewModel.downloadMedia(devicePhoto.id)
        advanceUntilIdle()

        assertTrue("expected transfer through media gateway, events=$events", events.contains("transfer:${devicePhoto.id}"))
        assertFalse("expected ViewModel not to duplicate device wifi open, events=$events", events.contains("configureWifi:true"))
    }

    @Test
    fun syncDeviceMediaToPhoneKeepsDeviceWifiOpenAfterTransferFinishes() = runTest {
        val events = mutableListOf<String>()
        val devicePhoto = MediaFile(
            id = "ute-wifi-photo-close-1",
            name = "IMG_CLOSE.jpg",
            kind = com.patrollink.domain.MediaKind.Photo,
            time = "123",
            size = "4 B",
            duration = null,
            verified = false,
            local = false,
            transferStatus = TransferStatus.Idle,
            progress = 0f
        )
        val mediaGateway = OrderRecordingMediaGateway(
            events = events,
            remote = listOf(devicePhoto),
            downloadedFiles = mapOf(
                devicePhoto.id to temp.newFile("IMG_CLOSE.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            )
        )
        val viewModel = testViewModel(
            coordinator = coordinatorWithMedia(mediaGateway),
            deviceControlGateway = TrackingWifiControlGateway(events)
        )
        loginForTest(viewModel)
        events.clear()

        viewModel.syncDeviceMediaToPhone(refreshFirst = true)
        advanceUntilIdle()

        assertTrue("expected transfer to finish, events=$events", events.contains("transfer:${devicePhoto.id}"))
        assertFalse("expected device wifi to stay open after transfer, events=$events", events.contains("configureWifi:false"))
    }

    @Test
    fun syncDeviceMediaToPhoneNotifiesDeviceOnlyAfterWholeBatchFinishes() = runTest {
        val events = mutableListOf<String>()
        val first = MediaFile(
            id = "ute-wifi-photo-batch-1",
            name = "IMG_BATCH_1.jpg",
            kind = com.patrollink.domain.MediaKind.Photo,
            time = "123",
            size = "4 B",
            duration = null,
            verified = false,
            local = false,
            transferStatus = TransferStatus.Idle,
            progress = 0f
        )
        val second = first.copy(
            id = "ute-wifi-photo-batch-2",
            name = "IMG_BATCH_2.jpg"
        )
        val mediaGateway = OrderRecordingMediaGateway(
            events = events,
            remote = listOf(first, second),
            downloadedFiles = mapOf(
                first.id to temp.newFile("IMG_BATCH_1.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) },
                second.id to temp.newFile("IMG_BATCH_2.jpg").apply { writeBytes(byteArrayOf(5, 6, 7, 8)) }
            )
        )
        val viewModel = testViewModel(
            coordinator = coordinatorWithMedia(mediaGateway),
            deviceControlGateway = TrackingWifiControlGateway(events)
        )
        loginForTest(viewModel)
        events.clear()

        viewModel.syncDeviceMediaToPhone(refreshFirst = true)
        advanceUntilIdle()

        assertEquals(
            "expected one device sync-complete notification after all batch transfers, events=$events",
            listOf("notifyMediaSyncCompleted"),
            events.filter { it == "notifyMediaSyncCompleted" }
        )
        assertTrue(
            "expected final notify after second transfer, events=$events",
            events.indexOf("transfer:${second.id}") < events.indexOf("notifyMediaSyncCompleted")
        )
        assertFalse("expected device wifi to stay open after batch sync, events=$events", events.contains("configureWifi:false"))
    }

    @Test
    fun syncDeviceMediaToPhoneRetriesFailedDeviceDownloadWithoutClosingDeviceWifi() = runTest {
        val events = mutableListOf<String>()
        val devicePhoto = MediaFile(
            id = "ute-wifi-photo-retry-1",
            name = "IMG_RETRY.jpg",
            kind = com.patrollink.domain.MediaKind.Photo,
            time = "123",
            size = "4 B",
            duration = null,
            verified = false,
            local = false,
            transferStatus = TransferStatus.Idle,
            progress = 0f
        )
        val mediaGateway = FlakyDownloadingMediaGateway(
            events = events,
            remote = devicePhoto,
            localFile = temp.newFile("IMG_RETRY.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        )
        val viewModel = testViewModel(
            coordinator = coordinatorWithMedia(mediaGateway),
            deviceControlGateway = TrackingWifiControlGateway(events)
        )
        loginForTest(viewModel)
        events.clear()

        viewModel.syncDeviceMediaToPhone(refreshFirst = true)
        advanceUntilIdle()

        assertEquals(2, mediaGateway.transferAttempts)
        assertTrue(viewModel.uiState.value.mediaFiles.any { it.id == devicePhoto.id && it.local && it.contentUri != null })
        assertEquals("已同步 1 个设备文件到手机本地媒体文件，设备热点保持连接", viewModel.uiState.value.operationMessage?.text)
        assertFalse("expected retry to keep device hotspot open, events=$events", events.contains("configureWifi:false"))
    }

    @Test
    fun syncDeviceMediaToPhoneIgnoresDuplicateTapWhileTransferIsActive() = runTest {
        val devicePhoto = MediaFile(
            id = "ute-wifi-photo-duplicate-1",
            name = "IMG_DUP.jpg",
            kind = com.patrollink.domain.MediaKind.Photo,
            time = "123",
            size = "4 B",
            duration = null,
            verified = false,
            local = false,
            transferStatus = TransferStatus.Idle,
            progress = 0f
        )
        val release = CompletableDeferred<Unit>()
        val mediaGateway = BlockingDownloadingMediaGateway(remote = devicePhoto, release = release)
        val viewModel = testViewModel(
            coordinator = coordinatorWithMedia(mediaGateway),
            deviceControlGateway = TrackingWifiControlGateway(mutableListOf())
        )
        loginForTest(viewModel)

        viewModel.syncDeviceMediaToPhone(refreshFirst = true)
        runCurrent()

        assertTrue(viewModel.uiState.value.deviceMediaSync.active)
        assertEquals(devicePhoto.id, viewModel.uiState.value.deviceMediaSync.fileId)
        assertEquals(devicePhoto.name, viewModel.uiState.value.deviceMediaSync.fileName)
        assertEquals(1, viewModel.uiState.value.deviceMediaSync.totalCount)

        viewModel.syncDeviceMediaToPhone(refreshFirst = true)
        runCurrent()

        assertEquals(1, mediaGateway.transferAttempts)
        assertEquals("正在同步设备文件，请稍候", viewModel.uiState.value.operationMessage?.text)

        release.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.deviceMediaSync.active)
    }

    @Test
    fun refreshMediaFilesRemovesStaleDeviceFilesWhenDeviceReportsEmpty() = runTest {
        val remote = MediaFile(
            id = "ute-wifi-stale-video",
            name = "stale-video.mp4",
            kind = com.patrollink.domain.MediaKind.Video,
            time = "123",
            size = "1 MB",
            duration = null,
            verified = false,
            local = false,
            transferStatus = TransferStatus.Idle,
            progress = 0f
        )
        val mediaGateway = MutableListingMediaGateway(remote = listOf(remote))
        val viewModel = testViewModel(coordinator = coordinatorWithMedia(mediaGateway))
        loginForTest(viewModel)
        viewModel.setMediaLocal(false)
        viewModel.refreshMediaFiles()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.mediaFiles.any { it.id == remote.id && !it.local })

        mediaGateway.remote = emptyList()
        viewModel.refreshMediaFiles()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.mediaFiles.none { it.id == remote.id && !it.local })
    }

    @Test
    fun refreshMediaFilesMarksDeviceFilesAlreadyPresentInPhoneSandbox() = runTest {
        val deviceFile = MediaFile(
            id = "ute-wifi-existing-phone-1",
            name = "IMG_EXISTING.jpg",
            kind = com.patrollink.domain.MediaKind.Photo,
            time = "123",
            size = "1 MB",
            duration = null,
            verified = false,
            local = false,
            transferStatus = TransferStatus.Idle,
            progress = 0f
        )
        val localFile = deviceFile.copy(
            local = true,
            contentUri = "file:///data/user/0/com.patrollink/files/patrol_media/ute/IMG_EXISTING.jpg"
        )
        val mediaGateway = MutableListingMediaGateway(remote = listOf(deviceFile), local = listOf(localFile))
        val viewModel = testViewModel(coordinator = coordinatorWithMedia(mediaGateway))
        loginForTest(viewModel)
        viewModel.setMediaLocal(false)
        viewModel.refreshMediaFiles()
        advanceUntilIdle()

        val deviceCopy = viewModel.uiState.value.mediaFiles.single { it.id == deviceFile.id && !it.local }
        val phoneCopy = viewModel.uiState.value.mediaFiles.single { it.id == deviceFile.id && it.local }
        assertEquals(TransferStatus.Done, deviceCopy.transferStatus)
        assertEquals(TransferTarget.PhoneSandbox, deviceCopy.lastTransferTarget)
        assertEquals(localFile.contentUri, phoneCopy.contentUri)
    }

    @Test
    fun syncDeviceMediaToPhoneSkipsSameNamedLocalSandboxCopyWithDifferentId() = runTest {
        val deviceFile = MediaFile(
            id = "ute-wifi-existing-phone-2",
            name = "眼镜照片_20260613152349823.jpg",
            kind = com.patrollink.domain.MediaKind.Photo,
            time = "123",
            size = "1 MB",
            duration = null,
            verified = false,
            local = false,
            transferStatus = TransferStatus.Idle,
            progress = 0f
        )
        val localFile = deviceFile.copy(
            id = "ute-photo-20260613152349823",
            local = true,
            contentUri = "file:///data/user/0/com.patrollink/files/patrol_media/ute/20260613152349823.jpg"
        )
        val mediaGateway = MutableListingMediaGateway(remote = listOf(deviceFile), local = listOf(localFile))
        val viewModel = testViewModel(coordinator = coordinatorWithMedia(mediaGateway))
        loginForTest(viewModel)

        viewModel.syncDeviceMediaToPhone(refreshFirst = true)
        advanceUntilIdle()

        val deviceCopy = viewModel.uiState.value.mediaFiles.single { it.id == deviceFile.id && !it.local }
        assertEquals(TransferStatus.Done, deviceCopy.transferStatus)
        assertEquals(TransferTarget.PhoneSandbox, deviceCopy.lastTransferTarget)
        assertEquals("设备端文件已在手机端，无需重复同步", viewModel.uiState.value.operationMessage?.text)
    }

    @Test
    fun syncDeviceMediaToPhoneRefreshFailureRemovesStaleDeviceFiles() = runTest {
        val remote = MediaFile(
            id = "ute-wifi-stale-sync-photo",
            name = "stale-photo.jpg",
            kind = com.patrollink.domain.MediaKind.Photo,
            time = "123",
            size = "1 MB",
            duration = null,
            verified = false,
            local = false,
            transferStatus = TransferStatus.Idle,
            progress = 0f
        )
        val existingLocal = remote.copy(
            id = "ute-photo-existing-local",
            name = "眼镜照片_existing-local.jpg",
            local = true,
            verified = true,
            contentUri = temp.newFile("existing-local.jpg").toURI().toString()
        )
        val mediaGateway = MutableListingMediaGateway(remote = listOf(remote), local = listOf(existingLocal))
        val viewModel = testViewModel(coordinator = coordinatorWithMedia(mediaGateway))
        loginForTest(viewModel)
        viewModel.setMediaLocal(false)
        viewModel.refreshMediaFiles()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.mediaFiles.any { it.id == remote.id && !it.local })
        assertTrue(viewModel.uiState.value.mediaFiles.any { it.id == existingLocal.id && it.local })

        mediaGateway.local = emptyList()
        mediaGateway.remoteFailure = IllegalStateException("device wifi switch rejected: error=100000,data=false")
        viewModel.syncDeviceMediaToPhone(refreshFirst = true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.mediaFiles.none { it.id == remote.id && !it.local })
        assertTrue(viewModel.uiState.value.mediaFiles.any { it.id == existingLocal.id && it.local && it.contentUri == existingLocal.contentUri })
        assertEquals(
            "设备文件读取失败：设备热点开启被拒绝或超时；请确认设备电量充足、蓝牙仍连接，并等待设备空闲后重试",
            viewModel.uiState.value.operationMessage?.text
        )
    }

    @Test
    fun manualDeviceMediaRefreshShowsWifiFailureToOperator() = runTest {
        val viewModel = testViewModel(
            coordinator = coordinatorWithMedia(FailingDeviceListingMediaGateway(IllegalStateException("device wifi unavailable: UTE_00F7")))
        )
        loginForTest(viewModel)
        viewModel.setMediaLocal(false)

        viewModel.refreshMediaFiles(showFailureMessage = true)
        advanceUntilIdle()

        assertEquals("设备文件读取失败：device wifi unavailable: UTE_00F7；请确认手机已连接设备热点后重试", viewModel.uiState.value.operationMessage?.text)
        assertEquals(OperationMessageType.Error, viewModel.uiState.value.operationMessage?.type)
    }

    @Test
    fun manualDeviceMediaRefreshShowsDeviceFileServiceFailureToOperator() = runTest {
        val viewModel = testViewModel(
            coordinator = coordinatorWithMedia(
                FailingDeviceListingMediaGateway(
                    IllegalStateException("device media http service did not expose media list: http://192.168.222.1:8000/media -> Failed to connect")
                )
            )
        )
        loginForTest(viewModel)
        viewModel.setMediaLocal(false)

        viewModel.refreshMediaFiles(showFailureMessage = true)
        advanceUntilIdle()

        assertEquals(
            "设备文件读取失败：手机已连接设备热点，但设备文件服务没有响应；请保持蓝牙连接，等待设备空闲后重新查看设备文件",
            viewModel.uiState.value.operationMessage?.text
        )
        assertEquals(OperationMessageType.Error, viewModel.uiState.value.operationMessage?.type)
    }

    @Test
    fun manualDeviceMediaRefreshShowsEmptyDeviceListToOperator() = runTest {
        val viewModel = testViewModel(
            coordinator = coordinatorWithMedia(MutableListingMediaGateway(remote = emptyList(), local = emptyList()))
        )
        loginForTest(viewModel)
        viewModel.setMediaLocal(false)

        viewModel.refreshMediaFiles(showFailureMessage = true)
        advanceUntilIdle()

        assertEquals("设备端没有读取到媒体文件；请先拍照/录像/录音，或确认手机已连接设备热点后重试", viewModel.uiState.value.operationMessage?.text)
        assertEquals(OperationMessageType.Warning, viewModel.uiState.value.operationMessage?.type)
    }

    @Test
    fun failedCloudUploadEnqueuesEvidenceForBackgroundRetry() = runTest {
        val localFile = temp.newFile("failed-upload.mp4").apply { writeBytes(byteArrayOf(7, 8, 9)) }
        val media = MediaFile(
            id = "ute-video-failed-upload",
            name = "failed-upload.mp4",
            kind = com.patrollink.domain.MediaKind.Video,
            time = "123",
            size = "3 B",
            duration = null,
            verified = true,
            local = true,
            transferStatus = TransferStatus.Idle,
            progress = 0f,
            contentUri = localFile.absolutePath
        )
        val taskGateway = InMemoryBackgroundTaskGateway()
        val viewModel = testViewModel(
            coordinator = coordinatorWithMedia(FailingCloudMediaGateway(media)),
            offlineSyncEngine = OfflineSyncEngine(taskGateway)
        )
        loginForTest(viewModel)

        viewModel.uploadMedia(media.id, local = true)
        advanceUntilIdle()

        val queued = taskGateway.pending().single().task
        assertEquals(BackgroundTaskType.UploadEvidence, queued.type)
        assertEquals(media.id, queued.payloadId)
        assertTrue(viewModel.uiState.value.mediaFiles.single { it.id == media.id && it.local }.contentUri?.isNotBlank() == true)
    }

    @Test
    fun failedCloudUploadKeepsLocalPreviewPlayable() = runTest {
        val localFile = temp.newFile("failed-upload-preview.jpg").apply { writeBytes(byteArrayOf(7, 8, 9)) }
        val media = MediaFile(
            id = "ute-wifi-failed-upload-preview",
            name = "failed-upload-preview.jpg",
            kind = com.patrollink.domain.MediaKind.Photo,
            time = "123",
            size = "3 B",
            duration = null,
            verified = true,
            local = true,
            transferStatus = TransferStatus.Idle,
            progress = 0f,
            contentUri = localFile.absolutePath
        )
        val viewModel = testViewModel(
            coordinator = coordinatorWithMedia(FailingCloudMediaGateway(media))
        )
        loginForTest(viewModel)

        viewModel.uploadMedia(media.id, local = true)
        advanceUntilIdle()
        viewModel.openMediaPreview(media.id, local = true)
        advanceUntilIdle()

        assertEquals(media.id, viewModel.uiState.value.previewMediaFile?.id)
        assertTrue(viewModel.uiState.value.previewMediaFile?.contentUri?.isNotBlank() == true)
    }

    @Test
    fun openDeviceVideoPreviewUsesRemoteDeviceUriWithoutForcingPhoneSync() = runTest {
        val remote = MediaFile(
            id = "ute-wifi-video-preview",
            name = "眼镜视频_preview.mp4",
            kind = com.patrollink.domain.MediaKind.Video,
            time = "123",
            size = "4 B",
            duration = null,
            verified = false,
            local = false,
            transferStatus = TransferStatus.Idle,
            progress = 0f,
            contentUri = "http://192.168.222.1:8000/media/preview.mp4"
        )
        val localFile = temp.newFile("preview.mp4").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val mediaGateway = DownloadingMediaGateway(remote, localFile)
        val viewModel = testViewModel(coordinator = coordinatorWithMedia(mediaGateway))
        viewModel.setMediaLocal(false)
        viewModel.refreshMediaFiles(showFailureMessage = true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.mediaFiles.any { it.id == remote.id && !it.local })

        viewModel.openMediaPreview(remote.id, local = false)
        advanceUntilIdle()

        assertTrue(mediaGateway.transferTargets.isEmpty())
        val preview = viewModel.uiState.value.previewMediaFile
        assertEquals(remote.id, preview?.id)
        assertTrue(preview?.local == false)
        assertEquals(remote.contentUri, preview?.contentUri)
    }

    @Test
    fun openDevicePreviewWithoutRemoteUriDoesNotFallbackToPhoneSync() = runTest {
        val remote = MediaFile(
            id = "ute-wifi-video-no-uri",
            name = "眼镜视频_no_uri.mp4",
            kind = com.patrollink.domain.MediaKind.Video,
            time = "123",
            size = "4 B",
            duration = null,
            verified = false,
            local = false,
            transferStatus = TransferStatus.Idle,
            progress = 0f,
            contentUri = null
        )
        val localFile = temp.newFile("preview-fallback.mp4").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val mediaGateway = DownloadingMediaGateway(remote, localFile)
        val viewModel = testViewModel(coordinator = coordinatorWithMedia(mediaGateway))
        viewModel.setMediaLocal(false)
        viewModel.refreshMediaFiles(showFailureMessage = true)
        advanceUntilIdle()

        viewModel.openMediaPreview(remote.id, local = false)
        advanceUntilIdle()

        assertTrue(mediaGateway.transferTargets.isEmpty())
        assertEquals(null, viewModel.uiState.value.previewMediaFile)
        assertEquals(remote.id, viewModel.uiState.value.selectedMediaFileId)
    }

    @Test
    fun successfulCloudUploadClosesPreviewAndShowsSuccessMessage() = runTest {
        val localFile = temp.newFile("cloud-upload.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val media = MediaFile(
            id = "ute-wifi-cloud-upload",
            name = "cloud-upload.jpg",
            kind = com.patrollink.domain.MediaKind.Photo,
            time = "123",
            size = "3 B",
            duration = null,
            verified = true,
            local = true,
            transferStatus = TransferStatus.Idle,
            progress = 0f,
            contentUri = localFile.toURI().toString()
        )
        val viewModel = testViewModel(
            coordinator = coordinatorWithMedia(SuccessfulCloudMediaGateway(media))
        )
        loginForTest(viewModel)
        viewModel.openMediaPreview(media.id, local = true)
        advanceUntilIdle()

        viewModel.uploadMedia(media.id, local = true)
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.previewMediaFile)
        assertEquals("cloud-upload.jpg 已上传云端", viewModel.uiState.value.operationMessage?.text)
        val uploaded = viewModel.uiState.value.mediaFiles.single { it.id == media.id && it.local }
        assertEquals(TransferStatus.Done, uploaded.transferStatus)
        assertEquals(TransferTarget.Cloud, uploaded.lastTransferTarget)
        assertEquals(localFile.toURI().toString(), uploaded.contentUri)
    }

    @Test
    fun consecutivePhoneMediaCloudUploadsBothComplete() = runTest {
        val firstFile = temp.newFile("cloud-upload-1.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val secondFile = temp.newFile("cloud-upload-2.jpg").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val first = MediaFile(
            id = "ute-wifi-cloud-upload-1",
            name = "cloud-upload-1.jpg",
            kind = com.patrollink.domain.MediaKind.Photo,
            time = "123",
            size = "3 B",
            duration = null,
            verified = true,
            local = true,
            transferStatus = TransferStatus.Idle,
            progress = 0f,
            contentUri = firstFile.toURI().toString()
        )
        val second = first.copy(
            id = "ute-wifi-cloud-upload-2",
            name = "cloud-upload-2.jpg",
            contentUri = secondFile.toURI().toString()
        )
        val gateway = MultiSuccessfulCloudMediaGateway(listOf(first, second))
        val viewModel = testViewModel(coordinator = coordinatorWithMedia(gateway))
        loginForTest(viewModel)

        viewModel.uploadMedia(first.id, local = true)
        advanceUntilIdle()
        viewModel.uploadMedia(second.id, local = true)
        advanceUntilIdle()

        assertEquals(listOf(first.id, second.id), gateway.uploadedIds)
        val uploaded = viewModel.uiState.value.mediaFiles.filter { it.local && it.lastTransferTarget == TransferTarget.Cloud }
        assertTrue(uploaded.any { it.id == first.id && it.transferStatus == TransferStatus.Done })
        assertTrue(uploaded.any { it.id == second.id && it.transferStatus == TransferStatus.Done })
        assertEquals("cloud-upload-2.jpg 已上传云端", viewModel.uiState.value.operationMessage?.text)
    }

    @Test
    fun refreshMediaFilesExposesLoadingStateBeforeLocalMediaArrives() = runTest {
        val localFile = temp.newFile("local-delayed.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val media = MediaFile(
            id = "ute-photo-local-delayed",
            name = "local-delayed.jpg",
            kind = com.patrollink.domain.MediaKind.Photo,
            time = "123",
            size = "3 B",
            duration = null,
            verified = true,
            local = true,
            transferStatus = TransferStatus.Idle,
            progress = 0f,
            contentUri = localFile.toURI().toString()
        )
        val viewModel = testViewModel(
            coordinator = coordinatorWithMedia(DelayedLocalListingMediaGateway(media))
        )
        loginForTest(viewModel)

        viewModel.refreshMediaFiles()
        runCurrent()

        assertTrue(viewModel.uiState.value.mediaLoading)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.mediaLoading)
        assertTrue(viewModel.uiState.value.mediaFiles.any { it.id == media.id && it.local })
    }

    @Test
    fun loginLoadsOfficerDutyAreaFromCurrentPatrolArea() = runTest {
        val viewModel = testViewModel()

        loginForTest(viewModel)

        assertEquals(viewModel.uiState.value.patrolArea.name, viewModel.uiState.value.user.dutyArea)
        assertEquals("TEAM-A-42", viewModel.uiState.value.patrolArea.teamId)
        assertTrue(viewModel.uiState.value.patrolArea.boundary.isNotEmpty())
        assertTrue(viewModel.uiState.value.patrolArea.route.isNotEmpty())
    }

    @Test
    fun wifiAccountMismatchMessageIsShownToOperator() = runTest {
        val expected = "设备账号不一致，请先在原应用解绑或重置设备后重新配对 PatrolLink"
        val viewModel = testViewModel(
            deviceControlGateway = FailingWifiControlGateway(IllegalStateException(expected))
        )
        loginAndConnect(viewModel)

        viewModel.configureDeviceWifi(enabled = true, ssid = "", password = "")
        advanceUntilIdle()

        assertEquals(expected, viewModel.uiState.value.operationMessage?.text)
        assertEquals(OperationMessageType.Error, viewModel.uiState.value.operationMessage?.type)
    }

    @Test
    fun wifiManualConnectionMessageIsShownToOperator() = runTest {
        val expected = "手机系统未授权连接设备热点 UTE_00F7；请在系统 Wi-Fi 弹窗或设置中手动选择 UTE_00F7，连接后返回 PatrolLink 重试媒体同步"
        val viewModel = testViewModel(
            deviceControlGateway = FailingWifiControlGateway(IllegalStateException(expected))
        )
        loginAndConnect(viewModel)

        viewModel.configureDeviceWifi(enabled = true, ssid = "", password = "")
        advanceUntilIdle()

        assertEquals(expected, viewModel.uiState.value.operationMessage?.text)
        assertEquals(OperationMessageType.Error, viewModel.uiState.value.operationMessage?.type)
    }

    @Test
    fun wifiApStoppedStateMessageIsShownToOperator() = runTest {
        val viewModel = testViewModel(
            deviceControlGateway = FailingWifiControlGateway(IllegalStateException("device wifi did not enable: 5"))
        )
        loginAndConnect(viewModel)

        viewModel.configureDeviceWifi(enabled = true, ssid = "", password = "")
        advanceUntilIdle()

        assertEquals("设备热点未开启，请确认设备电量和当前模式后重试；若仍失败，请在设备侧重启 Wi-Fi 或重启设备", viewModel.uiState.value.operationMessage?.text)
        assertEquals(OperationMessageType.Error, viewModel.uiState.value.operationMessage?.type)
    }

    @Test
    fun clearDeviceAccountUnbindsLocalDeviceAndPromptsReconnect() = runTest {
        val gateway = ResetControlGateway(clearSuccess = true)
        val viewModel = testViewModel(deviceControlGateway = gateway)
        loginAndConnect(viewModel)

        viewModel.clearConnectedDeviceAccount()
        advanceUntilIdle()

        assertTrue(gateway.clearCalled)
        assertFalse(viewModel.uiState.value.device.online)
        assertTrue(viewModel.uiState.value.connectedDevices.isEmpty())
        assertEquals("设备账号已清除，请重新配对 PatrolLink", viewModel.uiState.value.operationMessage?.text)
    }

    @Test
    fun unbindDiscoveredDeviceClearsDeviceAccountWhenResolvedByMac() = runTest {
        val gateway = ResetControlGateway(clearSuccess = true)
        val viewModel = testViewModel(deviceControlGateway = gateway)
        loginAndConnect(viewModel)

        viewModel.unbindDiscoveredDevice(
            scannedId = "ute-ble-control-scanned-FD:4A:BA:43:A2:43",
            macAddress = "HEADSET_001"
        )
        advanceUntilIdle()

        assertTrue(gateway.clearCalled)
        assertFalse(viewModel.uiState.value.device.online)
        assertTrue(viewModel.uiState.value.connectedDevices.isEmpty())
        assertEquals("设备账号已清除，请重新配对 PatrolLink", viewModel.uiState.value.operationMessage?.text)
    }

    @Test
    fun unbindDiscoveredDeviceRemovesLocalBindingWhenDeviceAccountClearFails() = runTest {
        val gateway = ResetControlGateway(clearSuccess = false)
        val viewModel = testViewModel(deviceControlGateway = gateway)
        loginAndConnect(viewModel)

        viewModel.unbindDiscoveredDevice(
            scannedId = "ute-ble-control-scanned-FD:4A:BA:43:A2:43",
            macAddress = "HEADSET_001"
        )
        advanceUntilIdle()

        assertTrue(gateway.clearCalled)
        assertFalse(viewModel.uiState.value.device.online)
        assertTrue(viewModel.uiState.value.connectedDevices.isEmpty())
        assertEquals("设备端账号清除失败，已移除 PatrolLink 本地绑定；如仍无法重连，请在设备侧重置配对", viewModel.uiState.value.operationMessage?.text)
        assertEquals(OperationMessageType.Warning, viewModel.uiState.value.operationMessage?.type)
    }

    @Test
    fun unbindDiscoveredDeviceResolvesConnectedHeadsetByScannedNameWhenIdsDiffer() = runTest {
        val gateway = ResetControlGateway(clearSuccess = true)
        val viewModel = testViewModel(deviceControlGateway = gateway)
        loginAndConnect(viewModel)

        viewModel.unbindDiscoveredDevice(
            scannedId = "FD:4A:BA:43:A2:43",
            macAddress = "FD:4A:BA:43:A2:43",
            scannedName = "ForceLink-H1",
            scannedType = DeviceType.Headset
        )
        advanceUntilIdle()

        assertTrue(gateway.clearCalled)
        assertFalse(viewModel.uiState.value.device.online)
        assertTrue(viewModel.uiState.value.connectedDevices.isEmpty())
        assertEquals("设备账号已清除，请重新配对 PatrolLink", viewModel.uiState.value.operationMessage?.text)
    }

    @Test
    fun unbindDiscoveredDeviceClearsDeviceAccountWhenTargetIsConnectedButNotSelected() = runTest {
        val gateway = ResetControlGateway(clearSuccess = true)
        val viewModel = testViewModel(
            coordinator = coordinatorWithDeviceGateway(TwoDeviceGateway()),
            deviceControlGateway = gateway
        )
        loginForTest(viewModel)
        viewModel.connectDiscoveredDevice(
            id = "HEADSET_001",
            name = "ForceLink-H1",
            mac = "2C:4A:91:3F:8B:02",
            signalBars = 4,
            type = DeviceType.Headset
        )
        viewModel.connectDiscoveredDevice(
            id = "GLASSES_G1",
            name = "PatrolGlass-G1",
            mac = "6B:13:9E:41:D7:50",
            signalBars = 4,
            type = DeviceType.Glasses
        )
        advanceUntilIdle()

        assertEquals("GLASSES_G1", viewModel.uiState.value.device.id)
        assertTrue(viewModel.uiState.value.connectedDevices.any { it.id == "HEADSET_001" })

        viewModel.unbindDiscoveredDevice(
            scannedId = "HEADSET_001",
            macAddress = "2C:4A:91:3F:8B:02",
            scannedName = "ForceLink-H1",
            scannedType = DeviceType.Headset
        )
        advanceUntilIdle()

        assertTrue(gateway.clearCalled)
        assertEquals("GLASSES_G1", viewModel.uiState.value.device.id)
        assertTrue(viewModel.uiState.value.connectedDevices.none { it.id == "HEADSET_001" })
        assertEquals("设备账号已清除，请重新配对 PatrolLink", viewModel.uiState.value.operationMessage?.text)
    }

    @Test
    fun systemConnectedGlassesRemainGlassesInConnectedDevices() = runTest {
        val viewModel = testViewModel(coordinator = coordinatorWithDeviceGateway(SystemBluetoothScanGateway()))
        loginForTest(viewModel)

        viewModel.refreshScannedDevices()
        advanceUntilIdle()

        val connected = viewModel.uiState.value.connectedDevices.single { it.id == "78:02:B7:66:00:F7" }
        assertEquals(DeviceType.Glasses, connected.type)
        assertEquals(DeviceType.Glasses, viewModel.uiState.value.device.type)
        assertTrue(viewModel.uiState.value.connectedDevices.none { it.id == "FD:4A:BA:43:A2:43" })
    }

    @Test
    fun systemConnectedGlassesReplaceStaleE1CurrentDevice() = runTest {
        val viewModel = testViewModel(coordinator = coordinatorWithDeviceGateway(StaleE1ThenConnectedGlassGateway()))
        loginForTest(viewModel)

        viewModel.connectDiscoveredDevice(
            id = "FD:4A:BA:43:A2:43",
            name = "E1-Pro-A243",
            mac = "FD:4A:BA:43:A2:43",
            signalBars = 4,
            type = DeviceType.Headset
        )
        advanceUntilIdle()

        assertEquals("FD:4A:BA:43:A2:43", viewModel.uiState.value.device.id)

        viewModel.refreshScannedDevices()
        advanceUntilIdle()

        assertEquals("78:02:B7:66:00:F7", viewModel.uiState.value.device.id)
        assertEquals(DeviceType.Glasses, viewModel.uiState.value.device.type)
        assertTrue(viewModel.uiState.value.connectedDevices.any { it.id == "78:02:B7:66:00:F7" && it.type == DeviceType.Glasses })
        assertTrue(viewModel.uiState.value.connectedDevices.none { it.id == "FD:4A:BA:43:A2:43" })
    }

    @Test
    fun factoryResetHeadsetUnbindsLocalDeviceAndPromptsReconnect() = runTest {
        val gateway = ResetControlGateway(factoryResetSuccess = true)
        val viewModel = testViewModel(deviceControlGateway = gateway)
        loginAndConnect(viewModel)

        viewModel.factoryResetConnectedDevice(DeviceFactoryResetTarget.Headset)
        advanceUntilIdle()

        assertEquals(DeviceFactoryResetTarget.Headset, gateway.factoryResetTarget)
        assertFalse(viewModel.uiState.value.device.online)
        assertTrue(viewModel.uiState.value.connectedDevices.isEmpty())
        assertEquals("耳机已恢复出厂并重启，请重新搜索并配对 PatrolLink", viewModel.uiState.value.operationMessage?.text)
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

    @Test
    fun saveCerebellumSettingsStoresLocallyAndEnablesHealthCheckWithoutBackend() = runTest {
        val runtimeConfig = FakeRuntimeConfigGateway()
        val createdApis = mutableListOf<Pair<String, String>>()
        val viewModel = testViewModel(
            runtimeConfigStore = runtimeConfig,
            cerebellumApiFactory = { baseUrl, apiKey ->
                createdApis += baseUrl to apiKey
                FakeHealthCerebellumApi()
            }
        )

        viewModel.updateCerebellumBaseUrl(" http://192.168.11.157:8088/ ")
        viewModel.updateCerebellumApiKey(" local-key ")
        viewModel.saveCerebellumSettings()
        advanceUntilIdle()
        viewModel.checkCerebellumHealth()
        advanceUntilIdle()

        assertEquals("http://192.168.11.157:8088", runtimeConfig.saved?.baseUrl)
        assertEquals("local-key", runtimeConfig.saved?.apiKey)
        assertEquals("http://192.168.11.157:8088" to "local-key", createdApis.single())
        assertEquals("ok · local-cerebellum", viewModel.uiState.value.cerebellumSettings.healthStatus)
        assertEquals("小脑健康检查通过", viewModel.uiState.value.operationMessage?.text)
    }

    @Test
    fun refreshMediaFilesWhileViewingPhoneOnlyLoadsLocalMedia() = runTest {
        val gateway = CountingMediaGateway(
            localMedia = listOf(
                MediaFile(
                    id = "ute-photo-local-fast",
                    name = "眼镜照片_fast.jpg",
                    kind = com.patrollink.domain.MediaKind.Photo,
                    time = "1710000000000",
                    size = "1 MB",
                    duration = null,
                    verified = true,
                    local = true,
                    transferStatus = TransferStatus.Idle,
                    progress = 0f,
                    contentUri = temp.newFile("fast.jpg").toURI().toString()
                )
            )
        )
        val viewModel = testViewModel(coordinator = coordinatorWithMedia(gateway))

        viewModel.setMediaLocal(true)
        viewModel.refreshMediaFiles(showFailureMessage = true)
        advanceUntilIdle()

        assertEquals(1, gateway.localListCalls)
        assertEquals(0, gateway.deviceListCalls)
        assertTrue(viewModel.uiState.value.mediaFiles.any { it.local && it.id == "ute-photo-local-fast" })
    }
}

private fun testViewModel(
    cerebellumApi: CerebellumApi? = null,
    coordinator: PatrolCoordinator = MockPatrolCoordinatorFactory.create(),
    offlineSyncEngine: OfflineSyncEngine? = null,
    deviceControlGateway: DeviceControlGateway? = null,
    runtimeConfigStore: RuntimeConfigGateway? = null,
    cerebellumApiFactory: (String, String) -> CerebellumApi? = { _, _ -> null }
) = PatrolViewModel(
    coordinator = coordinator,
    deviceControlGateway = deviceControlGateway,
    versionGateway = MockVersionGateway(),
    cerebellumApi = cerebellumApi,
    cerebellumApiFactory = cerebellumApiFactory,
    runtimeConfigStore = runtimeConfigStore,
    offlineSyncEngine = offlineSyncEngine
)

private class FakeRuntimeConfigGateway : RuntimeConfigGateway {
    var saved: CerebellumRuntimeSettings? = null

    override fun readCerebellumSettings(): CerebellumRuntimeSettings =
        saved ?: CerebellumRuntimeSettings(baseUrl = "", apiKey = "")

    override fun saveCerebellumSettings(baseUrl: String, apiKey: String): CerebellumRuntimeSettings =
        CerebellumRuntimeSettings(baseUrl = baseUrl.trim().trimEnd('/'), apiKey = apiKey.trim()).also {
            saved = it
        }
}

private class FakeHealthCerebellumApi : FakeCerebellumReportApi() {
    override suspend fun health(): CerebellumHealthDto =
        CerebellumHealthDto(
            status = "ok",
            deviceId = "local-cerebellum",
            uptimeSeconds = 12,
            primaryModel = "local-model"
        )
}

private fun coordinatorWithMedia(mediaGateway: MediaGateway) = PatrolCoordinator(
    authGateway = com.patrollink.data.MockAuthGateway(),
    deviceGateway = com.patrollink.data.MockDeviceGateway(),
    alertGateway = com.patrollink.data.MockAlertGateway(),
    mediaGateway = mediaGateway,
    realtimeGateway = com.patrollink.data.MockRealtimeGateway(),
    streamRelayGateway = com.patrollink.data.MockStreamRelayGateway(),
    sosGateway = com.patrollink.data.MockSosGateway(),
    patrolAreaGateway = com.patrollink.data.MockPatrolAreaGateway()
)

private fun coordinatorWithDeviceGateway(deviceGateway: DeviceGateway) = PatrolCoordinator(
    authGateway = com.patrollink.data.MockAuthGateway(),
    deviceGateway = deviceGateway,
    alertGateway = com.patrollink.data.MockAlertGateway(),
    mediaGateway = com.patrollink.data.MockMediaGateway(),
    realtimeGateway = com.patrollink.data.MockRealtimeGateway(),
    streamRelayGateway = com.patrollink.data.MockStreamRelayGateway(),
    sosGateway = com.patrollink.data.MockSosGateway(),
    patrolAreaGateway = com.patrollink.data.MockPatrolAreaGateway()
)

private class FailingCloudMediaGateway(private val localMedia: MediaFile) : MediaGateway {
    override suspend fun listFiles(local: Boolean): List<MediaFile> =
        if (local) listOf(localMedia) else emptyList()

    override fun transfer(fileId: String, target: TransferTarget): Flow<MediaFile> = flow {
        error("network unavailable")
    }

    override suspend fun delete(fileId: String, local: Boolean): Boolean = false
    override suspend fun verifySha256(fileId: String): Boolean = false
}

private class SuccessfulCloudMediaGateway(private val localMedia: MediaFile) : MediaGateway {
    override suspend fun listFiles(local: Boolean): List<MediaFile> =
        if (local) listOf(localMedia) else emptyList()

    override fun transfer(fileId: String, target: TransferTarget): Flow<MediaFile> = flow {
        emit(localMedia.copy(transferStatus = TransferStatus.Uploading, progress = 0.25f, lastTransferTarget = target))
        emit(localMedia.copy(transferStatus = TransferStatus.Done, progress = 1f, lastTransferTarget = target))
    }

    override suspend fun delete(fileId: String, local: Boolean): Boolean = false
    override suspend fun verifySha256(fileId: String): Boolean = false
}

private class MultiSuccessfulCloudMediaGateway(private val localMedia: List<MediaFile>) : MediaGateway {
    val uploadedIds = mutableListOf<String>()

    override suspend fun listFiles(local: Boolean): List<MediaFile> =
        if (local) localMedia else emptyList()

    override fun transfer(fileId: String, target: TransferTarget): Flow<MediaFile> = flow {
        val media = localMedia.first { it.id == fileId }
        uploadedIds += fileId
        emit(media.copy(transferStatus = TransferStatus.Uploading, progress = 0.25f, lastTransferTarget = target))
        emit(media.copy(transferStatus = TransferStatus.Done, progress = 1f, lastTransferTarget = target))
    }

    override suspend fun delete(fileId: String, local: Boolean): Boolean = false
    override suspend fun verifySha256(fileId: String): Boolean = false
}

private class DelayedLocalListingMediaGateway(private val localMedia: MediaFile) : MediaGateway {
    override suspend fun listFiles(local: Boolean): List<MediaFile> {
        if (local) delay(1_000)
        return if (local) listOf(localMedia) else emptyList()
    }

    override fun transfer(fileId: String, target: TransferTarget): Flow<MediaFile> = emptyFlow()
    override suspend fun delete(fileId: String, local: Boolean): Boolean = false
    override suspend fun verifySha256(fileId: String): Boolean = false
}

private class CountingMediaGateway(private val localMedia: List<MediaFile>) : MediaGateway {
    var localListCalls = 0
    var deviceListCalls = 0

    override suspend fun listFiles(local: Boolean): List<MediaFile> {
        return if (local) {
            localListCalls += 1
            localMedia
        } else {
            deviceListCalls += 1
            emptyList()
        }
    }

    override fun transfer(fileId: String, target: TransferTarget): Flow<MediaFile> = emptyFlow()
    override suspend fun delete(fileId: String, local: Boolean): Boolean = false
    override suspend fun verifySha256(fileId: String): Boolean = false
}

private class DownloadingMediaGateway(
    private val remote: MediaFile,
    private val localFile: java.io.File
) : MediaGateway {
    val transferTargets = mutableListOf<TransferTarget>()

    override suspend fun listFiles(local: Boolean): List<MediaFile> =
        if (local) emptyList() else listOf(remote)

    override fun transfer(fileId: String, target: TransferTarget): Flow<MediaFile> = flow {
        transferTargets += target
        emit(remote.copy(transferStatus = TransferStatus.Uploading, progress = 0.25f))
        emit(
            remote.copy(
                local = true,
                verified = true,
                transferStatus = TransferStatus.Done,
                progress = 1f,
                contentUri = localFile.toURI().toString(),
                lastTransferTarget = target
            )
        )
    }

    override suspend fun delete(fileId: String, local: Boolean): Boolean = false
    override suspend fun verifySha256(fileId: String): Boolean = false
}

private class BatchDownloadingMediaGateway(
    var remote: List<MediaFile>,
    private val local: List<MediaFile>,
    private val downloadedFiles: Map<String, java.io.File>
) : MediaGateway {
    val downloadedIds = mutableListOf<String>()

    override suspend fun listFiles(local: Boolean): List<MediaFile> =
        if (local) this.local else remote

    override fun transfer(fileId: String, target: TransferTarget): Flow<MediaFile> = flow {
        val media = remote.first { it.id == fileId }
        val localFile = downloadedFiles.getValue(fileId)
        downloadedIds += fileId
        emit(media.copy(transferStatus = TransferStatus.Uploading, progress = 0.25f))
        emit(
            media.copy(
                local = true,
                verified = true,
                transferStatus = TransferStatus.Done,
                progress = 1f,
                contentUri = localFile.toURI().toString(),
                lastTransferTarget = target
            )
        )
    }

    override suspend fun delete(fileId: String, local: Boolean): Boolean = false
    override suspend fun verifySha256(fileId: String): Boolean = false
}

private class OrderRecordingMediaGateway(
    private val events: MutableList<String>,
    private val remote: List<MediaFile>,
    private val downloadedFiles: Map<String, java.io.File>
) : MediaGateway {
    val downloadedIds = mutableListOf<String>()

    override suspend fun listFiles(local: Boolean): List<MediaFile> {
        events += if (local) "listPhone" else "listDevice"
        return if (local) emptyList() else remote
    }

    override fun transfer(fileId: String, target: TransferTarget): Flow<MediaFile> = flow {
        events += "transfer:$fileId"
        val media = remote.first { it.id == fileId }
        val localFile = downloadedFiles.getValue(fileId)
        downloadedIds += fileId
        emit(media.copy(transferStatus = TransferStatus.Uploading, progress = 0.25f))
        emit(
            media.copy(
                local = true,
                verified = true,
                transferStatus = TransferStatus.Done,
                progress = 1f,
                contentUri = localFile.toURI().toString(),
                lastTransferTarget = target
            )
        )
    }

    override suspend fun delete(fileId: String, local: Boolean): Boolean = false
    override suspend fun verifySha256(fileId: String): Boolean = false
}

private class BlockingDownloadingMediaGateway(
    private val remote: MediaFile,
    private val release: CompletableDeferred<Unit>
) : MediaGateway {
    var transferAttempts = 0

    override suspend fun listFiles(local: Boolean): List<MediaFile> =
        if (local) emptyList() else listOf(remote)

    override fun transfer(fileId: String, target: TransferTarget): Flow<MediaFile> = flow {
        transferAttempts += 1
        emit(remote.copy(transferStatus = TransferStatus.Uploading, progress = 0.25f))
        release.await()
        emit(
            remote.copy(
                local = true,
                verified = true,
                transferStatus = TransferStatus.Done,
                progress = 1f,
                contentUri = java.io.File("/tmp/$fileId.jpg").toURI().toString(),
                lastTransferTarget = target
            )
        )
    }

    override suspend fun delete(fileId: String, local: Boolean): Boolean = false
    override suspend fun verifySha256(fileId: String): Boolean = false
}

private class FlakyDownloadingMediaGateway(
    private val events: MutableList<String>,
    private val remote: MediaFile,
    private val localFile: java.io.File
) : MediaGateway {
    var transferAttempts = 0

    override suspend fun listFiles(local: Boolean): List<MediaFile> =
        if (local) emptyList() else listOf(remote)

    override fun transfer(fileId: String, target: TransferTarget): Flow<MediaFile> = flow {
        transferAttempts += 1
        events += "transfer:$fileId:$transferAttempts"
        emit(remote.copy(transferStatus = TransferStatus.Uploading, progress = 0.25f))
        if (transferAttempts == 1) {
            error("wifi media download failed: http://192.168.222.1:8000/media/${remote.name} -> timeout")
        }
        emit(
            remote.copy(
                local = true,
                verified = true,
                transferStatus = TransferStatus.Done,
                progress = 1f,
                contentUri = localFile.toURI().toString(),
                lastTransferTarget = target
            )
        )
    }

    override suspend fun delete(fileId: String, local: Boolean): Boolean = false
    override suspend fun verifySha256(fileId: String): Boolean = false
}

private class TwoDeviceGateway : DeviceGateway {
    private val devices = mutableMapOf(
        "HEADSET_001" to testDevice("HEADSET_001", "ForceLink-H1", DeviceType.Headset),
        "GLASSES_G1" to testDevice("GLASSES_G1", "PatrolGlass-G1", DeviceType.Glasses)
    )

    override fun scan(): Flow<List<ScannedDevice>> = flow {
        emit(
            listOf(
                ScannedDevice("HEADSET_001", "ForceLink-H1", 4, "0000-pl2-ble-control", true, "2C:4A:91:3F:8B:02", DeviceType.Headset),
                ScannedDevice("GLASSES_G1", "PatrolGlass-G1", 4, "0000-pl2-ble-control", false, "6B:13:9E:41:D7:50", DeviceType.Glasses)
            )
        )
    }

    override suspend fun bind(deviceId: String): DeviceStatus =
        devices.getValue(deviceId).copy(online = true).also { devices[deviceId] = it }

    override suspend fun unbind(deviceId: String): DeviceStatus? =
        devices[deviceId]?.copy(online = false)?.also { devices[deviceId] = it }

    override suspend fun sendCommand(deviceId: String, command: DeviceCommand): DeviceStatus =
        devices.getValue(deviceId)
}

private class SystemBluetoothScanGateway : DeviceGateway {
    override fun scan(): Flow<List<ScannedDevice>> = flow {
        emit(
            listOf(
                ScannedDevice(
                    id = "FD:4A:BA:43:A2:43",
                    name = "E1-Pro-A243",
                    signalBars = 3,
                    serviceUuid = "system-bluetooth-audio-bonded",
                    bonded = true,
                    macAddress = "FD:4A:BA:43:A2:43",
                    type = DeviceType.Headset
                ),
                ScannedDevice(
                    id = "78:02:B7:66:00:F7",
                    name = "Glory Glass 2-00F7",
                    signalBars = 5,
                    serviceUuid = "system-bluetooth-audio-connected",
                    bonded = true,
                    macAddress = "78:02:B7:66:00:F7",
                    type = DeviceType.Glasses
                )
            )
        )
    }

    override suspend fun bind(deviceId: String): DeviceStatus =
        testDevice(deviceId, "Glory Glass 2-00F7", DeviceType.Glasses).copy(online = false)

    override suspend fun unbind(deviceId: String): DeviceStatus? = null
    override suspend fun sendCommand(deviceId: String, command: DeviceCommand): DeviceStatus =
        testDevice(deviceId, "Glory Glass 2-00F7", DeviceType.Glasses).copy(online = false)
}

private class StaleE1ThenConnectedGlassGateway : DeviceGateway {
    override fun scan(): Flow<List<ScannedDevice>> = flow {
        emit(
            listOf(
                ScannedDevice(
                    id = "FD:4A:BA:43:A2:43",
                    name = "E1-Pro-A243",
                    signalBars = 3,
                    serviceUuid = "system-bluetooth-audio-bonded",
                    bonded = true,
                    macAddress = "FD:4A:BA:43:A2:43",
                    type = DeviceType.Headset
                ),
                ScannedDevice(
                    id = "78:02:B7:66:00:F7",
                    name = "Glory Glass 2-00F7",
                    signalBars = 5,
                    serviceUuid = "system-bluetooth-audio-connected",
                    bonded = true,
                    macAddress = "78:02:B7:66:00:F7",
                    type = DeviceType.Glasses
                )
            )
        )
    }

    override suspend fun bind(deviceId: String): DeviceStatus =
        testDevice(deviceId, "E1-Pro-A243", DeviceType.Headset)

    override suspend fun unbind(deviceId: String): DeviceStatus? = null
    override suspend fun sendCommand(deviceId: String, command: DeviceCommand): DeviceStatus =
        testDevice(deviceId, "E1-Pro-A243", DeviceType.Headset)
}

private class RecordingGlassDeviceGateway : DeviceGateway {
    val commands = mutableListOf<DeviceCommand>()

    override fun scan(): Flow<List<ScannedDevice>> = emptyFlow()

    override suspend fun bind(deviceId: String): DeviceStatus =
        testDevice(deviceId, "Glory Glass 2-00F7", DeviceType.Glasses)

    override suspend fun unbind(deviceId: String): DeviceStatus? = null

    override suspend fun sendCommand(deviceId: String, command: DeviceCommand): DeviceStatus {
        commands += command
        return testDevice(deviceId, "Glory Glass 2-00F7", DeviceType.Glasses).copy(
            isTalking = command == DeviceCommand.StartTalk
        )
    }
}

private fun testDevice(id: String, name: String, type: DeviceType) = DeviceStatus(
    id = id,
    name = name,
    online = true,
    battery = 88,
    signalBars = 4,
    onlineDuration = "刚刚连接",
    storageUsedGb = 0f,
    storageTotalGb = 0f,
    firmware = "",
    isRecording = false,
    isTalking = false,
    cloudConnected = true,
    type = type,
    batteryKnown = true,
    storageKnown = false
)

private class MutableListingMediaGateway(
    var remote: List<MediaFile> = emptyList(),
    var local: List<MediaFile> = emptyList(),
    var remoteFailure: Throwable? = null
) : MediaGateway {
    override suspend fun listFiles(local: Boolean): List<MediaFile> =
        if (local) this.local else remoteFailure?.let { throw it } ?: remote

    override fun transfer(fileId: String, target: TransferTarget): Flow<MediaFile> = emptyFlow()
    override suspend fun delete(fileId: String, local: Boolean): Boolean = false
    override suspend fun verifySha256(fileId: String): Boolean = false
}

private class FailingDeviceListingMediaGateway(private val failure: Throwable) : MediaGateway {
    override suspend fun listFiles(local: Boolean): List<MediaFile> =
        if (local) emptyList() else throw failure

    override fun transfer(fileId: String, target: TransferTarget): Flow<MediaFile> = emptyFlow()
    override suspend fun delete(fileId: String, local: Boolean): Boolean = false
    override suspend fun verifySha256(fileId: String): Boolean = false
}

private class FailingWifiControlGateway(private val failure: Throwable) : DeviceControlGateway {
    override fun events(): Flow<DeviceEvent> = emptyFlow()
    override suspend fun capabilities(device: DeviceStatus): DeviceCapabilities = DeviceCapabilities(supportsWifi = true)
    override suspend fun readWifi(): DeviceWifiState = DeviceWifiState()
    override suspend fun configureWifi(enabled: Boolean, ssid: String, password: String): DeviceWifiState {
        throw failure
    }
    override suspend fun applySettings(device: DeviceStatus, settings: DeviceAdvancedSettings): DeviceAdvancedSettings = settings
    override suspend fun startRealtimeAudioSync(sessionId: String): Boolean = false
    override suspend fun stopRealtimeAudioSync(): Boolean = false
    override suspend fun notifyMediaSyncCompleted(): Boolean = false
    override suspend fun clearDeviceAccount(): Boolean = false
    override suspend fun factoryResetDevice(target: DeviceFactoryResetTarget): Boolean = false
}

private class TrackingWifiControlGateway(
    private val events: MutableList<String>
) : DeviceControlGateway {
    override fun events(): Flow<DeviceEvent> = emptyFlow()
    override suspend fun capabilities(device: DeviceStatus): DeviceCapabilities = DeviceCapabilities(supportsWifi = true, supportsFileTransfer = true)
    override suspend fun readWifi(): DeviceWifiState = DeviceWifiState(enabled = false, ssid = "UTE_00F7")
    override suspend fun configureWifi(enabled: Boolean, ssid: String, password: String): DeviceWifiState {
        events += "configureWifi:$enabled"
        return DeviceWifiState(enabled = enabled, ssid = ssid.ifBlank { "UTE_00F7" }, passwordConfigured = true, connected = enabled)
    }
    override suspend fun applySettings(device: DeviceStatus, settings: DeviceAdvancedSettings): DeviceAdvancedSettings = settings
    override suspend fun startRealtimeAudioSync(sessionId: String): Boolean = false
    override suspend fun stopRealtimeAudioSync(): Boolean = false
    override suspend fun notifyMediaSyncCompleted(): Boolean {
        events += "notifyMediaSyncCompleted"
        return true
    }
    override suspend fun clearDeviceAccount(): Boolean = false
    override suspend fun factoryResetDevice(target: DeviceFactoryResetTarget): Boolean = false
}

private class FixedCapabilitiesControlGateway(
    private val fixedCapabilities: DeviceCapabilities
) : DeviceControlGateway {
    override fun events(): Flow<DeviceEvent> = emptyFlow()
    override suspend fun capabilities(device: DeviceStatus): DeviceCapabilities = fixedCapabilities
    override suspend fun readWifi(): DeviceWifiState = DeviceWifiState()
    override suspend fun configureWifi(enabled: Boolean, ssid: String, password: String): DeviceWifiState = DeviceWifiState()
    override suspend fun applySettings(device: DeviceStatus, settings: DeviceAdvancedSettings): DeviceAdvancedSettings = settings
    override suspend fun startRealtimeAudioSync(sessionId: String): Boolean = false
    override suspend fun stopRealtimeAudioSync(): Boolean = false
    override suspend fun notifyMediaSyncCompleted(): Boolean = false
    override suspend fun clearDeviceAccount(): Boolean = false
    override suspend fun factoryResetDevice(target: DeviceFactoryResetTarget): Boolean = false
}

private class ResetControlGateway(
    private val clearSuccess: Boolean = false,
    private val factoryResetSuccess: Boolean = false
) : DeviceControlGateway {
    var clearCalled: Boolean = false
    var factoryResetTarget: DeviceFactoryResetTarget? = null
    override fun events(): Flow<DeviceEvent> = emptyFlow()
    override suspend fun capabilities(device: DeviceStatus): DeviceCapabilities = DeviceCapabilities()
    override suspend fun readWifi(): DeviceWifiState = DeviceWifiState()
    override suspend fun configureWifi(enabled: Boolean, ssid: String, password: String): DeviceWifiState = DeviceWifiState()
    override suspend fun applySettings(device: DeviceStatus, settings: DeviceAdvancedSettings): DeviceAdvancedSettings = settings
    override suspend fun startRealtimeAudioSync(sessionId: String): Boolean = false
    override suspend fun stopRealtimeAudioSync(): Boolean = false
    override suspend fun notifyMediaSyncCompleted(): Boolean = false
    override suspend fun clearDeviceAccount(): Boolean {
        clearCalled = true
        return clearSuccess
    }
    override suspend fun factoryResetDevice(target: DeviceFactoryResetTarget): Boolean {
        factoryResetTarget = target
        return factoryResetSuccess
    }
}

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

private open class FakeCerebellumReportApi : CerebellumApi {
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
