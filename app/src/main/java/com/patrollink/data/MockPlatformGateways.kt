package com.patrollink.data

import com.patrollink.domain.AppPermission
import com.patrollink.domain.AuthSession
import com.patrollink.domain.BackgroundTask
import com.patrollink.domain.BackgroundTaskGateway
import com.patrollink.domain.BackgroundTaskReceipt
import com.patrollink.domain.EvidenceIntegrityGateway
import com.patrollink.domain.PermissionGateway
import com.patrollink.domain.SecureStore
import java.security.MessageDigest

class InMemorySecureStore : SecureStore {
    private var session: AuthSession? = null

    override suspend fun saveSession(session: AuthSession) {
        this.session = session
    }

    override suspend fun readSession(): AuthSession? = session

    override suspend fun clearSession() {
        session = null
    }
}

class AndroidPermissionPlanner : PermissionGateway {
    override fun requiredPermissions(androidApi: Int): List<AppPermission> {
        val base = mutableListOf(
            AppPermission.Internet,
            AppPermission.NetworkState,
            AppPermission.FineLocation,
            AppPermission.Camera,
            AppPermission.RecordAudio,
            AppPermission.ForegroundService
        )
        if (androidApi >= 31) {
            base += AppPermission.BluetoothScan
            base += AppPermission.BluetoothConnect
            base += AppPermission.BluetoothAdvertise
        }
        if (androidApi >= 33) {
            base += AppPermission.NearbyWifiDevices
        }
        if (androidApi >= 33) {
            base += AppPermission.PostNotifications
        }
        return base
    }

    override fun missingPermissions(androidApi: Int, granted: Set<AppPermission>): List<AppPermission> {
        return requiredPermissions(androidApi).filterNot { it in granted }
    }
}

class InMemoryBackgroundTaskGateway : BackgroundTaskGateway {
    private val receipts = linkedMapOf<String, BackgroundTaskReceipt>()

    override suspend fun enqueue(task: BackgroundTask): BackgroundTaskReceipt {
        return BackgroundTaskReceipt(task, queued = true).also { receipts[task.id] = it }
    }

    override suspend fun pending(): List<BackgroundTaskReceipt> = receipts.values.toList()

    override suspend fun complete(taskId: String): Boolean = receipts.remove(taskId) != null
}

class DefaultEvidenceIntegrityGateway : EvidenceIntegrityGateway {
    override fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    override fun watermarkToken(fileId: String, officerBadgeNo: String, timestamp: Long): String {
        return sha256("$fileId|$officerBadgeNo|$timestamp".encodeToByteArray()).take(16)
    }
}
