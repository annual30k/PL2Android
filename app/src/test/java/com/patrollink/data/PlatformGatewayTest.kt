package com.patrollink.data

import com.patrollink.domain.AppPermission
import com.patrollink.domain.AuthSession
import com.patrollink.domain.BackgroundTask
import com.patrollink.domain.BackgroundTaskType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformGatewayTest {
    @Test
    fun permissionPlannerUsesLocationBeforeAndroid12AndBluetoothPermissionsAfterAndroid12() {
        val planner = AndroidPermissionPlanner()

        val api30 = planner.requiredPermissions(androidApi = 30)
        val api31 = planner.requiredPermissions(androidApi = 31)
        val api33 = planner.requiredPermissions(androidApi = 33)

        assertEquals(
            listOf(
                AppPermission.Internet,
                AppPermission.NetworkState,
                AppPermission.FineLocation,
                AppPermission.Camera,
                AppPermission.RecordAudio,
                AppPermission.ForegroundService
            ),
            api30
        )
        assertTrue(AppPermission.FineLocation in api30)
        assertFalse(AppPermission.BluetoothScan in api30)
        assertTrue(AppPermission.BluetoothScan in api31)
        assertTrue(AppPermission.BluetoothConnect in api31)
        assertTrue(AppPermission.BluetoothAdvertise in api31)
        assertTrue(AppPermission.FineLocation in api31)
        assertFalse(AppPermission.PostNotifications in api31)
        assertTrue(AppPermission.PostNotifications in api33)
    }

    @Test
    fun missingPermissionsReturnsOnlyPermissionsNotGranted() {
        val planner = AndroidPermissionPlanner()
        val granted = setOf(
            AppPermission.Internet,
            AppPermission.NetworkState,
            AppPermission.FineLocation,
            AppPermission.Camera,
            AppPermission.RecordAudio,
            AppPermission.ForegroundService,
            AppPermission.BluetoothScan,
            AppPermission.BluetoothConnect,
            AppPermission.BluetoothAdvertise
        )

        val missing = planner.missingPermissions(androidApi = 33, granted = granted)

        assertEquals(listOf(AppPermission.PostNotifications), missing)
    }

    @Test
    fun secureStoreSavesReadsAndClearsSession() = runTest {
        val store = InMemorySecureStore()
        val session = AuthSession("access", "refresh", 7200)
        val replacement = AuthSession("access-2", "refresh-2", 3600)

        assertNull(store.readSession())

        store.saveSession(session)
        assertEquals(session, store.readSession())

        store.saveSession(replacement)
        assertEquals(replacement, store.readSession())

        store.clearSession()
        assertNull(store.readSession())
    }

    @Test
    fun emptyAuthGatewayDoesNotTreatMissingBackendAsValidSession() = runTest {
        val result = runCatching { EmptyAuthGateway().currentUser() }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("后端地址未配置") == true)
    }

    @Test
    fun backgroundTaskGatewayQueuesListsAndCompletesTasks() = runTest {
        val gateway = InMemoryBackgroundTaskGateway()
        val task = BackgroundTask("TASK-1", BackgroundTaskType.UploadEvidence, "VID-042", 1715832000L)
        val second = BackgroundTask("TASK-2", BackgroundTaskType.Heartbeat, "HEADSET_001", 1715832010L)

        val receipt = gateway.enqueue(task)
        val secondReceipt = gateway.enqueue(second)

        assertTrue(receipt.queued)
        assertTrue(secondReceipt.queued)
        assertEquals(listOf(task, second), gateway.pending().map { it.task })
        assertTrue(gateway.complete("TASK-1"))
        assertEquals(listOf(second), gateway.pending().map { it.task })
        assertFalse(gateway.complete("TASK-MISSING"))
    }

    @Test
    fun evidenceIntegrityCalculatesSha256AndStableWatermarkToken() {
        val gateway = DefaultEvidenceIntegrityGateway()

        val hash = gateway.sha256("abc".encodeToByteArray())
        val token1 = gateway.watermarkToken("VID-042", "POLICE_9527", 1715832000L)
        val token2 = gateway.watermarkToken("VID-042", "POLICE_9527", 1715832000L)
        val token3 = gateway.watermarkToken("VID-042", "POLICE_0001", 1715832000L)

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash)
        assertEquals(16, token1.length)
        assertEquals("dbb893caec25403d", token1)
        assertEquals(token1, token2)
        assertTrue(token1.all { it in '0'..'9' || it in 'a'..'f' })
        assertFalse(token1 == token3)
    }
}
