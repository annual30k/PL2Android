package com.patrollink.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
        val viewModel = PatrolViewModel()

        viewModel.login("POLICE_9527", "123456", agreed = true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLoggedIn)
        assertFalse(viewModel.uiState.value.loginLoading)
    }

    @Test
    fun deviceControlsUpdateRecordingAndTalkingState() = runTest {
        val viewModel = PatrolViewModel()

        viewModel.toggleRecord()
        viewModel.toggleTalk()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.device.isRecording)
        assertTrue(viewModel.uiState.value.device.isTalking)
    }

    @Test
    fun closingAlertMovesItToClosedList() = runTest {
        val viewModel = PatrolViewModel()
        val alertId = viewModel.uiState.value.alerts.first().id

        viewModel.closeAlert(alertId)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.alerts.first { it.id == alertId }.status.name == "Closed")
    }

    @Test
    fun mediaDownloadAndDeleteMutateUiCollection() = runTest {
        val viewModel = PatrolViewModel()
        val fileId = viewModel.uiState.value.mediaFiles.first { !it.local }.id

        viewModel.downloadMedia(fileId)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.mediaFiles.first { it.id == fileId }.local)

        viewModel.deleteMedia(fileId)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.mediaFiles.none { it.id == fileId })
    }

    @Test
    fun sosActivateAndCancelReflectInUiState() = runTest {
        val viewModel = PatrolViewModel()

        viewModel.activateSos()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.sosActive)

        viewModel.cancelSos()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.sosActive)
    }
}
